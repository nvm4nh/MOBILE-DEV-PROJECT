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
import com.seniortribe.app.activities.NeedDetailActivity;
import com.seniortribe.app.adapters.ScheduleAdapter;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.MyCoordinationViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F15 · Lịch và lịch sử hỗ trợ của đơn vị (cả đơn vị ghi nhận lẫn tổ chức phụ trách).
 *  - Sắp tới: nhu cầu đã thống nhất lịch (SCHEDULED), gần nhất trước.
 *  - Đã thực hiện: đã báo hỗ trợ (DELIVERED) hoặc đã hoàn thành (COMPLETED), mới nhất trước.
 * Bấm một lịch để mở chi tiết nhu cầu, từ đó xem lịch sử từng bước.
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
            adapter.setNeeds(new ArrayList<>());
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
        List<NeedSummary> list = showHistory ? history(s.needs) : upcoming(s.needs);
        adapter.setNeeds(list);
        if (list.isEmpty()) {
            stateView.showMessage(R.drawable.ic_schedule,
                    getString(showHistory ? R.string.schedule_empty_history_title : R.string.schedule_empty_upcoming_title),
                    getString(showHistory ? R.string.schedule_empty_history_message : R.string.schedule_empty_upcoming_message),
                    null, null);
        } else {
            stateView.hide();
        }
    }

    static List<NeedSummary> upcoming(List<NeedSummary> needs) {
        List<NeedSummary> out = new ArrayList<>();
        for (NeedSummary n : needs) {
            if (Constants.STATUS_SCHEDULED.equals(n.getStatus())) out.add(n);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.getScheduledAtMillis(), b.getScheduledAtMillis()));
        return out;
    }

    static List<NeedSummary> history(List<NeedSummary> needs) {
        List<NeedSummary> out = new ArrayList<>();
        for (NeedSummary n : needs) {
            if (Constants.STATUS_DELIVERED.equals(n.getStatus()) || Constants.STATUS_COMPLETED.equals(n.getStatus())) {
                out.add(n);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.getScheduledAtMillis(), a.getScheduledAtMillis()));
        return out;
    }

    @Override
    public void onScheduleClick(NeedSummary need) {
        Intent intent = new Intent(requireContext(), NeedDetailActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, need.getNeedId());
        startActivity(intent);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HISTORY, showHistory);
    }
}
