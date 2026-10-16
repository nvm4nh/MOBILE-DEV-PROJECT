package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.VnText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * ViewModel cho tab Nhu cầu: "Đơn vị ghi nhận" (F08) và "Tìm để tiếp nhận" (F09).
 *
 * Giữ dữ liệu và bộ lọc khi xoay màn hình; lắng nghe Firestore realtime nên khi
 * tổ chức khác tiếp nhận, danh sách tự cập nhật.
 */
public class NeedListViewModel extends ViewModel {

    public static final int MODE_UNIT = 0;
    public static final int MODE_SEARCH = 1;

    public static final String GROUP_ALL = "ALL";
    public static final String GROUP_REVIEW = "REVIEW";
    public static final String GROUP_OPEN = "OPEN";
    public static final String GROUP_ACTIVE = "ACTIVE"; // ACCEPTED, SCHEDULED, DELIVERED
    public static final String GROUP_DONE = "DONE";     // COMPLETED, CANCELLED

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final NeedRepository repository = new NeedRepository();
    private final MembershipRepository memberships = MembershipRepository.get();
    private final MutableLiveData<NeedListUiState> state = new MutableLiveData<>();

    private boolean started;
    private boolean membershipLoaded;
    private Membership membership;
    private String membershipError;

    // null = chưa tải xong
    private List<NeedSummary> recorded, assigned, open;
    private String unitError, openError;
    private boolean unitFromCache, openFromCache;

    private ListenerRegistration regRecorded, regAssigned, regOpen;

    // Lựa chọn của người dùng
    private int mode = MODE_UNIT;
    private String statusGroup = GROUP_ALL;
    private String keyword = "";
    private String area, topic;
    private int days;

    public LiveData<NeedListUiState> getState() {
        return state;
    }

    public Membership getMembership() {
        return membership;
    }

    /** Gọi mỗi khi view được tạo. Lần đầu mới tải dữ liệu; sau khi xoay thì dùng lại. */
    public void start() {
        if (started) {
            publish();
            return;
        }
        started = true;
        loadMembership(false);
    }

    /** Kéo để làm mới hoặc bấm Thử lại. */
    public void reload() {
        detach();
        recorded = assigned = open = null;
        unitError = openError = null;
        membershipLoaded = false;
        membershipError = null;
        publish();
        loadMembership(true);
    }

    private void loadMembership(boolean force) {
        publish();
        memberships.loadCurrent(force).addOnCompleteListener(task -> {
            membershipLoaded = true;
            if (task.isSuccessful()) {
                membership = task.getResult();
                membershipError = null;
                if (membership != null) attach();
            } else {
                membership = null;
                membershipError = NeedErrors.message(task.getException());
            }
            publish();
        });
    }

    private void attach() {
        String orgId = membership.getOrgId();
        regRecorded = repository.listenRecordedBy(orgId, new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                recorded = data;
                unitError = null;
                unitFromCache = fromCache;
                publish();
            }
            @Override public void onError(Exception error) {
                unitError = NeedErrors.message(error);
                publish();
            }
        });
        regAssigned = repository.listenAssignedTo(orgId, new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                assigned = data;
                unitFromCache = unitFromCache || fromCache;
                publish();
            }
            @Override public void onError(Exception error) {
                unitError = NeedErrors.message(error);
                publish();
            }
        });
        regOpen = repository.listenOpenNeeds(new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                open = data;
                openError = null;
                openFromCache = fromCache;
                publish();
            }
            @Override public void onError(Exception error) {
                openError = NeedErrors.message(error);
                publish();
            }
        });
    }

    private void detach() {
        if (regRecorded != null) regRecorded.remove();
        if (regAssigned != null) regAssigned.remove();
        if (regOpen != null) regOpen.remove();
        regRecorded = regAssigned = regOpen = null;
    }

    @Override
    protected void onCleared() {
        detach();
    }

    // ----- Thao tác của người dùng -----

    public void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        publish();
    }

    public void setStatusGroup(String group) {
        statusGroup = group;
        publish();
    }

    public void setKeyword(String text) {
        String clean = text != null ? text.trim() : "";
        if (clean.equals(keyword)) return;
        keyword = clean;
        publish();
    }

    public void setArea(String value) { area = value; publish(); }

    public void setTopic(String value) { topic = value; publish(); }

    public void setDays(int value) { days = value; publish(); }

    public void clearSearchFilters() {
        keyword = "";
        area = null;
        topic = null;
        days = 0;
        publish();
    }

    // ----- Tính trạng thái màn hình -----

    private void publish() {
        NeedListUiState s = new NeedListUiState();
        s.mode = mode;
        s.statusGroup = statusGroup;
        s.keyword = keyword;
        s.area = area;
        s.topic = topic;
        s.days = days;

        if (!membershipLoaded) {
            s.loading = true;
            state.setValue(s);
            return;
        }
        if (membershipError != null) {
            s.loading = false;
            s.error = membershipError;
            state.setValue(s);
            return;
        }
        if (membership == null) {
            s.loading = false;
            s.noMembership = true;
            state.setValue(s);
            return;
        }
        s.canCreate = membership.isRecorder();

        if (mode == MODE_UNIT) buildUnit(s);
        else buildSearch(s);
        state.setValue(s);
    }

    private void buildUnit(NeedListUiState s) {
        if (unitError != null && (recorded == null || assigned == null)) {
            s.loading = false;
            s.error = unitError;
            return;
        }
        if (recorded == null || assigned == null) {
            s.loading = true;
            return;
        }
        s.loading = false;
        s.fromCache = unitFromCache;

        // Gộp hai nguồn, bỏ trùng theo id
        Map<String, NeedSummary> merged = new LinkedHashMap<>();
        for (NeedSummary n : recorded) merged.put(n.getNeedId(), n);
        for (NeedSummary n : assigned) merged.put(n.getNeedId(), n);
        List<NeedSummary> all = new ArrayList<>(merged.values());
        NeedRepository.sortNewestFirst(all);

        List<NeedSummary> shown = new ArrayList<>();
        for (NeedSummary n : all) {
            String group = groupOf(n.getStatus());
            s.countAll++;
            switch (group) {
                case GROUP_REVIEW: s.countReview++; break;
                case GROUP_OPEN: s.countOpen++; break;
                case GROUP_ACTIVE: s.countActive++; break;
                default: s.countDone++; break;
            }
            if (GROUP_ALL.equals(statusGroup) || statusGroup.equals(group)) shown.add(n);
        }
        s.items = shown;
    }

    private void buildSearch(NeedListUiState s) {
        if (openError != null && open == null) {
            s.loading = false;
            s.error = openError;
            return;
        }
        if (open == null) {
            s.loading = true;
            return;
        }
        s.loading = false;
        s.fromCache = openFromCache;

        TreeSet<String> areas = new TreeSet<>();
        TreeSet<String> topics = new TreeSet<>();
        String key = VnText.normalize(keyword);
        long now = System.currentTimeMillis();

        List<NeedSummary> shown = new ArrayList<>();
        for (NeedSummary n : open) {
            if (n.getArea() != null) areas.add(n.getArea());
            if (n.getTopic() != null) topics.add(n.getTopic());

            if (area != null && !area.equals(n.getArea())) continue;
            if (topic != null && !topic.equals(n.getTopic())) continue;
            if (days > 0 && n.getCreatedAtMillis() > 0 && now - n.getCreatedAtMillis() > days * DAY_MILLIS) continue;
            if (!key.isEmpty() && !matches(n, key)) continue;
            shown.add(n);
        }
        s.items = shown;
        s.areaOptions = new ArrayList<>(areas);
        s.topicOptions = new ArrayList<>(topics);
    }

    /** Tìm từ khóa trong danh sách đã tải (F09), không phân biệt dấu. */
    private static boolean matches(NeedSummary n, String key) {
        String haystack = VnText.normalize(n.getCaseCode() + " " + n.getTitle() + " " + n.getContent()
                + " " + n.getArea() + " " + n.getTopic() + " " + n.getDesiredTime());
        return haystack.contains(key);
    }

    public static String groupOf(String status) {
        if (Constants.STATUS_REVIEW.equals(status)) return GROUP_REVIEW;
        if (Constants.STATUS_OPEN.equals(status)) return GROUP_OPEN;
        if (Constants.STATUS_ACCEPTED.equals(status) || Constants.STATUS_SCHEDULED.equals(status)
                || Constants.STATUS_DELIVERED.equals(status)) return GROUP_ACTIVE;
        return GROUP_DONE;
    }
}
