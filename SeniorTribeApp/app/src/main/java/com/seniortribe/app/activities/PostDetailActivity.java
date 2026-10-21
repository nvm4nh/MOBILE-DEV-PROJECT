package com.seniortribe.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.dialogs.PostOptionsSheet;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.OrgAvatar;
import com.seniortribe.app.viewmodels.PostDetailViewModel;

/**
 * F04 · Chi tiết bài đăng. Xem được cả khi offline (bài được truyền từ Bảng tin).
 * F05 · Đại diện của tổ chức đã đăng bấm ⋮ để sửa hoặc ẩn bài.
 */
public class PostDetailActivity extends AppCompatActivity {

    private PostDetailViewModel viewModel;
    private MaterialToolbar toolbar;
    private View progress, tvCachedNotice;
    private TextView tvAvatar, tvAuthorOrg, tvPostDate, tvPostTitle, tvPostContent;
    private TextView tvEventTime, tvEventPlace, tvRepresentative;

    private ActivityResultLauncher<Intent> editLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        Post post = (Post) getIntent().getSerializableExtra(Constants.EXTRA_POST);
        if (post == null) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbarPostDetail);
        progress = findViewById(R.id.progressPostDetail);
        tvCachedNotice = findViewById(R.id.tvCachedNotice);
        tvAvatar = findViewById(R.id.tvDetailAvatar);
        tvAuthorOrg = findViewById(R.id.tvAuthorOrg);
        tvPostDate = findViewById(R.id.tvPostDate);
        tvPostTitle = findViewById(R.id.tvPostTitle);
        tvPostContent = findViewById(R.id.tvPostContent);
        tvEventTime = findViewById(R.id.tvEventTime);
        tvEventPlace = findViewById(R.id.tvEventPlace);
        tvRepresentative = findViewById(R.id.tvRepresentative);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_post_detail);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);

        // Sửa xong: tải lại bài và báo Bảng tin cập nhật
        editLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK);
                viewModel.reload();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(PostOptionsSheet.RESULT_KEY, this, (key, bundle) -> {
            if (PostOptionsSheet.ACTION_EDIT.equals(bundle.getString(PostOptionsSheet.RESULT_ACTION))) openEdit();
            else confirmHide();
        });

        viewModel = new ViewModelProvider(this).get(PostDetailViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(tvPostTitle, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getClosed().observe(this, event -> {
            if (event.getIfNotHandled() == null) return;
            setResult(Activity.RESULT_OK);
            finish();
        });
        viewModel.start(post);
    }

    private void render(PostDetailViewModel.Ui s) {
        Post post = s.post;
        progress.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        tvCachedNotice.setVisibility(post.isCached() ? View.VISIBLE : View.GONE);

        tvAvatar.setText(OrgAvatar.initials(post.getOrgName()));
        OrgAvatar.bindName(tvAuthorOrg, post.getOrgName(), post.isOrgVerified());
        String posted = getString(R.string.post_posted_at, formatPostedAt(post.getCreatedAtMillis()));
        boolean edited = post.getUpdatedAt() != null && post.getCreatedAt() != null
                && post.getUpdatedAt().getTime() - post.getCreatedAt().getTime() > 60_000;
        tvPostDate.setText(edited ? getString(R.string.post_updated_suffix, posted) : posted);
        tvPostTitle.setText(post.getTitle());
        tvPostContent.setText(post.getContent());

        bindOptional(tvEventTime, post.getEventTime());
        bindOptional(tvEventPlace, post.getEventPlace());
        bindOptional(tvRepresentative, TextUtils.isEmpty(post.getAuthorName())
                ? null : getString(R.string.post_representative, post.getAuthorName()));

        MenuItem options = toolbar.getMenu().findItem(R.id.action_post_options);
        if (options != null) {
            options.setVisible(s.canManage());
            options.setEnabled(!s.busy);
        }
    }

    private boolean onMenuItem(MenuItem item) {
        if (item.getItemId() != R.id.action_post_options) return false;
        PostDetailViewModel.Ui s = viewModel.getUi().getValue();
        if (s == null || !s.canManage()
                || getSupportFragmentManager().findFragmentByTag("post_options") != null) return true;
        PostOptionsSheet.newInstance(s.post.getId(), s.post.getTitle(), s.post.getOrgName())
                .show(getSupportFragmentManager(), "post_options");
        return true;
    }

    private void openEdit() {
        PostDetailViewModel.Ui s = viewModel.getUi().getValue();
        if (s == null || s.post == null) return;
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Snackbar.make(tvPostTitle, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, CreatePostActivity.class);
        intent.putExtra(Constants.EXTRA_POST, s.post);
        editLauncher.launch(intent);
    }

    private void confirmHide() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.post_hide_confirm_title)
                .setMessage(R.string.post_hide_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.post_action_hide, (d, w) -> viewModel.hide())
                .show();
    }

    /** "08:15 · 12/10/2026" như Figma. */
    private static String formatPostedAt(long millis) {
        return DateTimeUtils.formatTime(millis) + " · " + DateTimeUtils.formatDate(millis);
    }

    private static void bindOptional(TextView view, String text) {
        boolean show = !TextUtils.isEmpty(text);
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) view.setText(text);
    }
}
