package com.seniortribe.app.notifications;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.seniortribe.app.models.NeedSummary;

import java.util.concurrent.TimeUnit;

/**
 * E01 · Hẹn nhắc lịch trước giờ hẹn bằng WorkManager (không cần quyền báo thức chính xác, vẫn
 * còn sau khi khởi động lại máy). Mỗi nhu cầu có tối đa hai mốc: trước 1 ngày và trước N giờ.
 *
 * Dữ liệu truyền cho worker chỉ gồm mã nhu cầu, mã hồ sơ, tiêu đề tóm tắt và giờ hẹn; không có
 * tên, địa chỉ hay số điện thoại của người cao tuổi (R03).
 */
public final class ReminderScheduler {

    static final String KEY_UID = "uid";
    static final String KEY_NEED_ID = "needId";
    static final String KEY_ASSIGNMENT_ID = "assignmentId";
    static final String KEY_CASE_CODE = "caseCode";
    static final String KEY_TITLE = "title";
    static final String KEY_START = "startAt";
    static final String KEY_END = "endAt";
    static final String KEY_FORMAT = "format";
    static final String KEY_SLOT = "slot";        // "day" hoặc "hours"

    private ReminderScheduler() {}

    private static String workName(String needId, String slot) {
        return "remind_" + needId + "_" + slot;
    }

    public static void schedule(Context context, String uid, NeedSummary n, NotificationPrefs prefs) {
        long now = System.currentTimeMillis();
        long start = n.getScheduledAtMillis();
        WorkManager wm = WorkManager.getInstance(context);
        long day = start - TimeUnit.DAYS.toMillis(1);
        int hours = prefs.remindHoursBefore();
        long beforeHours = hours > 0 ? start - TimeUnit.HOURS.toMillis(hours) : -1;

        enqueueOrCancel(wm, uid, n, "day", prefs.remindDayBefore() && day > now ? day - now : -1);
        enqueueOrCancel(wm, uid, n, "hours", beforeHours > now ? beforeHours - now : -1);
    }

    private static void enqueueOrCancel(WorkManager wm, String uid, NeedSummary n, String slot, long delay) {
        String name = workName(n.getNeedId(), slot);
        if (delay < 0) {
            wm.cancelUniqueWork(name);
            return;
        }
        Data data = new Data.Builder()
                .putString(KEY_UID, uid)
                .putString(KEY_NEED_ID, n.getNeedId())
                .putString(KEY_ASSIGNMENT_ID, n.getCurrentAssignmentId())
                .putString(KEY_CASE_CODE, n.getCaseCode())
                .putString(KEY_TITLE, n.getTitle())
                .putLong(KEY_START, n.getScheduledAtMillis())
                .putLong(KEY_END, n.getScheduledEndAtMillis())
                .putString(KEY_FORMAT, n.getScheduledFormat())
                .putString(KEY_SLOT, slot)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ScheduleReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder")
                .build();
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req);
    }

    public static void cancel(Context context, String needId) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(workName(needId, "day"));
        wm.cancelUniqueWork(workName(needId, "hours"));
    }

    /** Đăng xuất: hủy mọi lịch nhắc của tài khoản trên máy. */
    public static void cancelAll(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder");
    }
}
