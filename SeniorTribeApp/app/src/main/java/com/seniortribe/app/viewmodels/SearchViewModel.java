package com.seniortribe.app.viewmodels;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.data.PostRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.search.SearchMatcher;
import com.seniortribe.app.search.SearchResult;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E08 · Tìm nhiều loại nội dung trong một ô tìm kiếm:
 *  - Nhu cầu: của đơn vị (ghi nhận / phụ trách) và nhu cầu OPEN trong mạng lưới — chỉ bản tóm tắt,
 *    không đụng tới thông tin riêng của người cao tuổi (R02, R03);
 *  - Bài đăng trên bảng tin (bài chưa ẩn; mất mạng thì tìm trong bài đã lưu, F16);
 *  - Lịch hẹn đã thống nhất / đã hỗ trợ của đơn vị (tìm được theo "thứ 7", "19/10"…).
 * Dữ liệu tải một lần rồi lọc ở máy (như F09), nên gõ tới đâu ra kết quả tới đó, không tạo thêm
 * composite index. Từ khóa và bộ lọc nằm trong ViewModel nên xoay màn hình không mất (T04).
 */
public class SearchViewModel extends AndroidViewModel {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_NEEDS = 1;
    public static final int FILTER_POSTS = 2;
    public static final int FILTER_SCHEDULES = 3;

    /** Ở "Tất cả", mỗi nhóm hiện tối đa ngần này kết quả, kèm nút xem hết. */
    public static final int PREVIEW_PER_GROUP = 5;

    public static class Ui {
        public boolean loading = true;
        public String error;
        public boolean unauthorized;
        public boolean postsOffline;
        public String query = "";
        public int filter = FILTER_ALL;
        public List<SearchResult> needs = new ArrayList<>();
        public List<SearchResult> posts = new ArrayList<>();
        public List<SearchResult> schedules = new ArrayList<>();

        public int total() {
            return needs.size() + posts.size() + schedules.size();
        }
    }

    private final NeedRepository needRepo = new NeedRepository();
    private final PostRepository postRepo;
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    private final Map<String, NeedSummary> mine = new LinkedHashMap<>();
    private final Map<String, NeedSummary> recorded = new LinkedHashMap<>();
    private final Map<String, NeedSummary> assigned = new LinkedHashMap<>();
    private final Map<String, NeedSummary> open = new LinkedHashMap<>();
    private List<Post> posts = new ArrayList<>();
    private boolean started, postsLoaded;
    private int pendingLists;
    private String query = "";
    private int filter = FILTER_ALL;

    public SearchViewModel(@NonNull Application app) {
        super(app);
        postRepo = new PostRepository(app);
    }

    public LiveData<Ui> getUi() { return ui; }

    public void start() {
        if (started) return;
        started = true;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Ui s = base();
                s.loading = false;
                s.error = NeedErrors.message(task.getException());
                ui.setValue(s);
                return;
            }
            Membership me = task.getResult();
            if (me == null) {
                Ui s = base();
                s.loading = false;
                s.unauthorized = true;
                ui.setValue(s);
                return;
            }
            pendingLists = 3;
            registrations.add(needRepo.listenRecordedBy(me.getOrgId(), callback(recorded)));
            registrations.add(needRepo.listenAssignedTo(me.getOrgId(), callback(assigned)));
            registrations.add(needRepo.listenOpenNeeds(callback(open)));
            loadPosts();
        });
    }

    public void retry() {
        stop();
        started = false;
        recorded.clear();
        assigned.clear();
        open.clear();
        posts = new ArrayList<>();
        postsLoaded = false;
        ui.setValue(base());
        start();
    }

    private void loadPosts() {
        postRepo.loadFromServer().addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null) {
                posts = t.getResult();
                postsLoaded = true;
                publish(false);
                return;
            }
            // Mất mạng: tìm trong các bài đã lưu trên máy (F16)
            postRepo.loadFromCache().addOnCompleteListener(c -> {
                posts = c.isSuccessful() && c.getResult() != null ? c.getResult() : new ArrayList<>();
                postsLoaded = true;
                publish(true);
            });
        });
    }

    private NeedRepository.Callback<List<NeedSummary>> callback(Map<String, NeedSummary> target) {
        final boolean[] first = {true};
        return new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                target.clear();
                for (NeedSummary n : data) target.put(n.getNeedId(), n);
                if (first[0]) {
                    first[0] = false;
                    pendingLists--;
                }
                publish(null);
            }

            @Override public void onError(Exception error) {
                if (first[0]) {
                    first[0] = false;
                    pendingLists--;
                }
                Ui s = current();
                s.error = NeedErrors.message(error);
                ui.setValue(s);
                publish(null);
            }
        };
    }

    public void setQuery(String q) {
        String clean = q == null ? "" : q;
        if (clean.equals(query)) return;
        query = clean;
        publish(null);
    }

    public void setFilter(int f) {
        if (f == filter) return;
        filter = f;
        publish(null);
    }

    public String getQuery() { return query; }

    public int getFilter() { return filter; }

    // =====================================================================

    private void publish(Boolean postsOffline) {
        Ui s = current();
        if (postsOffline != null) s.postsOffline = postsOffline;
        s.loading = pendingLists > 0 || !postsLoaded;
        s.query = query;
        s.filter = filter;
        SearchMatcher m = new SearchMatcher(query);
        if (m.isEmpty()) {
            s.needs = new ArrayList<>();
            s.posts = new ArrayList<>();
            s.schedules = new ArrayList<>();
            ui.setValue(s);
            return;
        }
        mine.clear();
        mine.putAll(open);
        mine.putAll(recorded);
        mine.putAll(assigned);

        List<SearchResult> needs = new ArrayList<>();
        List<SearchResult> schedules = new ArrayList<>();
        for (NeedSummary n : mine.values()) {
            String code = nz(n.getCaseCode());
            if (m.matchesAll(code, n.getTitle(), n.getTopic(), n.getArea(), n.getContent(), n.getDesiredTime(),
                    n.getRecorderOrgName(), n.getAssignedOrgName())) {
                needs.add(new SearchResult(SearchResult.Kind.NEED, "n_" + n.getNeedId(), nz(n.getTitle()),
                        join(code, n.getTopic(), n.getArea()),
                        m.snippet(n.getContent(), 120),
                        m.score(n.getTitle(), code, n.getTopic(), n.getArea(), n.getContent()),
                        n.getCreatedAtMillis(), n.getStatus(), n, null));
            }
            if (n.getScheduledAtMillis() > 0 && isScheduleStatus(n.getStatus())) {
                String slot = DateTimeUtils.formatSlot(n.getScheduledAtMillis(), n.getScheduledEndAtMillis());
                String date = DateTimeUtils.formatDate(n.getScheduledAtMillis());
                if (m.matchesAll(code, n.getTitle(), slot, date, n.getScheduledFormat(), n.getScheduledPlace(),
                        n.getArea(), n.getAssignedOrgName(), n.getAssigneeName())) {
                    schedules.add(new SearchResult(SearchResult.Kind.SCHEDULE, "s_" + n.getNeedId(), slot,
                            join(code, n.getScheduledFormat(), n.getAssignedOrgName()),
                            nz(n.getTitle()),
                            m.score(slot, n.getTitle(), date, n.getScheduledFormat(), code),
                            n.getScheduledAtMillis(), n.getStatus(), n, null));
                }
            }
        }
        List<SearchResult> postResults = new ArrayList<>();
        for (Post p : posts) {
            if (p.isHidden()) continue;
            if (m.matchesAll(p.getTitle(), p.getContent(), p.getOrgName(), p.getEventTime(), p.getEventPlace())) {
                String when = p.getCreatedAtMillis() > 0 ? DateTimeUtils.formatDate(p.getCreatedAtMillis()) : null;
                postResults.add(new SearchResult(SearchResult.Kind.POST, "p_" + p.getId(), nz(p.getTitle()),
                        join(p.getOrgName(), when),
                        m.snippet(p.getContent(), 120),
                        m.score(p.getTitle(), p.getContent(), p.getOrgName(), p.getEventPlace()),
                        p.getCreatedAtMillis(), null, null, p));
            }
        }
        sort(needs);
        sort(schedules);
        sort(postResults);
        s.needs = needs;
        s.schedules = schedules;
        s.posts = postResults;
        ui.setValue(s);
    }

    private static boolean isScheduleStatus(String status) {
        return Constants.STATUS_SCHEDULED.equals(status) || Constants.STATUS_DELIVERED.equals(status)
                || Constants.STATUS_COMPLETED.equals(status);
    }

    private static void sort(List<SearchResult> list) {
        Collections.sort(list, (a, b) -> a.score != b.score ? Integer.compare(b.score, a.score) : Long.compare(b.time, a.time));
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (TextUtils.isEmpty(p)) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private Ui base() {
        Ui s = new Ui();
        s.query = query;
        s.filter = filter;
        return s;
    }

    private Ui current() {
        Ui old = ui.getValue();
        Ui s = base();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.unauthorized = old.unauthorized;
            s.postsOffline = old.postsOffline;
            s.needs = old.needs;
            s.posts = old.posts;
            s.schedules = old.schedules;
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
