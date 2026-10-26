package com.seniortribe.app.notifications;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * E01 · Quy tắc chọn ai nhận thông báo cho mỗi sự kiện của nhu cầu.
 *
 * Lớp Java thuần (không phụ thuộc Android / Firebase) để kiểm thử bằng JUnit. Nguyên tắc:
 *  - Không báo cho chính người vừa thao tác.
 *  - Chỉ báo cho người có việc liên quan theo vai trò (R01, R08): ví dụ đề xuất lịch chỉ báo
 *    cho đầu mối của đơn vị ghi nhận; tin nhắn chỉ báo cho hai người trong luồng chat.
 *  - Nội dung thông báo không chứa thông tin riêng của người cao tuổi (R03): chỉ mã hồ sơ,
 *    tiêu đề tóm tắt và tên người thao tác.
 */
public final class NotificationRules {

    // Loại sự kiện (khớp NeedRepository / CoordinationRepository; chép lại để lớp này không
    // phụ thuộc Firebase và chạy được trong unit test)
    public static final String SUBMITTED = "SUBMITTED";
    public static final String RESUBMITTED = "RESUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REVISION_REQUESTED = "REVISION_REQUESTED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String RETURNED = "RETURNED";
    public static final String CANCELLED = "CANCELLED";
    public static final String SCHEDULE_PROPOSED = "SCHEDULE_PROPOSED";
    public static final String SCHEDULE_DECLINED = "SCHEDULE_DECLINED";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String DELIVERED = "DELIVERED";
    public static final String RECONCILE_PENDING = "RECONCILE_PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String RESCHEDULE_PROPOSED = "RESCHEDULE_PROPOSED";
    public static final String RESCHEDULE_DECLINED = "RESCHEDULE_DECLINED";
    public static final String RESCHEDULE_WITHDRAWN = "RESCHEDULE_WITHDRAWN";
    public static final String RESCHEDULED = "RESCHEDULED";

    /** Màn hình mở khi bấm thông báo. */
    public enum Target { NEED, CHAT, SCHEDULE, RESCHEDULE, PROGRESS }

    /** Câu chữ của từng trường hợp (tiêu đề), để lớp Android tra ra chuỗi tiếng Việt. */
    public enum Kind {
        REVIEW_NEEDED,          // điều phối viên: có nhu cầu chờ rà soát
        REVISION_NEEDED,        // đầu mối: cần bổ sung
        APPROVED,               // đầu mối: đã được duyệt
        ASSIGNED_TO_ME,         // người được giao: bạn phụ trách nhu cầu
        ACCEPTED_BY_ORG,        // đầu mối: nhu cầu đã có tổ chức tiếp nhận
        RETURNED,               // đầu mối: tổ chức trả lại
        CANCELLED,              // tổ chức: đầu mối hủy
        SCHEDULE_TO_ANSWER,     // đầu mối: có đề xuất lịch cần hỏi ý kiến cụ
        SCHEDULE_DECLINED,      // tổ chức: đầu mối đề nghị giờ khác
        SCHEDULE_CONFIRMED,     // tổ chức: lịch đã chốt
        RESCHEDULE_TO_ANSWER,   // bên kia: có yêu cầu đổi lịch
        RESCHEDULE_DECLINED,    // bên gửi: yêu cầu đổi lịch không được nhận
        RESCHEDULE_WITHDRAWN,   // bên kia: yêu cầu đổi lịch đã rút
        RESCHEDULED,            // bên gửi: đã đổi sang lịch mới
        RECONCILE_NEEDED,       // đầu mối: tổ chức báo đã hỗ trợ, cần đối soát
        RECONCILE_NOTE,         // tổ chức: đầu mối ghi nội dung chưa khớp
        COMPLETED               // tổ chức: nhu cầu hoàn thành
    }

    /** Tài khoản đang đăng nhập (tách khỏi Membership để không phụ thuộc Android). */
    public static final class Me {
        public final String uid;
        public final String orgId;
        public final boolean recorder, reviewer, representative;

        public Me(String uid, String orgId, List<String> roles) {
            this.uid = uid;
            this.orgId = orgId;
            this.recorder = roles != null && roles.contains("RECORDER");
            this.reviewer = roles != null && roles.contains("REVIEWER");
            this.representative = roles != null && roles.contains("REPRESENTATIVE");
        }
    }

    /** Các trường của nhu cầu cần để quyết định. */
    public static final class Need {
        public String recorderOrgId, recorderUid, assignedOrgId, assigneeUid;

        public Need(String recorderOrgId, String recorderUid, @Nullable String assignedOrgId, @Nullable String assigneeUid) {
            this.recorderOrgId = recorderOrgId;
            this.recorderUid = recorderUid;
            this.assignedOrgId = assignedOrgId;
            this.assigneeUid = assigneeUid;
        }
    }

    /** Kết quả: có báo hay không, loại, câu chữ và màn hình mở ra. */
    public static final class Decision {
        public final NotifyCategory category;
        public final Kind kind;
        public final Target target;

        Decision(NotifyCategory category, Kind kind, Target target) {
            this.category = category;
            this.kind = kind;
            this.target = target;
        }
    }

    private NotificationRules() {}

    /** Đầu mối của đơn vị ghi nhận nhu cầu. */
    public static boolean isRecorderSide(Me me, Need n) {
        return me.recorder && eq(me.orgId, n.recorderOrgId);
    }

    /** Đại diện hoặc người được giao của tổ chức đang phụ trách. */
    public static boolean isOrgSide(Me me, Need n) {
        return n.assignedOrgId != null && eq(me.orgId, n.assignedOrgId)
                && (me.representative || eq(me.uid, n.assigneeUid));
    }

    /** Người trong luồng chat của lần tiếp nhận hiện tại (R08). */
    public static boolean isChatMember(Me me, Need n) {
        return eq(me.uid, n.recorderUid) || eq(me.uid, n.assigneeUid);
    }

    /**
     * Có báo sự kiện này cho tài khoản không.
     *
     * @param byUid   người thao tác
     * @param byOrgId tổ chức của người thao tác, để biết bên nào gửi yêu cầu đổi lịch
     * @return null nếu không cần báo
     */
    @Nullable
    public static Decision forEvent(Me me, Need n, @Nullable String type, @Nullable String byUid, @Nullable String byOrgId) {
        if (type == null || me == null || n == null) return null;
        if (eq(me.uid, byUid)) return null; // không báo việc của chính mình

        boolean recorderSide = isRecorderSide(me, n);
        boolean orgSide = isOrgSide(me, n);
        boolean byRecorderSide = byOrgId != null && eq(byOrgId, n.recorderOrgId);

        switch (type) {
            case SUBMITTED:
            case RESUBMITTED:
                return me.reviewer && eq(me.orgId, n.recorderOrgId)
                        ? new Decision(NotifyCategory.FOLLOW_UP, Kind.REVIEW_NEEDED, Target.NEED) : null;
            case REVISION_REQUESTED:
                return eq(me.uid, n.recorderUid)
                        ? new Decision(NotifyCategory.FOLLOW_UP, Kind.REVISION_NEEDED, Target.NEED) : null;
            case APPROVED:
                return eq(me.uid, n.recorderUid)
                        ? new Decision(NotifyCategory.STATUS, Kind.APPROVED, Target.NEED) : null;
            case ACCEPTED:
                if (eq(me.uid, n.assigneeUid)) return new Decision(NotifyCategory.STATUS, Kind.ASSIGNED_TO_ME, Target.CHAT);
                return recorderSide ? new Decision(NotifyCategory.STATUS, Kind.ACCEPTED_BY_ORG, Target.CHAT) : null;
            case RETURNED:
                return recorderSide ? new Decision(NotifyCategory.STATUS, Kind.RETURNED, Target.NEED) : null;
            case CANCELLED:
                return orgSide ? new Decision(NotifyCategory.STATUS, Kind.CANCELLED, Target.NEED) : null;
            case SCHEDULE_PROPOSED:
                return recorderSide ? new Decision(NotifyCategory.SCHEDULE, Kind.SCHEDULE_TO_ANSWER, Target.SCHEDULE) : null;
            case SCHEDULE_DECLINED:
                return orgSide ? new Decision(NotifyCategory.SCHEDULE, Kind.SCHEDULE_DECLINED, Target.SCHEDULE) : null;
            case SCHEDULED:
                return orgSide ? new Decision(NotifyCategory.SCHEDULE, Kind.SCHEDULE_CONFIRMED, Target.SCHEDULE) : null;
            case RESCHEDULE_PROPOSED:
                // Báo cho bên còn lại, là bên phải trả lời
                return (byRecorderSide ? orgSide : recorderSide)
                        ? new Decision(NotifyCategory.SCHEDULE, Kind.RESCHEDULE_TO_ANSWER, Target.RESCHEDULE) : null;
            case RESCHEDULE_DECLINED:
                return (byRecorderSide ? orgSide : recorderSide)
                        ? new Decision(NotifyCategory.SCHEDULE, Kind.RESCHEDULE_DECLINED, Target.RESCHEDULE) : null;
            case RESCHEDULE_WITHDRAWN:
                return (byRecorderSide ? orgSide : recorderSide)
                        ? new Decision(NotifyCategory.SCHEDULE, Kind.RESCHEDULE_WITHDRAWN, Target.RESCHEDULE) : null;
            case RESCHEDULED:
                return (byRecorderSide ? orgSide : recorderSide)
                        ? new Decision(NotifyCategory.SCHEDULE, Kind.RESCHEDULED, Target.RESCHEDULE) : null;
            case DELIVERED:
                return recorderSide ? new Decision(NotifyCategory.FOLLOW_UP, Kind.RECONCILE_NEEDED, Target.PROGRESS) : null;
            case RECONCILE_PENDING:
                return orgSide ? new Decision(NotifyCategory.FOLLOW_UP, Kind.RECONCILE_NOTE, Target.PROGRESS) : null;
            case COMPLETED:
                return orgSide ? new Decision(NotifyCategory.STATUS, Kind.COMPLETED, Target.PROGRESS) : null;
            default:
                return null;
        }
    }

    /** Tin nhắn mới: chỉ báo cho người còn lại trong luồng chat. */
    public static boolean shouldNotifyMessage(Me me, Need n, @Nullable String senderUid) {
        return me != null && n != null && !eq(me.uid, senderUid) && isChatMember(me, n);
    }

    /** Nhắc lịch: hai bên của lần phối hợp (đầu mối đã ghi nhận và tổ chức phụ trách). */
    public static boolean shouldRemind(Me me, Need n) {
        return me != null && n != null && (eq(me.uid, n.recorderUid) || isOrgSide(me, n));
    }

    /**
     * Các mốc nhắc trước giờ hẹn còn ở tương lai (mili giây tuyệt đối).
     *
     * @param dayBefore      nhắc trước 1 ngày
     * @param hoursBefore    nhắc trước N giờ (0 = tắt)
     */
    public static long[] reminderTimes(long startAt, long now, boolean dayBefore, int hoursBefore) {
        long day = startAt - 24L * 60 * 60 * 1000;
        long hours = hoursBefore > 0 ? startAt - hoursBefore * 60L * 60 * 1000 : -1;
        long a = dayBefore && day > now ? day : -1;
        long b = hours > now ? hours : -1;
        if (a > 0 && b > 0) return new long[]{a, b};
        if (a > 0) return new long[]{a};
        if (b > 0) return new long[]{b};
        return new long[0];
    }

    private static boolean eq(@Nullable String a, @Nullable String b) {
        return a != null && a.equals(b);
    }
}
