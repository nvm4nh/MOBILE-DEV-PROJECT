package com.seniortribe.app.auth;

import android.annotation.SuppressLint;
import android.content.MutableContextWrapper;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.seniortribe.app.SeniorTribeApplication;

/** Shared Google picker for login and registration; Firebase work stays in LoginViewModel. */
public final class GoogleSignInLauncher {
    private final AppCompatActivity activity;
    private final LoginViewModel model;
    private CancellationSignal cancellation;
    private long activeRequest = -1L;
    private MutableContextWrapper pickerContext;

    public GoogleSignInLauncher(AppCompatActivity activity, LoginViewModel model) {
        this.activity = activity;
        this.model = model;
    }

    @SuppressLint("DiscouragedApi")
    private String webClientId() {
        // Keep the existing fallback when the Google Services config has no OAuth web client.
        int resource = activity.getResources().getIdentifier(
                "default_web_client_id", "string", activity.getPackageName());
        return resource == 0 ? "" : activity.getString(resource).trim();
    }

    public void launch() {
        final long request = model.beginGoogle();
        if (request < 0L) return;
        activeRequest = request;
        String clientId = webClientId();
        if (clientId.isEmpty() || !clientId.endsWith(".apps.googleusercontent.com")) {
            model.googlePickerFailed(request, new IllegalStateException("GOOGLE_CONFIG"));
            return;
        }
        GoogleCredentialSession credentials = ((SeniorTribeApplication) activity.getApplication())
                .getGoogleCredentialSession();
        credentials.pendingClear().addOnCompleteListener(ignored -> {
            if (!model.isGooglePickerActive(request) || activity.isFinishing() || activity.isDestroyed()) return;
            cancellation = new CancellationSignal();
            pickerContext = new MutableContextWrapper(activity);
            try {
                GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(clientId).build();
                GetCredentialRequest credentialRequest = new GetCredentialRequest.Builder()
                        .addCredentialOption(option).build();
                credentials.manager().getCredentialAsync(pickerContext, credentialRequest,
                        cancellation, ContextCompat.getMainExecutor(activity),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override public void onResult(@NonNull GetCredentialResponse response) {
                                releasePicker(request);
                                if (!model.isGooglePickerActive(request)) return;
                                handleResponse(request, response);
                            }

                            @Override public void onError(@NonNull GetCredentialException error) {
                                releasePicker(request);
                                if (error instanceof GetCredentialCancellationException) {
                                    model.googlePickerCancelled(request);
                                } else {
                                    model.googlePickerFailed(request, error);
                                }
                            }
                        });
            } catch (RuntimeException error) {
                releasePicker(request);
                model.googlePickerFailed(request, error);
            }
        });
    }

    private void handleResponse(long request, GetCredentialResponse response) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            model.googlePickerFailed(request, new IllegalStateException("GOOGLE_RESPONSE"));
            return;
        }
        try {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
            model.googleTokenReceived(request, google.getIdToken());
        } catch (Exception error) {
            model.googlePickerFailed(request, error);
        }
    }

    private void releasePicker(long request) {
        if (request != activeRequest) return;
        cancellation = null;
        if (pickerContext != null) pickerContext.setBaseContext(activity.getApplicationContext());
        pickerContext = null;
    }

    public void close() {
        // A picker belongs to its foreground Activity. After rotation it can be requested again.
        if (model.isGooglePickerActive(activeRequest)) model.googlePickerCancelled(activeRequest);
        if (cancellation != null) cancellation.cancel();
        releasePicker(activeRequest);
    }
}
