package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Notification;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private Context context;
    private List<Notification> notifications;
    private OnNotificationClickListener listener;
    private FirebaseFirestore db;
    private Map<String, User> userCache = new HashMap<>();

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onMarkAsRead(Notification notification, int position);
    }

    public NotificationAdapter(Context context, OnNotificationClickListener listener) {
        this.context = context;
        this.notifications = new ArrayList<>();
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications != null ? notifications : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.bind(notification, position);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    private void loadUserInfo(String userId, TextView contentText, String type, ImageView avatar) {
        if (userId == null || userId.isEmpty()) {
            if (contentText != null) {
                contentText.setText("Ai đó " + getActionText(type));
            }
            return;
        }

        // Check cache
        if (userCache.containsKey(userId)) {
            User user = userCache.get(userId);
            updateContentText(contentText, user, type);
            if (avatar != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                Glide.with(context).load(user.getAvatarUrl()).into(avatar);
            }
            return;
        }

        // Load from Firestore
        db.collection(FirebaseManager.COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setId(documentSnapshot.getId());
                            userCache.put(userId, user);
                            updateContentText(contentText, user, type);
                            if (avatar != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                                Glide.with(context).load(user.getAvatarUrl()).into(avatar);
                            } else if (avatar != null) {
                                avatar.setImageResource(R.drawable.avatar_placeholder);
                            }
                        }
                    } else {
                        if (contentText != null) {
                            contentText.setText("Người dùng " + getActionText(type));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (contentText != null) {
                        contentText.setText("Ai đó " + getActionText(type));
                    }
                });
    }

    private void updateContentText(TextView contentText, User user, String type) {
        if (contentText == null) return;
        String name = user.getFullName() != null && !user.getFullName().isEmpty()
                ? user.getFullName()
                : (user.getUsername() != null ? user.getUsername() : "Người dùng");
        contentText.setText(name + " " + getActionText(type));
    }

    private String getActionText(String type) {
        if (type == null) return context.getString(R.string.notification_message);
        switch (type.toUpperCase()) {
            case "LIKE":
                return context.getString(R.string.notification_like);
            case "COMMENT":
                return context.getString(R.string.notification_comment);
            case "FOLLOW":
                return context.getString(R.string.notification_follow);
            case "MESSAGE":
                return context.getString(R.string.notification_message);
            default:
                return context.getString(R.string.notification_message);
        }
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView contentText;
        TextView timeText;
        ImageView typeIcon;
        View unreadIndicator;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.notification_avatar);
            contentText = itemView.findViewById(R.id.notification_content);
            timeText = itemView.findViewById(R.id.notification_time);
            typeIcon = itemView.findViewById(R.id.notification_type_icon);
            unreadIndicator = itemView.findViewById(R.id.unread_indicator);
        }

        void bind(Notification notification, int position) {
            String type = notification.getType();
            String referenceId = notification.getReferenceId();

            // Load user info from referenceId (người tạo ra hành động)
            loadUserInfo(referenceId, contentText, type, avatar);

            // Format thời gian
            String timeStr = formatTime(notification.getCreatedAt());
            timeText.setText(timeStr);

            // Set icon dựa trên type
            setTypeIcon(type);

            // Hiển thị unread indicator
            if (unreadIndicator != null) {
                unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(notification);
                    if (!notification.isRead()) {
                        listener.onMarkAsRead(notification, position);
                    }
                }
            });

            // Long press to mark as read
            itemView.setOnLongClickListener(v -> {
                if (!notification.isRead() && listener != null) {
                    listener.onMarkAsRead(notification, position);
                }
                return true;
            });
        }

        private void setTypeIcon(String type) {
            if (typeIcon == null) return;

            if (type == null) {
                typeIcon.setImageResource(R.drawable.ic_notification);
                return;
            }

            switch (type.toUpperCase()) {
                case "LIKE":
                    typeIcon.setImageResource(R.drawable.ic_heart_filled);
                    typeIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_red));
                    break;
                case "COMMENT":
                    typeIcon.setImageResource(R.drawable.ic_comment);
                    typeIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary));
                    break;
                case "FOLLOW":
                    typeIcon.setImageResource(R.drawable.ic_follow);
                    typeIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary));
                    break;
                case "MESSAGE":
                    typeIcon.setImageResource(R.drawable.ic_message);
                    typeIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary));
                    break;
                default:
                    typeIcon.setImageResource(R.drawable.ic_notification);
                    break;
            }
        }

        private String formatTime(Date date) {
            if (date == null) {
                return context.getString(R.string.just_now);
            }

            long now = System.currentTimeMillis();
            long diff = now - date.getTime();

            long minutes = diff / 60000;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return context.getString(R.string.days_ago, (int) days);
            } else if (hours > 0) {
                return context.getString(R.string.hours_ago, (int) hours);
            } else if (minutes > 0) {
                return context.getString(R.string.minutes_ago, (int) minutes);
            } else {
                return context.getString(R.string.just_now);
            }
        }
    }
}