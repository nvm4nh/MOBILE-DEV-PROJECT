package com.seniortribe.app.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.auth.AuthErrors;
import com.seniortribe.app.auth.AuthRepository;
import com.seniortribe.app.auth.ProfileErrors;
import com.seniortribe.app.auth.ProfileInput;
import com.seniortribe.app.models.AccountOrganization;
import com.seniortribe.app.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountProfileViewModel extends AndroidViewModel {

    public static final class State {
        public final User profile;
        public final boolean loading;
        public final boolean saving;
        public final boolean signedOut;
        public final boolean passwordProvider;
        public final boolean orgLoading;

        public final int error;
        public final int notice;
        public final int orgError;
        public final int editError;

        public final String editingField;
        public final List<AccountOrganization> organizations;

        private State(AccountProfileViewModel model) {
            profile = model.profile;
            loading = model.loading;
            saving = model.saving;
            signedOut = model.signedOut;
            passwordProvider = model.repository.hasPasswordProvider();
            orgLoading = model.orgLoading;
            error = model.error;
            notice = model.notice;
            orgError = model.orgError;
            editError = model.editError;
            editingField = model.editingField;

            organizations = Collections.unmodifiableList(
                    new ArrayList<>(model.organizations));
        }
    }

    private final AuthRepository repository;
    private final String uid;

    private final MutableLiveData<State> state =
            new MutableLiveData<>();

    private User profile;
    private boolean loading;
    private boolean saving;
    private boolean signedOut;
    private boolean orgLoading;

    private int error;
    private int notice;
    private int orgError;
    private int editError;
    private int profileRequest;
    private int orgRequest;

    private String editingField = "";
    private String editValue = "";

    private List<AccountOrganization> organizations =
            Collections.emptyList();

    public AccountProfileViewModel(@NonNull Application application) {
        super(application);

        repository = ((SeniorTribeApplication) application)
                .getAuthRepository();

        uid = repository.currentUser() == null
                ? "" : repository.currentUser().getUid();

        refresh();
    }

    public LiveData<State> state() {
        return state;
    }

    public String editValue() {
        return editValue;
    }

    public void setEditValue(String value) {
        if (!saving && !editingField.isEmpty()) {
            editValue = value;
        }
    }

    public boolean validateSession() {
        if (!uid.isEmpty()
                && repository.isCurrentUser(uid)
                && !signedOut) {
            return true;
        }

        signedOut = true;
        profile = null;
        loading = false;
        saving = false;
        orgLoading = false;
        editingField = "";
        editValue = "";
        organizations = Collections.emptyList();

        ++profileRequest;
        ++orgRequest;

        emit();
        return false;
    }

    public void refresh() {
        if (!validateSession() || loading || saving) return;

        loading = true;
        error = 0;
        notice = 0;

        final int request = ++profileRequest;
        emit();

        repository.readOwnProfile(uid).addOnCompleteListener(task -> {
            if (request != profileRequest || !validateSession()) return;

            loading = false;

            if (task.isSuccessful()) {
                profile = task.getResult();
            } else {
                error = AuthErrors.message(task.getException());
            }

            emit();
        });

        refreshOrganizations();
    }

    public void refreshOrganizations() {
        if (!validateSession() || orgLoading) return;

        orgLoading = true;
        orgError = 0;

        final int request = ++orgRequest;
        emit();

        repository.readOwnOrganizations(uid)
                .addOnCompleteListener(task -> {
                    if (request != orgRequest
                            || !validateSession()) return;

                    orgLoading = false;

                    if (task.isSuccessful()) {
                        organizations = task.getResult();
                    } else {
                        organizations = Collections.emptyList();
                        orgError = AuthErrors.message(
                                task.getException());
                    }

                    emit();
                });
    }

    public boolean beginEdit(String field) {
        if (!validateSession() || loading || saving
                || profile == null || !editingField.isEmpty()) {
            return false;
        }

        switch (field) {
            case "displayName":
                editValue = profile.getDisplayName();
                break;

            case "area":
                editValue = profile.getArea();
                break;

            case "phone":
                editValue = profile.getPhone() == null
                        ? "" : profile.getPhone();
                break;

            default:
                return false;
        }

        editingField = field;
        editError = 0;
        error = 0;
        notice = 0;

        emit();
        return true;
    }

    public void cancelEdit() {
        if (saving) return;

        editingField = "";
        editValue = "";
        editError = 0;

        emit();
    }

    public void saveEdit() {
        if (!validateSession() || loading || saving
                || profile == null || editingField.isEmpty()) {
            return;
        }

        ProfileInput.Problem problem =
                ProfileInput.field(editingField, editValue);

        if (problem != ProfileInput.Problem.NONE) {
            editError = ProfileErrors.input(problem);
            emit();
            return;
        }

        final String field = editingField;
        final String value = editValue;
        final int request = ++profileRequest;

        saving = true;
        editError = 0;
        error = 0;
        notice = 0;

        emit();

        repository.updateOwnProfileField(uid, field, value)
                .addOnCompleteListener(task -> {
                    if (request != profileRequest
                            || !validateSession()) return;

                    saving = false;

                    if (task.isSuccessful()) {
                        profile = task.getResult().profile;

                        notice = task.getResult().authNameUpdated
                                ? R.string.st_profile_saved
                                : R.string.st_profile_saved_auth_warning;

                        editingField = "";
                        editValue = "";
                        editError = 0;
                    } else {
                        editError = AuthErrors.message(
                                task.getException());
                    }

                    emit();
                });
    }

    public void signOut() {
        ((SeniorTribeApplication) getApplication())
                .clearActiveAccess();

        repository.signOut();
        validateSession();
    }

    private void emit() {
        state.setValue(new State(this));
    }

    @Override
    protected void onCleared() {
        ++profileRequest;
        ++orgRequest;

        profile = null;
        editingField = "";
        editValue = "";
        organizations = Collections.emptyList();

        super.onCleared();
    }
}