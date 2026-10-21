package com.seniortribe.app.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.seniortribe.app.R;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.PostRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.models.PostForm;
import com.seniortribe.app.utils.Event;

/**
 * Màn hình Đăng / sửa bài (F05). Giữ quyền đã kiểm tra và trạng thái "đang gửi" khi xoay màn hình,
 * để không gửi trùng và không mất kết quả trả về từ máy chủ (F16, T04).
 */
public class CreatePostViewModel extends AndroidViewModel {

    public static class Ui {
        public boolean checking = true;
        public boolean busy;
        @Nullable public Membership membership;
        /** Bài đang sửa; null khi đăng bài mới. */
        @Nullable public Post editing;

        public boolean isEditing() {
            return editing != null;
        }

        public boolean canSubmit() {
            if (checking || busy || membership == null) return false;
            return isEditing() ? PostRepository.canManage(membership, editing) : membership.isRepresentative();
        }
    }

    private final PostRepository repository;
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> done = new MutableLiveData<>();
    private boolean started;

    public CreatePostViewModel(@NonNull Application application) {
        super(application);
        repository = new PostRepository(application);
    }

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Phát một lần khi máy chủ đã xác nhận đăng / lưu. */
    public LiveData<Event<Boolean>> getDone() { return done; }

    public void start(@Nullable Post editing) {
        if (started) return;
        started = true;
        Ui first = new Ui();
        first.editing = editing;
        ui.setValue(first);

        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui s = copy();
            s.checking = false;
            s.membership = task.isSuccessful() ? task.getResult() : null;
            ui.setValue(s);
            if (!task.isSuccessful()) {
                messages.setValue(new Event<>(NeedErrors.message(task.getException())));
            } else if (!s.canSubmit()) {
                messages.setValue(new Event<>(getApplication().getString(s.isEditing()
                        ? R.string.post_error_not_owner : R.string.post_error_not_representative)));
            }
        });
    }

    public void submit(PostForm form) {
        Ui current = ui.getValue();
        if (current == null || !current.canSubmit()) return;
        setBusy(true);
        Task<?> task = current.isEditing()
                ? repository.updatePost(current.membership, current.editing, form)
                : repository.createPost(current.membership, form);
        task.addOnCompleteListener(t -> {
            setBusy(false);
            if (t.isSuccessful()) done.setValue(new Event<>(true));
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
            s.checking = old.checking;
            s.busy = old.busy;
            s.membership = old.membership;
            s.editing = old.editing;
        }
        return s;
    }
}
