package com.seniortribe.app.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;

public final class LoginViewModel extends AndroidViewModel {
    public enum Stage { FORM, LOADING, GOOGLE_PICKER, GOOGLE_LOADING, DONE }
    public static final class State {
        public final Stage stage;
        public final int message;
        State(Stage stage, int message) { this.stage = stage; this.message = message; }
    }
    private final AuthRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Stage.FORM, 0));
    private boolean cleared;
    private long generation;
    public String email = "";
    public String password = "";

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = ((SeniorTribeApplication) application).getAuthRepository();
        if (repository.currentUser() != null) state.setValue(new State(Stage.DONE, 0));
    }
    public LiveData<State> state() { return state; }
    public boolean busy() {
        Stage stage = state.getValue().stage;
        return stage == Stage.LOADING || stage == Stage.GOOGLE_PICKER || stage == Stage.GOOGLE_LOADING;
    }

    public void login() {
        if (state.getValue().stage != Stage.FORM) return;
        InputValidator.Problem problem = InputValidator.login(email, password);
        if (problem != InputValidator.Problem.NONE) {
            state.setValue(new State(Stage.FORM, problem == InputValidator.Problem.EMAIL
                    ? R.string.st_auth_error_email : R.string.st_auth_error_password_required));
            return;
        }
        final long request = ++generation;
        state.setValue(new State(Stage.LOADING, 0));
        repository.signIn(email, password).addOnCompleteListener(task -> {
            if (cleared || request != generation) return;
            if (task.isSuccessful() && repository.isCurrentUser(task.getResult().getUid())) {
                password = "";
                state.setValue(new State(Stage.DONE, 0));
            } else {
                state.setValue(new State(Stage.FORM, AuthErrors.message(task.getException())));
            }
        });
    }

    public long beginGoogle() {
        if (cleared || state.getValue().stage != Stage.FORM) return -1L;
        long request = ++generation;
        state.setValue(new State(Stage.GOOGLE_PICKER, 0));
        return request;
    }

    public boolean isGooglePickerActive(long request) {
        return !cleared && request == generation && state.getValue().stage == Stage.GOOGLE_PICKER;
    }

    public void googlePickerCancelled(long request) {
        if (!isGooglePickerActive(request)) return;
        ++generation;
        state.setValue(new State(Stage.FORM, 0));
    }

    public void googlePickerFailed(long request, Throwable error) {
        if (!isGooglePickerActive(request)) return;
        ++generation;
        state.setValue(new State(Stage.FORM, AuthErrors.googleMessage(error)));
    }

    public void googleTokenReceived(long request, String idToken) {
        if (!isGooglePickerActive(request)) return;
        state.setValue(new State(Stage.GOOGLE_LOADING, 0));
        // Token only exists in this call/SDK request, never in a ViewModel field or saved state.
        repository.signInWithGoogle(idToken).addOnCompleteListener(task -> {
            if (cleared || request != generation) return;
            if (task.isSuccessful() && repository.isCurrentUser(task.getResult().getUid())) {
                password = "";
                email = task.getResult().getEmail() == null ? "" : task.getResult().getEmail();
                state.setValue(new State(Stage.DONE, 0));
            } else {
                state.setValue(new State(Stage.FORM, AuthErrors.googleMessage(task.getException())));
            }
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        ++generation;
        password = "";
        super.onCleared();
    }
}
