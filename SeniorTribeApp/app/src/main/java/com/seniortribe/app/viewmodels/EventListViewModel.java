package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.EventRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.models.Membership;

import java.util.ArrayList;
import java.util.List;

/** E07 · Danh sách sự kiện sắp diễn ra; lọc "Tất cả" / "Tổ chức của tôi". */
public class EventListViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public List<CommunityEvent> events = new ArrayList<>();
        public boolean onlyMine;
        public boolean fromCache;

        public boolean canCreate() { return membership != null && membership.isRepresentative(); }

        public List<CommunityEvent> visible() {
            if (!onlyMine || membership == null) return events;
            List<CommunityEvent> out = new ArrayList<>();
            for (CommunityEvent e : events) if (membership.getOrgId().equals(e.getOrgId())) out.add(e);
            return out;
        }
    }

    private final EventRepository repository = new EventRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private ListenerRegistration registration;
    private boolean started;

    public LiveData<Ui> getUi() { return ui; }

    public void start() {
        if (started) return;
        started = true;
        // Chưa được cấp quyền tổ chức vẫn xem được sự kiện; chỉ không tạo / đăng ký được
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = copy();
            s.membership = t.isSuccessful() ? t.getResult() : null;
            ui.setValue(s);
            listen();
        });
    }

    public void retry() {
        stop();
        started = false;
        ui.setValue(new Ui());
        start();
    }

    public void setOnlyMine(boolean onlyMine) {
        Ui s = copy();
        s.onlyMine = onlyMine;
        ui.setValue(s);
    }

    private void listen() {
        registration = repository.listenUpcoming(new NeedRepository.Callback<List<CommunityEvent>>() {
            @Override public void onData(List<CommunityEvent> data, boolean fromCache) {
                Ui s = copy();
                s.events = data;
                s.loading = false;
                s.error = null;
                s.fromCache = fromCache;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        });
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.membership = old.membership;
            s.events = old.events;
            s.onlyMine = old.onlyMine;
            s.fromCache = old.fromCache;
        }
        return s;
    }

    private void stop() {
        if (registration != null) registration.remove();
        registration = null;
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
