package com.seniortribe.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * E01 · Tùy chọn thông báo và "mốc đã xem" để không báo trùng.
 *
 * Chỉ lưu cờ bật/tắt, mốc thời gian và mã hồ sơ đã hẹn nhắc lịch; KHÔNG lưu nội dung tin nhắn
 * hay thông tin riêng của người cao tuổi xuống máy (F16, T05). Mốc thời gian gắn với uid để
 * đổi tài khoản trên cùng máy không dùng lại mốc của người trước; đăng xuất thì xóa sạch.
 */
public final class NotificationPrefs {

    private static final String FILE_SETTINGS = "notification_settings";
    private static final String FILE_STATE = "notification_state";

    public static final String KEY_PREVIEW = "pref_notify_preview";
    public static final String KEY_REMIND_DAY = "pref_remind_day_before";
    public static final String KEY_REMIND_HOURS = "pref_remind_hours_before";
    public static final String KEY_PERMISSION_ASKED = "pref_permission_asked";

    /** Chỉ báo sự kiện trong vòng 24 giờ gần nhất khi mới thấy một nhu cầu, tránh báo dồn dập. */
    public static final long MAX_LOOKBACK_MS = 24L * 60 * 60 * 1000;

    private final SharedPreferences settings;
    private final SharedPreferences state;

    public NotificationPrefs(Context context) {
        Context app = context.getApplicationContext();
        settings = app.getSharedPreferences(FILE_SETTINGS, Context.MODE_PRIVATE);
        state = app.getSharedPreferences(FILE_STATE, Context.MODE_PRIVATE);
    }

    // ----- Tùy chọn của người dùng -----

    public boolean isEnabled(NotifyCategory c) {
        return settings.getBoolean(c.prefKey, true);
    }

    public void setEnabled(NotifyCategory c, boolean on) {
        settings.edit().putBoolean(c.prefKey, on).apply();
    }

    /** Hiện nội dung tin nhắn trên thông báo; mặc định tắt vì có thể lộ trên màn hình khóa. */
    public boolean showPreview() {
        return settings.getBoolean(KEY_PREVIEW, false);
    }

    public void setShowPreview(boolean on) {
        settings.edit().putBoolean(KEY_PREVIEW, on).apply();
    }

    public boolean remindDayBefore() {
        return settings.getBoolean(KEY_REMIND_DAY, true);
    }

    public void setRemindDayBefore(boolean on) {
        settings.edit().putBoolean(KEY_REMIND_DAY, on).apply();
    }

    /** Nhắc trước bao nhiêu giờ (0 = tắt). Mặc định 2 giờ. */
    public int remindHoursBefore() {
        return settings.getInt(KEY_REMIND_HOURS, 2);
    }

    public void setRemindHoursBefore(int hours) {
        settings.edit().putInt(KEY_REMIND_HOURS, Math.max(0, Math.min(hours, 12))).apply();
    }

    public boolean permissionAsked() {
        return settings.getBoolean(KEY_PERMISSION_ASKED, false);
    }

    public void markPermissionAsked() {
        settings.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
    }

    // ----- Mốc đã xem (theo uid) -----

    /**
     * Thời điểm bắt đầu theo dõi của tài khoản. Lần đầu gọi thì lấy "bây giờ" (lùi 2 phút cho
     * lệch đồng hồ), nên cài mới không bị báo lại mọi việc cũ.
     */
    public synchronized long since(String uid) {
        String key = "since_" + uid;
        long v = state.getLong(key, 0);
        if (v == 0) {
            v = System.currentTimeMillis() - 2 * 60 * 1000;
            state.edit().putLong(key, v).apply();
        }
        return v;
    }

    /** Mốc đã xử lý của một luồng (sự kiện của nhu cầu, tin nhắn của lần tiếp nhận). */
    public synchronized long watermark(String uid, String stream) {
        long w = state.getLong("w_" + uid + "_" + stream, 0);
        if (w > 0) return w;
        return Math.max(since(uid), System.currentTimeMillis() - MAX_LOOKBACK_MS);
    }

    /** Chỉ tiến lên, không lùi (hai nguồn cùng báo một sự kiện thì chỉ báo một lần). */
    public synchronized boolean advance(String uid, String stream, long at) {
        String key = "w_" + uid + "_" + stream;
        if (at <= state.getLong(key, 0)) return false;
        state.edit().putLong(key, at).apply();
        return true;
    }

    /** Lịch đã hẹn nhắc cho nhu cầu (giờ bắt đầu), để biết khi nào cần hẹn lại. */
    public synchronized long reminderFor(String uid, String needId) {
        return state.getLong("r_" + uid + "_" + needId, 0);
    }

    public synchronized void setReminderFor(String uid, String needId, long startAt) {
        if (startAt <= 0) state.edit().remove("r_" + uid + "_" + needId).apply();
        else state.edit().putLong("r_" + uid + "_" + needId, startAt).apply();
    }

    /** Các nhu cầu đang có nhắc lịch của tài khoản. */
    public synchronized Set<String> reminderNeeds(String uid) {
        Set<String> out = new HashSet<>();
        String prefix = "r_" + uid + "_";
        for (Map.Entry<String, ?> e : state.getAll().entrySet()) {
            if (e.getKey().startsWith(prefix)) out.add(e.getKey().substring(prefix.length()));
        }
        return out;
    }

    /** Đăng xuất: xóa mốc và lịch nhắc của mọi tài khoản trên máy (giữ tùy chọn bật/tắt). */
    public synchronized void clearState() {
        state.edit().clear().apply();
    }

    @Nullable
    public static String stream(String prefix, String id) {
        return id == null ? null : prefix + "_" + id;
    }
}
