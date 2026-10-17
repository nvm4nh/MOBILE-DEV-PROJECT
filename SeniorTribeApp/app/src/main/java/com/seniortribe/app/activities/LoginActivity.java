package com.seniortribe.app.activities;

import android.content.Intent;
import android.annotation.SuppressLint;
import android.content.MutableContextWrapper;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CustomCredential;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.auth.GoogleCredentialSession;
import com.seniortribe.app.auth.AuthFields;
import com.seniortribe.app.auth.LoginViewModel;

public class LoginActivity extends AppCompatActivity {
    private LoginViewModel model;
    private boolean routed;
    private View[] controls;
    private CancellationSignal googleCancellation;
    private long googleRequest = -1L;
    private MutableContextWrapper googleContext;

    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_SeniorTribe_Auth);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        model = new ViewModelProvider(this).get(LoginViewModel.class);
        TextInputEditText email = AuthFields.bind(this, R.id.st_login_email, model.email, v -> model.email = v);
        TextInputEditText password = AuthFields.bind(this, R.id.st_login_password, model.password, v -> model.password = v);
        View submit = findViewById(R.id.st_login_submit);
        View register = findViewById(R.id.st_login_register);
        View forgot = findViewById(R.id.st_login_forgot);
        View google = findViewById(R.id.st_login_google);
        controls = new View[]{email, password, submit, register, forgot, google};
        submit.setOnClickListener(v -> model.login());
        register.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        forgot.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        google.setOnClickListener(v -> signInWithGoogle());
        password.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            model.login();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (model.busy()) {
                    Toast.makeText(LoginActivity.this, R.string.st_auth_wait, Toast.LENGTH_SHORT).show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        model.state().observe(this, state -> {
            if (state.stage == LoginViewModel.Stage.DONE) {
                if (!routed) {
                    routed = true;
                    password.setText("");
                    Intent intent = new Intent(this, SessionGateActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
                return;
            }
            for (View control : controls) control.setEnabled(!model.busy());
            findViewById(R.id.st_login_progress).setVisibility(model.busy() ? View.VISIBLE : View.GONE);
            TextView message = findViewById(R.id.st_login_message);
            message.setVisibility(state.message == 0 ? View.GONE : View.VISIBLE);
            if (state.message != 0) message.setText(state.message);
        });
    }

    @SuppressLint("DiscouragedApi")
    private String googleWebClientId() {
        // The Google Services plugin generates this resource from OAuth client_type 3.
        // Resolve it at runtime so an old config still builds and shows an actionable fallback.
        int resource = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        return resource == 0 ? "" : getString(resource).trim();
    }

    private void signInWithGoogle() {
        final long request = model.beginGoogle();
        if (request < 0L) return;
        googleRequest = request;
        String clientId = googleWebClientId();
        if (clientId.isEmpty() || !clientId.endsWith(".apps.googleusercontent.com")) {
            model.googlePickerFailed(request, new IllegalStateException("GOOGLE_CONFIG"));
            return;
        }
        GoogleCredentialSession credentials = ((SeniorTribeApplication) getApplication())
                .getGoogleCredentialSession();
        // Wait for an earlier sign-out cleanup. A cleanup error must not sign Firebase back in.
        credentials.pendingClear().addOnCompleteListener(ignored -> {
            if (!model.isGooglePickerActive(request) || isFinishing() || isDestroyed()) return;
            googleCancellation = new CancellationSignal();
            googleContext = new MutableContextWrapper(this);
            try {
                GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(clientId).build();
                GetCredentialRequest credentialRequest = new GetCredentialRequest.Builder()
                        .addCredentialOption(option).build();
                credentials.manager().getCredentialAsync(googleContext, credentialRequest,
                        googleCancellation, ContextCompat.getMainExecutor(this),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override public void onResult(@NonNull GetCredentialResponse response) {
                                releaseGooglePicker(request);
                                if (!model.isGooglePickerActive(request)) return;
                                handleGoogleResponse(request, response);
                            }
                            @Override public void onError(@NonNull GetCredentialException error) {
                                releaseGooglePicker(request);
                                if (error instanceof GetCredentialCancellationException) {
                                    model.googlePickerCancelled(request);
                                } else {
                                    model.googlePickerFailed(request, error);
                                }
                            }
                        });
            } catch (RuntimeException error) {
                releaseGooglePicker(request);
                model.googlePickerFailed(request, error);
            }
        });
    }

    private void handleGoogleResponse(long request, GetCredentialResponse response) {
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

    private void releaseGooglePicker(long request) {
        if (request != googleRequest) return;
        googleCancellation = null;
        if (googleContext != null) googleContext.setBaseContext(getApplicationContext());
        googleContext = null;
    }

    @Override protected void onDestroy() {
        if (model != null && model.isGooglePickerActive(googleRequest)) {
            // A picker belongs to its foreground Activity; after rotation the user can request it again.
            model.googlePickerCancelled(googleRequest);
        }
        if (googleCancellation != null) googleCancellation.cancel();
        releaseGooglePicker(googleRequest);
        super.onDestroy();
    }
}
