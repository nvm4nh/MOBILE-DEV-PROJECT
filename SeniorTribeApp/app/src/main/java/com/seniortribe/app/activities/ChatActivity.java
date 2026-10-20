package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ChatViewModel;

/**
 * F11 · Chat phối hợp realtime giữa đầu mối ghi nhận và đại diện được giao, theo từng lần
 * tiếp nhận (R08). Theo Figma:
 *  - Tiêu đề "Chat · NC-xxxx", dải trạng thái + hai đơn vị phối hợp.
 *  - Luồng tin có dòng chia ngày ("Hôm nay") và tin hệ thống về lịch hẹn ("Đề xuất lịch: …").
 *  - Thống nhất lịch (F12) và tiến độ / đối soát (F13) là màn hình riêng, mở từ dải trạng thái,
 *    từ tin hệ thống hoặc từ menu.
 * Khi lần tiếp nhận kết thúc (hoàn thành, hủy, trả lại), chat chỉ còn đọc.
 */
public class ChatActivity extends AppCompatActivity implements ChatAdapter.OnScheduleClickListener {

    private ChatViewModel viewModel;
    private ChatViewModel.Ui current;
    private String needId;
    private String caseCode;

    private MaterialToolbar toolbar;
    private View layoutHeader, layoutInput;
    private TextView tvStatus, tvOrgs, tvReadOnly, tvEmptyChat;
    private MaterialButton btnNextStep;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private View btnSend;
    private StateView stateView;
    private ChatAdapter adapter;
    private int lastRowCount = -1;

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
        if (passed != null) setCaseCode(passed.getCaseCode());

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_chat);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_schedule) {
                openSchedule();
                return true;
            }
            if (id == R.id.action_progress) {
                openProgress();
                return true;
            }
            if (id == R.id.action_open_need) {
                startActivity(new Intent(this, NeedDetailActivity.class).putExtra(Constants.EXTRA_NEED_ID, needId));
                return true;
            }
            if (id == R.id.action_history) {
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
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                btnSend.setEnabled(s.toString().trim().length() > 0);
            }
        });
        btnSend.setEnabled(etMessage.getText().toString().trim().length() > 0);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(rvMessages, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getMessageSent().observe(this, event -> {
            if (event.getIfNotHandled() != null) etMessage.setText("");
        });
        viewModel.getSendFailed().observe(this, event -> {
            String failed = event.getIfNotHandled();
            if (failed != null) restoreDraft(failed);
        });
        viewModel.start(needId, assignmentId);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.chatToolbar);
        layoutHeader = findViewById(R.id.layoutChatHeader);
        tvStatus = findViewById(R.id.tvChatStatus);
        tvOrgs = findViewById(R.id.tvChatOrgs);
        btnNextStep = findViewById(R.id.btnChatNextStep);
        tvReadOnly = findViewById(R.id.tvReadOnly);
        tvEmptyChat = findViewById(R.id.tvEmptyChat);
        rvMessages = findViewById(R.id.rvMessages);
        layoutInput = findViewById(R.id.layoutInput);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        stateView = new StateView(findViewById(R.id.frameMessages));
    }

    /**
     * Gửi thất bại thì trả nội dung về ô nhập. Nếu người dùng đã gõ tiếp tin khác, nội dung cũ
     * được đặt lên trước để không ghi đè phần đang gõ.
     */
    private void restoreDraft(String failed) {
        String typing = etMessage.getText().toString();
        String restored = typing.trim().isEmpty() ? failed : failed + "\n" + typing;
        etMessage.setText(restored);
        etMessage.setSelection(etMessage.length());
        etMessage.requestFocus();
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(ChatViewModel.Ui s) {
        current = s;

        if (s.membership != null && adapter == null) {
            adapter = new ChatAdapter(this, s.membership.getUid(), this);
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

        NeedSummary n = s.need;
        if (adapter != null) {
            adapter.setData(s.messages, s.schedules, n != null ? n.getRecorderUid() : null);
            int rows = adapter.getItemCount();
            if (rows != lastRowCount && rows > 0) rvMessages.scrollToPosition(rows - 1);
            lastRowCount = rows;
        }
        tvEmptyChat.setVisibility(!s.loading && s.error == null && s.messages.isEmpty() ? View.VISIBLE : View.GONE);

        if (n != null) setCaseCode(n.getCaseCode());
        renderHeader(s);
        renderReadOnly(s);
    }

    private void setCaseCode(String code) {
        if (TextUtils.isEmpty(code)) return;
        caseCode = code;
        toolbar.setTitle(getString(R.string.chat_title_with_code, code));
    }

    /** Dải trạng thái: chip, "Đơn vị ghi nhận · Tổ chức phụ trách" và lối tắt sang bước tiếp theo. */
    private void renderHeader(ChatViewModel.Ui s) {
        NeedSummary n = s.need;
        if (n == null) {
            layoutHeader.setVisibility(View.GONE);
            return;
        }
        layoutHeader.setVisibility(View.VISIBLE);
        NeedStatus.bindChip(tvStatus, n.getStatus());
        tvOrgs.setText(getString(R.string.chat_orgs, nonNull(n.getRecorderOrgName()), nonNull(n.getAssignedOrgName())));

        boolean currentThread = viewModel.getAssignmentId().equals(n.getCurrentAssignmentId());
        String status = n.getStatus();
        if (currentThread && Constants.STATUS_ACCEPTED.equals(status)) {
            btnNextStep.setVisibility(View.VISIBLE);
            btnNextStep.setText(R.string.chat_next_schedule);
            btnNextStep.setOnClickListener(v -> openSchedule());
        } else if (currentThread && (Constants.STATUS_SCHEDULED.equals(status)
                || Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_COMPLETED.equals(status))) {
            btnNextStep.setVisibility(View.VISIBLE);
            btnNextStep.setText(R.string.chat_next_progress);
            btnNextStep.setOnClickListener(v -> openProgress());
        } else {
            btnNextStep.setVisibility(View.GONE);
        }
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

    // =====================================================================
    // Điều hướng sang F12, F13
    // =====================================================================

    @Override
    public void onScheduleClick() {
        openSchedule();
    }

    private void openSchedule() {
        startActivity(new Intent(this, ScheduleAgreementActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(Constants.EXTRA_CASE_CODE, caseCode));
    }

    private void openProgress() {
        startActivity(new Intent(this, NeedProgressActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(Constants.EXTRA_CASE_CODE, caseCode));
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
