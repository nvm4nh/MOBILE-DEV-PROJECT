package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.seniortribe.app.data.ChatMediaRepository;
import com.seniortribe.app.data.NeedErrors;

/**
 * E06 · Tải một tệp đính kèm về bộ nhớ (không ghi xuống máy) và giữ qua lần xoay màn hình.
 */
public class AttachmentViewModel extends ViewModel {

    public static final class State {
        public final boolean loading;
        public final byte[] data;
        public final String error;

        State(boolean loading, byte[] data, String error) {
            this.loading = loading;
            this.data = data;
            this.error = error;
        }
    }

    private final ChatMediaRepository repo = new ChatMediaRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private String key;

    public LiveData<State> getState() { return state; }

    public void load(String assignmentId, String attachmentId) {
        String k = assignmentId + "/" + attachmentId;
        State s = state.getValue();
        if (k.equals(key) && s != null && (s.loading || s.data != null)) return; // đã tải / đang tải
        key = k;
        state.setValue(new State(true, null, null));
        repo.download(assignmentId, attachmentId).addOnCompleteListener(t -> {
            if (!k.equals(key)) return;
            if (t.isSuccessful()) state.setValue(new State(false, t.getResult(), null));
            else state.setValue(new State(false, null, NeedErrors.message(t.getException())));
        });
    }

    @Override
    protected void onCleared() {
        state.setValue(null); // bỏ tham chiếu tới dữ liệu tệp
    }
}
