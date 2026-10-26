package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * Một đề xuất lịch hẹn trong needs_assignments/{assignmentId}/schedules (F12).
 *
 * Tổ chức phụ trách đề xuất ngày, khung giờ và hình thức (PROPOSED). Đầu mối hỏi ý kiến người cao tuổi rồi xác nhận
 * (CONFIRMED, nhu cầu sang SCHEDULED) hoặc từ chối kèm lý do (DECLINED). Khi tổ chức gửi
 * đề xuất mới, đề xuất đang chờ cũ chuyển WITHDRAWN.
 */
@IgnoreExtraProperties
public class ScheduleSession {

    public static final String PROPOSED = "PROPOSED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String DECLINED = "DECLINED";
    public static final String WITHDRAWN = "WITHDRAWN";
    /** E02 · Lịch đã chốt bị thay bằng một lịch mới (giữ lại để xem lịch sử). */
    public static final String RESCHEDULED = "RESCHEDULED";

    /** E02 · Loại đề xuất: yêu cầu đổi lịch sau khi đã chốt. Đề xuất F12 không có trường này. */
    public static final String KIND_RESCHEDULE = "RESCHEDULE";
    /** Bên gửi đề xuất đổi lịch. */
    public static final String SIDE_ORG = "ORG";
    public static final String SIDE_RECORDER = "RECORDER";

    @DocumentId
    private String scheduleId;
    private Date startAt;
    private Date endAt;             // hết khung giờ hẹn, ví dụ 14:00 – 16:00
    private String format;          // hình thức, ví dụ "Đến nhà · 2 đoàn viên"
    private String place;           // khu vực chung của nhu cầu, địa chỉ chính xác nằm ở thông tin riêng
    private String note;
    private String status;
    private String proposedByUid;
    private String proposedByName;
    private Date createdAt;
    private boolean elderConsent;   // đầu mối đã tick "Cụ đồng ý với lịch hẹn này"
    private String consentMethod;   // cách đầu mối hỏi ý kiến người cao tuổi khi xác nhận
    private String declineReason;
    private String decidedByName;
    private Date decidedAt;

    // E02 · Đổi lịch
    private String kind;                // RESCHEDULE, hoặc null với đề xuất F12
    private String replacesScheduleId;  // lịch đã chốt mà yêu cầu này muốn thay
    private String proposedBySide;      // ORG / RECORDER
    private String reason;              // lý do đổi lịch
    private String replacedById;        // khi RESCHEDULED: lịch mới thay cho lịch này
    private Date rescheduledAt;

    public ScheduleSession() {}

    /** Đề xuất F12 đang chờ đầu mối trả lời (không tính yêu cầu đổi lịch). */
    @Exclude
    public boolean isPending() { return PROPOSED.equals(status) && !isReschedule(); }

    /** Yêu cầu đổi lịch (E02), ở bất kỳ trạng thái nào. */
    @Exclude
    public boolean isReschedule() { return KIND_RESCHEDULE.equals(kind); }

    /** Yêu cầu đổi lịch đang chờ bên kia trả lời. */
    @Exclude
    public boolean isPendingReschedule() { return isReschedule() && PROPOSED.equals(status); }

    /** Bên gửi; đề xuất F12 cũ không ghi thì luôn là tổ chức. */
    @Exclude
    public String side() { return SIDE_RECORDER.equals(proposedBySide) ? SIDE_RECORDER : SIDE_ORG; }

    @Exclude
    public long getStartAtMillis() { return startAt != null ? startAt.getTime() : 0L; }

    /** Hết khung giờ; đề xuất cũ chưa có khung giờ thì trả 0. */
    @Exclude
    public long getEndAtMillis() { return endAt != null ? endAt.getTime() : 0L; }

    /** Thời điểm đề xuất, dùng để xếp tin hệ thống vào đúng chỗ trong luồng chat. */
    @Exclude
    public long getCreatedAtMillis() { return createdAt != null ? createdAt.getTime() : System.currentTimeMillis(); }

    @Exclude
    public long getDecidedAtMillis() { return decidedAt != null ? decidedAt.getTime() : 0L; }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public Date getStartAt() { return startAt; }
    public void setStartAt(Date startAt) { this.startAt = startAt; }

    public Date getEndAt() { return endAt; }
    public void setEndAt(Date endAt) { this.endAt = endAt; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public boolean isElderConsent() { return elderConsent; }
    public void setElderConsent(boolean elderConsent) { this.elderConsent = elderConsent; }

    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProposedByUid() { return proposedByUid; }
    public void setProposedByUid(String proposedByUid) { this.proposedByUid = proposedByUid; }

    public String getProposedByName() { return proposedByName; }
    public void setProposedByName(String proposedByName) { this.proposedByName = proposedByName; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getConsentMethod() { return consentMethod; }
    public void setConsentMethod(String consentMethod) { this.consentMethod = consentMethod; }

    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }

    public String getDecidedByName() { return decidedByName; }
    public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }

    public Date getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Date decidedAt) { this.decidedAt = decidedAt; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getReplacesScheduleId() { return replacesScheduleId; }
    public void setReplacesScheduleId(String replacesScheduleId) { this.replacesScheduleId = replacesScheduleId; }

    public String getProposedBySide() { return proposedBySide; }
    public void setProposedBySide(String proposedBySide) { this.proposedBySide = proposedBySide; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getReplacedById() { return replacedById; }
    public void setReplacedById(String replacedById) { this.replacedById = replacedById; }

    public Date getRescheduledAt() { return rescheduledAt; }
    public void setRescheduledAt(Date rescheduledAt) { this.rescheduledAt = rescheduledAt; }
}
