package com.seniortribe.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.NeedEventAdapter;
import com.seniortribe.app.data.CoordinationRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.NeedEvent;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.StateView;

import java.util.List;

/**
 * F15 · Lịch sử hỗ trợ của một nhu cầu: ghi nhận, rà soát, các lần tiếp nhận (kể cả lần
 * đã trả lại), lịch hẹn, báo hỗ trợ và đối soát. Dữ liệu từ needs_summary/{id}/events,
 * chỉ thêm không sửa, nên dùng làm nhật ký đối soát được. Quyền đọc theo Security Rules.
 */
public class NeedHistoryActivity extends AppCompatActivity {

    private final CoordinationRepository repository = new CoordinationRepository();
    private ListenerRegistration registration;
    private NeedEventAdapter adapter;
    private StateView stateView;
    private String needId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_need_history);

        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NeedEventAdapter(this);
        rv.setAdapter(adapter);
        stateView = new StateView(findViewById(R.id.frameHistory));

        listen();
    }

    private void listen() {
        stateView.showLoading();
        registration = repository.listenEvents(needId, new NeedRepository.Callback<List<NeedEvent>>() {
            @Override public void onData(List<NeedEvent> data, boolean fromCache) {
                adapter.setEvents(data);
                if (data.isEmpty()) {
                    stateView.showMessage(R.drawable.ic_inbox, getString(R.string.history_empty_title),
                            getString(R.string.history_empty_message), null, null);
                } else {
                    stateView.hide();
                }
            }

            @Override public void onError(Exception error) {
                stateView.showError(getString(R.string.state_error_title), NeedErrors.message(error),
                        getString(R.string.action_retry), v -> {
                            stop();
                            listen();
                        });
            }
        });
    }

    private void stop() {
        if (registration != null) registration.remove();
        registration = null;
    }

    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }
}
