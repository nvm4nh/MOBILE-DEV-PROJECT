package com.seniortribe.app.viewmodels;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.CoordinationRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.Event;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Dữ liệu và thao tác chung của hai màn hình sau tiếp nhận:
 *  - F12 · Thống nhất lịch (ScheduleAgreementActivity)
 *  - F13 · Tiến độ & đối soát (NeedProgressActivity)
 *
 * Theo dõi realtime nhu cầu và các đề xuất lịch của lần tiếp nhận hiện tại; mọi bước đổi
 * trạng thái gọi CoordinationRepository (transaction, R04, R06). Trạng thái form lịch hẹn
 * (ngày, khung giờ đang chọn) cũng nằm ở đây để không mất khi xoay màn hình (T04).
 */
public class CoordinationViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public NeedSummary need;
        public List<ScheduleSession> schedules = new ArrayList<>();
        public boolean busy;

        /** Đề xuất đang chờ đầu mối trả lời (mới nhất). */
        @Nullable
        public ScheduleSession pendingSchedule() {
            for (ScheduleSession s : schedules) if (s.isPending()) return s;
            return null;
        }

        /** Đề xuất gần nhất, để hiện lý do khi vừa bị từ chối. */
        @Nullable
        public ScheduleSession latestSchedule() {
            return schedules.isEmpty() ? null : schedules.get(0);
        }

        /** Lịch đã chốt của nhu cầu (nếu có). */
        @Nullable
        public ScheduleSession confirmedSchedule() {
            if (need == null || need.getScheduleId() == null) return null;
            for (ScheduleSession s : schedules) if (need.getScheduleId().equals(s.getScheduleId())) return s;
            return null;
        }

        /** E02 · Yêu cầu đổi lịch đang chờ cho lịch hiện tại (mới nhất trước). */
        @Nullable
        public ScheduleSession pendingReschedule() {
            if (need == null || need.getScheduleId() == null) return null;
            for (ScheduleSession s : schedules) {
                if (s.isPendingReschedule() && need.getScheduleId().equals(s.getReplacesScheduleId())) return s;
            }
            return null;
        }

        /** E02 · Lịch sử các lần chốt / đổi lịch, mới nhất trước (bỏ đề xuất F12 bị rút lại). */
        public List<ScheduleSession> scheduleHistory() {
            List<ScheduleSession> out = new ArrayList<>();
            for (ScheduleSession s : schedules) {
                if (s.isReschedule() || ScheduleSession.CONFIRMED.equals(s.getStatus())
                        || ScheduleSession.RESCHEDULED.equals(s.getStatus())) {
                    out.add(s);
                }
            }
            return out;
        }
    }

    /** E02 · Lịch mới đang soạn trên màn hình Đổi lịch, giữ qua xoay màn hình. */
    public static class RescheduleDraft {
        public boolean prefilled;
        public long startAt;
        public long endAt;
        public boolean dateSet;
    }

    /** Lịch tổ chức đang soạn trên màn hình F12, giữ qua xoay màn hình. */
    public static class Draft {
        public boolean editing;      // tổ chức đang soạn đề xuất mới
        public boolean prefilled;    // đã điền sẵn ô Hình thức một lần
        public long startAt;         // 0 = chưa chọn
        public long endAt;           // 0 = chưa chọn
        public boolean dateSet;
    }

    private final NeedRepository needs = new NeedRepository();
    private final CoordinationRepository coordination = new CoordinationRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> done = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> rescheduleSent = new MutableLiveData<>();
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private final Draft draft = new Draft();
    private final RescheduleDraft rescheduleDraft = new RescheduleDraft();

    private String needId;
    private String assignmentId;

    public LiveData<Ui> getUi() { return ui; }

    /** Thông báo ngắn (lỗi hoặc thành công) hiện bằng Snackbar. */
    public LiveData<Event<String>> getMessages() { return messages; }

    /** Một thao tác đổi trạng thái vừa thành công; giá trị là câu thông báo. */
    public LiveData<Event<String>> getDone() { return done; }

    /** E02 · Yêu cầu đổi lịch vừa gửi thành công: màn hình xóa ô lý do / cách hỏi ý kiến. */
    public LiveData<Event<Boolean>> getRescheduleSent() { return rescheduleSent; }

    public Draft getDraft() { return draft; }

    public RescheduleDraft getRescheduleDraft() { return rescheduleDraft; }

    public String getAssignmentId() { return assignmentId; }

    public void start(String needId, @Nullable String assignmentId) {
        if (this.needId != null) return; // đã chạy, ví dụ sau khi xoay màn hình
        this.needId = needId;
        this.assignmentId = assignmentId;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui s = copy();
            if (!task.isSuccessful() || task.getResult() == null) {
                s.loading = false;
                s.error = task.isSuccessful() ? "Tài khoản chưa được cấp quyền tổ chức."
                        : NeedErrors.message(task.getException());
                ui.setValue(s);
                return;
            }
            s.membership = task.getResult();
            ui.setValue(s);
            listen();
        });
    }

    public void retry() {
        String n = needId, a = assignmentId;
        stop();
        needId = null;
        ui.setValue(new Ui());
        start(n, a);
    }

    private void listen() {
        registrations.add(needs.listenNeed(needId, new NeedRepository.Callback<NeedSummary>() {
            @Override public void onData(NeedSummary data, boolean fromCache) {
                Ui s = copy();
                s.need = data;
                s.loading = false;
                s.error = null;
                ui.setValue(s);
                // Mở từ màn hình Lịch không kèm assignmentId: lấy theo lần tiếp nhận hiện tại
                if (assignmentId == null && data != null && data.getCurrentAssignmentId() != null) {
                    assignmentId = data.getCurrentAssignmentId();
                    listenSchedules();
                }
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        }));
        if (assignmentId != null) listenSchedules();
    }

    private void listenSchedules() {
        registrations.add(coordination.listenSchedules(assignmentId, new NeedRepository.Callback<List<ScheduleSession>>() {
            @Override public void onData(List<ScheduleSession> data, boolean fromCache) {
                Ui s = copy();
                s.schedules = data;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                // Không đọc được lịch (ví dụ lần tiếp nhận cũ) thì màn hình vẫn hiện trạng thái nhu cầu
            }
        }));
    }

    // ----- F12 · Lịch hẹn -----

    public void proposeSchedule(Date startAt, Date endAt, String format) {
        Membership me = membership();
        Ui s = ui.getValue();
        if (me == null || s == null || s.need == null || assignmentId == null) return;
        List<String> pending = new ArrayList<>();
        for (ScheduleSession x : s.schedules) if (x.isPending()) pending.add(x.getScheduleId());
        run(coordination.proposeSchedule(needId, assignmentId, me, startAt, endAt, format, s.need.getArea(), pending),
                "Đã gửi đề xuất lịch. Chờ đầu mối hỏi ý kiến người cao tuổi.", true);
    }

    public void confirmSchedule(String scheduleId, boolean elderAgreed, String consentMethod) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.confirmSchedule(needId, scheduleId, me, elderAgreed, consentMethod),
                "Đã chốt lịch. Đại diện được giao giờ xem được thông tin liên hệ cần thiết.", false);
    }

    public void declineSchedule(String scheduleId, String reason) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.declineSchedule(needId, scheduleId, me, reason),
                "Đã báo tổ chức đề xuất giờ khác.", false);
    }

    // ----- E02 · Đổi lịch -----

    /** Gửi yêu cầu đổi lịch theo bên của tài khoản; kết quả báo qua getDone / getMessages. */
    public void proposeReschedule(Date startAt, Date endAt, String format, String reason, @Nullable String consentMethod) {
        Membership me = membership();
        Ui s = ui.getValue();
        if (me == null || s == null || s.need == null || assignmentId == null) return;
        String side = CoordinationRepository.sideOf(me, s.need);
        if (side == null) {
            messages.setValue(new Event<>("Tài khoản của bạn không thuộc hai bên đang phối hợp nhu cầu này."));
            return;
        }
        List<String> mine = new ArrayList<>();
        for (ScheduleSession x : s.schedules) {
            if (x.isPendingReschedule() && side.equals(x.side())) mine.add(x.getScheduleId());
        }
        String done = ScheduleSession.SIDE_RECORDER.equals(side)
                ? "Đã gửi yêu cầu đổi lịch. Lịch cũ giữ nguyên cho tới khi tổ chức xác nhận."
                : "Đã gửi yêu cầu đổi lịch. Lịch cũ giữ nguyên cho tới khi đầu mối hỏi ý kiến cụ và xác nhận.";
        run(coordination.proposeReschedule(needId, assignmentId, me, side, startAt, endAt, format, reason,
                consentMethod, mine), done, false, true);
    }

    public void confirmReschedule(String scheduleId, boolean elderAgreed, @Nullable String consentMethod) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.confirmReschedule(needId, scheduleId, me, elderAgreed, consentMethod),
                "Đã đổi sang lịch mới. Lịch cũ vẫn được giữ trong lịch sử.", false, false);
    }

    public void declineReschedule(String scheduleId, String reason) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.declineReschedule(needId, scheduleId, me, reason),
                "Đã từ chối giờ mới. Lịch đang hẹn giữ nguyên.", false, false);
    }

    public void withdrawReschedule(String scheduleId) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.withdrawReschedule(needId, scheduleId, me), "Đã rút lại yêu cầu đổi lịch.", false, false);
    }

    // ----- F13 · Báo hỗ trợ và đối soát -----

    public void reportDelivered(String report) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.reportDelivered(needId, me, report),
                "Đã báo hỗ trợ. Chờ đầu mối đối soát với người cao tuổi.", false);
    }

    public void noteReconcile(String note) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.noteReconcile(needId, me, note), "Đã ghi nội dung chưa khớp để tổ chức xem lại.", false);
    }

    public void complete() {
        Membership me = membership();
        if (me == null) return;
        run(coordination.complete(needId, me, null), "Đã xác nhận hoàn thành nhu cầu.", false);
    }

    private void run(Task<?> task, String successMessage, boolean endEditing) {
        run(task, successMessage, endEditing, false);
    }

    private void run(Task<?> task, String successMessage, boolean endEditing, boolean resetReschedule) {
        setBusy(true);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            if (t.isSuccessful()) {
                if (endEditing) draft.editing = false;
                if (resetReschedule) {
                    rescheduleDraft.dateSet = false;
                    rescheduleDraft.startAt = rescheduleDraft.endAt = 0;
                    rescheduleSent.setValue(new Event<>(true));
                }
                done.setValue(new Event<>(successMessage));
            } else {
                messages.setValue(new Event<>(NeedErrors.message(t.getException())));
            }
        });
    }

    @Nullable
    private Membership membership() {
        Ui s = ui.getValue();
        if (s == null || s.membership == null) {
            messages.setValue(new Event<>("Tài khoản chưa được cấp quyền tổ chức."));
            return null;
        }
        return s.membership;
    }

    private void setBusy(boolean busy) {
        Ui s = copy();
        s.busy = busy;
        ui.setValue(s);
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.membership = old.membership;
            s.need = old.need;
            s.schedules = old.schedules;
            s.busy = old.busy;
        }
        return s;
    }

    private void stop() {
        for (ListenerRegistration r : registrations) r.remove();
        registrations.clear();
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
