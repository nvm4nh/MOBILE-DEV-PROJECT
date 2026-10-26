package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E07 · Phiếu đăng ký của một tổ chức cho một sự kiện
 * (community_events/{eventId}/registrations/{orgId}). Mỗi tổ chức một phiếu, ghi số thành viên.
 * Chỉ lưu tên tổ chức và số người, không lưu danh sách cá nhân.
 */
@IgnoreExtraProperties
public class EventRegistration {

    @DocumentId
    private String orgId;
    private String orgName;
    private long people;
    private String byUid;
    private String byName;
    private Date createdAt;
    private Date updatedAt;

    public EventRegistration() {}

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public long getPeople() { return people; }
    public void setPeople(long people) { this.people = people; }
    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }
    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
