package com.seniortribe.app.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.seniortribe.app.activities.ChatActivity;
import com.seniortribe.app.activities.MainActivity;
import com.seniortribe.app.activities.NeedDetailActivity;
import com.seniortribe.app.activities.NeedProgressActivity;
import com.seniortribe.app.activities.RescheduleActivity;
import com.seniortribe.app.activities.ScheduleAgreementActivity;
import com.seniortribe.app.utils.Constants;

/**
 * E01 · Bấm thông báo thì mở đúng màn hình.
 *
 * Thông báo luôn đi qua MainActivity (rồi SessionGate nếu cần) để kiểm tra phiên đăng nhập và
 * quyền tổ chức trước (R01), sau đó MainActivity mới mở màn hình đích. Không bao giờ mở thẳng
 * màn hình chat / nhu cầu từ thông báo khi tài khoản chưa được xác nhận quyền.
 */
public final class DeepLink {

    public static final String EXTRA_TARGET = "extra_deeplink_target";

    private DeepLink() {}

    /** Intent mở MainActivity kèm đích đến, dùng làm PendingIntent của thông báo. */
    public static PendingIntent pendingIntent(Context context, int requestCode, NotificationRules.Target target,
                                              String needId, @Nullable String assignmentId, @Nullable String caseCode) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TARGET, target.name())
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, assignmentId)
                .putExtra(Constants.EXTRA_CASE_CODE, caseCode);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    /** Có đích đến trong intent không. */
    public static boolean has(@Nullable Intent intent) {
        return intent != null && intent.getStringExtra(EXTRA_TARGET) != null
                && intent.getStringExtra(Constants.EXTRA_NEED_ID) != null;
    }

    /** Chép đích đến sang intent khác (MainActivity → SessionGate → MainActivity). */
    public static void copy(@Nullable Intent from, Intent to) {
        if (!has(from)) return;
        Bundle b = new Bundle();
        b.putString(EXTRA_TARGET, from.getStringExtra(EXTRA_TARGET));
        b.putString(Constants.EXTRA_NEED_ID, from.getStringExtra(Constants.EXTRA_NEED_ID));
        b.putString(Constants.EXTRA_ASSIGNMENT_ID, from.getStringExtra(Constants.EXTRA_ASSIGNMENT_ID));
        b.putString(Constants.EXTRA_CASE_CODE, from.getStringExtra(Constants.EXTRA_CASE_CODE));
        to.putExtras(b);
    }

    /** Bỏ đích đến khỏi intent sau khi đã mở, để xoay màn hình không mở lại lần nữa. */
    public static void clear(@Nullable Intent intent) {
        if (intent == null) return;
        intent.removeExtra(EXTRA_TARGET);
    }

    /** Intent màn hình đích, hoặc null nếu thiếu dữ liệu. */
    @Nullable
    public static Intent targetIntent(Context context, Intent from) {
        if (!has(from)) return null;
        NotificationRules.Target target;
        try {
            target = NotificationRules.Target.valueOf(from.getStringExtra(EXTRA_TARGET));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String needId = from.getStringExtra(Constants.EXTRA_NEED_ID);
        String assignmentId = from.getStringExtra(Constants.EXTRA_ASSIGNMENT_ID);
        String caseCode = from.getStringExtra(Constants.EXTRA_CASE_CODE);

        Class<?> cls;
        switch (target) {
            case CHAT:
                if (assignmentId == null) cls = NeedDetailActivity.class;
                else cls = ChatActivity.class;
                break;
            case SCHEDULE: cls = ScheduleAgreementActivity.class; break;
            case RESCHEDULE: cls = RescheduleActivity.class; break;
            case PROGRESS: cls = NeedProgressActivity.class; break;
            case NEED:
            default: cls = NeedDetailActivity.class; break;
        }
        return new Intent(context, cls)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, assignmentId)
                .putExtra(Constants.EXTRA_CASE_CODE, caseCode);
    }
}
