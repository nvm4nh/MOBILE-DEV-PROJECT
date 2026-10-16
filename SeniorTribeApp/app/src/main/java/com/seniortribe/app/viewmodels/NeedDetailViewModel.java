package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgMember;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.Event;

/**
 * Dùng chung cho màn hình Chi tiết nhu cầu (F10, F14) và Rà soát nhu cầu (F07).
 *
 * Theo dõi nhu cầu realtime, quyết định có được xem thông tin riêng hay không,
 * và thực hiện các thao tác đổi trạng thái qua NeedRepository.
 */
public class NeedDetailViewModel extends ViewModel {

    /** Trạng thái của thẻ "Thông tin riêng". */
    public static final int PRIVATE_HIDDEN = 0;   // không có quyền xem → hiện vạch che
    public static final int PRIVATE_LOADING = 1;
    public static final int PRIVATE_SHOWN = 2;
    public static final int PRIVATE_ERROR = 3;

    /** Ảnh chụp trạng thái màn hình. */
    public static class Ui {
        public boolean loading = true;
        public String error;
        public NeedSummary need;
        public Membership membership;
        public int privateState = PRIVATE_HIDDEN;
        public NeedPrivateDetails privateDetails;
        public boolean busy;
        public boolean fromCache;
    }

    private final NeedRepository repository = new NeedRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> closeScreen = new MutableLiveData<>();

    private String needId;
    private ListenerRegistration registration;
    private String privateLoadedKey;   // tránh tải lại thông tin riêng với mỗi snapshot

    public LiveData<Ui> getUi() { return ui; }

    public LiveData<Event<String>> getMessages() { return messages; }

    public LiveData<Event<Boolean>> getCloseScreen() { return closeScreen; }

    public void start(String needId) {
        if (this.needId != null) return; // đã chạy, ví dụ sau khi xoay màn hình
        this.needId = needId;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui s = copy();
            if (!task.isSuccessful()) {
                s.loading = false;
                s.error = NeedErrors.message(task.getException());
                ui.setValue(s);
                return;
            }
            s.membership = task.getResult();
            ui.setValue(s);
            listen();
        });
    }

    public void retry() {
        if (needId == null) return;
        String id = needId;
        stop();
        needId = null;
        privateLoadedKey = null;
        ui.setValue(new Ui());
        start(id);
    }

    private void listen() {
        registration = repository.listenNeed(needId, new NeedRepository.Callback<NeedSummary>() {
            @Override public void onData(NeedSummary data, boolean fromCache) {
                Ui s = copy();
                s.loading = false;
                s.error = null;
                s.need = data;
                s.fromCache = fromCache;
                ui.setValue(s);
                updatePrivate(data);
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        });
    }

    /**
     * Quyền xem thông tin riêng (R03): thành viên đơn vị ghi nhận, hoặc đúng đại diện
     * được giao sau khi đã chốt lịch. Máy chủ kiểm tra lại bằng Security Rules.
     */
    public static boolean canSeePrivate(Membership me, NeedSummary need) {
        if (me == null || need == null) return false;
        if (me.belongsToRecorderOf(need)) return true;
        boolean scheduled = Constants.STATUS_SCHEDULED.equals(need.getStatus())
                || Constants.STATUS_DELIVERED.equals(need.getStatus());
        return scheduled && me.getUid().equals(need.getAssigneeUid());
    }

    private void updatePrivate(NeedSummary need) {
        Ui current = ui.getValue();
        Membership me = current != null ? current.membership : null;
        if (!canSeePrivate(me, need)) {
            privateLoadedKey = null;
            Ui s = copy();
            s.privateState = PRIVATE_HIDDEN;
            s.privateDetails = null;
            ui.setValue(s);
            return;
        }
        String key = need.getStatus() + "|" + need.getAssigneeUid();
        if (key.equals(privateLoadedKey)) return;
        privateLoadedKey = key;

        Ui loading = copy();
        loading.privateState = PRIVATE_LOADING;
        ui.setValue(loading);
        repository.loadPrivate(needId).addOnCompleteListener(task -> {
            Ui s = copy();
            if (task.isSuccessful() && task.getResult() != null) {
                s.privateState = PRIVATE_SHOWN;
                s.privateDetails = task.getResult();
            } else {
                s.privateState = PRIVATE_ERROR;
                s.privateDetails = null;
                privateLoadedKey = null;
            }
            ui.setValue(s);
        });
    }

    // ----- Thao tác -----

    public void approve() {
        Membership me = membership();
        if (me == null) return;
        run(repository.approve(needId, me), "Đã duyệt. Nhu cầu chuyển sang Chờ tiếp nhận.", true);
    }

    public void requestRevision(String note) {
        Membership me = membership();
        if (me == null) return;
        run(repository.requestRevision(needId, me, note), "Đã gửi yêu cầu bổ sung cho đầu mối.", true);
    }

    public void accept(OrgMember assignee) {
        Membership me = membership();
        if (me == null) return;
        run(repository.accept(needId, me, assignee),
                "Đã tiếp nhận. " + assignee.getDisplayName() + " phụ trách nhu cầu này.", false);
    }

    public void returnNeed(String reason, String note) {
        Membership me = membership();
        if (me == null) return;
        run(repository.returnNeed(needId, me, reason, note),
                "Đã trả lại. Nhu cầu quay về Chờ tiếp nhận.", false);
    }

    public void cancel(String reason, String note) {
        Membership me = membership();
        if (me == null) return;
        run(repository.cancel(needId, me, reason, note), "Đã hủy nhu cầu.", false);
    }

    private void run(Task<?> task, String successMessage, boolean closeAfter) {
        setBusy(true);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            if (t.isSuccessful()) {
                messages.setValue(new Event<>(successMessage));
                if (closeAfter) closeScreen.setValue(new Event<>(true));
            } else {
                messages.setValue(new Event<>(NeedErrors.message(t.getException())));
            }
        });
    }

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

    /** Sao chép trạng thái hiện tại để sửa rồi phát lại (LiveData nên nhận object mới). */
    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.need = old.need;
            s.membership = old.membership;
            s.privateState = old.privateState;
            s.privateDetails = old.privateDetails;
            s.busy = old.busy;
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
