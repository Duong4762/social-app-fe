package com.example.social_app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class ShareReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String postId = intent.getStringExtra("shared_post_id");
        Log.d("ShareReceiver", "Post shared successfully: " + postId);

        if (postId != null) {
            // Cập nhật trực tiếp vào Firestore từ Receiver để đảm bảo ngay cả khi Fragment đã bị destroy
            FirebaseFirestore.getInstance()
                    .collection(FirebaseManager.COLLECTION_POSTS)
                    .document(postId)
                    .update("shareCount", FieldValue.increment(1))
                    .addOnSuccessListener(aVoid -> Log.d("ShareReceiver", "Incremented share count in Firestore"))
                    .addOnFailureListener(e -> Log.e("ShareReceiver", "Failed to increment share count", e));
        }
    }
}
