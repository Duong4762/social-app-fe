package com.example.social_app.firebase;

import android.util.Log;

import com.example.social_app.data.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * Seed admin user vào Firestore nếu chưa tồn tại bất kỳ user ADMIN nào.
 */
public class AdminUserInitializer {

    private static final String TAG = "AdminUserInitializer";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL = "admin@socialapp.local";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public AdminUserInitializer(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
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
                    ensureAdminAuthAndProfile();
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to check existing admin user.", e));
    }

    private void ensureAdminAuthAndProfile() {
        auth.createUserWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        Log.e(TAG, "Created admin auth but FirebaseUser is null.");
                        return;
                    }
                    upsertAdminUser(user.getUid());
                    auth.signOut();
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthException
                            && "ERROR_EMAIL_ALREADY_IN_USE".equals(((FirebaseAuthException) e).getErrorCode())) {
                        resolveExistingAdminUidAndUpsert();
                    } else {
                        Log.e(TAG, "Failed to seed admin auth account.", e);
                    }
                });
    }

    private void resolveExistingAdminUidAndUpsert() {
        auth.signInWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        upsertAdminUser(user.getUid());
                    }
                    auth.signOut();
                })
                .addOnFailureListener(signInError -> {
                    Log.w(TAG, "Admin auth exists but cannot sign in with seeded password, fallback by email lookup.");
                    firestore.collection(FirebaseManager.COLLECTION_USERS)
                            .whereEqualTo("email", ADMIN_EMAIL)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(this::upsertByExistingDocIfPossible)
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Failed to resolve existing admin by email.", e));
                });
    }

    private void upsertByExistingDocIfPossible(QuerySnapshot querySnapshot) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            Log.w(TAG, "Admin auth exists but cannot map Firestore profile without known uid.");
            return;
        }
        String docId = querySnapshot.getDocuments().get(0).getId();
        upsertAdminUser(docId);
    }

    private void upsertAdminUser(String adminDocId) {
        User adminUser = new User(
                adminDocId,
                ADMIN_USERNAME,
                ADMIN_EMAIL,
                "System Admin",
                "",
                "Default seeded admin user",
                "OTHER",
                "2000-01-01",
                ADMIN_ROLE,
                true
        );

        firestore.collection(FirebaseManager.COLLECTION_USERS)
                .document(adminDocId)
                .set(adminUser)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Default admin user/profile created successfully."))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to create default admin user.", e));
    }
}
