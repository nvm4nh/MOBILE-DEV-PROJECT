package com.seniortribe.app.models;

import java.io.Serializable;

public class NeedSummary implements Serializable {
    private String needId;
    private String caseCode;
    private String title;
    private String category; // Trò chuyện, Sinh hoạt, Y tế, Nhu yếu phẩm
    private String district;
    private String urgency;  // Low, Medium, High, Urgent
    private String status;   // REVIEW, OPEN, ACCEPTED, SCHEDULED, DELIVERED, COMPLETED, CANCELLED
    private String reporterUid;
    private String reporterName;
    private String assigneeUid;
    private String assigneeName;
    private String orgId;
    private String orgName;
    private long createdAt;
    private long updatedAt;

    public NeedSummary() {}

    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReporterUid() { return reporterUid; }
    public void setReporterUid(String reporterUid) { this.reporterUid = reporterUid; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getAssigneeUid() { return assigneeUid; }
    public void setAssigneeUid(String assigneeUid) { this.assigneeUid = assigneeUid; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
