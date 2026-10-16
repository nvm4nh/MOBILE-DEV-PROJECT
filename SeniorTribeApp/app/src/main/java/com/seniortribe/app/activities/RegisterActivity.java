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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.seniortribe.app.R;
import com.seniortribe.app.models.User;
import com.seniortribe.app.utils.Constants;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPhone, etDistrict, etOrg, etPassword;
    private Button btnRegister;
    private TextView tvGoToLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDistrict = findViewById(R.id.etDistrict);
        etOrg = findViewById(R.id.etOrg);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        try {
            mAuth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception ignored) {}

        btnRegister.setOnClickListener(v -> performRegister());
        tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void performRegister() {
        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            Toast.makeText(this, "Vui lòng nhập họ tên, email và mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth != null) {
            btnRegister.setEnabled(false);
            mAuth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        btnRegister.setEnabled(true);
                        if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                            FirebaseUser fbUser = mAuth.getCurrentUser();
                            User u = new User(fbUser.getUid(), email, name, Constants.ROLE_REPORTER);
                            u.setPhone(etPhone.getText().toString().trim());
                            u.setDistrict(etDistrict.getText().toString().trim());
                            u.setOrgName(etOrg.getText().toString().trim());

                            if (firestore != null) {
                                firestore.collection(Constants.COL_USERS).document(fbUser.getUid()).set(u);
                            }

                            Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finish();
                        } else {
                            Toast.makeText(this, "Đăng ký thất bại: " + (task.getException() != null ? task.getException().getMessage() : "Lỗi"), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
}
