package com.seniortribe.app.models;

/**
 * Một thành viên trong danh sách tổ chức (organizations/{orgId}/members/{uid}).
 * Dùng khi tiếp nhận nhu cầu để chọn đại diện phụ trách.
 */
public class OrgMember {

    private final String uid;
    private final String displayName;

    public OrgMember(String uid, String displayName) {
        this.uid = uid;
        this.displayName = displayName;
    }

    public String getUid() { return uid; }
    public String getDisplayName() { return displayName; }
}
