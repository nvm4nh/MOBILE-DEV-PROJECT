package com.seniortribe.app.activities;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.TextInputEditText;
import com.seniortribe.app.R;
import com.seniortribe.app.auth.AuthFields;
import com.seniortribe.app.auth.PasswordResetViewModel;

public class ForgotPasswordActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_SeniorTribe_Auth);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        PasswordResetViewModel model = new ViewModelProvider(this).get(PasswordResetViewModel.class);
        TextInputEditText email = AuthFields.bind(this, R.id.st_reset_email, model.email, v -> model.email = v);
        View submit = findViewById(R.id.st_reset_submit);
        submit.setOnClickListener(v -> model.submit());
        findViewById(R.id.st_reset_back).setOnClickListener(v -> finish());
        email.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            model.submit();
            return true;
        });
        model.state().observe(this, state -> {
            boolean done = state.stage == PasswordResetViewModel.Stage.DONE;
            email.setEnabled(!model.busy() && !done);
            submit.setEnabled(!model.busy() && !done);
            findViewById(R.id.st_reset_progress).setVisibility(model.busy() ? View.VISIBLE : View.GONE);
            TextView message = findViewById(R.id.st_reset_message);
            message.setVisibility(state.message == 0 ? View.GONE : View.VISIBLE);
            if (state.message != 0) message.setText(state.message);
            TextView result = findViewById(R.id.st_reset_result);
            result.setVisibility(done ? View.VISIBLE : View.GONE);
            if (done) result.setText(getString(R.string.st_reset_success_note, model.maskedEmail()));
        });
    }
}
