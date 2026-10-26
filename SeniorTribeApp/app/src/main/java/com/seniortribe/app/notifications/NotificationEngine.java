package com.seniortribe.app.notifications;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * E01 · Xử lý chung cho hai nguồn thông báo:
 *  - {@link NotificationCenter}: listener realtime khi ứng dụng đang chạy;
 *  - {@link NotificationSyncWorker}: WorkManager kiểm tra định kỳ (~15 phút) khi ứng dụng đã tắt.
 * Cả hai đọc cùng mốc "đã xử lý" trong {@link NotificationPrefs} nên một sự kiện chỉ được báo
 * một lần. Quyết định báo cho ai nằm ở {@link NotificationRules}.
 *
 * Firestore Security Rules vẫn là lớp chặn cuối: tài khoản chỉ nhận được sự kiện / tin nhắn
 * mà nó được phép đọc, nên thông báo không thể làm lộ dữ liệu ngoài quyền.
 */
public final class NotificationEngine {

    /** Vẫn theo dõi nhu cầu đã kết thúc trong 2 ngày để không lỡ sự kiện hoàn thành / hủy. */
    private static final long FINISHED_GRACE_MS = 2L * 24 * 60 * 60 * 1000;

    private final Context context;
    private final AppNotifier notifier;
    private final NotificationPrefs prefs;
    private final NotificationRules.Me me;
    private final String uid;

    public NotificationEngine(Context context, Membership membership) {
        this.context = context.getApplicationContext();
        this.notifier = new AppNotifier(this.context);
        this.prefs = notifier.prefs();
        this.uid = membership.getUid();
        this.me = new NotificationRules.Me(membership.getUid(), membership.getOrgId(), membership.getRoles());
    }

    public String uid() {
        return uid;
    }

    public static NotificationRules.Need need(NeedSummary n) {
        return new NotificationRules.Need(n.getRecorderOrgId(), n.getRecorderUid(), n.getAssignedOrgId(), n.getAssigneeUid());
    }

    // =====================================================================
    // Chọn luồng cần theo dõi
    // =====================================================================

    /** Nhu cầu có thể sinh thông báo cho tài khoản này. */
    public boolean watchEvents(NeedSummary n) {
        if (n == null || n.getNeedId() == null) return false;
        if (n.isFinished()) {
            long updated = n.getUpdatedAt() != null ? n.getUpdatedAt().getTime() : 0;
            if (System.currentTimeMillis() - updated > FINISHED_GRACE_MS) return false;
        }
        NotificationRules.Need x = need(n);
        return NotificationRules.isRecorderSide(me, x) || NotificationRules.isOrgSide(me, x)
                || me.reviewer && me.orgId.equals(n.getRecorderOrgId())
                || uid.equals(n.getRecorderUid()) || uid.equals(n.getAssigneeUid());
    }

    /** Lần tiếp nhận hiện tại có chat mà tài khoản là một trong hai người (R08). */
    public boolean watchMessages(NeedSummary n) {
        return n != null && n.getCurrentAssignmentId() != null && n.isCoordinating()
                && NotificationRules.isChatMember(me, need(n));
    }

    public long eventsSince(String needId) {
        return prefs.watermark(uid, "ev_" + needId);
    }

    public long messagesSince(String assignmentId) {
        return prefs.watermark(uid, "msg_" + assignmentId);
    }

    // =====================================================================
    // Sự kiện của nhu cầu
    // =====================================================================

    /** Xử lý các sự kiện mới (đã sắp theo thời gian hay chưa đều được). */
    public void onEvents(NeedSummary n, List<DocumentSnapshot> docs) {
        List<DocumentSnapshot> sorted = sortByAt(docs);
        String stream = "ev_" + n.getNeedId();
        long seen = eventsSince(n.getNeedId());
        NotificationRules.Need x = need(n);
        for (DocumentSnapshot d : sorted) {
            long at = millis(d.getTimestamp("at"));
            if (at <= 0 || at <= seen) continue;
            if (!prefs.advance(uid, stream, at)) continue;
            seen = at;
            NotificationRules.Decision decision = NotificationRules.forEvent(me, x,
                    d.getString("type"), d.getString("byUid"), d.getString("byOrgId"));
            if (decision == null) continue;
            postEvent(n, decision, d.getString("byName"), d.getString("type"));
        }
    }

    private void postEvent(NeedSummary n, NotificationRules.Decision decision, @Nullable String byName,
                           @Nullable String type) {
        String code = nonEmpty(n.getCaseCode(), context.getString(R.string.notif_need_fallback));
        String title = context.getString(R.string.notif_title_with_code,
                context.getString(AppNotifier.title(decision.kind)), code);
        String summary = nonEmpty(n.getTitle(), "");
        String text = TextUtils.isEmpty(byName) ? summary
                : context.getString(R.string.notif_by_line, byName, summary);
        // Một nhu cầu chỉ giữ một thông báo trạng thái gần nhất cho mỗi loại
        String key = "need_" + n.getNeedId() + "_" + decision.category.name();
        notifier.post(decision.category, key, title, text,
                DeepLink.pendingIntent(context, AppNotifier.requestCode(key + type), decision.target,
                        n.getNeedId(), n.getCurrentAssignmentId(), n.getCaseCode()),
                "need_" + n.getNeedId());
    }

    // =====================================================================
    // Tin nhắn
    // =====================================================================

    /**
     * Tin nhắn mới của một lần tiếp nhận.
     *
     * @param visibleAssignmentId luồng chat đang mở trên màn hình: chỉ đánh dấu đã xem, không báo
     */
    public void onMessages(NeedSummary n, String assignmentId, List<DocumentSnapshot> docs,
                           @Nullable String visibleAssignmentId) {
        List<DocumentSnapshot> sorted = sortByAt(docs);
        String stream = "msg_" + assignmentId;
        long seen = messagesSince(assignmentId);
        NotificationRules.Need x = need(n);
        int count = 0;
        DocumentSnapshot last = null;
        for (DocumentSnapshot d : sorted) {
            long at = millis(d.getTimestamp("at"));
            if (at <= 0 || at <= seen) continue; // tin còn chờ máy chủ thì chưa có giờ, xử lý sau
            if (!prefs.advance(uid, stream, at)) continue;
            seen = at;
            if (!NotificationRules.shouldNotifyMessage(me, x, d.getString("senderUid"))) continue;
            count++;
            last = d;
        }
        if (last == null || assignmentId.equals(visibleAssignmentId)) return;

        String code = nonEmpty(n.getCaseCode(), context.getString(R.string.notif_need_fallback));
        String sender = nonEmpty(last.getString("senderName"), context.getString(R.string.chat_sender_unknown));
        String title = context.getString(R.string.notif_message_title, code);
        String text;
        if (prefs.showPreview()) {
            text = context.getString(R.string.notif_message_preview, sender, messagePreview(last));
        } else {
            text = count > 1 ? context.getString(R.string.notif_message_many, sender, count)
                    : context.getString(R.string.notif_message_hidden, sender);
        }
        String key = chatKey(assignmentId);
        notifier.post(NotifyCategory.MESSAGES, key, title, text,
                DeepLink.pendingIntent(context, AppNotifier.requestCode(key), NotificationRules.Target.CHAT,
                        n.getNeedId(), assignmentId, n.getCaseCode()),
                null);
    }

    /** Nội dung xem trước một tin nhắn; tin ảnh / tệp (E06) chỉ ghi loại, không ghi tên tệp. */
    private String messagePreview(DocumentSnapshot d) {
        String type = d.getString("type");
        if ("IMAGE".equals(type)) return context.getString(R.string.notif_message_image);
        if ("FILE".equals(type)) return context.getString(R.string.notif_message_file);
        String text = d.getString("text");
        if (text == null) return "";
        return text.length() > 120 ? text.substring(0, 117) + "…" : text;
    }

    public static String chatKey(String assignmentId) {
        return "chat_" + assignmentId;
    }

    // =====================================================================
    // Nhắc lịch
    // =====================================================================

    /**
     * Hẹn / hủy nhắc lịch theo danh sách nhu cầu hiện tại: nhu cầu SCHEDULED mà mình là một bên
     * thì hẹn nhắc; nhu cầu không còn SCHEDULED (đã hỗ trợ, trả lại, hủy) hoặc đổi giờ thì hủy /
     * hẹn lại. Chỉ gọi khi danh sách đã đầy đủ (cả hai truy vấn đã có dữ liệu).
     */
    public void syncReminders(List<NeedSummary> needs) {
        Set<String> keep = new HashSet<>();
        boolean schedulesOn = prefs.isEnabled(NotifyCategory.SCHEDULE);
        for (NeedSummary n : needs) {
            if (n == null || n.getNeedId() == null) continue;
            if (!schedulesOn || !Constants.STATUS_SCHEDULED.equals(n.getStatus())) continue;
            if (!NotificationRules.shouldRemind(me, need(n))) continue;
            long start = n.getScheduledAtMillis();
            if (start <= System.currentTimeMillis()) continue;
            keep.add(n.getNeedId());
            if (prefs.reminderFor(uid, n.getNeedId()) == start) continue; // đã hẹn đúng giờ này
            ReminderScheduler.schedule(context, uid, n, prefs);
            prefs.setReminderFor(uid, n.getNeedId(), start);
        }
        for (String needId : prefs.reminderNeeds(uid)) {
            if (keep.contains(needId)) continue;
            ReminderScheduler.cancel(context, needId);
            prefs.setReminderFor(uid, needId, 0);
        }
    }

    // =====================================================================

    private static List<DocumentSnapshot> sortByAt(List<DocumentSnapshot> docs) {
        List<DocumentSnapshot> out = new ArrayList<>(docs);
        Collections.sort(out, (a, b) -> Long.compare(millis(a.getTimestamp("at")), millis(b.getTimestamp("at"))));
        return out;
    }

    private static long millis(@Nullable Timestamp t) {
        return t != null ? t.toDate().getTime() : 0;
    }

    private static String nonEmpty(@Nullable String s, String fallback) {
        return TextUtils.isEmpty(s) ? fallback : s;
    }
}
