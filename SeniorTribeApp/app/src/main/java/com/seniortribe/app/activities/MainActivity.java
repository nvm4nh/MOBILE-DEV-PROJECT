package com.seniortribe.app.activities;

import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import com.seniortribe.app.notifications.DeepLink;
import com.seniortribe.app.notifications.NotificationPrefs;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.firebase.auth.FirebaseUser;
import com.seniortribe.app.R;
import com.seniortribe.app.SeniorTribeApplication;
import com.seniortribe.app.auth.LiveAccessMonitor;
import com.seniortribe.app.fragments.ChatListFragment;
import com.seniortribe.app.fragments.FeedFragment;
import com.seniortribe.app.fragments.NeedListFragment;
import com.seniortribe.app.fragments.ProfileFragment;
import com.seniortribe.app.fragments.ScheduleFragment;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_SESSION_UID = "KEY_SESSION_UID";

    private static final String KEY_DEEPLINK_HANDLED = "KEY_DEEPLINK_HANDLED";

    private boolean redirecting, revalidateOnStart, contentInitialized, deepLinkHandled;
    private String sessionUid = "";
    private int currentNavId = R.id.nav_feed;

    private SeniorTribeApplication app;
    private View mainContent;
    private LinearLayout accessOverlay;

    // E01 · Xin quyền hiện thông báo (Android 13+), chỉ hỏi một lần; đổi lại trong Cài đặt thông báo
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        app = (SeniorTribeApplication) getApplication();

        FirebaseUser user = app.getAuthRepository().currentUser();
        if (user != null) sessionUid = user.getUid();

        boolean allowed = app.hasActiveAccess() && !sessionUid.isEmpty();
        boolean restore = allowed
                && savedInstanceState != null
                && sessionUid.equals(savedInstanceState.getString(KEY_SESSION_UID));

        super.onCreate(restore ? savedInstanceState : null);

        if (!allowed) {
            openSessionGate();
            return;
        }

        setContentView(R.layout.activity_main);

        if (restore) {
            currentNavId = savedInstanceState.getInt("KEY_NAV_ID", R.id.nav_feed);
            deepLinkHandled = savedInstanceState.getBoolean(KEY_DEEPLINK_HANDLED, false);
        }

        contentInitialized = getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer) != null;

        createAccessOverlay();
        setupNavigation();
        app.liveAccess().observe(this, this::renderAccess);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (redirecting) return;

        if (!sameAuthorizedUser()) {
            openSessionGate();
            return;
        }

        if (revalidateOnStart) {
            revalidateOnStart = false;
            app.recheckActiveAccess(sessionUid);
        }
    }

    @Override
    protected void onStop() {
        revalidateOnStart = true;
        showChecking(true);
        super.onStop();
    }

    private boolean sameAuthorizedUser() {
        return app.hasActiveAccess()
                && app.getAuthRepository().isCurrentUser(sessionUid);
    }

    private boolean businessReady() {
        LiveAccessMonitor.State state = app.liveAccess().getValue();

        return !redirecting
                && sameAuthorizedUser()
                && state != null
                && state.stage == LiveAccessMonitor.Stage.GRANTED
                && sessionUid.equals(state.uid);
    }

    private void renderAccess(LiveAccessMonitor.State state) {
        if (redirecting || state == null) return;

        if (!sameAuthorizedUser() || !sessionUid.equals(state.uid)) {
            openSessionGate();
            return;
        }

        if (state.stage == LiveAccessMonitor.Stage.CHECKING) {
            showChecking(true);
        } else if (state.stage == LiveAccessMonitor.Stage.GRANTED) {
            showChecking(false);

            if (!contentInitialized) {
                contentInitialized = loadFragment(getFragmentForId(currentNavId));
            }
            openDeepLink();
            askNotificationPermissionOnce();
        } else {
            openSessionGate();
        }
    }

    private void createAccessOverlay() {
        FrameLayout content = findViewById(android.R.id.content);
        mainContent = content.getChildAt(0);

        accessOverlay = new LinearLayout(this);
        accessOverlay.setOrientation(LinearLayout.VERTICAL);
        accessOverlay.setGravity(Gravity.CENTER);
        accessOverlay.setPadding(dp(24), dp(24), dp(24), dp(24));

        TypedArray colors = obtainStyledAttributes(
                new int[]{android.R.attr.colorBackground}
        );
        accessOverlay.setBackgroundColor(colors.getColor(0, Color.WHITE));
        colors.recycle();

        accessOverlay.setClickable(true);
        accessOverlay.setFocusableInTouchMode(true);

        accessOverlay.addView(
                new ProgressBar(this),
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );

        TextView label = new TextView(this);
        label.setText(R.string.st_access_loading_note);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(16), 0, 0);

        accessOverlay.addView(
                label,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        content.addView(
                accessOverlay,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        showChecking(true);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showChecking(boolean checking) {
        if (mainContent == null || accessOverlay == null) return;

        mainContent.setVisibility(checking ? View.INVISIBLE : View.VISIBLE);
        accessOverlay.setVisibility(checking ? View.VISIBLE : View.GONE);

        if (checking) {
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(mainContent.getWindowToken(), 0);
            }

            accessOverlay.requestFocus();
        }
    }

    // =====================================================================
    // E01 · Mở màn hình từ thông báo
    // =====================================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (DeepLink.has(intent)) {
            setIntent(intent);
            deepLinkHandled = false;
            if (businessReady()) openDeepLink();
        }
    }

    /** Chỉ mở màn hình đích sau khi phiên và quyền tổ chức đã được xác nhận (R01). */
    private void openDeepLink() {
        if (!businessReady() || deepLinkHandled) return;
        Intent target = DeepLink.targetIntent(this, getIntent());
        DeepLink.clear(getIntent());
        deepLinkHandled = true; // xoay màn hình tạo lại Activity với intent cũ: không mở lần nữa
        if (target != null) startActivity(target);
    }

    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        NotificationPrefs prefs = new NotificationPrefs(this);
        if (prefs.permissionAsked()) return;
        prefs.markPermissionAsked();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void openSessionGate() {
        if (redirecting) return;

        redirecting = true;
        showChecking(true);

        Intent intent = new Intent(this, SessionGateActivity.class);
        DeepLink.copy(getIntent(), intent); // giữ đích đến của thông báo qua bước kiểm tra phiên
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("KEY_NAV_ID", currentNavId);
        outState.putString(KEY_SESSION_UID, sessionUid);
        outState.putBoolean(KEY_DEEPLINK_HANDLED, deepLinkHandled);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationRailView navRail = findViewById(R.id.navRail);

        if (bottomNav != null) {
            bottomNav.setSelectedItemId(currentNavId);
            bottomNav.setOnItemSelectedListener(item -> {
                if (!businessReady()) return false;

                currentNavId = item.getItemId();
                return loadFragment(getFragmentForId(currentNavId));
            });
        }

        if (navRail != null) {
            navRail.setSelectedItemId(currentNavId);
            navRail.setOnItemSelectedListener(item -> {
                if (!businessReady()) return false;

                currentNavId = item.getItemId();
                return loadFragment(getFragmentForId(currentNavId));
            });
        }
    }

    private Fragment getFragmentForId(int id) {
        if (id == R.id.nav_needs) return new NeedListFragment();
        if (id == R.id.nav_chat) return new ChatListFragment();
        if (id == R.id.nav_schedule) return new ScheduleFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new FeedFragment();
    }

    private boolean loadFragment(Fragment fragment) {
        if (!businessReady() || getSupportFragmentManager().isStateSaved()) {
            return false;
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();

        return true;
    }
}