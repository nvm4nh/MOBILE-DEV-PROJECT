package com.seniortribe.app.notifications;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** E01 · Ai nhận thông báo cho từng sự kiện, nhắc lịch lúc nào. */
public class NotificationRulesTest {

    private static final String REC_ORG = "org-rec";
    private static final String ORG = "org-help";

    private final NotificationRules.Me recorder =
            new NotificationRules.Me("u-recorder", REC_ORG, Collections.singletonList("RECORDER"));
    private final NotificationRules.Me otherRecorder =
            new NotificationRules.Me("u-recorder-2", REC_ORG, Collections.singletonList("RECORDER"));
    private final NotificationRules.Me reviewer =
            new NotificationRules.Me("u-reviewer", REC_ORG, Collections.singletonList("REVIEWER"));
    private final NotificationRules.Me rep =
            new NotificationRules.Me("u-rep", ORG, Collections.singletonList("REPRESENTATIVE"));
    private final NotificationRules.Me assignee =
            new NotificationRules.Me("u-assignee", ORG, Collections.<String>emptyList());
    private final NotificationRules.Me plainMember =
            new NotificationRules.Me("u-member", ORG, Collections.<String>emptyList());
    private final NotificationRules.Me outsider =
            new NotificationRules.Me("u-out", "org-x", Arrays.asList("RECORDER", "REPRESENTATIVE"));

    private final NotificationRules.Need need =
            new NotificationRules.Need(REC_ORG, "u-recorder", ORG, "u-assignee");

    @Test
    public void khongBaoViecCuaChinhMinh() {
        assertNull(NotificationRules.forEvent(recorder, need, NotificationRules.SCHEDULE_PROPOSED, "u-recorder", REC_ORG));
    }

    @Test
    public void nhuCauMoiChiBaoChoDieuPhoiVienCuaDonViGhiNhan() {
        assertEquals(NotificationRules.Kind.REVIEW_NEEDED,
                NotificationRules.forEvent(reviewer, need, NotificationRules.SUBMITTED, "u-recorder", REC_ORG).kind);
        assertNull(NotificationRules.forEvent(rep, need, NotificationRules.SUBMITTED, "u-recorder", REC_ORG));
        assertNull(NotificationRules.forEvent(otherRecorder, need, NotificationRules.SUBMITTED, "u-recorder", REC_ORG));
    }

    @Test
    public void tiepNhanBaoChoNguoiDuocGiaoVaDauMoi() {
        NotificationRules.Decision a = NotificationRules.forEvent(assignee, need, NotificationRules.ACCEPTED, "u-rep", ORG);
        assertNotNull(a);
        assertEquals(NotificationRules.Kind.ASSIGNED_TO_ME, a.kind);
        assertEquals(NotificationRules.Target.CHAT, a.target);
        assertEquals(NotificationRules.Kind.ACCEPTED_BY_ORG,
                NotificationRules.forEvent(recorder, need, NotificationRules.ACCEPTED, "u-rep", ORG).kind);
        assertNull(NotificationRules.forEvent(outsider, need, NotificationRules.ACCEPTED, "u-rep", ORG));
    }

    @Test
    public void deXuatLichChiBaoDauMoi() {
        NotificationRules.Decision d = NotificationRules.forEvent(recorder, need, NotificationRules.SCHEDULE_PROPOSED, "u-rep", ORG);
        assertEquals(NotifyCategory.SCHEDULE, d.category);
        assertEquals(NotificationRules.Target.SCHEDULE, d.target);
        assertNull(NotificationRules.forEvent(assignee, need, NotificationRules.SCHEDULE_PROPOSED, "u-rep", ORG));
    }

    @Test
    public void thanhVienThuongCuaToChucKhongNhanViecCuaNguoiPhuTrach() {
        assertNull(NotificationRules.forEvent(plainMember, need, NotificationRules.SCHEDULED, "u-recorder", REC_ORG));
        assertNotNull(NotificationRules.forEvent(rep, need, NotificationRules.SCHEDULED, "u-recorder", REC_ORG));
        assertNotNull(NotificationRules.forEvent(assignee, need, NotificationRules.SCHEDULED, "u-recorder", REC_ORG));
    }

    @Test
    public void doiLichBaoChoBenConLai() {
        // Tổ chức gửi → đầu mối nhận
        assertEquals(NotificationRules.Kind.RESCHEDULE_TO_ANSWER,
                NotificationRules.forEvent(recorder, need, NotificationRules.RESCHEDULE_PROPOSED, "u-assignee", ORG).kind);
        assertNull(NotificationRules.forEvent(rep, need, NotificationRules.RESCHEDULE_PROPOSED, "u-assignee", ORG));
        // Đầu mối gửi → tổ chức nhận
        assertNotNull(NotificationRules.forEvent(assignee, need, NotificationRules.RESCHEDULE_PROPOSED, "u-recorder", REC_ORG));
        assertNull(NotificationRules.forEvent(otherRecorder, need, NotificationRules.RESCHEDULE_PROPOSED, "u-recorder", REC_ORG));
        // Đã đổi lịch do đầu mối xác nhận → báo tổ chức
        assertEquals(NotificationRules.Target.RESCHEDULE,
                NotificationRules.forEvent(assignee, need, NotificationRules.RESCHEDULED, "u-recorder", REC_ORG).target);
    }

    @Test
    public void doiSoatBaoDauMoi_hoanThanhBaoToChuc() {
        assertEquals(NotifyCategory.FOLLOW_UP,
                NotificationRules.forEvent(recorder, need, NotificationRules.DELIVERED, "u-assignee", ORG).category);
        assertEquals(NotificationRules.Kind.COMPLETED,
                NotificationRules.forEvent(assignee, need, NotificationRules.COMPLETED, "u-recorder", REC_ORG).kind);
        assertNull(NotificationRules.forEvent(recorder, need, NotificationRules.COMPLETED, "u-recorder-2", REC_ORG));
    }

    @Test
    public void tinNhanChiBaoNguoiConLaiTrongLuongChat() {
        assertTrue(NotificationRules.shouldNotifyMessage(recorder, need, "u-assignee"));
        assertTrue(NotificationRules.shouldNotifyMessage(assignee, need, "u-recorder"));
        assertFalse(NotificationRules.shouldNotifyMessage(assignee, need, "u-assignee"));
        assertFalse(NotificationRules.shouldNotifyMessage(rep, need, "u-recorder"));          // R08
        assertFalse(NotificationRules.shouldNotifyMessage(otherRecorder, need, "u-assignee")); // R08
    }

    @Test
    public void loaiSuKienLaKhongBao() {
        assertNull(NotificationRules.forEvent(recorder, need, "SOMETHING_NEW", "u-rep", ORG));
        assertNull(NotificationRules.forEvent(recorder, need, null, "u-rep", ORG));
    }

    @Test
    public void mocNhacLich() {
        long hour = 60L * 60 * 1000;
        long now = 1_000_000_000L;
        long start = now + 48 * hour;
        assertArrayEquals(new long[]{start - 24 * hour, start - 2 * hour},
                NotificationRules.reminderTimes(start, now, true, 2));
        // Còn 5 tiếng: không còn mốc trước 1 ngày
        long soon = now + 5 * hour;
        assertArrayEquals(new long[]{soon - 2 * hour}, NotificationRules.reminderTimes(soon, now, true, 2));
        // Tắt cả hai
        assertEquals(0, NotificationRules.reminderTimes(start, now, false, 0).length);
        // Giờ hẹn đã qua
        assertEquals(0, NotificationRules.reminderTimes(now - hour, now, true, 2).length);
    }

    @Test
    public void nhacLichChoDauMoiVaToChucPhuTrach() {
        assertTrue(NotificationRules.shouldRemind(recorder, need));
        assertTrue(NotificationRules.shouldRemind(assignee, need));
        assertTrue(NotificationRules.shouldRemind(rep, need));
        assertFalse(NotificationRules.shouldRemind(plainMember, need));
        assertFalse(NotificationRules.shouldRemind(outsider, need));
    }
}
