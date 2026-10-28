package com.seniortribe.app.data;

import android.location.Location;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.SosAlert;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E09 · Gửi và theo dõi báo khẩn trong needs_assignments/{assignmentId}/sos.
 * Chỉ hai người trong luồng chat đọc / ghi (Security Rules); mọi thao tác cần mạng.
 */
public class SosRepository {

    public static final String SUB_SOS = "sos";
    public static final List<String> HOTLINES = Arrays.asList("115", "113", "114");

    private final FirebaseFirestore db;

    public SosRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public SosRepository(FirebaseFirestore db) {
        this.db = db;
    }

    private CollectionReference sos(String assignmentId) {
        return db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId).collection(SUB_SOS);
    }

    /** Báo khẩn của một lần tiếp nhận, mới nhất trước. */
    public ListenerRegistration listen(String assignmentId, NeedRepository.Callback<List<SosAlert>> callback) {
        return sos(assignmentId).orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<SosAlert> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        SosAlert a = d.toObject(SosAlert.class);
                        if (a != null) list.add(a);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    /** Báo khẩn đang mở (chưa ai nhận) của một lần tiếp nhận, để hiện thông báo. */
    public Query openAlerts(String assignmentId) {
        return sos(assignmentId).whereEqualTo("status", SosAlert.OPEN);
    }

    public Task<Void> send(String assignmentId, Membership me, String kind, @Nullable String note,
                           @Nullable String calledNumber, @Nullable Location location) {
        String clean = note != null ? note.trim() : "";
        if (clean.length() > 300) return Tasks.forException(new NeedActionException("Ghi chú dài quá 300 ký tự."));
        Map<String, Object> m = new HashMap<>();
        m.put("byUid", me.getUid());
        m.put("byName", me.getDisplayName());
        m.put("kind", kind);
        m.put("note", clean);
        m.put("status", SosAlert.OPEN);
        m.put("createdAt", FieldValue.serverTimestamp());
        if (calledNumber != null && HOTLINES.contains(calledNumber)) m.put("calledNumber", calledNumber);
        if (location != null) {
            m.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
            if (location.hasAccuracy()) m.put("accuracyM", (double) location.getAccuracy());
        }
        return sos(assignmentId).document().set(m);
    }

    public Task<Void> acknowledge(String assignmentId, String alertId, Membership me) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", SosAlert.ACK);
        m.put("ackByUid", me.getUid());
        m.put("ackByName", me.getDisplayName());
        m.put("ackAt", FieldValue.serverTimestamp());
        return sos(assignmentId).document(alertId).update(m);
    }

    public Task<Void> resolve(String assignmentId, String alertId, Membership me, @Nullable String note) {
        String clean = note != null ? note.trim() : "";
        if (clean.length() > 300) return Tasks.forException(new NeedActionException("Ghi chú dài quá 300 ký tự."));
        Map<String, Object> m = new HashMap<>();
        m.put("status", SosAlert.RESOLVED);
        m.put("resolvedByUid", me.getUid());
        m.put("resolvedByName", me.getDisplayName());
        m.put("resolvedAt", FieldValue.serverTimestamp());
        m.put("resolveNote", clean);
        return sos(assignmentId).document(alertId).update(m);
    }
}
