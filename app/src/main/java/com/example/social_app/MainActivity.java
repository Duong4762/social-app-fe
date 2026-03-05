package com.example.social_app;

import android.os.Bundle;
import android.view.View;
import android.view.animation.TranslateAnimation;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.fragments.HomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * MainActivity is the main entry point for the SocialNine application.
 * It manages fragment navigation and the bottom navigation menu with hide-on-scroll behavior.
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FragmentManager fragmentManager;
    private boolean isBottomNavVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize fragment manager and bottom navigation
        fragmentManager = getSupportFragmentManager();
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Load home fragment by default
        if (savedInstanceState == null) {
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        // Set up bottom navigation listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                HomeFragment homeFragment = new HomeFragment();
                homeFragment.setBottomNavigationCallback(this::setupScrollListener);
                loadFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                showToast("Search not yet implemented");
                return true;
            } else if (itemId == R.id.nav_add) {
                showToast("Post creation not yet implemented");
                return true;
            } else if (itemId == R.id.nav_message) {
                showToast("Messages not yet implemented");
                // Remove badge when message is opened
                removeMessageBadge();
                return true;
            } else if (itemId == R.id.nav_profile) {
                showToast("Profile not yet implemented");
                return true;
            }
            return false;
        });

        // Show notification badge on message icon (demo)
        showMessageBadge();
    }

    /**
     * Shows a notification badge on the message navigation item.
     */
    public void showMessageBadge() {
        com.google.android.material.badge.BadgeDrawable badge =
                com.google.android.material.badge.BadgeDrawable.create(this);
        badge.setNumber(1);
        badge.setBackgroundColor(getResources().getColor(R.color.accent_red, null));
        bottomNavigationView.getOrCreateBadge(R.id.nav_message).setVisible(true);
    }

    /**
     * Removes the notification badge from the message navigation item.
     */
    public void removeMessageBadge() {
        bottomNavigationView.removeBadge(R.id.nav_message);
    }

    /**
     * Sets up the scroll listener for the RecyclerView to hide/show bottom navigation.
     *
     * @param recyclerView The RecyclerView to attach the scroll listener to
     */
    public void setupScrollListener(RecyclerView recyclerView) {
        if (recyclerView != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                private int scrollDy = 0;

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    scrollDy += dy;

                    // If user scrolls down, hide the bottom navigation
                    if (dy > 0 && isBottomNavVisible) {
                        hideBottomNavigation();
                    }
                    // If user scrolls up, show the bottom navigation
                    else if (dy < 0 && !isBottomNavVisible) {
                        showBottomNavigation();
                    }
                }
            });
        }
    }

    /**
     * Hides the bottom navigation bar with animation.
     */
    private void hideBottomNavigation() {
        isBottomNavVisible = false;
        int height = bottomNavigationView.getHeight();
        TranslateAnimation slideDown = new TranslateAnimation(0, 0, 0, height);
        slideDown.setDuration(300);
        slideDown.setFillAfter(true);
        bottomNavigationView.startAnimation(slideDown);
        bottomNavigationView.setVisibility(View.GONE);
    }

    /**
     * Shows the bottom navigation bar with animation.
     */
    private void showBottomNavigation() {
        isBottomNavVisible = true;
        bottomNavigationView.setVisibility(View.VISIBLE);
        int height = bottomNavigationView.getHeight();
        TranslateAnimation slideUp = new TranslateAnimation(0, 0, height, 0);
        slideUp.setDuration(300);
        slideUp.setFillAfter(true);
        bottomNavigationView.startAnimation(slideUp);
    }

    /**
     * Loads the specified fragment into the fragment container.
     *
     * @param fragment The fragment to load
     */
    private void loadFragment(Fragment fragment) {
        fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }

    /**
     * Shows a temporary toast message.
     *
     * @param message The message to display
     */
    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}