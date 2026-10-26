package com.seniortribe.app.notifications;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E01 · Thông báo realtime khi ứng dụng đang chạy (kể cả ở nền, tới khi Android dừng tiến trình).
 *
 * Sau khi tài khoản được xác nhận quyền (SeniorTribeApplication.markActiveAccess), lắng nghe:
 *  - nhu cầu của đơn vị (đơn vị ghi nhận hoặc đang phụ trách) — hai truy vấn như màn hình Chat;
 *  - sự kiện mới của từng nhu cầu liên quan (needs_summary/{id}/events);
 *  - tin nhắn mới của lần tiếp nhận mà mình là một trong hai người chat (R08).
 * Mỗi listener chỉ đọc phần sau mốc đã xử lý nên không tải lại lịch sử. Đồng thời hẹn / hủy nhắc
 * lịch theo trạng thái nhu cầu. Mất quyền hoặc đăng xuất thì dừng hết.
 */
public final class NotificationCenter {

    private static volatile boolean running;
    @Nullable private static String visibleChat;

    private final Context context;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final NeedRepository needs = new NeedRepository();

    private final List<ListenerRegistration> base = new ArrayList<>();
    private final Map<String, ListenerRegistration> eventListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> messageListeners = new HashMap<>();
    private final Map<String, NeedSummary> recorded = new LinkedHashMap<>();
    private final Map<String, NeedSummary> assigned = new LinkedHashMap<>();
    private boolean recordedLoaded, assignedLoaded;

    @Nullable private NotificationEngine engine;
    @Nullable private String uid;
    private long generation;

    public NotificationCenter(Context context) {
        this.context = context.getApplicationContext();
        AppNotifier.createChannels(this.context);
    }

    /** Có listener realtime đang chạy trong tiến trình này không (worker định kỳ sẽ bỏ qua). */
    public static boolean isRunning() {
        return running;
    }

    /** ChatActivity báo luồng chat đang mở để không hiện thông báo trùng với màn hình. */
    @MainThread
    public static void setVisibleChat(@Nullable String assignmentId) {
        visibleChat = assignmentId;
    }

    @MainThread
    public void start(String forUid) {
        if (running && forUid.equals(uid)) return;
        stop();
        uid = forUid;
        final long request = ++generation;
        NotificationSyncWorker.schedule(context);
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            if (request != generation) return; // đã dừng / đổi tài khoản trong lúc chờ
            Membership me = task.isSuccessful() ? task.getResult() : null;
            if (me == null || !forUid.equals(me.getUid())) return;
            engine = new NotificationEngine(context, me);
            running = true;
            base.add(needs.listenRecordedBy(me.getOrgId(), callback(recorded, true)));
            base.add(needs.listenAssignedTo(me.getOrgId(), callback(assigned, false)));
        });
    }

    @MainThread
    public void stop() {
        ++generation;
        running = false;
        for (ListenerRegistration r : base) r.remove();
        base.clear();
        for (ListenerRegistration r : eventListeners.values()) r.remove();
        eventListeners.clear();
        for (ListenerRegistration r : messageListeners.values()) r.remove();
        messageListeners.clear();
        recorded.clear();
        assigned.clear();
        recordedLoaded = assignedLoaded = false;
        engine = null;
        uid = null;
    }

    /** Đăng xuất: dừng listener, hủy nhắc lịch và việc định kỳ, gỡ thông báo, xóa mốc đã xem. */
    @MainThread
    public void signOut() {
        stop();
        NotificationSyncWorker.cancel(context);
        ReminderScheduler.cancelAll(context);
        AppNotifier n = new AppNotifier(context);
        n.cancelAll();
        n.prefs().clearState();
    }

    private NeedRepository.Callback<List<NeedSummary>> callback(Map<String, NeedSummary> target, boolean isRecorded) {
        final long request = generation;
        return new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                if (request != generation) return;
                target.clear();
                for (NeedSummary n : data) target.put(n.getNeedId(), n);
                if (isRecorded) recordedLoaded = true;
                else assignedLoaded = true;
                refresh();
            }

            @Override public void onError(Exception error) {
                // Mất quyền / mất mạng: LiveAccessMonitor sẽ xử lý phiên; ở đây chỉ bỏ qua
            }
        };
    }

    /**
     * Người dùng vừa đổi tùy chọn nhắc lịch: hủy các lịch nhắc đã hẹn rồi hẹn lại theo tùy chọn mới.
     */
    @MainThread
    public void resyncReminders() {
        NotificationPrefs prefs = new NotificationPrefs(context);
        if (uid != null) {
            for (String needId : prefs.reminderNeeds(uid)) {
                ReminderScheduler.cancel(context, needId);
                prefs.setReminderFor(uid, needId, 0);
            }
        }
        refresh();
    }

    /** Gắn / gỡ listener theo danh sách nhu cầu mới nhất. */
    private void refresh() {
        NotificationEngine e = engine;
        if (e == null || !recordedLoaded || !assignedLoaded) return;
        Map<String, NeedSummary> all = new LinkedHashMap<>(recorded);
        all.putAll(assigned);

        Set<String> wantEvents = new HashSet<>();
        Set<String> wantChats = new HashSet<>();
        for (NeedSummary n : all.values()) {
            if (e.watchEvents(n)) {
                wantEvents.add(n.getNeedId());
                if (!eventListeners.containsKey(n.getNeedId())) eventListeners.put(n.getNeedId(), listenEvents(n.getNeedId()));
            }
            if (e.watchMessages(n)) {
                String a = n.getCurrentAssignmentId();
                wantChats.add(a);
                if (!messageListeners.containsKey(a)) messageListeners.put(a, listenMessages(n.getNeedId(), a));
            }
        }
        removeOthers(eventListeners, wantEvents);
        removeOthers(messageListeners, wantChats);
        e.syncReminders(new ArrayList<>(all.values()));
    }

    private ListenerRegistration listenEvents(String needId) {
        final long request = generation;
        NotificationEngine e = engine;
        return db.collection(Constants.COL_NEEDS_SUMMARY).document(needId).collection(Constants.SUB_EVENTS)
                .whereGreaterThan("at", new Timestamp(new Date(e.eventsSince(needId))))
                .orderBy("at")
                .addSnapshotListener((snap, error) -> {
                    if (request != generation || snap == null || engine == null) return;
                    NeedSummary n = current(needId);
                    if (n != null) engine.onEvents(n, snap.getDocuments());
                });
    }

    private ListenerRegistration listenMessages(String needId, String assignmentId) {
        final long request = generation;
        NotificationEngine e = engine;
        return db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId).collection(Constants.COL_MESSAGES)
                .whereGreaterThan("at", new Timestamp(new Date(e.messagesSince(assignmentId))))
                .orderBy("at")
                .addSnapshotListener((snap, error) -> {
                    if (request != generation || snap == null || engine == null) return;
                    NeedSummary n = current(needId);
                    if (n != null) engine.onMessages(n, assignmentId, snap.getDocuments(), visibleChat);
                });
    }

    @Nullable
    private NeedSummary current(String needId) {
        NeedSummary n = assigned.get(needId);
        return n != null ? n : recorded.get(needId);
    }

    private static void removeOthers(Map<String, ListenerRegistration> map, Set<String> keep) {
        List<String> drop = new ArrayList<>();
        for (String k : map.keySet()) if (!keep.contains(k)) drop.add(k);
        for (String k : drop) {
            ListenerRegistration r = map.remove(k);
            if (r != null) r.remove();
        }
    }
}
