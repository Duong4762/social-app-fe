package com.example.social_app;

import android.os.Bundle;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.fragments.ChatDetailFragment;
import com.example.social_app.fragments.SearchFragment;
import com.example.social_app.fragments.HomeFragment;
import com.example.social_app.fragments.MessagesFragment;
import com.example.social_app.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private View customBottomNav;
    private ImageButton navBtnHome, navBtnSearch, navBtnMessage, navBtnNotifications, navBtnProfile, navBtnSettings;
    private View notificationBadge;
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

        fragmentManager.addOnBackStackChangedListener(this::syncChatOverlayVisibility);

        // Load home fragment by default
        if (savedInstanceState == null) {
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
            selectNavButton(navBtnHome);
        }

        // Show notification badge on notifications icon (demo)
        showNotificationBadge();
    }

    /**
     * Initialize and set up click listeners for all navigation buttons.
     */
    private void initializeNavigationButtons() {
        navBtnHome = customBottomNav.findViewById(R.id.nav_btn_home);
        navBtnSearch = customBottomNav.findViewById(R.id.nav_btn_search);
        navBtnMessage = customBottomNav.findViewById(R.id.nav_btn_message);
        navBtnNotifications = customBottomNav.findViewById(R.id.nav_btn_notifications);
        navBtnProfile = customBottomNav.findViewById(R.id.nav_btn_profile);
        navBtnSettings = customBottomNav.findViewById(R.id.nav_btn_settings);
        notificationBadge = customBottomNav.findViewById(R.id.notification_badge);

        // Set click listeners
        navBtnHome.setOnClickListener(v -> {
            selectNavButton(navBtnHome);
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
        });

        navBtnSearch.setOnClickListener(v -> {
            selectNavButton(navBtnSearch);
            loadFragment(new SearchFragment());
        });

        navBtnMessage.setOnClickListener(v -> {
            selectNavButton(navBtnMessage);
            loadFragment(new MessagesFragment());
        });

        navBtnNotifications.setOnClickListener(v -> {
            selectNavButton(navBtnNotifications);
            removeNotificationBadge();
            showToast("Notifications not yet implemented");
        });

        navBtnProfile.setOnClickListener(v -> {
            selectNavButton(navBtnProfile);
            showToast("Profile not yet implemented");
        });

        navBtnSettings.setOnClickListener(v -> {
            selectNavButton(navBtnSettings);
            loadFragment(new SettingsFragment());
        });
    }

    /**
     * Select and highlight the specified navigation button.
     */
    private void selectNavButton(ImageButton button) {
        // Reset all buttons to normal state
        navBtnHome.setSelected(false);
        navBtnSearch.setSelected(false);
        navBtnMessage.setSelected(false);
        navBtnNotifications.setSelected(false);
        navBtnProfile.setSelected(false);
        navBtnSettings.setSelected(false);

        // Set the selected button
        button.setSelected(true);
        currentSelectedNav = button.getId();
    }

    /**
     * Shows a notification badge on the message navigation button.
     */
    public void showNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Removes the notification badge from the message navigation button.
     */
    public void removeNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisibility(View.GONE);
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
     * Mở chat toàn màn hình chồng lên nội dung + bottom nav; bấm back trên hệ thống để đóng.
     */
    public void openChatDetail(
            @NonNull String conversationId,
            @NonNull String peerName,
            @Nullable String peerAvatarUrl,
            @NonNull String peerUserId) {
        View overlay = findViewById(R.id.full_screen_chat_overlay);
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
        }
        ChatDetailFragment chat = ChatDetailFragment.newInstance(
                conversationId, peerName, peerAvatarUrl, peerUserId);
        fragmentManager.beginTransaction()
                .replace(R.id.full_screen_chat_overlay, chat)
                .addToBackStack("chat_detail")
                .commit();
        fragmentManager.executePendingTransactions();
        syncChatOverlayVisibility();
    }

    private void syncChatOverlayVisibility() {
        View overlay = findViewById(R.id.full_screen_chat_overlay);
        if (overlay == null) {
            return;
        }
        Fragment f = fragmentManager.findFragmentById(R.id.full_screen_chat_overlay);
        overlay.setVisibility(f != null ? View.VISIBLE : View.GONE);
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