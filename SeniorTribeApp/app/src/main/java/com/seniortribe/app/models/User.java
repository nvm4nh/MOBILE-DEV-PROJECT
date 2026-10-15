package com.seniortribe.app.models;

import java.io.Serializable;

public class User implements Serializable {
    private String uid;
    private String email;
    private String displayName;
    private String role; // Reporter (Đầu mối), Coordinator (Điều phối viên), Assignee (Tổ chức)
    private String orgId;
    private String orgName;
    private String phone;
    private String district;
    private long createdAt;

    public User() {}

    public User(String uid, String email, String displayName, String role) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
