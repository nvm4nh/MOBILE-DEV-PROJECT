package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.TextInputEditText;
import com.seniortribe.app.R;
import com.seniortribe.app.auth.AuthFields;
import com.seniortribe.app.auth.GoogleSignInLauncher;
import com.seniortribe.app.auth.LoginViewModel;

public class LoginActivity extends AppCompatActivity {
    private LoginViewModel model;
    private boolean routed;
    private View[] controls;
    private GoogleSignInLauncher googleLauncher;

    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_SeniorTribe_Auth);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        model = new ViewModelProvider(this).get(LoginViewModel.class);
        googleLauncher = new GoogleSignInLauncher(this, model);
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
        google.setOnClickListener(v -> googleLauncher.launch());
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

    @Override protected void onDestroy() {
        if (googleLauncher != null) googleLauncher.close();
        super.onDestroy();
    }
}
