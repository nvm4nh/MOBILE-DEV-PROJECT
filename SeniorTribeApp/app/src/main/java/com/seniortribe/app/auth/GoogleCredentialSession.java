package com.seniortribe.app.auth;

import android.content.Context;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Executor;

/** Application-scoped provider state. It never stores a Google ID token. */
public final class GoogleCredentialSession {
    private final CredentialManager manager;
    private final Executor executor;
    private Task<Void> lastClear = Tasks.forResult(null);

    public GoogleCredentialSession(Context context) {
        Context application = context.getApplicationContext();
        manager = CredentialManager.create(application);
        executor = ContextCompat.getMainExecutor(application);
    }

    public CredentialManager manager() { return manager; }
    public Task<Void> pendingClear() { return lastClear; }

    public Task<Void> clearState() {
        if (!lastClear.isComplete()) return lastClear;
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        lastClear = completion.getTask();
        try {
            manager.clearCredentialStateAsync(new ClearCredentialStateRequest(),
                    new CancellationSignal(), executor,
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(Void unused) {
                            completion.trySetResult(null);
                        }
                        @Override public void onError(@NonNull ClearCredentialException error) {
                            completion.trySetException(error);
                        }
                    });
        } catch (RuntimeException error) {
            completion.trySetException(error);
        }
        return lastClear;
    }
}
