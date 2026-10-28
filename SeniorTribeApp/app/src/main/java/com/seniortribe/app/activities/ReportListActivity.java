package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.ReportAdapter;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ReportListViewModel;

import java.util.List;

/**
 * E03 · Danh sách phản ánh. Điều phối viên thấy thêm mục "Cần xử lý" (kèm số phản ánh mới);
 * mọi tài khoản đều xem được "Tôi đã gửi". Mở từ nút cờ ở đầu tab Nhu cầu.
 */
public class ReportListActivity extends AppCompatActivity implements ReportAdapter.OnReportClickListener {

    private ReportListViewModel viewModel;
    private MaterialButtonToggleGroup toggle;
    private MaterialButton btnHandle, btnMine;
    private RecyclerView rv;
    private ReportAdapter adapter;
    private StateView stateView;
    private boolean syncingToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toggle = findViewById(R.id.toggleReports);
        btnHandle = findViewById(R.id.btnModeHandle);
        btnMine = findViewById(R.id.btnModeMine);
        rv = findViewById(R.id.rvReports);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(this, this);
        rv.setAdapter(adapter);
        stateView = new StateView(findViewById(R.id.frameReports));

        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || syncingToggle) return;
            viewModel.setMode(id == R.id.btnModeHandle ? ReportListViewModel.MODE_HANDLE : ReportListViewModel.MODE_MINE);
        });

        viewModel = new ViewModelProvider(this).get(ReportListViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.start();
    }

    private void render(ReportListViewModel.Ui s) {
        // Chỉ điều phối viên mới có mục "Cần xử lý"
        boolean canHandle = s.canHandle();
        toggle.setVisibility(canHandle ? View.VISIBLE : View.GONE);
        int count = s.newCount();
        btnHandle.setText(count > 0 ? getString(R.string.reports_mode_handle_count, count) : getString(R.string.reports_mode_handle));
        syncingToggle = true;
        toggle.check(s.mode == ReportListViewModel.MODE_HANDLE ? R.id.btnModeHandle : R.id.btnModeMine);
        syncingToggle = false;

        if (s.noMembership) {
            adapter.setReports(null);
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.reports_title),
                    getString(R.string.reports_no_membership), null, null);
            return;
        }
        if (s.loading) {
            stateView.showLoading();
            return;
        }
        List<FeedbackReport> list = s.visible();
        adapter.setReports(list);
        if (s.error != null && list.isEmpty()) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
        } else if (list.isEmpty()) {
            boolean handle = s.mode == ReportListViewModel.MODE_HANDLE;
            stateView.showMessage(R.drawable.ic_flag,
                    getString(handle ? R.string.reports_empty_handle_title : R.string.reports_empty_mine_title),
                    getString(handle ? R.string.reports_empty_handle_message : R.string.reports_empty_mine_message),
                    null, null);
        } else {
            stateView.hide();
        }
    }

    @Override
    public void onReportClick(FeedbackReport report) {
        startActivity(new Intent(this, ReportDetailActivity.class)
                .putExtra(ReportDetailActivity.EXTRA_REPORT_ID, report.getReportId()));
    }
}
