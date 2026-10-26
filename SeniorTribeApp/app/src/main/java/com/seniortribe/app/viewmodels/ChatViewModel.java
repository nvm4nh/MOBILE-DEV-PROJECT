package com.seniortribe.app.viewmodels;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.CallRepository;
import com.seniortribe.app.data.ChatMediaRepository;
import com.seniortribe.app.models.CallRecord;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Màn hình chat phối hợp của một lần tiếp nhận (F11). Ngoài tin nhắn, theo dõi các đề xuất
 * lịch để chèn tin hệ thống "Đề xuất lịch: …" vào đúng chỗ trong luồng chat. Các bước thống
 * nhất lịch (F12) và đối soát (F13) nằm ở màn hình riêng. Giữ dữ liệu khi xoay màn hình (T04).
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
        public List<CallRecord> calls = new ArrayList<>();          // E06 · nhật ký cuộc gọi
        @Nullable public ChatMediaRepository.Prepared pendingAttachment; // E06 · chờ người dùng xác nhận gửi
        public boolean preparing;                                   // đang đọc / nén tệp
        public int uploadPercent = -1;                               // -1 = không gửi tệp nào
    }

    private final NeedRepository needs = new NeedRepository();
    private final CoordinationRepository coordination = new CoordinationRepository();
    private final ChatMediaRepository media = new ChatMediaRepository();
    private final CallRepository callsRepo = new CallRepository();
    private final MutableLiveData<Event<CallRecord>> incomingCall = new MutableLiveData<>();
    private final Set<String> announcedCalls = new HashSet<>();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> messageSent = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> sendFailed = new MutableLiveData<>();

    private String needId;
    private String assignmentId;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public LiveData<Ui> getUi() { return ui; }

    public LiveData<Event<String>> getMessages() { return messages; }

    /** Tin đã được nhận để gửi: Activity xóa ô nhập. */
    public LiveData<Event<Boolean>> getMessageSent() { return messageSent; }

    /** Gửi thất bại: giá trị là nội dung vừa gõ, Activity trả lại vào ô nhập. */
    public LiveData<Event<String>> getSendFailed() { return sendFailed; }

    public String getAssignmentId() { return assignmentId; }

    /** E06 · Có cuộc gọi đến (vừa đổ chuông) cho tài khoản này trong luồng chat. */
    public LiveData<Event<CallRecord>> getIncomingCall() { return incomingCall; }

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
                // Không đọc được lịch thì vẫn chat bình thường, chỉ thiếu tin hệ thống về lịch
            }
        }));
        registrations.add(callsRepo.listenCalls(assignmentId, new NeedRepository.Callback<List<CallRecord>>() {
            @Override public void onData(List<CallRecord> data, boolean fromCache) {
                Ui s = copy();
                s.calls = data;
                ui.setValue(s);
                Membership me = s.membership;
                if (me == null) return;
                long now = System.currentTimeMillis();
                for (CallRecord c : data) {
                    if (me.getUid().equals(c.getCalleeUid()) && c.isFreshRinging(now)
                            && announcedCalls.add(c.getCallId())) {
                        incomingCall.setValue(new Event<>(c));
                    }
                }
            }

            @Override public void onError(Exception error) {
                // Không đọc được nhật ký cuộc gọi thì vẫn chat bình thường
            }
        }));
    }

    // =====================================================================
    // E06 · Ảnh / tệp
    // =====================================================================

    /** Đọc và (với ảnh) nén tệp vừa chọn; xong thì Activity hỏi xác nhận trước khi gửi. */
    public void prepareAttachment(Context context, Uri uri, boolean image) {
        Ui start = copy();
        if (start.preparing || start.uploadPercent >= 0) return;
        start.preparing = true;
        ui.setValue(start);
        Task<ChatMediaRepository.Prepared> task = image ? media.prepareImage(context, uri) : media.prepareFile(context, uri);
        task.addOnCompleteListener(t -> {
            Ui s = copy();
            s.preparing = false;
            if (t.isSuccessful()) s.pendingAttachment = t.getResult();
            else messages.setValue(new Event<>(NeedErrors.message(t.getException())));
            ui.setValue(s);
        });
    }

    /** Người dùng bỏ không gửi tệp đang chờ. */
    public void discardAttachment() {
        Ui s = copy();
        s.pendingAttachment = null;
        ui.setValue(s);
    }

    /** Gửi tệp đang chờ kèm chú thích; hiện tiến độ theo từng mảnh. */
    public void sendAttachment(@Nullable String caption) {
        Membership me = membership();
        Ui s0 = copy();
        ChatMediaRepository.Prepared p = s0.pendingAttachment;
        if (me == null || p == null || s0.uploadPercent >= 0) return;
        s0.pendingAttachment = null;
        s0.uploadPercent = 0;
        ui.setValue(s0);
        media.send(assignmentId, me, p, caption, (sent, total) -> {
            Ui s = copy();
            if (s.uploadPercent < 0) return;
            s.uploadPercent = (int) Math.min(99, sent * 100 / Math.max(1, total));
            ui.setValue(s);
        }).addOnCompleteListener(t -> {
            Ui s = copy();
            s.uploadPercent = -1;
            ui.setValue(s);
            if (!t.isSuccessful()) {
                messages.setValue(new Event<>("Chưa gửi được tệp: " + NeedErrors.message(t.getException())));
            }
        });
    }

    /**
     * Gửi tin nhắn. Ô nhập chỉ được xóa khi tin hợp lệ và đã được giao cho Firestore; nếu máy
     * chủ từ chối (ví dụ lần tiếp nhận vừa kết thúc) thì nội dung được trả lại ô nhập, không mất.
     */
    public void send(@Nullable String text) {
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
        // Firestore ghi vào cache rồi mới đồng bộ: tin hiện ngay với nhãn "Đang gửi…"
        messageSent.setValue(new Event<>(true));
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
            s.calls = old.calls;
            s.pendingAttachment = old.pendingAttachment;
            s.preparing = old.preparing;
            s.uploadPercent = old.uploadPercent;
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
