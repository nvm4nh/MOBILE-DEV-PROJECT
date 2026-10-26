package com.seniortribe.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.CreatePostActivity;
import com.seniortribe.app.activities.PostDetailActivity;
import com.seniortribe.app.adapters.PostAdapter;
import com.seniortribe.app.dialogs.PostOptionsSheet;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.FeedViewModel;

import java.util.List;

/**
 * Tab Bảng tin.
 *  - F04: danh sách bài của các tổ chức trong mạng lưới, lọc "Tất cả" / "Đơn vị của tôi", xem chi tiết.
 *  - F05: đại diện tổ chức đăng bài (nút "Đăng bài"), sửa / ẩn bài của tổ chức mình (nút ⋮).
 *  - F16: đang tải, rỗng, lỗi có nút Thử lại; mất mạng thì xem bài đã lưu trong SQLite;
 *    xoay màn hình không tải lại vì dữ liệu nằm trong FeedViewModel.
 */
public class FeedFragment extends Fragment implements PostAdapter.OnPostClickListener {

    private FeedViewModel viewModel;
    private PostAdapter adapter;
    private StateView stateView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvOfflineNotice;
    private ChipGroup chipGroup;
    private ExtendedFloatingActionButton fabCreatePost;
    private RecyclerView rvPosts;

    /** Đăng / sửa / xem chi tiết xong (có thay đổi) thì tải lại bảng tin. */
    private ActivityResultLauncher<Intent> postLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        postLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && viewModel != null) viewModel.refresh();
        });
        // Kết quả từ bảng Tùy chọn bài; đăng ký ở đây để vẫn nhận được sau khi xoay màn hình
        getChildFragmentManager().setFragmentResultListener(PostOptionsSheet.RESULT_KEY, this, (key, bundle) -> {
            Post post = findPost(bundle.getString(PostOptionsSheet.RESULT_POST_ID));
            if (post == null) return;
            if (PostOptionsSheet.ACTION_EDIT.equals(bundle.getString(PostOptionsSheet.RESULT_ACTION))) {
                openEdit(post);
            } else {
                confirmHide(post);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        stateView = new StateView(view);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvOfflineNotice = view.findViewById(R.id.tvOfflineNotice);
        chipGroup = view.findViewById(R.id.chipGroupFeed);
        fabCreatePost = view.findViewById(R.id.fabCreatePost);
        rvPosts = view.findViewById(R.id.rvPosts);

        adapter = new PostAdapter(requireContext(), this);
        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPosts.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);

        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        fabCreatePost.setOnClickListener(v -> openCreate());
        // E08 · Tìm kiếm chung, E09 · Khẩn cấp
        view.findViewById(R.id.btnFeedSearch).setOnClickListener(v -> startActivity(
                new android.content.Intent(requireContext(), com.seniortribe.app.activities.SearchActivity.class)));
        view.findViewById(R.id.btnFeedSos).setOnClickListener(v -> startActivity(
                new android.content.Intent(requireContext(), com.seniortribe.app.activities.SosActivity.class)));
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            viewModel.setFilter(ids.get(0) == R.id.chipFeedMyOrg
                    ? FeedViewModel.FILTER_MY_ORG : FeedViewModel.FILTER_ALL);
        });

        viewModel.getUi().observe(getViewLifecycleOwner(), this::render);
        viewModel.getMessages().observe(getViewLifecycleOwner(), event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(rvPosts, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.start();
    }

    private void render(FeedViewModel.Ui s) {
        List<Post> visible = s.visiblePosts();
        adapter.setPosts(visible, s.membership);
        swipeRefresh.setRefreshing(s.refreshing || s.busy);
        swipeRefresh.setEnabled(!s.loading);

        int wantedChip = s.filter == FeedViewModel.FILTER_MY_ORG ? R.id.chipFeedMyOrg : R.id.chipFeedAll;
        if (chipGroup.getCheckedChipId() != wantedChip) chipGroup.check(wantedChip);

        // Dòng báo offline chỉ hiện khi đang thật sự xem bài đã lưu
        boolean showNotice = s.isFromCache() && !s.posts.isEmpty();
        tvOfflineNotice.setVisibility(showNotice ? View.VISIBLE : View.GONE);
        if (showNotice) {
            tvOfflineNotice.setText(s.source == FeedViewModel.SOURCE_CACHE_OFFLINE
                    ? R.string.feed_offline_notice : R.string.feed_stale_notice);
        }

        // Đăng bài cần máy chủ xác nhận, nên chỉ mở khi đang có dữ liệu mới từ máy chủ
        boolean showFab = s.canPost() && !s.loading && !s.isFromCache();
        fabCreatePost.setVisibility(showFab ? View.VISIBLE : View.GONE);

        if (s.loading) {
            stateView.showLoading();
        } else if (s.offlineNoCache) {
            stateView.showMessage(R.drawable.ic_cloud_off, getString(R.string.feed_offline_empty_title),
                    getString(R.string.feed_offline_empty_message),
                    getString(R.string.action_retry), v -> viewModel.refresh());
        } else if (s.error != null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.refresh());
        } else if (visible.isEmpty()) {
            showEmpty(s);
        } else {
            stateView.hide();
        }
    }

    private void showEmpty(FeedViewModel.Ui s) {
        if (s.filter != FeedViewModel.FILTER_MY_ORG) {
            stateView.showMessage(R.drawable.ic_feed, getString(R.string.feed_empty_title),
                    getString(R.string.feed_empty_message), null, null);
        } else if (s.membership == null) {
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.feed_no_org_title),
                    getString(R.string.feed_no_org_message), null, null);
        } else if (s.canPost() && !s.isFromCache()) {
            stateView.showMessage(R.drawable.ic_feed, getString(R.string.feed_empty_my_org_title),
                    getString(R.string.feed_empty_my_org_message),
                    getString(R.string.btn_create_post), v -> openCreate());
        } else {
            stateView.showMessage(R.drawable.ic_feed, getString(R.string.feed_empty_my_org_title),
                    getString(R.string.feed_empty_message), null, null);
        }
    }

    // =====================================================================
    // Thao tác
    // =====================================================================

    @Override
    public void onPostClick(Post post) {
        // Truyền cả bài để xem chi tiết được cả khi offline
        Intent intent = new Intent(requireContext(), PostDetailActivity.class);
        intent.putExtra(Constants.EXTRA_POST, post);
        postLauncher.launch(intent);
    }

    @Override
    public void onPostMenu(Post post) {
        if (getChildFragmentManager().findFragmentByTag("post_options") != null) return;
        PostOptionsSheet.newInstance(post.getId(), post.getTitle(), post.getOrgName())
                .show(getChildFragmentManager(), "post_options");
    }

    private void openCreate() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Snackbar.make(rvPosts, R.string.feed_offline_cannot_post, Snackbar.LENGTH_LONG).show();
            return;
        }
        postLauncher.launch(new Intent(requireContext(), CreatePostActivity.class));
    }

    private void openEdit(Post post) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Snackbar.make(rvPosts, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(requireContext(), CreatePostActivity.class);
        intent.putExtra(Constants.EXTRA_POST, post);
        postLauncher.launch(intent);
    }

    private void confirmHide(Post post) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.post_hide_confirm_title)
                .setMessage(R.string.post_hide_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.post_action_hide, (d, w) -> viewModel.hide(post))
                .show();
    }

    @Nullable
    private Post findPost(@Nullable String postId) {
        FeedViewModel.Ui s = viewModel != null ? viewModel.getUi().getValue() : null;
        if (s == null || postId == null) return null;
        for (Post p : s.posts) if (postId.equals(p.getId())) return p;
        return null;
    }
}
