package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Event;

/** E03 · Gửi phản ánh về lần hỗ trợ của một nhu cầu. */
public class ReportFormViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public NeedSummary need;
        public boolean busy;
    }

    private final NeedRepository needs = new NeedRepository();
    private final FeedbackRepository feedback = new FeedbackRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> submitted = new MutableLiveData<>();

    private String needId;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Gửi xong; giá trị là id phản ánh. */
    public LiveData<Event<String>> getSubmitted() { return submitted; }

    public void start(String needId) {
        if (this.needId != null) return;
        this.needId = needId;
        load();
    }

    public void retry() {
        ui.setValue(new Ui());
        load();
    }

    private void load() {
        MembershipRepository.get().loadCurrent(false)
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) throw t.getException();
                    Ui s = copy();
                    s.membership = t.getResult();
                    ui.setValue(s);
                    return needs.loadNeed(needId);
                })
                .addOnCompleteListener(t -> {
                    Ui s = copy();
                    s.loading = false;
                    if (t.isSuccessful()) {
                        s.need = t.getResult();
                        s.error = null;
                    } else {
                        s.error = NeedErrors.message(t.getException());
                    }
                    ui.setValue(s);
                });
    }

    public void submit(String type, String description) {
        Ui current = ui.getValue();
        if (current == null || current.need == null || current.busy) return;
        setBusy(true);
        feedback.submitReport(current.need, current.membership, type, description)
                .addOnCompleteListener(t -> {
                    setBusy(false);
                    if (t.isSuccessful()) submitted.setValue(new Event<>(t.getResult()));
                    else messages.setValue(new Event<>(NeedErrors.message(t.getException())));
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
            s.need = old.need;
            s.busy = old.busy;
        }
        return s;
    }
}
