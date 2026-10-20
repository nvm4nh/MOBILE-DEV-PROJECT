package com.seniortribe.app.utils;

public class Constants {
    // Firestore Collections
    public static final String COL_USERS = "users";
    public static final String COL_ORGANIZATIONS = "organizations";
    public static final String COL_NEEDS_SUMMARY = "needs_summary";
    public static final String COL_NEEDS_PRIVATE = "needs_private";
    public static final String COL_ASSIGNMENTS = "needs_assignments";
    public static final String COL_POSTS = "posts";
    public static final String COL_CHATS = "chats";
    public static final String COL_MESSAGES = "messages";
    public static final String COL_SCHEDULES = "schedules";

    // Workflow Statuses (Quy trình 6 bước khép kín)
    public static final String STATUS_REVIEW = "REVIEW";         // Chờ rà soát / bổ sung
    public static final String STATUS_OPEN = "OPEN";             // Chờ tiếp nhận
    public static final String STATUS_ACCEPTED = "ACCEPTED";     // Đã tiếp nhận
    public static final String STATUS_SCHEDULED = "SCHEDULED";   // Đã thống nhất lịch
    public static final String STATUS_DELIVERED = "DELIVERED";   // Chờ đối soát kết quả
    public static final String STATUS_COMPLETED = "COMPLETED";   // Hoàn thành
    public static final String STATUS_CANCELLED = "CANCELLED";   // Đã hủy

    // User Roles
    public static final String ROLE_REPORTER = "Reporter";       // Đầu mối cộng đồng
    public static final String ROLE_COORDINATOR = "Coordinator"; // Điều phối viên
    public static final String ROLE_ASSIGNEE = "Assignee";       // Đại diện tổ chức tiếp nhận

    // Categories
    public static final String CAT_CONVERSATION = "Trò chuyện & Tâm sự";
    public static final String CAT_DAILY_LIVING = "Hỗ trợ sinh hoạt";
    public static final String CAT_MEDICAL = "Y tế & Khám bệnh";
    public static final String CAT_SUPPLIES = "Nhu yếu phẩm";

    // Urgencies
    public static final String URGENCY_LOW = "Bình thường";
    public static final String URGENCY_MEDIUM = "Cần sớm";
    public static final String URGENCY_HIGH = "Khẩn cấp";

    // Intent extras
    public static final String EXTRA_NEED = "extra_need";
    public static final String EXTRA_NEED_ID = "extra_need_id";
    public static final String EXTRA_POST = "extra_post";
    public static final String EXTRA_CHAT_PARTNER = "extra_chat_partner";

    // ----- Nhu cầu hỗ trợ (F06–F10, F14) -----
    public static final String COL_MEMBERSHIPS = "memberships";      // memberships/{uid}/organizations/{orgId}
    public static final String SUB_ORGANIZATIONS = "organizations";
    public static final String SUB_MEMBERS = "members";              // organizations/{orgId}/members/{uid}
    public static final String SUB_EVENTS = "events";                // needs_summary/{needId}/events/{eventId}
    public static final String COL_COUNTERS = "counters";            // counters/needs: { next }

    // Lần tiếp nhận hiện tại; chat theo từng lần tiếp nhận nên dùng id này (quy tắc R08)
    public static final String EXTRA_ASSIGNMENT_ID = "extra_assignment_id";
    /** Mã hồ sơ NC-xxxx, để màn hình F12 / F13 hiện tiêu đề ngay khi chưa tải xong. */
    public static final String EXTRA_CASE_CODE = "extra_case_code";
}
