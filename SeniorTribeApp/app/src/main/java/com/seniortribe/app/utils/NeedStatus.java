package com.seniortribe.app.utils;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.seniortribe.app.R;

/**
 * Nhãn và màu của 7 trạng thái nhu cầu (Bảng 3.2 trong báo cáo).
 * Dùng chung cho thẻ nhu cầu, màn hình chi tiết và rà soát.
 */
public final class NeedStatus {

    private NeedStatus() {}

    @StringRes
    public static int label(String status) {
        if (status == null) return R.string.status_open_label;
        switch (status) {
            case Constants.STATUS_REVIEW: return R.string.status_review_label;
            case Constants.STATUS_ACCEPTED: return R.string.status_accepted_label;
            case Constants.STATUS_SCHEDULED: return R.string.status_scheduled_label;
            case Constants.STATUS_DELIVERED: return R.string.status_delivered_label;
            case Constants.STATUS_COMPLETED: return R.string.status_completed_label;
            case Constants.STATUS_CANCELLED: return R.string.status_cancelled_label;
            case Constants.STATUS_OPEN:
            default: return R.string.status_open_label;
        }
    }

    @ColorRes
    public static int textColor(String status) {
        if (status == null) return R.color.status_open;
        switch (status) {
            case Constants.STATUS_REVIEW: return R.color.status_review;
            case Constants.STATUS_ACCEPTED: return R.color.status_accepted;
            case Constants.STATUS_SCHEDULED: return R.color.status_scheduled;
            case Constants.STATUS_DELIVERED: return R.color.status_delivered;
            case Constants.STATUS_COMPLETED: return R.color.status_completed;
            case Constants.STATUS_CANCELLED: return R.color.status_cancelled;
            case Constants.STATUS_OPEN:
            default: return R.color.status_open;
        }
    }

    @ColorRes
    public static int backgroundColor(String status) {
        if (status == null) return R.color.status_open_bg;
        switch (status) {
            case Constants.STATUS_REVIEW: return R.color.status_review_bg;
            case Constants.STATUS_ACCEPTED: return R.color.status_accepted_bg;
            case Constants.STATUS_SCHEDULED: return R.color.status_scheduled_bg;
            case Constants.STATUS_DELIVERED: return R.color.status_delivered_bg;
            case Constants.STATUS_COMPLETED: return R.color.status_completed_bg;
            case Constants.STATUS_CANCELLED: return R.color.status_cancelled_bg;
            case Constants.STATUS_OPEN:
            default: return R.color.status_open_bg;
        }
    }

    /**
     * Hiển thị nhãn trạng thái dạng chip: chấm màu + chữ trên nền nhạt.
     * TextView phải dùng background @drawable/bg_status_chip và drawableStart @drawable/ic_status_dot.
     */
    public static void bindChip(TextView chip, String status) {
        int fg = ContextCompat.getColor(chip.getContext(), textColor(status));
        int bg = ContextCompat.getColor(chip.getContext(), backgroundColor(status));
        chip.setText(label(status));
        chip.setTextColor(fg);
        chip.setBackgroundTintList(ColorStateList.valueOf(bg));
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(fg));
    }
}
