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
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.firestore.FirebaseFirestore;

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
            String actorId = notification.getActorId();
            
            if (actorId != null) {
                FirebaseFirestore.getInstance().collection(FirebaseManager.COLLECTION_USERS)
                        .document(actorId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                User actor = documentSnapshot.toObject(User.class);
                                if (actor != null) {
                                    String actorName = actor.getFullName() != null ? actor.getFullName() : actor.getUsername();
                                    contentText.setText(buildNotificationContent(actorName, notification.getType()));
                                    UserAvatarLoader.load(avatar, actor.getAvatarUrl());
                                }
                            }
                        });
            } else {
                avatar.setImageResource(R.drawable.avatar_placeholder);
                contentText.setText(buildNotificationContent("Ai đó", notification.getType()));
            }

            // Format thời gian
            String timeStr = formatTime(notification.getCreatedAt());
            this.timeText.setText(timeStr);

            // Set icon dựa trên type
            setTypeIcon(notification.getType());

            // Hiển thị unread indicator
            unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

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
                return actorName + " " + context.getString(R.string.notification_message);
            }

            switch (type.toUpperCase()) {
                case "LIKE":
                    return actorName + " " + context.getString(R.string.notification_like);
                case "COMMENT":
                    return actorName + " " + context.getString(R.string.notification_comment);
                case "FOLLOW":
                    return actorName + " " + context.getString(R.string.notification_follow);
                case "LIKE_COMMENT":
                    return actorName + " " + context.getString(R.string.notification_like_comment);
                case "REPLY_COMMENT":
                    return actorName + " " + context.getString(R.string.notification_reply_comment);
                case "MESSAGE":
                    return actorName + " " + context.getString(R.string.notification_message);
                default:
                    return actorName + " " + context.getString(R.string.notification_message);
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
                case "LIKE_COMMENT":
                    typeIcon.setImageResource(R.drawable.ic_heart_filled);
                    typeIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_red));
                    break;
                case "REPLY_COMMENT":
                    typeIcon.setImageResource(R.drawable.ic_comment);
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

            long seconds = diff / 1000;
            long minutes = seconds / 60;
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