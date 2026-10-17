package com.seniortribe.app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.LoginActivity;
import com.seniortribe.app.activities.SessionGateActivity;
import com.seniortribe.app.auth.ProfileErrors;
import com.seniortribe.app.auth.ProfileInput;
import com.seniortribe.app.dialogs.ChangePasswordDialog;
import com.seniortribe.app.models.AccountOrganization;
import com.seniortribe.app.viewmodels.AccountProfileViewModel;
import java.util.function.Consumer;

/** Same F03 screen for the Main profile tab and accounts waiting for membership. */
public class ProfileFragment extends Fragment {
    private AccountProfileViewModel model;
    private AccountProfileViewModel.State current;
    private FirebaseAuth.AuthStateListener authListener;
    private View root;
    private TextInputEditText name, area;
    private boolean routed;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        model = new ViewModelProvider(this).get(AccountProfileViewModel.class);
        name = bind(R.id.st_profile_name, model.name(), model::setName);
        area = bind(R.id.st_profile_area, model.area(), model::setArea);
        MaterialToolbar toolbar = view.findViewById(R.id.st_profile_toolbar);
        if (requireActivity() instanceof SessionGateActivity) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription(R.string.st_profile_back);
            toolbar.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }
        view.findViewById(R.id.st_profile_save).setOnClickListener(v -> model.save());
        view.findViewById(R.id.st_profile_reload).setOnClickListener(v -> model.refresh());
        view.findViewById(R.id.st_profile_org_reload).setOnClickListener(v -> model.refreshOrganizations());
        view.findViewById(R.id.st_profile_sign_out).setOnClickListener(v -> model.signOut());
        view.findViewById(R.id.st_profile_copy_uid).setOnClickListener(v -> copyUid());
        view.findViewById(R.id.st_profile_change_password).setOnClickListener(v -> {
            if (current != null && current.passwordProvider
                    && getChildFragmentManager().findFragmentByTag("change_password") == null) {
                new ChangePasswordDialog().show(getChildFragmentManager(), "change_password");
            }
        });
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override public void onStart() {
        super.onStart();
        authListener = auth -> { if (model != null) model.validateSession(); };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    @Override public void onStop() {
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null;
        super.onStop();
    }

    private void render(AccountProfileViewModel.State state) {
        if (root == null) return;
        current = state;
        if (state.signedOut) { routeToLogin(); return; }
        boolean busy = state.loading || state.saving;
        root.findViewById(R.id.st_profile_progress).setVisibility(busy ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.st_profile_form).setVisibility(state.profile == null ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.st_profile_save).setEnabled(!busy && state.profile != null);
        root.findViewById(R.id.st_profile_reload).setEnabled(!busy);
        root.findViewById(R.id.st_profile_sign_out).setEnabled(!state.saving);
        name.setEnabled(!busy); area.setEnabled(!busy);

        // Draft setters do not emit State on each keystroke. A replayed State can
        // be older than the current RAM draft after view recreation or resume.
        setText(name, model.name()); setText(area, model.area());

        ((TextInputLayout) root.findViewById(R.id.st_profile_name_layout)).setError(
                state.problem == ProfileInput.Problem.NAME ? getString(ProfileErrors.input(state.problem)) : null);
        ((TextInputLayout) root.findViewById(R.id.st_profile_area_layout)).setError(
                state.problem == ProfileInput.Problem.AREA ? getString(ProfileErrors.input(state.problem)) : null);
        TextView message = root.findViewById(R.id.st_profile_message);
        int resource = state.error != 0 ? state.error : state.notice;
        message.setText(resource == 0 ? "" : getString(resource));
        message.setVisibility(resource == 0 ? View.GONE : View.VISIBLE);
        message.setTextColor(ContextCompat.getColor(requireContext(), state.error == 0 ? R.color.primary : R.color.urgency_high));
        if (state.profile != null) {
            ((TextView) root.findViewById(R.id.st_profile_email)).setText(state.profile.getEmail());
            ((TextView) root.findViewById(R.id.st_profile_uid)).setText(state.profile.getUid());
            String phone = state.profile.getPhone();
            ((TextView) root.findViewById(R.id.st_profile_phone)).setText(getString(R.string.st_profile_phone,
                    phone == null || phone.isEmpty() ? getString(R.string.st_profile_not_provided) : phone));
        }
        root.findViewById(R.id.st_profile_copy_uid).setEnabled(state.profile != null);
        root.findViewById(R.id.st_profile_change_password).setVisibility(state.passwordProvider ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.st_profile_change_password).setEnabled(!busy);
        ((TextView) root.findViewById(R.id.st_profile_provider_note)).setText(state.passwordProvider
                ? R.string.st_profile_password_note : R.string.st_profile_google_password_note);
        renderOrganizations(state);
    }

    private void renderOrganizations(AccountProfileViewModel.State state) {
        LinearLayout container = root.findViewById(R.id.st_profile_organizations);
        container.removeAllViews();
        root.findViewById(R.id.st_profile_org_progress).setVisibility(state.orgLoading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.st_profile_org_reload).setEnabled(!state.orgLoading);
        TextView note = root.findViewById(R.id.st_profile_org_message);
        note.setVisibility(state.orgLoading ? View.GONE : View.VISIBLE);
        if (state.orgLoading) return;
        if (state.orgError != 0) { note.setText(getString(R.string.st_profile_org_error, getString(state.orgError))); return; }
        if (state.organizations.isEmpty()) { note.setText(R.string.st_profile_no_organizations); return; }
        note.setVisibility(View.GONE);
        for (AccountOrganization item : state.organizations) {
            View card = getLayoutInflater().inflate(R.layout.item_profile_organization, container, false);
            ((TextView) card.findViewById(R.id.st_org_name)).setText(item.name);
            ((TextView) card.findViewById(R.id.st_org_id)).setText(getString(R.string.st_profile_org_id, item.id));
            ((TextView) card.findViewById(R.id.st_org_membership)).setText(item.active
                    ? R.string.st_profile_membership_active : R.string.st_profile_membership_inactive);
            Chip badge = card.findViewById(R.id.st_org_verification);
            badge.setText(item.verification == AccountOrganization.Verification.VERIFIED
                    ? R.string.st_profile_verified : item.verification == AccountOrganization.Verification.UNVERIFIED
                    ? R.string.st_profile_unverified : R.string.st_profile_verification_unknown);
            if (item.verification == AccountOrganization.Verification.VERIFIED) {
                badge.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary)));
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary));
            }
            ChipGroup roles = card.findViewById(R.id.st_org_roles);
            for (String role : item.roles) {
                Chip chip = new Chip(requireContext());
                chip.setText(roleName(role)); chip.setCheckable(false); chip.setClickable(false);
                if (!item.active) chip.setAlpha(0.65f);
                roles.addView(chip);
            }
            TextView details = card.findViewById(R.id.st_org_details);
            StringBuilder lines = new StringBuilder();
            append(lines, item.area, R.string.st_profile_org_area);
            append(lines, item.address, R.string.st_profile_org_address);
            append(lines, item.phone, R.string.st_profile_org_phone);
            details.setText(lines.toString());
            details.setVisibility(lines.length() == 0 ? View.GONE : View.VISIBLE);
            TextView warning = card.findViewById(R.id.st_org_warning);
            warning.setVisibility(item.details == AccountOrganization.Details.LOADED ? View.GONE : View.VISIBLE);
            warning.setText(item.details == AccountOrganization.Details.MISSING ? R.string.st_profile_org_missing
                    : item.details == AccountOrganization.Details.INVALID ? R.string.st_profile_org_invalid : R.string.st_profile_org_unavailable);
            container.addView(card);
        }
    }

    private void append(StringBuilder lines, String value, int resource) {
        if (value.isEmpty()) return;
        if (lines.length() > 0) lines.append('\n');
        lines.append(getString(resource, value));
    }

    private int roleName(String value) {
        switch (value) {
            case "RECORDER": return R.string.st_profile_role_recorder;
            case "REVIEWER": return R.string.st_profile_role_reviewer;
            case "REPRESENTATIVE": return R.string.st_profile_role_representative;
            default: return R.string.st_profile_role_unknown;
        }
    }

    private TextInputEditText bind(int id, String initial, Consumer<String> consumer) {
        TextInputEditText field = root.findViewById(id);
        field.setText(initial);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        return field;
    }

    private static void setText(TextInputEditText field, String value) {
        if (!value.contentEquals(field.getText() == null ? "" : field.getText())) field.setText(value);
    }

    private void copyUid() {
        if (current == null || current.profile == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.st_register_uid_label), current.profile.getUid()));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(requireContext(), R.string.st_register_uid_copied, Toast.LENGTH_SHORT).show();
    }

    private void routeToLogin() {
        if (routed || !isAdded()) return;
        routed = true;
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override public void onDestroyView() {
        root = null; name = null; area = null; current = null;
        super.onDestroyView();
    }
}