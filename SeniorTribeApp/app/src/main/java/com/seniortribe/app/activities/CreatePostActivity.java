package com.seniortribe.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.models.PostForm;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.OrgAvatar;
import com.seniortribe.app.utils.PrivacyGuard;
import com.seniortribe.app.viewmodels.CreatePostViewModel;

/**
 * F05 · Đại diện tổ chức đăng bài, hoặc sửa bài của tổ chức mình (mở kèm Constants.EXTRA_POST).
 *  - Chỉ báo thành công khi máy chủ xác nhận; offline thì báo cần mạng, không báo thành công giả.
 *  - Chặn số điện thoại trong bài (không đăng thông tin nhận diện người được hỗ trợ).
 *  - Xoay màn hình: chữ trong ô nhập và trạng thái đang gửi được giữ nguyên (F16).
 */
public class CreatePostActivity extends AppCompatActivity {

    private static final int TITLE_MIN = 5, TITLE_MAX = 120;
    private static final int CONTENT_MIN = 10, CONTENT_MAX = 2000;
    private static final int EVENT_MAX = 120;

    private CreatePostViewModel viewModel;
    private TextInputLayout tilTitle, tilContent, tilEventTime, tilEventPlace;
    private TextInputEditText etTitle, etContent, etEventTime, etEventPlace;
    private View layoutPostAs, progress;
    private TextView tvPostAsAvatar, tvPostAsOrg;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        Post editing = (Post) getIntent().getSerializableExtra(Constants.EXTRA_POST);

        MaterialToolbar toolbar = findViewById(R.id.toolbarCreatePost);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(editing != null ? R.string.post_edit_title : R.string.btn_create_post);
        tilTitle = findViewById(R.id.tilPostTitle);
        tilContent = findViewById(R.id.tilPostContent);
        tilEventTime = findViewById(R.id.tilEventTime);
        tilEventPlace = findViewById(R.id.tilEventPlace);
        etTitle = findViewById(R.id.etPostTitle);
        etContent = findViewById(R.id.etPostContent);
        etEventTime = findViewById(R.id.etEventTime);
        etEventPlace = findViewById(R.id.etEventPlace);
        layoutPostAs = findViewById(R.id.layoutPostAs);
        tvPostAsAvatar = findViewById(R.id.tvPostAsAvatar);
        tvPostAsOrg = findViewById(R.id.tvPostAsOrg);
        btnSubmit = findViewById(R.id.btnSubmitPost);
        progress = findViewById(R.id.progressCreatePost);

        // Sửa bài: điền sẵn một lần; sau khi xoay, các ô tự giữ chữ đang gõ
        if (editing != null && savedInstanceState == null) {
            etTitle.setText(editing.getTitle());
            etContent.setText(editing.getContent());
            etEventTime.setText(editing.getEventTime());
            etEventPlace.setText(editing.getEventPlace());
        }

        viewModel = new ViewModelProvider(this).get(CreatePostViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(btnSubmit, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getDone().observe(this, event -> {
            if (event.getIfNotHandled() == null) return;
            CreatePostViewModel.Ui s = viewModel.getUi().getValue();
            boolean wasEditing = s != null && s.isEditing();
            Toast.makeText(this, wasEditing ? R.string.post_saved : R.string.post_published, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_OK);
            finish();
        });

        btnSubmit.setOnClickListener(v -> submit());
        viewModel.start(editing);
    }

    private void render(CreatePostViewModel.Ui s) {
        progress.setVisibility(s.checking || s.busy ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(s.canSubmit());
        if (s.checking) btnSubmit.setText(R.string.post_checking_permission);
        else if (s.busy) btnSubmit.setText(s.isEditing() ? R.string.post_saving : R.string.post_publishing);
        else btnSubmit.setText(s.isEditing() ? R.string.post_save_changes : R.string.btn_create_post);

        // "Đăng với tư cách": bài mới lấy tổ chức của tài khoản, bài sửa giữ tổ chức đã đăng
        String orgName = s.isEditing() ? s.editing.getOrgName()
                : (s.membership != null ? s.membership.getOrgName() : null);
        Membership me = s.membership;
        boolean showPostAs = orgName != null && (s.isEditing() || (me != null && me.isRepresentative()));
        layoutPostAs.setVisibility(showPostAs ? View.VISIBLE : View.INVISIBLE);
        if (showPostAs) {
            tvPostAsAvatar.setText(OrgAvatar.initials(orgName));
            tvPostAsOrg.setText(orgName);
        }

        boolean editable = !s.busy;
        etTitle.setEnabled(editable);
        etContent.setEnabled(editable);
        etEventTime.setEnabled(editable);
        etEventPlace.setEnabled(editable);
    }

    private void submit() {
        String title = text(etTitle);
        String content = text(etContent);
        String eventTime = text(etEventTime);
        String eventPlace = text(etEventPlace);
        tilTitle.setError(null);
        tilContent.setError(null);
        tilEventTime.setError(null);
        tilEventPlace.setError(null);

        boolean ok = true;
        if (title.length() < TITLE_MIN || title.length() > TITLE_MAX) {
            tilTitle.setError(getString(R.string.post_error_title_length));
            ok = false;
        }
        if (content.length() < CONTENT_MIN || content.length() > CONTENT_MAX) {
            tilContent.setError(getString(R.string.post_error_content_length));
            ok = false;
        }
        if (eventTime.length() > EVENT_MAX) {
            tilEventTime.setError(getString(R.string.post_error_event_length));
            ok = false;
        }
        if (eventPlace.length() > EVENT_MAX) {
            tilEventPlace.setError(getString(R.string.post_error_event_length));
            ok = false;
        }
        // Không đăng số điện thoại (thông tin nhận diện người được hỗ trợ) ở bất kỳ ô nào
        if (PrivacyGuard.containsPhoneNumber(title)) {
            tilTitle.setError(getString(R.string.post_error_phone));
            ok = false;
        }
        if (PrivacyGuard.containsPhoneNumber(content)) {
            tilContent.setError(getString(R.string.post_error_phone));
            ok = false;
        }
        if (PrivacyGuard.containsPhoneNumber(eventPlace)) {
            tilEventPlace.setError(getString(R.string.post_error_phone));
            ok = false;
        }
        if (!ok) return;

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Snackbar.make(btnSubmit, R.string.feed_offline_cannot_post, Snackbar.LENGTH_LONG).show();
            return;
        }
        viewModel.submit(new PostForm(title, content, eventTime, eventPlace));
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
