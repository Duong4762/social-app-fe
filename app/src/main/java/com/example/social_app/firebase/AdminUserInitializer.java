package com.example.social_app.firebase;

import android.util.Log;

import com.example.social_app.data.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Seed admin user vào Firestore nếu chưa tồn tại bất kỳ user ADMIN nào.
 */
public class AdminUserInitializer {

    private static final String TAG = "AdminUserInitializer";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_DOC_ID = "admin_seed";

    private final FirebaseFirestore firestore;

    public AdminUserInitializer(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void ensureAdminUserExists() {
        firestore.collection(FirebaseManager.COLLECTION_USERS)
                .whereEqualTo("role", ADMIN_ROLE)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Log.d(TAG, "Admin user already exists, skip seeding.");
                        return;
                    }
                    createDefaultAdminUser();
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to check existing admin user.", e));
    }

    private void createDefaultAdminUser() {
        User adminUser = new User(
                ADMIN_DOC_ID,
                "admin",
                "admin@socialapp.local",
                "System Admin",
                "",
                "Default seeded admin user",
                "OTHER",
                "2000-01-01",
                ADMIN_ROLE,
                true
        );

        firestore.collection(FirebaseManager.COLLECTION_USERS)
                .document(ADMIN_DOC_ID)
                .set(adminUser)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Default admin user created successfully."))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to create default admin user.", e));
    }
}
