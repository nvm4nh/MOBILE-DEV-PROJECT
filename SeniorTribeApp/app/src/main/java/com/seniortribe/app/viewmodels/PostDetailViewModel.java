package com.seniortribe.app.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.seniortribe.app.R;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.PostRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Event;
import com.seniortribe.app.utils.NetworkUtils;

/**
 * Màn hình Chi tiết bài đăng (F04) và thao tác Sửa / Ẩn của đại diện tổ chức (F05).
 * Giữ bài và trạng thái đang ẩn khi xoay màn hình.
 */
public class PostDetailViewModel extends AndroidViewModel {

    public static class Ui {
        public Post post;
        @Nullable public Membership membership;
        public boolean busy;

        /** Đúng đại diện của tổ chức đã đăng, và bài không phải bản lưu offline. */
        public boolean canManage() {
            return post != null && !post.isCached() && PostRepository.canManage(membership, post);
        }
    }

    private final PostRepository repository;
    private final MutableLiveData<Ui> ui = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> closed = new MutableLiveData<>();
    private boolean started;

    public PostDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new PostRepository(application);
    }

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }
    /** Bài đã bị ẩn: đóng màn hình và báo Bảng tin tải lại. */
    public LiveData<Event<Boolean>> getClosed() { return closed; }

    public void start(Post post) {
        if (started) return;
        started = true;
        Ui s = new Ui();
        s.post = post;
        ui.setValue(s);
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui d = copy();
            d.membership = task.isSuccessful() ? task.getResult() : null;
            ui.setValue(d);
        });
    }

    /** Sau khi sửa bài: lấy lại bản mới nhất từ máy chủ. */
    public void reload() {
        Ui current = ui.getValue();
        if (current == null || current.post == null || current.post.getId() == null) return;
        repository.loadPost(current.post.getId()).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                messages.setValue(new Event<>(NeedErrors.message(task.getException())));
                return;
            }
            Ui d = copy();
            d.post = task.getResult();
            ui.setValue(d);
        });
    }

    public void hide() {
        Ui current = ui.getValue();
        if (current == null || current.busy || !current.canManage()) return;
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            messages.setValue(new Event<>(getApplication().getString(R.string.action_needs_network)));
            return;
        }
        setBusy(true);
        repository.hidePost(current.membership, current.post).addOnCompleteListener(task -> {
            setBusy(false);
            if (task.isSuccessful()) {
                messages.setValue(new Event<>(getApplication().getString(R.string.post_hidden)));
                closed.setValue(new Event<>(true));
            } else {
                messages.setValue(new Event<>(NeedErrors.message(task.getException())));
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
            s.post = old.post;
            s.membership = old.membership;
            s.busy = old.busy;
        }
        return s;
    }
}
