package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.ChatAdapter;
import com.seniortribe.app.dialogs.ProposeScheduleDialog;
import com.seniortribe.app.dialogs.TextInputDialog;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ChatViewModel;

import java.util.Date;

/**
 * F11 · Chat phối hợp realtime giữa đầu mối ghi nhận và đại diện được giao, theo từng lần
 * tiếp nhận (R08). Thẻ phía trên dẫn dắt các bước sau tiếp nhận:
 *
 *  - ACCEPTED : tổ chức đề xuất lịch → đầu mối xác nhận (ghi cách người cao tuổi đồng ý) hoặc từ chối (F12)
 *  - SCHEDULED: tổ chức báo đã hỗ trợ (F13)
 *  - DELIVERED: đầu mối xác nhận hoàn thành hoặc ghi nội dung chưa thống nhất (F13)
 *  - COMPLETED / CANCELLED / đã trả lại: chat chỉ còn đọc
 */
public class ChatActivity extends AppCompatActivity {

    private static final String KEY_CONFIRM = "coord_confirm";
    private static final String KEY_DECLINE = "coord_decline";
    private static final String KEY_DELIVERED = "coord_delivered";
    private static final String KEY_RECONCILE = "coord_reconcile";
    private static final String KEY_COMPLETE = "coord_complete";

    private static final int ACTION_NONE = 0;
    private static final int ACTION_PROPOSE = 1;
    private static final int ACTION_CONFIRM = 2;
    private static final int ACTION_DECLINE = 3;
    private static final int ACTION_DELIVERED = 4;
    private static final int ACTION_COMPLETE = 5;
    private static final int ACTION_RECONCILE = 6;

    private ChatViewModel viewModel;
    private ChatViewModel.Ui current;
    private String needId;

    private MaterialToolbar toolbar;
    private View progressBusy, cardCoordination, layoutInput;
    private TextView tvCoordTitle, tvCoordStatus, tvCoordBody, tvCoordNote, tvReadOnly, tvEmptyChat;
    private MaterialButton btnPrimary, btnSecondary;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private View btnSend;
    private StateView stateView;
    private ChatAdapter adapter;
    private int lastMessageCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        bindViews();

        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        String assignmentId = getIntent().getStringExtra(Constants.EXTRA_ASSIGNMENT_ID);
        NeedSummary passed = (NeedSummary) getIntent().getSerializableExtra(Constants.EXTRA_NEED);
        if (assignmentId == null && passed != null) assignmentId = passed.getCurrentAssignmentId();
        if (needId == null && passed != null) needId = passed.getNeedId();
        if (needId == null || assignmentId == null) {
            finish();
            return;
        }
        if (passed != null && passed.getCaseCode() != null) {
            toolbar.setTitle(getString(R.string.chat_title_with_code, passed.getCaseCode()));
        }

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_chat);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open_need) {
                startActivity(new Intent(this, NeedDetailActivity.class).putExtra(Constants.EXTRA_NEED_ID, needId));
                return true;
            }
            if (item.getItemId() == R.id.action_history) {
                startActivity(new Intent(this, NeedHistoryActivity.class).putExtra(Constants.EXTRA_NEED_ID, needId));
                return true;
            }
            return false;
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);

        btnSend.setOnClickListener(v -> {
            if (current == null || current.membership == null) return;
            viewModel.send(etMessage.getText().toString());
        });

        registerDialogResults();

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(rvMessages, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getMessageSent().observe(this, event -> {
            if (event.getIfNotHandled() != null) etMessage.setText("");
        });
        viewModel.start(needId, assignmentId);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.chatToolbar);
        progressBusy = findViewById(R.id.progressBusy);
        cardCoordination = findViewById(R.id.cardCoordination);
        tvCoordTitle = findViewById(R.id.tvCoordTitle);
        tvCoordStatus = findViewById(R.id.tvCoordStatus);
        tvCoordBody = findViewById(R.id.tvCoordBody);
        tvCoordNote = findViewById(R.id.tvCoordNote);
        btnPrimary = findViewById(R.id.btnCoordPrimary);
        btnSecondary = findViewById(R.id.btnCoordSecondary);
        tvReadOnly = findViewById(R.id.tvReadOnly);
        tvEmptyChat = findViewById(R.id.tvEmptyChat);
        rvMessages = findViewById(R.id.rvMessages);
        layoutInput = findViewById(R.id.layoutInput);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        stateView = new StateView(findViewById(R.id.frameMessages));
    }

    /** Kết quả từ các hộp thoại; đăng ký trước khi hộp thoại được khôi phục sau xoay màn hình. */
    private void registerDialogResults() {
        getSupportFragmentManager().setFragmentResultListener(ProposeScheduleDialog.RESULT_KEY, this, (k, r) ->
                viewModel.proposeSchedule(new Date(r.getLong(ProposeScheduleDialog.RESULT_START_AT)),
                        r.getString(ProposeScheduleDialog.RESULT_PLACE), r.getString(ProposeScheduleDialog.RESULT_NOTE)));
        getSupportFragmentManager().setFragmentResultListener(KEY_CONFIRM, this, (k, r) ->
                viewModel.confirmSchedule(r.getString(TextInputDialog.RESULT_TAG), r.getString(TextInputDialog.RESULT_TEXT)));
        getSupportFragmentManager().setFragmentResultListener(KEY_DECLINE, this, (k, r) ->
                viewModel.declineSchedule(r.getString(TextInputDialog.RESULT_TAG), r.getString(TextInputDialog.RESULT_TEXT)));
        getSupportFragmentManager().setFragmentResultListener(KEY_DELIVERED, this, (k, r) ->
                viewModel.reportDelivered(r.getString(TextInputDialog.RESULT_TEXT)));
        getSupportFragmentManager().setFragmentResultListener(KEY_RECONCILE, this, (k, r) ->
                viewModel.noteReconcile(r.getString(TextInputDialog.RESULT_TEXT)));
        getSupportFragmentManager().setFragmentResultListener(KEY_COMPLETE, this, (k, r) ->
                viewModel.complete(r.getString(TextInputDialog.RESULT_TEXT)));
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(ChatViewModel.Ui s) {
        current = s;
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);

        if (s.membership != null && adapter == null) {
            adapter = new ChatAdapter(this, s.membership.getUid());
            rvMessages.setAdapter(adapter);
        }

        if (s.loading) {
            stateView.showLoading();
        } else if (s.error != null && s.messages.isEmpty()) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
        } else {
            stateView.hide();
        }

        if (adapter != null) {
            adapter.setMessages(s.messages);
            if (s.messages.size() != lastMessageCount && !s.messages.isEmpty()) {
                rvMessages.scrollToPosition(s.messages.size() - 1);
            }
            lastMessageCount = s.messages.size();
        }
        tvEmptyChat.setVisibility(!s.loading && s.error == null && s.messages.isEmpty() ? View.VISIBLE : View.GONE);

        NeedSummary n = s.need;
        if (n != null && n.getCaseCode() != null) toolbar.setTitle(getString(R.string.chat_title_with_code, n.getCaseCode()));
        if (n != null) toolbar.setSubtitle(partnerName(s.membership, n));

        renderReadOnly(s);
        renderCoordination(s);
    }

    /** Chat chỉ gửi được khi lần tiếp nhận này còn hiệu lực (R08). */
    private boolean threadActive(ChatViewModel.Ui s) {
        NeedSummary n = s.need;
        return n != null && n.isCoordinating() && viewModel.getAssignmentId().equals(n.getCurrentAssignmentId());
    }

    private void renderReadOnly(ChatViewModel.Ui s) {
        boolean active = threadActive(s);
        layoutInput.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active || s.loading) {
            tvReadOnly.setVisibility(View.GONE);
            return;
        }
        NeedSummary n = s.need;
        int text;
        if (n != null && Constants.STATUS_COMPLETED.equals(n.getStatus())) text = R.string.chat_readonly_completed;
        else if (n != null && Constants.STATUS_CANCELLED.equals(n.getStatus())) text = R.string.chat_readonly_cancelled;
        else text = R.string.chat_readonly_ended;
        tvReadOnly.setVisibility(View.VISIBLE);
        tvReadOnly.setText(text);
    }

    private void renderCoordination(ChatViewModel.Ui s) {
        NeedSummary n = s.need;
        Membership me = s.membership;
        if (n == null || me == null || !viewModel.getAssignmentId().equals(n.getCurrentAssignmentId())) {
            cardCoordination.setVisibility(View.GONE);
            return;
        }
        cardCoordination.setVisibility(View.VISIBLE);
        NeedStatus.bindChip(tvCoordStatus, n.getStatus());

        boolean recorder = me.isRecorder() && me.belongsToRecorderOf(n);
        boolean assignedOrg = me.belongsToAssignedOrgOf(n);
        boolean canPropose = assignedOrg && (me.isRepresentative() || me.getUid().equals(n.getAssigneeUid()));
        ScheduleSession pending = s.pendingSchedule();
        ScheduleSession latest = s.latestSchedule();

        String title, body, note = null;
        int primary = ACTION_NONE, secondary = ACTION_NONE;

        switch (n.getStatus()) {
            case Constants.STATUS_ACCEPTED:
                title = getString(R.string.coord_step_schedule);
                if (pending != null) {
                    body = getString(R.string.coord_pending, describe(pending),
                            recorder ? getString(R.string.coord_pending_recorder) : getString(R.string.coord_pending_org));
                    if (!TextUtils.isEmpty(pending.getNote())) body += "\n" + getString(R.string.coord_schedule_note, pending.getNote());
                    if (recorder) {
                        primary = ACTION_CONFIRM;
                        secondary = ACTION_DECLINE;
                    } else if (canPropose) {
                        primary = ACTION_PROPOSE;
                    }
                } else {
                    body = getString(canPropose ? R.string.coord_no_schedule_org : R.string.coord_no_schedule_other);
                    if (latest != null && ScheduleSession.DECLINED.equals(latest.getStatus())) {
                        note = getString(R.string.coord_declined, describe(latest), nonNull(latest.getDeclineReason()));
                    }
                    if (canPropose) primary = ACTION_PROPOSE;
                }
                break;
            case Constants.STATUS_SCHEDULED:
                title = getString(R.string.coord_step_visit);
                body = getString(R.string.coord_scheduled, scheduledText(n),
                        assignedOrg ? getString(R.string.coord_scheduled_org) : getString(R.string.coord_scheduled_other));
                if (assignedOrg) primary = ACTION_DELIVERED;
                break;
            case Constants.STATUS_DELIVERED:
                title = getString(R.string.coord_step_reconcile);
                body = getString(R.string.coord_delivered, nonNull(n.getDeliveredByName()), nonNull(n.getDeliveryReport()));
                if (!TextUtils.isEmpty(n.getReconcileNote())) {
                    note = getString(R.string.coord_reconcile_pending, n.getReconcileNote());
                }
                if (recorder) {
                    primary = ACTION_COMPLETE;
                    secondary = ACTION_RECONCILE;
                }
                break;
            case Constants.STATUS_COMPLETED:
                title = getString(R.string.coord_step_done);
                body = getString(R.string.coord_completed, scheduledText(n), nonNull(n.getDeliveryReport()));
                if (!TextUtils.isEmpty(n.getCompletionNote())) note = n.getCompletionNote();
                break;
            default:
                title = getString(R.string.coord_step_closed);
                body = getString(R.string.coord_closed);
                break;
        }

        tvCoordTitle.setText(title);
        tvCoordBody.setText(body);
        tvCoordNote.setVisibility(note != null ? View.VISIBLE : View.GONE);
        tvCoordNote.setText(note);
        setupButton(btnPrimary, primary, pending, s.busy);
        setupButton(btnSecondary, secondary, pending, s.busy);
    }

    private void setupButton(MaterialButton button, int action, ScheduleSession pending, boolean busy) {
        if (action == ACTION_NONE) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setEnabled(!busy);
        switch (action) {
            case ACTION_PROPOSE:
                button.setText(pending != null ? R.string.coord_action_propose_again : R.string.coord_action_propose);
                button.setOnClickListener(v -> {
                    if (!requireOnline()) return;
                    ProposeScheduleDialog.newInstance(current.need.getArea())
                            .show(getSupportFragmentManager(), "propose_schedule");
                });
                break;
            case ACTION_CONFIRM:
                button.setText(R.string.coord_action_confirm);
                button.setOnClickListener(v -> showInput(KEY_CONFIRM, R.string.coord_confirm_title,
                        getString(R.string.coord_confirm_message, describe(pending)),
                        R.string.coord_confirm_hint, R.string.coord_action_confirm, 1, pending.getScheduleId()));
                break;
            case ACTION_DECLINE:
                button.setText(R.string.coord_action_decline);
                button.setOnClickListener(v -> showInput(KEY_DECLINE, R.string.coord_decline_title,
                        getString(R.string.coord_decline_message), R.string.coord_decline_hint,
                        R.string.coord_action_decline, 1, pending.getScheduleId()));
                break;
            case ACTION_DELIVERED:
                button.setText(R.string.coord_action_delivered);
                button.setOnClickListener(v -> showInput(KEY_DELIVERED, R.string.coord_delivered_title,
                        getString(R.string.coord_delivered_message), R.string.coord_delivered_hint,
                        R.string.coord_action_delivered, 5, null));
                break;
            case ACTION_COMPLETE:
                button.setText(R.string.coord_action_complete);
                button.setOnClickListener(v -> showInput(KEY_COMPLETE, R.string.coord_complete_title,
                        getString(R.string.coord_complete_message), R.string.coord_complete_hint,
                        R.string.coord_action_complete, 0, null));
                break;
            case ACTION_RECONCILE:
            default:
                button.setText(R.string.coord_action_reconcile);
                button.setOnClickListener(v -> showInput(KEY_RECONCILE, R.string.coord_reconcile_title,
                        getString(R.string.coord_reconcile_message), R.string.coord_reconcile_hint,
                        R.string.coord_action_save, 1, null));
                break;
        }
    }

    private void showInput(String key, int title, String message, int hint, int positive, int minLength, String tag) {
        if (!requireOnline()) return;
        TextInputDialog.newInstance(key, getString(title), message, getString(hint), getString(positive), minLength, tag)
                .show(getSupportFragmentManager(), key);
    }

    /** Đổi trạng thái phải được máy chủ xác nhận, nên chặn sớm khi mất mạng (R04, R06). */
    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(rvMessages, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
        return false;
    }

    private String describe(ScheduleSession s) {
        if (s == null) return "";
        return DateTimeUtils.formatDateTime(s.getStartAtMillis()) + " · " + nonNull(s.getPlace());
    }

    private String scheduledText(NeedSummary n) {
        if (n.getScheduledAtMillis() == 0) return "";
        return DateTimeUtils.formatDateTime(n.getScheduledAtMillis()) + " · " + nonNull(n.getScheduledPlace());
    }

    /** Tên bên còn lại của cuộc trò chuyện, hiện ở dòng phụ của thanh tiêu đề. */
    private String partnerName(Membership me, NeedSummary n) {
        if (me == null) return null;
        if (me.getUid().equals(n.getRecorderUid())) {
            return getString(R.string.chat_partner, nonNull(n.getAssigneeName()), nonNull(n.getAssignedOrgName()));
        }
        return getString(R.string.chat_partner, nonNull(n.getRecorderName()), nonNull(n.getRecorderOrgName()));
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
