package com.seniortribe.app.data;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedForm;
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgMember;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Toàn bộ thao tác Firestore cho nhu cầu hỗ trợ (F06–F10, F14).
 *
 * Mọi thao tác đổi trạng thái đều chạy trong transaction: đọc trạng thái hiện tại trên
 * máy chủ, kiểm tra điều kiện, rồi mới ghi. Transaction không chạy được khi offline,
 * nên ứng dụng không bao giờ báo "đã tiếp nhận" khi chưa được máy chủ xác nhận (R04, R06).
 * Security Rules (firebase/firestore.rules) kiểm tra lại cùng các điều kiện này.
 */
public class NeedRepository {

    // Trạng thái của một lần tiếp nhận (collection needs_assignments)
    public static final String ASSIGNMENT_ACTIVE = "ACTIVE";
    public static final String ASSIGNMENT_RETURNED = "RETURNED";
    public static final String ASSIGNMENT_CLOSED = "CLOSED";

    // Loại sự kiện ghi vào needs_summary/{id}/events để xem lịch sử
    public static final String EVENT_SUBMITTED = "SUBMITTED";
    public static final String EVENT_RESUBMITTED = "RESUBMITTED";
    public static final String EVENT_APPROVED = "APPROVED";
    public static final String EVENT_REVISION_REQUESTED = "REVISION_REQUESTED";
    public static final String EVENT_ACCEPTED = "ACCEPTED";
    public static final String EVENT_RETURNED = "RETURNED";
    public static final String EVENT_CANCELLED = "CANCELLED";

    private static final String COUNTER_DOC = "needs";

    /** Nhận dữ liệu realtime từ Firestore. */
    public interface Callback<T> {
        void onData(T data, boolean fromCache);

        void onError(Exception error);
    }

    private final FirebaseFirestore db;

    public NeedRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public NeedRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // =====================================================================
    // F06 · Ghi nhận nhu cầu
    // =====================================================================

    /**
     * Tạo nhu cầu mới ở trạng thái REVIEW. Mã NC-xxxx lấy từ bộ đếm counters/needs
     * trong cùng transaction nên hai đầu mối gửi cùng lúc vẫn không bị trùng mã.
     *
     * @return mã trường hợp vừa tạo
     */
    public Task<String> createNeed(Membership me, NeedForm form) {
        if (!me.isRecorder()) {
            return Tasks.forException(new NeedActionException("Chỉ đầu mối ghi nhận mới tạo được nhu cầu."));
        }
        final DocumentReference counterRef = db.collection(Constants.COL_COUNTERS).document(COUNTER_DOC);
        final DocumentReference needRef = db.collection(Constants.COL_NEEDS_SUMMARY).document();

        return db.runTransaction(tx -> {
            DocumentSnapshot counter = tx.get(counterRef);
            Long stored = counter.exists() ? counter.getLong("next") : null;
            long number = stored != null ? stored : 1L;
            String caseCode = String.format(Locale.US, "NC-%04d", number);

            Map<String, Object> summary = summaryFields(form);
            summary.put("caseCode", caseCode);
            summary.put("status", Constants.STATUS_REVIEW);
            summary.put("needsRevision", false);
            summary.put("reviewNote", null);
            summary.put("recorderOrgId", me.getOrgId());
            summary.put("recorderOrgName", me.getOrgName());
            summary.put("recorderUid", me.getUid());
            summary.put("recorderName", me.getDisplayName());
            summary.put("assignedOrgId", null);
            summary.put("assignedOrgName", null);
            summary.put("assigneeUid", null);
            summary.put("assigneeName", null);
            summary.put("currentAssignmentId", null);
            summary.put("returnCount", 0L);
            summary.put("createdAt", FieldValue.serverTimestamp());
            summary.put("updatedAt", FieldValue.serverTimestamp());
            summary.put("submittedAt", FieldValue.serverTimestamp());

            Map<String, Object> counterData = new HashMap<>();
            counterData.put("next", number + 1);

            tx.set(counterRef, counterData);
            tx.set(needRef, summary);
            tx.set(privateRef(needRef.getId()), privateFields(form, me.getOrgId()));
            tx.set(newEventRef(needRef), event(EVENT_SUBMITTED, null, Constants.STATUS_REVIEW, me, null));
            return caseCode;
        });
    }

    /**
     * Đầu mối sửa nhu cầu khi chưa có tổ chức nhận (F08). Sửa xong luôn đưa về REVIEW
     * để điều phối viên rà soát lại, kể cả nhu cầu đang OPEN.
     */
    public Task<Void> updateNeed(String needId, Membership me, NeedForm form) {
        final DocumentReference ref = summaryRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            requireRecorder(me, snap);
            String status = snap.getString("status");
            if (!Constants.STATUS_REVIEW.equals(status) && !Constants.STATUS_OPEN.equals(status)) {
                throw new NeedActionException("Nhu cầu đã có tổ chức tiếp nhận nên không sửa được nữa.");
            }

            Map<String, Object> update = summaryFields(form);
            update.put("status", Constants.STATUS_REVIEW);
            update.put("needsRevision", false);
            update.put("updatedAt", FieldValue.serverTimestamp());
            update.put("submittedAt", FieldValue.serverTimestamp());

            tx.update(ref, update);
            tx.set(privateRef(needId), privateFields(form, me.getOrgId()));
            tx.set(newEventRef(ref), event(EVENT_RESUBMITTED, status, Constants.STATUS_REVIEW, me, null));
            return null;
        });
    }

    // =====================================================================
    // F07 · Rà soát nhu cầu
    // =====================================================================

    public Task<Void> approve(String needId, Membership me) {
        final DocumentReference ref = summaryRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            requireReviewer(me, snap);

            Map<String, Object> update = reviewFields(me);
            update.put("status", Constants.STATUS_OPEN);
            update.put("needsRevision", false);

            tx.update(ref, update);
            tx.set(newEventRef(ref), event(EVENT_APPROVED, Constants.STATUS_REVIEW, Constants.STATUS_OPEN, me, null));
            return null;
        });
    }

    public Task<Void> requestRevision(String needId, Membership me, String note) {
        if (note == null || note.trim().isEmpty()) {
            return Tasks.forException(new NeedActionException("Hãy ghi rõ nội dung cần bổ sung cho đầu mối."));
        }
        final String cleanNote = note.trim();
        final DocumentReference ref = summaryRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            requireReviewer(me, snap);

            Map<String, Object> update = reviewFields(me);
            update.put("needsRevision", true);
            update.put("reviewNote", cleanNote);

            tx.update(ref, update);
            tx.set(newEventRef(ref),
                    event(EVENT_REVISION_REQUESTED, Constants.STATUS_REVIEW, Constants.STATUS_REVIEW, me, cleanNote));
            return null;
        });
    }

    // =====================================================================
    // F10 · Tiếp nhận nhu cầu
    // =====================================================================

    /**
     * Một tổ chức nhận nhu cầu và giao cho một đại diện. Transaction đọc lại trạng thái:
     * nếu tổ chức khác vừa nhận trước thì báo lỗi, không ghi đè (chống nhận trùng).
     */
    public Task<Void> accept(String needId, Membership me, OrgMember assignee) {
        if (!me.isRepresentative()) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện tổ chức mới tiếp nhận được nhu cầu."));
        }
        final DocumentReference ref = summaryRef(needId);
        final DocumentReference assignmentRef = db.collection(Constants.COL_ASSIGNMENTS).document();

        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            if (!Constants.STATUS_OPEN.equals(snap.getString("status")) || snap.getString("currentAssignmentId") != null) {
                throw new NeedActionException("Nhu cầu này vừa được tổ chức khác tiếp nhận hoặc không còn chờ tiếp nhận.");
            }

            Map<String, Object> assignment = new HashMap<>();
            assignment.put("needId", needId);
            assignment.put("caseCode", snap.getString("caseCode"));
            assignment.put("recorderOrgId", snap.getString("recorderOrgId"));
            assignment.put("orgId", me.getOrgId());
            assignment.put("orgName", me.getOrgName());
            assignment.put("assigneeUid", assignee.getUid());
            assignment.put("assigneeName", assignee.getDisplayName());
            assignment.put("acceptedByUid", me.getUid());
            assignment.put("status", ASSIGNMENT_ACTIVE);
            assignment.put("acceptedAt", FieldValue.serverTimestamp());
            assignment.put("endedAt", null);
            assignment.put("endReason", null);
            assignment.put("endNote", null);
            assignment.put("endedByUid", null);

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_ACCEPTED);
            update.put("assignedOrgId", me.getOrgId());
            update.put("assignedOrgName", me.getOrgName());
            update.put("assigneeUid", assignee.getUid());
            update.put("assigneeName", assignee.getDisplayName());
            update.put("acceptedByUid", me.getUid());
            update.put("currentAssignmentId", assignmentRef.getId());
            update.put("acceptedAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(ref, update);
            tx.set(assignmentRef, assignment);
            tx.set(newEventRef(ref), event(EVENT_ACCEPTED, Constants.STATUS_OPEN, Constants.STATUS_ACCEPTED, me,
                    "Phụ trách: " + assignee.getDisplayName()));
            return null;
        });
    }

    // =====================================================================
    // F14 · Trả lại / hủy nhu cầu
    // =====================================================================

    /**
     * Tổ chức không thực hiện được thì trả nhu cầu về OPEN. Lần tiếp nhận cũ được đánh dấu
     * RETURNED và vẫn giữ trong lịch sử (R07). Chỉ trả lại trước khi báo đã hỗ trợ.
     */
    public Task<Void> returnNeed(String needId, Membership me, String reason, @Nullable String note) {
        if (reason == null || reason.trim().isEmpty()) {
            return Tasks.forException(new NeedActionException("Hãy chọn lý do trả lại."));
        }
        final DocumentReference ref = summaryRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            String status = snap.getString("status");
            if (!me.isRepresentative() || !me.getOrgId().equals(snap.getString("assignedOrgId"))) {
                throw new NeedActionException("Chỉ đại diện của tổ chức đang phụ trách mới trả lại được.");
            }
            if (!Constants.STATUS_ACCEPTED.equals(status) && !Constants.STATUS_SCHEDULED.equals(status)) {
                throw new NeedActionException("Chỉ trả lại được khi chưa báo đã hỗ trợ.");
            }
            String assignmentId = snap.getString("currentAssignmentId");

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_OPEN);
            update.put("assignedOrgId", null);
            update.put("assignedOrgName", null);
            update.put("assigneeUid", null);
            update.put("assigneeName", null);
            update.put("acceptedByUid", null);
            update.put("currentAssignmentId", null);
            update.put("acceptedAt", null);
            update.put("returnCount", FieldValue.increment(1));
            update.put("lastReturnReason", reason);
            // Lịch đã chốt với tổ chức cũ hết hiệu lực khi trả lại
            update.put("scheduleId", null);
            update.put("scheduledAt", null);
            update.put("scheduledPlace", null);
            update.put("scheduledByUid", null);
            update.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ref, update);

            if (assignmentId != null) {
                tx.update(db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId),
                        endAssignment(ASSIGNMENT_RETURNED, reason, note, me));
            }
            tx.set(newEventRef(ref), event(EVENT_RETURNED, status, Constants.STATUS_OPEN, me, joinReason(reason, note)));
            return null;
        });
    }

    /** Đầu mối hủy nhu cầu theo yêu cầu thực tế, bắt buộc có lý do. */
    public Task<Void> cancel(String needId, Membership me, String reason, @Nullable String note) {
        if (reason == null || reason.trim().isEmpty()) {
            return Tasks.forException(new NeedActionException("Hãy chọn lý do hủy."));
        }
        final DocumentReference ref = summaryRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            requireExists(snap);
            requireRecorder(me, snap);
            String status = snap.getString("status");
            if (Constants.STATUS_COMPLETED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                throw new NeedActionException("Nhu cầu đã kết thúc nên không hủy được.");
            }
            String assignmentId = snap.getString("currentAssignmentId");

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_CANCELLED);
            update.put("cancelReason", reason);
            update.put("cancelNote", note != null && !note.trim().isEmpty() ? note.trim() : null);
            update.put("cancelledByUid", me.getUid());
            update.put("cancelledAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ref, update);

            if (assignmentId != null) {
                tx.update(db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId),
                        endAssignment(ASSIGNMENT_CLOSED, "Đầu mối hủy nhu cầu: " + reason, note, me));
            }
            tx.set(newEventRef(ref), event(EVENT_CANCELLED, status, Constants.STATUS_CANCELLED, me, joinReason(reason, note)));
            return null;
        });
    }

    // =====================================================================
    // F08, F09 · Danh sách nhu cầu (realtime)
    // =====================================================================

    /** Nhu cầu do đơn vị ghi nhận (mọi trạng thái). */
    public ListenerRegistration listenRecordedBy(String orgId, Callback<List<NeedSummary>> callback) {
        return listenQuery(db.collection(Constants.COL_NEEDS_SUMMARY).whereEqualTo("recorderOrgId", orgId), callback);
    }

    /** Nhu cầu đơn vị đang hoặc đã tiếp nhận. */
    public ListenerRegistration listenAssignedTo(String orgId, Callback<List<NeedSummary>> callback) {
        return listenQuery(db.collection(Constants.COL_NEEDS_SUMMARY).whereEqualTo("assignedOrgId", orgId), callback);
    }

    /** Nhu cầu đã duyệt, đang chờ tiếp nhận (F09). Chỉ bản tóm tắt. */
    public ListenerRegistration listenOpenNeeds(Callback<List<NeedSummary>> callback) {
        return listenQuery(db.collection(Constants.COL_NEEDS_SUMMARY)
                .whereEqualTo("status", Constants.STATUS_OPEN), callback);
    }

    /** Theo dõi một nhu cầu để màn hình chi tiết tự cập nhật khi trạng thái đổi. */
    public ListenerRegistration listenNeed(String needId, Callback<NeedSummary> callback) {
        return summaryRef(needId).addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null || !snap.exists()) {
                callback.onError(new NeedActionException("Không tìm thấy nhu cầu."));
                return;
            }
            callback.onData(snap.toObject(NeedSummary.class), snap.getMetadata().isFromCache());
        });
    }

    public Task<NeedSummary> loadNeed(String needId) {
        return summaryRef(needId).get().continueWith(task -> {
            if (!task.isSuccessful()) throw errorOf(task.getException());
            DocumentSnapshot snap = task.getResult();
            if (!snap.exists()) throw new NeedActionException("Không tìm thấy nhu cầu.");
            return snap.toObject(NeedSummary.class);
        });
    }

    /** Đọc thông tin riêng. Máy chủ từ chối nếu tài khoản không có quyền xem. */
    public Task<NeedPrivateDetails> loadPrivate(String needId) {
        return privateRef(needId).get().continueWith(task -> {
            if (!task.isSuccessful()) throw errorOf(task.getException());
            DocumentSnapshot snap = task.getResult();
            return snap.exists() ? snap.toObject(NeedPrivateDetails.class) : null;
        });
    }

    // =====================================================================
    // Hàm hỗ trợ
    // =====================================================================

    private ListenerRegistration listenQuery(Query query, Callback<List<NeedSummary>> callback) {
        return query.addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null) return;
            List<NeedSummary> list = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                NeedSummary need = doc.toObject(NeedSummary.class);
                if (need != null) list.add(need);
            }
            sortNewestFirst(list);
            callback.onData(list, snap.getMetadata().isFromCache());
        });
    }

    /** Sắp xếp ở máy để không phải tạo composite index cho mỗi truy vấn. */
    public static void sortNewestFirst(List<NeedSummary> list) {
        Collections.sort(list, (a, b) -> {
            long ta = a.getCreatedAtMillis() == 0 ? Long.MAX_VALUE : a.getCreatedAtMillis();
            long tb = b.getCreatedAtMillis() == 0 ? Long.MAX_VALUE : b.getCreatedAtMillis();
            return Long.compare(tb, ta);
        });
    }

    private DocumentReference summaryRef(String needId) {
        return db.collection(Constants.COL_NEEDS_SUMMARY).document(needId);
    }

    private DocumentReference privateRef(String needId) {
        return db.collection(Constants.COL_NEEDS_PRIVATE).document(needId);
    }

    static DocumentReference newEventRef(DocumentReference needRef) {
        return needRef.collection(Constants.SUB_EVENTS).document();
    }

    private static Map<String, Object> summaryFields(NeedForm f) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", f.title.trim());
        m.put("topic", f.topic.trim());
        m.put("content", f.content.trim());
        m.put("area", f.area.trim());
        m.put("desiredTime", f.desiredTime.trim());
        m.put("source", f.source.trim());
        m.put("consentRecorded", f.consentRecorded);
        m.put("consentMethod", f.consentMethod.trim());
        return m;
    }

    private static Map<String, Object> privateFields(NeedForm f, String recorderOrgId) {
        Map<String, Object> m = new HashMap<>();
        m.put("elderlyName", f.elderlyName.trim());
        m.put("contact", f.contact.trim());
        m.put("address", f.address != null ? f.address.trim() : "");
        m.put("recorderOrgId", recorderOrgId);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    private static Map<String, Object> reviewFields(Membership me) {
        Map<String, Object> m = new HashMap<>();
        m.put("reviewerUid", me.getUid());
        m.put("reviewerName", me.getDisplayName());
        m.put("reviewedAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    static Map<String, Object> endAssignment(String status, String reason, @Nullable String note, Membership me) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("endReason", reason);
        m.put("endNote", note != null && !note.trim().isEmpty() ? note.trim() : null);
        m.put("endedByUid", me.getUid());
        m.put("endedAt", FieldValue.serverTimestamp());
        return m;
    }

    static Map<String, Object> event(String type, @Nullable String from, String to, Membership me, @Nullable String note) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("fromStatus", from);
        m.put("toStatus", to);
        m.put("byUid", me.getUid());
        m.put("byName", me.getDisplayName());
        m.put("byOrgId", me.getOrgId());
        m.put("note", note);
        m.put("at", FieldValue.serverTimestamp());
        return m;
    }

    private static String joinReason(String reason, @Nullable String note) {
        return note != null && !note.trim().isEmpty() ? reason + " — " + note.trim() : reason;
    }

    private static void requireExists(DocumentSnapshot snap) {
        if (!snap.exists()) throw new NeedActionException("Không tìm thấy nhu cầu.");
    }

    private static void requireRecorder(Membership me, DocumentSnapshot snap) {
        if (!me.isRecorder() || !me.getOrgId().equals(snap.getString("recorderOrgId"))) {
            throw new NeedActionException("Chỉ đầu mối của đơn vị ghi nhận mới thực hiện được thao tác này.");
        }
    }

    private static void requireReviewer(Membership me, DocumentSnapshot snap) {
        if (!me.isReviewer() || !me.getOrgId().equals(snap.getString("recorderOrgId"))) {
            throw new NeedActionException("Chỉ điều phối viên của đơn vị ghi nhận mới rà soát được nhu cầu này.");
        }
        if (me.getUid().equals(snap.getString("recorderUid"))) {
            throw new NeedActionException("Bạn không thể tự duyệt nhu cầu do chính mình ghi nhận.");
        }
        if (!Constants.STATUS_REVIEW.equals(snap.getString("status"))) {
            throw new NeedActionException("Nhu cầu không còn ở trạng thái chờ rà soát.");
        }
    }

    private static Exception errorOf(@Nullable Exception e) {
        return e != null ? e : new NeedActionException("Không đọc được dữ liệu.");
    }
}
