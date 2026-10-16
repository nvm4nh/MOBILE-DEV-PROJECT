package com.seniortribe.app.viewmodels;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Tasks;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedForm;
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.Event;

/**
 * ViewModel của màn hình Ghi nhận / Sửa nhu cầu (F06, F08).
 * Nội dung các ô nhập do chính EditText tự giữ khi xoay; ViewModel giữ phần
 * đã tải từ máy chủ và trạng thái đang gửi để không gửi trùng.
 */
public class NeedFormViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        public boolean editing;
        public NeedSummary existing;   // null khi tạo mới
        public boolean busy;
    }

    /** Dữ liệu để điền sẵn vào form khi sửa (chỉ dùng một lần). */
    public static class Prefill {
        public final NeedSummary summary;
        @Nullable public final NeedPrivateDetails privateDetails;

        Prefill(NeedSummary summary, @Nullable NeedPrivateDetails privateDetails) {
            this.summary = summary;
            this.privateDetails = privateDetails;
        }
    }

    private final NeedRepository repository = new NeedRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<Prefill>> prefill = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> submitted = new MutableLiveData<>();

    private boolean started;
    private String needId;

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<Prefill>> getPrefill() { return prefill; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Phát mã trường hợp sau khi gửi thành công. */
    public LiveData<Event<String>> getSubmitted() { return submitted; }

    public void start(@Nullable String needId) {
        if (started) return;
        started = true;
        this.needId = needId;

        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                fail(NeedErrors.message(task.getException()));
                return;
            }
            Membership me = task.getResult();
            if (me == null || !me.isRecorder()) {
                fail("Chỉ đầu mối ghi nhận của đơn vị mới ghi nhận hoặc sửa được nhu cầu.");
                return;
            }
            if (needId == null) {
                Ui s = new Ui();
                s.loading = false;
                s.membership = me;
                ui.setValue(s);
                return;
            }
            loadExisting(me);
        });
    }

    private void loadExisting(Membership me) {
        repository.loadNeed(needId).<Prefill>continueWithTask(t -> {
            if (!t.isSuccessful()) return Tasks.<Prefill>forException(errorOf(t.getException()));
            NeedSummary need = t.getResult();
            return repository.loadPrivate(needId).<Prefill>continueWith(p -> {
                NeedPrivateDetails details = p.isSuccessful() ? p.getResult() : null;
                return new Prefill(need, details);
            });
        }).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                fail(NeedErrors.message(task.getException()));
                return;
            }
            Prefill data = task.getResult();
            if (!me.belongsToRecorderOf(data.summary)) {
                fail("Nhu cầu này không thuộc đơn vị của bạn.");
                return;
            }
            String status = data.summary.getStatus();
            if (!Constants.STATUS_REVIEW.equals(status) && !Constants.STATUS_OPEN.equals(status)) {
                fail("Nhu cầu đã có tổ chức tiếp nhận hoặc đã kết thúc nên không sửa được nữa.");
                return;
            }
            Ui s = new Ui();
            s.loading = false;
            s.membership = me;
            s.editing = true;
            s.existing = data.summary;
            ui.setValue(s);
            prefill.setValue(new Event<>(data));
        });
    }

    public void submit(NeedForm form) {
        Ui current = ui.getValue();
        if (current == null || current.membership == null || current.busy) return;
        setBusy(true);
        if (current.editing) {
            String code = current.existing.getCaseCode();
            repository.updateNeed(needId, current.membership, form).addOnCompleteListener(task -> {
                setBusy(false);
                if (task.isSuccessful()) submitted.setValue(new Event<>(code));
                else messages.setValue(new Event<>(NeedErrors.message(task.getException())));
            });
        } else {
            repository.createNeed(current.membership, form).addOnCompleteListener(task -> {
                setBusy(false);
                if (task.isSuccessful()) submitted.setValue(new Event<>(task.getResult()));
                else messages.setValue(new Event<>(NeedErrors.message(task.getException())));
            });
        }
    }

    private void setBusy(boolean busy) {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.membership = old.membership;
            s.editing = old.editing;
            s.existing = old.existing;
        }
        s.busy = busy;
        ui.setValue(s);
    }

    private void fail(String message) {
        Ui s = new Ui();
        s.loading = false;
        s.error = message;
        ui.setValue(s);
    }

    private static Exception errorOf(@Nullable Exception e) {
        return e != null ? e : new IllegalStateException("load");
    }
}
