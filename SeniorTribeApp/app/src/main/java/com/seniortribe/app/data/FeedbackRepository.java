package com.seniortribe.app.data;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgRating;
import com.seniortribe.app.models.ReportNote;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E03 · Đánh giá sau hỗ trợ và xử lý phản ánh.
 *
 * Firestore:
 *   org_ratings/{assignmentId}                 một đánh giá cho mỗi lần tiếp nhận đã hoàn thành
 *   feedback_reports/{reportId}                phản ánh về một lần tiếp nhận
 *   feedback_reports/{reportId}/notes/{noteId} quá trình xử lý (bắt đầu, hỏi thêm, bổ sung, đóng)
 * Security Rules kiểm tra lại quyền và trạng thái trên máy chủ (R06).
 */
public class FeedbackRepository {

    public static final String COL_RATINGS = "org_ratings";
    public static final String COL_REPORTS = "feedback_reports";
    public static final String SUB_NOTES = "notes";

    public static final int MIN_DESCRIPTION = 10;
    public static final int MAX_TEXT = 1000;
    public static final int MAX_COMMENT = 500;
    public static final int MAX_TAGS = 6;

    private final FirebaseFirestore db;

    public FeedbackRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public FeedbackRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // =====================================================================
    // Đánh giá sau khi hoàn thành
    // =====================================================================

    /** Đầu mối ghi nhận đánh giá được khi nhu cầu đã hoàn thành và có lần tiếp nhận. */
    public static boolean canRate(@Nullable Membership me, @Nullable NeedSummary need) {
        return me != null && need != null
                && me.isRecorder() && me.belongsToRecorderOf(need)
                && Constants.STATUS_COMPLETED.equals(need.getStatus())
                && !TextUtils.isEmpty(need.getCurrentAssignmentId())
                && !TextUtils.isEmpty(need.getAssignedOrgId());
    }

    /** Đánh giá đã có của lần tiếp nhận; null nếu chưa ai đánh giá. */
    public Task<OrgRating> loadRating(String assignmentId) {
        return ratingRef(assignmentId).get().continueWith(task -> {
            if (!task.isSuccessful()) throw errorOf(task.getException());
            DocumentSnapshot snap = task.getResult();
            return snap != null && snap.exists() ? snap.toObject(OrgRating.class) : null;
        });
    }

    public Task<Void> submitRating(NeedSummary need, Membership me, int stars,
                                   List<String> tags, @Nullable String comment) {
        if (!canRate(me, need)) {
            return Tasks.forException(new NeedActionException(
                    "Chỉ đầu mối của đơn vị ghi nhận mới đánh giá được, sau khi nhu cầu hoàn thành."));
        }
        if (stars < 1 || stars > 5) {
            return Tasks.forException(new NeedActionException("Hãy chọn số sao theo ý kiến của cụ."));
        }
        String cleanComment = comment != null ? comment.trim() : "";
        if (cleanComment.length() > MAX_COMMENT) {
            return Tasks.forException(new NeedActionException("Nhận xét dài quá " + MAX_COMMENT + " ký tự."));
        }
        List<String> cleanTags = new ArrayList<>();
        if (tags != null) for (String t : tags) if (!TextUtils.isEmpty(t) && cleanTags.size() < MAX_TAGS) cleanTags.add(t);

        final DocumentReference ref = ratingRef(need.getCurrentAssignmentId());
        final Map<String, Object> data = new HashMap<>();
        data.put("needId", need.getNeedId());
        data.put("caseCode", need.getCaseCode());
        data.put("recorderOrgId", need.getRecorderOrgId());
        data.put("orgId", need.getAssignedOrgId());
        data.put("orgName", need.getAssignedOrgName());
        data.put("assigneeName", need.getAssigneeName());
        data.put("stars", stars);
        data.put("tags", cleanTags);
        data.put("comment", cleanComment.isEmpty() ? null : cleanComment);
        data.put("elderAsked", true);
        data.put("byUid", me.getUid());
        data.put("byName", me.getDisplayName());
        data.put("createdAt", FieldValue.serverTimestamp());

        return db.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(ref);
            if (existing.exists()) {
                throw new NeedActionException("Lần hỗ trợ này đã được đánh giá.");
            }
            tx.set(ref, data);
            return null;
        });
    }

    // =====================================================================
    // Gửi phản ánh
    // =====================================================================

    /** Bên liên quan đến lần tiếp nhận (đơn vị ghi nhận hoặc tổ chức tiếp nhận) mới gửi phản ánh được. */
    public static boolean canReport(@Nullable Membership me, @Nullable NeedSummary need) {
        return me != null && need != null
                && !TextUtils.isEmpty(need.getCurrentAssignmentId())
                && !TextUtils.isEmpty(need.getAssignedOrgId())
                && (me.belongsToRecorderOf(need) || me.belongsToAssignedOrgOf(need));
    }

    /** Tên tổ chức bị phản ánh: luôn là bên còn lại của lần tiếp nhận. */
    @Nullable
    public static String reportedOrgNameFor(Membership me, NeedSummary need) {
        if (me == null || need == null) return null;
        return me.belongsToRecorderOf(need) ? need.getAssignedOrgName() : need.getRecorderOrgName();
    }

    /** @return id phản ánh vừa tạo */
    public Task<String> submitReport(NeedSummary need, Membership me, String type, String description) {
        if (!canReport(me, need)) {
            return Tasks.forException(new NeedActionException(
                    "Chỉ đơn vị ghi nhận hoặc tổ chức đang phụ trách nhu cầu này mới gửi phản ánh được."));
        }
        if (!isKnownType(type)) {
            return Tasks.forException(new NeedActionException("Hãy chọn loại phản ánh."));
        }
        String clean = description != null ? description.trim() : "";
        if (clean.length() < MIN_DESCRIPTION) {
            return Tasks.forException(new NeedActionException(
                    "Hãy mô tả sự việc (ít nhất " + MIN_DESCRIPTION + " ký tự)."));
        }
        if (clean.length() > MAX_TEXT) {
            return Tasks.forException(new NeedActionException("Mô tả dài quá " + MAX_TEXT + " ký tự."));
        }
        boolean fromRecorder = me.belongsToRecorderOf(need);

        Map<String, Object> data = new HashMap<>();
        data.put("needId", need.getNeedId());
        data.put("caseCode", need.getCaseCode());
        data.put("assignmentId", need.getCurrentAssignmentId());
        data.put("recorderOrgId", need.getRecorderOrgId());
        data.put("reporterOrgId", me.getOrgId());
        data.put("reportedOrgId", fromRecorder ? need.getAssignedOrgId() : need.getRecorderOrgId());
        data.put("reportedOrgName", fromRecorder ? need.getAssignedOrgName() : need.getRecorderOrgName());
        data.put("type", type);
        data.put("description", clean);
        data.put("status", FeedbackReport.STATUS_NEW);
        data.put("createdByUid", me.getUid());
        data.put("createdByName", me.getDisplayName());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        final DocumentReference ref = db.collection(COL_REPORTS).document();
        return ref.set(data).continueWith(task -> {
            if (!task.isSuccessful()) throw errorOf(task.getException());
            return ref.getId();
        });
    }

    public static boolean isKnownType(@Nullable String type) {
        return FeedbackReport.TYPE_LATE.equals(type) || FeedbackReport.TYPE_ATTITUDE.equals(type)
                || FeedbackReport.TYPE_PRIVACY.equals(type) || FeedbackReport.TYPE_OTHER.equals(type);
    }

    // =====================================================================
    // Danh sách và chi tiết phản ánh
    // =====================================================================

    /** Phản ánh do chính tài khoản này gửi. */
    public ListenerRegistration listenMyReports(String uid, NeedRepository.Callback<List<FeedbackReport>> callback) {
        return listenReports(db.collection(COL_REPORTS).whereEqualTo("createdByUid", uid), callback);
    }

    /** Phản ánh điều phối viên của đơn vị cần xử lý. */
    public ListenerRegistration listenReportsToHandle(String orgId, NeedRepository.Callback<List<FeedbackReport>> callback) {
        return listenReports(db.collection(COL_REPORTS).whereEqualTo("recorderOrgId", orgId), callback);
    }

    public ListenerRegistration listenReport(String reportId, NeedRepository.Callback<FeedbackReport> callback) {
        return reportRef(reportId).addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null || !snap.exists()) {
                callback.onError(new NeedActionException("Không tìm thấy phản ánh."));
                return;
            }
            callback.onData(snap.toObject(FeedbackReport.class), snap.getMetadata().isFromCache());
        });
    }

    public ListenerRegistration listenNotes(String reportId, NeedRepository.Callback<List<ReportNote>> callback) {
        return reportRef(reportId).collection(SUB_NOTES)
                .orderBy("at", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<ReportNote> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ReportNote n = doc.toObject(ReportNote.class);
                        if (n != null) list.add(n);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    // =====================================================================
    // Điều phối viên xử lý
    // =====================================================================

    /** Điều phối viên của đơn vị ghi nhận, không phải người gửi phản ánh. */
    public static boolean canHandle(@Nullable Membership me, @Nullable FeedbackReport r) {
        return me != null && r != null && me.isReviewer()
                && me.getOrgId().equals(r.getRecorderOrgId())
                && !me.getUid().equals(r.getCreatedByUid());
    }

    public static boolean isReporter(@Nullable Membership me, @Nullable FeedbackReport r) {
        return me != null && r != null && me.getUid().equals(r.getCreatedByUid());
    }

    public Task<Void> startHandling(FeedbackReport r, Membership me) {
        if (!canHandle(me, r) || !r.isNew()) {
            return Tasks.forException(new NeedActionException("Phản ánh này không còn ở trạng thái chờ xử lý."));
        }
        WriteBatch batch = db.batch();
        Map<String, Object> update = new HashMap<>();
        update.put("status", FeedbackReport.STATUS_IN_PROGRESS);
        update.put("handlerUid", me.getUid());
        update.put("handlerName", me.getDisplayName());
        update.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(reportRef(r.getReportId()), update);
        batch.set(newNoteRef(r.getReportId()), note(ReportNote.KIND_START, "Tiếp nhận phản ánh", me, ReportNote.ROLE_REVIEWER));
        return batch.commit();
    }

    /** Điều phối viên hỏi thêm thông tin; người gửi trả lời bằng addInfo. */
    public Task<Void> askMore(FeedbackReport r, Membership me, String text) {
        if (!canHandle(me, r) || !FeedbackReport.STATUS_IN_PROGRESS.equals(r.getStatus())) {
            return Tasks.forException(new NeedActionException("Hãy tiếp nhận phản ánh trước khi hỏi thêm."));
        }
        String clean = cleanText(text);
        if (clean == null) return Tasks.forException(new NeedActionException("Hãy nhập nội dung cần hỏi."));
        return newNoteRef(r.getReportId()).set(note(ReportNote.KIND_ASK, clean, me, ReportNote.ROLE_REVIEWER));
    }

    public Task<Void> addInfo(FeedbackReport r, Membership me, String text) {
        if (!isReporter(me, r) || r.isClosed()) {
            return Tasks.forException(new NeedActionException("Phản ánh đã đóng, không bổ sung được nữa."));
        }
        String clean = cleanText(text);
        if (clean == null) return Tasks.forException(new NeedActionException("Hãy nhập nội dung bổ sung."));
        return newNoteRef(r.getReportId()).set(note(ReportNote.KIND_INFO, clean, me, ReportNote.ROLE_REPORTER));
    }

    public Task<Void> close(FeedbackReport r, Membership me, String resolution) {
        if (!canHandle(me, r) || !FeedbackReport.STATUS_IN_PROGRESS.equals(r.getStatus())) {
            return Tasks.forException(new NeedActionException("Hãy tiếp nhận phản ánh trước khi đóng."));
        }
        String clean = resolution != null ? resolution.trim() : "";
        if (clean.length() < 5) {
            return Tasks.forException(new NeedActionException("Hãy ghi kết quả xử lý (ít nhất 5 ký tự)."));
        }
        if (clean.length() > MAX_TEXT) {
            return Tasks.forException(new NeedActionException("Kết quả xử lý dài quá " + MAX_TEXT + " ký tự."));
        }
        WriteBatch batch = db.batch();
        Map<String, Object> update = new HashMap<>();
        update.put("status", FeedbackReport.STATUS_CLOSED);
        update.put("resolution", clean);
        update.put("closedAt", FieldValue.serverTimestamp());
        update.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(reportRef(r.getReportId()), update);
        batch.set(newNoteRef(r.getReportId()), note(ReportNote.KIND_CLOSE, clean, me, ReportNote.ROLE_REVIEWER));
        return batch.commit();
    }

    // =====================================================================
    // Hàm hỗ trợ
    // =====================================================================

    private ListenerRegistration listenReports(Query query, NeedRepository.Callback<List<FeedbackReport>> callback) {
        return query.addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null) return;
            List<FeedbackReport> list = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                FeedbackReport r = doc.toObject(FeedbackReport.class);
                if (r != null) list.add(r);
            }
            // Mới cập nhật lên trước; sắp xếp ở máy để không cần composite index
            Collections.sort(list, (a, b) -> Long.compare(b.getUpdatedAtMillis(), a.getUpdatedAtMillis()));
            callback.onData(list, snap.getMetadata().isFromCache());
        });
    }

    private static Map<String, Object> note(String kind, String text, Membership me, String role) {
        Map<String, Object> n = new HashMap<>();
        n.put("kind", kind);
        n.put("text", text);
        n.put("byUid", me.getUid());
        n.put("byName", me.getDisplayName());
        n.put("byRole", role);
        n.put("at", FieldValue.serverTimestamp());
        return n;
    }

    @Nullable
    private static String cleanText(String text) {
        String clean = text != null ? text.trim() : "";
        if (clean.isEmpty()) return null;
        return clean.length() > MAX_TEXT ? clean.substring(0, MAX_TEXT) : clean;
    }

    private DocumentReference ratingRef(String assignmentId) {
        return db.collection(COL_RATINGS).document(assignmentId);
    }

    private DocumentReference reportRef(String reportId) {
        return db.collection(COL_REPORTS).document(reportId);
    }

    private DocumentReference newNoteRef(String reportId) {
        return reportRef(reportId).collection(SUB_NOTES).document();
    }

    private static Exception errorOf(@Nullable Exception e) {
        return e != null ? e : new NeedActionException("Không kết nối được máy chủ. Vui lòng thử lại.");
    }
}
