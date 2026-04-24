package com.example.social_app.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_Service";
    private static final String CHANNEL_ID = "social_notifications";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Xử lý dữ liệu từ payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }

        // Xử lý notification payload (nếu có)
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            // Thường thì ta ưu tiên dùng data payload để tùy biến cao hơn
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String title = data.get("title");
        String body = data.get("body");
        String type = data.get("type");
        String referenceId = data.get("referenceId");
        String actorName = data.get("actorName");
        String actorAvatar = data.get("actorAvatar");
        String actorId = data.get("actorId");
        String notifId = data.get("notifId");
        String targetUserId = data.get("targetUserId"); // Lấy thêm ID người nhận từ payload

        // KIỂM TRA: Chỉ hiện thông báo nếu đúng người đang đăng nhập trên máy này
        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId != null && targetUserId != null && !currentUserId.equals(targetUserId)) {
            Log.d(TAG, "Thông báo dành cho user khác (" + targetUserId + "), bỏ qua.");
            return;
        }

        sendNotification(title, body, type, referenceId, actorName, actorAvatar, actorId, notifId);
    }

    private void sendNotification(String title, String body, String type, String referenceId, 
                                String actorName, String actorAvatar, String actorId, String notifId) {
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (notifId != null) ? Math.abs(notifId.hashCode()) : (int) System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID,
                        getString(R.string.notification_title),
                        NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("NOTIF_TYPE", type);
        intent.putExtra("REF_ID", referenceId);
        intent.putExtra("ACTOR_NAME", actorName);
        intent.putExtra("ACTOR_AVATAR", actorAvatar);
        intent.putExtra("ACTOR_ID", actorId);
        intent.putExtra("NOTIF_ID", notifId);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        if (actorAvatar != null && !actorAvatar.isEmpty()) {
            Glide.with(getApplicationContext())
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
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        } else {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        sendTokenToServer(token);
    }

    private void sendTokenToServer(String token) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Token updated successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update token", e));
        }
    }
}
