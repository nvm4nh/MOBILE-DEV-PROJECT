package com.seniortribe.app.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.auth.ProfileErrors;
import com.seniortribe.app.auth.ProfileInput;
import com.seniortribe.app.viewmodels.ChangePasswordViewModel;
import java.util.function.Consumer;

public final class ChangePasswordDialog extends DialogFragment {
    private ChangePasswordViewModel model;
    private View content;
    private TextInputEditText current, next, repeated;

    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(ChangePasswordViewModel.class);
        content = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        current = bind(R.id.st_password_current, model.current(), model::setCurrent);
        next = bind(R.id.st_password_next, model.next(), model::setNext);
        repeated = bind(R.id.st_password_repeated, model.repeated(), model::setRepeated);
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_SeniorTribe_ProfileDialog)
                .setTitle(R.string.st_profile_change_password)
                .setView(content)
                .setPositiveButton(R.string.st_profile_change_password, null)
                .setNegativeButton(R.string.st_profile_cancel, null)
                .create();
        model.state().observe(this, this::render);
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (model.state().getValue().stage == ChangePasswordViewModel.Stage.SUCCESS) {
                dismiss();
            } else {
                hideKeyboard();
                model.submit();
            }
        });

        render(model.state().getValue());
    }

    private void render(ChangePasswordViewModel.State state) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null || content == null
                || dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null) {
            return;
        }

        if (state.stage == ChangePasswordViewModel.Stage.SIGNED_OUT) {
            dismiss();
            return;
        }

        boolean busy = state.stage == ChangePasswordViewModel.Stage.BUSY;
        boolean success = state.stage == ChangePasswordViewModel.Stage.SUCCESS;

        setCancelable(!busy);
        dialog.setCanceledOnTouchOutside(!busy);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                success ? R.string.st_profile_close
                        : R.string.st_profile_change_password);

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(
                success ? View.GONE : View.VISIBLE);

        content.findViewById(R.id.st_password_progress).setVisibility(
                busy ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.st_password_fields).setVisibility(
                success ? View.GONE : View.VISIBLE);

        current.setEnabled(!busy);
        next.setEnabled(!busy);
        repeated.setEnabled(!busy);

        setText(current, model.current());
        setText(next, model.next());
        setText(repeated, model.repeated());

        boolean incorrectCurrent =
                state.error == R.string.st_profile_password_incorrect;

        ((TextInputLayout) content.findViewById(
                R.id.st_password_current_layout)).setError(
                incorrectCurrent ? getString(state.error)
                        : state.problem == ProfileInput.Problem.CURRENT_PASSWORD
                        ? getString(ProfileErrors.input(state.problem)) : null);

        fieldError(
                R.id.st_password_next_layout,
                state.problem == ProfileInput.Problem.NEW_PASSWORD
                        || state.problem == ProfileInput.Problem.PASSWORD_UNCHANGED,
                state.problem);

        fieldError(
                R.id.st_password_repeated_layout,
                state.problem == ProfileInput.Problem.PASSWORD_MATCH,
                state.problem);

        TextView note = content.findViewById(R.id.st_password_message);

        boolean hasError = state.error != 0
                || state.problem != ProfileInput.Problem.NONE;

        int message = success ? R.string.st_profile_password_changed
                : state.error != 0 ? state.error
                : state.problem != ProfileInput.Problem.NONE
                ? ProfileErrors.input(state.problem)
                : busy ? R.string.st_auth_loading
                : R.string.st_profile_password_dialog_note;

        note.setText(message);
        note.setTextColor(ContextCompat.getColor(
                requireContext(),
                hasError ? R.color.st_auth_error : R.color.st_auth_text));

        if (hasError) {
            // Show the message even when a lower input previously had focus.
            View renderedContent = content;
            renderedContent.post(() -> {
                if (content != renderedContent || !isAdded()) return;
                ((ScrollView) renderedContent).scrollTo(0, 0);
            });
        }
    }

    private void hideKeyboard() {
        View focused = getDialog() == null
                ? null : getDialog().getCurrentFocus();

        InputMethodManager keyboard =
                (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);

        if (keyboard != null && content != null) {
            keyboard.hideSoftInputFromWindow(
                    focused == null
                            ? content.getWindowToken()
                            : focused.getWindowToken(),
                    0);
        }

        if (focused != null) focused.clearFocus();
    }

    private void fieldError(
            int id, boolean applies, ProfileInput.Problem problem) {
        ((TextInputLayout) content.findViewById(id)).setError(
                applies ? getString(ProfileErrors.input(problem)) : null);
    }

    private TextInputEditText bind(
            int id, String initial, Consumer<String> changed) {
        TextInputEditText field = content.findViewById(id);
        field.setText(initial);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                changed.accept(s.toString());
            }

            @Override public void afterTextChanged(Editable s) { }
        });
        return field;
    }

    private static void setText(TextInputEditText field, String value) {
        if (!value.contentEquals(
                field.getText() == null ? "" : field.getText())) {
            field.setText(value);
        }
    }

    @Override public void onDestroyView() {
        content = null;
        current = null;
        next = null;
        repeated = null;
        super.onDestroyView();
    }
}