package com.seniortribe.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
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

/**
 * F12 · Thống nhất lịch + đồng ý (màn hình riêng theo Figma, thay cho hộp thoại cũ).
 *
 * Một màn hình cho cả hai bên, tùy vai trò và trạng thái:
 *  - Tổ chức phụ trách (ACCEPTED): soạn đề xuất gồm Ngày, Khung giờ, Hình thức → "Gửi đề xuất".
 *  - Đầu mối ghi nhận khi có đề xuất: tick "Cụ đồng ý với lịch hẹn này", ghi cách hỏi ý kiến
 *    → "Chốt lịch" (nhu cầu sang SCHEDULED), hoặc "Đổi giờ khác" kèm lý do.
 *  - Sau khi chốt: xem lại lịch và cách hỏi ý kiến, mở màn hình Tiến độ (F13).
 *
 * Trạng thái form nằm trong ViewModel và các ô nhập có id cố định, nên xoay màn hình (bố cục
 * ngang 2 cột) không mất dữ liệu (T04). Mọi thao tác ghi cần mạng và chạy qua transaction /
 * Security Rules (R04, R06).
 */
public class ScheduleAgreementActivity extends AppCompatActivity {

    private static final String KEY_DECLINE = "agree_decline";

    private enum Mode { LOADING, FORM, RECORDER_REVIEW, WAITING_ORG, WAITING_OTHER, NONE_YET, CONFIRMED, CLOSED }

    private CoordinationViewModel viewModel;
    private CoordinationViewModel.Ui current;
    private Mode mode = Mode.LOADING;
    private String needId;

    private MaterialToolbar toolbar;
    private View header, progressBusy, scrollContent, layoutConsent, layoutActions, spaceActions;
    private TextView tvHeaderStatus, tvHeaderMeta, tvDate, tvTimeRange, tvTimeError, tvFormat, tvProposedBy,
            tvProposalNote, tvDeclinedNote, tvPrivacyNote;
    private TextInputLayout tilFormat, tilConsent;
    private TextInputEditText etFormat, etConsent;
    private MaterialCheckBox cbAgreed;
    private MaterialButton btnPrimary, btnSecondary;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_agreement);
        bindViews();

        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        String assignmentId = getIntent().getStringExtra(Constants.EXTRA_ASSIGNMENT_ID);
        if (needId == null) {
            finish();
            return;
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvDate.setOnClickListener(v -> pickDate());
        tvTimeRange.setOnClickListener(v -> pickStartTime());
        TextWatcher refresh = new SimpleWatcher(() -> updatePrimaryEnabled());
        etConsent.addTextChangedListener(refresh);
        cbAgreed.setOnCheckedChangeListener((b, checked) -> updatePrimaryEnabled());

        // Đăng ký trước khi hộp thoại được khôi phục sau xoay màn hình
        getSupportFragmentManager().setFragmentResultListener(KEY_DECLINE, this, (k, r) ->
                viewModel.declineSchedule(r.getString(TextInputDialog.RESULT_TAG), r.getString(TextInputDialog.RESULT_TEXT)));

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
        viewModel.start(needId, assignmentId);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        header = findViewById(R.id.layoutCoordHeader);
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        tvHeaderMeta = findViewById(R.id.tvHeaderMeta);
        progressBusy = findViewById(R.id.progressBusy);
        scrollContent = findViewById(R.id.scrollContent);
        tvDate = findViewById(R.id.tvDate);
        tvTimeRange = findViewById(R.id.tvTimeRange);
        tvTimeError = findViewById(R.id.tvTimeError);
        tilFormat = findViewById(R.id.tilFormat);
        etFormat = findViewById(R.id.etFormat);
        tvFormat = findViewById(R.id.tvFormat);
        tvProposedBy = findViewById(R.id.tvProposedBy);
        tvProposalNote = findViewById(R.id.tvProposalNote);
        tvDeclinedNote = findViewById(R.id.tvDeclinedNote);
        layoutConsent = findViewById(R.id.layoutConsent);
        cbAgreed = findViewById(R.id.cbElderAgreed);
        tilConsent = findViewById(R.id.tilConsentMethod);
        etConsent = findViewById(R.id.etConsentMethod);
        tvPrivacyNote = findViewById(R.id.tvPrivacyNote);
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

        header.setVisibility(View.VISIBLE);
        NeedStatus.bindChip(tvHeaderStatus, n.getStatus());
        tvHeaderMeta.setText(getString(R.string.agree_header_assignee, nonNull(n.getAssigneeName())));

        mode = resolveMode(s);
        if (mode == Mode.CLOSED) {
            scrollContent.setVisibility(View.GONE);
            layoutActions.setVisibility(View.GONE);
            stateView.showMessage(R.drawable.ic_schedule, getString(R.string.agree_closed_title),
                    getString(R.string.agree_closed_message), null, null);
            return;
        }
        stateView.hide();
        scrollContent.setVisibility(View.VISIBLE);

        renderProposal(s);
        renderConsent(s);
        renderActions(s);
    }

    private Mode resolveMode(CoordinationViewModel.Ui s) {
        NeedSummary n = s.need;
        Membership me = s.membership;
        String status = n.getStatus();
        boolean currentThread = viewModel.getAssignmentId() != null
                && viewModel.getAssignmentId().equals(n.getCurrentAssignmentId());

        if (Constants.STATUS_SCHEDULED.equals(status) || Constants.STATUS_DELIVERED.equals(status)
                || Constants.STATUS_COMPLETED.equals(status)) {
            return currentThread ? Mode.CONFIRMED : Mode.CLOSED;
        }
        if (!Constants.STATUS_ACCEPTED.equals(status) || !currentThread || me == null) return Mode.CLOSED;

        ScheduleSession pending = s.pendingSchedule();
        boolean canPropose = canPropose(me, n);
        CoordinationViewModel.Draft d = viewModel.getDraft();
        if (canPropose && (d.editing || pending == null)) return Mode.FORM;
        if (pending == null) return Mode.NONE_YET;
        if (isRecorder(me, n)) return Mode.RECORDER_REVIEW;
        return canPropose ? Mode.WAITING_ORG : Mode.WAITING_OTHER;
    }

    private void renderProposal(CoordinationViewModel.Ui s) {
        NeedSummary n = s.need;
        ScheduleSession pending = s.pendingSchedule();
        ScheduleSession latest = s.latestSchedule();
        boolean form = mode == Mode.FORM;

        tvDate.setClickable(form);
        tvDate.setFocusable(form);
        tvTimeRange.setClickable(form);
        tvTimeRange.setFocusable(form);
        tilFormat.setVisibility(form ? View.VISIBLE : View.GONE);
        tvFormat.setVisibility(form ? View.GONE : View.VISIBLE);
        if (!form) tvTimeError.setVisibility(View.GONE);

        String note = null;
        String declined = null;
        tvProposedBy.setVisibility(View.GONE);

        switch (mode) {
            case FORM: {
                prefillDraft(pending != null ? pending : latest);
                renderDraft();
                if (pending != null) note = getString(R.string.agree_note_replace);
                break;
            }
            case CONFIRMED: {
                long end = n.getScheduledEndAtMillis();
                showValues(n.getScheduledAtMillis(), end, n.getScheduledFormat());
                note = getString(R.string.agree_note_confirmed);
                break;
            }
            case NONE_YET:
                showValues(0, 0, null);
                note = getString(R.string.agree_note_none_yet);
                break;
            default: // RECORDER_REVIEW, WAITING_ORG, WAITING_OTHER
                showValues(pending.getStartAtMillis(), pending.getEndAtMillis(), pending.getFormat());
                if (pending.getProposedByName() != null && pending.getCreatedAt() != null) {
                    tvProposedBy.setVisibility(View.VISIBLE);
                    tvProposedBy.setText(getString(R.string.agree_proposed_by, pending.getProposedByName(),
                            DateTimeUtils.formatDateTime(pending.getCreatedAtMillis())));
                }
                if (mode == Mode.WAITING_ORG || mode == Mode.WAITING_OTHER) {
                    note = getString(R.string.agree_note_waiting_recorder);
                } else {
                    note = getString(R.string.agree_note_ask_elder);
                }
                break;
        }
        if ((mode == Mode.FORM || mode == Mode.NONE_YET) && latest != null
                && ScheduleSession.DECLINED.equals(latest.getStatus())) {
            declined = getString(R.string.agree_note_declined,
                    DateTimeUtils.formatSlot(latest.getStartAtMillis(), latest.getEndAtMillis()),
                    nonNull(latest.getDeclineReason()));
        }
        tvProposalNote.setVisibility(note != null ? View.VISIBLE : View.GONE);
        tvProposalNote.setText(note);
        tvDeclinedNote.setVisibility(declined != null ? View.VISIBLE : View.GONE);
        tvDeclinedNote.setText(declined);
    }

    /** Hiện giá trị chỉ đọc; 0 / null thì để trống cho hiện gợi ý. */
    private void showValues(long start, long end, @Nullable String format) {
        tvDate.setText(start > 0 ? DateTimeUtils.formatWeekdayFullDate(start) : null);
        tvTimeRange.setText(start > 0 ? DateTimeUtils.formatTimeRange(start, end) : null);
        tvFormat.setText(!TextUtils.isEmpty(format) ? format : getString(R.string.agree_format_missing));
    }

    private void renderConsent(CoordinationViewModel.Ui s) {
        boolean review = mode == Mode.RECORDER_REVIEW;
        boolean confirmed = mode == Mode.CONFIRMED;
        layoutConsent.setVisibility(review || confirmed ? View.VISIBLE : View.GONE);
        cbAgreed.setEnabled(review && !s.busy);
        etConsent.setEnabled(review && !s.busy);
        if (confirmed) {
            // Lịch đã chốt: hiện lại ghi nhận đồng ý, chỉ đọc
            cbAgreed.setChecked(true);
            ScheduleSession c = s.confirmedSchedule();
            String method = c != null ? c.getConsentMethod() : null;
            if (method != null && !method.contentEquals(nonNullEditable(etConsent))) etConsent.setText(method);
            tvPrivacyNote.setText(R.string.agree_privacy_after);
        } else {
            tvPrivacyNote.setText(R.string.agree_privacy_note);
        }
    }

    private void renderActions(CoordinationViewModel.Ui s) {
        String primary = null, secondary = null;
        View.OnClickListener onPrimary = null, onSecondary = null;
        ScheduleSession pending = s.pendingSchedule();

        switch (mode) {
            case FORM:
                primary = getString(R.string.agree_action_send);
                onPrimary = v -> submitProposal();
                if (pending != null) {
                    secondary = getString(R.string.action_cancel);
                    onSecondary = v -> {
                        viewModel.getDraft().editing = false;
                        render(current);
                    };
                }
                break;
            case RECORDER_REVIEW:
                secondary = getString(R.string.agree_action_change);
                onSecondary = v -> askDecline(pending);
                primary = getString(R.string.agree_action_confirm);
                onPrimary = v -> confirm(pending);
                break;
            case WAITING_ORG:
                secondary = getString(R.string.agree_action_propose_again);
                onSecondary = v -> {
                    viewModel.getDraft().editing = true;
                    render(current);
                };
                break;
            case CONFIRMED:
                primary = getString(R.string.agree_action_progress);
                onPrimary = v -> openProgress();
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
        btnSecondary.setEnabled(!s.busy);
        updatePrimaryEnabled();
    }

    /** "Chốt lịch" chỉ bấm được khi đã tick đồng ý và ghi cách hỏi ý kiến. */
    private void updatePrimaryEnabled() {
        if (current == null) return;
        boolean enabled = !current.busy;
        if (mode == Mode.RECORDER_REVIEW) {
            enabled = enabled && cbAgreed.isChecked() && nonNullEditable(etConsent).trim().length() > 0;
        }
        btnPrimary.setEnabled(enabled);
    }

    // =====================================================================
    // Form đề xuất của tổ chức
    // =====================================================================

    /** Điền sẵn từ đề xuất gần nhất (nếu có) đúng một lần, để "Đề xuất giờ khác" chỉ cần sửa ít. */
    private void prefillDraft(@Nullable ScheduleSession from) {
        CoordinationViewModel.Draft d = viewModel.getDraft();
        if (d.prefilled) return;
        d.prefilled = true;
        if (from != null && from.getStartAtMillis() > System.currentTimeMillis()) {
            d.startAt = from.getStartAtMillis();
            d.endAt = from.getEndAtMillis();
            d.dateSet = true;
        }
        if (from != null && from.getFormat() != null && nonNullEditable(etFormat).isEmpty()) {
            etFormat.setText(from.getFormat());
        }
    }

    private void renderDraft() {
        CoordinationViewModel.Draft d = viewModel.getDraft();
        tvDate.setText(d.dateSet ? DateTimeUtils.formatWeekdayFullDate(d.startAt) : null);
        tvTimeRange.setText(d.endAt > 0 ? DateTimeUtils.formatTimeRange(d.startAt, d.endAt) : null);
    }

    private void pickDate() {
        if (mode != Mode.FORM) return;
        CoordinationViewModel.Draft d = viewModel.getDraft();
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
            tvTimeError.setVisibility(View.GONE);
            renderDraft();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    /** Khung giờ chọn bằng hai bước: giờ bắt đầu rồi giờ kết thúc. */
    private void pickStartTime() {
        if (mode != Mode.FORM) return;
        CoordinationViewModel.Draft d = viewModel.getDraft();
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
        CoordinationViewModel.Draft d = viewModel.getDraft();
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
            tvTimeError.setVisibility(View.GONE);
            renderDraft();
        }, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), true);
        dialog.setTitle(R.string.agree_pick_end);
        dialog.show();
    }

    private void showTimeError(String text) {
        tvTimeError.setText(text);
        tvTimeError.setVisibility(View.VISIBLE);
    }

    private void submitProposal() {
        CoordinationViewModel.Draft d = viewModel.getDraft();
        boolean ok = true;
        if (!d.dateSet || d.endAt <= 0) {
            showTimeError(getString(R.string.agree_error_slot_missing));
            ok = false;
        } else if (d.startAt <= System.currentTimeMillis()) {
            showTimeError(getString(R.string.agree_error_past));
            ok = false;
        } else if (d.endAt - d.startAt > com.seniortribe.app.data.CoordinationRepository.MAX_SLOT_MILLIS) {
            showTimeError(getString(R.string.agree_error_too_long));
            ok = false;
        }
        String format = nonNullEditable(etFormat).trim();
        if (format.length() < 2) {
            tilFormat.setError(getString(R.string.agree_error_format));
            ok = false;
        } else {
            tilFormat.setError(null);
        }
        if (!ok || !requireOnline()) return;
        viewModel.proposeSchedule(new Date(d.startAt), new Date(d.endAt), format);
    }

    // =====================================================================
    // Đầu mối: chốt lịch hoặc đổi giờ khác
    // =====================================================================

    private void confirm(ScheduleSession pending) {
        String method = nonNullEditable(etConsent).trim();
        if (!cbAgreed.isChecked()) {
            Snackbar.make(scrollContent, R.string.agree_error_consent_unchecked, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (method.isEmpty()) {
            tilConsent.setError(getString(R.string.agree_error_consent_method));
            return;
        }
        tilConsent.setError(null);
        if (!requireOnline()) return;
        viewModel.confirmSchedule(pending.getScheduleId(), true, method);
    }

    private void askDecline(ScheduleSession pending) {
        if (!requireOnline()) return;
        TextInputDialog.newInstance(KEY_DECLINE, getString(R.string.agree_decline_title),
                getString(R.string.agree_decline_message), getString(R.string.agree_decline_hint),
                getString(R.string.agree_decline_send), 1, pending.getScheduleId())
                .show(getSupportFragmentManager(), KEY_DECLINE);
    }

    private void openProgress() {
        startActivity(new Intent(this, NeedProgressActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(Constants.EXTRA_CASE_CODE, current != null && current.need != null
                        ? current.need.getCaseCode() : null));
    }

    /** Đổi trạng thái phải được máy chủ xác nhận, nên chặn sớm khi mất mạng (R04, R06). */
    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(scrollContent, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    // =====================================================================
    // Quyền (chỉ để hiện đúng nút; Security Rules mới là lớp kiểm tra cuối)
    // =====================================================================

    private static boolean isRecorder(Membership me, NeedSummary n) {
        return me.isRecorder() && me.belongsToRecorderOf(n);
    }

    private static boolean canPropose(Membership me, NeedSummary n) {
        return me.belongsToAssignedOrgOf(n) && (me.isRepresentative() || me.getUid().equals(n.getAssigneeUid()));
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }

    private static String nonNullEditable(TextInputEditText et) {
        Editable e = et.getText();
        return e != null ? e.toString() : "";
    }

    /** TextWatcher gọn: chỉ cần biết nội dung vừa đổi. */
    static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChanged.run(); }
    }
}
