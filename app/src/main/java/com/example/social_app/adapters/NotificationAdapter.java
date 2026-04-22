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

import com.example.social_app.R;
import com.example.social_app.data.model.Notification;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.MockDataGenerator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private Context context;
    private List<Notification> notifications;
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onMarkAsRead(Notification notification, int position);
    }

    public NotificationAdapter(Context context, OnNotificationClickListener listener) {
        this.context = context;
        this.notifications = new ArrayList<>();
        this.listener = listener;
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
            // Lấy thông tin actor từ userId (trong model gốc không có actorId riêng)
            // Giả định: người gửi thông báo được lấy từ MockDataGenerator
            String actorName = "Someone";
            User mockUser = MockDataGenerator.generateMockUser(position);
            if (mockUser != null) {
                actorName = mockUser.getFullName() != null ? mockUser.getFullName() : mockUser.getUsername();
            }

            // Tạo nội dung hiển thị dựa trên type
            String type = notification.getType();
            String contentText = buildNotificationContent(actorName, type);

            this.contentText.setText(contentText);

            // Format thời gian
            String timeStr = formatTime(notification.getCreatedAt());
            this.timeText.setText(timeStr);

            // Set icon dựa trên type
            setTypeIcon(type);

            // Hiển thị unread indicator
            unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            // Avatar placeholder
            avatar.setImageResource(R.drawable.avatar_placeholder);

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

        private String buildNotificationContent(String actorName, String type) {
            if (type == null) {
                return actorName + " sent you a notification";
            }

            switch (type.toUpperCase()) {
                case "LIKE":
                    return actorName + " liked your post";
                case "COMMENT":
                    return actorName + " commented on your post";
                case "FOLLOW":
                    return actorName + " started following you";
                case "MESSAGE":
                    return actorName + " sent you a message";
                default:
                    return actorName + " sent you a notification";
            }
        }

        private void setTypeIcon(String type) {
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
                return "Just now";
            }

            long now = System.currentTimeMillis();
            long diff = now - date.getTime();

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + "d ago";
            } else if (hours > 0) {
                return hours + "h ago";
            } else if (minutes > 0) {
                return minutes + "m ago";
            } else {
                return "Just now";
            }
        }
    }
}