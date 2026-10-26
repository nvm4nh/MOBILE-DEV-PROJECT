package com.seniortribe.app.viewmodels;

import android.location.Location;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.data.SosRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.SosAlert;
import com.seniortribe.app.utils.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E09 · Màn hình SOS: danh sách nhu cầu mình đang phối hợp (để chọn nhu cầu cần báo khẩn),
 * báo khẩn của nhu cầu đang chọn, gửi / xác nhận / ghi đã xử lý. Giữ lựa chọn qua xoay màn hình.
 */
public class SosViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public Membership membership;
        /** Nhu cầu đang phối hợp mà mình là đầu mối đã ghi nhận hoặc người được giao (R08). */
        public List<NeedSummary> needs = new ArrayList<>();
        @Nullable public NeedSummary selected;
        public List<SosAlert> alerts = new ArrayList<>();
        public boolean sending;
        public boolean locating;
    }

    private final NeedRepository needRepo = new NeedRepository();
    private final SosRepository sosRepo = new SosRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> sent = new MutableLiveData<>();
    private final List<ListenerRegistration> base = new ArrayList<>();
    private final Map<String, NeedSummary> recorded = new LinkedHashMap<>();
    private final Map<String, NeedSummary> assigned = new LinkedHashMap<>();
    @Nullable private ListenerRegistration alertListener;
    @Nullable private String wantedNeedId;
    @Nullable private String lastCalled;
    private boolean started;

    public LiveData<Ui> getUi() { return ui; }

    public LiveData<Event<String>> getMessages() { return messages; }

    public LiveData<Event<Boolean>> getSent() { return sent; }

    /** Số khẩn cấp vừa bấm gọi (115 / 113 / 114), gửi kèm báo khẩn để bên kia biết. */
    public void setLastCalled(String number) { lastCalled = number; }

    @Nullable
    public String getLastCalled() { return lastCalled; }

    public void start(@Nullable String needId) {
        if (started) return;
        started = true;
        wantedNeedId = needId;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            Ui s = copy();
            if (!t.isSuccessful() || t.getResult() == null) {
                s.loading = false;
                s.error = t.isSuccessful() ? null : NeedErrors.message(t.getException());
                ui.setValue(s);
                return;
            }
            s.membership = t.getResult();
            ui.setValue(s);
            base.add(needRepo.listenRecordedBy(s.membership.getOrgId(), callback(recorded)));
            base.add(needRepo.listenAssignedTo(s.membership.getOrgId(), callback(assigned)));
        });
    }

    private NeedRepository.Callback<List<NeedSummary>> callback(Map<String, NeedSummary> target) {
        return new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                target.clear();
                for (NeedSummary n : data) target.put(n.getNeedId(), n);
                refreshNeeds();
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        };
    }

    private void refreshNeeds() {
        Ui s = copy();
        Membership me = s.membership;
        if (me == null) return;
        Map<String, NeedSummary> all = new LinkedHashMap<>(recorded);
        all.putAll(assigned);
        List<NeedSummary> list = new ArrayList<>();
        for (NeedSummary n : all.values()) {
            if (!n.isCoordinating() || n.getCurrentAssignmentId() == null) continue;
            if (!me.getUid().equals(n.getRecorderUid()) && !me.getUid().equals(n.getAssigneeUid())) continue;
            list.add(n);
        }
        // Nhu cầu đã hẹn lịch (đang đi hỗ trợ) lên đầu, rồi tới mới cập nhật
        Collections.sort(list, (a, b) -> {
            boolean sa = "SCHEDULED".equals(a.getStatus()), sb = "SCHEDULED".equals(b.getStatus());
            if (sa != sb) return sa ? -1 : 1;
            long ta = a.getUpdatedAt() != null ? a.getUpdatedAt().getTime() : 0;
            long tb = b.getUpdatedAt() != null ? b.getUpdatedAt().getTime() : 0;
            return Long.compare(tb, ta);
        });
        s.needs = list;
        s.loading = false;
        NeedSummary keep = null;
        String want = s.selected != null ? s.selected.getNeedId() : wantedNeedId;
        for (NeedSummary n : list) if (n.getNeedId().equals(want)) keep = n;
        if (keep == null && !list.isEmpty() && s.selected == null && wantedNeedId == null) keep = list.get(0);
        boolean changed = keep == null ? s.selected != null : s.selected == null
                || !keep.getCurrentAssignmentId().equals(s.selected.getCurrentAssignmentId());
        s.selected = keep;
        ui.setValue(s);
        if (changed) listenAlerts();
    }

    public void select(NeedSummary n) {
        Ui s = copy();
        s.selected = n;
        s.alerts = new ArrayList<>();
        wantedNeedId = n.getNeedId();
        ui.setValue(s);
        listenAlerts();
    }

    private void listenAlerts() {
        if (alertListener != null) alertListener.remove();
        alertListener = null;
        Ui s0 = ui.getValue();
        if (s0 == null || s0.selected == null) return;
        final String assignmentId = s0.selected.getCurrentAssignmentId();
        alertListener = sosRepo.listen(assignmentId, new NeedRepository.Callback<List<SosAlert>>() {
            @Override public void onData(List<SosAlert> data, boolean fromCache) {
                Ui s = copy();
                if (s.selected == null || !assignmentId.equals(s.selected.getCurrentAssignmentId())) return;
                s.alerts = data;
                ui.setValue(s);
            }

            @Override public void onError(Exception error) {
                messages.setValue(new Event<>(NeedErrors.message(error)));
            }
        });
    }

    public void setLocating(boolean on) {
        Ui s = copy();
        s.locating = on;
        ui.setValue(s);
    }

    public void send(String kind, String note, @Nullable Location location) {
        Ui s = copy();
        if (s.membership == null || s.selected == null || s.sending) return;
        s.sending = true;
        s.locating = false;
        ui.setValue(s);
        sosRepo.send(s.selected.getCurrentAssignmentId(), s.membership, kind, note, lastCalled, location)
                .addOnCompleteListener(t -> {
                    Ui after = copy();
                    after.sending = false;
                    ui.setValue(after);
                    if (t.isSuccessful()) {
                        sent.setValue(new Event<>(true));
                        messages.setValue(new Event<>("Đã gửi báo khẩn. Bên cùng phối hợp sẽ nhận được thông báo."));
                    } else {
                        messages.setValue(new Event<>("Chưa gửi được báo khẩn: " + NeedErrors.message(t.getException())
                                + " Hãy gọi điện trực tiếp."));
                    }
                });
    }

    public void acknowledge(SosAlert a) {
        Ui s = copy();
        if (s.membership == null || s.selected == null) return;
        sosRepo.acknowledge(s.selected.getCurrentAssignmentId(), a.getAlertId(), s.membership)
                .addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) messages.setValue(new Event<>(NeedErrors.message(t.getException())));
                });
    }

    public void resolve(SosAlert a, String note) {
        Ui s = copy();
        if (s.membership == null || s.selected == null) return;
        sosRepo.resolve(s.selected.getCurrentAssignmentId(), a.getAlertId(), s.membership, note)
                .addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) messages.setValue(new Event<>(NeedErrors.message(t.getException())));
                });
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.membership = old.membership;
            s.needs = old.needs;
            s.selected = old.selected;
            s.alerts = old.alerts;
            s.sending = old.sending;
            s.locating = old.locating;
        }
        return s;
    }

    @Override
    protected void onCleared() {
        for (ListenerRegistration r : base) r.remove();
        base.clear();
        if (alertListener != null) alertListener.remove();
    }
}
