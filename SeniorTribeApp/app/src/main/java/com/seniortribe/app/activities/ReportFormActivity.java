package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.PrivacyGuard;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ReportFormViewModel;

/**
 * E03 · Gửi phản ánh về lần hỗ trợ của một nhu cầu.
 * Người gửi là đầu mối ghi nhận hoặc thành viên tổ chức đang phụ trách; phản ánh luôn nói về bên còn lại
 * và chuyển cho điều phối viên của đơn vị ghi nhận.
 */
public class ReportFormActivity extends AppCompatActivity {

    private ReportFormViewModel viewModel;

    private View root, scroll;
    private TextView tvRelated, tvTypeError;
    private RadioGroup rgType;
    private TextInputLayout tilDescription;
    private TextInputEditText etDescription;
    private MaterialButton btnSubmit;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_form);
        String needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        root = findViewById(R.id.rootReportForm);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        scroll = findViewById(R.id.scrollReportForm);
        tvRelated = findViewById(R.id.tvRelated);
        tvTypeError = findViewById(R.id.tvTypeError);
        rgType = findViewById(R.id.rgType);
        tilDescription = findViewById(R.id.tilDescription);
        etDescription = findViewById(R.id.etDescription);
        btnSubmit = findViewById(R.id.btnSubmitReport);
        stateView = new StateView(findViewById(R.id.frameReportForm));

        toolbar.setNavigationOnClickListener(v -> finish());
        rgType.setOnCheckedChangeListener((g, id) -> tvTypeError.setVisibility(View.GONE));
        btnSubmit.setOnClickListener(v -> submit());

        viewModel = new ViewModelProvider(this).get(ReportFormViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getSubmitted().observe(this, e -> {
            if (e.getIfNotHandled() == null) return;
            Snackbar.make(root, R.string.report_sent, Snackbar.LENGTH_LONG).show();
            root.postDelayed(this::finish, 1200);
        });
        viewModel.start(needId);
    }

    private void render(ReportFormViewModel.Ui s) {
        scroll.setVisibility(View.INVISIBLE);
        if (s.loading) {
            stateView.showLoading();
            return;
        }
        if (s.error != null && s.need == null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        NeedSummary n = s.need;
        if (n == null) {
            finish();
            return;
        }
        if (!FeedbackRepository.canReport(s.membership, n)) {
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.report_title),
                    getString(R.string.report_not_allowed), null, null);
            return;
        }
        stateView.hide();
        scroll.setVisibility(View.VISIBLE);
        String other = FeedbackRepository.reportedOrgNameFor(s.membership, n);
        String code = n.getCaseCode() != null ? n.getCaseCode() : "";
        tvRelated.setText(TextUtils.isEmpty(other) ? code : getString(R.string.report_related_value, code, other));
        btnSubmit.setEnabled(!s.busy);
        rgType.setEnabled(!s.busy);
        etDescription.setEnabled(!s.busy);
    }

    private String selectedType() {
        int id = rgType.getCheckedRadioButtonId();
        if (id == R.id.rbLate) return FeedbackReport.TYPE_LATE;
        if (id == R.id.rbAttitude) return FeedbackReport.TYPE_ATTITUDE;
        if (id == R.id.rbPrivacy) return FeedbackReport.TYPE_PRIVACY;
        if (id == R.id.rbOther) return FeedbackReport.TYPE_OTHER;
        return null;
    }

    private void submit() {
        String type = selectedType();
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        boolean ok = true;
        if (type == null) {
            tvTypeError.setVisibility(View.VISIBLE);
            ok = false;
        }
        if (description.length() < FeedbackRepository.MIN_DESCRIPTION) {
            tilDescription.setError(getString(R.string.report_error_desc));
            ok = false;
        } else if (PrivacyGuard.containsPhoneNumber(description)) {
            tilDescription.setError(getString(R.string.feedback_phone_warning));
            ok = false;
        } else {
            tilDescription.setError(null);
        }
        if (ok) viewModel.submit(type, description);
    }
}
