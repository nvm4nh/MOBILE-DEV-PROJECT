package com.seniortribe.app.dialogs;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthException;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.auth.AuthErrors;
import com.seniortribe.app.auth.AuthRepository;
import com.seniortribe.app.auth.InputValidator;

import java.util.function.Consumer;

public final class ChangeEmailDialog extends DialogFragment {

    private EmailModel model;
    private LinearLayout fields;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextView message;
    private ProgressBar progress;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(EmailModel.class);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(
                        requireContext(),
                        R.style.ThemeOverlay_SeniorTribe_ProfileDialog);

        Context context = builder.getContext();

        ScrollView scroll = new ScrollView(context);
        scroll.setSaveEnabled(false);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(12), dp(24), dp(8));
        scroll.addView(body);

        message = new TextView(context);
        message.setTextSize(16);
        message.setAccessibilityLiveRegion(
                View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(message);

        fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        body.addView(fields);

        email = addField(
                context,
                R.string.st_email_new,
                false,
                model.email,
                value -> {
                    if (model.state.getValue() == Stage.FORM) {
                        model.email = value;
                    }
                });

        password = addField(
                context,
                R.string.st_profile_current_password,
                true,
                model.password,
                value -> {
                    if (model.state.getValue() == Stage.FORM) {
                        model.password = value;
                    }
                });

        progress = new ProgressBar(context);
        progress.setContentDescription(
                getString(R.string.st_auth_loading));

        LinearLayout.LayoutParams spinner =
                new LinearLayout.LayoutParams(dp(36), dp(36));
        spinner.gravity = Gravity.CENTER_HORIZONTAL;
        spinner.topMargin = dp(16);

        body.addView(progress, spinner);

        AlertDialog dialog = builder
                .setTitle(R.string.st_email_change)
                .setView(scroll)
                .setPositiveButton(R.string.st_email_send, null)
                .setNegativeButton(R.string.st_profile_cancel, null)
                .create();

        model.state.observe(this, ignored -> render());

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (model.state.getValue() == Stage.SENT) {
                        model.signOut();
                    } else {
                        model.submit();
                    }
                });

        render();
    }

    private void render() {
        AlertDialog dialog = (AlertDialog) getDialog();

        if (dialog == null || message == null
                || dialog.getButton(
                AlertDialog.BUTTON_POSITIVE) == null) {
            return;
        }

        Stage stage = model.state.getValue();

        if (stage == Stage.CLOSED) {
            dismissAllowingStateLoss();
            return;
        }

        boolean busy = stage == Stage.BUSY;
        boolean sent = stage == Stage.SENT;

        setCancelable(!busy);
        dialog.setCanceledOnTouchOutside(!busy);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setEnabled(!busy);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                sent ? R.string.st_register_sign_out
                        : R.string.st_email_send);

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setEnabled(!busy);

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(
                sent ? R.string.st_profile_close
                        : R.string.st_profile_cancel);

        fields.setVisibility(sent ? View.GONE : View.VISIBLE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);

        email.setEnabled(!busy);
        password.setEnabled(!busy);

        CharSequence passwordText = password.getText() == null
                ? "" : password.getText();

        if (!model.password.contentEquals(passwordText)) {
            password.setText(model.password);
        }

        if (sent) {
            message.setText(getString(
                    R.string.st_email_sent, model.email));
        } else {
            message.setText(
                    model.error != 0
                            ? model.error
                            : busy
                            ? R.string.st_auth_loading
                            : R.string.st_email_intro);
        }

        message.setTextColor(ContextCompat.getColor(
                requireContext(),
                model.error != 0
                        ? R.color.st_auth_error
                        : R.color.st_auth_text));
    }

    private TextInputEditText addField(
            Context context,
            int label,
            boolean secret,
            String initial,
            Consumer<String> changed) {

        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(label);
        layout.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE);

        if (secret) {
            layout.setEndIconMode(
                    TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }

        TextInputEditText input = new TextInputEditText(context);

        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (secret
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));

        input.setSingleLine(true);
        input.setSaveEnabled(false);
        input.setSaveFromParentEnabled(false);

        if (!secret) {
            input.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(254)
            });
        }

        input.setText(initial);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                changed.accept(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        layout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);

        fields.addView(layout, params);
        return input;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        fields = null;
        email = null;
        password = null;
        message = null;
        progress = null;

        super.onDestroyView();
    }

    public enum Stage {
        FORM,
        BUSY,
        SENT,
        CLOSED
    }

    public static final class EmailModel extends AndroidViewModel {

        final MutableLiveData<Stage> state =
                new MutableLiveData<>(Stage.FORM);

        private final AuthRepository repository;
        private final String uid;

        String email = "";
        String password = "";
        int error;

        private int generation;

        public EmailModel(@NonNull Application application) {
            super(application);

            repository = ((SeniorTribeApplication) application)
                    .getAuthRepository();

            uid = repository.currentUser() == null
                    ? "" : repository.currentUser().getUid();
        }

        void submit() {
            if (state.getValue() != Stage.FORM) return;

            if (!repository.isCurrentUser(uid)) {
                state.setValue(Stage.CLOSED);
                return;
            }

            email = InputValidator.normalizeEmail(email);
            error = 0;

            final int request = ++generation;
            state.setValue(Stage.BUSY);

            repository.requestEmailChange(uid, email, password)
                    .addOnCompleteListener(task -> {
                        if (request != generation) return;

                        password = "";

                        if (!repository.isCurrentUser(uid)) {
                            state.setValue(Stage.CLOSED);
                        } else if (task.isSuccessful()) {
                            state.setValue(Stage.SENT);
                        } else {
                            error = errorMessage(task.getException());
                            state.setValue(Stage.FORM);
                        }
                    });
        }

        void signOut() {
            if (repository.isCurrentUser(uid)) {
                ((SeniorTribeApplication) getApplication())
                        .clearActiveAccess();

                repository.signOut();
            }

            state.setValue(Stage.CLOSED);
        }

        private static int errorMessage(Throwable error) {
            if (error instanceof FirebaseAuthException) {
                switch (((FirebaseAuthException) error).getErrorCode()) {
                    case "ERROR_WRONG_PASSWORD":
                    case "ERROR_INVALID_CREDENTIAL":
                    case "ERROR_INVALID_LOGIN_CREDENTIALS":
                        return R.string.st_profile_password_incorrect;

                    case "ERROR_INVALID_EMAIL":
                        return R.string.st_email_invalid;

                    case "ERROR_EMAIL_ALREADY_IN_USE":
                        return R.string.st_email_used;

                    case "ERROR_REQUIRES_RECENT_LOGIN":
                        return R.string.st_profile_password_recent;

                    case "ERROR_OPERATION_NOT_ALLOWED":
                        return R.string.st_email_unavailable;

                    default:
                        break;
                }
            }

            String reason = error == null
                    ? "" : error.getMessage();

            if ("EMAIL_INVALID".equals(reason)) {
                return R.string.st_email_invalid;
            }

            if ("EMAIL_UNCHANGED".equals(reason)) {
                return R.string.st_email_same;
            }

            if ("PASSWORD_REQUIRED".equals(reason)) {
                return R.string.st_profile_current_password_error;
            }

            if ("EMAIL_PROVIDER".equals(reason)) {
                return R.string.st_email_google;
            }

            return AuthErrors.message(error);
        }

        @Override
        protected void onCleared() {
            ++generation;
            email = "";
            password = "";

            super.onCleared();
        }
    }
}