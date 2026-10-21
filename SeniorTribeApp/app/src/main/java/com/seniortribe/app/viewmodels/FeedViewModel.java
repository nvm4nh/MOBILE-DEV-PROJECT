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

import java.util.ArrayList;
import java.util.List;

/**
 * Trạng thái tab Bảng tin (F04, F05, F16). Giữ danh sách và bộ lọc khi xoay màn hình (T04),
 * quyết định đang xem bài mới từ máy chủ hay bài đã lưu trong SQLite (T05).
 */
public class FeedViewModel extends AndroidViewModel {

    /** Bài lấy thẳng từ máy chủ. */
    public static final int SOURCE_SERVER = 0;
    /** Không có mạng, đang xem bài đã lưu. */
    public static final int SOURCE_CACHE_OFFLINE = 1;
    /** Có mạng nhưng máy chủ lỗi, đang xem bài đã lưu. */
    public static final int SOURCE_CACHE_STALE = 2;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_MY_ORG = 1;

    public static class Ui {
        /** Lần tải đầu, chưa có gì để hiện. */
        public boolean loading = true;
        /** Đang tải lại trong khi danh sách cũ vẫn hiện (kéo để làm mới). */
        public boolean refreshing;
        /** Đang ẩn bài, chờ máy chủ xác nhận. */
        public boolean busy;
        public List<Post> posts = new ArrayList<>();
        public int source = SOURCE_SERVER;
        public int filter = FILTER_ALL;
        /** Lỗi khi không có bài nào để hiện (kể cả trong bộ đệm). */
        @Nullable public String error;
        /** Mất mạng và chưa từng tải bài nào. */
        public boolean offlineNoCache;
        /** Quyền của tài khoản (null khi chưa được cấp quyền hoặc chưa tải được). */
        @Nullable public Membership membership;

        public boolean isFromCache() {
            return source != SOURCE_SERVER;
        }

        /** Tài khoản là đại diện tổ chức nên được đăng bài (F05). */
        public boolean canPost() {
            return membership != null && membership.isRepresentative();
        }

        /** Bài theo bộ lọc "Tất cả" / "Đơn vị của tôi" (lọc ở máy, không cần index). */
        public List<Post> visiblePosts() {
            if (filter != FILTER_MY_ORG) return posts;
            List<Post> mine = new ArrayList<>();
            String orgId = membership != null ? membership.getOrgId() : null;
            if (orgId == null) return mine;
            for (Post p : posts) if (orgId.equals(p.getOrgId())) mine.add(p);
            return mine;
        }
    }

    private final PostRepository repository;
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private boolean started;
    private boolean inFlight;

    public FeedViewModel(@NonNull Application application) {
        super(application);
        repository = new PostRepository(application);
    }

    public LiveData<Ui> getUi() { return ui; }
    public LiveData<Event<String>> getMessages() { return messages; }

    public void start() {
        if (started) return;   // đã chạy, ví dụ sau khi xoay màn hình
        started = true;
        loadMembership();
        load();
    }

    /** Kéo để làm mới, bấm "Thử lại", hoặc vừa đăng / sửa / ẩn bài xong. */
    public void refresh() {
        Ui s = ui.getValue();
        if (s != null && s.membership == null) loadMembership();
        load();
    }

    public void setFilter(int filter) {
        Ui s = copy();
        if (s.filter == filter) return;
        s.filter = filter;
        ui.setValue(s);
    }

    /** F05 · Ẩn bài khỏi bảng tin; bài biến mất khỏi danh sách khi máy chủ đã xác nhận. */
    public void hide(Post post) {
        Ui current = ui.getValue();
        if (current == null || current.busy) return;
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            messages.setValue(new Event<>(getApplication().getString(R.string.action_needs_network)));
            return;
        }
        setBusy(true);
        repository.hidePost(current.membership, post).addOnCompleteListener(task -> {
            Ui s = copy();
            s.busy = false;
            if (task.isSuccessful()) {
                List<Post> rest = new ArrayList<>();
                for (Post p : s.posts) if (!p.getId().equals(post.getId())) rest.add(p);
                s.posts = rest;
                repository.saveToCache(rest);
                messages.setValue(new Event<>(getApplication().getString(R.string.post_hidden)));
            } else {
                messages.setValue(new Event<>(NeedErrors.message(task.getException())));
            }
            ui.setValue(s);
        });
    }

    private void loadMembership() {
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui s = copy();
            s.membership = task.isSuccessful() ? task.getResult() : null;
            ui.setValue(s);
        });
    }

    private void load() {
        if (inFlight) return;
        inFlight = true;

        Ui s = copy();
        s.loading = s.posts.isEmpty();
        s.refreshing = !s.posts.isEmpty();
        s.error = null;
        s.offlineNoCache = false;
        ui.setValue(s);

        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            showCache(true, null);
            return;
        }

        repository.loadFromServer().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                // Mạng rớt giữa chừng hoặc máy chủ lỗi: chuyển sang bài đã lưu
                showCache(!NetworkUtils.isNetworkAvailable(getApplication()), task.getException());
                return;
            }
            List<Post> posts = task.getResult();
            repository.saveToCache(posts);

            Ui d = copy();
            d.loading = false;
            d.refreshing = false;
            d.posts = posts;
            d.source = SOURCE_SERVER;
            d.error = null;
            d.offlineNoCache = false;
            ui.setValue(d);
            inFlight = false;
        });
    }

    private void showCache(boolean offline, @Nullable Exception serverError) {
        repository.loadFromCache().addOnCompleteListener(task -> {
            inFlight = false;
            List<Post> cached = task.isSuccessful() && task.getResult() != null
                    ? task.getResult() : new ArrayList<>();

            Ui d = copy();
            d.loading = false;
            d.refreshing = false;
            d.posts = cached;
            d.source = offline ? SOURCE_CACHE_OFFLINE : SOURCE_CACHE_STALE;
            d.error = null;
            d.offlineNoCache = false;
            if (cached.isEmpty()) {
                if (offline) d.offlineNoCache = true;
                else d.error = NeedErrors.message(serverError);
            }
            ui.setValue(d);
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
            s.refreshing = old.refreshing;
            s.busy = old.busy;
            s.posts = old.posts;
            s.source = old.source;
            s.filter = old.filter;
            s.error = old.error;
            s.offlineNoCache = old.offlineNoCache;
            s.membership = old.membership;
        }
        return s;
    }
}
