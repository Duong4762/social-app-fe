package com.example.social_app.utils;

import android.content.Context;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

public class FCMTokenHelper {

    public static void getToken(Context context) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    android.util.Log.d("FCM_TOKEN", "Token: " + token);
                    Toast.makeText(context, "Token: " + token, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FCM_TOKEN", "Failed to get token", e);
                });
    }
}