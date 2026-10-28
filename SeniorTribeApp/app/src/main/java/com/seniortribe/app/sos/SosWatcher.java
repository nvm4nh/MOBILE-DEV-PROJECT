package com.seniortribe.app.sos;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.SosActivity;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.data.SosRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E09 · Khi ứng dụng đang chạy, theo dõi báo khẩn mới của các lần phối hợp mà mình là một trong
 * hai người (đầu mối đã ghi nhận / người được giao) và hiện thông báo mức cao trên kênh "Khẩn cấp".
 * Mỗi báo khẩn chỉ báo một lần; báo khẩn của chính mình hoặc đã quá 30 phút thì bỏ qua.
 * Đăng xuất thì dừng và xóa danh sách đã báo.
 */
public final class SosWatcher {

    private static final String CHANNEL = "st_sos";
    private static final String PREFS = "sos_watcher";
    private static final long MAX_AGE_MS = 30 * 60 * 1000;

    @Nullable private static SosWatcher instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final NeedRepository needs = new NeedRepository();
    private final SosRepository sos = new SosRepository();
    private final List<ListenerRegistration> base = new ArrayList<>();
    private final Map<String, ListenerRegistration> alertListeners = new HashMap<>();
    private final Map<String, NeedSummary> recorded = new LinkedHashMap<>();
    private final Map<String, NeedSummary> assigned = new LinkedHashMap<>();
    @Nullable private String uid;
    private long generation;

    private SosWatcher(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        createChannel(this.context);
        FirebaseAuth.getInstance().addAuthStateListener(a -> {
            String now = a.getCurrentUser() != null ? a.getCurrentUser().getUid() : null;
            if (uid != null && !uid.equals(now)) {
                stop();
                prefs.edit().clear().apply();
            }
        });
    }

    @MainThread
    public static SosWatcher get(Context context) {
        if (instance == null) instance = new SosWatcher(context);
        return instance;
    }

    /** Bắt đầu theo dõi cho tài khoản hiện tại (gọi nhiều lần không sao). */
    @MainThread
    public void start() {
        String current = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (current == null) return;
        if (current.equals(uid) && !base.isEmpty()) return;
        stop();
        uid = current;
        final long request = ++generation;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            if (request != generation) return;
            Membership me = t.isSuccessful() ? t.getResult() : null;
            if (me == null || !me.getUid().equals(uid)) return;
            base.add(needs.listenRecordedBy(me.getOrgId(), callback(recorded)));
            base.add(needs.listenAssignedTo(me.getOrgId(), callback(assigned)));
        });
    }

    @MainThread
    public void stop() {
        ++generation;
        for (ListenerRegistration r : base) r.remove();
        base.clear();
        for (ListenerRegistration r : alertListeners.values()) r.remove();
        alertListeners.clear();
        recorded.clear();
        assigned.clear();
    }

    private NeedRepository.Callback<List<NeedSummary>> callback(Map<String, NeedSummary> target) {
        final long request = generation;
        return new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                if (request != generation) return;
                target.clear();
                for (NeedSummary n : data) target.put(n.getNeedId(), n);
                refresh();
            }

            @Override public void onError(Exception error) {
                // Mất quyền / mất mạng: thử lại ở lần mở ứng dụng sau
            }
        };
    }

    private void refresh() {
        Map<String, NeedSummary> all = new LinkedHashMap<>(recorded);
        all.putAll(assigned);
        Set<String> want = new HashSet<>();
        for (NeedSummary n : all.values()) {
            String a = n.getCurrentAssignmentId();
            if (a == null || !n.isCoordinating()) continue;
            if (uid == null || (!uid.equals(n.getRecorderUid()) && !uid.equals(n.getAssigneeUid()))) continue;
            want.add(a);
            if (!alertListeners.containsKey(a)) alertListeners.put(a, listen(n.getNeedId(), a));
        }
        List<String> drop = new ArrayList<>();
        for (String k : alertListeners.keySet()) if (!want.contains(k)) drop.add(k);
        for (String k : drop) {
            ListenerRegistration r = alertListeners.remove(k);
            if (r != null) r.remove();
        }
    }

    private ListenerRegistration listen(String needId, String assignmentId) {
        final long request = generation;
        return sos.openAlerts(assignmentId).addSnapshotListener((snap, error) -> {
            if (request != generation || snap == null) return;
            NeedSummary n = assigned.containsKey(needId) ? assigned.get(needId) : recorded.get(needId);
            for (DocumentSnapshot d : snap.getDocuments()) notifyIfNew(n, needId, d);
        });
    }

    private void notifyIfNew(@Nullable NeedSummary n, String needId, DocumentSnapshot d) {
        if (uid == null || uid.equals(d.getString("byUid"))) return;
        Timestamp at = d.getTimestamp("createdAt");
        if (at == null || System.currentTimeMillis() - at.toDate().getTime() > MAX_AGE_MS) return;
        String key = "seen_" + d.getId();
        if (prefs.getBoolean(key, false)) return;
        prefs.edit().putBoolean(key, true).apply();
        if (!canPost()) return;

        String code = n != null && n.getCaseCode() != null ? n.getCaseCode() : "";
        String by = d.getString("byName") != null ? d.getString("byName") : context.getString(R.string.sos_someone);
        String kind = context.getString(SosActivity.kindLabel(d.getString("kind")));
        String note = d.getString("note");
        String text = context.getString(R.string.sos_notif_text, by, kind)
                + (note != null && !note.isEmpty() ? " — " + note : "");

        Intent open = new Intent(context, SosActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap = PendingIntent.getActivity(context, d.getId().hashCode(), open, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sos)
                .setColor(ContextCompat.getColor(context, R.color.sos_red))
                .setContentTitle(context.getString(R.string.sos_notif_title, code))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(tap);
        try {
            NotificationManagerCompat.from(context).notify("sos_" + d.getId(), 0, b.build());
        } catch (SecurityException ignored) {
            // Người dùng chưa cho phép thông báo
        }
    }

    private boolean canPost() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.sos_channel),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.sos_channel_desc));
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(ch);
    }
}
