package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.ReportAdapter;
import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.dialogs.TextInputDialog;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.ReportNote;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ReportDetailViewModel;

/**
 * E03 · Chi tiết phản ánh.
 *  - Điều phối viên của đơn vị ghi nhận: "Tiếp nhận xử lý" (Mới → Đang xử lý), "Hỏi thêm",
 *    ghi kết quả và "Đóng phản ánh" (Đang xử lý → Đã đóng).
 *  - Người gửi: theo dõi quá trình, "Bổ sung thông tin" khi chưa đóng.
 *  - Tổ chức bị phản ánh: chỉ đọc, và chỉ thấy khi điều phối viên đã bắt đầu xử lý (Security Rules).
 */
public class ReportDetailActivity extends AppCompatActivity {

    public static final String EXTRA_REPORT_ID = "extra_report_id";
    private static final String KEY_ASK = "report_ask";
    private static final String KEY_INFO = "report_info";

    private ReportDetailViewModel viewModel;
    private View root, scroll;
    private MaterialToolbar toolbar;
    private LinearProgressIndicator progressBusy;
    private TextView tvStatus, tvSentBy, tvType, tvDescription, tvRelated, tvAbout, tvNotesEmpty, tvResult;
    private LinearLayout layoutNotes, layoutResolution;
    private TextInputLayout tilResolution;
    private TextInputEditText etResolution;
    private View layoutActions, spaceActions;
    private MaterialButton btnPrimary, btnSecondary;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);
        String reportId = getIntent().getStringExtra(EXTRA_REPORT_ID);
        if (reportId == null) {
            finish();
            return;
        }
        bindViews();
        toolbar.setNavigationOnClickListener(v -> finish());

        getSupportFragmentManager().setFragmentResultListener(KEY_ASK, this, (k, r) ->
                viewModel.askMore(r.getString(TextInputDialog.RESULT_TEXT)));
        getSupportFragmentManager().setFragmentResultListener(KEY_INFO, this, (k, r) ->
                viewModel.addInfo(r.getString(TextInputDialog.RESULT_TEXT)));

        viewModel = new ViewModelProvider(this).get(ReportDetailViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getDone().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.start(reportId);
    }

    private void bindViews() {
        root = findViewById(R.id.rootReportDetail);
        toolbar = findViewById(R.id.toolbar);
        progressBusy = findViewById(R.id.progressBusy);
        scroll = findViewById(R.id.scrollReport);
        tvStatus = findViewById(R.id.tvReportStatus);
        tvSentBy = findViewById(R.id.tvSentBy);
        tvType = findViewById(R.id.tvReportType);
        tvDescription = findViewById(R.id.tvReportDescription);
        tvRelated = findViewById(R.id.tvReportRelated);
        tvAbout = findViewById(R.id.tvReportAbout);
        tvNotesEmpty = findViewById(R.id.tvNotesEmpty);
        layoutNotes = findViewById(R.id.layoutNotes);
        layoutResolution = findViewById(R.id.layoutResolution);
        tilResolution = findViewById(R.id.tilResolution);
        etResolution = findViewById(R.id.etResolution);
        tvResult = findViewById(R.id.tvResultCard);
        layoutActions = findViewById(R.id.layoutActions);
        spaceActions = findViewById(R.id.spaceActions);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnSecondary = findViewById(R.id.btnSecondary);
        stateView = new StateView(findViewById(R.id.frameReportDetail));
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(ReportDetailViewModel.Ui s) {
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        FeedbackReport r = s.report;
        if (r == null) {
            scroll.setVisibility(View.INVISIBLE);
            layoutActions.setVisibility(View.GONE);
            if (s.loading) {
                stateView.showLoading();
            } else {
                stateView.showError(getString(R.string.state_error_title),
                        s.error != null ? s.error : getString(R.string.state_error_title),
                        getString(R.string.action_retry), v -> viewModel.retry());
            }
            return;
        }
        stateView.hide();
        scroll.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(r.getCaseCode())) toolbar.setTitle(getString(R.string.report_detail_title, r.getCaseCode()));
        ReportAdapter.bindStatus(tvStatus, r.getStatus());
        String when = r.getCreatedAt() != null ? DateTimeUtils.formatShortStamp(r.getCreatedAt().getTime()) : "";
        tvSentBy.setText(getString(R.string.report_sent_by, when, nonNull(r.getCreatedByName())));
        tvType.setText(ReportAdapter.typeLabel(r.getType()));
        tvDescription.setText(r.getDescription());
        tvRelated.setText(getString(R.string.report_related_need, nonNull(r.getCaseCode())));
        tvRelated.setOnClickListener(TextUtils.isEmpty(r.getNeedId()) ? null : v ->
                startActivity(new Intent(this, NeedDetailActivity.class).putExtra(Constants.EXTRA_NEED_ID, r.getNeedId())));
        tvAbout.setText(getString(R.string.report_about, nonNull(r.getReportedOrgName())));

        renderNotes(s);
        renderResult(s, r);
        renderActions(s, r);
    }

    private void renderNotes(ReportDetailViewModel.Ui s) {
        layoutNotes.removeAllViews();
        tvNotesEmpty.setVisibility(s.notes.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReportNote n : s.notes) {
            View row = inflater.inflate(R.layout.item_report_note, layoutNotes, false);
            ((TextView) row.findViewById(R.id.tvNoteKind)).setText(kindLabel(n.getKind()));
            TextView tvText = row.findViewById(R.id.tvNoteText);
            tvText.setText(n.getText());
            tvText.setVisibility(TextUtils.isEmpty(n.getText()) ? View.GONE : View.VISIBLE);
            String role = getString(ReportNote.ROLE_REVIEWER.equals(n.getByRole())
                    ? R.string.report_role_reviewer : R.string.report_role_reporter);
            ((TextView) row.findViewById(R.id.tvNoteBy)).setText(getString(R.string.report_note_by,
                    nonNull(n.getByName()), role, DateTimeUtils.formatShortStamp(n.getAtMillis())));
            layoutNotes.addView(row);
        }
    }

    private void renderResult(ReportDetailViewModel.Ui s, FeedbackReport r) {
        boolean editing = FeedbackRepository.canHandle(s.membership, r)
                && FeedbackReport.STATUS_IN_PROGRESS.equals(r.getStatus());
        layoutResolution.setVisibility(editing ? View.VISIBLE : View.GONE);
        etResolution.setEnabled(!s.busy);
        if (r.isClosed() && !TextUtils.isEmpty(r.getResolution())) {
            tvResult.setVisibility(View.VISIBLE);
            String closed = r.getClosedAt() != null ? DateTimeUtils.formatShortStamp(r.getClosedAt().getTime()) : "";
            tvResult.setText(getString(R.string.report_resolution_label) + ": " + r.getResolution() + "\n"
                    + getString(R.string.report_closed_on, closed, nonNull(r.getHandlerName())));
        } else {
            tvResult.setVisibility(View.GONE);
        }
    }

    /** Nút cuối màn hình theo vai trò và trạng thái. */
    private void renderActions(ReportDetailViewModel.Ui s, FeedbackReport r) {
        Membership me = s.membership;
        boolean handler = FeedbackRepository.canHandle(me, r);
        boolean reporter = FeedbackRepository.isReporter(me, r);
        btnPrimary.setOnClickListener(null);
        btnSecondary.setOnClickListener(null);

        if (handler && r.isNew()) {
            show(R.string.report_action_start, 0);
            btnPrimary.setOnClickListener(v -> viewModel.startHandling());
        } else if (handler && FeedbackReport.STATUS_IN_PROGRESS.equals(r.getStatus())) {
            show(R.string.report_action_close, R.string.report_action_ask);
            btnPrimary.setOnClickListener(v -> confirmClose());
            btnSecondary.setOnClickListener(v -> TextInputDialog.newInstance(KEY_ASK,
                    getString(R.string.report_ask_title), null, getString(R.string.report_ask_hint),
                    getString(R.string.report_send), 2, null).show(getSupportFragmentManager(), KEY_ASK));
        } else if (reporter && !r.isClosed()) {
            show(R.string.report_action_add_info, 0);
            btnPrimary.setOnClickListener(v -> TextInputDialog.newInstance(KEY_INFO,
                    getString(R.string.report_info_title), null, getString(R.string.report_info_hint),
                    getString(R.string.report_send), 2, null).show(getSupportFragmentManager(), KEY_INFO));
        } else {
            layoutActions.setVisibility(View.GONE);
            return;
        }
        btnPrimary.setEnabled(!s.busy);
        btnSecondary.setEnabled(!s.busy);
    }

    private void show(int primaryText, int secondaryText) {
        layoutActions.setVisibility(View.VISIBLE);
        btnPrimary.setText(primaryText);
        boolean two = secondaryText != 0;
        btnSecondary.setVisibility(two ? View.VISIBLE : View.GONE);
        spaceActions.setVisibility(two ? View.VISIBLE : View.GONE);
        if (two) btnSecondary.setText(secondaryText);
    }

    private void confirmClose() {
        String resolution = etResolution.getText() != null ? etResolution.getText().toString().trim() : "";
        if (resolution.length() < 5) {
            tilResolution.setError(getString(R.string.report_resolution_error));
            etResolution.requestFocus();
            return;
        }
        tilResolution.setError(null);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_close_confirm_title)
                .setMessage(R.string.report_close_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.report_action_close, (d, w) -> viewModel.close(resolution))
                .show();
    }

    private int kindLabel(String kind) {
        if (ReportNote.KIND_START.equals(kind)) return R.string.report_note_start;
        if (ReportNote.KIND_ASK.equals(kind)) return R.string.report_note_ask;
        if (ReportNote.KIND_CLOSE.equals(kind)) return R.string.report_note_close;
        return R.string.report_note_info;
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
