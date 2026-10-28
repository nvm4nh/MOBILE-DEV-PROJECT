package com.seniortribe.app.activities;

import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.seniortribe.app.R;
import com.seniortribe.app.ui.DisplaySettings;

/**
 * E08 · Cỡ chữ &amp; giao diện. Đổi cỡ chữ thì màn hình này tạo lại ngay để thấy kết quả; các màn
 * hình khác tự tạo lại khi quay về (TextScaleApplier). Đổi giao diện sáng / tối thì AppCompat tự
 * tạo lại mọi màn hình đang mở. Tùy chọn lưu trên máy, không cần mạng.
 */
public class DisplaySettingsActivity extends AppCompatActivity {

    private static final int[] TEXT_BUTTONS = {
            R.id.btnTextSmall, R.id.btnTextNormal, R.id.btnTextLarge, R.id.btnTextHuge
    };

    private DisplaySettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_settings);
        settings = new DisplaySettings(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButtonToggleGroup sizes = findViewById(R.id.toggleTextSize);
        sizes.check(TEXT_BUTTONS[settings.textLevel()]);
        sizes.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < TEXT_BUTTONS.length; i++) {
                if (TEXT_BUTTONS[i] == id && i != settings.textLevel()) {
                    settings.setTextLevel(i);
                    recreate(); // áp dụng ngay để người dùng thấy cỡ chữ mới
                    return;
                }
            }
        });

        RadioGroup theme = findViewById(R.id.groupTheme);
        int mode = settings.themeMode();
        theme.check(mode == DisplaySettings.THEME_DARK ? R.id.radioThemeDark
                : mode == DisplaySettings.THEME_LIGHT ? R.id.radioThemeLight : R.id.radioThemeSystem);
        theme.setOnCheckedChangeListener((g, id) -> {
            int m = id == R.id.radioThemeDark ? DisplaySettings.THEME_DARK
                    : id == R.id.radioThemeLight ? DisplaySettings.THEME_LIGHT : DisplaySettings.THEME_SYSTEM;
            if (m != settings.themeMode()) settings.setThemeMode(m);
        });
    }
}
