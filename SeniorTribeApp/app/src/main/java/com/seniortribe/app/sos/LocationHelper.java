package com.seniortribe.app.sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * E09 · Lấy vị trí hiện tại một lần (không theo dõi liên tục) để gửi kèm báo khẩn.
 * Dùng LocationManager của Android, không cần thêm thư viện. Ưu tiên vị trí gần nhất trong 2 phút;
 * không có thì chờ một lần cập nhật tối đa 10 giây; vẫn không có thì gửi không kèm vị trí.
 */
public final class LocationHelper {

    public interface Callback {
        void onLocation(@Nullable Location location);
    }

    private static final long FRESH_MS = 2 * 60 * 1000;
    private static final long TIMEOUT_MS = 10_000;

    private LocationHelper() {}

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void getOnce(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !hasPermission(app)) {
            callback.onLocation(null);
            return;
        }
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Nhà cung cấp vị trí không có trên máy
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < FRESH_MS) {
            callback.onLocation(best);
            return;
        }
        final Location fallback = best;
        final Handler main = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        final LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                if (done[0]) return;
                done[0] = true;
                lm.removeUpdates(this);
                callback.onLocation(location);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };
        boolean requested = false;
        for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 0, 0, listener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
                // bỏ qua nhà cung cấp lỗi
            }
        }
        if (!requested) {
            callback.onLocation(fallback);
            return;
        }
        main.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            lm.removeUpdates(listener);
            callback.onLocation(fallback); // hết giờ: dùng vị trí cũ nhất có được (có thể null)
        }, TIMEOUT_MS);
    }
}
