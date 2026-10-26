package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import android.content.ActivityNotFoundException;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.ChatAdapter;
import com.seniortribe.app.data.ChatMediaRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.models.CallRecord;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.notifications.AppNotifier;
import com.seniortribe.app.notifications.NotificationCenter;
import com.seniortribe.app.notifications.NotificationEngine;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.ChatViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * F11 · Chat phối hợp realtime giữa đầu mối ghi nhận và đại diện được giao, theo từng lần
 * tiếp nhận (R08). Theo Figma:
 *  - Tiêu đề "Chat · NC-xxxx", dải trạng thái + hai đơn vị phối hợp.
 *  - Luồng tin có dòng chia ngày ("Hôm nay") và tin hệ thống về lịch hẹn ("Đề xuất lịch: …").
 *  - Thống nhất lịch (F12) và tiến độ / đối soát (F13) là màn hình riêng, mở từ dải trạng thái,
 *    từ tin hệ thống hoặc từ menu.
 * Khi lần tiếp nhận kết thúc (hoàn thành, hủy, trả lại), chat chỉ còn đọc.
 *
 * E06 · Chat nâng cao: gửi ảnh (thư viện / chụp) và tệp (PDF, Word, Excel…) có hỏi xác nhận và
 * nhắc riêng tư; xem ảnh, mở / lưu tệp; gọi thoại / video cho người còn lại trong luồng chat.
 * Tất cả chỉ khi lần tiếp nhận còn hiệu lực, Security Rules kiểm tra lại (R08).
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

    // E06
    private View btnAttach, layoutUpload;
    private TextView tvUpload;
    private LinearProgressIndicator progressUpload;
    @Nullable private AlertDialog attachDialog;
    @Nullable private Uri cameraUri;
    private final ChatMediaRepository media = new ChatMediaRepository();
    private byte[] fileToSave;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) viewModel.prepareAttachment(this, uri, true);
            });

    private final ActivityResultLauncher<Uri> takePhoto = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraUri != null) viewModel.prepareAttachment(this, cameraUri, true);
            });

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) viewModel.prepareAttachment(this, uri, false);
            });

    private final ActivityResultLauncher<String> saveFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("*/*"), this::writeSavedFile);

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
            if (id == R.id.action_reschedule) {
                openReschedule();
                return true;
            }
            if (id == R.id.action_call_voice || id == R.id.action_call_video) {
                startCall(id == R.id.action_call_video);
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
        btnAttach.setOnClickListener(v -> showAttachMenu());
        if (savedInstanceState != null) {
            String cam = savedInstanceState.getString(STATE_CAMERA_URI);
            if (cam != null) cameraUri = Uri.parse(cam);
        }

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
        viewModel.getIncomingCall().observe(this, event -> {
            CallRecord c = event.getIfNotHandled();
            if (c != null) openIncomingCall(c);
        });
        viewModel.start(needId, assignmentId);
    }

    // E01 · Đang xem luồng chat này thì không hiện thông báo tin nhắn của nó, và gỡ thông báo cũ
    @Override
    protected void onResume() {
        super.onResume();
        String assignmentId = viewModel != null ? viewModel.getAssignmentId() : null;
        if (assignmentId == null) return;
        NotificationCenter.setVisibleChat(assignmentId);
        new AppNotifier(this).cancel(NotificationEngine.chatKey(assignmentId));
    }

    @Override
    protected void onPause() {
        NotificationCenter.setVisibleChat(null);
        super.onPause();
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
        btnAttach = findViewById(R.id.btnAttach);
        layoutUpload = findViewById(R.id.layoutUpload);
        tvUpload = findViewById(R.id.tvUpload);
        progressUpload = findViewById(R.id.progressUpload);
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
            adapter.setData(s.messages, s.schedules, s.calls, n != null ? n.getRecorderUid() : null);
            int rows = adapter.getItemCount();
            if (rows != lastRowCount && rows > 0) rvMessages.scrollToPosition(rows - 1);
            lastRowCount = rows;
        }
        tvEmptyChat.setVisibility(!s.loading && s.error == null && s.messages.isEmpty() ? View.VISIBLE : View.GONE);

        if (n != null) setCaseCode(n.getCaseCode());
        renderHeader(s);
        renderReadOnly(s);
        renderMedia(s);
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

    @Override
    public void onRescheduleClick() {
        openReschedule();
    }

    /** E02 · Đổi lịch đã chốt. */
    private void openReschedule() {
        startActivity(new Intent(this, RescheduleActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(Constants.EXTRA_CASE_CODE, caseCode));
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

    // =====================================================================
    // E06 · Ảnh / tệp
    // =====================================================================

    private static final String STATE_CAMERA_URI = "chat_camera_uri";

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraUri != null) outState.putString(STATE_CAMERA_URI, cameraUri.toString());
    }

    private void renderMedia(ChatViewModel.Ui s) {
        boolean active = threadActive(s);
        boolean busy = s.preparing || s.uploadPercent >= 0;
        btnAttach.setEnabled(active && !busy);
        btnAttach.setAlpha(active && !busy ? 1f : 0.4f);
        layoutUpload.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (s.preparing) {
            tvUpload.setText(R.string.chat_attach_preparing);
            progressUpload.setIndeterminate(true);
        } else if (s.uploadPercent >= 0) {
            tvUpload.setText(getString(R.string.chat_attach_uploading, s.uploadPercent));
            if (progressUpload.isIndeterminate()) {
                progressUpload.setVisibility(View.INVISIBLE);
                progressUpload.setIndeterminate(false);
                progressUpload.setVisibility(View.VISIBLE);
            }
            progressUpload.setProgressCompat(s.uploadPercent, true);
        }
        // Tệp đã chuẩn bị xong (kể cả sau khi xoay màn hình): hỏi xác nhận trước khi gửi
        if (s.pendingAttachment != null && attachDialog == null && active) showConfirm(s.pendingAttachment, s);
        else if (s.pendingAttachment != null && !active) viewModel.discardAttachment();

        Menu menu = toolbar.getMenu();
        MenuItem voice = menu.findItem(R.id.action_call_voice);
        MenuItem videoCall = menu.findItem(R.id.action_call_video);
        if (voice != null) voice.setVisible(active);
        if (videoCall != null) videoCall.setVisible(active);
        MenuItem resched = menu.findItem(R.id.action_reschedule);
        if (resched != null) resched.setVisible(RescheduleActivity.canReschedule(s.membership, s.need)
                && viewModel.getAssignmentId().equals(s.need.getCurrentAssignmentId()));
    }

    private void showAttachMenu() {
        if (current == null || !threadActive(current)) return;
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Snackbar.make(rvMessages, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
            return;
        }
        String[] items = {
                getString(R.string.chat_attach_gallery),
                getString(R.string.chat_attach_camera),
                getString(R.string.chat_attach_file)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chat_attach_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pickImage.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else if (which == 1) {
                        launchCamera();
                    } else {
                        pickFile.launch(ChatMediaRepository.ALLOWED_FILE_TYPES);
                    }
                })
                .show();
    }

    /** Ảnh chụp ghi tạm vào cache riêng của ứng dụng, xóa khi đóng chat. */
    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), SHARED_DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir");
            File photo = File.createTempFile("chup_", ".jpg", dir);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
            takePhoto.launch(cameraUri);
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(rvMessages, R.string.chat_file_no_app, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showConfirm(ChatMediaRepository.Prepared p, ChatViewModel.Ui s) {
        View content = getLayoutInflater().inflate(R.layout.dialog_attachment_confirm, null);
        ImageView preview = content.findViewById(R.id.ivPreview);
        TextView fileRow = content.findViewById(R.id.tvFileRow);
        TextView privacy = content.findViewById(R.id.tvPrivacy);
        EditText caption = content.findViewById(R.id.etCaption);
        boolean image = ChatMessage.TYPE_IMAGE.equals(p.kind);
        if (image && p.thumb != null) {
            preview.setImageBitmap(BitmapFactory.decodeByteArray(p.thumb, 0, p.thumb.length));
            preview.setVisibility(View.VISIBLE);
        }
        fileRow.setVisibility(image ? View.GONE : View.VISIBLE);
        fileRow.setText(p.fileName + " · " + ChatMediaRepository.formatSize(p.data.length));
        boolean beforeSchedule = s.need != null && Constants.STATUS_ACCEPTED.equals(s.need.getStatus());
        privacy.setText(getString(R.string.chat_attach_privacy)
                + (beforeSchedule ? "\n\n" + getString(R.string.chat_attach_privacy_before_schedule) : ""));
        String what = image ? getString(R.string.chat_attach_image_label) : getString(R.string.chat_attach_file_label, p.fileName);
        attachDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.chat_attach_confirm_title, what))
                .setView(content)
                .setNegativeButton(R.string.action_cancel, (d, w) -> viewModel.discardAttachment())
                .setPositiveButton(R.string.chat_attach_send, (d, w) -> {
                    if (!NetworkUtils.isNetworkAvailable(this)) {
                        Snackbar.make(rvMessages, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
                        return; // giữ tệp đang chờ để gửi lại
                    }
                    viewModel.sendAttachment(caption.getText().toString());
                })
                .setOnCancelListener(d -> viewModel.discardAttachment())
                .create();
        attachDialog.setOnDismissListener(d -> attachDialog = null);
        attachDialog.show();
    }

    @Override
    public void onAttachmentClick(ChatMessage msg) {
        if (msg.isImage()) {
            startActivity(new Intent(this, ImageViewerActivity.class)
                    .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                    .putExtra(ImageViewerActivity.EXTRA_ATTACHMENT_ID, msg.getAttachmentId())
                    .putExtra(ImageViewerActivity.EXTRA_FILE_NAME, msg.getFileName()));
            return;
        }
        String[] items = {getString(R.string.chat_file_open), getString(R.string.chat_file_save)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(msg.getFileName())
                .setItems(items, (d, which) -> downloadFile(msg, which == 0))
                .show();
    }

    /** Tải tệp vào bộ nhớ rồi mở bằng ứng dụng khác (qua cache tạm) hoặc lưu về chỗ người dùng chọn. */
    private void downloadFile(ChatMessage msg, boolean open) {
        Snackbar loading = Snackbar.make(rvMessages, R.string.chat_file_loading, Snackbar.LENGTH_INDEFINITE);
        loading.show();
        media.download(viewModel.getAssignmentId(), msg.getAttachmentId()).addOnCompleteListener(this, t -> {
            loading.dismiss();
            if (!t.isSuccessful()) {
                Snackbar.make(rvMessages, NeedErrors.message(t.getException()), Snackbar.LENGTH_LONG).show();
                return;
            }
            if (open) openWithOtherApp(msg, t.getResult());
            else {
                fileToSave = t.getResult();
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.chat_file_save)
                        .setMessage(R.string.chat_file_save_warning)
                        .setNegativeButton(R.string.action_cancel, (d, w) -> fileToSave = null)
                        .setPositiveButton(R.string.chat_file_save, (d, w) -> saveFile.launch(msg.getFileName()))
                        .show();
            }
        });
    }

    private void openWithOtherApp(ChatMessage msg, byte[] data) {
        try {
            File dir = new File(new File(getCacheDir(), SHARED_DIR), msg.getAttachmentId());
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir");
            File file = new File(dir, msg.getFileName() != null ? msg.getFileName() : "tep");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, msg.getMimeType())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, getString(R.string.chat_file_open)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(rvMessages, R.string.chat_file_no_app, Snackbar.LENGTH_LONG).show();
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(rvMessages, R.string.viewer_error, Snackbar.LENGTH_LONG).show();
        }
    }

    private void writeSavedFile(@Nullable Uri uri) {
        byte[] data = fileToSave;
        fileToSave = null;
        if (uri == null || data == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("no stream");
            out.write(data);
            Snackbar.make(rvMessages, getString(R.string.chat_file_saved, ""), Snackbar.LENGTH_SHORT).show();
        } catch (IOException e) {
            Snackbar.make(rvMessages, R.string.viewer_error, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Thư mục tạm cho ảnh chụp và tệp mở bằng ứng dụng khác; xóa khi đóng chat (F16). */
    public static final String SHARED_DIR = "shared";

    public static void clearSharedCache(android.content.Context context) {
        deleteRecursive(new File(context.getCacheDir(), SHARED_DIR));
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) clearSharedCache(this);
        super.onDestroy();
    }

    // =====================================================================
    // E06 · Gọi thoại / video
    // =====================================================================

    private void startCall(boolean video) {
        ChatViewModel.Ui s = current;
        if (s == null || s.membership == null || s.need == null || !threadActive(s)) {
            Snackbar.make(rvMessages, R.string.chat_call_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Snackbar.make(rvMessages, R.string.action_needs_network, Snackbar.LENGTH_LONG).show();
            return;
        }
        NeedSummary n = s.need;
        boolean iAmRecorder = s.membership.getUid().equals(n.getRecorderUid());
        String peerUid = iAmRecorder ? n.getAssigneeUid() : n.getRecorderUid();
        String peerName = iAmRecorder ? n.getAssigneeName() : n.getRecorderName();
        if (peerUid == null || peerUid.equals(s.membership.getUid())) {
            Snackbar.make(rvMessages, R.string.chat_call_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, CallActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(CallActivity.EXTRA_VIDEO, video)
                .putExtra(CallActivity.EXTRA_PEER_UID, peerUid)
                .putExtra(CallActivity.EXTRA_PEER_NAME, peerName));
    }

    private void openIncomingCall(CallRecord c) {
        startActivity(new Intent(this, CallActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)
                .putExtra(Constants.EXTRA_ASSIGNMENT_ID, viewModel.getAssignmentId())
                .putExtra(CallActivity.EXTRA_CALL_ID, c.getCallId())
                .putExtra(CallActivity.EXTRA_INCOMING, true)
                .putExtra(CallActivity.EXTRA_PEER_NAME, c.getCallerName()));
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
