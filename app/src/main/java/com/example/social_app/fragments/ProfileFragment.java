package com.example.social_app.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private TextView tvUsernameTop;
    private TextView tvName;
    private TextView tvHandle;
    private TextView tvBio;
    private ImageView imgAvatar;

    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        loadCurrentUserProfile();
    }

    private void bindViews(View view) {
        tvUsernameTop = view.findViewById(R.id.tvUsernameTop);
        tvName = view.findViewById(R.id.tvName);
        tvHandle = view.findViewById(R.id.tvHandle);
        tvBio = view.findViewById(R.id.tvBio);
        imgAvatar = view.findViewById(R.id.imgAvatar);
    }

    private void loadCurrentUserProfile() {
        FirebaseManager firebaseManager = FirebaseManager.getInstance();
        FirebaseUser authUser = firebaseManager.getAuth().getCurrentUser();

        if (authUser == null) {
            Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = firebaseManager.getFirestore();
        db.collection(FirebaseManager.COLLECTION_USERS)
                .document(authUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (!documentSnapshot.exists()) {
                        bindFallbackProfile(authUser);
                        return;
                    }

                    User user = documentSnapshot.toObject(User.class);
                    if (user == null) {
                        bindFallbackProfile(authUser);
                        return;
                    }

                    bindProfile(user, authUser);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), "Không tải được hồ sơ người dùng", Toast.LENGTH_SHORT).show();
                    bindFallbackProfile(authUser);
                });
    }

    private void bindProfile(User user, FirebaseUser authUser) {
        String fullName = safeOrDefault(user.getFullName(), "Người dùng");
        String username = safeOrDefault(user.getUsername(), authUser.getEmail() != null ? authUser.getEmail() : "username");
        String bio = safeOrDefault(user.getBio(), "Chưa có tiểu sử");

        tvName.setText(fullName);
        tvHandle.setText("@" + username.replace("@", ""));
        tvUsernameTop.setText(username);
        tvBio.setText(bio);

        loadAvatar(user.getAvatarUrl());
    }

    private void bindFallbackProfile(FirebaseUser authUser) {
        String email = authUser.getEmail();
        String username = !TextUtils.isEmpty(email) && email.contains("@")
                ? email.substring(0, email.indexOf("@"))
                : "username";

        tvName.setText("Người dùng");
        tvHandle.setText("@" + username);
        tvUsernameTop.setText(username);
        tvBio.setText("Chưa có tiểu sử");
        imgAvatar.setImageResource(R.drawable.avatar_placeholder);
    }

    private void loadAvatar(String avatarUrl) {
        if (TextUtils.isEmpty(avatarUrl)) {
            imgAvatar.setImageResource(R.drawable.avatar_placeholder);
            return;
        }

        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            loadRemoteAvatar(avatarUrl);
            return;
        }

        if (avatarUrl.startsWith("drawable/")) {
            String drawableName = avatarUrl.substring("drawable/".length());
            int resourceId = requireContext().getResources().getIdentifier(
                    drawableName,
                    "drawable",
                    requireContext().getPackageName()
            );
            if (resourceId != 0) {
                imgAvatar.setImageResource(resourceId);
                return;
            }
        }

        if (avatarUrl.startsWith("content://")
                || avatarUrl.startsWith("file://")
                || avatarUrl.startsWith("android.resource://")) {
            imgAvatar.setImageURI(Uri.parse(avatarUrl));
            return;
        }

        imgAvatar.setImageResource(R.drawable.avatar_placeholder);
    }

    private void loadRemoteAvatar(String url) {
        imgAvatar.setImageResource(R.drawable.avatar_placeholder);

        avatarExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                URL avatarUrl = new URL(url);
                connection = (HttpURLConnection) avatarUrl.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
            } catch (Exception ignored) {
                // Keep placeholder if any error occurs.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            Bitmap finalBitmap = bitmap;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (finalBitmap != null) {
                        imgAvatar.setImageBitmap(finalBitmap);
                    } else {
                        imgAvatar.setImageResource(R.drawable.avatar_placeholder);
                    }
                });
            }
        });
    }

    private String safeOrDefault(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        avatarExecutor.shutdownNow();
    }
}

