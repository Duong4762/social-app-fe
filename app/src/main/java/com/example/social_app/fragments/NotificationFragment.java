package com.example.social_app.fragments;

import android.os.Bundle;
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
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationFragment extends Fragment {

    private RecyclerView notificationRecyclerView;
    private NotificationAdapter notificationAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private TextView btnMarkAllRead;

    private List<Notification> notifications = new ArrayList<>();
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener; // Real-time listener

    public NotificationFragment() {
        super(R.layout.fragment_notification);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseManager.getInstance().getFirestore();

        notificationRecyclerView = view.findViewById(R.id.notification_recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        loadingProgress = view.findViewById(R.id.loading_progress);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        btnMarkAllRead = view.findViewById(R.id.btn_mark_all_read);

        if (btnMarkAllRead != null) {
            btnMarkAllRead.setOnClickListener(v -> markAllAsRead());
        }

        setupRecyclerView();
        setupSwipeRefresh();
        setupRealtimeNotifications(); // Thay vì loadNotifications()
    }

    private void setupRecyclerView() {
        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        notificationAdapter = new NotificationAdapter(requireContext(), new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                handleNotificationClick(notification);
            }

            @Override
            public void onMarkAsRead(Notification notification, int position) {
                markAsRead(notification);
            }
        });
        notificationRecyclerView.setAdapter(notificationAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            // Refresh manually - force reload
            if (notificationListener != null) {
                notificationListener.remove();
                setupRealtimeNotifications();
            }
            swipeRefresh.setRefreshing(false);
        });
    }

    /**
     * REAL-TIME NOTIFICATIONS - tự động cập nhật khi có notification mới
     */
    private void setupRealtimeNotifications() {
        showLoading(true);

        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        if (currentUserId == null) {
            showLoading(false);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("Vui lòng đăng nhập");
            return;
        }

        // Bỏ orderBy để không cần index, chỉ lấy tất cả và sắp xếp trong code
        notificationListener = db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUserId)
                // .orderBy("createdAt", Query.Direction.DESCENDING)  // COMMENT DÒNG NÀY
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        android.util.Log.e("NotificationFragment", "Listen failed: " + e.getMessage(), e);
                        showLoading(false);
                        emptyStateText.setVisibility(View.VISIBLE);
                        emptyStateText.setText("Lỗi kết nối: " + e.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots == null) return;

                    notifications.clear();
                    for (var doc : queryDocumentSnapshots.getDocuments()) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification != null) {
                            notification.setId(doc.getId());
                            notifications.add(notification);
                        }
                    }

                    // Sắp xếp thủ công trong code (mới nhất lên đầu)
                    notifications.sort((a, b) -> {
                        long t1 = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0;
                        long t2 = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0;
                        return Long.compare(t2, t1);
                    });

                    notificationAdapter.setNotifications(notifications);

                    if (notifications.isEmpty()) {
                        emptyStateText.setVisibility(View.VISIBLE);
                        emptyStateText.setText("Chưa có thông báo nào");
                    } else {
                        emptyStateText.setVisibility(View.GONE);
                    }

                    showLoading(false);
                });
    }

    private void markAsRead(Notification notification) {
        if (notification == null || notification.getId() == null || notification.isRead()) return;

        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notification.getId())
                .update("isRead", true)
                .addOnSuccessListener(aVoid -> {
                    notification.setRead(true);
                    notificationAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> android.util.Log.e("NotificationFragment", "Error marking read", e));
    }

    private void markAllAsRead() {
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        if (currentUserId == null) return;

        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) return;
                    
                    var batch = db.batch();
                    for (var doc : queryDocumentSnapshots.getDocuments()) {
                        batch.update(doc.getReference(), "isRead", true);
                    }
                    batch.commit().addOnSuccessListener(aVoid -> {
                        Toast.makeText(requireContext(), "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void handleNotificationClick(Notification notification) {
        markAsRead(notification);

        String type = notification.getType();
        String referenceId = notification.getReferenceId();

        if (type == null) return;

        switch (type.toUpperCase()) {
            case "LIKE":
            case "COMMENT":
                if (referenceId != null) {
                    openPostDetail(referenceId);
                }
                break;
            case "FOLLOW":
                if (referenceId != null) {
                    openProfile(referenceId);
                }
                break;
            case "MESSAGE":
                Toast.makeText(requireContext(), "Mở tin nhắn", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void openPostDetail(String postId) {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            HomeFragment homeFragment = HomeFragment.newInstance(postId);
            homeFragment.setBottomNavigationCallback(mainActivity::setupScrollListener);
            mainActivity.loadFragment(homeFragment);
        }
    }

    private void openProfile(String userId) {
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        if (userId.equals(currentUserId)) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        } else {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(userId))
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) emptyStateText.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Quan trọng: Xóa listener khi fragment bị destroy để tránh memory leak
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }
}