package com.seniortribe.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.data.CoordinationRepository;
import com.seniortribe.app.dialogs.TextInputDialog;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.CoordinationViewModel;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * E02 · Đổi lịch sau khi đã chốt (nhu cầu đang SCHEDULED).
 *
 * Một màn hình cho cả hai bên phối hợp:
 *  - Gửi yêu cầu đổi lịch: ngày, khung giờ, hình thức và lý do. Đầu mối gửi thì phải hỏi ý kiến
 *    người cao tuổi trước (tick + ghi cách hỏi), vì người cao tuổi không dùng app.
 *  - Bên còn lại xác nhận hoặc từ chối kèm lý do. Đầu mối xác nhận yêu cầu của tổ chức thì cũng
 *    phải ghi nhận đồng ý của cụ.
 *  - Bên gửi rút lại yêu cầu đang chờ, hoặc gửi yêu cầu khác thay thế.
 *  - Lịch sử: lịch đã chốt, các yêu cầu đổi và kết quả, không bị xóa.
 * Lịch cũ giữ nguyên tới khi xác nhận; xác nhận chạy trong transaction và Security Rules kiểm tra
 * lại bên nào được xác nhận, đồng ý của cụ và việc lịch cũ chuyển RESCHEDULED (R06).
 */
public class RescheduleActivity extends AppCompatActivity {

    private static final String KEY_DECLINE = "resched_decline";

    private enum Mode { CLOSED, PROPOSE, MINE_PENDING, RESPOND }

    private CoordinationViewModel viewModel;
    private CoordinationViewModel.Ui current;
    private Mode mode = Mode.CLOSED;
    private String side;

    private MaterialToolbar toolbar;
    private View header, progressBusy, scrollContent, layoutActions, spaceActions;
    private TextView tvHeaderStatus, tvHeaderMeta;
    private TextView tvCurrentSlot, tvCurrentFormat, tvCurrentMeta, tvClosedNote;
    private View layoutPending, layoutPendingConsent;
    private TextView tvPendingSlot, tvPendingFormat, tvPendingReason, tvPendingConsent, tvPendingBy, tvPendingNote;
    private MaterialCheckBox cbPendingAgreed;
    private TextInputLayout tilPendingConsent;
    private TextInputEditText etPendingConsent;
    private View layoutForm, layoutFormConsent;
    private TextView tvFormTitle, tvNewDate, tvNewTime, tvNewTimeError, tvFormNote;
    private TextInputLayout tilNewFormat, tilReason, tilFormConsent;
    private TextInputEditText etNewFormat, etReason, etFormConsent;
    private MaterialCheckBox cbFormAgreed;
    private ViewGroup layoutHistory;
    private TextView tvHistoryEmpty;
    private MaterialButton btnPrimary, btnSecondary;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reschedule);
        bindViews();

        String needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        String code = getIntent().getStringExtra(Constants.EXTRA_CASE_CODE);
        if (!TextUtils.isEmpty(code)) toolbar.setTitle(getString(R.string.resched_title_with_code, code));
        toolbar.setNavigationOnClickListener(v -> finish());

        tvNewDate.setOnClickListener(v -> pickDate());
        tvNewTime.setOnClickListener(v -> pickStartTime());
        cbPendingAgreed.setOnCheckedChangeListener((b, c) -> updatePrimaryEnabled());
        etPendingConsent.addTextChangedListener(new ScheduleAgreementActivity.SimpleWatcher(this::updatePrimaryEnabled));

        // Đăng ký trước khi hộp thoại được khôi phục sau xoay màn hình
        getSupportFragmentManager().setFragmentResultListener(KEY_DECLINE, this, (k, r) ->
                viewModel.declineReschedule(r.getString(TextInputDialog.RESULT_TAG), r.getString(TextInputDialog.RESULT_TEXT)));

        viewModel = new ViewModelProvider(this).get(CoordinationViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getDone().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getRescheduleSent().observe(this, e -> {
            if (e.getIfNotHandled() == null) return;
            etReason.setText("");
            etFormConsent.setText("");
            cbFormAgreed.setChecked(false);
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
        tvCurrentSlot = findViewById(R.id.tvCurrentSlot);
        tvCurrentFormat = findViewById(R.id.tvCurrentFormat);
        tvCurrentMeta = findViewById(R.id.tvCurrentMeta);
        tvClosedNote = findViewById(R.id.tvClosedNote);
        layoutPending = findViewById(R.id.layoutPending);
        tvPendingSlot = findViewById(R.id.tvPendingSlot);
        tvPendingFormat = findViewById(R.id.tvPendingFormat);
        tvPendingReason = findViewById(R.id.tvPendingReason);
        tvPendingConsent = findViewById(R.id.tvPendingConsent);
        tvPendingBy = findViewById(R.id.tvPendingBy);
        tvPendingNote = findViewById(R.id.tvPendingNote);
        layoutPendingConsent = findViewById(R.id.layoutPendingConsent);
        cbPendingAgreed = findViewById(R.id.cbPendingAgreed);
        tilPendingConsent = findViewById(R.id.tilPendingConsent);
        etPendingConsent = findViewById(R.id.etPendingConsent);
        layoutForm = findViewById(R.id.layoutForm);
        tvFormTitle = findViewById(R.id.tvFormTitle);
        tvNewDate = findViewById(R.id.tvNewDate);
        tvNewTime = findViewById(R.id.tvNewTime);
        tvNewTimeError = findViewById(R.id.tvNewTimeError);
        tilNewFormat = findViewById(R.id.tilNewFormat);
        etNewFormat = findViewById(R.id.etNewFormat);
        tilReason = findViewById(R.id.tilReason);
        etReason = findViewById(R.id.etReason);
        layoutFormConsent = findViewById(R.id.layoutFormConsent);
        cbFormAgreed = findViewById(R.id.cbFormAgreed);
        tilFormConsent = findViewById(R.id.tilFormConsent);
        etFormConsent = findViewById(R.id.etFormConsent);
        tvFormNote = findViewById(R.id.tvFormNote);
        layoutHistory = findViewById(R.id.layoutHistory);
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty);
        layoutActions = findViewById(R.id.layoutActions);
        spaceActions = findViewById(R.id.spaceActions);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnSecondary = findViewById(R.id.btnSecondary);
        stateView = new StateView(findViewById(R.id.frameContent));
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
        if (!TextUtils.isEmpty(n.getCaseCode())) toolbar.setTitle(getString(R.string.resched_title_with_code, n.getCaseCode()));
        header.setVisibility(View.VISIBLE);
        NeedStatus.bindChip(tvHeaderStatus, n.getStatus());
        tvHeaderMeta.setText(getString(R.string.agree_header_assignee, nonNull(n.getAssigneeName())));

        side = s.membership != null ? CoordinationRepository.sideOf(s.membership, n) : null;
        ScheduleSession pending = s.pendingReschedule();
        mode = resolveMode(s, pending);

        renderCurrent(n);
        renderPending(pending);
        renderForm();
        renderHistory(s.scheduleHistory());
        renderActions(pending);
    }

    private Mode resolveMode(CoordinationViewModel.Ui s, @Nullable ScheduleSession pending) {
        NeedSummary n = s.need;
        boolean currentThread = viewModel.getAssignmentId() != null
                && viewModel.getAssignmentId().equals(n.getCurrentAssignmentId());
        if (!Constants.STATUS_SCHEDULED.equals(n.getStatus()) || !currentThread || side == null) return Mode.CLOSED;
        if (pending == null) return Mode.PROPOSE;
        return side.equals(pending.side()) ? Mode.MINE_PENDING : Mode.RESPOND;
    }

    private void renderCurrent(NeedSummary n) {
        if (n.getScheduledAtMillis() > 0) {
            tvCurrentSlot.setText(DateTimeUtils.formatSlot(n.getScheduledAtMillis(), n.getScheduledEndAtMillis()));
            tvCurrentFormat.setText(n.getScheduledFormat());
            tvCurrentFormat.setVisibility(TextUtils.isEmpty(n.getScheduledFormat()) ? View.GONE : View.VISIBLE);
        } else {
            tvCurrentSlot.setText(R.string.resched_no_current);
            tvCurrentFormat.setVisibility(View.GONE);
        }
        long count = n.getRescheduleCount();
        tvCurrentMeta.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) tvCurrentMeta.setText(getString(R.string.resched_count, count));

        if (mode == Mode.CLOSED) {
            tvClosedNote.setVisibility(View.VISIBLE);
            @StringRes int note;
            if (side == null && Constants.STATUS_SCHEDULED.equals(n.getStatus())) note = R.string.resched_closed_not_party;
            else if (Constants.STATUS_SCHEDULED.equals(n.getStatus())) note = R.string.resched_closed_thread;
            else note = R.string.resched_closed_status;
            tvClosedNote.setText(note);
        } else {
            tvClosedNote.setVisibility(View.GONE);
        }
    }

    private void renderPending(@Nullable ScheduleSession p) {
        boolean show = p != null && mode != Mode.CLOSED;
        layoutPending.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        tvPendingSlot.setText(DateTimeUtils.formatSlot(p.getStartAtMillis(), p.getEndAtMillis()));
        tvPendingFormat.setText(p.getFormat());
        tvPendingReason.setText(getString(R.string.resched_reason_value, nonNull(p.getReason())));
        boolean byRecorder = ScheduleSession.SIDE_RECORDER.equals(p.side());
        tvPendingConsent.setVisibility(byRecorder && !TextUtils.isEmpty(p.getConsentMethod()) ? View.VISIBLE : View.GONE);
        if (byRecorder) tvPendingConsent.setText(getString(R.string.resched_consent_value, nonNull(p.getConsentMethod())));
        tvPendingBy.setText(getString(byRecorder ? R.string.resched_by_recorder : R.string.resched_by_org,
                nonNull(p.getProposedByName()), DateTimeUtils.formatDateTime(p.getCreatedAtMillis())));

        // Đầu mối trả lời yêu cầu của tổ chức thì phải ghi nhận đồng ý của cụ
        boolean needConsent = mode == Mode.RESPOND && ScheduleSession.SIDE_RECORDER.equals(side);
        layoutPendingConsent.setVisibility(needConsent ? View.VISIBLE : View.GONE);
        cbPendingAgreed.setEnabled(needConsent && !current.busy);
        etPendingConsent.setEnabled(needConsent && !current.busy);

        int note;
        if (mode == Mode.MINE_PENDING) note = R.string.resched_note_mine;
        else if (needConsent) note = R.string.resched_note_ask_elder;
        else note = R.string.resched_note_org_respond;
        tvPendingNote.setText(note);
    }

    private void renderForm() {
        boolean show = mode == Mode.PROPOSE || mode == Mode.MINE_PENDING;
        layoutForm.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        boolean recorder = ScheduleSession.SIDE_RECORDER.equals(side);
        tvFormTitle.setText(mode == Mode.MINE_PENDING ? R.string.resched_section_form_replace : R.string.resched_section_form);
        layoutFormConsent.setVisibility(recorder ? View.VISIBLE : View.GONE);
        tvFormNote.setText(recorder ? R.string.resched_form_note_recorder : R.string.resched_form_note_org);

        CoordinationViewModel.RescheduleDraft d = viewModel.getRescheduleDraft();
        if (!d.prefilled && current.need != null) {
            d.prefilled = true;
            Editable f = etNewFormat.getText();
            if ((f == null || f.length() == 0) && current.need.getScheduledFormat() != null) {
                etNewFormat.setText(current.need.getScheduledFormat());
            }
        }
        tvNewDate.setText(d.dateSet ? DateTimeUtils.formatWeekdayFullDate(d.startAt) : null);
        tvNewTime.setText(d.endAt > 0 ? DateTimeUtils.formatTimeRange(d.startAt, d.endAt) : null);
        boolean enabled = !current.busy;
        tvNewDate.setEnabled(enabled);
        tvNewTime.setEnabled(enabled);
        etNewFormat.setEnabled(enabled);
        etReason.setEnabled(enabled);
        cbFormAgreed.setEnabled(enabled);
        etFormConsent.setEnabled(enabled);
    }

    private void renderHistory(List<ScheduleSession> history) {
        layoutHistory.removeAllViews();
        tvHistoryEmpty.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        String currentId = current.need != null ? current.need.getScheduleId() : null;
        for (ScheduleSession s : history) {
            View row = inflater.inflate(R.layout.item_reschedule_history, layoutHistory, false);
            TextView chip = row.findViewById(R.id.tvHistoryStatus);
            TextView slot = row.findViewById(R.id.tvHistorySlot);
            TextView detail = row.findViewById(R.id.tvHistoryDetail);
            TextView by = row.findViewById(R.id.tvHistoryBy);

            int label, fg, bg;
            String status = s.getStatus();
            boolean isCurrent = s.getScheduleId() != null && s.getScheduleId().equals(currentId);
            if (ScheduleSession.CONFIRMED.equals(status) && isCurrent) {
                label = R.string.resched_hist_current; fg = R.color.status_completed; bg = R.color.status_completed_bg;
            } else if (ScheduleSession.CONFIRMED.equals(status)) {
                label = R.string.resched_hist_confirmed; fg = R.color.status_completed; bg = R.color.status_completed_bg;
            } else if (ScheduleSession.RESCHEDULED.equals(status)) {
                label = R.string.resched_hist_replaced; fg = R.color.status_cancelled; bg = R.color.status_cancelled_bg;
            } else if (ScheduleSession.PROPOSED.equals(status)) {
                label = R.string.resched_hist_pending; fg = R.color.status_scheduled; bg = R.color.status_scheduled_bg;
            } else if (ScheduleSession.DECLINED.equals(status)) {
                label = R.string.resched_hist_declined; fg = R.color.warning_text; bg = R.color.warning_bg;
            } else {
                label = R.string.resched_hist_withdrawn; fg = R.color.status_cancelled; bg = R.color.status_cancelled_bg;
            }
            chip.setText(label);
            chip.setTextColor(ContextCompat.getColor(this, fg));
            chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(chip,
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, fg)));

            String text = DateTimeUtils.formatSlot(s.getStartAtMillis(), s.getEndAtMillis());
            if (!TextUtils.isEmpty(s.getFormat())) text += " · " + s.getFormat();
            slot.setText(text);

            StringBuilder more = new StringBuilder();
            if (s.isReschedule() && !TextUtils.isEmpty(s.getReason())) {
                more.append(getString(R.string.resched_reason_value, s.getReason()));
            }
            if (ScheduleSession.DECLINED.equals(status) && !TextUtils.isEmpty(s.getDeclineReason())) {
                if (more.length() > 0) more.append('\n');
                more.append(getString(R.string.resched_declined_value, s.getDeclineReason()));
            }
            if (!TextUtils.isEmpty(s.getConsentMethod())) {
                if (more.length() > 0) more.append('\n');
                more.append(getString(R.string.resched_consent_value, s.getConsentMethod()));
            }
            detail.setVisibility(more.length() > 0 ? View.VISIBLE : View.GONE);
            detail.setText(more);

            String proposer = nonNull(s.getProposedByName());
            String decider = s.getDecidedByName();
            String when = s.getDecidedAtMillis() > 0 ? DateTimeUtils.formatDateTime(s.getDecidedAtMillis())
                    : DateTimeUtils.formatDateTime(s.getCreatedAtMillis());
            by.setText(TextUtils.isEmpty(decider)
                    ? getString(R.string.resched_hist_by_one, proposer, when)
                    : getString(R.string.resched_hist_by_two, proposer, decider, when));
            row.setContentDescription(getString(label) + ". " + text + (more.length() > 0 ? ". " + more : ""));
            layoutHistory.addView(row);
        }
    }

    private void renderActions(@Nullable ScheduleSession pending) {
        String primary = null, secondary = null;
        View.OnClickListener onPrimary = null, onSecondary = null;
        switch (mode) {
            case PROPOSE:
                primary = getString(R.string.resched_action_send);
                onPrimary = v -> submitProposal();
                break;
            case MINE_PENDING:
                secondary = getString(R.string.resched_action_withdraw);
                onSecondary = v -> askWithdraw(pending);
                primary = getString(R.string.resched_action_send_replace);
                onPrimary = v -> submitProposal();
                break;
            case RESPOND:
                secondary = getString(R.string.resched_action_decline);
                onSecondary = v -> askDecline(pending);
                primary = getString(R.string.resched_action_confirm);
                onPrimary = v -> confirm(pending);
                break;
            default:
                break;
        }
        layoutActions.setVisibility(primary != null || secondary != null ? View.VISIBLE : View.GONE);
        btnPrimary.setVisibility(primary != null ? View.VISIBLE : View.GONE);
        btnSecondary.setVisibility(secondary != null ? View.VISIBLE : View.GONE);
        spaceActions.setVisibility(primary != null && secondary != null ? View.VISIBLE : View.GONE);
        btnPrimary.setText(primary);
        btnSecondary.setText(secondary);
        btnPrimary.setOnClickListener(onPrimary);
        btnSecondary.setOnClickListener(onSecondary);
        btnSecondary.setEnabled(!current.busy);
        updatePrimaryEnabled();
    }

    /** "Đồng ý đổi lịch" của đầu mối chỉ bấm được khi đã tick và ghi cách hỏi ý kiến cụ. */
    private void updatePrimaryEnabled() {
        if (current == null) return;
        boolean enabled = !current.busy;
        if (mode == Mode.RESPOND && ScheduleSession.SIDE_RECORDER.equals(side)) {
            enabled = enabled && cbPendingAgreed.isChecked() && text(etPendingConsent).length() > 0;
        }
        btnPrimary.setEnabled(enabled);
    }

    // =====================================================================
    // Chọn ngày giờ
    // =====================================================================

    private void pickDate() {
        if (mode != Mode.PROPOSE && mode != Mode.MINE_PENDING) return;
        CoordinationViewModel.RescheduleDraft d = viewModel.getRescheduleDraft();
        Calendar c = Calendar.getInstance();
        if (d.dateSet) c.setTimeInMillis(d.startAt);
        else c.add(Calendar.DAY_OF_MONTH, 1);
        DatePickerDialog dialog = new DatePickerDialog(this, (dp, y, m, day) -> {
            Calendar start = Calendar.getInstance();
            if (d.endAt > 0) start.setTimeInMillis(d.startAt);
            else {
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
            }
            long length = d.endAt > 0 ? d.endAt - d.startAt : 0;
            start.set(y, m, day);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            d.startAt = start.getTimeInMillis();
            d.endAt = length > 0 ? d.startAt + length : 0;
            d.dateSet = true;
            tvNewTimeError.setVisibility(View.GONE);
            renderForm();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void pickStartTime() {
        if (mode != Mode.PROPOSE && mode != Mode.MINE_PENDING) return;
        CoordinationViewModel.RescheduleDraft d = viewModel.getRescheduleDraft();
        if (!d.dateSet) {
            showTimeError(getString(R.string.agree_error_date_first));
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(d.startAt);
        int hour = d.endAt > 0 ? c.get(Calendar.HOUR_OF_DAY) : 9;
        int minute = d.endAt > 0 ? c.get(Calendar.MINUTE) : 0;
        TimePickerDialog dialog = new TimePickerDialog(this, (tp, h, min) -> {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(d.startAt);
            start.set(Calendar.HOUR_OF_DAY, h);
            start.set(Calendar.MINUTE, min);
            pickEndTime(start.getTimeInMillis());
        }, hour, minute, true);
        dialog.setTitle(R.string.agree_pick_start);
        dialog.show();
    }

    private void pickEndTime(long startAt) {
        CoordinationViewModel.RescheduleDraft d = viewModel.getRescheduleDraft();
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(d.endAt > startAt ? d.endAt : startAt + 2L * 60 * 60 * 1000);
        TimePickerDialog dialog = new TimePickerDialog(this, (tp, h, min) -> {
            Calendar e = Calendar.getInstance();
            e.setTimeInMillis(startAt);
            e.set(Calendar.HOUR_OF_DAY, h);
            e.set(Calendar.MINUTE, min);
            if (e.getTimeInMillis() <= startAt) {
                showTimeError(getString(R.string.agree_error_end_before_start));
                return;
            }
            d.startAt = startAt;
            d.endAt = e.getTimeInMillis();
            tvNewTimeError.setVisibility(View.GONE);
            renderForm();
        }, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), true);
        dialog.setTitle(R.string.agree_pick_end);
        dialog.show();
    }

    private void showTimeError(String text) {
        tvNewTimeError.setText(text);
        tvNewTimeError.setVisibility(View.VISIBLE);
    }

    // =====================================================================
    // Thao tác
    // =====================================================================

    private void submitProposal() {
        CoordinationViewModel.RescheduleDraft d = viewModel.getRescheduleDraft();
        NeedSummary n = current != null ? current.need : null;
        boolean ok = true;
        if (!d.dateSet || d.endAt <= 0) {
            showTimeError(getString(R.string.agree_error_slot_missing));
            ok = false;
        } else if (d.startAt <= System.currentTimeMillis()) {
            showTimeError(getString(R.string.agree_error_past));
            ok = false;
        } else if (d.endAt - d.startAt > CoordinationRepository.MAX_SLOT_MILLIS) {
            showTimeError(getString(R.string.agree_error_too_long));
            ok = false;
        } else if (n != null && d.startAt == n.getScheduledAtMillis() && d.endAt == n.getScheduledEndAtMillis()) {
            showTimeError(getString(R.string.resched_error_same_slot));
            ok = false;
        }
        String format = text(etNewFormat);
        if (format.length() < 2) {
            tilNewFormat.setError(getString(R.string.agree_error_format));
            ok = false;
        } else {
            tilNewFormat.setError(null);
        }
        String reason = text(etReason);
        if (reason.isEmpty()) {
            tilReason.setError(getString(R.string.resched_error_reason));
            ok = false;
        } else {
            tilReason.setError(null);
        }
        String consent = null;
        if (ScheduleSession.SIDE_RECORDER.equals(side)) {
            consent = text(etFormConsent);
            if (!cbFormAgreed.isChecked()) {
                Snackbar.make(scrollContent, R.string.resched_error_consent_unchecked, Snackbar.LENGTH_LONG).show();
                ok = false;
            }
            if (consent.isEmpty()) {
                tilFormConsent.setError(getString(R.string.agree_error_consent_method));
                ok = false;
            } else {
                tilFormConsent.setError(null);
            }
        }
        if (!ok || !requireOnline()) return;
        viewModel.proposeReschedule(new Date(d.startAt), new Date(d.endAt), format, reason, consent);
    }

    private void confirm(@Nullable ScheduleSession pending) {
        if (pending == null) return;
        boolean recorder = ScheduleSession.SIDE_RECORDER.equals(side);
        String method = null;
        if (recorder) {
            method = text(etPendingConsent);
            if (!cbPendingAgreed.isChecked()) {
                Snackbar.make(scrollContent, R.string.resched_error_consent_unchecked, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (method.isEmpty()) {
                tilPendingConsent.setError(getString(R.string.agree_error_consent_method));
                return;
            }
            tilPendingConsent.setError(null);
        }
        if (!requireOnline()) return;
        viewModel.confirmReschedule(pending.getScheduleId(), !recorder || cbPendingAgreed.isChecked(), method);
    }

    private void askDecline(@Nullable ScheduleSession pending) {
        if (pending == null || !requireOnline()) return;
        TextInputDialog.newInstance(KEY_DECLINE, getString(R.string.resched_decline_title),
                getString(R.string.resched_decline_message), getString(R.string.resched_decline_hint),
                getString(R.string.resched_decline_send), 1, pending.getScheduleId())
                .show(getSupportFragmentManager(), KEY_DECLINE);
    }

    private void askWithdraw(@Nullable ScheduleSession pending) {
        if (pending == null || !requireOnline()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.resched_withdraw_title)
                .setMessage(R.string.resched_withdraw_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.resched_action_withdraw, (dlg, w) ->
                        viewModel.withdrawReschedule(pending.getScheduleId()))
                .show();
    }

    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(scrollContent, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    private static String text(TextInputEditText et) {
        Editable e = et.getText();
        return e != null ? e.toString().trim() : "";
    }

    private static String nonNull(@Nullable String s) {
        return s != null ? s : "";
    }

    /** Bên của tài khoản hiện tại, để màn hình khác biết có hiện nút "Đổi lịch" hay không. */
    public static boolean canReschedule(@Nullable Membership me, @Nullable NeedSummary n) {
        return me != null && n != null && Constants.STATUS_SCHEDULED.equals(n.getStatus())
                && CoordinationRepository.sideOf(me, n) != null;
    }
}
