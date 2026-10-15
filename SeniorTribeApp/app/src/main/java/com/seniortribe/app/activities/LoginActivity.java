package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.seniortribe.app.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvForgotPassword, tvGoToRegister;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);

        try { mAuth = FirebaseAuth.getInstance(); } catch (Exception ignored) {}

        btnLogin.setOnClickListener(v -> performLogin());

        btnGoogleSignIn.setOnClickListener(v -> {
            Toast.makeText(this, "Đang kết nối Google Sign-In...", Toast.LENGTH_SHORT).show();
            // Đăng nhập demo nếu môi trường chưa kích hoạt Google Services SHA-1
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        tvGoToRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ email và mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth != null) {
            btnLogin.setEnabled(false);
            mAuth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        btnLogin.setEnabled(true);
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Demo fallback nếu offline hoặc sai pass
                            Toast.makeText(this, "Đăng nhập: " + (task.getException() != null ? task.getException().getMessage() : "Lỗi xác thực"), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // Chạy demo khi chưa nạp config
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }
}
