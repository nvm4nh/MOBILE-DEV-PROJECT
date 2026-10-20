package com.seniortribe.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.Date;

/** Shared model. Existing organization/contact fields remain available. */
public class User implements Serializable {
    private String uid;
    private String email;
    private String displayName;
    private String role;
    private String orgId;
    private String orgName;
    private String phone;
    private String district;
    private String area;
    private long createdAt;

    public User() { }

    public User(String uid, String email, String displayName, String role) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
    }

    /** Read both the team's old model and the stage-1 profile without rewriting it. */
    public static User fromDocument(DocumentSnapshot document) {
        User user = new User();
        user.uid = text(document, "uid");
        user.email = text(document, "email");
        user.displayName = text(document, "displayName");
        user.role = text(document, "role");
        user.orgId = text(document, "orgId");
        user.orgName = text(document, "orgName");
        user.phone = text(document, "phone");
        user.district = text(document, "district");
        // Personal area is independent of a legacy business district when both exist.
        user.area = document.contains("area") ? text(document, "area") : user.district;
        if (user.district.isEmpty()) user.district = text(document, "area");
        Object created = document.get("createdAt");
        if (created instanceof Timestamp) user.createdAt = ((Timestamp) created).toDate().getTime();
        else if (created instanceof Date) user.createdAt = ((Date) created).getTime();
        else if (created instanceof Number) user.createdAt = ((Number) created).longValue();
        else if (created != null) throw new IllegalStateException("PROFILE");
        return user;
    }

    private static String text(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value == null) return "";
        if (!(value instanceof String)) throw new IllegalStateException("PROFILE");
        return (String) value;
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

    @Exclude
    public String getArea() { return area == null ? (district == null ? "" : district) : area; }
    public void setArea(String area) { this.area = area; }
}
