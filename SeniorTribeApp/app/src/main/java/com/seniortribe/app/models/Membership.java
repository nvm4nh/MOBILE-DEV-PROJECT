package com.seniortribe.app.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Quyền của tài khoản đang đăng nhập trong một tổ chức.
 *
 * Đọc từ memberships/{uid}/organizations/{orgId} (cùng schema với nhánh feat/auth-profile).
 * Quyền do quản trị cấp sẵn, người dùng không tự chọn được (quy tắc R01).
 */
public class Membership {

    public static final String ROLE_RECORDER = "RECORDER";             // Đầu mối ghi nhận
    public static final String ROLE_REVIEWER = "REVIEWER";             // Điều phối viên rà soát
    public static final String ROLE_REPRESENTATIVE = "REPRESENTATIVE"; // Đại diện tổ chức hỗ trợ

    private final String uid;
    private final String displayName;
    private final String orgId;
    private final String orgName;
    private final List<String> roles;

    public Membership(String uid, String displayName, String orgId, String orgName, List<String> roles) {
        this.uid = uid;
        this.displayName = displayName;
        this.orgId = orgId;
        this.orgName = orgName;
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
    }

    public String getUid() { return uid; }
    public String getDisplayName() { return displayName; }
    public String getOrgId() { return orgId; }
    public String getOrgName() { return orgName; }
    public List<String> getRoles() { return new ArrayList<>(roles); }

    public boolean has(String role) { return roles.contains(role); }
    public boolean isRecorder() { return has(ROLE_RECORDER); }
    public boolean isReviewer() { return has(ROLE_REVIEWER); }
    public boolean isRepresentative() { return has(ROLE_REPRESENTATIVE); }

    /** Thành viên của đơn vị đã ghi nhận nhu cầu này. */
    public boolean belongsToRecorderOf(NeedSummary need) {
        return need != null && orgId.equals(need.getRecorderOrgId());
    }

    /** Thành viên của tổ chức đang tiếp nhận nhu cầu này. */
    public boolean belongsToAssignedOrgOf(NeedSummary need) {
        return need != null && orgId.equals(need.getAssignedOrgId());
    }
}
