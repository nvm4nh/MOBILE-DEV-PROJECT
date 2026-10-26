package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.SearchAdapter;
import com.seniortribe.app.search.SearchHistory;
import com.seniortribe.app.search.SearchResult;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.SearchViewModel;

import java.util.List;

/**
 * E08 · Tìm kiếm chung: nhu cầu, bài đăng và lịch hẹn trong một ô tìm, không cần gõ dấu.
 * Mở từ biểu tượng tìm kiếm ở Bảng tin. Bấm kết quả để mở chi tiết nhu cầu, bài đăng hoặc
 * tiến độ của lịch hẹn.
 */
public class SearchActivity extends AppCompatActivity implements SearchAdapter.Listener {

    private SearchViewModel viewModel;
    private SearchAdapter adapter;
    private SearchHistory history;
    private EditText etSearch;
    private View btnClear, layoutRecent, frameResults, tvOffline;
    private ChipGroup chipsKind, chipsRecent;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        history = new SearchHistory(this);

        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        layoutRecent = findViewById(R.id.layoutRecent);
        frameResults = findViewById(R.id.frameResults);
        tvOffline = findViewById(R.id.tvOffline);
        chipsKind = findViewById(R.id.chipsKind);
        chipsRecent = findViewById(R.id.chipsRecent);
        stateView = new StateView(frameResults);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.requestFocus();
        });
        findViewById(R.id.btnClearRecent).setOnClickListener(v -> {
            history.clear();
            renderRecent();
        });

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter(this, this);
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView r, int state) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }
        });

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        chipsKind.check(chipFor(viewModel.getFilter()));
        chipsKind.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            viewModel.setFilter(filterFor(ids.get(0)));
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                viewModel.setQuery(s.toString());
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
        etSearch.setOnEditorActionListener((TextView v, int action, KeyEvent e) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                history.add(etSearch.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });

        viewModel.getUi().observe(this, this::render);
        viewModel.start();
        renderRecent();
        if (savedInstanceState == null) {
            etSearch.requestFocus();
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void render(SearchViewModel.Ui s) {
        boolean typing = !s.query.trim().isEmpty();
        layoutRecent.setVisibility(typing ? View.GONE : View.VISIBLE);
        frameResults.setVisibility(typing || s.unauthorized || s.error != null ? View.VISIBLE : View.GONE);
        tvOffline.setVisibility(s.postsOffline && typing ? View.VISIBLE : View.GONE);
        if (s.unauthorized) {
            adapter.show(new SearchViewModel.Ui());
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.needs_state_no_membership_title),
                    getString(R.string.needs_state_no_membership_message), null, null);
            return;
        }
        if (!typing) {
            adapter.show(new SearchViewModel.Ui());
            stateView.hide();
            return;
        }
        adapter.show(s);
        if (s.total() > 0) {
            stateView.hide();
        } else if (s.loading) {
            stateView.showLoading();
        } else if (s.error != null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
        } else {
            stateView.showMessage(R.drawable.ic_search, getString(R.string.search_empty_title),
                    getString(R.string.search_empty_message, s.query.trim()), null, null);
        }
    }

    private void renderRecent() {
        chipsRecent.removeAllViews();
        List<String> recent = history.recent();
        findViewById(R.id.tvRecentTitle).setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnClearRecent).setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        for (String q : recent) {
            Chip chip = new Chip(this);
            chip.setText(q);
            chip.setChipIconResource(R.drawable.ic_time);
            chip.setChipIconVisible(true);
            chip.setOnClickListener(v -> {
                etSearch.setText(q);
                etSearch.setSelection(q.length());
                hideKeyboard();
            });
            chipsRecent.addView(chip);
        }
    }

    @Override
    public void onResultClick(SearchResult r) {
        history.add(etSearch.getText().toString());
        Intent intent;
        if (r.kind == SearchResult.Kind.POST && r.post != null) {
            intent = new Intent(this, PostDetailActivity.class).putExtra(Constants.EXTRA_POST, r.post);
        } else if (r.kind == SearchResult.Kind.SCHEDULE && r.need != null) {
            intent = new Intent(this, NeedProgressActivity.class)
                    .putExtra(Constants.EXTRA_NEED_ID, r.need.getNeedId())
                    .putExtra(Constants.EXTRA_ASSIGNMENT_ID, r.need.getCurrentAssignmentId())
                    .putExtra(Constants.EXTRA_CASE_CODE, r.need.getCaseCode());
        } else if (r.need != null) {
            intent = new Intent(this, NeedDetailActivity.class).putExtra(Constants.EXTRA_NEED_ID, r.need.getNeedId());
        } else {
            return;
        }
        startActivity(intent);
    }

    @Override
    public void onSeeAll(int filter) {
        chipsKind.check(chipFor(filter));
    }

    private static int chipFor(int filter) {
        switch (filter) {
            case SearchViewModel.FILTER_NEEDS: return R.id.chipNeeds;
            case SearchViewModel.FILTER_POSTS: return R.id.chipPosts;
            case SearchViewModel.FILTER_SCHEDULES: return R.id.chipSchedules;
            default: return R.id.chipAll;
        }
    }

    private static int filterFor(int chipId) {
        if (chipId == R.id.chipNeeds) return SearchViewModel.FILTER_NEEDS;
        if (chipId == R.id.chipPosts) return SearchViewModel.FILTER_POSTS;
        if (chipId == R.id.chipSchedules) return SearchViewModel.FILTER_SCHEDULES;
        return SearchViewModel.FILTER_ALL;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }
}
