package com.seniortribe.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.seniortribe.app.R;
import com.seniortribe.app.auth.SessionGateViewModel;
import com.seniortribe.app.fragments.ProfileFragment;

public class SessionGateActivity extends AppCompatActivity {
    private SessionGateViewModel model;
    private boolean routed;
    private String uid = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_SeniorTribe_Auth);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_gate);
        model = new ViewModelProvider(this).get(SessionGateViewModel.class);
        findViewById(R.id.st_access_retry).setOnClickListener(v -> model.refresh());
        findViewById(R.id.st_access_sign_out).setOnClickListener(v -> model.signOut());
        findViewById(R.id.st_access_copy_uid).setOnClickListener(v -> copyUid());
        findViewById(R.id.st_access_profile).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("account_profile") != null) return;
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.st_access_profile_container, new ProfileFragment(), "account_profile")
                    .addToBackStack("account_profile").commit();
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            updateProfileVisibility();
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) model.refresh();
        });
        updateProfileVisibility();
        model.state().observe(this, this::render);
    }

    private void render(SessionGateViewModel.State state) {
        if (state.stage == SessionGateViewModel.Stage.SIGNED_OUT) {
            route(LoginActivity.class);
            return;
        }
        if (state.stage == SessionGateViewModel.Stage.GRANTED) {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) route(MainActivity.class);
            return;
        }
        uid = state.uid;
        boolean loading = state.stage == SessionGateViewModel.Stage.LOADING;
        boolean error = state.stage == SessionGateViewModel.Stage.ERROR;
        findViewById(R.id.st_access_lock).setVisibility(!loading && !error ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.st_access_uid)).setText(uid);
        ((TextView) findViewById(R.id.st_access_title)).setText(loading
                ? R.string.st_access_loading_title : error
                ? R.string.st_access_error_title : R.string.st_access_pending_title);
        ((TextView) findViewById(R.id.st_access_description)).setText(loading
                ? R.string.st_access_loading_note : error
                ? state.message : R.string.st_access_pending_note);
        findViewById(R.id.st_access_progress).setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.st_access_retry).setEnabled(!loading);
        findViewById(R.id.st_access_copy_uid).setEnabled(!uid.isEmpty());
        findViewById(R.id.st_access_profile).setEnabled(!loading && !uid.isEmpty());
    }

    private void updateProfileVisibility() {
        boolean showing = getSupportFragmentManager().getBackStackEntryCount() > 0;
        findViewById(R.id.st_access_content).setVisibility(showing ? View.GONE : View.VISIBLE);
        findViewById(R.id.st_access_profile_container).setVisibility(showing ? View.VISIBLE : View.GONE);
    }

    private void route(Class<?> destination) {
        if (routed) return;
        routed = true;
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void copyUid() {
        if (uid.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.st_register_uid_label), uid));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.st_register_uid_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
