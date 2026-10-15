package com.seniortribe.app.activities;

import android.content.res.Configuration;
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

    private int currentNavId = R.id.nav_feed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
