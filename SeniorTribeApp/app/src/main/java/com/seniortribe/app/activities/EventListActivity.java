package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.EventAdapter;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.EventListViewModel;

import java.util.List;

/**
 * E07 · Sự kiện cộng đồng sắp diễn ra. Mọi tài khoản đã đăng nhập xem được; đại diện tổ chức
 * tạo sự kiện bằng nút nổi và lọc "Tổ chức của tôi". Mở từ nút "Sự kiện" ở đầu tab Bảng tin.
 */
public class EventListActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {

    private EventListViewModel viewModel;
    private EventAdapter adapter;
    private StateView stateView;
    private ChipGroup chipGroup;
    private Chip chipMine;
    private TextView tvOffline;
    private ExtendedFloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        chipGroup = findViewById(R.id.chipGroupEvents);
        chipMine = findViewById(R.id.chipEventsMine);
        tvOffline = findViewById(R.id.tvEventsOffline);
        fab = findViewById(R.id.fabCreateEvent);
        RecyclerView rv = findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(this, this);
        rv.setAdapter(adapter);
        stateView = new StateView(findViewById(R.id.frameEvents));

        chipGroup.setOnCheckedStateChangeListener((group, ids) ->
                viewModel.setOnlyMine(ids.contains(R.id.chipEventsMine)));
        fab.setOnClickListener(v -> startActivity(new Intent(this, CreateEventActivity.class)));
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy > 8) fab.shrink();
                else if (dy < -8) fab.extend();
            }
        });

        viewModel = new ViewModelProvider(this).get(EventListViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.start();
    }

    private void render(EventListViewModel.Ui s) {
        boolean hasOrg = s.membership != null;
        chipGroup.setVisibility(hasOrg ? View.VISIBLE : View.GONE);
        fab.setVisibility(s.canCreate() ? View.VISIBLE : View.GONE);
        tvOffline.setVisibility(s.fromCache && !s.loading ? View.VISIBLE : View.GONE);

        if (s.loading) {
            stateView.showLoading();
            return;
        }
        List<CommunityEvent> list = s.visible();
        adapter.setData(list, hasOrg ? s.membership.getOrgId() : null);
        if (s.error != null && list.isEmpty()) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
        } else if (list.isEmpty()) {
            boolean mine = s.onlyMine && hasOrg;
            stateView.showMessage(R.drawable.ic_event,
                    getString(mine ? R.string.events_empty_mine_title : R.string.events_empty_title),
                    getString(mine ? R.string.events_empty_mine_message : R.string.events_empty_message),
                    null, null);
        } else {
            stateView.hide();
        }
    }

    @Override
    public void onEventClick(CommunityEvent event) {
        startActivity(new Intent(this, EventDetailActivity.class)
                .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getEventId()));
    }
}
