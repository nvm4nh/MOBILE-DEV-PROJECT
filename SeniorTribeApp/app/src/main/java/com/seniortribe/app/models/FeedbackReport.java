package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E03 · Phản ánh về một lần hỗ trợ (feedback_reports/{reportId}).
 *
 * Người gửi là đầu mối ghi nhận hoặc thành viên tổ chức tiếp nhận; phản ánh luôn nói về bên còn lại.
 * Điều phối viên (REVIEWER) của đơn vị ghi nhận xử lý: Mới → Đang xử lý → Đã đóng.
 * Tổ chức bị phản ánh chỉ đọc được khi điều phối viên đã bắt đầu xử lý.
 */
@IgnoreExtraProperties
public class FeedbackReport {

    public static final String STATUS_NEW = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String TYPE_LATE = "LATE";
    public static final String TYPE_ATTITUDE = "ATTITUDE";
    public static final String TYPE_PRIVACY = "PRIVACY";
    public static final String TYPE_OTHER = "OTHER";

    @DocumentId
    private String reportId;
    private String needId;
    private String caseCode;
    private String assignmentId;
    private String recorderOrgId;
    private String reporterOrgId;
    private String reportedOrgId;
    private String reportedOrgName;
    private String type;
    private String description;
    private String status;
    private String createdByUid;
    private String createdByName;
    private String handlerUid;
    private String handlerName;
    private String resolution;
    private Date createdAt;
    private Date updatedAt;
    private Date closedAt;

    public FeedbackReport() {}

    @Exclude
    public boolean isClosed() { return STATUS_CLOSED.equals(status); }

    @Exclude
    public boolean isNew() { return STATUS_NEW.equals(status); }

    @Exclude
    public long getCreatedAtMillis() { return createdAt != null ? createdAt.getTime() : 0L; }

    @Exclude
    public long getUpdatedAtMillis() { return updatedAt != null ? updatedAt.getTime() : getCreatedAtMillis(); }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getRecorderOrgId() { return recorderOrgId; }
    public void setRecorderOrgId(String recorderOrgId) { this.recorderOrgId = recorderOrgId; }
    public String getReporterOrgId() { return reporterOrgId; }
    public void setReporterOrgId(String reporterOrgId) { this.reporterOrgId = reporterOrgId; }
    public String getReportedOrgId() { return reportedOrgId; }
    public void setReportedOrgId(String reportedOrgId) { this.reportedOrgId = reportedOrgId; }
    public String getReportedOrgName() { return reportedOrgName; }
    public void setReportedOrgName(String reportedOrgName) { this.reportedOrgName = reportedOrgName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedByUid() { return createdByUid; }
    public void setCreatedByUid(String createdByUid) { this.createdByUid = createdByUid; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getHandlerUid() { return handlerUid; }
    public void setHandlerUid(String handlerUid) { this.handlerUid = handlerUid; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public Date getClosedAt() { return closedAt; }
    public void setClosedAt(Date closedAt) { this.closedAt = closedAt; }
}
