package com.example.social_app.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.adapters.NotificationAdapter;
import com.example.social_app.data.model.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationFragment extends Fragment {

    private RecyclerView notificationRecyclerView;
    private NotificationAdapter notificationAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private TextView btnMarkAllRead;

    private List<Notification> notifications = new ArrayList<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationFragment() {
        super(R.layout.fragment_notification);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotifications();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notificationRecyclerView = view.findViewById(R.id.notification_recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        loadingProgress = view.findViewById(R.id.loading_progress);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        btnMarkAllRead = view.findViewById(R.id.btn_mark_all_read);

        btnMarkAllRead.setOnClickListener(v -> markAllAsRead());
        
        setupRecyclerView();
        setupSwipeRefresh();
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        
        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        notificationAdapter = new NotificationAdapter(getContext(), new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                if (isAdded()) {
                    handleNotificationClick(notification);
                }
            }

            @Override
            public void onMarkAsRead(Notification notification, int position) {
                if (notification.getId() == null) return;
                
                com.example.social_app.firebase.FirebaseManager.getInstance().getFirestore()
                        .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_NOTIFICATIONS)
                        .document(notification.getId())
                        .update("isRead", true)
                        .addOnSuccessListener(aVoid -> {
                            if (!isAdded() || getContext() == null) return;
                            notification.setRead(true);
                            notificationAdapter.notifyItemChanged(position);
                            try {
                                Toast.makeText(getContext(), R.string.marked_as_read, Toast.LENGTH_SHORT).show();
                            } catch (Exception ignored) {}
                        })
                        .addOnFailureListener(e -> {
                            if (isAdded() && getContext() != null) {
                                try {
                                    Toast.makeText(getContext(), "Failed to update status", Toast.LENGTH_SHORT).show();
                                } catch (Exception ignored) {}
                            }
                        });
            }
        });
        notificationRecyclerView.setAdapter(notificationAdapter);
    }

    private void handleNotificationClick(Notification notification) {
        if (!isAdded() || getActivity() == null) return;

        String type = notification.getType();

        // Mark as read when clicked
        if (!notification.isRead() && notification.getId() != null) {
            com.example.social_app.firebase.FirebaseManager.getInstance().getFirestore()
                    .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_NOTIFICATIONS)
                    .document(notification.getId())
                    .update("isRead", true);
        }

        if (type == null) {
            Toast.makeText(getContext(), R.string.notification_clicked, Toast.LENGTH_SHORT).show();
            return;
        }

        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            switch (type.toUpperCase()) {
                case "LIKE":
                case "COMMENT":
                case "REPLY_COMMENT":
                case "LIKE_COMMENT":
                    com.example.social_app.fragments.HomeFragment homeFragment = com.example.social_app.fragments.HomeFragment.newInstance(notification.getReferenceId());
                    homeFragment.setBottomNavigationCallback(mainActivity::setupScrollListener);
                    mainActivity.loadFragment(homeFragment);
                    break;
                case "FOLLOW":
                    if (notification.getActorId() != null) {
                        mainActivity.openOtherProfile(notification.getActorId());
                    }
                    break;
                case "MESSAGE":
                    if (notification.getActorId() != null && notification.getReferenceId() != null) {
                        // Fetch actor info to open chat detail
                        com.example.social_app.firebase.FirebaseManager.getInstance().getFirestore()
                                .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_USERS)
                                .document(notification.getActorId())
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (!isAdded() || getActivity() == null) return;
                                    String name = "Người dùng";
                                    String avatar = null;
                                    if (doc.exists()) {
                                        name = doc.getString("fullName");
                                        if (name == null || name.isEmpty()) name = doc.getString("username");
                                        avatar = doc.getString("avatarUrl");
                                    }
                                    mainActivity.openChatDetail(notification.getReferenceId(), name, avatar, notification.getActorId());
                                })
                                .addOnFailureListener(e -> {
                                    if (isAdded()) mainActivity.loadFragment(new com.example.social_app.fragments.MessagesFragment());
                                });
                    } else {
                        mainActivity.loadFragment(new com.example.social_app.fragments.MessagesFragment());
                    }
                    break;
                default:
                    Toast.makeText(getContext(), R.string.notification_clicked, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private void markAllAsRead() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).markAllNotificationsAsRead();
            // Cập nhật UI ngay lập tức
            for (Notification n : notifications) {
                n.setRead(true);
            }
            if (notificationAdapter != null) {
                notificationAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadNotifications);
    }

    private com.google.firebase.firestore.ListenerRegistration notificationListener;

    private void loadNotifications() {
        showLoading(true);
        String currentUserId = com.example.social_app.firebase.FirebaseManager.getInstance().getAuth().getUid();
        if (currentUserId == null) {
            showLoading(false);
            swipeRefresh.setRefreshing(false);
            return;
        }

        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = com.example.social_app.firebase.FirebaseManager.getInstance().getFirestore()
                .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (!isAdded() || getView() == null) {
                        if (notificationListener != null) {
                            notificationListener.remove();
                            notificationListener = null;
                        }
                        return;
                    }

                    if (e != null) {
                        android.util.Log.e("NotificationFragment", "Error loading notifications", e);
                        showLoading(false);
                        swipeRefresh.setRefreshing(false);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Notification> loadedNotifications = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            try {
                                Notification n = doc.toObject(Notification.class);
                                if (n != null) {
                                    n.setId(doc.getId());
                                    loadedNotifications.add(n);
                                }
                            } catch (Exception ex) {
                                android.util.Log.e("NotificationFragment", "Error parsing notification: " + doc.getId(), ex);
                            }
                        }
                        notifications = loadedNotifications;
                        
                        // Sắp xếp an toàn
                        Collections.sort(notifications, (n1, n2) -> {
                            Date d1 = n1.getCreatedAt();
                            Date d2 = n2.getCreatedAt();
                            if (d1 == null && d2 == null) return 0;
                            if (d1 == null) return 1;
                            if (d2 == null) return -1;
                            return d2.compareTo(d1);
                        });

                        if (notificationAdapter != null) {
                            notificationAdapter.setNotifications(notifications);
                        }

                        if (notifications.isEmpty()) {
                            emptyStateText.setVisibility(View.VISIBLE);
                        } else {
                            emptyStateText.setVisibility(View.GONE);
                        }
                    }
                    showLoading(false);
                    swipeRefresh.setRefreshing(false);
                });
    }

    private List<Notification> generateMockNotifications() {
        return new ArrayList<>();
    }

    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
