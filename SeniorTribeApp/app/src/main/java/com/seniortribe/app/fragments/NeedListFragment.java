package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.CreateNeedActivity;
import com.seniortribe.app.activities.NeedDetailActivity;
import com.seniortribe.app.activities.ReviewNeedActivity;
import com.seniortribe.app.adapters.NeedAdapter;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.NeedListUiState;
import com.seniortribe.app.viewmodels.NeedListViewModel;

import java.util.List;

/**
 * Tab Nhu cầu.
 *  - F08 "Đơn vị ghi nhận": nhu cầu do đơn vị ghi nhận hoặc đang tiếp nhận, lọc theo trạng thái.
 *  - F09 "Tìm để tiếp nhận": nhu cầu đã duyệt (OPEN), tìm từ khóa, lọc khu vực / chủ đề / thời gian.
 */
public class NeedListFragment extends Fragment implements NeedAdapter.OnNeedClickListener {

    private NeedListViewModel viewModel;
    private NeedAdapter adapter;
    private StateView stateView;

    private MaterialButtonToggleGroup toggleMode;
    private View scrollStatusChips, layoutSearch;
    private ChipGroup chipGroupStatus;
    private Chip chipAll, chipReview, chipOpen, chipActive, chipDone;
    private Chip chipFilterArea, chipFilterTopic, chipFilterTime;
    private TextView tvResultCount, tvOfflineBanner;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNeeds;
    private ExtendedFloatingActionButton fabCreate;
    private View btnHeaderSearch;

    private NeedListUiState lastState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_need_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);

        viewModel = new ViewModelProvider(this).get(NeedListViewModel.class);

        adapter = new NeedAdapter(requireContext(), this);
        rvNeeds.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNeeds.setAdapter(adapter);

        setupListeners(view);

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.start();
    }

    private void bindViews(View v) {
        stateView = new StateView(v);
        toggleMode = v.findViewById(R.id.toggleMode);
        scrollStatusChips = v.findViewById(R.id.scrollStatusChips);
        layoutSearch = v.findViewById(R.id.layoutSearch);
        chipGroupStatus = v.findViewById(R.id.chipGroupStatus);
        chipAll = v.findViewById(R.id.chipAll);
        chipReview = v.findViewById(R.id.chipReview);
        chipOpen = v.findViewById(R.id.chipOpen);
        chipActive = v.findViewById(R.id.chipActive);
        chipDone = v.findViewById(R.id.chipDone);
        chipFilterArea = v.findViewById(R.id.chipFilterArea);
        chipFilterTopic = v.findViewById(R.id.chipFilterTopic);
        chipFilterTime = v.findViewById(R.id.chipFilterTime);
        tvResultCount = v.findViewById(R.id.tvResultCount);
        tvOfflineBanner = v.findViewById(R.id.tvOfflineBanner);
        swipeRefresh = v.findViewById(R.id.swipeRefreshNeeds);
        rvNeeds = v.findViewById(R.id.rvNeeds);
        fabCreate = v.findViewById(R.id.fabCreateNeed);
        btnHeaderSearch = v.findViewById(R.id.btnHeaderSearch);
    }

    private void setupListeners(View v) {
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            viewModel.setMode(checkedId == R.id.btnModeSearch
                    ? NeedListViewModel.MODE_SEARCH : NeedListViewModel.MODE_UNIT);
        });

        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String groupKey = NeedListViewModel.GROUP_ALL;
            if (id == R.id.chipReview) groupKey = NeedListViewModel.GROUP_REVIEW;
            else if (id == R.id.chipOpen) groupKey = NeedListViewModel.GROUP_OPEN;
            else if (id == R.id.chipActive) groupKey = NeedListViewModel.GROUP_ACTIVE;
            else if (id == R.id.chipDone) groupKey = NeedListViewModel.GROUP_DONE;
            viewModel.setStatusGroup(groupKey);
        });

        TextView etSearch = v.findViewById(R.id.etSearch);
        // Nút tìm trên tiêu đề (Figma F08) mở thẳng chế độ "Tìm để tiếp nhận"
        btnHeaderSearch.setOnClickListener(x -> {
            viewModel.setMode(NeedListViewModel.MODE_SEARCH);
            etSearch.requestFocus();
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { viewModel.setKeyword(s.toString()); }
        });

        chipFilterArea.setOnClickListener(x -> showAreaFilter());
        chipFilterArea.setOnCloseIconClickListener(x -> showAreaFilter());
        chipFilterTopic.setOnClickListener(x -> showTopicFilter());
        chipFilterTopic.setOnCloseIconClickListener(x -> showTopicFilter());
        chipFilterTime.setOnClickListener(x -> showTimeFilter());
        chipFilterTime.setOnCloseIconClickListener(x -> showTimeFilter());

        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(viewModel::reload);

        fabCreate.setOnClickListener(x -> startActivity(new Intent(requireContext(), CreateNeedActivity.class)));
    }

    // =====================================================================
    // Vẽ màn hình theo trạng thái
    // =====================================================================

    private void render(NeedListUiState s) {
        lastState = s;
        boolean search = s.mode == NeedListViewModel.MODE_SEARCH;

        int wantedButton = search ? R.id.btnModeSearch : R.id.btnModeUnit;
        if (toggleMode.getCheckedButtonId() != wantedButton) toggleMode.check(wantedButton);

        btnHeaderSearch.setVisibility(search ? View.INVISIBLE : View.VISIBLE);
        scrollStatusChips.setVisibility(search ? View.GONE : View.VISIBLE);
        layoutSearch.setVisibility(search ? View.VISIBLE : View.GONE);

        if (search) renderSearchFilters(s);
        else renderStatusChips(s);

        boolean showFab = !search && s.canCreate;
        fabCreate.setVisibility(showFab ? View.VISIBLE : View.GONE);

        boolean offline = s.fromCache && !NetworkUtils.isNetworkAvailable(requireContext());
        tvOfflineBanner.setVisibility(offline ? View.VISIBLE : View.GONE);

        if (!s.loading) swipeRefresh.setRefreshing(false);
        adapter.setNeeds(s.items);
        renderState(s, search);
    }

    private void renderStatusChips(NeedListUiState s) {
        chipAll.setText(getString(R.string.needs_chip_with_count, getString(R.string.needs_chip_all), s.countAll));
        chipReview.setText(getString(R.string.needs_chip_with_count, getString(R.string.status_review_label), s.countReview));
        chipOpen.setText(getString(R.string.needs_chip_with_count, getString(R.string.status_open_label), s.countOpen));
        chipActive.setText(getString(R.string.needs_chip_with_count, getString(R.string.needs_chip_active), s.countActive));
        chipDone.setText(getString(R.string.needs_chip_with_count, getString(R.string.needs_chip_done), s.countDone));

        int wanted = R.id.chipAll;
        if (NeedListViewModel.GROUP_REVIEW.equals(s.statusGroup)) wanted = R.id.chipReview;
        else if (NeedListViewModel.GROUP_OPEN.equals(s.statusGroup)) wanted = R.id.chipOpen;
        else if (NeedListViewModel.GROUP_ACTIVE.equals(s.statusGroup)) wanted = R.id.chipActive;
        else if (NeedListViewModel.GROUP_DONE.equals(s.statusGroup)) wanted = R.id.chipDone;
        if (chipGroupStatus.getCheckedChipId() != wanted) chipGroupStatus.check(wanted);
    }

    private void renderSearchFilters(NeedListUiState s) {
        setFilterChip(chipFilterArea, s.area, getString(R.string.needs_filter_area));
        setFilterChip(chipFilterTopic, s.topic, getString(R.string.needs_filter_topic));
        setFilterChip(chipFilterTime, s.days > 0 ? timeLabel(s.days) : null, getString(R.string.needs_filter_time));
        tvResultCount.setVisibility(s.loading || s.error != null ? View.GONE : View.VISIBLE);
        tvResultCount.setText(getResources().getQuantityString(R.plurals.needs_result_count, s.items.size(), s.items.size()));
    }

    private void setFilterChip(Chip chip, @Nullable String value, String placeholder) {
        boolean active = value != null;
        chip.setText(active ? value : placeholder);
        chip.setChipBackgroundColorResource(active ? R.color.primary_light : R.color.surface);
        chip.setChipStrokeColorResource(active ? R.color.primary_light : R.color.divider);
    }

    private void renderState(NeedListUiState s, boolean search) {
        if (s.loading && s.items.isEmpty()) {
            stateView.showLoading();
            return;
        }
        if (s.noMembership) {
            stateView.showMessage(R.drawable.ic_shield,
                    getString(R.string.needs_state_no_membership_title),
                    getString(R.string.needs_state_no_membership_message),
                    getString(R.string.action_reload), x -> viewModel.reload());
            return;
        }
        if (s.error != null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), x -> viewModel.reload());
            return;
        }
        if (s.items.isEmpty()) {
            if (search && s.hasSearchFilter()) {
                stateView.showMessage(R.drawable.ic_search,
                        getString(R.string.needs_state_no_result_title),
                        getString(R.string.needs_state_no_result_message),
                        getString(R.string.action_clear_filters), x -> clearSearch());
            } else if (search) {
                stateView.showMessage(R.drawable.ic_inbox,
                        getString(R.string.needs_state_no_open_title),
                        getString(R.string.needs_state_no_open_message), null, null);
            } else if (!NeedListViewModel.GROUP_ALL.equals(s.statusGroup)) {
                stateView.showMessage(R.drawable.ic_inbox,
                        getString(R.string.needs_state_empty_group_title),
                        getString(R.string.needs_state_empty_group_message),
                        getString(R.string.needs_show_all), x -> viewModel.setStatusGroup(NeedListViewModel.GROUP_ALL));
            } else {
                stateView.showMessage(R.drawable.ic_inbox,
                        getString(R.string.needs_state_empty_unit_title),
                        getString(R.string.needs_state_empty_unit_message),
                        s.canCreate ? getString(R.string.btn_create_need) : null,
                        s.canCreate ? x -> startActivity(new Intent(requireContext(), CreateNeedActivity.class)) : null);
            }
            return;
        }
        stateView.hide();
    }

    private void clearSearch() {
        View root = getView();
        if (root != null) {
            TextView etSearch = root.findViewById(R.id.etSearch);
            etSearch.setText("");
        }
        viewModel.clearSearchFilters();
    }

    // =====================================================================
    // Bộ lọc F09
    // =====================================================================

    private void showAreaFilter() {
        if (lastState == null) return;
        showChoice(R.string.needs_filter_area_title, getString(R.string.needs_filter_all_areas),
                lastState.areaOptions, lastState.area, viewModel::setArea);
    }

    private void showTopicFilter() {
        if (lastState == null) return;
        showChoice(R.string.needs_filter_topic_title, getString(R.string.needs_filter_all_topics),
                lastState.topicOptions, lastState.topic, viewModel::setTopic);
    }

    private interface OnPicked {
        void picked(@Nullable String value);
    }

    /** Hộp thoại chọn một giá trị; mục đầu tiên là "Tất cả" (bỏ lọc). */
    private void showChoice(int titleRes, String allLabel, List<String> options,
                            @Nullable String current, OnPicked onPicked) {
        String[] items = new String[options.size() + 1];
        items[0] = allLabel;
        int checked = 0;
        for (int i = 0; i < options.size(); i++) {
            items[i + 1] = options.get(i);
            if (options.get(i).equals(current)) checked = i + 1;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    onPicked.picked(which == 0 ? null : items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void showTimeFilter() {
        if (lastState == null) return;
        String[] labels = getResources().getStringArray(R.array.need_time_filter_labels);
        int[] values = getResources().getIntArray(R.array.need_time_filter_days);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == lastState.days) checked = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.needs_filter_time_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.setDays(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private String timeLabel(int days) {
        String[] labels = getResources().getStringArray(R.array.need_time_filter_labels);
        int[] values = getResources().getIntArray(R.array.need_time_filter_days);
        for (int i = 0; i < values.length; i++) if (values[i] == days) return labels[i];
        return getString(R.string.needs_filter_time);
    }

    // =====================================================================
    // Mở nhu cầu
    // =====================================================================

    @Override
    public void onNeedClick(NeedSummary need) {
        Membership me = viewModel.getMembership();
        boolean canReview = me != null && me.isReviewer() && me.belongsToRecorderOf(need)
                && Constants.STATUS_REVIEW.equals(need.getStatus())
                && !me.getUid().equals(need.getRecorderUid());
        Class<?> target = canReview ? ReviewNeedActivity.class : NeedDetailActivity.class;
        Intent intent = new Intent(requireContext(), target);
        intent.putExtra(Constants.EXTRA_NEED_ID, need.getNeedId());
        startActivity(intent);
    }
}
