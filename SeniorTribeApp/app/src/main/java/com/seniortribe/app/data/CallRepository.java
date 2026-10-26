package com.seniortribe.app.data;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.seniortribe.app.models.CallRecord;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E06 · Trao đổi tín hiệu WebRTC qua Firestore cho cuộc gọi thoại / video.
 *
 * needs_assignments/{a}/calls/{callId}: người gọi, người nghe, offer, answer, trạng thái.
 * needs_assignments/{a}/calls/{callId}/candidates: ICE candidate của hai bên.
 * Âm thanh, hình ảnh đi thẳng giữa hai máy (qua STUN/TURN), không đi qua Firestore và không
 * được lưu lại. Security Rules chỉ cho hai người trong luồng chat gọi nhau khi lần tiếp nhận
 * còn hiệu lực (R08).
 */
public class CallRepository {

    public static final String SUB_CALLS = "calls";
    private static final String SUB_CANDIDATES = "candidates";

    private final FirebaseFirestore db;

    public CallRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public CallRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public CollectionReference calls(String assignmentId) {
        return db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId).collection(SUB_CALLS);
    }

    public DocumentReference newCallRef(String assignmentId) {
        return calls(assignmentId).document();
    }

    public DocumentReference callRef(String assignmentId, String callId) {
        return calls(assignmentId).document(callId);
    }

    /** Các cuộc gọi của luồng chat, để hiện nhật ký và phát hiện cuộc gọi đến. */
    public ListenerRegistration listenCalls(String assignmentId, NeedRepository.Callback<List<CallRecord>> callback) {
        return calls(assignmentId).orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<CallRecord> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        CallRecord c = d.toObject(CallRecord.class);
                        if (c != null) list.add(c);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    public Task<Void> createCall(DocumentReference ref, Membership me, String calleeUid, boolean video, String offerSdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("callerUid", me.getUid());
        m.put("callerName", me.getDisplayName());
        m.put("calleeUid", calleeUid);
        m.put("video", video);
        m.put("status", CallRecord.RINGING);
        m.put("offer", sdp("offer", offerSdp));
        m.put("createdAt", FieldValue.serverTimestamp());
        return ref.set(m);
    }

    public Task<Void> answer(DocumentReference ref, String answerSdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", CallRecord.ACCEPTED);
        m.put("answer", sdp("answer", answerSdp));
        m.put("answeredAt", FieldValue.serverTimestamp());
        return ref.update(m);
    }

    /** Kết thúc / từ chối / hủy / gọi nhỡ, kèm thời lượng nếu cuộc gọi đã kết nối. */
    public Task<Void> end(DocumentReference ref, Membership me, String status, @Nullable Long durationSec) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("endedByUid", me.getUid());
        m.put("endedAt", FieldValue.serverTimestamp());
        if (durationSec != null) m.put("durationSec", durationSec);
        return ref.update(m);
    }

    public Task<Void> addCandidate(DocumentReference callRef, Membership me, String candidate, String sdpMid, int index) {
        Map<String, Object> m = new HashMap<>();
        m.put("fromUid", me.getUid());
        m.put("candidate", candidate);
        m.put("sdpMid", sdpMid != null ? sdpMid : "");
        m.put("sdpMLineIndex", (long) index);
        m.put("at", FieldValue.serverTimestamp());
        return callRef.collection(SUB_CANDIDATES).document().set(m);
    }

    public Query candidates(DocumentReference callRef) {
        return callRef.collection(SUB_CANDIDATES);
    }

    private static Map<String, Object> sdp(String type, String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("sdp", sdp);
        return m;
    }
}
