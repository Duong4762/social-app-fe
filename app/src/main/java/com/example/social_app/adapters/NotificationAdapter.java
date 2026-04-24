package com.example.social_app.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.Notification;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private Context context;
    private List<Notification> notifications = new ArrayList<>();
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onMarkAsRead(Notification notification, int position);
    }

    public NotificationAdapter(Context context, OnNotificationClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(notifications.get(position), position);
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

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.notification_avatar);
            contentText = itemView.findViewById(R.id.notification_content);
            timeText = itemView.findViewById(R.id.notification_time);
            typeIcon = itemView.findViewById(R.id.notification_type_icon);
            unreadIndicator = itemView.findViewById(R.id.unread_indicator);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNotificationClick(notifications.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMarkAsRead(notifications.get(pos), pos);
                }
                return true;
            });
        }

        void bind(Notification notification, int position) {
            if (notification == null) return;

            // Reset UI state
            avatar.setImageResource(R.drawable.avatar_placeholder);
            contentText.setText("...");
            
            String type = notification.getType() != null ? notification.getType() : "";
            String actorId = notification.getActorId();

            if (actorId != null && !actorId.isEmpty()) {
                FirebaseFirestore.getInstance().collection(FirebaseManager.COLLECTION_USERS)
                        .document(actorId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (context == null || itemView.getParent() == null) return;
                            
                            try {
                                if (documentSnapshot.exists()) {
                                    User actor = documentSnapshot.toObject(User.class);
                                    if (actor != null) {
                                        String name = (actor.getFullName() != null && !actor.getFullName().isEmpty()) 
                                                ? actor.getFullName() : actor.getUsername();
                                        contentText.setText(buildContent(name, type));
                                        UserAvatarLoader.load(avatar, actor.getAvatarUrl());
                                    }
                                } else {
                                    contentText.setText(buildContent("Người dùng", type));
                                }
                            } catch (Exception ignored) {}
                        })
                        .addOnFailureListener(e -> {
                            if (contentText != null) contentText.setText(buildContent("Người dùng", type));
                        });
            } else {
                contentText.setText(buildContent("Hệ thống", type));
            }

            try {
                timeText.setText(formatTime(notification.getCreatedAt()));
                unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
                setIcon(type);
            } catch (Exception ignored) {}
        }

        private String buildContent(String name, String type) {
            if (type == null) return name + " đã tương tác với bạn";
            switch (type.toUpperCase()) {
                case "LIKE": return name + " đã thích bài viết của bạn";
                case "COMMENT": return name + " đã bình luận về bài viết";
                case "FOLLOW": return name + " đã bắt đầu theo dõi bạn";
                case "MESSAGE": return name + " đã gửi một tin nhắn";
                case "LIKE_COMMENT": return name + " đã thích bình luận của bạn";
                case "REPLY_COMMENT": return name + " đã trả lời bình luận";
                default: return name + " đã gửi một thông báo";
            }
        }

        private void setIcon(String type) {
            if (typeIcon == null) return;
            int resId = R.drawable.ic_heart_filled; // Mặc định là tim
            try {
                switch (type.toUpperCase()) {
                    case "LIKE":
                    case "LIKE_COMMENT":
                        resId = R.drawable.ic_heart_filled;
                        break;
                    case "COMMENT":
                    case "REPLY_COMMENT":
                        resId = R.drawable.ic_comment; 
                        break;
                    case "FOLLOW":
                        resId = R.drawable.ic_follow;
                        break;
                    case "MESSAGE":
                        resId = R.drawable.ic_message;
                        if (typeIcon != null) {
                            typeIcon.setColorFilter(context.getResources().getColor(R.color.primary_blue, context.getTheme()));
                        }
                        break;
                }
                typeIcon.setImageResource(resId);
                
                // Reset color filter for other types if needed, or set specific colors
                if (!"MESSAGE".equalsIgnoreCase(type)) {
                    typeIcon.clearColorFilter();
                }
                
                if ("LIKE".equalsIgnoreCase(type) || "LIKE_COMMENT".equalsIgnoreCase(type)) {
                    typeIcon.setColorFilter(android.graphics.Color.RED);
                } else if ("FOLLOW".equalsIgnoreCase(type)) {
                    typeIcon.setColorFilter(context.getResources().getColor(R.color.accent_purple, context.getTheme()));
                }
            } catch (Exception e) {
                // Nếu vẫn lỗi thì dùng icon hệ thống an toàn nhất
                typeIcon.setImageResource(android.R.drawable.ic_dialog_info);
            }
        }

        private String formatTime(Date date) {
            if (date == null) return context.getString(R.string.just_now);
            long diff = System.currentTimeMillis() - date.getTime();
            if (diff < 60000) { // Dưới 1 phút
                return context.getString(R.string.just_now);
            }
            return (String) DateUtils.getRelativeTimeSpanString(date.getTime(), 
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        }
    }
}
