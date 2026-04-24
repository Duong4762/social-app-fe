package com.example.social_app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.social_app.fragments.AdminDashboardFragment;
import com.example.social_app.fragments.AdminManageUsersFragment;
import com.example.social_app.fragments.AdminSettingsFragment;
import com.example.social_app.fragments.AdminStatsFragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminActivity extends AppCompatActivity implements AdminDashboardFragment.DashboardListener {

    private BottomNavigationView adminBottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        View root = findViewById(R.id.adminRoot);
        adminBottomNav = findViewById(R.id.adminBottomNav);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            adminBottomNav.setPadding(
                    adminBottomNav.getPaddingLeft(),
                    adminBottomNav.getPaddingTop(),
                    adminBottomNav.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });

        adminBottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_admin_dashboard) {
                loadFragment(new AdminDashboardFragment(), true);
                return true;
            }
            if (itemId == R.id.nav_admin_users) {
                loadFragment(new AdminManageUsersFragment(), true);
                return true;
            }
            if (itemId == R.id.nav_admin_stats) {
                loadFragment(new AdminStatsFragment(), true);
                return true;
            }
            if (itemId == R.id.nav_admin_settings) {  // THÊM DÒNG NÀY
                loadFragment(new AdminSettingsFragment(), true);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            adminBottomNav.setSelectedItemId(R.id.nav_admin_dashboard);
        }
    }

    private void loadFragment(Fragment fragment, boolean animate) {
        if (animate) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.adminFragmentContainer, fragment)
                    .commit();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .commit();
    }

    @Override
    public void onReportCountChanged(int count) {
        if (adminBottomNav == null) {
            return;
        }
        if (count <= 0) {
            adminBottomNav.removeBadge(R.id.nav_admin_dashboard);
            return;
        }
        BadgeDrawable badge = adminBottomNav.getOrCreateBadge(R.id.nav_admin_dashboard);
        badge.setVisible(true);
        badge.setMaxCharacterCount(2);
        badge.setNumber(Math.min(count, 99));
    }
}