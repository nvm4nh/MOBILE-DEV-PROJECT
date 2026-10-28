package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.dialogs.AcceptNeedDialog;
import com.seniortribe.app.dialogs.NeedReasonSheet;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgMember;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.NeedDetailViewModel;

/**
 * F10 · Chi tiết và tiếp nhận nhu cầu; F14 · Trả lại / hủy nhu cầu.
 *
 * Nút hành động thay đổi theo quyền và trạng thái:
 *  - Điều phối viên, nhu cầu REVIEW      → Rà soát nhu cầu (mở F07)
 *  - Đại diện tổ chức, nhu cầu OPEN      → Tiếp nhận nhu cầu (hộp thoại chọn người phụ trách)
 *  - Đầu mối, nhu cầu REVIEW/OPEN        → Chỉnh sửa
 *  - Hai bên phụ trách, từ ACCEPTED      → Mở chat phối hợp
 *  - Menu ⋮: Trả lại (tổ chức phụ trách), Hủy (đầu mối)
 */
public class NeedDetailActivity extends AppCompatActivity {

    private static final int ACTION_NONE = 0;
    private static final int ACTION_REVIEW = 1;
    private static final int ACTION_ACCEPT = 2;
    private static final int ACTION_EDIT = 3;
    private static final int ACTION_CHAT = 4;

    private NeedDetailViewModel viewModel;
    private StateView stateView;
    private String needId;
    private NeedDetailViewModel.Ui current;

    private MaterialToolbar toolbar;
    private NestedScrollView scrollContent;
    private View progressBusy, bottomBar, cardAssignment;
    private TextView tvCaseCode, tvStatus, tvTitle, tvStatusNote, tvContent, tvTopic, tvArea, tvDesiredTime, tvRecorder;
    private TextView tvAssignedOrg, tvAssignee, tvAcceptedAt;
    private View layoutPrivateMasked, layoutPrivateShown, progressPrivate;
    private TextView tvPrivateHint, tvPrivateName, tvPrivateContact, tvPrivateAddress;
    private MaterialButton btnPrimary, btnSecondary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_need_detail);
        bindViews();

        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            // Tương thích màn hình cũ truyền cả object NeedSummary
            Object legacy = getIntent().getSerializableExtra(Constants.EXTRA_NEED);
            if (legacy instanceof NeedSummary) needId = ((NeedSummary) legacy).getNeedId();
        }
        if (needId == null) {
            finish();
            return;
        }

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_need_detail);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_need) openEdit();
            else if (id == R.id.action_return_need) openReasonSheet(NeedReasonSheet.MODE_RETURN);
            else if (id == R.id.action_cancel_need) openReasonSheet(NeedReasonSheet.MODE_CANCEL);
            else if (id == R.id.action_need_history) openHistory();
            else if (id == R.id.action_need_sos) openSos();
            else return false;
            return true;
        });

        // Nhận kết quả từ hộp thoại tiếp nhận và bảng trả lại / hủy (vẫn nhận được sau khi xoay màn hình)
        getSupportFragmentManager().setFragmentResultListener(AcceptNeedDialog.RESULT_KEY, this, (key, result) -> {
            OrgMember assignee = new OrgMember(result.getString(AcceptNeedDialog.RESULT_UID),
                    result.getString(AcceptNeedDialog.RESULT_NAME));
            viewModel.accept(assignee);
        });
        getSupportFragmentManager().setFragmentResultListener(NeedReasonSheet.RESULT_KEY, this, (key, result) -> {
            String reason = result.getString(NeedReasonSheet.RESULT_REASON);
            String note = result.getString(NeedReasonSheet.RESULT_NOTE);
            if (NeedReasonSheet.MODE_RETURN.equals(result.getString(NeedReasonSheet.RESULT_MODE))) {
                viewModel.returnNeed(reason, note);
            } else {
                viewModel.cancel(reason, note);
            }
        });

        viewModel = new ViewModelProvider(this).get(NeedDetailViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollContent, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.start(needId);
    }

    private void bindViews() {
        stateView = new StateView(findViewById(R.id.rootNeedDetail));
        toolbar = findViewById(R.id.toolbar);
        scrollContent = findViewById(R.id.scrollContent);
        progressBusy = findViewById(R.id.progressBusy);
        bottomBar = findViewById(R.id.bottomBar);
        cardAssignment = findViewById(R.id.cardAssignment);
        tvCaseCode = findViewById(R.id.tvCaseCode);
        tvStatus = findViewById(R.id.tvStatus);
        tvTitle = findViewById(R.id.tvTitle);
        tvStatusNote = findViewById(R.id.tvStatusNote);
        tvContent = findViewById(R.id.tvContent);
        tvTopic = findViewById(R.id.tvTopic);
        tvArea = findViewById(R.id.tvArea);
        tvDesiredTime = findViewById(R.id.tvDesiredTime);
        tvRecorder = findViewById(R.id.tvRecorder);
        tvAssignedOrg = findViewById(R.id.tvAssignedOrg);
        tvAssignee = findViewById(R.id.tvAssignee);
        tvAcceptedAt = findViewById(R.id.tvAcceptedAt);
        layoutPrivateMasked = findViewById(R.id.layoutPrivateMasked);
        layoutPrivateShown = findViewById(R.id.layoutPrivateShown);
        progressPrivate = findViewById(R.id.progressPrivate);
        tvPrivateHint = findViewById(R.id.tvPrivateHint);
        tvPrivateName = findViewById(R.id.tvPrivateName);
        tvPrivateContact = findViewById(R.id.tvPrivateContact);
        tvPrivateAddress = findViewById(R.id.tvPrivateAddress);
        btnPrimary = findViewById(R.id.btnPrimaryAction);
        btnSecondary = findViewById(R.id.btnSecondaryAction);
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(NeedDetailViewModel.Ui s) {
        current = s;
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);

        if (s.loading || (s.need == null && s.error == null)) {
            showContent(false);
            stateView.showLoading();
            return;
        }
        if (s.need == null) {
            showContent(false);
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        stateView.hide();
        showContent(true);

        NeedSummary n = s.need;
        toolbar.setTitle(n.getCaseCode() != null ? n.getCaseCode() : getString(R.string.detail_title));
        tvCaseCode.setText(n.getCaseCode());
        NeedStatus.bindChip(tvStatus, n.getStatus());
        tvTitle.setText(n.getTitle());
        tvContent.setText(n.getContent());
        tvTopic.setText(n.getTopic());
        tvArea.setText(n.getArea());
        tvDesiredTime.setText(n.getDesiredTime());
        String recordedAt = n.getCreatedAtMillis() > 0 ? DateTimeUtils.formatDate(n.getCreatedAtMillis()) : "";
        tvRecorder.setText(getString(R.string.detail_recorded_by, nonNull(n.getRecorderOrgName()), recordedAt));

        renderStatusNote(n, s.membership);
        renderAssignment(n);
        renderPrivate(s);
        renderActions(s);
    }

    private void showContent(boolean visible) {
        scrollContent.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(visible ? bottomBar.getVisibility() : View.GONE);
        if (!visible) {
            setMenuVisible(false, false, false);
            MenuItem history = toolbar.getMenu().findItem(R.id.action_need_history);
            if (history != null) history.setVisible(false);
        }
    }

    private void renderStatusNote(NeedSummary n, Membership me) {
        String text = null;
        String status = n.getStatus();
        if (Constants.STATUS_REVIEW.equals(status) && n.isNeedsRevision() && me != null && me.belongsToRecorderOf(n)) {
            text = getString(R.string.detail_note_revision, nonNull(n.getReviewNote()));
        } else if (Constants.STATUS_REVIEW.equals(status)) {
            text = getString(R.string.detail_note_review);
        } else if (Constants.STATUS_SCHEDULED.equals(status) && n.getScheduledAtMillis() > 0) {
            text = getString(R.string.detail_note_scheduled,
                    DateTimeUtils.formatSlot(n.getScheduledAtMillis(), n.getScheduledEndAtMillis()),
                    nonNull(!TextUtils.isEmpty(n.getScheduledFormat()) ? n.getScheduledFormat() : n.getScheduledPlace()));
        } else if (Constants.STATUS_DELIVERED.equals(status)) {
            text = getString(R.string.detail_note_delivered, nonNull(n.getDeliveredByName()), nonNull(n.getDeliveryReport()));
            if (!TextUtils.isEmpty(n.getReconcileNote())) {
                text += "\n" + getString(R.string.coord_reconcile_pending, n.getReconcileNote());
            }
        } else if (Constants.STATUS_COMPLETED.equals(status)) {
            text = getString(R.string.detail_note_completed, nonNull(n.getDeliveryReport()));
        } else if (Constants.STATUS_CANCELLED.equals(status)) {
            text = TextUtils.isEmpty(n.getCancelNote())
                    ? getString(R.string.detail_note_cancelled, nonNull(n.getCancelReason()))
                    : getString(R.string.detail_note_cancelled_with_note, nonNull(n.getCancelReason()), n.getCancelNote());
        }
        tvStatusNote.setVisibility(text != null ? View.VISIBLE : View.GONE);
        tvStatusNote.setText(text);
    }

    private void renderAssignment(NeedSummary n) {
        boolean hasAssignment = !TextUtils.isEmpty(n.getAssignedOrgId());
        cardAssignment.setVisibility(hasAssignment ? View.VISIBLE : View.GONE);
        if (!hasAssignment) return;
        tvAssignedOrg.setText(n.getAssignedOrgName());
        tvAssignee.setText(getString(R.string.detail_assignee, nonNull(n.getAssigneeName())));
        if (n.getAcceptedAt() != null) {
            tvAcceptedAt.setVisibility(View.VISIBLE);
            tvAcceptedAt.setText(getString(R.string.detail_accepted_at,
                    DateTimeUtils.formatDateTime(n.getAcceptedAt().getTime())));
        } else {
            tvAcceptedAt.setVisibility(View.GONE);
        }
    }

    private void renderPrivate(NeedDetailViewModel.Ui s) {
        boolean shown = s.privateState == NeedDetailViewModel.PRIVATE_SHOWN && s.privateDetails != null;
        layoutPrivateShown.setVisibility(shown ? View.VISIBLE : View.GONE);
        layoutPrivateMasked.setVisibility(shown ? View.GONE : View.VISIBLE);
        progressPrivate.setVisibility(s.privateState == NeedDetailViewModel.PRIVATE_LOADING ? View.VISIBLE : View.GONE);

        if (shown) {
            NeedPrivateDetails p = s.privateDetails;
            tvPrivateHint.setVisibility(View.GONE);
            tvPrivateName.setText(p.getElderlyName());
            tvPrivateContact.setText(p.getContact());
            tvPrivateAddress.setText(TextUtils.isEmpty(p.getAddress()) ? getString(R.string.detail_private_no_address) : p.getAddress());
        } else {
            tvPrivateHint.setVisibility(View.VISIBLE);
            tvPrivateHint.setText(s.privateState == NeedDetailViewModel.PRIVATE_ERROR
                    ? R.string.detail_private_error : R.string.detail_private_hidden);
        }
    }

    /** Chọn nút chính, nút phụ và mục menu theo quyền + trạng thái. */
    private void renderActions(NeedDetailViewModel.Ui s) {
        NeedSummary n = s.need;
        Membership me = s.membership;
        String status = n.getStatus();

        boolean recorderOrg = me != null && me.belongsToRecorderOf(n);
        boolean assignedOrg = me != null && me.belongsToAssignedOrgOf(n);

        boolean canReview = recorderOrg && me.isReviewer() && Constants.STATUS_REVIEW.equals(status)
                && !me.getUid().equals(n.getRecorderUid());
        boolean canAccept = me != null && me.isRepresentative() && Constants.STATUS_OPEN.equals(status);
        boolean canEdit = recorderOrg && me.isRecorder()
                && (Constants.STATUS_REVIEW.equals(status) || Constants.STATUS_OPEN.equals(status));
        boolean canReturn = assignedOrg && me.isRepresentative()
                && (Constants.STATUS_ACCEPTED.equals(status) || Constants.STATUS_SCHEDULED.equals(status));
        boolean canCancel = recorderOrg && me.isRecorder() && !n.isFinished();
        // Chat chỉ giữa đầu mối đã ghi nhận và người được giao phụ trách (F11, R08)
        boolean chatMember = me != null
                && (me.getUid().equals(n.getRecorderUid()) || me.getUid().equals(n.getAssigneeUid()));
        boolean canChat = chatMember && !TextUtils.isEmpty(n.getCurrentAssignmentId());

        int primary = ACTION_NONE;
        if (canReview) primary = ACTION_REVIEW;
        else if (canAccept) primary = ACTION_ACCEPT;
        else if (canEdit) primary = ACTION_EDIT;
        else if (canChat) primary = ACTION_CHAT;

        int secondary = ACTION_NONE;
        if (canChat && primary != ACTION_CHAT) secondary = ACTION_CHAT;
        else if (canEdit && primary != ACTION_EDIT) secondary = ACTION_EDIT;

        setupButton(btnPrimary, primary, n, s.busy);
        setupButton(btnSecondary, secondary, n, s.busy);
        bottomBar.setVisibility(primary == ACTION_NONE ? View.GONE : View.VISIBLE);

        setMenuVisible(canEdit && primary != ACTION_EDIT && secondary != ACTION_EDIT, canReturn, canCancel);
        MenuItem history = toolbar.getMenu().findItem(R.id.action_need_history);
        if (history != null) history.setVisible(recorderOrg || assignedOrg);
        // E09 · SOS cho hai người đang phối hợp (đầu mối đã ghi nhận / người được giao)
        MenuItem sos = toolbar.getMenu().findItem(R.id.action_need_sos);
        if (sos != null) sos.setVisible(canChat && n.isCoordinating());
    }

    /** E09 · Màn hình Khẩn cấp, chọn sẵn nhu cầu này để báo cho bên cùng phối hợp. */
    private void openSos() {
        startActivity(new android.content.Intent(this, SosActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId));
    }

    private void setupButton(MaterialButton button, int action, NeedSummary n, boolean busy) {
        if (action == ACTION_NONE) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setEnabled(!busy);
        switch (action) {
            case ACTION_REVIEW:
                button.setText(R.string.detail_action_review);
                button.setOnClickListener(v -> openReview());
                break;
            case ACTION_ACCEPT:
                button.setText(R.string.detail_action_accept);
                button.setOnClickListener(v -> openAcceptDialog());
                break;
            case ACTION_EDIT:
                button.setText(n.isNeedsRevision() ? R.string.detail_action_supplement : R.string.action_edit_need);
                button.setOnClickListener(v -> openEdit());
                break;
            case ACTION_CHAT:
            default:
                button.setText(R.string.detail_action_chat);
                button.setOnClickListener(v -> openChat());
                break;
        }
    }

    private void setMenuVisible(boolean edit, boolean ret, boolean cancel) {
        Menu menu = toolbar.getMenu();
        if (menu.findItem(R.id.action_edit_need) == null) return;
        menu.findItem(R.id.action_edit_need).setVisible(edit);
        menu.findItem(R.id.action_return_need).setVisible(ret);
        menu.findItem(R.id.action_cancel_need).setVisible(cancel);
    }

    // =====================================================================
    // Điều hướng và thao tác
    // =====================================================================

    private void openReview() {
        Intent intent = new Intent(this, ReviewNeedActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, needId);
        startActivity(intent);
    }

    private void openEdit() {
        Intent intent = new Intent(this, CreateNeedActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, needId);
        startActivity(intent);
    }

    /** Chat theo từng lần tiếp nhận (R08) – phần chat do F11 phụ trách. */
    private void openChat() {
        if (current == null || current.need == null) return;
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, needId);
        intent.putExtra(Constants.EXTRA_NEED, current.need);
        intent.putExtra(Constants.EXTRA_ASSIGNMENT_ID, current.need.getCurrentAssignmentId());
        startActivity(intent);
    }

    /** F15 · Lịch sử các bước của nhu cầu. */
    private void openHistory() {
        Intent intent = new Intent(this, NeedHistoryActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, needId);
        startActivity(intent);
    }

    private void openAcceptDialog() {
        if (current == null || current.membership == null || !requireOnline()) return;
        Membership me = current.membership;
        AcceptNeedDialog.newInstance(current.need.getCaseCode(), me.getOrgId(), me.getOrgName(),
                me.getUid(), me.getDisplayName())
                .show(getSupportFragmentManager(), "accept_need");
    }

    private void openReasonSheet(String mode) {
        if (current == null || current.need == null || !requireOnline()) return;
        NeedReasonSheet.newInstance(mode, current.need.getCaseCode())
                .show(getSupportFragmentManager(), "need_reason");
    }

    /** Đổi trạng thái phải được máy chủ xác nhận, nên chặn sớm khi mất mạng (R04). */
    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(scrollContent, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
