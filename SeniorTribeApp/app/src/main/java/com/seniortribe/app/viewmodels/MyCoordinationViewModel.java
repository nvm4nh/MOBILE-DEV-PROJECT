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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Các nhu cầu đơn vị của tài khoản đang ghi nhận hoặc đang phụ trách, dùng cho danh sách chat
 * (F11) và màn hình lịch hẹn / lịch sử (F15). Gộp hai truy vấn đơn giản ở máy để không phải
 * tạo composite index; mỗi truy vấn đều nằm trong phạm vi Security Rules cho phép đọc.
 */
public class MyCoordinationViewModel extends ViewModel {

    public static class Ui {
        public boolean loading = true;
        public String error;
        public boolean unauthorized;
        public Membership membership;
        public List<NeedSummary> needs = new ArrayList<>();
        public boolean fromCache;
    }

    private final NeedRepository repository = new NeedRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private final Map<String, NeedSummary> recorded = new LinkedHashMap<>();
    private final Map<String, NeedSummary> assigned = new LinkedHashMap<>();
    private boolean recordedLoaded, assignedLoaded, started;

    public LiveData<Ui> getUi() { return ui; }

    public void start() {
        if (started) return;
        started = true;
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(task -> {
            Ui s = new Ui();
            if (!task.isSuccessful()) {
                s.loading = false;
                s.error = NeedErrors.message(task.getException());
                ui.setValue(s);
                return;
            }
            if (task.getResult() == null) {
                s.loading = false;
                s.unauthorized = true;
                ui.setValue(s);
                return;
            }
            s.membership = task.getResult();
            ui.setValue(s);
            listen(s.membership);
        });
    }

    public void retry() {
        stop();
        started = false;
        recorded.clear();
        assigned.clear();
        recordedLoaded = assignedLoaded = false;
        ui.setValue(new Ui());
        start();
    }

    private void listen(Membership me) {
        registrations.add(repository.listenRecordedBy(me.getOrgId(), callback(recorded, true)));
        registrations.add(repository.listenAssignedTo(me.getOrgId(), callback(assigned, false)));
    }

    private NeedRepository.Callback<List<NeedSummary>> callback(Map<String, NeedSummary> target, boolean isRecorded) {
        return new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                target.clear();
                for (NeedSummary n : data) target.put(n.getNeedId(), n);
                if (isRecorded) recordedLoaded = true;
                else assignedLoaded = true;
                publish(fromCache);
            }

            @Override public void onError(Exception error) {
                Ui s = copy();
                s.loading = false;
                s.error = NeedErrors.message(error);
                ui.setValue(s);
            }
        };
    }

    private void publish(boolean fromCache) {
        Map<String, NeedSummary> merged = new LinkedHashMap<>(recorded);
        merged.putAll(assigned);
        Ui s = copy();
        s.loading = !(recordedLoaded && assignedLoaded);
        s.error = null;
        s.needs = new ArrayList<>(merged.values());
        s.fromCache = fromCache;
        ui.setValue(s);
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.loading = old.loading;
            s.error = old.error;
            s.unauthorized = old.unauthorized;
            s.membership = old.membership;
            s.needs = old.needs;
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
