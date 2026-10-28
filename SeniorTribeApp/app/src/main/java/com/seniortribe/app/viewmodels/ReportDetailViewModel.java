package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.ReportNote;
import com.seniortribe.app.utils.Event;

import java.util.ArrayList;
import java.util.List;

/** E03 · Chi tiết một phản ánh và các thao tác xử lý (điều phối viên) hoặc bổ sung (người gửi). */
public class ReportDetailViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public FeedbackReport report;
        public List<ReportNote> notes = new ArrayList<>();
        public boolean busy;
    }

    private final FeedbackRepository feedback = new FeedbackRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> done = new MutableLiveData<>();
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private String reportId;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Một thao tác vừa thành công; giá trị là câu thông báo. */
    public LiveData<Event<String>> getDone() { return done; }

    public void start(String reportId) {
        if (this.reportId != null) return;
        this.reportId = reportId;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = copy();
            if (!t.isSuccessful()) {
                s.loading = false;
                s.error = NeedErrors.message(t.getException());
                ui.setValue(s);
                return;
            }
            s.membership = t.getResult();
            ui.setValue(s);
            listen();
        });
    }

    public void retry() {
        String id = reportId;
        stop();
        reportId = null;
        ui.setValue(new Ui());
        start(id);
    }

    private void listen() {
        registrations.add(feedback.listenReport(reportId, new NeedRepository.Callback<FeedbackReport>() {
            @Override public void onData(FeedbackReport data, boolean fromCache) {
                Ui s = copy();
                s.report = data;
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
        registrations.add(feedback.listenNotes(reportId, new NeedRepository.Callback<List<ReportNote>>() {
            @Override public void onData(List<ReportNote> data, boolean fromCache) {
                Ui s = copy();
                s.notes = data;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                // Không đọc được quá trình xử lý thì vẫn hiện nội dung phản ánh
            }
        }));
    }

    public void startHandling() {
        Ui s = ui.getValue();
        if (s == null || s.report == null) return;
        run(feedback.startHandling(s.report, s.membership), "Đã tiếp nhận phản ánh. Tổ chức liên quan sẽ thấy phản ánh này.");
    }

    public void askMore(String text) {
        Ui s = ui.getValue();
        if (s == null || s.report == null) return;
        run(feedback.askMore(s.report, s.membership, text), "Đã gửi câu hỏi cho người phản ánh.");
    }

    public void addInfo(String text) {
        Ui s = ui.getValue();
        if (s == null || s.report == null) return;
        run(feedback.addInfo(s.report, s.membership, text), "Đã gửi thông tin bổ sung.");
    }

    public void close(String resolution) {
        Ui s = ui.getValue();
        if (s == null || s.report == null) return;
        run(feedback.close(s.report, s.membership, resolution), "Đã đóng phản ánh.");
    }

    private void run(Task<Void> task, String successMessage) {
        setBusy(true);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            if (t.isSuccessful()) done.setValue(new Event<>(successMessage));
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
            s.report = old.report;
            s.notes = old.notes;
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
