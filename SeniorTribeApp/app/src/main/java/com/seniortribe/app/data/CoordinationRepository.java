package com.seniortribe.app.data;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedEvent;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phối hợp sau tiếp nhận: chat (F11), thống nhất lịch (F12), báo hỗ trợ và đối soát (F13),
 * lịch sử (F15).
 *
 * Chat và lịch nằm dưới lần tiếp nhận needs_assignments/{assignmentId} để mỗi lần tiếp nhận
 * có luồng riêng (R08). Mọi bước đổi trạng thái chạy trong transaction như NeedRepository:
 * đọc trạng thái trên máy chủ, kiểm tra quyền, rồi mới ghi (R06); khi offline sẽ báo lỗi
 * thay vì báo thành công giả.
 */
public class CoordinationRepository {

    public static final String EVENT_SCHEDULE_PROPOSED = "SCHEDULE_PROPOSED";
    public static final String EVENT_SCHEDULE_DECLINED = "SCHEDULE_DECLINED";
    public static final String EVENT_SCHEDULED = "SCHEDULED";
    public static final String EVENT_DELIVERED = "DELIVERED";
    public static final String EVENT_RECONCILE_PENDING = "RECONCILE_PENDING";
    public static final String EVENT_COMPLETED = "COMPLETED";
    // E02 · Đổi lịch
    public static final String EVENT_RESCHEDULE_PROPOSED = "RESCHEDULE_PROPOSED";
    public static final String EVENT_RESCHEDULE_DECLINED = "RESCHEDULE_DECLINED";
    public static final String EVENT_RESCHEDULE_WITHDRAWN = "RESCHEDULE_WITHDRAWN";
    public static final String EVENT_RESCHEDULED = "RESCHEDULED";

    /** Khung giờ hẹn dài tối đa 12 tiếng, khớp với rule tạo schedules. */
    public static final long MAX_SLOT_MILLIS = 12L * 60 * 60 * 1000;

    private final FirebaseFirestore db;

    public CoordinationRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public CoordinationRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // =====================================================================
    // F11 · Chat theo lần tiếp nhận
    // =====================================================================

    public ListenerRegistration listenMessages(String assignmentId, NeedRepository.Callback<List<ChatMessage>> callback) {
        // INCLUDE để biết tin nào còn chờ máy chủ xác nhận (hiện "Đang gửi…")
        return assignmentRef(assignmentId).collection(Constants.COL_MESSAGES)
                .orderBy("at", Query.Direction.ASCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<ChatMessage> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ChatMessage m = doc.toObject(ChatMessage.class);
                        if (m == null) continue;
                        m.setPending(doc.getMetadata().hasPendingWrites());
                        list.add(m);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    public Task<Void> sendMessage(String assignmentId, Membership me, String text) {
        String clean = text != null ? text.trim() : "";
        if (clean.isEmpty()) return Tasks.forException(new NeedActionException("Tin nhắn đang trống."));
        if (clean.length() > 1000) return Tasks.forException(new NeedActionException("Tin nhắn dài quá 1000 ký tự."));

        Map<String, Object> m = new HashMap<>();
        m.put("senderUid", me.getUid());
        m.put("senderName", me.getDisplayName());
        m.put("text", clean);
        m.put("at", FieldValue.serverTimestamp());
        return assignmentRef(assignmentId).collection(Constants.COL_MESSAGES).document().set(m);
    }

    // =====================================================================
    // F12 · Đề xuất và thống nhất lịch
    // =====================================================================

    /** Các đề xuất lịch của lần tiếp nhận, mới nhất trước. */
    public ListenerRegistration listenSchedules(String assignmentId, NeedRepository.Callback<List<ScheduleSession>> callback) {
        return assignmentRef(assignmentId).collection(Constants.COL_SCHEDULES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<ScheduleSession> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ScheduleSession s = doc.toObject(ScheduleSession.class);
                        if (s != null) list.add(s);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    /**
     * Tổ chức phụ trách đề xuất lịch (ngày, khung giờ, hình thức) khi nhu cầu đang ACCEPTED.
     * Địa điểm lấy theo khu vực chung của nhu cầu, địa chỉ chính xác chỉ mở sau khi chốt (R03).
     * Đề xuất đang chờ cũ (nếu có) được rút lại trong cùng một lần ghi để đầu mối chỉ thấy
     * một đề xuất cần trả lời.
     */
    public Task<Void> proposeSchedule(String needId, String assignmentId, Membership me, Date startAt, Date endAt,
                                      String format, String place, List<String> pendingIds) {
        if (startAt == null || startAt.getTime() <= System.currentTimeMillis()) {
            return Tasks.forException(new NeedActionException("Hãy chọn ngày và giờ bắt đầu trong tương lai."));
        }
        if (endAt == null || endAt.getTime() <= startAt.getTime()) {
            return Tasks.forException(new NeedActionException("Giờ kết thúc phải sau giờ bắt đầu."));
        }
        if (endAt.getTime() - startAt.getTime() > MAX_SLOT_MILLIS) {
            return Tasks.forException(new NeedActionException("Khung giờ hẹn dài tối đa 12 tiếng."));
        }
        String cleanFormat = format != null ? format.trim() : "";
        if (cleanFormat.length() < 2 || cleanFormat.length() > 120) {
            return Tasks.forException(new NeedActionException("Hãy ghi hình thức hỗ trợ (ví dụ: Đến nhà · 2 đoàn viên)."));
        }
        String cleanPlace = place != null ? place.trim() : "";
        if (cleanPlace.length() < 2) cleanPlace = "Theo khu vực của nhu cầu";

        DocumentReference needRef = needRef(needId);
        WriteBatch batch = db.batch();
        for (String id : pendingIds) {
            Map<String, Object> withdraw = new HashMap<>();
            withdraw.put("status", ScheduleSession.WITHDRAWN);
            withdraw.put("decidedByUid", me.getUid());
            withdraw.put("decidedByName", me.getDisplayName());
            withdraw.put("decidedAt", FieldValue.serverTimestamp());
            batch.update(scheduleRef(assignmentId, id), withdraw);
        }

        Map<String, Object> s = new HashMap<>();
        s.put("startAt", new Timestamp(startAt));
        s.put("endAt", new Timestamp(endAt));
        s.put("format", cleanFormat);
        s.put("place", cleanPlace);
        s.put("status", ScheduleSession.PROPOSED);
        s.put("proposedByUid", me.getUid());
        s.put("proposedByName", me.getDisplayName());
        s.put("createdAt", FieldValue.serverTimestamp());
        batch.set(assignmentRef(assignmentId).collection(Constants.COL_SCHEDULES).document(), s);

        batch.set(NeedRepository.newEventRef(needRef), NeedRepository.event(EVENT_SCHEDULE_PROPOSED,
                Constants.STATUS_ACCEPTED, Constants.STATUS_ACCEPTED, me,
                describe(startAt, endAt, cleanFormat)));
        return batch.commit();
    }

    /**
     * Đầu mối đã hỏi ý kiến người cao tuổi và họ đồng ý: xác nhận lịch, nhu cầu sang SCHEDULED.
     * Từ đây người phụ trách được đọc thông tin riêng cần để đến hỗ trợ (R03).
     */
    public Task<Void> confirmSchedule(String needId, String scheduleId, Membership me, boolean elderAgreed,
                                      String consentMethod) {
        if (!elderAgreed) {
            return Tasks.forException(new NeedActionException("Chỉ chốt lịch khi người cao tuổi đã đồng ý."));
        }
        String consent = consentMethod != null ? consentMethod.trim() : "";
        if (consent.isEmpty()) {
            return Tasks.forException(new NeedActionException("Hãy ghi cách người cao tuổi đã đồng ý lịch hẹn."));
        }
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            requireRecorder(me, need);
            if (!Constants.STATUS_ACCEPTED.equals(need.getString("status"))) {
                throw new NeedActionException("Nhu cầu không còn ở bước chờ thống nhất lịch.");
            }
            String assignmentId = need.getString("currentAssignmentId");
            if (assignmentId == null) throw new NeedActionException("Nhu cầu chưa có tổ chức phụ trách.");

            DocumentReference scheduleRef = scheduleRef(assignmentId, scheduleId);
            DocumentSnapshot schedule = tx.get(scheduleRef);
            if (!schedule.exists() || !ScheduleSession.PROPOSED.equals(schedule.getString("status"))) {
                throw new NeedActionException("Đề xuất lịch này đã được thay đổi. Vui lòng xem lại.");
            }
            Timestamp startAt = schedule.getTimestamp("startAt");
            Timestamp endAt = schedule.getTimestamp("endAt");
            String format = schedule.getString("format");
            String place = schedule.getString("place");
            if (startAt == null || endAt == null || format == null) {
                throw new NeedActionException("Đề xuất này thiếu khung giờ hoặc hình thức. Hãy nhờ tổ chức đề xuất lại.");
            }

            Map<String, Object> decided = new HashMap<>();
            decided.put("status", ScheduleSession.CONFIRMED);
            decided.put("elderConsent", true);
            decided.put("consentMethod", consent);
            decided.put("decidedByUid", me.getUid());
            decided.put("decidedByName", me.getDisplayName());
            decided.put("decidedAt", FieldValue.serverTimestamp());

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_SCHEDULED);
            update.put("scheduleId", scheduleId);
            update.put("scheduledAt", startAt);
            update.put("scheduledEndAt", endAt);
            update.put("scheduledFormat", format);
            update.put("scheduledPlace", place);
            update.put("scheduledByUid", me.getUid());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(scheduleRef, decided);
            tx.update(ref, update);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_SCHEDULED,
                    Constants.STATUS_ACCEPTED, Constants.STATUS_SCHEDULED, me,
                    describe(startAt.toDate(), endAt.toDate(), format) + " · Đồng ý: " + consent));
            return null;
        });
    }

    /** Người cao tuổi không đồng ý lịch: đầu mối từ chối kèm lý do, nhu cầu vẫn ACCEPTED. */
    public Task<Void> declineSchedule(String needId, String scheduleId, Membership me, String reason) {
        String clean = reason != null ? reason.trim() : "";
        if (clean.isEmpty()) return Tasks.forException(new NeedActionException("Hãy ghi lý do để tổ chức đề xuất lịch khác."));
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            requireRecorder(me, need);
            String assignmentId = need.getString("currentAssignmentId");
            if (!Constants.STATUS_ACCEPTED.equals(need.getString("status")) || assignmentId == null) {
                throw new NeedActionException("Nhu cầu không còn ở bước chờ thống nhất lịch.");
            }
            DocumentReference scheduleRef = scheduleRef(assignmentId, scheduleId);
            DocumentSnapshot schedule = tx.get(scheduleRef);
            if (!schedule.exists() || !ScheduleSession.PROPOSED.equals(schedule.getString("status"))) {
                throw new NeedActionException("Đề xuất lịch này đã được thay đổi. Vui lòng xem lại.");
            }

            Map<String, Object> decided = new HashMap<>();
            decided.put("status", ScheduleSession.DECLINED);
            decided.put("declineReason", clean);
            decided.put("decidedByUid", me.getUid());
            decided.put("decidedByName", me.getDisplayName());
            decided.put("decidedAt", FieldValue.serverTimestamp());

            tx.update(scheduleRef, decided);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_SCHEDULE_DECLINED,
                    Constants.STATUS_ACCEPTED, Constants.STATUS_ACCEPTED, me, clean));
            return null;
        });
    }

    // =====================================================================
    // E02 · Đổi lịch sau khi đã chốt
    // =====================================================================

    /**
     * Một bên đề xuất đổi lịch đã chốt (nhu cầu đang SCHEDULED); bên còn lại xác nhận.
     *  - Tổ chức đề xuất: đầu mối hỏi ý kiến người cao tuổi rồi mới xác nhận.
     *  - Đầu mối đề xuất: phải hỏi ý kiến người cao tuổi trước và ghi lại cách hỏi.
     * Lịch đang hẹn vẫn giữ nguyên cho tới khi bên kia xác nhận. Yêu cầu đổi lịch đang chờ của
     * chính bên mình (nếu có) được rút lại trong cùng transaction để chỉ còn một yêu cầu.
     */
    public Task<Void> proposeReschedule(String needId, String assignmentId, Membership me, String side,
                                        Date startAt, Date endAt, String format, String reason,
                                        @Nullable String consentMethod, List<String> myPendingIds) {
        if (startAt == null || startAt.getTime() <= System.currentTimeMillis()) {
            return Tasks.forException(new NeedActionException("Hãy chọn ngày và giờ bắt đầu trong tương lai."));
        }
        if (endAt == null || endAt.getTime() <= startAt.getTime()) {
            return Tasks.forException(new NeedActionException("Giờ kết thúc phải sau giờ bắt đầu."));
        }
        if (endAt.getTime() - startAt.getTime() > MAX_SLOT_MILLIS) {
            return Tasks.forException(new NeedActionException("Khung giờ hẹn dài tối đa 12 tiếng."));
        }
        final String cleanFormat = format != null ? format.trim() : "";
        if (cleanFormat.length() < 2 || cleanFormat.length() > 120) {
            return Tasks.forException(new NeedActionException("Hãy ghi hình thức hỗ trợ (ví dụ: Đến nhà · 2 đoàn viên)."));
        }
        final String cleanReason = reason != null ? reason.trim() : "";
        if (cleanReason.isEmpty() || cleanReason.length() > 300) {
            return Tasks.forException(new NeedActionException("Hãy ghi lý do đổi lịch (tối đa 300 ký tự)."));
        }
        final boolean recorderSide = ScheduleSession.SIDE_RECORDER.equals(side);
        final String consent = consentMethod != null ? consentMethod.trim() : "";
        if (recorderSide && (consent.isEmpty() || consent.length() > 200)) {
            return Tasks.forException(new NeedActionException(
                    "Hãy hỏi ý kiến người cao tuổi về giờ mới và ghi lại cách hỏi trước khi gửi."));
        }

        final DocumentReference ref = needRef(needId);
        final DocumentReference newRef = assignmentRef(assignmentId).collection(Constants.COL_SCHEDULES).document();
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            requireSide(me, need, side);
            requireScheduledThread(need, assignmentId);

            // Đọc trước mọi đề xuất cần rút (transaction phải đọc xong mới được ghi)
            List<DocumentReference> toWithdraw = new ArrayList<>();
            for (String id : myPendingIds) {
                DocumentReference r = scheduleRef(assignmentId, id);
                DocumentSnapshot s = tx.get(r);
                if (s.exists() && ScheduleSession.PROPOSED.equals(s.getString("status"))
                        && ScheduleSession.KIND_RESCHEDULE.equals(s.getString("kind"))
                        && side.equals(s.getString("proposedBySide"))) {
                    toWithdraw.add(r);
                }
            }
            String place = need.getString("scheduledPlace");
            if (place == null || place.trim().length() < 2) place = need.getString("area");
            if (place == null || place.trim().length() < 2) place = "Theo khu vực của nhu cầu";

            for (DocumentReference r : toWithdraw) tx.update(r, decision(ScheduleSession.WITHDRAWN, me));

            Map<String, Object> s = new HashMap<>();
            s.put("startAt", new Timestamp(startAt));
            s.put("endAt", new Timestamp(endAt));
            s.put("format", cleanFormat);
            s.put("place", place.trim());
            s.put("status", ScheduleSession.PROPOSED);
            s.put("proposedByUid", me.getUid());
            s.put("proposedByName", me.getDisplayName());
            s.put("createdAt", FieldValue.serverTimestamp());
            s.put("kind", ScheduleSession.KIND_RESCHEDULE);
            s.put("replacesScheduleId", need.getString("scheduleId"));
            s.put("proposedBySide", side);
            s.put("reason", cleanReason);
            if (recorderSide) {
                s.put("elderConsent", true);
                s.put("consentMethod", consent);
            }
            tx.set(newRef, s);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_RESCHEDULE_PROPOSED,
                    Constants.STATUS_SCHEDULED, Constants.STATUS_SCHEDULED, me,
                    describe(startAt, endAt, cleanFormat) + " · Lý do: " + cleanReason));
            return null;
        });
    }

    /**
     * Bên còn lại xác nhận yêu cầu đổi lịch: yêu cầu thành CONFIRMED, lịch cũ thành RESCHEDULED
     * và nhu cầu trỏ sang lịch mới, tất cả trong một transaction (Security Rules kiểm tra khớp).
     *
     * @param consentMethod bắt buộc khi đầu mối xác nhận đề xuất của tổ chức (cách hỏi ý kiến cụ)
     */
    public Task<Void> confirmReschedule(String needId, String scheduleId, Membership me,
                                        boolean elderAgreed, @Nullable String consentMethod) {
        final String consent = consentMethod != null ? consentMethod.trim() : "";
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            String assignmentId = need.getString("currentAssignmentId");
            requireScheduledThread(need, assignmentId);
            String currentId = need.getString("scheduleId");

            DocumentReference nextRef = scheduleRef(assignmentId, scheduleId);
            DocumentSnapshot next = tx.get(nextRef);
            requirePendingReschedule(next, currentId);
            String proposerSide = sideOf(next);
            // Người xác nhận phải ở bên còn lại
            requireSide(me, need, opposite(proposerSide));
            if (me.getUid().equals(next.getString("proposedByUid"))) {
                throw new NeedActionException("Bạn không thể tự xác nhận yêu cầu đổi lịch của chính mình.");
            }
            boolean recorderConfirms = ScheduleSession.SIDE_ORG.equals(proposerSide);
            if (recorderConfirms) {
                if (!elderAgreed) throw new NeedActionException("Chỉ đổi lịch khi người cao tuổi đã đồng ý giờ mới.");
                if (consent.isEmpty() || consent.length() > 200) {
                    throw new NeedActionException("Hãy ghi cách người cao tuổi đã đồng ý giờ mới.");
                }
            }
            DocumentReference prevRef = currentId != null ? scheduleRef(assignmentId, currentId) : null;
            if (prevRef != null) {
                DocumentSnapshot prev = tx.get(prevRef);
                if (!prev.exists() || !ScheduleSession.CONFIRMED.equals(prev.getString("status"))) {
                    throw new NeedActionException("Lịch đang hẹn vừa thay đổi. Vui lòng xem lại.");
                }
            }
            Timestamp startAt = next.getTimestamp("startAt");
            Timestamp endAt = next.getTimestamp("endAt");
            String format = next.getString("format");
            String place = next.getString("place");
            if (startAt == null || endAt == null || format == null || place == null) {
                throw new NeedActionException("Yêu cầu đổi lịch thiếu thông tin. Hãy nhờ bên kia gửi lại.");
            }

            Map<String, Object> decided = decision(ScheduleSession.CONFIRMED, me);
            if (recorderConfirms) {
                decided.put("elderConsent", true);
                decided.put("consentMethod", consent);
            }
            Map<String, Object> superseded = new HashMap<>();
            superseded.put("status", ScheduleSession.RESCHEDULED);
            superseded.put("replacedById", scheduleId);
            superseded.put("rescheduledAt", FieldValue.serverTimestamp());

            Map<String, Object> update = new HashMap<>();
            update.put("scheduleId", scheduleId);
            update.put("scheduledAt", startAt);
            update.put("scheduledEndAt", endAt);
            update.put("scheduledFormat", format);
            update.put("scheduledPlace", place);
            update.put("scheduledByUid", me.getUid());
            update.put("rescheduleCount", FieldValue.increment(1));
            update.put("rescheduledAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(nextRef, decided);
            if (prevRef != null) tx.update(prevRef, superseded);
            tx.update(ref, update);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_RESCHEDULED,
                    Constants.STATUS_SCHEDULED, Constants.STATUS_SCHEDULED, me,
                    describe(startAt.toDate(), endAt.toDate(), format)
                            + (recorderConfirms ? " · Đồng ý: " + consent : "")));
            return null;
        });
    }

    /** Bên còn lại không nhận giờ mới: từ chối kèm lý do, lịch đang hẹn giữ nguyên. */
    public Task<Void> declineReschedule(String needId, String scheduleId, Membership me, String reason) {
        final String clean = reason != null ? reason.trim() : "";
        if (clean.isEmpty() || clean.length() > 300) {
            return Tasks.forException(new NeedActionException("Hãy ghi lý do không nhận giờ mới."));
        }
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            String assignmentId = need.getString("currentAssignmentId");
            requireScheduledThread(need, assignmentId);
            DocumentReference r = scheduleRef(assignmentId, scheduleId);
            DocumentSnapshot s = tx.get(r);
            requirePendingReschedule(s, need.getString("scheduleId"));
            requireSide(me, need, opposite(sideOf(s)));

            Map<String, Object> decided = decision(ScheduleSession.DECLINED, me);
            decided.put("declineReason", clean);
            tx.update(r, decided);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_RESCHEDULE_DECLINED,
                    Constants.STATUS_SCHEDULED, Constants.STATUS_SCHEDULED, me, clean));
            return null;
        });
    }

    /** Bên gửi rút lại yêu cầu đổi lịch còn đang chờ. */
    public Task<Void> withdrawReschedule(String needId, String scheduleId, Membership me) {
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            String assignmentId = need.getString("currentAssignmentId");
            if (assignmentId == null) throw new NeedActionException("Nhu cầu chưa có tổ chức phụ trách.");
            DocumentReference r = scheduleRef(assignmentId, scheduleId);
            DocumentSnapshot s = tx.get(r);
            if (!s.exists() || !ScheduleSession.PROPOSED.equals(s.getString("status"))
                    || !ScheduleSession.KIND_RESCHEDULE.equals(s.getString("kind"))) {
                throw new NeedActionException("Yêu cầu đổi lịch này đã được trả lời hoặc rút lại.");
            }
            requireSide(me, need, sideOf(s));

            tx.update(r, decision(ScheduleSession.WITHDRAWN, me));
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_RESCHEDULE_WITHDRAWN,
                    need.getString("status"), need.getString("status"), me, null));
            return null;
        });
    }

    // =====================================================================
    // F13 · Báo đã hỗ trợ và đối soát kết quả
    // =====================================================================

    /** Thành viên tổ chức phụ trách báo đã thực hiện hỗ trợ theo lịch: SCHEDULED → DELIVERED. */
    public Task<Void> reportDelivered(String needId, Membership me, String report) {
        String clean = report != null ? report.trim() : "";
        if (clean.length() < 5) {
            return Tasks.forException(new NeedActionException("Hãy mô tả ngắn việc đã hỗ trợ (ít nhất 5 ký tự)."));
        }
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            if (!me.getOrgId().equals(need.getString("assignedOrgId"))) {
                throw new NeedActionException("Chỉ tổ chức đang phụ trách mới báo đã hỗ trợ được.");
            }
            if (!Constants.STATUS_SCHEDULED.equals(need.getString("status"))) {
                throw new NeedActionException("Chỉ báo đã hỗ trợ sau khi lịch đã được thống nhất.");
            }

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_DELIVERED);
            update.put("deliveryReport", clean);
            update.put("deliveredByUid", me.getUid());
            update.put("deliveredByName", me.getDisplayName());
            update.put("deliveredAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(ref, update);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_DELIVERED,
                    Constants.STATUS_SCHEDULED, Constants.STATUS_DELIVERED, me, clean));
            return null;
        });
    }

    /** Đối soát chưa thống nhất với người cao tuổi: giữ DELIVERED và ghi nội dung cần làm rõ. */
    public Task<Void> noteReconcile(String needId, Membership me, String note) {
        String clean = note != null ? note.trim() : "";
        if (clean.isEmpty()) return Tasks.forException(new NeedActionException("Hãy ghi nội dung cần đối soát thêm."));
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            requireRecorder(me, need);
            if (!Constants.STATUS_DELIVERED.equals(need.getString("status"))) {
                throw new NeedActionException("Nhu cầu không ở bước chờ đối soát.");
            }

            Map<String, Object> update = new HashMap<>();
            update.put("reconcileNote", clean);
            update.put("reconcileAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(ref, update);
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_RECONCILE_PENDING,
                    Constants.STATUS_DELIVERED, Constants.STATUS_DELIVERED, me, clean));
            return null;
        });
    }

    /**
     * Đầu mối đối soát xong: DELIVERED → COMPLETED, đóng lần tiếp nhận nên chat chỉ còn đọc.
     * COMPLETED chỉ đóng nhu cầu cụ thể này; người cao tuổi vẫn có thể có nhu cầu mới (R09).
     */
    public Task<Void> complete(String needId, Membership me, @Nullable String note) {
        final String clean = note != null && !note.trim().isEmpty() ? note.trim() : null;
        final DocumentReference ref = needRef(needId);
        return db.runTransaction(tx -> {
            DocumentSnapshot need = tx.get(ref);
            requireExists(need);
            requireRecorder(me, need);
            if (!Constants.STATUS_DELIVERED.equals(need.getString("status"))) {
                throw new NeedActionException("Chỉ xác nhận hoàn thành sau khi tổ chức báo đã hỗ trợ.");
            }
            String assignmentId = need.getString("currentAssignmentId");

            Map<String, Object> update = new HashMap<>();
            update.put("status", Constants.STATUS_COMPLETED);
            update.put("completionNote", clean);
            update.put("completedByUid", me.getUid());
            update.put("completedAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());

            tx.update(ref, update);
            if (assignmentId != null) {
                tx.update(assignmentRef(assignmentId), NeedRepository.endAssignment(
                        NeedRepository.ASSIGNMENT_CLOSED, "Hoàn thành", clean, me));
            }
            tx.set(NeedRepository.newEventRef(ref), NeedRepository.event(EVENT_COMPLETED,
                    Constants.STATUS_DELIVERED, Constants.STATUS_COMPLETED, me, clean));
            return null;
        });
    }

    // =====================================================================
    // F15 · Lịch sử
    // =====================================================================

    /** Lịch sử thay đổi của một nhu cầu, cũ nhất trước. */
    public ListenerRegistration listenEvents(String needId, NeedRepository.Callback<List<NeedEvent>> callback) {
        return needRef(needId).collection(Constants.SUB_EVENTS)
                .orderBy("at", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<NeedEvent> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        NeedEvent e = doc.toObject(NeedEvent.class);
                        if (e != null) list.add(e);
                    }
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    // =====================================================================
    // Hàm hỗ trợ
    // =====================================================================

    private DocumentReference needRef(String needId) {
        return db.collection(Constants.COL_NEEDS_SUMMARY).document(needId);
    }

    private DocumentReference assignmentRef(String assignmentId) {
        return db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId);
    }

    private DocumentReference scheduleRef(String assignmentId, String scheduleId) {
        return assignmentRef(assignmentId).collection(Constants.COL_SCHEDULES).document(scheduleId);
    }

    private static void requireExists(DocumentSnapshot snap) {
        if (!snap.exists()) throw new NeedActionException("Không tìm thấy nhu cầu.");
    }

    private static void requireRecorder(Membership me, DocumentSnapshot need) {
        if (!me.isRecorder() || !me.getOrgId().equals(need.getString("recorderOrgId"))) {
            throw new NeedActionException("Chỉ đầu mối của đơn vị ghi nhận mới thực hiện được thao tác này.");
        }
    }

    /**
     * Bên của tài khoản trong lần phối hợp (E02): đầu mối của đơn vị ghi nhận, hoặc đại diện /
     * người được giao của tổ chức phụ trách. Trả null nếu không thuộc bên nào.
     */
    @Nullable
    public static String sideOf(Membership me, @Nullable com.seniortribe.app.models.NeedSummary need) {
        if (me == null || need == null) return null;
        if (me.isRecorder() && me.getOrgId().equals(need.getRecorderOrgId())) return ScheduleSession.SIDE_RECORDER;
        if (me.getOrgId().equals(need.getAssignedOrgId())
                && (me.isRepresentative() || me.getUid().equals(need.getAssigneeUid()))) {
            return ScheduleSession.SIDE_ORG;
        }
        return null;
    }

    private static void requireSide(Membership me, DocumentSnapshot need, String side) {
        boolean ok;
        if (ScheduleSession.SIDE_RECORDER.equals(side)) {
            ok = me.isRecorder() && me.getOrgId().equals(need.getString("recorderOrgId"));
        } else {
            ok = me.getOrgId().equals(need.getString("assignedOrgId"))
                    && (me.isRepresentative() || me.getUid().equals(need.getString("assigneeUid")));
        }
        if (!ok) {
            throw new NeedActionException(ScheduleSession.SIDE_RECORDER.equals(side)
                    ? "Chỉ đầu mối của đơn vị ghi nhận mới thực hiện được bước này."
                    : "Chỉ tổ chức đang phụ trách mới thực hiện được bước này.");
        }
    }

    private static void requireScheduledThread(DocumentSnapshot need, @Nullable String assignmentId) {
        if (!Constants.STATUS_SCHEDULED.equals(need.getString("status"))) {
            throw new NeedActionException("Chỉ đổi lịch khi nhu cầu đang ở bước đã hẹn lịch.");
        }
        if (assignmentId == null || !assignmentId.equals(need.getString("currentAssignmentId"))) {
            throw new NeedActionException("Lần tiếp nhận này đã kết thúc nên không đổi lịch được.");
        }
    }

    private static void requirePendingReschedule(DocumentSnapshot s, @Nullable String currentScheduleId) {
        if (!s.exists() || !ScheduleSession.PROPOSED.equals(s.getString("status"))
                || !ScheduleSession.KIND_RESCHEDULE.equals(s.getString("kind"))) {
            throw new NeedActionException("Yêu cầu đổi lịch này đã được trả lời hoặc rút lại.");
        }
        if (currentScheduleId == null || !currentScheduleId.equals(s.getString("replacesScheduleId"))) {
            throw new NeedActionException("Lịch đang hẹn đã đổi sau khi gửi yêu cầu này. Hãy gửi yêu cầu mới.");
        }
    }

    private static String sideOf(DocumentSnapshot schedule) {
        return ScheduleSession.SIDE_RECORDER.equals(schedule.getString("proposedBySide"))
                ? ScheduleSession.SIDE_RECORDER : ScheduleSession.SIDE_ORG;
    }

    private static String opposite(String side) {
        return ScheduleSession.SIDE_RECORDER.equals(side) ? ScheduleSession.SIDE_ORG : ScheduleSession.SIDE_RECORDER;
    }

    /** Các trường ghi khi một đề xuất được trả lời hoặc rút lại. */
    private static Map<String, Object> decision(String status, Membership me) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("decidedByUid", me.getUid());
        m.put("decidedByName", me.getDisplayName());
        m.put("decidedAt", FieldValue.serverTimestamp());
        return m;
    }

    /** "Thứ 7, 19/10 · 14:00 – 16:00 · Đến nhà · 2 đoàn viên" cho lịch sử thay đổi. */
    private static String describe(Date startAt, Date endAt, @Nullable String format) {
        String slot = DateTimeUtils.formatSlot(startAt.getTime(), endAt.getTime());
        return TextUtils.isEmpty(format) ? slot : slot + " · " + format;
    }
}
