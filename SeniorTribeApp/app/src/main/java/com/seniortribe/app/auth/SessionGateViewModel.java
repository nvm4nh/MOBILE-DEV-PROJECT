package com.seniortribe.app.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseUser;
import com.seniortribe.app.SeniorTribeApplication;

/** Errors and a confirmed absence of membership are deliberately different states. */
public final class SessionGateViewModel extends AndroidViewModel {
    public enum Stage { LOADING, ERROR, PENDING, GRANTED, SIGNED_OUT }
    public static final class State {
        public final Stage stage;
        public final String uid;
        public final int message;
        State(Stage stage, String uid, int message) {
            this.stage = stage;
            this.uid = uid;
            this.message = message;
        }
    }

    private final SeniorTribeApplication app;
    private final AuthRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private int generation;
    private boolean cleared;

    public SessionGateViewModel(@NonNull Application application) {
        super(application);
        app = (SeniorTribeApplication) application;
        repository = app.getAuthRepository();
        refresh();
    }

    public LiveData<State> state() { return state; }

    public void refresh() {
        State previous = state.getValue();
        if (previous != null && previous.stage == Stage.LOADING) return;
        app.clearActiveAccess();
        FirebaseUser user = repository.currentUser();
        if (user == null) {
            state.setValue(new State(Stage.SIGNED_OUT, "", 0));
            return;
        }
        final String uid = user.getUid();
        final int token = ++generation;
        state.setValue(new State(Stage.LOADING, uid, 0));
        repository.refreshSession().addOnCompleteListener(session -> {
            if (!current(token, uid)) return;
            if (!session.isSuccessful()) { fail(uid, session.getException()); return; }
            repository.ensureProfile(null).addOnCompleteListener(profile -> {
                if (!current(token, uid)) return;
                if (!profile.isSuccessful()) { fail(uid, profile.getException()); return; }
                repository.hasAssignedMembership(uid).addOnCompleteListener(membership -> {
                    if (!current(token, uid)) return;
                    if (!membership.isSuccessful()) { fail(uid, membership.getException()); return; }
                    if (Boolean.TRUE.equals(membership.getResult())) {
                        app.markActiveAccess(uid);
                        state.setValue(new State(Stage.GRANTED, uid, 0));
                    } else {
                        state.setValue(new State(Stage.PENDING, uid, 0));
                    }
                });
            });
        });
    }

    private boolean current(int token, String uid) {
        if (cleared || token != generation) return false;
        if (!repository.isCurrentUser(uid)) {
            app.clearActiveAccess();
            state.setValue(new State(Stage.SIGNED_OUT, "", 0));
            return false;
        }
        return true;
    }

    private void fail(String uid, Throwable error) {
        state.setValue(new State(Stage.ERROR, uid, AuthErrors.message(error)));
    }

    public void signOut() {
        ++generation;
        app.clearActiveAccess();
        repository.signOut();
        state.setValue(new State(Stage.SIGNED_OUT, "", 0));
    }

    @Override protected void onCleared() {
        cleared = true;
        ++generation;
        super.onCleared();
    }
}
