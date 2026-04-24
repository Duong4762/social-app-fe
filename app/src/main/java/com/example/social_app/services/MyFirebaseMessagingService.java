package com.example.social_app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "social_app_notifications";
    private static final int NOTIFICATION_ID = 100;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Lấy dữ liệu từ notification
        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "Thông báo mới";
        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "Bạn có thông báo mới";
        String type = remoteMessage.getData().get("type");
        String referenceId = remoteMessage.getData().get("referenceId");

        // Hiển thị notification trên điện thoại
        showNotification(title, body, type, referenceId);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Lưu token để server gửi notification (cần backend)
        // Hiện tại chỉ log để debug
        android.util.Log.d("FCM", "New token: " + token);
    }

    private void showNotification(String title, String body, String type, String referenceId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Thêm dữ liệu để xử lý khi nhấn vào notification
        intent.putExtra("notification_type", type);
        intent.putExtra("notification_reference_id", referenceId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Tạo notification channel (cần cho Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Thông báo Social App",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Nhận thông báo khi có like, comment, follow");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Tạo notification
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
}