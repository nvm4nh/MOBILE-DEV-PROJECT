package com.seniortribe.app.viewmodels;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.EventRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.models.EventRegistration;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.Event;

import java.util.ArrayList;
import java.util.List;

/** E07 · Chi tiết sự kiện, danh sách tổ chức đã đăng ký, đăng ký / đổi số người / hủy. */
public class EventDetailViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public CommunityEvent event;
        public List<EventRegistration> registrations = new ArrayList<>();
        public boolean busy;
        /** Số thành viên đang chọn trên bộ tăng giảm. */
        public int people = 1;

        /** Phiếu đăng ký của tổ chức mình (nếu đã đăng ký). */
        @Nullable
        public EventRegistration mine() {
            if (membership == null) return null;
            for (EventRegistration r : registrations) if (membership.getOrgId().equals(r.getOrgId())) return r;
            return null;
        }
    }

    private final EventRepository repository = new EventRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private String eventId;
    private boolean peopleTouched;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }

    public void start(String eventId) {
        if (this.eventId != null) return;
        this.eventId = eventId;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = copy();
            s.membership = t.isSuccessful() ? t.getResult() : null;
            ui.setValue(s);
            listen();
        });
    }

    public void retry() {
        String id = eventId;
        stop();
        eventId = null;
        peopleTouched = false;
        ui.setValue(new Ui());
        start(id);
    }

    private void listen() {
        registrations.add(repository.listenEvent(eventId, new NeedRepository.Callback<CommunityEvent>() {
            @Override public void onData(CommunityEvent data, boolean fromCache) {
                Ui s = copy();
                s.event = data;
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
        registrations.add(repository.listenRegistrations(eventId, new NeedRepository.Callback<List<EventRegistration>>() {
            @Override public void onData(List<EventRegistration> data, boolean fromCache) {
                Ui s = copy();
                s.registrations = data;
                // Lần đầu mở: bộ đếm bắt đầu từ số người tổ chức mình đã đăng ký
                EventRegistration mine = s.mine();
                if (!peopleTouched && mine != null) s.people = (int) Math.max(1, mine.getPeople());
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                // Không đọc được danh sách đăng ký thì vẫn hiện thông tin sự kiện
            }
        }));
    }

    public void changePeople(int delta) {
        Ui s = copy();
        int next = Math.max(1, Math.min(EventRepository.MAX_PEOPLE_PER_ORG, s.people + delta));
        if (next == s.people) return;
        peopleTouched = true;
        s.people = next;
        ui.setValue(s);
    }

    public void register() {
        Ui s = ui.getValue();
        if (s == null || s.event == null || s.busy) return;
        boolean update = s.mine() != null;
        run(repository.register(eventId, s.membership, s.people),
                update ? "Đã cập nhật số thành viên tham gia." : "Đã đăng ký tham gia sự kiện.");
    }

    public void cancelRegistration() {
        Ui s = ui.getValue();
        if (s == null || s.event == null || s.busy) return;
        run(repository.cancelRegistration(eventId, s.membership), "Đã hủy tham gia. Tổ chức tạo sự kiện sẽ thấy số chỗ được trả lại.");
    }

    public void cancelEvent() {
        Ui s = ui.getValue();
        if (s == null || s.event == null || s.busy) return;
        run(repository.cancelEvent(s.event, s.membership), "Đã hủy sự kiện.");
    }

    private void run(Task<Void> task, String successMessage) {
        setBusy(true);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            messages.setValue(new Event<>(t.isSuccessful() ? successMessage : NeedErrors.message(t.getException())));
        });
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
            s.event = old.event;
            s.registrations = old.registrations;
            s.busy = old.busy;
            s.people = old.people;
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
