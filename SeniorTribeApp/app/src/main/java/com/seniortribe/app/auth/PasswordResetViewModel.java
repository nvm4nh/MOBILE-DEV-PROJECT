package com.seniortribe.app.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuthException;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;

public final class PasswordResetViewModel extends AndroidViewModel {
    public enum Stage { FORM, LOADING, DONE }
    public static final class State {
        public final Stage stage;
        public final int message;
        State(Stage stage, int message) { this.stage = stage; this.message = message; }
    }
    private final AuthRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Stage.FORM, 0));
    private boolean cleared;
    public String email = "";
    private String requestedEmail = "";

    public PasswordResetViewModel(@NonNull Application application) {
        super(application);
        repository = ((SeniorTribeApplication) application).getAuthRepository();
    }
    public LiveData<State> state() { return state; }
    public boolean busy() { return state.getValue().stage == Stage.LOADING; }

    public void submit() {
        if (state.getValue().stage != Stage.FORM) return;
        if (!InputValidator.validEmail(email)) {
            state.setValue(new State(Stage.FORM, R.string.st_auth_error_email));
            return;
        }
        requestedEmail = InputValidator.normalizeEmail(email);
        state.setValue(new State(Stage.LOADING, 0));
        repository.sendPasswordReset(requestedEmail).addOnCompleteListener(task -> {
            if (cleared) return;
            Exception error = task.getException();
            // Give the same conditional guidance whether or not this email has an account.
            boolean unknownEmail = error instanceof FirebaseAuthException
                    && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthException) error).getErrorCode());
            state.setValue(task.isSuccessful() || unknownEmail ? new State(Stage.DONE, 0)
                    : new State(Stage.FORM, AuthErrors.message(error)));
        });
    }

    public String maskedEmail() {
        int at = requestedEmail.lastIndexOf('@');
        return at > 0 ? requestedEmail.substring(0, 1) + "***" + requestedEmail.substring(at) : "***";
    }

    @Override protected void onCleared() { cleared = true; super.onCleared(); }
}
