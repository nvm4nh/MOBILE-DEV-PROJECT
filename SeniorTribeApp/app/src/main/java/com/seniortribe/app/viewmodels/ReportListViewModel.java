package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.models.Membership;

import java.util.ArrayList;
import java.util.List;

/**
 * E03 · Danh sách phản ánh.
 *  - "Cần xử lý": phản ánh điều phối viên của đơn vị phải xử lý (chỉ tài khoản có vai trò REVIEWER).
 *  - "Tôi đã gửi": phản ánh do chính tài khoản này gửi.
 */
public class ReportListViewModel extends ViewModel {

    public static final int MODE_HANDLE = 0;
    public static final int MODE_MINE = 1;

    public static class Ui {
        public boolean loading = true;
        public String error;
        public boolean noMembership;
        public Membership membership;
        public int mode = MODE_MINE;
        public List<FeedbackReport> toHandle = new ArrayList<>();
        public List<FeedbackReport> mine = new ArrayList<>();
        public boolean fromCache;

        public boolean canHandle() { return membership != null && membership.isReviewer(); }

        public List<FeedbackReport> visible() { return mode == MODE_HANDLE ? toHandle : mine; }

        /** Số phản ánh mới chưa ai nhận xử lý, để ghi lên nút "Cần xử lý". */
        public int newCount() {
            int n = 0;
            for (FeedbackReport r : toHandle) if (r.isNew()) n++;
            return n;
        }
    }

    private final FeedbackRepository feedback = new FeedbackRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private boolean started;

    public LiveData<Ui> getUi() { return ui; }

    public void start() {
        if (started) return;
        started = true;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = copy();
            if (!t.isSuccessful()) {
                s.loading = false;
                s.error = NeedErrors.message(t.getException());
                ui.setValue(s);
                return;
            }
            Membership me = t.getResult();
            if (me == null) {
                s.loading = false;
                s.noMembership = true;
                ui.setValue(s);
                return;
            }
            s.membership = me;
            s.mode = me.isReviewer() ? MODE_HANDLE : MODE_MINE;
            ui.setValue(s);
            listen(me);
        });
    }

    public void retry() {
        stop();
        started = false;
        ui.setValue(new Ui());
        start();
    }

    public void setMode(int mode) {
        Ui s = copy();
        if (mode == MODE_HANDLE && !s.canHandle()) return;
        s.mode = mode;
        ui.setValue(s);
    }

    private void listen(Membership me) {
        registrations.add(feedback.listenMyReports(me.getUid(), new NeedRepository.Callback<List<FeedbackReport>>() {
            @Override public void onData(List<FeedbackReport> data, boolean fromCache) {
                Ui s = copy();
                s.mine = data;
                s.loading = false;
                s.error = null;
                s.fromCache = fromCache;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) { fail(error); }
        }));
        if (me.isReviewer()) {
            registrations.add(feedback.listenReportsToHandle(me.getOrgId(), new NeedRepository.Callback<List<FeedbackReport>>() {
                @Override public void onData(List<FeedbackReport> data, boolean fromCache) {
                    Ui s = copy();
                    // Không tự xử lý phản ánh của chính mình
                    List<FeedbackReport> list = new ArrayList<>();
                    for (FeedbackReport r : data) if (!me.getUid().equals(r.getCreatedByUid())) list.add(r);
                    s.toHandle = list;
                    s.loading = false;
                    s.error = null;
                    s.fromCache = fromCache;
                    ui.setValue(s);
                }

                @Override public void onError(Exception error) { fail(error); }
            }));
        }
    }

    private void fail(Exception error) {
        Ui s = copy();
        s.loading = false;
        s.error = NeedErrors.message(error);
        ui.setValue(s);
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.noMembership = old.noMembership;
            s.membership = old.membership;
            s.mode = old.mode;
            s.toHandle = old.toHandle;
            s.mine = old.mine;
            s.fromCache = old.fromCache;
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
