package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.io.Serializable;
import java.util.Date;

/**
 * Bản tóm tắt của một nhu cầu hỗ trợ (collection "needs_summary").
 *
 * Bản này được chia sẻ trong mạng lưới nên KHÔNG chứa tên, địa chỉ, số điện thoại
 * của người cao tuổi. Thông tin riêng nằm ở {@link NeedPrivateDetails}.
 *
 * Lớp chỉ dùng để ĐỌC dữ liệu (toObject). Việc ghi đi qua NeedRepository bằng Map
 * để kiểm soát chính xác các field được ghi (khớp với Security Rules).
 */
@IgnoreExtraProperties
public class NeedSummary implements Serializable {

    @DocumentId
    private String needId;

    // Nội dung tóm tắt (F06)
    private String caseCode;        // NC-0151, tự tạo khi gửi
    private String title;
    private String topic;           // Chủ đề
    private String content;
    private String area;            // Khu vực mức phường, ví dụ "Phường 9, Quận 3"
    private String desiredTime;     // Thời gian mong muốn, ví dụ "Chiều thứ 7 hoặc Chủ nhật"
    private String source;          // Nguồn ghi nhận
    private boolean consentRecorded;
    private String consentMethod;   // Cách hỏi ý kiến

    // Quy trình
    private String status;          // REVIEW, OPEN, ACCEPTED, SCHEDULED, DELIVERED, COMPLETED, CANCELLED
    private boolean needsRevision;  // true khi điều phối viên yêu cầu bổ sung
    private String reviewNote;
    private String reviewerUid;
    private String reviewerName;

    // Đơn vị ghi nhận
    private String recorderOrgId;
    private String recorderOrgName;
    private String recorderUid;
    private String recorderName;

    // Lần tiếp nhận hiện tại (F10)
    private String assignedOrgId;
    private String assignedOrgName;
    private String assigneeUid;
    private String assigneeName;
    private String currentAssignmentId;
    private long returnCount;

    // Hủy (F14)
    private String cancelReason;
    private String cancelNote;

    // Thời gian (server timestamp)
    private Date createdAt;
    private Date updatedAt;
    private Date submittedAt;
    private Date reviewedAt;
    private Date acceptedAt;
    private Date cancelledAt;

    // Phối hợp sau tiếp nhận (F12, F13)
    private String scheduleId;      // lịch đã chốt trong needs_assignments/{id}/schedules
    private Date scheduledAt;
    private String scheduledPlace;
    private String deliveryReport;  // tổ chức mô tả việc đã hỗ trợ
    private String deliveredByName;
    private Date deliveredAt;
    private String reconcileNote;   // nội dung đầu mối còn cần đối soát
    private Date reconcileAt;
    private String completionNote;
    private Date completedAt;

    public NeedSummary() {}

    // ----- Tiện ích hiển thị -----

    /** Thời điểm ghi nhận tính bằng mili giây, 0 nếu server chưa trả timestamp. */
    @Exclude
    public long getCreatedAtMillis() {
        return createdAt != null ? createdAt.getTime() : 0L;
    }

    /** Nhu cầu đang ở một bước phối hợp sau tiếp nhận, khi chat còn gửi được. */
    @Exclude
    public boolean isCoordinating() {
        return "ACCEPTED".equals(status) || "SCHEDULED".equals(status) || "DELIVERED".equals(status);
    }

    @Exclude
    public long getScheduledAtMillis() {
        return scheduledAt != null ? scheduledAt.getTime() : 0L;
    }

    @Exclude
    public boolean isFinished() {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }

    // ----- Tương thích với code mẫu cũ (ChatListFragment). Không lưu lên Firestore. -----

    @Exclude
    public String getCategory() { return topic; }

    @Exclude
    public void setCategory(String category) { this.topic = category; }

    @Exclude
    public String getDistrict() { return area; }

    @Exclude
    public void setDistrict(String district) { this.area = district; }

    @Exclude
    public void setCreatedAt(long millis) { this.createdAt = new Date(millis); }

    // ----- Getter / setter cho Firestore -----

    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getDesiredTime() { return desiredTime; }
    public void setDesiredTime(String desiredTime) { this.desiredTime = desiredTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isConsentRecorded() { return consentRecorded; }
    public void setConsentRecorded(boolean consentRecorded) { this.consentRecorded = consentRecorded; }

    public String getConsentMethod() { return consentMethod; }
    public void setConsentMethod(String consentMethod) { this.consentMethod = consentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isNeedsRevision() { return needsRevision; }
    public void setNeedsRevision(boolean needsRevision) { this.needsRevision = needsRevision; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public String getReviewerUid() { return reviewerUid; }
    public void setReviewerUid(String reviewerUid) { this.reviewerUid = reviewerUid; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getRecorderOrgId() { return recorderOrgId; }
    public void setRecorderOrgId(String recorderOrgId) { this.recorderOrgId = recorderOrgId; }

    public String getRecorderOrgName() { return recorderOrgName; }
    public void setRecorderOrgName(String recorderOrgName) { this.recorderOrgName = recorderOrgName; }

    public String getRecorderUid() { return recorderUid; }
    public void setRecorderUid(String recorderUid) { this.recorderUid = recorderUid; }

    public String getRecorderName() { return recorderName; }
    public void setRecorderName(String recorderName) { this.recorderName = recorderName; }

    public String getAssignedOrgId() { return assignedOrgId; }
    public void setAssignedOrgId(String assignedOrgId) { this.assignedOrgId = assignedOrgId; }

    public String getAssignedOrgName() { return assignedOrgName; }
    public void setAssignedOrgName(String assignedOrgName) { this.assignedOrgName = assignedOrgName; }

    public String getAssigneeUid() { return assigneeUid; }
    public void setAssigneeUid(String assigneeUid) { this.assigneeUid = assigneeUid; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public String getCurrentAssignmentId() { return currentAssignmentId; }
    public void setCurrentAssignmentId(String currentAssignmentId) { this.currentAssignmentId = currentAssignmentId; }

    public long getReturnCount() { return returnCount; }
    public void setReturnCount(long returnCount) { this.returnCount = returnCount; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getCancelNote() { return cancelNote; }
    public void setCancelNote(String cancelNote) { this.cancelNote = cancelNote; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }

    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }

    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }

    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public Date getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Date scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getScheduledPlace() { return scheduledPlace; }
    public void setScheduledPlace(String scheduledPlace) { this.scheduledPlace = scheduledPlace; }

    public String getDeliveryReport() { return deliveryReport; }
    public void setDeliveryReport(String deliveryReport) { this.deliveryReport = deliveryReport; }

    public String getDeliveredByName() { return deliveredByName; }
    public void setDeliveredByName(String deliveredByName) { this.deliveredByName = deliveredByName; }

    public Date getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Date deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getReconcileNote() { return reconcileNote; }
    public void setReconcileNote(String reconcileNote) { this.reconcileNote = reconcileNote; }

    public Date getReconcileAt() { return reconcileAt; }
    public void setReconcileAt(Date reconcileAt) { this.reconcileAt = reconcileAt; }

    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
}
