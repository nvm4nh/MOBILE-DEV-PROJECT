package com.seniortribe.app.activities;

import android.content.Intent;
import com.google.firebase.auth.FirebaseAuth;
import com.seniortribe.app.SeniorTribeApplication;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.seniortribe.app.R;
import com.seniortribe.app.fragments.ChatListFragment;
import com.seniortribe.app.fragments.FeedFragment;
import com.seniortribe.app.fragments.NeedListFragment;
import com.seniortribe.app.fragments.ProfileFragment;
import com.seniortribe.app.fragments.ScheduleFragment;

public class MainActivity extends AppCompatActivity {

    private boolean redirecting;
    private FirebaseAuth.AuthStateListener authListener;
    private int currentNavId = R.id.nav_feed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean allowed = ((SeniorTribeApplication) getApplication()).hasActiveAccess();
        // Do not restore business fragments before the account has passed the gate.
        super.onCreate(allowed ? savedInstanceState : null);
        if (!allowed) {
            openSessionGate();
            return;
        }
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            currentNavId = savedInstanceState.getInt("KEY_NAV_ID", R.id.nav_feed);
        }

        setupNavigation();

        if (savedInstanceState == null) {
            loadFragment(getFragmentForId(currentNavId));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!((SeniorTribeApplication) getApplication()).hasActiveAccess()) openSessionGate();
        authListener = auth -> {
            if (!((SeniorTribeApplication) getApplication()).hasActiveAccess()) openSessionGate();
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    @Override
    protected void onStop() {
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null;
        super.onStop();
    }

    private void openSessionGate() {
        if (redirecting) return;
        redirecting = true;
        Intent intent = new Intent(this, SessionGateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("KEY_NAV_ID", currentNavId);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationRailView navRail = findViewById(R.id.navRail);

        if (bottomNav != null) {
            bottomNav.setSelectedItemId(currentNavId);
            bottomNav.setOnItemSelectedListener(item -> {
                currentNavId = item.getItemId();
                return loadFragment(getFragmentForId(currentNavId));
            });
        }

        if (navRail != null) {
            navRail.setSelectedItemId(currentNavId);
            navRail.setOnItemSelectedListener(item -> {
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
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}
