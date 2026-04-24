package com.example.social_app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

        fragmentManager = getSupportFragmentManager();
        customBottomNav = findViewById(R.id.custom_bottom_nav);
        initializeNavigationButtons();

        fragmentManager.addOnBackStackChangedListener(this::syncChatOverlayVisibility);

        if (savedInstanceState == null) {
            HomeFragment homeFragment = new HomeFragment();
            homeFragment.setBottomNavigationCallback(this::setupScrollListener);
            loadFragment(homeFragment);
            selectNavButton(navBtnHome);
        }

        showNotificationBadge();
        setupRealtimeNotificationListener();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
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
                selectNavButton(navBtnHome);
                HomeFragment homeFragment = new HomeFragment();
                homeFragment.setBottomNavigationCallback(this::setupScrollListener);
                loadFragment(homeFragment);
            }
            intent.removeExtra("NOTIF_TYPE");
            intent.removeExtra("REF_ID");
            intent.removeExtra("NOTIF_ID");
        }

        // Đưa app lên foreground nếu đang ở background
        bringAppToForeground();
    }

    private void bringAppToForeground() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
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

    private void initializeNavigationButtons() {
        navBtnHome = customBottomNav.findViewById(R.id.nav_btn_home);
        navBtnSearch = customBottomNav.findViewById(R.id.nav_btn_search);
        navBtnMessage = customBottomNav.findViewById(R.id.nav_btn_message);
        navBtnNotifications = customBottomNav.findViewById(R.id.nav_btn_notifications);
        navBtnProfile = customBottomNav.findViewById(R.id.nav_btn_profile);
        navBtnSettings = customBottomNav.findViewById(R.id.nav_btn_settings);
        notificationBadge = customBottomNav.findViewById(R.id.notification_badge);

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
            markAllNotificationsAsRead();
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

    private void selectNavButton(ImageButton button) {
        navBtnHome.setSelected(false);
        navBtnSearch.setSelected(false);
        navBtnMessage.setSelected(false);
        navBtnNotifications.setSelected(false);
        navBtnProfile.setSelected(false);
        navBtnSettings.setSelected(false);
        button.setSelected(true);
        currentSelectedNav = button.getId();
    }

    public void showNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisibility(View.VISIBLE);
        }
    }

    public void removeNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisibility(View.GONE);
        }
    }

    public void setupScrollListener(RecyclerView recyclerView) {
        if (recyclerView != null && customBottomNav != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy > 0 && isBottomNavVisible) {
                        hideBottomNavigation();
                    } else if (dy < 0 && !isBottomNavVisible) {
                        showBottomNavigation();
                    }
                }
            });
        }
    }

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
        if (overlay == null) return;
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
                showToast("Notification permission denied");
            }
        }
    }

    public void loadFragment(Fragment fragment) {
        if (fragment == null) return;
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.nav_host_fragment);
        if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) {
            return;
        }
        fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void updateCurrentUserActiveStatus(boolean isActive) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
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

    private void setupRealtimeNotificationListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
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
                        if (!snapshots.isEmpty()) {
                            showNotificationBadge();
                        } else {
                            removeNotificationBadge();
                            if (isInitialLoad) {
                                isInitialLoad = false;
                            }
                            return;
                        }

                        for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                if (isInitialLoad) continue;

                                com.google.firebase.firestore.QueryDocumentSnapshot doc = dc.getDocument();
                                String type = doc.getString("type");
                                String actorId = doc.getString("actorId");
                                String referenceId = doc.getString("referenceId");
                                String notifId = doc.getId();

                                fetchActorAndNotify(actorId, type, referenceId, notifId);
                            }
                        }

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
        } else {
            message = actorName + " " + getString(R.string.action_success);
        }

        sendLocalNotification(getString(R.string.app_name), message, type, referenceId, actorName, actorAvatar, actorId, notifId);
    }

    private void sendLocalNotification(String title, String body, String type, String referenceId, String actorName, String actorAvatar, String actorId, String notifId) {
        String channelId = "social_notifications";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        intent.putExtra("NOTIF_TYPE", type);
        intent.putExtra("REF_ID", referenceId);
        intent.putExtra("ACTOR_NAME", actorName);
        intent.putExtra("ACTOR_AVATAR", actorAvatar);
        intent.putExtra("ACTOR_ID", actorId);
        intent.putExtra("NOTIF_ID", notifId);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, notificationId, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, builder.build());
    }
}