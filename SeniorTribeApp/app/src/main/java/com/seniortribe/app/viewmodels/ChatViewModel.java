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
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.Event;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Màn hình chat phối hợp của một lần tiếp nhận (F11) kèm các bước lịch hẹn (F12) và
 * báo hỗ trợ / đối soát (F13). Giữ dữ liệu khi xoay màn hình (T04).
 */
public class ChatViewModel extends ViewModel {

    /** Độ dài tối đa một tin nhắn, khớp với Security Rules. */
    public static final int MAX_MESSAGE_LENGTH = 1000;

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public NeedSummary need;
        public List<ChatMessage> messages = new ArrayList<>();
        public List<ScheduleSession> schedules = new ArrayList<>();
        public boolean busy;

        /** Đề xuất lịch đang chờ đầu mối trả lời (mới nhất). */
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
    }

    private final NeedRepository needs = new NeedRepository();
    private final CoordinationRepository coordination = new CoordinationRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> messageSent = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> sendFailed = new MutableLiveData<>();

    private String needId;
    private String assignmentId;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public LiveData<Ui> getUi() { return ui; }

    public LiveData<Event<String>> getMessages() { return messages; }

    public LiveData<Event<Boolean>> getMessageSent() { return messageSent; }

    /** Gửi thất bại: giá trị là nội dung vừa gõ, Activity trả lại vào ô nhập. */
    public LiveData<Event<String>> getSendFailed() { return sendFailed; }

    public String getAssignmentId() { return assignmentId; }

    public void start(String needId, String assignmentId) {
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
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                // Người phụ trách cũ không còn đọc được nhu cầu sau khi trả lại: vẫn xem chat cũ, chỉ đọc
                Ui s = copy();
                s.need = null;
                s.loading = false;
                ui.setValue(s);
            }
        }));
        registrations.add(coordination.listenMessages(assignmentId, new NeedRepository.Callback<List<ChatMessage>>() {
            @Override public void onData(List<ChatMessage> data, boolean fromCache) {
                Ui s = copy();
                s.messages = data;
                s.loading = false;
                s.error = null;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        }));
        registrations.add(coordination.listenSchedules(assignmentId, new NeedRepository.Callback<List<ScheduleSession>>() {
            @Override public void onData(List<ScheduleSession> data, boolean fromCache) {
                Ui s = copy();
                s.schedules = data;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                // Không đọc được lịch thì vẫn cho chat; thẻ phối hợp chỉ hiện trạng thái
            }
        }));
    }

    // ----- Thao tác -----

    /**
     * Gửi tin nhắn. Ô nhập chỉ được xóa khi tin hợp lệ và đã được giao cho Firestore; nếu máy
     * chủ từ chối (ví dụ lần tiếp nhận vừa kết thúc) thì nội dung được trả lại ô nhập, không mất.
     */
    public void send(String text) {
        Membership me = membership();
        if (me == null) return;
        final String clean = text != null ? text.trim() : "";
        if (clean.isEmpty()) return;
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            messages.setValue(new Event<>("Tin nhắn dài quá " + MAX_MESSAGE_LENGTH + " ký tự."));
            return;
        }
        coordination.sendMessage(assignmentId, me, clean).addOnCompleteListener(t -> {
            if (!t.isSuccessful()) {
                sendFailed.setValue(new Event<>(clean));
                messages.setValue(new Event<>("Chưa gửi được tin nhắn: " + NeedErrors.message(t.getException())
                        + " Nội dung đã được giữ lại trong ô nhập."));
            }
        });
        // Firestore ghi vào cache rồi mới đồng bộ, nên xóa ô nhập ngay cho mượt
        messageSent.setValue(new Event<>(true));
    }

    public void proposeSchedule(Date startAt, String place, String note) {
        Membership me = membership();
        if (me == null) return;
        List<String> pending = new ArrayList<>();
        Ui s = ui.getValue();
        if (s != null) for (ScheduleSession x : s.schedules) if (x.isPending()) pending.add(x.getScheduleId());
        run(coordination.proposeSchedule(needId, assignmentId, me, startAt, place, note, pending),
                "Đã gửi đề xuất lịch. Chờ đầu mối hỏi ý kiến người cao tuổi.");
    }

    public void confirmSchedule(String scheduleId, String consentMethod) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.confirmSchedule(needId, scheduleId, me, consentMethod),
                "Đã chốt lịch. Nhu cầu chuyển sang Đã thống nhất lịch.");
    }

    public void declineSchedule(String scheduleId, String reason) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.declineSchedule(needId, scheduleId, me, reason),
                "Đã báo tổ chức đề xuất lịch khác.");
    }

    public void reportDelivered(String report) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.reportDelivered(needId, me, report),
                "Đã báo hỗ trợ. Chờ đầu mối đối soát với người cao tuổi.");
    }

    public void noteReconcile(String note) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.noteReconcile(needId, me, note), "Đã ghi nội dung cần đối soát.");
    }

    public void complete(String note) {
        Membership me = membership();
        if (me == null) return;
        run(coordination.complete(needId, me, note), "Đã xác nhận hoàn thành nhu cầu.");
    }

    private void run(Task<?> task, String successMessage) {
        setBusy(true);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            messages.setValue(new Event<>(t.isSuccessful() ? successMessage : NeedErrors.message(t.getException())));
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
            s.messages = old.messages;
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
