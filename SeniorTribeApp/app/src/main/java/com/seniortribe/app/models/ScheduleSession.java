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

    public ScheduleSession() {}

    @Exclude
    public boolean isPending() { return PROPOSED.equals(status); }

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
}
