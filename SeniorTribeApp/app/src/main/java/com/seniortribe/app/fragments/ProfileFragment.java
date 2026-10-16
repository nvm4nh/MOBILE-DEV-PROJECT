package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.LoginActivity;

public class ProfileFragment extends Fragment {

    private TextView tvProfileName, tvProfileRole, tvProfileEmail;
    private Button btnLogout;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileRole = view.findViewById(R.id.tvProfileRole);
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail);
        btnLogout = view.findViewById(R.id.btnLogout);

        try { mAuth = FirebaseAuth.getInstance(); } catch (Exception ignored) {}

        if (mAuth != null && mAuth.getCurrentUser() != null) {
            FirebaseUser u = mAuth.getCurrentUser();
            tvProfileEmail.setText("Email: " + u.getEmail());
            tvProfileName.setText(u.getDisplayName() != null ? u.getDisplayName() : "Cộng tác viên");
        }

        btnLogout.setOnClickListener(v -> {
            if (mAuth != null) mAuth.signOut();
            Toast.makeText(requireContext(), "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }
}
