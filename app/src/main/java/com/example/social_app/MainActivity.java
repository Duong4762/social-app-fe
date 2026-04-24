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
import com.example.social_app.fragments.OtherProfileFragment;
import com.example.social_app.fragments.ProfileFragment;
import com.example.social_app.fragments.MessagesFragment;
import com.example.social_app.fragments.SettingsFragment;
import com.example.social_app.utils.LanguageUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
        // Không dừng listener ở đây để tiếp tục nhận thông báo khi app ở background/màn hình khóa
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopNotificationListener();
        super.onDestroy();
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

        // Lưu Token để nhận thông báo
        saveFCMToken();

        // Xử lý điều hướng nếu được mở từ thông báo
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent == null) return;
        
        String type = intent.getStringExtra("NOTIF_TYPE");
        String refId = intent.getStringExtra("REF_ID");
        String notifId = intent.getStringExtra("NOTIF_ID");
        
        if (notifId != null) {
            markNotificationAsRead(notifId);
        }

        if (type != null && refId != null) {
            if ("MESSAGE".equals(type)) {
                String actorName = intent.getStringExtra("ACTOR_NAME");
                String actorAvatar = intent.getStringExtra("ACTOR_AVATAR");
                String actorId = intent.getStringExtra("ACTOR_ID");
                openChatDetail(refId, actorName, actorAvatar, actorId);
            } else if ("FOLLOW".equals(type)) {
                String actorId = intent.getStringExtra("ACTOR_ID");
                if (actorId != null) {
                    openOtherProfile(actorId);
                }
            } else if ("COMMENT".equals(type) || "LIKE".equals(type) || "REPLY_COMMENT".equals(type) || "LIKE_COMMENT".equals(type)) {
                // Mở Home và tự động hiện comment của bài viết
                selectNavButton(navBtnHome);
                HomeFragment homeFragment = HomeFragment.newInstance(refId);
                homeFragment.setBottomNavigationCallback(this::setupScrollListener);
                loadFragment(homeFragment);
            }
            // Clear intent extras after handling
            intent.removeExtra("NOTIF_TYPE");
            intent.removeExtra("REF_ID");
            intent.removeExtra("NOTIF_ID");
        }
    }

    private void markNotificationAsRead(String notifId) {
        if (notifId == null) return;
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notifId)
                .update("isRead", true)
                .addOnFailureListener(e -> Log.e("MainActivity", "Failed to mark notif as read", e));
    }

    public void markAllNotificationsAsRead() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) return;
                    com.google.firebase.firestore.WriteBatch batch = FirebaseManager.getInstance().getFirestore().batch();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.update(doc.getReference(), "isRead", true);
                    }
                    batch.commit().addOnFailureListener(e -> Log.e("MainActivity", "Failed to mark all as read", e));
                })
                .addOnFailureListener(e -> Log.e("MainActivity", "Error fetching unread notifications", e));
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
            markAllNotificationsAsRead(); // Đánh dấu tất cả đã đọc khi vào tab
            loadFragment(new NotificationFragment());
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

    public void openOtherProfile(String userId) {
        OtherProfileFragment fragment = OtherProfileFragment.newInstance(userId);
        fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
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
    public void loadFragment(Fragment fragment) {
        if (fragment == null) return;
        
        // Tránh load lại chính fragment đang hiển thị
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.nav_host_fragment);
        if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) {
            return;
        }

        fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
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

    private boolean isInitialLoad = true;

    /**
     * Lắng nghe thông báo mới từ Firestore và hiển thị cho người dùng.
     */
    private void setupRealtimeNotificationListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        // Nếu đã có listener thì không tạo thêm
        if (notificationListener != null) return;

        isInitialLoad = true; 

        notificationListener = FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("isRead", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("MainActivity", "Listen failed.", e);
                        return;
                    }

                    if (snapshots != null) {
                        // Cập nhật Badge dựa trên tổng số thông báo chưa đọc
                        if (!snapshots.isEmpty()) {
                            showNotificationBadge();
                            Log.d("MainActivity", "Có " + snapshots.size() + " thông báo chưa đọc.");
                        } else {
                            removeNotificationBadge();
                        }
                        
                        // Xử lý hiển thị thông báo hệ thống (Banner)
                        for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                // Nếu là lần load đầu tiên (snapshot chứa các thông báo cũ), bỏ qua việc hiện banner
                                if (isInitialLoad) {
                                    continue;
                                }

                                // Chỉ hiện banner cho thông báo thực sự mới phát sinh
                                com.google.firebase.firestore.QueryDocumentSnapshot doc = dc.getDocument();
                                String type = doc.getString("type");
                                String actorId = doc.getString("actorId");
                                String referenceId = doc.getString("referenceId");
                                String notifId = doc.getId();
                                
                                fetchActorAndNotify(actorId, type, referenceId, notifId);
                            }
                        }
                        
                        // Đánh dấu đã qua lần tải đầu tiên
                        if (isInitialLoad) {
                            isInitialLoad = false;
                        }
                    }
                });
    }

    private void fetchActorAndNotify(String actorId, String type, String referenceId, String notifId) {
        if (actorId == null) {
            sendNotificationWithActorName("Ai đó", type, referenceId, notifId);
            return;
        }

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(actorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String actorName = "Ai đó";
                    String actorAvatar = null;
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        String username = documentSnapshot.getString("username");
                        actorName = (fullName != null && !fullName.isEmpty()) ? fullName : (username != null ? username : "Ai đó");
                        actorAvatar = documentSnapshot.getString("avatarUrl");
                    }
                    sendNotificationWithActorName(actorName, type, referenceId, actorAvatar, actorId, notifId);
                })
                .addOnFailureListener(e -> {
                    sendNotificationWithActorName("Ai đó", type, referenceId, notifId);
                });
    }

    private void sendNotificationWithActorName(String actorName, String type, String referenceId, String notifId) {
        sendNotificationWithActorName(actorName, type, referenceId, null, null, notifId);
    }

    private void sendNotificationWithActorName(String actorName, String type, String referenceId, String actorAvatar, String actorId, String notifId) {
        String message;
        
        if ("LIKE".equals(type)) {
            message = actorName + " " + getString(R.string.notification_like);
        } else if ("COMMENT".equals(type)) {
            message = actorName + " " + getString(R.string.notification_comment);
        } else if ("FOLLOW".equals(type)) {
            message = actorName + " " + getString(R.string.notification_follow);
        } else if ("LIKE_COMMENT".equals(type)) {
            message = actorName + " " + getString(R.string.notification_like_comment);
        } else if ("REPLY_COMMENT".equals(type)) {
            message = actorName + " " + getString(R.string.notification_reply_comment);
        } else if ("MESSAGE".equals(type)) {
            message = actorName + " " + getString(R.string.notification_message);
        } else {
            message = actorName + " " + getString(R.string.action_success);
        }
        
        sendLocalNotification(getString(R.string.app_name), message, type, referenceId, actorName, actorAvatar, actorId, notifId);
    }

    private void sendLocalNotification(String title, String body, String type, String referenceId, String actorName, String actorAvatar, String actorId, String notifId) {
        String channelId = "social_notifications";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Dùng hashCode của notifId để mỗi thông báo là duy nhất, không bị ghi đè khi nhiều người tương tác cùng lúc
        // Dùng Math.abs để đảm bảo ID không âm
        int notificationId = (notifId != null) ? Math.abs(notifId.hashCode()) : (int) System.currentTimeMillis();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            if (channel == null) {
                channel = new NotificationChannel(channelId,
                        getString(R.string.notification_title),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Social notifications channel");
                channel.enableLights(true);
                channel.setLightColor(android.graphics.Color.BLUE);
                channel.enableVibration(true);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Tạo Intent để mở App khi nhấn vào thông báo
        android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        intent.putExtra("NOTIF_TYPE", type);
        intent.putExtra("REF_ID", referenceId);
        intent.putExtra("ACTOR_NAME", actorName);
        intent.putExtra("ACTOR_AVATAR", actorAvatar);
        intent.putExtra("ACTOR_ID", actorId);
        intent.putExtra("NOTIF_ID", notifId);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, notificationId, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Xây dựng giao diện thông báo
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, false) // Tăng khả năng hiển thị banner trên một số dòng máy
                .setAutoCancel(true);

        // Hiển thị thông báo với ảnh đại diện người gửi nếu có
        if (actorAvatar != null && !actorAvatar.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(actorAvatar)
                    .circleCrop()
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            builder.setLargeIcon(resource);
                            notificationManager.notify(notificationId, builder.build());
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } else {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    private void saveFCMToken() {
        // Cố gắng lấy token để hỗ trợ Push Notification nếu sau này có Server Key
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        String userId = com.example.social_app.firebase.FirebaseManager.getInstance().getAuth().getUid();
                        if (userId != null) {
                            com.example.social_app.firebase.FirebaseManager.getInstance().getFirestore()
                                    .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_USERS)
                                    .document(userId)
                                    .update("fcmToken", token);
                        }
                    });
        } catch (Exception e) {
            Log.e("MainActivity", "FCM Token error: " + e.getMessage());
        }
    }
}
