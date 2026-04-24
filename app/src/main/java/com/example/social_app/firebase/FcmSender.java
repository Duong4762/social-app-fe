package com.example.social_app.firebase;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FcmSender {

    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";
    // DÁN SERVER KEY CỦA CẬU VÀO ĐÂY (Lưu ý: Cách này chỉ dùng để demo/đồ án)
    private static final String SERVER_KEY = "AIzaSyAJOYSRCDwApxludSaEeykbYjFbmXFIOcg";

    public static void sendNotification(String targetToken, String title, String body, String type, String refId, String targetUserId) {
        try {
            OkHttpClient client = new OkHttpClient();
            JSONObject json = new JSONObject();

            json.put("to", targetToken);
            json.put("priority", "high");

            // Đối tượng chứa dữ liệu thông báo (Data payload)
            JSONObject data = new JSONObject();
            data.put("title", title);
            data.put("body", body);
            data.put("type", type);
            data.put("referenceId", refId);
            data.put("targetUserId", targetUserId); // Quan trọng để lọc khi đăng nhập nhiều tài khoản

            // notification payload giúp hiện banner hệ thống khi app ở background
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);
            notification.put("sound", "default");

            json.put("data", data);
            json.put("notification", notification);

            Log.d("FCM_SENDER", "Sending payload: " + json.toString());
            RequestBody requestBody = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(FCM_URL)
                    .post(requestBody)
                    .addHeader("Authorization", "key=" + SERVER_KEY)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("FCM_SENDER", "Gửi push thất bại", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.d("FCM_SENDER", "Gửi push thành công: " + response.body().string());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
