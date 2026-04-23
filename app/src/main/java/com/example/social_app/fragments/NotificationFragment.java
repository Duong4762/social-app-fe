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

import com.example.social_app.R;
import com.example.social_app.adapters.NotificationAdapter;
import com.example.social_app.data.model.Notification;

import java.util.ArrayList;
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

    private List<Notification> notifications = new ArrayList<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationFragment() {
        super(R.layout.fragment_notification);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notificationRecyclerView = view.findViewById(R.id.notification_recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        loadingProgress = view.findViewById(R.id.loading_progress);
        emptyStateText = view.findViewById(R.id.empty_state_text);

        setupRecyclerView();
        setupSwipeRefresh();
        loadNotifications();
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
                notification.setRead(true);
                notificationAdapter.notifyItemChanged(position);
                Toast.makeText(requireContext(), R.string.marked_as_read, Toast.LENGTH_SHORT).show();
            }
        });
        notificationRecyclerView.setAdapter(notificationAdapter);
    }

    private void handleNotificationClick(Notification notification) {
        String type = notification.getType();

        if (type == null) {
            Toast.makeText(requireContext(), R.string.notification_clicked, Toast.LENGTH_SHORT).show();
            return;
        }

        switch (type.toUpperCase()) {
            case "LIKE":
            case "COMMENT":
                Toast.makeText(requireContext(), getString(R.string.open_post_id, notification.getReferenceId()), Toast.LENGTH_SHORT).show();
                break;
            case "FOLLOW":
                Toast.makeText(requireContext(), getString(R.string.open_profile_id, notification.getUserId()), Toast.LENGTH_SHORT).show();
                break;
            case "MESSAGE":
                Toast.makeText(requireContext(), R.string.open_chat, Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(requireContext(), R.string.notification_clicked, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadNotifications);
    }

    private void loadNotifications() {
        showLoading(true);

        executorService.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            List<Notification> newNotifications = generateMockNotifications();

            mainHandler.post(() -> {
                notifications.clear();
                notifications.addAll(newNotifications);
                notificationAdapter.setNotifications(notifications);

                if (notifications.isEmpty()) {
                    emptyStateText.setVisibility(View.VISIBLE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                }

                showLoading(false);
                swipeRefresh.setRefreshing(false);
            });
        });
    }

    private List<Notification> generateMockNotifications() {
        List<Notification> list = new ArrayList<>();
        String[] types = {"LIKE", "COMMENT", "FOLLOW", "MESSAGE"};

        for (int i = 0; i < 12; i++) {
            Notification notification = new Notification();
            notification.setId("notif_" + i);
            notification.setUserId("user_" + i);
            notification.setType(types[i % types.length]);
            notification.setReferenceId("ref_" + i);
            notification.setRead(i % 3 == 0);
            notification.setCreatedAt(new Date(System.currentTimeMillis() - (i * 60000 * 10)));
            list.add(notification);
        }

        return list;
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}