package com.seniortribe.app.viewmodels;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgRating;
import com.seniortribe.app.utils.Event;

import java.util.List;

/**
 * E03 · Đánh giá sau khi hoàn thành. Tải quyền, nhu cầu và đánh giá đã có (nếu có);
 * giữ số sao đang chọn qua xoay màn hình.
 */
public class RateOrgViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public NeedSummary need;
        public OrgRating existing;
        public boolean busy;
    }

    private final NeedRepository needs = new NeedRepository();
    private final FeedbackRepository feedback = new FeedbackRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> submitted = new MutableLiveData<>();

    private String needId;
    private int stars;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    public LiveData<Event<Boolean>> getSubmitted() { return submitted; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = Math.max(0, Math.min(5, stars)); }

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
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) throw t.getException();
                    NeedSummary need = t.getResult();
                    Ui s = copy();
                    s.need = need;
                    ui.setValue(s);
                    String assignmentId = need != null ? need.getCurrentAssignmentId() : null;
                    if (TextUtils.isEmpty(assignmentId)) return com.google.android.gms.tasks.Tasks.forResult((OrgRating) null);
                    return feedback.loadRating(assignmentId);
                })
                .addOnCompleteListener(t -> {
                    Ui s = copy();
                    s.loading = false;
                    if (t.isSuccessful()) {
                        s.existing = t.getResult();
                        s.error = null;
                    } else {
                        s.error = NeedErrors.message(t.getException());
                    }
                    ui.setValue(s);
                });
    }

    public void submit(List<String> tags, String comment) {
        Ui current = ui.getValue();
        if (current == null || current.need == null || current.busy) return;
        setBusy(true);
        feedback.submitRating(current.need, current.membership, stars, tags, comment)
                .addOnCompleteListener(t -> {
                    setBusy(false);
                    if (t.isSuccessful()) {
                        submitted.setValue(new Event<>(true));
                    } else {
                        messages.setValue(new Event<>(NeedErrors.message(t.getException())));
                    }
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
            s.existing = old.existing;
            s.busy = old.busy;
        }
        return s;
    }
}
