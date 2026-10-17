package com.seniortribe.app.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.auth.AuthRepository;
import com.seniortribe.app.auth.ProfileErrors;
import com.seniortribe.app.auth.ProfileInput;

/** Sensitive fields survive a rotation in RAM; never saved to Bundle or logs. */
public final class ChangePasswordViewModel extends AndroidViewModel {
    public enum Stage {
        FORM, BUSY, SUCCESS, SIGNED_OUT
    }

    public static final class State {
        public final Stage stage;
        public final ProfileInput.Problem problem;
        public final int error;

        State(Stage stage, ProfileInput.Problem problem, int error) {
            this.stage = stage;
            this.problem = problem;
            this.error = error;
        }
    }

    private final AuthRepository repository;
    private final String uid;

    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(Stage.FORM, ProfileInput.Problem.NONE, 0));

    private String current = "";
    private String next = "";
    private String repeated = "";
    private int request;

    public ChangePasswordViewModel(@NonNull Application application) {
        super(application);
        repository = ((SeniorTribeApplication) application).getAuthRepository();
        uid = repository.currentUser() == null
                ? "" : repository.currentUser().getUid();
    }

    public LiveData<State> state() {
        return state;
    }

    public String current() {
        return current;
    }

    public String next() {
        return next;
    }

    public String repeated() {
        return repeated;
    }

    public void setCurrent(String value) {
        if (state.getValue().stage == Stage.FORM) current = value;
    }

    public void setNext(String value) {
        if (state.getValue().stage == Stage.FORM) next = value;
    }

    public void setRepeated(String value) {
        if (state.getValue().stage == Stage.FORM) repeated = value;
    }

    public void submit() {
        if (state.getValue().stage != Stage.FORM) return;

        if (!repository.isCurrentUser(uid)) {
            clear();
            emit(Stage.SIGNED_OUT, ProfileInput.Problem.NONE, 0);
            return;
        }

        ProfileInput.Problem problem =
                ProfileInput.password(current, next, repeated);

        if (problem != ProfileInput.Problem.NONE) {
            emit(Stage.FORM, problem, 0);
            return;
        }

        final int generation = ++request;
        emit(Stage.BUSY, ProfileInput.Problem.NONE, 0);

        repository.changePassword(uid, current, next, repeated)
                .addOnCompleteListener(task -> {
                    if (generation != request) return;

                    if (!repository.isCurrentUser(uid)) {
                        clear();
                        emit(Stage.SIGNED_OUT, ProfileInput.Problem.NONE, 0);
                    } else if (task.isSuccessful()) {
                        clear();
                        emit(Stage.SUCCESS, ProfileInput.Problem.NONE, 0);
                    } else {
                        int error = ProfileErrors.password(task.getException());

                        // Keep the new password draft in this dialog's RAM.
                        // An incorrect current password must be entered again.
                        if (error == R.string.st_profile_password_incorrect) {
                            current = "";
                        }

                        emit(Stage.FORM, ProfileInput.Problem.NONE, error);
                    }
                });
    }

    private void emit(
            Stage stage, ProfileInput.Problem problem, int error) {
        state.setValue(new State(stage, problem, error));
    }

    private void clear() {
        current = "";
        next = "";
        repeated = "";
    }

    @Override protected void onCleared() {
        ++request;
        clear();
    }
}