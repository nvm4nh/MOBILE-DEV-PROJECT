package com.seniortribe.app.fragments;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.LoginActivity;
import com.seniortribe.app.activities.SessionGateActivity;
import com.seniortribe.app.dialogs.ChangeEmailDialog;
import com.seniortribe.app.dialogs.ChangePasswordDialog;
import com.seniortribe.app.models.AccountOrganization;
import com.seniortribe.app.models.User;
import com.seniortribe.app.viewmodels.AccountProfileViewModel;

public class ProfileFragment extends Fragment {

    private AccountProfileViewModel model;
    private AccountProfileViewModel.State current;
    private FirebaseAuth.AuthStateListener authListener;

    private View root;
    private boolean routed;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        root = view;

        model = new ViewModelProvider(this)
                .get(AccountProfileViewModel.class);

        MaterialToolbar toolbar =
                view.findViewById(R.id.st_profile_toolbar);

        if (requireActivity() instanceof SessionGateActivity) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription(
                    R.string.st_profile_back);

            toolbar.setNavigationOnClickListener(v ->
                    requireActivity()
                            .getOnBackPressedDispatcher()
                            .onBackPressed());
        }

        view.findViewById(R.id.st_profile_edit_name)
                .setOnClickListener(v -> openEdit("displayName"));

        view.findViewById(R.id.st_profile_edit_area)
                .setOnClickListener(v -> openEdit("area"));

        view.findViewById(R.id.st_profile_edit_phone)
                .setOnClickListener(v -> openEdit("phone"));

        view.findViewById(R.id.st_profile_reload)
                .setOnClickListener(v -> model.refresh());

        view.findViewById(R.id.st_profile_org_reload)
                .setOnClickListener(v -> model.refreshOrganizations());

        view.findViewById(R.id.st_profile_sign_out)
                .setOnClickListener(v -> model.signOut());

        view.findViewById(R.id.st_profile_copy_uid)
                .setOnClickListener(v -> copyUid());

        // E08 · Cỡ chữ & giao diện, E09 · Khẩn cấp
        view.findViewById(R.id.st_profile_display)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(),
                        com.seniortribe.app.activities.DisplaySettingsActivity.class)));
        view.findViewById(R.id.st_profile_sos)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(),
                        com.seniortribe.app.activities.SosActivity.class)));

        view.findViewById(R.id.st_profile_change_password)
                .setOnClickListener(v -> {
                    if (!canOpenSecurityDialog()) return;

                    if (getChildFragmentManager()
                            .findFragmentByTag("change_password") == null) {
                        new ChangePasswordDialog().show(
                                getChildFragmentManager(),
                                "change_password");
                    }
                });

        view.findViewById(R.id.st_profile_change_email)
                .setOnClickListener(v -> {
                    if (!canOpenSecurityDialog()) return;

                    if (getChildFragmentManager()
                            .findFragmentByTag("change_email") == null) {
                        new ChangeEmailDialog().show(
                                getChildFragmentManager(),
                                "change_email");
                    }
                });

        model.state().observe(
                getViewLifecycleOwner(), this::render);
    }

    private boolean canOpenSecurityDialog() {
        return current != null
                && current.profile != null
                && current.passwordProvider
                && !current.loading
                && !current.saving
                && !getChildFragmentManager().isStateSaved();
    }

    private void openEdit(String field) {
        if (getChildFragmentManager().isStateSaved()) return;

        if (getChildFragmentManager()
                .findFragmentByTag("profile_edit") != null) {
            return;
        }

        if (!model.beginEdit(field)) return;

        EditFieldDialog.newInstance(field).show(
                getChildFragmentManager(), "profile_edit");
    }

    @Override
    public void onStart() {
        super.onStart();

        authListener = auth -> {
            if (model != null) model.validateSession();
        };

        FirebaseAuth.getInstance()
                .addAuthStateListener(authListener);
    }

    @Override
    public void onStop() {
        if (authListener != null) {
            FirebaseAuth.getInstance()
                    .removeAuthStateListener(authListener);
        }

        authListener = null;
        super.onStop();
    }

    private void render(AccountProfileViewModel.State state) {
        if (root == null) return;

        current = state;

        if (state.signedOut) {
            routeToLogin();
            return;
        }

        boolean busy = state.loading || state.saving;
        boolean hasProfile = state.profile != null;

        root.findViewById(R.id.st_profile_progress)
                .setVisibility(busy ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.st_profile_form)
                .setVisibility(hasProfile ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.st_profile_reload)
                .setEnabled(!busy);

        root.findViewById(R.id.st_profile_sign_out)
                .setEnabled(!state.saving);

        root.findViewById(R.id.st_profile_copy_uid)
                .setEnabled(hasProfile);

        int[] editButtons = {
                R.id.st_profile_edit_name,
                R.id.st_profile_edit_area,
                R.id.st_profile_edit_phone
        };

        for (int id : editButtons) {
            root.findViewById(id).setEnabled(!busy && hasProfile);
        }

        TextView message =
                root.findViewById(R.id.st_profile_message);

        int resource = state.error != 0
                ? state.error : state.notice;

        message.setText(resource == 0 ? "" : getString(resource));
        message.setVisibility(
                resource == 0 ? View.GONE : View.VISIBLE);

        message.setTextColor(ContextCompat.getColor(
                requireContext(),
                state.error != 0
                        ? R.color.st_auth_error
                        : R.color.st_auth_text));

        if (hasProfile) {
            User profile = state.profile;

            setText(R.id.st_profile_header_name,
                    profile.getDisplayName());

            setText(R.id.st_profile_header_email,
                    profile.getEmail());

            setText(R.id.st_profile_name,
                    profile.getDisplayName());

            setText(R.id.st_profile_area,
                    provided(profile.getArea()));

            setText(R.id.st_profile_phone,
                    provided(profile.getPhone()));

            setText(R.id.st_profile_email,
                    profile.getEmail());

            setText(R.id.st_profile_uid,
                    profile.getUid());
        }

        View changePassword =
                root.findViewById(R.id.st_profile_change_password);

        changePassword.setVisibility(
                state.passwordProvider ? View.VISIBLE : View.GONE);

        changePassword.setEnabled(!busy && hasProfile);

        View changeEmail =
                root.findViewById(R.id.st_profile_change_email);

        changeEmail.setVisibility(
                state.passwordProvider ? View.VISIBLE : View.GONE);

        changeEmail.setEnabled(!busy && hasProfile);

        ((TextView) root.findViewById(R.id.st_profile_email_note))
                .setText(state.passwordProvider
                        ? R.string.st_email_profile_note
                        : R.string.st_email_google);

        ((TextView) root.findViewById(R.id.st_profile_provider_note))
                .setText(state.passwordProvider
                        ? R.string.st_profile_password_note
                        : R.string.st_profile_google_password_note);

        renderOrganizations(state);
    }

    private void setText(int id, String value) {
        ((TextView) root.findViewById(id))
                .setText(value == null ? "" : value);
    }

    private String provided(String value) {
        return value == null || value.trim().isEmpty()
                ? getString(R.string.st_profile_not_provided)
                : value;
    }

    private void renderOrganizations(
            AccountProfileViewModel.State state) {

        LinearLayout container =
                root.findViewById(R.id.st_profile_organizations);

        container.removeAllViews();

        root.findViewById(R.id.st_profile_org_progress)
                .setVisibility(
                        state.orgLoading ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.st_profile_org_reload)
                .setEnabled(!state.orgLoading);

        TextView note =
                root.findViewById(R.id.st_profile_org_message);

        note.setVisibility(
                state.orgLoading ? View.GONE : View.VISIBLE);

        if (state.orgLoading) return;

        if (state.orgError != 0) {
            note.setText(getString(
                    R.string.st_profile_org_error,
                    getString(state.orgError)));
            return;
        }

        if (state.organizations.isEmpty()) {
            note.setText(R.string.st_profile_no_organizations);
            return;
        }

        note.setVisibility(View.GONE);

        for (AccountOrganization item : state.organizations) {
            View card = getLayoutInflater().inflate(
                    R.layout.item_profile_organization,
                    container, false);

            ((TextView) card.findViewById(R.id.st_org_name))
                    .setText(item.name);

            ((TextView) card.findViewById(R.id.st_org_id))
                    .setText(getString(
                            R.string.st_profile_org_id, item.id));

            ((TextView) card.findViewById(R.id.st_org_membership))
                    .setText(item.active
                            ? R.string.st_profile_membership_active
                            : R.string.st_profile_membership_inactive);

            Chip badge =
                    card.findViewById(R.id.st_org_verification);

            badge.setText(
                    item.verification
                            == AccountOrganization.Verification.VERIFIED
                            ? R.string.st_profile_verified
                            : item.verification
                            == AccountOrganization.Verification.UNVERIFIED
                            ? R.string.st_profile_unverified
                            : R.string.st_profile_verification_unknown);

            if (item.verification
                    == AccountOrganization.Verification.VERIFIED) {

                badge.setChipBackgroundColor(
                        ColorStateList.valueOf(
                                ContextCompat.getColor(
                                        requireContext(),
                                        R.color.primary)));

                badge.setTextColor(ContextCompat.getColor(
                        requireContext(), R.color.on_primary));
            }

            ChipGroup roles =
                    card.findViewById(R.id.st_org_roles);

            for (String role : item.roles) {
                Chip chip = new Chip(requireContext());

                chip.setText(roleName(role));
                chip.setCheckable(false);
                chip.setClickable(false);

                if (!item.active) chip.setAlpha(0.65f);

                roles.addView(chip);
            }

            StringBuilder lines = new StringBuilder();

            append(lines, item.area, R.string.st_profile_org_area);
            append(lines, item.address, R.string.st_profile_org_address);
            append(lines, item.phone, R.string.st_profile_org_phone);

            TextView details =
                    card.findViewById(R.id.st_org_details);

            details.setText(lines.toString());
            details.setVisibility(
                    lines.length() == 0 ? View.GONE : View.VISIBLE);

            TextView warning =
                    card.findViewById(R.id.st_org_warning);

            warning.setVisibility(
                    item.details == AccountOrganization.Details.LOADED
                            ? View.GONE : View.VISIBLE);

            warning.setText(
                    item.details == AccountOrganization.Details.MISSING
                            ? R.string.st_profile_org_missing
                            : item.details
                            == AccountOrganization.Details.INVALID
                            ? R.string.st_profile_org_invalid
                            : R.string.st_profile_org_unavailable);

            container.addView(card);
        }
    }

    private void append(
            StringBuilder lines, String value, int resource) {
        if (value == null || value.isEmpty()) return;

        if (lines.length() > 0) lines.append('\n');

        lines.append(getString(resource, value));
    }

    private int roleName(String value) {
        switch (value) {
            case "RECORDER":
                return R.string.st_profile_role_recorder;

            case "REVIEWER":
                return R.string.st_profile_role_reviewer;

            case "REPRESENTATIVE":
                return R.string.st_profile_role_representative;

            default:
                return R.string.st_profile_role_unknown;
        }
    }

    private void copyUid() {
        if (current == null || current.profile == null) return;

        ClipboardManager clipboard =
                (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard == null) return;

        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.st_register_uid_label),
                current.profile.getUid()));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                    requireContext(),
                    R.string.st_register_uid_copied,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void routeToLogin() {
        if (routed || !isAdded()) return;

        routed = true;

        Intent intent =
                new Intent(requireContext(), LoginActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        root = null;
        current = null;

        super.onDestroyView();
    }

    public static final class EditFieldDialog extends DialogFragment {

        private AccountProfileViewModel model;
        private String field;

        private TextInputLayout inputLayout;
        private TextInputEditText input;
        private ProgressBar progress;

        public static EditFieldDialog newInstance(String field) {
            EditFieldDialog dialog = new EditFieldDialog();

            Bundle arguments = new Bundle();
            arguments.putString("field", field);

            dialog.setArguments(arguments);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            model = new ViewModelProvider(requireParentFragment())
                    .get(AccountProfileViewModel.class);

            field = requireArguments().getString("field", "");

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

            int label = "displayName".equals(field)
                    ? R.string.st_profile_name
                    : "area".equals(field)
                    ? R.string.st_profile_area
                    : R.string.st_profile_phone_label;

            inputLayout = new TextInputLayout(context);
            inputLayout.setHint(label);
            inputLayout.setBoxBackgroundMode(
                    TextInputLayout.BOX_BACKGROUND_OUTLINE);

            if ("area".equals(field)) {
                inputLayout.setHelperText(
                        getString(R.string.st_profile_area_hint));
            } else if ("phone".equals(field)) {
                inputLayout.setHelperText(
                        getString(R.string.st_profile_phone_hint));
            }

            input = new TextInputEditText(context);
            input.setSaveEnabled(false);
            input.setSaveFromParentEnabled(false);

            input.setInputType(
                    "phone".equals(field)
                            ? InputType.TYPE_CLASS_PHONE
                            : InputType.TYPE_CLASS_TEXT
                            | ("displayName".equals(field)
                            ? InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                            | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                            : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));

            input.setSingleLine(true);
            input.setText(model.editValue());

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(
                        CharSequence s, int start, int before, int count) {
                    model.setEditValue(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) { }
            });

            inputLayout.addView(
                    input,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

            body.addView(inputLayout);

            progress = new ProgressBar(context);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(32), dp(32));

            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.topMargin = dp(16);

            progress.setContentDescription(
                    getString(R.string.st_auth_loading));

            body.addView(progress, params);

            AlertDialog dialog = builder
                    .setTitle(label)
                    .setView(scroll)
                    .setPositiveButton(
                            R.string.st_profile_save_field, null)
                    .setNegativeButton(
                            R.string.st_profile_cancel,
                            (d, which) -> model.cancelEdit())
                    .create();

            model.state().observe(this, this::renderEdit);

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
                    .setOnClickListener(v -> model.saveEdit());

            AccountProfileViewModel.State state =
                    model.state().getValue();

            if (state != null) renderEdit(state);
        }

        private void renderEdit(
                AccountProfileViewModel.State state) {

            AlertDialog dialog = (AlertDialog) getDialog();

            if (dialog == null || inputLayout == null
                    || dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE) == null) {
                return;
            }

            if (state.signedOut
                    || !field.equals(state.editingField)) {
                dismissAllowingStateLoss();
                return;
            }

            setCancelable(!state.saving);
            dialog.setCanceledOnTouchOutside(!state.saving);

            input.setEnabled(!state.saving);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setEnabled(!state.saving);

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setEnabled(!state.saving);

            progress.setVisibility(
                    state.saving ? View.VISIBLE : View.GONE);

            inputLayout.setError(
                    state.editError == 0
                            ? null : getString(state.editError));
        }

        @Override
        public void onCancel(@NonNull DialogInterface dialog) {
            if (model != null) model.cancelEdit();

            super.onCancel(dialog);
        }

        private int dp(int value) {
            return Math.round(
                    value * getResources()
                            .getDisplayMetrics().density);
        }

        @Override
        public void onDestroyView() {
            inputLayout = null;
            input = null;
            progress = null;

            super.onDestroyView();
        }
    }
}