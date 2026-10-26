package com.seniortribe.app.notifications;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.seniortribe.app.R;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.concurrent.TimeUnit;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * E01 · Hiện thông báo nhắc lịch khi tới mốc đã hẹn.
 *
 * Trước khi báo, đọc lại nhu cầu trên máy chủ: nếu lịch đã đổi (E02), đã báo hỗ trợ, bị trả lại
 * hoặc hủy thì không báo nhầm giờ cũ. Không có mạng thì vẫn nhắc theo giờ đã lưu (nhắc nhầm một
 * lần còn hơn quên buổi hẹn với người cao tuổi).
 */
public class ScheduleReminderWorker extends Worker {

    public ScheduleReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uid = getInputData().getString(ReminderScheduler.KEY_UID);
        String needId = getInputData().getString(ReminderScheduler.KEY_NEED_ID);
        long start = getInputData().getLong(ReminderScheduler.KEY_START, 0);
        long end = getInputData().getLong(ReminderScheduler.KEY_END, 0);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Tài khoản đã đăng xuất / đổi tài khoản thì không nhắc lịch của người trước
        if (uid == null || needId == null || user == null || !uid.equals(user.getUid())) return Result.success();

        try {
            DocumentSnapshot d = Tasks.await(FirebaseFirestore.getInstance()
                    .collection(Constants.COL_NEEDS_SUMMARY).document(needId).get(Source.SERVER), 20, TimeUnit.SECONDS);
            if (!d.exists() || !Constants.STATUS_SCHEDULED.equals(d.getString("status"))) return Result.success();
            Timestamp at = d.getTimestamp("scheduledAt");
            if (at == null || at.toDate().getTime() != start) return Result.success(); // lịch đã đổi
        } catch (Exception offlineOrDenied) {
            // Không kiểm tra được: vẫn nhắc theo dữ liệu đã lưu
        }

        Context c = getApplicationContext();
        AppNotifier.createChannels(c);
        String code = getInputData().getString(ReminderScheduler.KEY_CASE_CODE);
        String title = getInputData().getString(ReminderScheduler.KEY_TITLE);
        String format = getInputData().getString(ReminderScheduler.KEY_FORMAT);
        boolean day = "day".equals(getInputData().getString(ReminderScheduler.KEY_SLOT));

        String slot = end > 0 ? DateTimeUtils.formatSlot(start, end) : DateTimeUtils.formatDateTime(start);
        String head = c.getString(day ? R.string.notif_remind_day : R.string.notif_remind_soon,
                TextUtils.isEmpty(code) ? c.getString(R.string.notif_need_fallback) : code);
        StringBuilder text = new StringBuilder(slot);
        if (!TextUtils.isEmpty(format)) text.append(" · ").append(format);
        if (!TextUtils.isEmpty(title)) text.append('\n').append(title);

        String key = "remind_" + needId;
        new AppNotifier(c).post(NotifyCategory.SCHEDULE, key, head, text.toString(),
                DeepLink.pendingIntent(c, AppNotifier.requestCode(key), NotificationRules.Target.PROGRESS,
                        needId, getInputData().getString(ReminderScheduler.KEY_ASSIGNMENT_ID), code),
                null);
        return Result.success();
    }
}
