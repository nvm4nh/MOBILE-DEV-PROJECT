package com.seniortribe.app.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.seniortribe.app.R;

/**
 * E01 · Tạo kênh và đăng thông báo.
 *
 * Mọi thông báo đều đặt VISIBILITY_PRIVATE: trên màn hình khóa chỉ hiện "SeniorTribe · Có thông
 * báo mới", không hiện mã hồ sơ hay nội dung. Nội dung tin nhắn chỉ hiện khi người dùng tự bật
 * "Hiện nội dung tin nhắn" (mặc định tắt).
 */
public final class AppNotifier {

    private final Context context;
    private final NotificationPrefs prefs;

    public AppNotifier(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new NotificationPrefs(this.context);
    }

    public NotificationPrefs prefs() {
        return prefs;
    }

    /** Tạo các kênh thông báo (gọi nhiều lần không sao). */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(channel(context, NotifyCategory.MESSAGES, R.string.notif_channel_messages,
                R.string.notif_channel_messages_desc, NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(context, NotifyCategory.SCHEDULE, R.string.notif_channel_schedule,
                R.string.notif_channel_schedule_desc, NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(context, NotifyCategory.STATUS, R.string.notif_channel_status,
                R.string.notif_channel_status_desc, NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(context, NotifyCategory.FOLLOW_UP, R.string.notif_channel_follow_up,
                R.string.notif_channel_follow_up_desc, NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(context, NotifyCategory.CALLS, R.string.notif_channel_calls,
                R.string.notif_channel_calls_desc, NotificationManager.IMPORTANCE_HIGH));
    }

    private static NotificationChannel channel(Context c, NotifyCategory cat, @StringRes int name,
                                               @StringRes int desc, int importance) {
        NotificationChannel ch = new NotificationChannel(cat.channelId, c.getString(name), importance);
        ch.setDescription(c.getString(desc));
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        return ch;
    }

    /** Ứng dụng có được phép hiện thông báo không (Android 13+ cần người dùng cho phép). */
    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Đăng (hoặc thay) một thông báo.
     *
     * @param key khóa ổn định, cùng khóa thì thông báo mới thay thông báo cũ (ví dụ một luồng chat)
     */
    public void post(NotifyCategory category, String key, String title, @Nullable String text,
                     PendingIntent tap, @Nullable String group) {
        if (!prefs.isEnabled(category) || !canPost(context)) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, category.channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(new NotificationCompat.Builder(context, category.channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(context.getString(R.string.notif_public_text))
                        .build())
                .setPriority(category == NotifyCategory.MESSAGES || category == NotifyCategory.SCHEDULE
                        || category == NotifyCategory.CALLS
                        ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(category == NotifyCategory.CALLS ? NotificationCompat.CATEGORY_CALL
                        : category == NotifyCategory.MESSAGES ? NotificationCompat.CATEGORY_MESSAGE
                        : category == NotifyCategory.SCHEDULE ? NotificationCompat.CATEGORY_REMINDER
                        : NotificationCompat.CATEGORY_STATUS);
        if (!TextUtils.isEmpty(text)) {
            b.setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        }
        if (group != null) b.setGroup(group);
        if (category == NotifyCategory.CALLS) b.setTimeoutAfter(com.seniortribe.app.models.CallRecord.RING_TIMEOUT_MS);
        try {
            NotificationManagerCompat.from(context).notify(key, 0, b.build());
        } catch (SecurityException ignored) {
            // Người dùng vừa thu hồi quyền thông báo
        }
    }

    /** Gỡ thông báo theo khóa (ví dụ khi mở luồng chat đó). */
    public void cancel(String key) {
        NotificationManagerCompat.from(context).cancel(key, 0);
    }

    /** Đăng xuất: gỡ mọi thông báo đang hiện để người sau không thấy. */
    public void cancelAll() {
        NotificationManagerCompat.from(context).cancelAll();
    }

    /** Mã yêu cầu cho PendingIntent, khác nhau theo khóa. */
    public static int requestCode(String key) {
        return key.hashCode() & 0x7fffffff;
    }

    // ----- Câu chữ -----

    @StringRes
    public static int title(NotificationRules.Kind kind) {
        switch (kind) {
            case REVIEW_NEEDED: return R.string.notif_review_needed;
            case REVISION_NEEDED: return R.string.notif_revision_needed;
            case APPROVED: return R.string.notif_approved;
            case ASSIGNED_TO_ME: return R.string.notif_assigned_to_me;
            case ACCEPTED_BY_ORG: return R.string.notif_accepted_by_org;
            case RETURNED: return R.string.notif_returned;
            case CANCELLED: return R.string.notif_cancelled;
            case SCHEDULE_TO_ANSWER: return R.string.notif_schedule_to_answer;
            case SCHEDULE_DECLINED: return R.string.notif_schedule_declined;
            case SCHEDULE_CONFIRMED: return R.string.notif_schedule_confirmed;
            case RESCHEDULE_TO_ANSWER: return R.string.notif_reschedule_to_answer;
            case RESCHEDULE_DECLINED: return R.string.notif_reschedule_declined;
            case RESCHEDULE_WITHDRAWN: return R.string.notif_reschedule_withdrawn;
            case RESCHEDULED: return R.string.notif_rescheduled;
            case RECONCILE_NEEDED: return R.string.notif_reconcile_needed;
            case RECONCILE_NOTE: return R.string.notif_reconcile_note;
            case COMPLETED:
            default: return R.string.notif_completed;
        }
    }
}
