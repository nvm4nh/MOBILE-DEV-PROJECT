package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.seniortribe.app.data.EventRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.models.EventForm;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.Event;

/**
 * E07 · Tạo sự kiện (đại diện tổ chức). Ngày, giờ, số chỗ và hai công tắc giữ trong ViewModel
 * để không mất khi xoay màn hình; các ô chữ do EditText tự giữ.
 */
public class CreateEventViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public boolean busy;
    }

    /** Phần form không nằm trong ô nhập chữ. */
    public static class Draft {
        public long startAt;   // 0 = chưa chọn ngày
        public long endAt;     // 0 = chưa chọn giờ
        public boolean dateSet;
        public int capacity = 20;
        public boolean allowCancel = true;
        public boolean openToOtherOrgs = true;
    }

    private final EventRepository repository = new EventRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> created = new MutableLiveData<>();
    private final Draft draft = new Draft();
    private boolean started;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Tạo xong; giá trị là id sự kiện. */
    public LiveData<Event<String>> getCreated() { return created; }
    public Draft getDraft() { return draft; }

    public void start() {
        if (started) return;
        started = true;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = new Ui();
            s.loading = false;
            if (t.isSuccessful()) s.membership = t.getResult();
            else s.error = NeedErrors.message(t.getException());
            ui.setValue(s);
        });
    }

    public void changeCapacity(int delta) {
        draft.capacity = Math.max(1, Math.min(EventRepository.MAX_CAPACITY, draft.capacity + delta));
    }

    public void submit(EventForm form) {
        Ui current = ui.getValue();
        if (current == null || current.busy) return;
        Ui busy = copy(current);
        busy.busy = true;
        ui.setValue(busy);
        repository.createEvent(current.membership, form).addOnCompleteListener(t -> {
            Ui s = copy(ui.getValue());
            s.busy = false;
            ui.setValue(s);
            if (t.isSuccessful()) created.setValue(new Event<>(t.getResult()));
            else messages.setValue(new Event<>(NeedErrors.message(t.getException())));
        });
    }

    private static Ui copy(Ui old) {
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.membership = old.membership;
            s.busy = old.busy;
        }
        return s;
    }
}
