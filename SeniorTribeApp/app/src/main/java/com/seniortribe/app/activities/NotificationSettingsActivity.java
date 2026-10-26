package com.seniortribe.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.notifications.AppNotifier;
import com.seniortribe.app.notifications.NotificationPrefs;
import com.seniortribe.app.notifications.NotifyCategory;

/**
 * E01 · Thông báo &amp; nhắc lịch: người dùng chọn loại thông báo muốn nhận, mốc nhắc trước giờ
 * hẹn và có hiện nội dung tin nhắn hay không. Tùy chọn lưu ngay khi bấm (SharedPreferences),
 * nên xoay màn hình không mất gì.
 */
public class NotificationSettingsActivity extends AppCompatActivity {

    private NotificationPrefs prefs;
    private View layoutPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        prefs = new NotificationPrefs(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        layoutPermission = findViewById(R.id.layoutPermission);
        findViewById(R.id.btnOpenSystem).setOnClickListener(v -> openSystemSettings());

        bind(R.id.swMessages, NotifyCategory.MESSAGES);
        bind(R.id.swSchedule, NotifyCategory.SCHEDULE);
        bind(R.id.swStatus, NotifyCategory.STATUS);
        bind(R.id.swFollowUp, NotifyCategory.FOLLOW_UP);

        MaterialSwitch day = findViewById(R.id.swRemindDay);
        day.setChecked(prefs.remindDayBefore());
        day.setOnCheckedChangeListener((b, on) -> {
            prefs.setRemindDayBefore(on);
            resync();
        });

        ChipGroup hours = findViewById(R.id.chipsHours);
        hours.check(chipFor(prefs.remindHoursBefore()));
        hours.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            int h = id == R.id.chipHours1 ? 1 : id == R.id.chipHours2 ? 2 : id == R.id.chipHours3 ? 3 : 0;
            if (h == prefs.remindHoursBefore()) return;
            prefs.setRemindHoursBefore(h);
            resync();
        });

        MaterialSwitch preview = findViewById(R.id.swPreview);
        preview.setChecked(prefs.showPreview());
        preview.setOnCheckedChangeListener((b, on) -> prefs.setShowPreview(on));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng có thể vừa bật / tắt quyền trong Cài đặt hệ thống
        layoutPermission.setVisibility(AppNotifier.canPost(this) ? View.GONE : View.VISIBLE);
    }

    private void bind(int id, NotifyCategory category) {
        MaterialSwitch sw = findViewById(id);
        sw.setChecked(prefs.isEnabled(category));
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setEnabled(category, on);
            if (category == NotifyCategory.SCHEDULE) resync(); // tắt lịch hẹn thì hủy luôn nhắc lịch
        });
    }

    private static int chipFor(int hours) {
        switch (hours) {
            case 1: return R.id.chipHours1;
            case 2: return R.id.chipHours2;
            case 3: return R.id.chipHours3;
            default: return R.id.chipHours0;
        }
    }

    private void resync() {
        ((SeniorTribeApplication) getApplication()).getNotificationCenter().resyncReminders();
    }

    private void openSystemSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }
}
