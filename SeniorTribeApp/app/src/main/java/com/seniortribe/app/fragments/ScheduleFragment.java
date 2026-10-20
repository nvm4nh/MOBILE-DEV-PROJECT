package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.NeedProgressActivity;
import com.seniortribe.app.adapters.ScheduleAdapter;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.MyCoordinationViewModel;

import java.util.ArrayList;

/**
 * F15 · Lịch (Figma) của đơn vị, gồm cả nhu cầu đơn vị ghi nhận lẫn nhu cầu đơn vị phụ trách.
 *  - Sắp tới: lịch đã thống nhất (SCHEDULED) nhóm theo ngày, kèm vài lần hỗ trợ gần đây.
 *  - Lịch sử: đã báo hỗ trợ (DELIVERED) hoặc đã hoàn thành (COMPLETED), mới nhất trước.
 * Bấm một lịch để mở màn hình Tiến độ (F13).
 */
public class ScheduleFragment extends Fragment implements ScheduleAdapter.OnScheduleClickListener {

    private static final String STATE_HISTORY = "show_history";

    private ScheduleAdapter adapter;
    private StateView stateView;
    private MyCoordinationViewModel viewModel;
    private boolean showHistory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);
        RecyclerView rv = view.findViewById(R.id.rvSchedules);
        adapter = new ScheduleAdapter(requireContext(), this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        stateView = new StateView(view.findViewById(R.id.frameSchedules));

        showHistory = savedInstanceState != null && savedInstanceState.getBoolean(STATE_HISTORY);
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleSchedule);
        toggle.check(showHistory ? R.id.btnHistory : R.id.btnUpcoming);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            showHistory = checkedId == R.id.btnHistory;
            MyCoordinationViewModel.Ui s = viewModel.getUi().getValue();
            if (s != null) render(s);
        });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(MyCoordinationViewModel.class);
        viewModel.getUi().observe(getViewLifecycleOwner(), this::render);
        viewModel.start();
    }

    private void render(MyCoordinationViewModel.Ui s) {
        if (s.unauthorized) {
            adapter.show(new ArrayList<>(), showHistory);
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.needs_state_no_membership_title),
                    getString(R.string.needs_state_no_membership_message), null, null);
            return;
        }
        if (s.error != null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        if (s.loading) {
            stateView.showLoading();
            return;
        }
        adapter.show(s.needs, showHistory);
        if (adapter.getItemCount() == 0) {
            stateView.showMessage(R.drawable.ic_schedule,
                    getString(showHistory ? R.string.schedule_empty_history_title : R.string.schedule_empty_upcoming_title),
                    getString(showHistory ? R.string.schedule_empty_history_message : R.string.schedule_empty_upcoming_message),
                    null, null);
        } else {
            stateView.hide();
        }
    }

    /** Bấm một lịch để xem tiến độ 5 bước và đối soát của nhu cầu đó (F13). */
    @Override
    public void onScheduleClick(NeedSummary need) {
        Intent intent = new Intent(requireContext(), NeedProgressActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, need.getNeedId());
        intent.putExtra(Constants.EXTRA_ASSIGNMENT_ID, need.getCurrentAssignmentId());
        intent.putExtra(Constants.EXTRA_CASE_CODE, need.getCaseCode());
        startActivity(intent);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HISTORY, showHistory);
    }
}
