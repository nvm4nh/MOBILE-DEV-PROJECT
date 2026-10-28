package com.seniortribe.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * E08 · Tùy chỉnh hiển thị lưu trên máy: cỡ chữ và giao diện sáng / tối.
 *
 * Cỡ chữ là hệ số nhân thêm vào cỡ chữ của hệ thống (vẫn tôn trọng cài đặt trợ năng của máy),
 * áp dụng cho mọi màn hình qua {@link TextScaleApplier}. Giao diện dùng AppCompatDelegate nên
 * bảng màu values-night/colors.xml được dùng khi chọn "Tối".
 */
public final class DisplaySettings {

    private static final String FILE = "display_settings";
    private static final String KEY_TEXT = "text_scale_level";
    private static final String KEY_THEME = "theme_mode";

    /** Các mức cỡ chữ: Nhỏ, Vừa (mặc định), Lớn, Rất lớn. */
    public static final float[] TEXT_FACTORS = {0.9f, 1.0f, 1.15f, 1.3f};
    public static final int TEXT_DEFAULT = 1;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private final SharedPreferences prefs;

    public DisplaySettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int textLevel() {
        int v = prefs.getInt(KEY_TEXT, TEXT_DEFAULT);
        return v < 0 || v >= TEXT_FACTORS.length ? TEXT_DEFAULT : v;
    }

    public void setTextLevel(int level) {
        prefs.edit().putInt(KEY_TEXT, Math.max(0, Math.min(level, TEXT_FACTORS.length - 1))).apply();
    }

    public float textFactor() {
        return TEXT_FACTORS[textLevel()];
    }

    public int themeMode() {
        int v = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        return v < THEME_SYSTEM || v > THEME_DARK ? THEME_SYSTEM : v;
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
        applyTheme(mode);
    }

    /** Áp dụng chế độ sáng / tối cho cả ứng dụng (Android tự tạo lại các màn hình đang mở). */
    public static void applyTheme(int mode) {
        int night;
        switch (mode) {
            case THEME_LIGHT: night = AppCompatDelegate.MODE_NIGHT_NO; break;
            case THEME_DARK: night = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) AppCompatDelegate.setDefaultNightMode(night);
    }

    /** Gọi một lần khi mở ứng dụng. */
    public void applyStoredTheme() {
        applyTheme(themeMode());
    }
}
