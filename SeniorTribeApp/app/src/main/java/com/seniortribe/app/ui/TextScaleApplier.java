package com.seniortribe.app.ui;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * E08 · Áp dụng cỡ chữ người dùng chọn cho mọi màn hình mà không phải sửa từng Activity.
 *
 * Android tạo lại Activity khi cấu hình đổi; lúc Activity vừa được tạo (trước setContentView của
 * lớp con) ta đặt fontScale = cỡ chữ hệ thống × hệ số người dùng chọn, nên mọi chữ dùng đơn vị sp
 * (kể cả trong hộp thoại, danh sách) đều to / nhỏ theo. Khi người dùng đổi cỡ chữ, màn hình đang
 * mở được tạo lại lúc quay về (onResume). Cỡ chữ hệ thống luôn đọc từ Resources.getSystem() để
 * không nhân chồng nhiều lần.
 */
public final class TextScaleApplier implements Application.ActivityLifecycleCallbacks {

    private final DisplaySettings settings;
    /** Hệ số đã áp dụng cho từng Activity đang sống. */
    private final Map<Activity, Float> applied = new WeakHashMap<>();

    private TextScaleApplier(DisplaySettings settings) {
        this.settings = settings;
    }

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new TextScaleApplier(new DisplaySettings(app)));
    }

    /** fontScale cần dùng: cỡ chữ hệ thống (trợ năng) × hệ số chọn trong ứng dụng, tối đa 2.0. */
    public static float targetScale(float systemScale, float factor) {
        float s = systemScale * factor;
        return Math.max(0.8f, Math.min(s, 2.0f));
    }

    @SuppressWarnings("deprecation")
    private void apply(Activity activity) {
        float factor = settings.textFactor();
        Resources res = activity.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        float target = targetScale(Resources.getSystem().getConfiguration().fontScale, factor);
        if (config.fontScale != target) {
            config.fontScale = target;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        applied.put(activity, factor);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        apply(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Float was = applied.get(activity);
        if (was != null && was != settings.textFactor()) {
            activity.recreate(); // người dùng vừa đổi cỡ chữ ở màn hình khác
        }
    }

    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        applied.remove(activity);
    }
}
