package com.seniortribe.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Định dạng ngày giờ theo cách người Việt đọc: "Thứ 7, 19/10", "14:00 – 16:00", "Hôm nay".
 * Chỉ gọi trên luồng giao diện (SimpleDateFormat không an toàn đa luồng).
 */
public class DateTimeUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat FULL_FORMAT = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat DAY_MONTH = new SimpleDateFormat("dd/MM", Locale.getDefault());

    public static String formatDate(long millis) {
        return DATE_FORMAT.format(new Date(millis));
    }

    public static String formatTime(long millis) {
        return TIME_FORMAT.format(new Date(millis));
    }

    public static String formatDateTime(long millis) {
        return FULL_FORMAT.format(new Date(millis));
    }

    /** "Thứ 7", "Chủ nhật"... */
    public static String weekday(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SUNDAY ? "Chủ nhật" : "Thứ " + d; // MONDAY = 2 ... SATURDAY = 7
    }

    /** "Thứ 7, 19/10"; thêm năm nếu khác năm hiện tại. */
    public static String formatWeekdayDate(long millis) {
        String date = isSameYear(millis, System.currentTimeMillis())
                ? DAY_MONTH.format(new Date(millis)) : DATE_FORMAT.format(new Date(millis));
        return weekday(millis) + ", " + date;
    }

    /** "Thứ 7, 19/10/2026": luôn có năm, dùng cho ô Ngày của lịch hẹn. */
    public static String formatWeekdayFullDate(long millis) {
        return weekday(millis) + ", " + DATE_FORMAT.format(new Date(millis));
    }

    /** Nhãn dòng chia ngày trong chat: "Hôm nay", "Hôm qua" hoặc "Thứ 7, 19/10". */
    public static String formatDayLabel(long millis) {
        long now = System.currentTimeMillis();
        if (isSameDay(millis, now)) return "Hôm nay";
        if (isSameDay(millis, now - 24L * 60 * 60 * 1000)) return "Hôm qua";
        if (isSameDay(millis, now + 24L * 60 * 60 * 1000)) return "Ngày mai";
        return formatWeekdayDate(millis);
    }

    /** "14:00 – 16:00"; nếu chưa có giờ kết thúc thì chỉ "14:00". */
    public static String formatTimeRange(long start, long end) {
        if (end <= start) return formatTime(start);
        return formatTime(start) + " – " + formatTime(end);
    }

    /** "Thứ 7, 19/10 · 14:00 – 16:00" */
    public static String formatSlot(long start, long end) {
        return formatWeekdayDate(start) + " · " + formatTimeRange(start, end);
    }

    public static boolean isSameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean isSameYear(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR);
    }

    /** "19/10" */
    public static String formatDayMonth(long millis) {
        return DAY_MONTH.format(new Date(millis));
    }

    /** "19/10 · 16:20" — mốc thời gian ngắn trên màn hình tiến độ. */
    public static String formatShortStamp(long millis) {
        return DAY_MONTH.format(new Date(millis)) + " · " + formatTime(millis);
    }

    public static String formatRelativeTime(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60 * 1000) {
            return "Vừa xong";
        } else if (diff < 60 * 60 * 1000) {
            return (diff / (60 * 1000)) + " phút trước";
        } else if (diff < 24 * 60 * 60 * 1000) {
            return (diff / (60 * 60 * 1000)) + " giờ trước";
        } else if (diff < 7 * 24 * 60 * 60 * 1000) {
            return (diff / (24 * 60 * 60 * 1000)) + " ngày trước";
        } else {
            return formatDate(millis);
        }
    }
}
