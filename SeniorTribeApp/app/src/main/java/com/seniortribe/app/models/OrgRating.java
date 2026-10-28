package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * E03 · Đánh giá của đầu mối ghi nhận dành cho tổ chức đã hỗ trợ, theo ý kiến của người cao tuổi.
 *
 * Lưu ở org_ratings/{assignmentId}: mỗi lần tiếp nhận chỉ có một đánh giá, tạo sau khi nhu cầu
 * đã COMPLETED. Chỉ đơn vị ghi nhận (gồm điều phối viên) và tổ chức được đánh giá đọc được;
 * không hiện trên bảng tin.
 */
@IgnoreExtraProperties
public class OrgRating {

    @DocumentId
    private String assignmentId;
    private String needId;
    private String caseCode;
    private String recorderOrgId;
    private String orgId;
    private String orgName;
    private String assigneeName;
    private long stars;
    private List<String> tags;
    private String comment;
    private boolean elderAsked;
    private String byUid;
    private String byName;
    private Date createdAt;

    public OrgRating() {}

    @Exclude
    public int getStarCount() { return (int) Math.max(0, Math.min(5, stars)); }

    @Exclude
    public List<String> getTagList() { return tags != null ? tags : new ArrayList<>(); }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public String getRecorderOrgId() { return recorderOrgId; }
    public void setRecorderOrgId(String recorderOrgId) { this.recorderOrgId = recorderOrgId; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public long getStars() { return stars; }
    public void setStars(long stars) { this.stars = stars; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isElderAsked() { return elderAsked; }
    public void setElderAsked(boolean elderAsked) { this.elderAsked = elderAsked; }
    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }
    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
