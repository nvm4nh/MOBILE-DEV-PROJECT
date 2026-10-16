package com.seniortribe.app.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat FULL_FORMAT = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

    public static String formatDate(long millis) {
        return DATE_FORMAT.format(new Date(millis));
    }

    public static String formatTime(long millis) {
        return TIME_FORMAT.format(new Date(millis));
    }

    public static String formatDateTime(long millis) {
        return FULL_FORMAT.format(new Date(millis));
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
