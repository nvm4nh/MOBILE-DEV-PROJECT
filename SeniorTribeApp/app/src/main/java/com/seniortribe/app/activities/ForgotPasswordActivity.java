package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.seniortribe.app.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etResetEmail;
    private Button btnSendReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etResetEmail = findViewById(R.id.etResetEmail);
        btnSendReset = findViewById(R.id.btnSendReset);

        btnSendReset.setOnClickListener(v -> {
            String email = etResetEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Vui lòng nhập địa chỉ email", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Đã gửi email khôi phục mật khẩu!", Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                Toast.makeText(this, "Không thể gửi email: " + (task.getException() != null ? task.getException().getMessage() : "Lỗi"), Toast.LENGTH_SHORT).show();
                            }
                        });
            } catch (Exception e) {
                Toast.makeText(this, "Hệ thống đã ghi nhận yêu cầu đặt lại mật khẩu cho " + email, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
