package com.seniortribe.app.activities;

import com.seniortribe.app.R;
import android.content.Intent;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.appbar.MaterialToolbar;
import com.seniortribe.app.auth.GoogleSignInLauncher;
import com.seniortribe.app.auth.LoginViewModel;
import com.seniortribe.app.auth.RegistrationViewModel;

import java.util.function.Consumer;

public class RegisterActivity extends AppCompatActivity {

    private boolean routed;
    private RegistrationViewModel model;
    private LoginViewModel googleModel;
    private GoogleSignInLauncher googleLauncher;
    private TextInputEditText password;
    private TextInputEditText confirmation;
    private View[] formControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_SeniorTribe_Auth);
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_register);
        model = new ViewModelProvider(this).get(RegistrationViewModel.class);
        googleModel = new ViewModelProvider(this).get(LoginViewModel.class);
        googleLauncher = new GoogleSignInLauncher(this, googleModel);
        MaterialToolbar toolbar = findViewById(R.id.st_register_toolbar);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());

        TextInputEditText name = bind(R.id.st_register_name, model.name,
                value -> model.name = value);
        TextInputEditText email = bind(R.id.st_register_email, model.email,
                value -> model.email = value);
        password = bind(R.id.st_register_password, model.password,
                value -> model.password = value);
        confirmation = bind(R.id.st_register_confirmation, model.confirmation,
                value -> model.confirmation = value);

        View submit = findViewById(R.id.st_register_submit);
        View back = findViewById(R.id.btnGoToLogin);
        View google = findViewById(R.id.st_register_google);
        formControls = new View[] {name, email, password, confirmation, submit, back, google};
        submit.setOnClickListener(view -> submitEmail());
        google.setOnClickListener(view -> {
            if (!model.busy() && model.state().getValue().stage == RegistrationViewModel.Stage.FORM) {
                googleLauncher.launch();
            }
        });
        back.setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.st_register_retry).setOnClickListener(view -> model.retryProfile());
        findViewById(R.id.st_register_copy_uid).setOnClickListener(view -> copyUid());
        findViewById(R.id.st_register_sign_out).setOnClickListener(view -> {
            if (busy()) return;
            model.signOut();
            finish();
        });
        confirmation.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submitEmail();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (busy()) {
                    Toast.makeText(RegisterActivity.this,
                            R.string.st_auth_wait, Toast.LENGTH_SHORT).show();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        model.state().observe(this, this::render);
        googleModel.state().observe(this, state -> {
            if (state.stage == LoginViewModel.Stage.DONE) {
                // Existing email accounts recovering profile creation stay in the registration flow.
                if (model.state().getValue().stage == RegistrationViewModel.Stage.FORM) routeToGate();
            } else {
                render(model.state().getValue());
            }
        });
    }

    private boolean busy() { return model.busy() || googleModel.busy(); }

    private void submitEmail() {
        if (googleModel.busy()) return;
        googleModel.clearFormMessage();
        model.register();
    }

    private void routeToGate() {
        if (routed) return;
        routed = true;
        password.setText("");
        confirmation.setText("");
        Intent intent = new Intent(this, SessionGateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private TextInputEditText bind(int id, String initial, Consumer<String> changed) {
        TextInputEditText field = findViewById(id);
        field.setText(initial);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                changed.accept(text.toString());
            }

            @Override
            public void afterTextChanged(Editable text) { }
        });
        return field;
    }

    private void render(RegistrationViewModel.State state) {
        if (state.stage == RegistrationViewModel.Stage.DONE) {
            routeToGate();
            return;
        }
        boolean busy = busy();
        boolean showForm = state.stage == RegistrationViewModel.Stage.FORM
                || state.stage == RegistrationViewModel.Stage.REGISTERING;
        findViewById(R.id.st_register_form).setVisibility(showForm ? View.VISIBLE : View.GONE);
        findViewById(R.id.st_register_result).setVisibility(showForm ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnGoToLogin).setVisibility(showForm ? View.VISIBLE : View.GONE);
        findViewById(R.id.st_register_progress).setVisibility(busy ? View.VISIBLE : View.GONE);
        for (View control : formControls) control.setEnabled(!busy);

        if (!showForm) {
            password.setText("");
            confirmation.setText("");
        }

        TextView message = findViewById(R.id.st_register_message);
        int googleMessage = googleModel.state().getValue().message;
        int messageResource = googleModel.busy() ? 0 : googleMessage != 0 ? googleMessage : state.message;
        message.setVisibility(messageResource == 0 ? View.GONE : View.VISIBLE);
        if (messageResource != 0) message.setText(messageResource);
        message.setTextColor(ContextCompat.getColor(this,
                busy ? R.color.st_auth_secondary : R.color.st_auth_error));

        TextView uid = findViewById(R.id.st_register_uid);
        uid.setText(model.accountUid());
        TextView title = findViewById(R.id.st_register_result_title);
        TextView description = findViewById(R.id.st_register_result_description);
        if (state.stage == RegistrationViewModel.Stage.PROFILE_ERROR) {
            title.setText(R.string.st_register_profile_incomplete);
            description.setText(R.string.st_register_profile_incomplete_note);
        } else if (state.stage == RegistrationViewModel.Stage.SAVING_PROFILE) {
            title.setText(R.string.st_register_saving);
            description.setText(R.string.st_register_saving_note);
        } else {
            title.setText(R.string.st_register_success);
            description.setText(R.string.st_register_success_note);
        }

        findViewById(R.id.st_register_retry).setVisibility(
                state.stage == RegistrationViewModel.Stage.PROFILE_ERROR ? View.VISIBLE : View.GONE);
        findViewById(R.id.st_register_copy_uid).setEnabled(!busy && !model.accountUid().isEmpty());
        findViewById(R.id.st_register_sign_out).setEnabled(!busy);
    }

    private void copyUid() {
        String uid = model.accountUid();
        if (uid.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.st_register_uid_label), uid));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.st_register_uid_copied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        if (googleLauncher != null) googleLauncher.close();
        super.onDestroy();
    }
}
