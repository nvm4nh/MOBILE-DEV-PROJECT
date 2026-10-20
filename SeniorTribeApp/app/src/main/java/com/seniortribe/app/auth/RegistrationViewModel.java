package com.seniortribe.app.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;

/** Registration and profile creation. Organization roles are never client-assigned. */
public final class RegistrationViewModel extends AndroidViewModel {

    public enum Stage { FORM, REGISTERING, SAVING_PROFILE, PROFILE_ERROR, DONE }

    public static final class State {
        public final Stage stage;
        public final int message;

        private State(Stage stage, int message) {
            this.stage = stage;
            this.message = message;
        }
    }

    private final AuthRepository repository;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Stage.FORM, 0));
    private String accountUid = "";
    private boolean cleared;

    // Kept in memory across rotation. Never save passwords in a Bundle or database.
    public String name = "";
    public String email = "";
    public String password = "";
    public String confirmation = "";

    public RegistrationViewModel(@NonNull Application application) {
        super(application);
        repository = ((SeniorTribeApplication) application).getAuthRepository();

        // Recover a signed-in account if Auth succeeded before profile creation failed.
        FirebaseUser existing = repository.currentUser();
        if (existing != null) {
            accountUid = existing.getUid();
            name = existing.getDisplayName() == null ? "" : existing.getDisplayName();
            email = existing.getEmail() == null ? "" : existing.getEmail();
            saveProfile();
        }
    }

    public LiveData<State> state() {
        return state;
    }

    public String accountUid() {
        return accountUid;
    }

    public boolean busy() {
        State current = state.getValue();
        return current != null && (current.stage == Stage.REGISTERING
                || current.stage == Stage.SAVING_PROFILE);
    }

    public void register() {
        State current = state.getValue();
        if (busy() || current == null || current.stage != Stage.FORM) return;

        InputValidator.Problem problem =
                InputValidator.registration(name, email, password, confirmation);
        if (problem != InputValidator.Problem.NONE) {
            show(Stage.FORM, validationMessage(problem));
            return;
        }

        show(Stage.REGISTERING, R.string.st_register_creating);
        repository.signUp(name, email, password).addOnCompleteListener(task -> {
            if (cleared) return;
            if (!task.isSuccessful()) {
                show(Stage.FORM, errorMessage(task.getException()));
                return;
            }

            accountUid = task.getResult().getUid();
            password = "";
            confirmation = "";
            saveProfile();
        });
    }

    public void retryProfile() {
        State current = state.getValue();
        if (current != null && current.stage == Stage.PROFILE_ERROR) saveProfile();
    }

    private void saveProfile() {
        final String expectedUid = accountUid;
        if (!repository.isCurrentUser(expectedUid)) {
            show(Stage.PROFILE_ERROR, R.string.st_auth_error_session);
            return;
        }

        show(Stage.SAVING_PROFILE, R.string.st_register_saving);
        repository.ensureProfile(name).addOnCompleteListener(task -> {
            if (cleared) return;
            if (!repository.isCurrentUser(expectedUid)) {
                show(Stage.PROFILE_ERROR, R.string.st_auth_error_session);
            } else if (task.isSuccessful()) {
                show(Stage.DONE, 0);
            } else {
                show(Stage.PROFILE_ERROR, errorMessage(task.getException()));
            }
        });
    }

    public void signOut() {
        if (busy()) return;
        repository.signOut();
        name = "";
        email = "";
        password = "";
        confirmation = "";
        accountUid = "";
        show(Stage.FORM, 0);
    }

    private void show(Stage stage, int message) {
        state.setValue(new State(stage, message));
    }

    private static int validationMessage(InputValidator.Problem problem) {
        switch (problem) {
            case NAME: return R.string.st_auth_error_name;
            case EMAIL: return R.string.st_auth_error_email;
            case PASSWORD_REQUIRED: return R.string.st_auth_error_password_required;
            case PASSWORD_LENGTH: return R.string.st_auth_error_password_length;
            case PASSWORD_MATCH: return R.string.st_auth_error_password_match;
            default: return R.string.st_auth_error_generic;
        }
    }

    private static int errorMessage(Throwable error) {
        if (error instanceof FirebaseNetworkException) return R.string.st_auth_error_network;
        if (error instanceof FirebaseTooManyRequestsException) return R.string.st_auth_error_rate_limit;
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return R.string.st_auth_error_network;
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return R.string.st_auth_error_permission;
            }
            if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return R.string.st_auth_error_session;
            }
        }
        if (error instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) error).getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE": return R.string.st_auth_error_email_in_use;
                case "ERROR_WEAK_PASSWORD": return R.string.st_auth_error_weak_password;
                case "ERROR_INVALID_EMAIL": return R.string.st_auth_error_email;
                case "ERROR_USER_DISABLED": return R.string.st_auth_error_disabled;
                case "ERROR_TOO_MANY_REQUESTS": return R.string.st_auth_error_rate_limit;
            }
        }
        if (error != null && "SESSION".equals(error.getMessage())) {
            return R.string.st_auth_error_session;
        }
        if (error != null && "PROFILE".equals(error.getMessage())) {
            return R.string.st_auth_error_profile;
        }
        return R.string.st_auth_error_generic;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cleared = true;
        password = "";
        confirmation = "";
    }
}
