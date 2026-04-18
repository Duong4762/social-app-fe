package com.example.social_app;

import android.os.Bundle;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.fragments.HomeFragment;

/**
 * MainActivity is the main entry point for the SocialNine application.
 * It manages fragment navigation and the custom bottom navigation menu with hide-on-scroll behavior.
 */
public class MainActivity extends AppCompatActivity {

    private View customBottomNav;
    private ImageButton navBtnHome, navBtnSearch, navBtnAdd, navBtnMessage, navBtnProfile;
    private View messageBadge;
    private FragmentManager fragmentManager;
    private boolean isBottomNavVisible = true;
    private int currentSelectedNav = R.id.nav_btn_home;

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

        // Initialize views
        fragmentManager = getSupportFragmentManager();
        customBottomNav = findViewById(R.id.custom_bottom_nav);
        initializeNavigationButtons();

        // Load home fragment by default
        if (savedInstanceState == null) {
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
            selectNavButton(navBtnHome);
        }

        // Show notification badge on message icon (demo)
        showMessageBadge();
    }

    /**
     * Initialize and set up click listeners for all navigation buttons.
     */
    private void initializeNavigationButtons() {
        navBtnHome = customBottomNav.findViewById(R.id.nav_btn_home);
        navBtnSearch = customBottomNav.findViewById(R.id.nav_btn_search);
        navBtnAdd = customBottomNav.findViewById(R.id.nav_btn_add);
        navBtnMessage = customBottomNav.findViewById(R.id.nav_btn_message);
        navBtnProfile = customBottomNav.findViewById(R.id.nav_btn_profile);
        messageBadge = customBottomNav.findViewById(R.id.message_badge);

        // Set click listeners
        navBtnHome.setOnClickListener(v -> {
            selectNavButton(navBtnHome);
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
        });

        navBtnSearch.setOnClickListener(v -> {
            selectNavButton(navBtnSearch);
            showToast("Search not yet implemented");
        });

        navBtnAdd.setOnClickListener(v -> {
            selectNavButton(navBtnAdd);
            android.util.Log.d("MainActivity", "Add button clicked - opening NewPostFragment");
            openNewPostFragment();
        });

        navBtnMessage.setOnClickListener(v -> {
            selectNavButton(navBtnMessage);
            removeMessageBadge();
            showToast("Messages not yet implemented");
        });

        navBtnProfile.setOnClickListener(v -> {
            selectNavButton(navBtnProfile);
            showToast("Profile not yet implemented");
        });
    }

    /**
     * Select and highlight the specified navigation button.
     */
    private void selectNavButton(ImageButton button) {
        // Reset all buttons to normal state
        navBtnHome.setSelected(false);
        navBtnSearch.setSelected(false);
        navBtnAdd.setSelected(false);
        navBtnMessage.setSelected(false);
        navBtnProfile.setSelected(false);

        // Set the selected button
        button.setSelected(true);
        currentSelectedNav = button.getId();
    }

    /**
     * Shows a notification badge on the message navigation button.
     */
    public void showMessageBadge() {
        if (messageBadge != null) {
            messageBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Removes the notification badge from the message navigation button.
     */
    public void removeMessageBadge() {
        if (messageBadge != null) {
            messageBadge.setVisibility(View.GONE);
        }
    }

    /**
     * Sets up the scroll listener for the RecyclerView to hide/show bottom navigation.
     *
     * @param recyclerView The RecyclerView to attach the scroll listener to
     */
    public void setupScrollListener(RecyclerView recyclerView) {
        if (recyclerView != null && customBottomNav != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

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
        int height = customBottomNav.getHeight();
        TranslateAnimation slideDown = new TranslateAnimation(0, 0, 0, height);
        slideDown.setDuration(300);
        slideDown.setFillAfter(true);
        customBottomNav.startAnimation(slideDown);
        customBottomNav.setVisibility(View.GONE);
    }

    /**
     * Shows the bottom navigation bar with animation.
     */
    private void showBottomNavigation() {
        isBottomNavVisible = true;
        customBottomNav.setVisibility(View.VISIBLE);
        int height = customBottomNav.getHeight();
        TranslateAnimation slideUp = new TranslateAnimation(0, 0, height, 0);
        slideUp.setDuration(300);
        slideUp.setFillAfter(true);
        customBottomNav.startAnimation(slideUp);
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

    /**
     * Opens the NewPostFragment for creating a new post.
     * This is called when the Add button in the bottom navigation is clicked.
     */
    private void openNewPostFragment() {
        android.util.Log.d("MainActivity", "openNewPostFragment() called");

        com.example.social_app.fragments.NewPostFragment newPostFragment =
                new com.example.social_app.fragments.NewPostFragment();

        fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, newPostFragment)
                .addToBackStack(null)
                .commit();

        android.util.Log.d("MainActivity", "NewPostFragment opened successfully");
    }
}