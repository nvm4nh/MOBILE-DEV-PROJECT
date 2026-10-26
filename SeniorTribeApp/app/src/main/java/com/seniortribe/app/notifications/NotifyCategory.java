package com.seniortribe.app.notifications;

/**
 * E01 · Các loại thông báo người dùng bật / tắt được trong "Thông báo &amp; nhắc lịch".
 * Mỗi loại là một kênh thông báo riêng của Android, nên người dùng cũng chỉnh được trong
 * Cài đặt hệ thống (âm thanh, hiện trên màn hình khóa…).
 */
public enum NotifyCategory {
    /** Tin nhắn mới trong chat phối hợp (F11, E06). */
    MESSAGES("st_messages", "pref_notify_messages"),
    /** Đề xuất / chốt / đổi lịch (F12, E02) và nhắc lịch trước giờ hẹn. */
    SCHEDULE("st_schedule", "pref_notify_schedule"),
    /** Tiếp nhận, trả lại, hủy, hoàn thành (F10, F13, F14). */
    STATUS("st_status", "pref_notify_status"),
    /** Việc cần mình xử lý: rà soát, bổ sung, đối soát kết quả (F07, F13). */
    FOLLOW_UP("st_follow_up", "pref_notify_follow_up");

    public final String channelId;
    public final String prefKey;

    NotifyCategory(String channelId, String prefKey) {
        this.channelId = channelId;
        this.prefKey = prefKey;
    }
}
