package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.NeedDetailViewModel;

/**
 * F07 · Rà soát nhu cầu.
 *
 * Điều phối viên của đơn vị ghi nhận kiểm tra 4 mục rồi Duyệt (REVIEW → OPEN) hoặc
 * Yêu cầu bổ sung (giữ REVIEW, ghi chú cho đầu mối). Đây là kiểm tra nội bộ,
 * không phải xác minh hoàn cảnh (R05).
 */
public class ReviewNeedActivity extends AppCompatActivity {

    private NeedDetailViewModel viewModel;
    private StateView stateView;

    private MaterialToolbar toolbar;
    private NestedScrollView scrollContent;
    private View progressBusy;
    private TextView tvStatus, tvSubmittedBy, tvTitle, tvContent, tvTopic, tvArea, tvDesiredTime, tvSource,
            tvConsent, tvPreviousNote;
    private CheckBox cbNoIdentity, cbSourceClear, cbConsentRecorded, cbWardOnly;
    private TextInputLayout tilReviewNote;
    private EditText etReviewNote;
    private MaterialButton btnApprove, btnRequestRevision;

    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_need);
        bindViews();

        toolbar.setNavigationOnClickListener(v -> finish());
        CheckBox[] checks = {cbNoIdentity, cbSourceClear, cbConsentRecorded, cbWardOnly};
        for (CheckBox cb : checks) cb.setOnCheckedChangeListener((b, c) -> updateApproveEnabled());
        etReviewNote.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilReviewNote.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnApprove.setOnClickListener(v -> approve());
        btnRequestRevision.setOnClickListener(v -> requestRevision());

        String needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        viewModel = new ViewModelProvider(this).get(NeedDetailViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getCloseScreen().observe(this, event -> {
            Boolean close = event.getIfNotHandled();
            if (Boolean.TRUE.equals(close)) {
                Toast.makeText(this, R.string.review_done, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        viewModel.start(needId);
    }

    private void bindViews() {
        stateView = new StateView(findViewById(R.id.rootReviewNeed));
        toolbar = findViewById(R.id.toolbar);
        scrollContent = findViewById(R.id.scrollContent);
        progressBusy = findViewById(R.id.progressBusy);
        tvStatus = findViewById(R.id.tvStatus);
        tvSubmittedBy = findViewById(R.id.tvSubmittedBy);
        tvTitle = findViewById(R.id.tvTitle);
        tvContent = findViewById(R.id.tvContent);
        tvTopic = findViewById(R.id.tvTopic);
        tvArea = findViewById(R.id.tvArea);
        tvDesiredTime = findViewById(R.id.tvDesiredTime);
        tvSource = findViewById(R.id.tvSource);
        tvConsent = findViewById(R.id.tvConsent);
        tvPreviousNote = findViewById(R.id.tvPreviousNote);
        cbNoIdentity = findViewById(R.id.cbNoIdentity);
        cbSourceClear = findViewById(R.id.cbSourceClear);
        cbConsentRecorded = findViewById(R.id.cbConsentRecorded);
        cbWardOnly = findViewById(R.id.cbWardOnly);
        tilReviewNote = findViewById(R.id.tilReviewNote);
        etReviewNote = findViewById(R.id.etReviewNote);
        btnApprove = findViewById(R.id.btnApprove);
        btnRequestRevision = findViewById(R.id.btnRequestRevision);
    }

    private void render(NeedDetailViewModel.Ui s) {
        busy = s.busy;
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);

        if (s.loading || (s.need == null && s.error == null)) {
            scrollContent.setVisibility(View.GONE);
            stateView.showLoading();
            return;
        }
        if (s.error != null && s.need == null) {
            scrollContent.setVisibility(View.GONE);
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        NeedSummary need = s.need;
        Membership me = s.membership;

        boolean allowed = me != null && me.isReviewer() && me.belongsToRecorderOf(need)
                && !me.getUid().equals(need.getRecorderUid());
        if (!allowed) {
            scrollContent.setVisibility(View.GONE);
            stateView.showMessage(R.drawable.ic_shield, getString(R.string.review_not_allowed_title),
                    getString(R.string.review_not_allowed_message), getString(R.string.action_back), v -> finish());
            return;
        }
        // Đang gửi thì bỏ qua cập nhật trạng thái, tránh nháy màn hình trước khi đóng
        if (!Constants.STATUS_REVIEW.equals(need.getStatus()) && !s.busy) {
            scrollContent.setVisibility(View.GONE);
            stateView.showMessage(R.drawable.ic_check, getString(R.string.review_already_done_title),
                    getString(R.string.review_already_done_message, getString(NeedStatus.label(need.getStatus()))),
                    getString(R.string.action_back), v -> finish());
            return;
        }

        stateView.hide();
        scrollContent.setVisibility(View.VISIBLE);
        toolbar.setTitle(getString(R.string.review_title_with_code, need.getCaseCode()));
        NeedStatus.bindChip(tvStatus, need.getStatus());
        String when = need.getSubmittedAt() != null ? DateTimeUtils.formatDate(need.getSubmittedAt().getTime()) : "";
        tvSubmittedBy.setText(getString(R.string.review_submitted_by, nonNull(need.getRecorderName()), when));
        tvTitle.setText(need.getTitle());
        tvContent.setText(need.getContent());
        tvTopic.setText(need.getTopic());
        tvArea.setText(need.getArea());
        tvDesiredTime.setText(need.getDesiredTime());
        tvSource.setText(getString(R.string.review_source, nonNull(need.getSource())));
        tvConsent.setText(need.isConsentRecorded()
                ? getString(R.string.review_consent_yes, nonNull(need.getConsentMethod()))
                : getString(R.string.review_consent_no));

        boolean hadRevision = need.isNeedsRevision() && !TextUtils.isEmpty(need.getReviewNote());
        tvPreviousNote.setVisibility(hadRevision ? View.VISIBLE : View.GONE);
        if (hadRevision) tvPreviousNote.setText(getString(R.string.review_previous_note, need.getReviewNote()));

        btnRequestRevision.setEnabled(!s.busy);
        updateApproveEnabled();
    }

    /** Chỉ cho duyệt khi đã đánh dấu đủ 4 mục kiểm tra. */
    private void updateApproveEnabled() {
        boolean allChecked = cbNoIdentity.isChecked() && cbSourceClear.isChecked()
                && cbConsentRecorded.isChecked() && cbWardOnly.isChecked();
        btnApprove.setEnabled(allChecked && !busy);
    }

    private void approve() {
        if (!requireOnline()) return;
        viewModel.approve();
    }

    private void requestRevision() {
        String note = etReviewNote.getText() != null ? etReviewNote.getText().toString().trim() : "";
        if (note.isEmpty()) {
            tilReviewNote.setError(getString(R.string.review_error_note));
            etReviewNote.requestFocus();
            return;
        }
        if (!requireOnline()) return;
        viewModel.requestRevision(note);
    }

    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(scrollContent, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
