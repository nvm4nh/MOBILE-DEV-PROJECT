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
import com.seniortribe.app.auth.ProfileInput;
import com.seniortribe.app.models.AccountOrganization;
import com.seniortribe.app.models.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Drafts stay in this ViewModel's RAM, never SavedStateHandle, preferences or a local DB. */
public final class AccountProfileViewModel extends AndroidViewModel {
    public static final class State {
        public final User profile;
        public final boolean loading, saving, signedOut, passwordProvider, orgLoading;
        public final String name, area;
        public final int error, notice, orgError;
        public final ProfileInput.Problem problem;
        public final List<AccountOrganization> organizations;
        private State(AccountProfileViewModel m) {
            profile = m.profile; loading = m.loading; saving = m.saving; signedOut = m.signedOut;
            passwordProvider = m.repository.hasPasswordProvider(); orgLoading = m.orgLoading;
            name = m.name; area = m.area; error = m.error; notice = m.notice; orgError = m.orgError;
            problem = m.problem;
            organizations = Collections.unmodifiableList(new ArrayList<>(m.organizations));
        }
    }

    private final AuthRepository repository;
    private final String uid;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private User profile;
    private boolean initialized, loading, saving, signedOut, orgLoading;
    private String name = "", area = "";
    private int error, notice, orgError, profileRequest, orgRequest;
    private ProfileInput.Problem problem = ProfileInput.Problem.NONE;
    private List<AccountOrganization> organizations = Collections.emptyList();

    public AccountProfileViewModel(@NonNull Application application) {
        super(application);
        repository = ((SeniorTribeApplication) application).getAuthRepository();
        uid = repository.currentUser() == null ? "" : repository.currentUser().getUid();
        refresh();
    }

    public LiveData<State> state() { return state; }
    public String name() { return name; }
    public String area() { return area; }
    public void setName(String value) { if (!saving) name = value; }
    public void setArea(String value) { if (!saving) area = value; }

    public boolean validateSession() {
        if (!uid.isEmpty() && repository.isCurrentUser(uid) && !signedOut) return true;
        signedOut = true;
        profile = null; name = ""; area = ""; organizations = Collections.emptyList();
        loading = false; saving = false; orgLoading = false;
        ++profileRequest; ++orgRequest;
        emit();
        return false;
    }

    public void refresh() {
        if (!validateSession() || loading || saving) return;
        loading = true; error = 0; notice = 0;
        final int request = ++profileRequest;
        emit();
        repository.readOwnProfile(uid).addOnCompleteListener(task -> {
            if (request != profileRequest || !validateSession()) return;
            loading = false;
            if (task.isSuccessful()) {
                profile = task.getResult();
                if (!initialized) {
                    name = profile.getDisplayName(); area = profile.getArea(); initialized = true;
                }
            } else error = AuthErrors.message(task.getException());
            emit();
        });
        refreshOrganizations();
    }

    public void refreshOrganizations() {
        if (!validateSession() || orgLoading) return;
        orgLoading = true; orgError = 0;
        final int request = ++orgRequest;
        emit();
        repository.readOwnOrganizations(uid).addOnCompleteListener(task -> {
            if (request != orgRequest || !validateSession()) return;
            orgLoading = false;
            if (task.isSuccessful()) organizations = task.getResult();
            else {
                organizations = Collections.emptyList();
                orgError = AuthErrors.message(task.getException());
            }
            emit();
        });
    }

    public void save() {
        if (!validateSession() || loading || saving || profile == null) return;
        problem = ProfileInput.profile(name, area);
        notice = 0; error = 0;
        if (problem != ProfileInput.Problem.NONE) { emit(); return; }
        saving = true;
        final int request = ++profileRequest;
        emit();
        repository.updateOwnProfile(uid, name, area).addOnCompleteListener(task -> {
            if (request != profileRequest || !validateSession()) return;
            saving = false;
            if (task.isSuccessful()) {
                profile = task.getResult().profile;
                name = profile.getDisplayName(); area = profile.getArea();
                notice = task.getResult().authNameUpdated ? R.string.st_profile_saved : R.string.st_profile_saved_auth_warning;
            } else error = AuthErrors.message(task.getException());
            emit();
        });
    }

    public void signOut() {
        ((SeniorTribeApplication) getApplication()).clearActiveAccess();
        repository.signOut();
        validateSession();
    }

    private void emit() { state.setValue(new State(this)); }

    @Override protected void onCleared() {
        ++profileRequest; ++orgRequest;
        name = ""; area = ""; profile = null; organizations = Collections.emptyList();
    }
}
