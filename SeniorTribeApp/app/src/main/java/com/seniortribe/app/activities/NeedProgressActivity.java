package com.seniortribe.app.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.CoordinationViewModel;

import java.util.Date;

/**
 * F13 · Tiến độ & đối soát kết quả (Figma "Tiến độ · NC-0142").
 *
 * Dòng tiến độ 5 bước: Đã tiếp nhận → Đã hẹn lịch → Tổ chức báo đã hỗ trợ → Đầu mối đối soát
 * → Hoàn thành. Bên dưới là việc cần làm ở bước hiện tại:
 *  - SCHEDULED, thành viên tổ chức phụ trách: ghi kết quả → "Báo đã hỗ trợ" (sang DELIVERED).
 *  - DELIVERED, đầu mối ghi nhận: xem kết quả tổ chức báo, đối soát với cụ rồi "Hoàn thành"
 *    (sang COMPLETED, đóng lần tiếp nhận) hoặc ghi nội dung "Chưa khớp" (giữ DELIVERED).
 * COMPLETED chỉ đóng nhu cầu này; cụ vẫn có thể có nhu cầu mới (R09).
 */
public class NeedProgressActivity extends AppCompatActivity {

    private static final int STEP_DONE = 0;
    private static final int STEP_CURRENT = 1;
    private static final int STEP_PENDING = 2;

    private enum Action { NONE, REPORT, RECONCILE }

    private CoordinationViewModel viewModel;
    private CoordinationViewModel.Ui current;
    private Action action = Action.NONE;
    private String needId;

    private MaterialToolbar toolbar;
    private View header, progressBusy, scrollContent, cardReport, layoutInput, layoutActions, spaceActions;
    private TextView tvHeaderStatus, tvHeaderMeta, tvNeedTitle, tvClosedNote, tvReport, tvReportBy,
            tvReconcileExisting, tvInputLabel, tvWarning;
    private ViewGroup layoutSteps;
    private TextInputLayout tilInput;
    private TextInputEditText etInput;
    private MaterialButton btnPrimary, btnSecondary;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_need_progress);
        bindViews();

        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        setCaseCode(getIntent().getStringExtra(Constants.EXTRA_CASE_CODE));
        toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(CoordinationViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getDone().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg == null) return;
            etInput.setText(""); // nội dung đã được lưu
            Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.start(needId, getIntent().getStringExtra(Constants.EXTRA_ASSIGNMENT_ID));
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        header = findViewById(R.id.layoutCoordHeader);
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        tvHeaderMeta = findViewById(R.id.tvHeaderMeta);
        progressBusy = findViewById(R.id.progressBusy);
        scrollContent = findViewById(R.id.scrollContent);
        tvNeedTitle = findViewById(R.id.tvNeedTitle);
        layoutSteps = findViewById(R.id.layoutSteps);
        tvClosedNote = findViewById(R.id.tvClosedNote);
        cardReport = findViewById(R.id.cardReport);
        tvReport = findViewById(R.id.tvReport);
        tvReportBy = findViewById(R.id.tvReportBy);
        tvReconcileExisting = findViewById(R.id.tvReconcileExisting);
        layoutInput = findViewById(R.id.layoutProgressInput);
        tvInputLabel = findViewById(R.id.tvProgressInputLabel);
        tilInput = findViewById(R.id.tilProgressInput);
        etInput = findViewById(R.id.etProgressInput);
        tvWarning = findViewById(R.id.tvProgressWarning);
        layoutActions = findViewById(R.id.layoutActions);
        spaceActions = findViewById(R.id.spaceActions);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnSecondary = findViewById(R.id.btnSecondary);
        stateView = new StateView(findViewById(R.id.frameContent));
    }

    private void setCaseCode(@Nullable String code) {
        toolbar.setTitle(TextUtils.isEmpty(code) ? getString(R.string.progress_title)
                : getString(R.string.progress_title_with_code, code));
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(CoordinationViewModel.Ui s) {
        current = s;
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        NeedSummary n = s.need;
        if (n == null) {
            header.setVisibility(View.GONE);
            scrollContent.setVisibility(View.GONE);
            layoutActions.setVisibility(View.GONE);
            if (s.loading) stateView.showLoading();
            else stateView.showError(getString(R.string.state_error_title),
                    s.error != null ? s.error : getString(R.string.coord_need_unavailable),
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        stateView.hide();
        scrollContent.setVisibility(View.VISIBLE);
        setCaseCode(n.getCaseCode());

        header.setVisibility(View.VISIBLE);
        NeedStatus.bindChip(tvHeaderStatus, n.getStatus());
        Date updated = n.getUpdatedAt();
        tvHeaderMeta.setText(updated != null
                ? getString(R.string.progress_updated, DateTimeUtils.formatShortStamp(updated.getTime())) : null);
        tvNeedTitle.setText(n.getTitle());

        renderSteps(n);
        renderReport(n);
        renderAction(s);
    }

    private void renderSteps(NeedSummary n) {
        String st = n.getStatus();
        int reached = reachedStep(st); // số bước đã xong
        boolean closed = Constants.STATUS_CANCELLED.equals(st) || Constants.STATUS_OPEN.equals(st)
                || Constants.STATUS_REVIEW.equals(st);
        String code = n.getCaseCode() != null ? n.getCaseCode() : "";

        String[] titles = {
                getString(R.string.progress_step_accepted),
                getString(R.string.progress_step_scheduled),
                getString(R.string.progress_step_delivered),
                getString(R.string.progress_step_reconcile),
                getString(R.string.progress_step_completed)
        };
        String[] details = new String[5];

        // 1 · Đã tiếp nhận
        details[0] = joinDot(n.getAssignedOrgName(), dayMonth(n.getAcceptedAt()));
        // 2 · Đã hẹn lịch
        details[1] = reached >= 2
                ? getString(R.string.progress_scheduled_detail,
                        DateTimeUtils.formatSlot(n.getScheduledAtMillis(), n.getScheduledEndAtMillis()))
                : getString(R.string.progress_scheduled_waiting);
        // 3 · Tổ chức báo đã hỗ trợ
        details[2] = reached >= 3 && n.getDeliveredAt() != null
                ? DateTimeUtils.formatShortStamp(n.getDeliveredAt().getTime())
                : getString(R.string.progress_delivered_waiting);
        // 4 · Đầu mối đối soát
        if (reached >= 4) {
            details[3] = n.getCompletedAt() != null
                    ? getString(R.string.progress_reconcile_done, DateTimeUtils.formatShortStamp(n.getCompletedAt().getTime()))
                    : getString(R.string.progress_reconcile_done_plain);
        } else if (!TextUtils.isEmpty(n.getReconcileNote())) {
            details[3] = getString(R.string.progress_reconcile_mismatch);
        } else {
            details[3] = getString(R.string.progress_reconcile_waiting);
        }
        // 5 · Hoàn thành
        details[4] = getString(R.string.progress_completed_detail, code);

        layoutSteps.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < 5; i++) {
            int state;
            if (closed) state = i < reached ? STEP_DONE : STEP_PENDING;
            else if (i < reached) state = STEP_DONE;
            else if (i == reached) state = STEP_CURRENT;
            else state = STEP_PENDING;
            View row = inflater.inflate(R.layout.item_progress_step, layoutSteps, false);
            bindStep(row, titles[i], details[i], state, i == 4, i < reached);
            layoutSteps.addView(row);
        }

        if (Constants.STATUS_CANCELLED.equals(st)) {
            tvClosedNote.setVisibility(View.VISIBLE);
            tvClosedNote.setText(R.string.progress_closed_cancelled);
        } else if (closed || !isCurrentThread(n)) {
            tvClosedNote.setVisibility(View.VISIBLE);
            tvClosedNote.setText(R.string.progress_closed_returned);
        } else {
            tvClosedNote.setVisibility(View.GONE);
        }
    }

    /** Số bước đã hoàn tất theo trạng thái nhu cầu. */
    static int reachedStep(String status) {
        if (status == null) return 0;
        switch (status) {
            case Constants.STATUS_ACCEPTED: return 1;
            case Constants.STATUS_SCHEDULED: return 2;
            case Constants.STATUS_DELIVERED: return 3;
            case Constants.STATUS_COMPLETED: return 5;
            default: return 0;
        }
    }

    private void bindStep(View row, String title, String detail, int state, boolean last, boolean lineDone) {
        ImageView iv = row.findViewById(R.id.ivStep);
        TextView tvTitle = row.findViewById(R.id.tvStepTitle);
        TextView tvDetail = row.findViewById(R.id.tvStepDetail);
        View line = row.findViewById(R.id.viewStepLine);

        @DrawableRes int bg;
        int titleColor;
        String stateLabel;
        if (state == STEP_DONE) {
            bg = R.drawable.bg_step_done;
            iv.setImageResource(R.drawable.ic_check);
            iv.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.on_primary)));
            titleColor = R.color.text_primary;
            stateLabel = getString(R.string.progress_state_done);
        } else if (state == STEP_CURRENT) {
            bg = R.drawable.bg_step_current;
            iv.setImageDrawable(null);
            titleColor = R.color.text_primary;
            stateLabel = getString(R.string.progress_state_current);
        } else {
            bg = R.drawable.bg_step_pending;
            iv.setImageDrawable(null);
            titleColor = R.color.text_secondary;
            stateLabel = getString(R.string.progress_state_pending);
        }
        iv.setBackgroundResource(bg);
        tvTitle.setText(title);
        tvTitle.setTextColor(ContextCompat.getColor(this, titleColor));
        tvTitle.setTypeface(null, state == STEP_PENDING ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        tvDetail.setText(detail);
        tvDetail.setVisibility(TextUtils.isEmpty(detail) ? View.GONE : View.VISIBLE);
        line.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        line.setBackgroundColor(ContextCompat.getColor(this, lineDone ? R.color.primary : R.color.divider));
        row.setContentDescription(stateLabel + ". " + title + (TextUtils.isEmpty(detail) ? "" : ". " + detail));
    }

    private void renderReport(NeedSummary n) {
        boolean hasReport = !TextUtils.isEmpty(n.getDeliveryReport());
        cardReport.setVisibility(hasReport ? View.VISIBLE : View.GONE);
        if (hasReport) {
            tvReport.setText(n.getDeliveryReport());
            String when = n.getDeliveredAt() != null ? DateTimeUtils.formatShortStamp(n.getDeliveredAt().getTime()) : null;
            String by = joinDot(n.getDeliveredByName(), when);
            tvReportBy.setText(by);
            tvReportBy.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
        }
        boolean mismatch = Constants.STATUS_DELIVERED.equals(n.getStatus()) && !TextUtils.isEmpty(n.getReconcileNote());
        tvReconcileExisting.setVisibility(mismatch ? View.VISIBLE : View.GONE);
        if (mismatch) tvReconcileExisting.setText(getString(R.string.progress_reconcile_existing, n.getReconcileNote()));
    }

    private void renderAction(CoordinationViewModel.Ui s) {
        NeedSummary n = s.need;
        Membership me = s.membership;
        boolean currentThread = isCurrentThread(n);
        action = Action.NONE;
        if (me != null && currentThread) {
            if (Constants.STATUS_SCHEDULED.equals(n.getStatus()) && me.belongsToAssignedOrgOf(n)) {
                action = Action.REPORT;
            } else if (Constants.STATUS_DELIVERED.equals(n.getStatus()) && me.isRecorder() && me.belongsToRecorderOf(n)) {
                action = Action.RECONCILE;
            }
        }

        layoutInput.setVisibility(action != Action.NONE ? View.VISIBLE : View.GONE);
        tvWarning.setVisibility(action == Action.RECONCILE ? View.VISIBLE : View.GONE);
        etInput.setEnabled(!s.busy);

        String primary = null, secondary = null;
        View.OnClickListener onPrimary = null, onSecondary = null;
        if (action == Action.REPORT) {
            tvInputLabel.setText(R.string.progress_report_input_label);
            tilInput.setPlaceholderText(getString(R.string.progress_report_input_hint));
            primary = getString(R.string.progress_action_delivered);
            onPrimary = v -> submitReport();
        } else if (action == Action.RECONCILE) {
            tvInputLabel.setText(R.string.progress_reconcile_input_label);
            tilInput.setPlaceholderText(getString(R.string.progress_reconcile_input_hint));
            secondary = getString(R.string.progress_action_mismatch);
            onSecondary = v -> submitMismatch();
            primary = getString(R.string.progress_action_complete);
            onPrimary = v -> askComplete();
        } else if (Constants.STATUS_ACCEPTED.equals(n.getStatus()) && currentThread) {
            primary = getString(R.string.progress_action_open_schedule);
            onPrimary = v -> openSchedule();
        }

        layoutActions.setVisibility(primary != null || secondary != null ? View.VISIBLE : View.GONE);
        btnPrimary.setVisibility(primary != null ? View.VISIBLE : View.GONE);
        btnSecondary.setVisibility(secondary != null ? View.VISIBLE : View.GONE);
        spaceActions.setVisibility(primary != null && secondary != null ? View.VISIBLE : View.GONE);
        btnPrimary.setText(primary);
        btnSecondary.setText(secondary);
        btnPrimary.setOnClickListener(onPrimary);
        btnSecondary.setOnClickListener(onSecondary);
        btnPrimary.setEnabled(!s.busy);
        btnSecondary.setEnabled(!s.busy);
    }

    private boolean isCurrentThread(NeedSummary n) {
        String a = viewModel.getAssignmentId();
        return a != null && a.equals(n.getCurrentAssignmentId());
    }

    // =====================================================================
    // Thao tác
    // =====================================================================

    private void submitReport() {
        String text = inputText();
        if (text.length() < 5) {
            tilInput.setError(getString(R.string.progress_error_report));
            return;
        }
        tilInput.setError(null);
        if (!requireOnline()) return;
        viewModel.reportDelivered(text);
    }

    private void submitMismatch() {
        String text = inputText();
        if (text.isEmpty()) {
            tilInput.setError(getString(R.string.progress_error_mismatch));
            return;
        }
        tilInput.setError(null);
        if (!requireOnline()) return;
        viewModel.noteReconcile(text);
    }

    /** Hoàn thành không thể hoàn tác nên hỏi lại; nhắc nếu ô "chưa khớp" đang có nội dung. */
    private void askComplete() {
        if (!requireOnline()) return;
        boolean typing = !inputText().isEmpty();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.progress_complete_title)
                .setMessage(typing ? R.string.progress_complete_message_typing : R.string.progress_complete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.progress_action_complete, (d, w) -> viewModel.complete())
                .show();
    }

    private void openSchedule() {
        startActivity(new Intent(this, ScheduleAgreementActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId()));
    }

    private String inputText() {
        Editable e = etInput.getText();
        return e != null ? e.toString().trim() : "";
    }

    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(scrollContent, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    @Nullable
    private static String dayMonth(@Nullable Date d) {
        return d != null ? DateTimeUtils.formatDayMonth(d.getTime()) : null;
    }

    private static String joinDot(@Nullable String a, @Nullable String b) {
        if (TextUtils.isEmpty(a)) return b != null ? b : "";
        if (TextUtils.isEmpty(b)) return a;
        return a + " · " + b;
    }
}
