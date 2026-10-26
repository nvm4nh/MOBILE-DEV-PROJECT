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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.seniortribe.app.R;
import com.seniortribe.app.models.NeedForm;
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NeedDraftStore;
import com.seniortribe.app.utils.NeedFormAssist;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.utils.PrivacyGuard;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.NeedFormViewModel;

/**
 * F06 · Ghi nhận nhu cầu, và F08 · Sửa nhu cầu khi chưa có tổ chức nhận.
 *
 * Mở không kèm EXTRA_NEED_ID → tạo mới. Kèm EXTRA_NEED_ID → sửa (gửi lại rà soát).
 * Form gồm ba phần: Tóm tắt (chia sẻ trong mạng lưới), Thông tin riêng (lưu tách), Sự đồng ý.
 */
public class CreateNeedActivity extends AppCompatActivity {

    private static final String STATE_DRAFT_CHECKED = "draft_checked";

    private NeedFormViewModel viewModel;
    private NeedDraftStore draftStore;
    private StateView stateView;
    private boolean draftChecked;

    private MaterialToolbar toolbar;
    private NestedScrollView scrollForm;
    private TextView tvFormNotice;
    private EditText etCaseCode, etTitle, etTopic, etContent, etArea, etDesiredTime, etSource;
    private EditText etElderlyName, etContact, etAddress, etConsentMethod;
    private TextInputLayout tilTitle, tilTopic, tilContent, tilArea, tilDesiredTime, tilSource;
    private TextInputLayout tilElderlyName, tilContact, tilConsentMethod;
    private CheckBox cbConsent;
    private TextView tvConsentError, tvDraftHint;
    private MaterialButton btnSubmit, btnSaveDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_need);
        bindViews();
        // E04, E05 · "Nói để nhập nội dung" và "Nhờ trợ lý viết lại" dưới ô Nội dung
        new NeedFormAssist(this, scrollForm, etContent, etElderlyName, etAddress, etContact, etDesiredTime);

        draftChecked = savedInstanceState != null && savedInstanceState.getBoolean(STATE_DRAFT_CHECKED);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        draftStore = new NeedDraftStore(this, user != null ? user.getUid() : null);

        toolbar.setNavigationOnClickListener(v -> finish());
        etTopic.setOnClickListener(v -> showTopicPicker());
        tilTopic.setEndIconOnClickListener(v -> showTopicPicker());
        btnSubmit.setOnClickListener(v -> submit());
        btnSaveDraft.setOnClickListener(v -> saveDraft());
        cbConsent.setOnCheckedChangeListener((b, checked) -> { if (checked) tvConsentError.setVisibility(View.GONE); });
        clearErrorWhileTyping(etTitle, tilTitle);
        clearErrorWhileTyping(etContent, tilContent);
        clearErrorWhileTyping(etArea, tilArea);
        clearErrorWhileTyping(etDesiredTime, tilDesiredTime);
        clearErrorWhileTyping(etSource, tilSource);
        clearErrorWhileTyping(etElderlyName, tilElderlyName);
        clearErrorWhileTyping(etContact, tilContact);
        clearErrorWhileTyping(etConsentMethod, tilConsentMethod);

        viewModel = new ViewModelProvider(this).get(NeedFormViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getPrefill().observe(this, event -> {
            NeedFormViewModel.Prefill data = event.getIfNotHandled();
            if (data != null) fillFromExisting(data.summary, data.privateDetails);
        });
        viewModel.getMessages().observe(this, event -> {
            String msg = event.getIfNotHandled();
            if (msg != null) Snackbar.make(scrollForm, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getSubmitted().observe(this, event -> {
            String caseCode = event.getIfNotHandled();
            if (caseCode == null) return;
            draftStore.clear();
            Toast.makeText(this, getString(R.string.form_submitted, caseCode), Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        });

        viewModel.start(getIntent().getStringExtra(Constants.EXTRA_NEED_ID));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAFT_CHECKED, draftChecked);
    }

    private void bindViews() {
        stateView = new StateView(findViewById(R.id.rootCreateNeed));
        toolbar = findViewById(R.id.toolbar);
        scrollForm = findViewById(R.id.scrollForm);
        tvFormNotice = findViewById(R.id.tvFormNotice);
        etCaseCode = findViewById(R.id.etCaseCode);
        etTitle = findViewById(R.id.etTitle);
        etTopic = findViewById(R.id.etTopic);
        etContent = findViewById(R.id.etContent);
        etArea = findViewById(R.id.etArea);
        etDesiredTime = findViewById(R.id.etDesiredTime);
        etSource = findViewById(R.id.etSource);
        etElderlyName = findViewById(R.id.etElderlyName);
        etContact = findViewById(R.id.etContact);
        etAddress = findViewById(R.id.etAddress);
        etConsentMethod = findViewById(R.id.etConsentMethod);
        tilTitle = findViewById(R.id.tilTitle);
        tilTopic = findViewById(R.id.tilTopic);
        tilContent = findViewById(R.id.tilContent);
        tilArea = findViewById(R.id.tilArea);
        tilDesiredTime = findViewById(R.id.tilDesiredTime);
        tilSource = findViewById(R.id.tilSource);
        tilElderlyName = findViewById(R.id.tilElderlyName);
        tilContact = findViewById(R.id.tilContact);
        tilConsentMethod = findViewById(R.id.tilConsentMethod);
        cbConsent = findViewById(R.id.cbConsent);
        tvConsentError = findViewById(R.id.tvConsentError);
        tvDraftHint = findViewById(R.id.tvDraftHint);
        btnSubmit = findViewById(R.id.btnSubmitNeed);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(NeedFormViewModel.Ui s) {
        if (s.loading) {
            scrollForm.setVisibility(View.GONE);
            stateView.showLoading();
            return;
        }
        if (s.error != null) {
            scrollForm.setVisibility(View.GONE);
            stateView.showError(getString(R.string.form_cannot_open), s.error,
                    getString(R.string.action_back), v -> finish());
            return;
        }
        stateView.hide();
        scrollForm.setVisibility(View.VISIBLE);

        if (s.editing && s.existing != null) {
            toolbar.setTitle(getString(R.string.form_title_edit, s.existing.getCaseCode()));
            etCaseCode.setText(s.existing.getCaseCode());
            btnSaveDraft.setVisibility(View.GONE);
            tvDraftHint.setVisibility(View.GONE);
            showEditNotice(s.existing);
        } else {
            toolbar.setTitle(R.string.form_title_create);
            etCaseCode.setText(R.string.form_case_code_auto);
            tvFormNotice.setVisibility(View.GONE);
            maybeRestoreDraft();
        }

        btnSubmit.setEnabled(!s.busy);
        btnSaveDraft.setEnabled(!s.busy);
        if (s.busy) btnSubmit.setText(R.string.form_sending);
        else btnSubmit.setText(s.editing ? R.string.form_resubmit : R.string.form_submit);
    }

    private void showEditNotice(NeedSummary need) {
        String text = null;
        if (need.isNeedsRevision() && !TextUtils.isEmpty(need.getReviewNote())) {
            text = getString(R.string.form_notice_revision, need.getReviewNote());
        } else if (Constants.STATUS_OPEN.equals(need.getStatus())) {
            text = getString(R.string.form_notice_open_edit);
        }
        tvFormNotice.setVisibility(text != null ? View.VISIBLE : View.GONE);
        tvFormNotice.setText(text);
    }

    private void fillFromExisting(NeedSummary s, @Nullable NeedPrivateDetails p) {
        etTitle.setText(s.getTitle());
        etTopic.setText(s.getTopic());
        etContent.setText(s.getContent());
        etArea.setText(s.getArea());
        etDesiredTime.setText(s.getDesiredTime());
        etSource.setText(s.getSource());
        cbConsent.setChecked(s.isConsentRecorded());
        etConsentMethod.setText(s.getConsentMethod());
        if (p != null) {
            etElderlyName.setText(p.getElderlyName());
            etContact.setText(p.getContact());
            etAddress.setText(p.getAddress());
        }
    }

    /** Lần đầu mở form tạo mới: nếu có bản nháp thì khôi phục phần tóm tắt. */
    private void maybeRestoreDraft() {
        if (draftChecked) return;
        draftChecked = true;
        NeedForm draft = draftStore.load();
        if (draft == null) return;
        etTitle.setText(draft.title);
        etTopic.setText(draft.topic);
        etContent.setText(draft.content);
        etArea.setText(draft.area);
        etDesiredTime.setText(draft.desiredTime);
        etSource.setText(draft.source);
        cbConsent.setChecked(draft.consentRecorded);
        etConsentMethod.setText(draft.consentMethod);
        Snackbar.make(scrollForm, R.string.form_draft_restored, Snackbar.LENGTH_LONG)
                .setAction(R.string.form_draft_discard, v -> {
                    draftStore.clear();
                    clearSummaryFields();
                })
                .show();
    }

    private void clearSummaryFields() {
        etTitle.setText("");
        etTopic.setText("");
        etContent.setText("");
        etArea.setText("");
        etDesiredTime.setText("");
        etSource.setText("");
        cbConsent.setChecked(false);
        etConsentMethod.setText("");
    }

    private void showTopicPicker() {
        String[] topics = getResources().getStringArray(R.array.need_topics);
        String current = etTopic.getText().toString();
        int checked = -1;
        for (int i = 0; i < topics.length; i++) if (topics[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.form_topic)
                .setSingleChoiceItems(topics, checked, (dialog, which) -> {
                    etTopic.setText(topics[which]);
                    tilTopic.setError(null);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    // =====================================================================
    // Gửi và lưu nháp
    // =====================================================================

    private NeedForm collectForm() {
        NeedForm f = new NeedForm();
        f.title = text(etTitle);
        f.topic = text(etTopic);
        f.content = text(etContent);
        f.area = text(etArea);
        f.desiredTime = text(etDesiredTime);
        f.source = text(etSource);
        f.consentRecorded = cbConsent.isChecked();
        f.consentMethod = text(etConsentMethod);
        f.elderlyName = text(etElderlyName);
        f.contact = text(etContact);
        f.address = text(etAddress);
        return f;
    }

    private void submit() {
        NeedForm form = collectForm();
        if (!validate(form)) return;
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Snackbar.make(scrollForm, R.string.form_offline, Snackbar.LENGTH_LONG).show();
            return;
        }
        viewModel.submit(form);
    }

    private void saveDraft() {
        draftStore.save(collectForm());
        Toast.makeText(this, R.string.form_draft_saved, Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * Kiểm tra bắt buộc nhập và kiểm tra phần tóm tắt không lộ thông tin nhận diện.
     * Lỗi đầu tiên được cuộn tới để người dùng thấy ngay.
     */
    private boolean validate(NeedForm f) {
        View firstError = null;
        clearErrors();

        if (f.title.length() < 5) firstError = mark(firstError, tilTitle, R.string.form_error_title);
        if (f.topic.isEmpty()) firstError = mark(firstError, tilTopic, R.string.form_error_topic);
        if (f.content.length() < 10) firstError = mark(firstError, tilContent, R.string.form_error_content);
        if (f.area.isEmpty()) firstError = mark(firstError, tilArea, R.string.form_error_area);
        if (f.desiredTime.isEmpty()) firstError = mark(firstError, tilDesiredTime, R.string.form_error_required);
        if (f.source.isEmpty()) firstError = mark(firstError, tilSource, R.string.form_error_required);

        // Tóm tắt không được chứa số điện thoại hoặc tên người cao tuổi (R03)
        TextInputLayout[] summaryFields = {tilTitle, tilContent, tilArea, tilDesiredTime, tilSource};
        String[] summaryValues = {f.title, f.content, f.area, f.desiredTime, f.source};
        for (int i = 0; i < summaryFields.length; i++) {
            if (summaryFields[i].getError() != null) continue;
            if (PrivacyGuard.containsPhoneNumber(summaryValues[i])) {
                firstError = mark(firstError, summaryFields[i], R.string.form_error_phone_in_summary);
            } else if (PrivacyGuard.containsName(summaryValues[i], f.elderlyName)) {
                firstError = mark(firstError, summaryFields[i], R.string.form_error_name_in_summary);
            }
        }

        if (f.elderlyName.length() < 2) firstError = mark(firstError, tilElderlyName, R.string.form_error_required);
        if (f.contact.isEmpty()) firstError = mark(firstError, tilContact, R.string.form_error_required);

        if (!f.consentRecorded) {
            tvConsentError.setVisibility(View.VISIBLE);
            if (firstError == null) firstError = cbConsent;
        }
        if (f.consentMethod.isEmpty()) firstError = mark(firstError, tilConsentMethod, R.string.form_error_consent_method);

        if (firstError != null) {
            final View target = firstError;
            scrollForm.post(() -> scrollForm.smoothScrollTo(0, Math.max(0, topWithinScroll(target) - 48)));
            return false;
        }
        return true;
    }

    private View mark(@Nullable View firstError, TextInputLayout til, int messageRes) {
        til.setError(getString(messageRes));
        return firstError != null ? firstError : til;
    }

    private void clearErrors() {
        TextInputLayout[] all = {tilTitle, tilTopic, tilContent, tilArea, tilDesiredTime, tilSource,
                tilElderlyName, tilContact, tilConsentMethod};
        for (TextInputLayout til : all) til.setError(null);
        tvConsentError.setVisibility(View.GONE);
    }

    /** Vị trí của view tính từ đầu vùng cuộn (view có thể nằm trong include lồng nhau). */
    private int topWithinScroll(View view) {
        int top = 0;
        View v = view;
        while (v != null && v != scrollForm) {
            top += v.getTop();
            if (!(v.getParent() instanceof View)) break;
            v = (View) v.getParent();
        }
        return top;
    }

    private static void clearErrorWhileTyping(EditText editText, TextInputLayout til) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (til.getError() != null) til.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private static String text(EditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }
}
