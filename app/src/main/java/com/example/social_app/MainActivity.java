package com.example.social_app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.fragments.ChatDetailFragment;
import com.example.social_app.fragments.SearchFragment;
import com.example.social_app.fragments.HomeFragment;
import com.example.social_app.fragments.NotificationFragment;
import com.example.social_app.fragments.ProfileFragment;
import com.example.social_app.fragments.SearchFragment;
import com.example.social_app.fragments.MessagesFragment;
import com.example.social_app.fragments.SettingsFragment;
import com.example.social_app.utils.LanguageUtils;
import com.example.social_app.utils.ThemeUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.SetOptions;

import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private ImageButton navBtnHome, navBtnSearch, navBtnMessage, navBtnNotifications, navBtnProfile, navBtnSettings;
    private View customBottomNav;
    private View notificationBadge;
    private FragmentManager fragmentManager;
    private boolean isBottomNavVisible = true;
    private int currentSelectedNav = R.id.nav_btn_home;
    private ListenerRegistration notificationListener;

    @Override
    protected void onStart() {
        super.onStart();
        updateCurrentUserActiveStatus(true);
    }

    @Override
    protected void onStop() {
        updateCurrentUserActiveStatus(false);
        stopNotificationListener();
        super.onStop();
    }

    private void stopNotificationListener() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        checkNotificationPermission();

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
        
        // Bắt đầu lắng nghe thông báo Realtime
        setupRealtimeNotificationListener();
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

        // ==================== HOME ====================
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
            loadFragment(new NotificationFragment());  // ✅ Đã sửa
        });

        navBtnProfile.setOnClickListener(v -> {
            selectNavButton(navBtnProfile);
            loadFragment(new ProfileFragment());
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
     * Shows a notification badge on the notification navigation button.
     */
    public void showNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Removes the notification badge from the notification navigation button.
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
        if (!isBottomNavVisible) return;
        isBottomNavVisible = false;
        
        customBottomNav.animate()
                .translationY(customBottomNav.getHeight())
                .setDuration(300)
                .withEndAction(() -> {
                    customBottomNav.setVisibility(View.GONE);
                    customBottomNav.setClickable(false);
                })
                .start();
    }

    /**
     * Shows the bottom navigation bar with animation.
     */
    private void showBottomNavigation() {
        if (isBottomNavVisible) return;
        isBottomNavVisible = true;
        
        customBottomNav.setVisibility(View.VISIBLE);
        customBottomNav.setClickable(true);
        customBottomNav.animate()
                .translationY(0)
                .setDuration(300)
                .start();
    }

    public void setBottomNavigationHiddenForOverlay(boolean hidden) {
        if (hidden) {
            hideBottomNavigation();
        } else {
            showBottomNavigation();
        }
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
     * Checks and requests notification permission for Android 13+.
     */
    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showToast("Notification permission granted");
            } else {
                showToast("Notification permission denied. You won't receive push notifications.");
            }
        }
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

    private void updateCurrentUserActiveStatus(boolean isActive) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("isActive", isActive);
        payload.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseManager.getInstance()
                .getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(currentUser.getUid())
                .set(payload, SetOptions.merge());
    }

    /**
     * Lắng nghe thông báo mới từ Firestore và hiển thị cho người dùng.
     */
    private void setupRealtimeNotificationListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        stopNotificationListener(); // Xóa listener cũ nếu có

        notificationListener = FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("isRead", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("MainActivity", "Listen failed.", e);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        showNotificationBadge();
                        
                        for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                String type = dc.getDocument().getString("type");
                                String actorId = dc.getDocument().getString("actorId");
                                
                                fetchActorAndNotify(actorId, type);
                            }
                        }
                    }
                });
    }

    private void fetchActorAndNotify(String actorId, String type) {
        if (actorId == null) {
            sendNotificationWithActorName("Ai đó", type);
            return;
        }

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(actorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String actorName = "Ai đó";
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        String username = documentSnapshot.getString("username");
                        actorName = (fullName != null && !fullName.isEmpty()) ? fullName : (username != null ? username : "Ai đó");
                    }
                    sendNotificationWithActorName(actorName, type);
                })
                .addOnFailureListener(e -> {
                    sendNotificationWithActorName("Ai đó", type);
                });
    }

    private void sendNotificationWithActorName(String actorName, String type) {
        String message = actorName + " đã tương tác với bạn";
        
        if ("LIKE".equals(type)) {
            message = actorName + " đã thích bài viết của bạn";
        } else if ("COMMENT".equals(type)) {
            message = actorName + " đã bình luận về bài viết của bạn";
        } else if ("FOLLOW".equals(type)) {
            message = actorName + " đã bắt đầu theo dõi bạn";
        } else if ("LIKE_COMMENT".equals(type)) {
            message = actorName + " đã thích bình luận của bạn";
        } else if ("REPLY_COMMENT".equals(type)) {
            message = actorName + " đã trả lời bình luận của bạn";
        } else if ("MESSAGE".equals(type)) {
            message = actorName + " đã gửi cho bạn một tin nhắn";
        }
        
        sendLocalNotification("Social App", message);
    }

    private void sendLocalNotification(String title, String body) {
        String channelId = "social_notifications";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Thông báo hệ thống",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications) // Đảm bảo dùng đúng icon vector nếu có
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC) // Hiển thị trên màn hình khóa
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL) // Âm thanh + Rung
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
