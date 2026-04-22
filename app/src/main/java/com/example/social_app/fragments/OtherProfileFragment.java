package com.example.social_app.fragments;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;
import com.example.social_app.data.model.Follow;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OtherProfileFragment extends Fragment {

    private static final String ARG_USER_ID = "user_id";

    private TextView tvUsernameTop;
    private TextView tvName;
    private TextView tvHandle;
    private TextView tvBio;
    private TextView tvBlog;
    private TextView tvFollowed;
    private TextView tvFollower;
    private ImageView imgAvatar;
    private ImageView btnBack;
    private Button btnFollow;
    private Button btnChat;

    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();
    private String targetUserId;
    private String currentUserId;
    private boolean isFollowing = false;
    private boolean isFollowLoading = false;

    public OtherProfileFragment() {
        super(R.layout.fragment_other_profile);
    }

    public static OtherProfileFragment newInstance(String userId) {
        OtherProfileFragment fragment = new OtherProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupActions();
        loadUserProfile();
    }

    private void bindViews(View view) {
        tvUsernameTop = view.findViewById(R.id.tvUsernameTop);
        tvName = view.findViewById(R.id.tvName);
        tvHandle = view.findViewById(R.id.tvHandle);
        tvBio = view.findViewById(R.id.tvBio);
        tvBlog = view.findViewById(R.id.tvBlog);
        tvFollowed = view.findViewById(R.id.tvFollowed);
        tvFollower = view.findViewById(R.id.tvFollower);
        imgAvatar = view.findViewById(R.id.imgAvatar);
        btnBack = view.findViewById(R.id.btnBack);
        btnFollow = view.findViewById(R.id.btnFollow);
        btnChat = view.findViewById(R.id.btnChat);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        imgAvatar.setOnClickListener(v -> openAvatarPreviewOnly());
        btnFollow.setOnClickListener(v -> onFollowButtonClicked());
        btnChat.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Chat - Coming soon", Toast.LENGTH_SHORT).show());
    }

    private void loadUserProfile() {
        String userId = getArguments() != null ? getArguments().getString(ARG_USER_ID) : null;
        if (TextUtils.isEmpty(userId)) {
            FirebaseUser currentUser = FirebaseManager.getInstance().getAuth().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            }
        }

        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(requireContext(), "Khong tim thay nguoi dung", Toast.LENGTH_SHORT).show();
            return;
        }
        targetUserId = userId;

        FirebaseUser currentUser = FirebaseManager.getInstance().getAuth().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    User user = snapshot.toObject(User.class);
                    if (user == null) {
                        Toast.makeText(requireContext(), "Khong tim thay thong tin nguoi dung", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindProfile(user);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), "Khong tai duoc thong tin nguoi dung", Toast.LENGTH_SHORT).show();
                });

        refreshFollowState();
        refreshProfileStats();
    }

    private void bindProfile(User user) {
        String fullName = safeOrDefault(user.getFullName(), "Nguoi dung");
        String username = safeOrDefault(user.getUsername(), "username");
        String bio = safeOrDefault(user.getBio(), "Chua co tieu su");

        tvName.setText(fullName);
        tvHandle.setText("@" + username.replace("@", ""));
        tvUsernameTop.setText(username);
        tvBio.setText(bio);

        loadAvatar(user.getAvatarUrl());
    }

    private void openAvatarPreviewOnly() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_avatar_view_only, null, false);
        ImageView imgLarge = dialogView.findViewById(R.id.imgAvatarLarge);
        imgLarge.setImageDrawable(imgAvatar.getDrawable());

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_open_avatar_preview))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.profile_close), null)
                .show();
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
        imgAvatar.setImageResource(R.drawable.avatar_placeholder);
    }

    private void loadRemoteAvatar(String url) {
        imgAvatar.setImageResource(R.drawable.avatar_placeholder);
        avatarExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
            } catch (Exception ignored) {
                // Keep placeholder.
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

    private void onFollowButtonClicked() {
        if (isFollowLoading) {
            return;
        }
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            Toast.makeText(requireContext(), getString(R.string.follow_action_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            Toast.makeText(requireContext(), getString(R.string.follow_own_profile_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFollowing) {
            showUnfollowConfirmDialog();
        } else {
            followUser();
        }
    }

    private void showUnfollowConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.unfollow_confirm_title))
                .setMessage(getString(R.string.unfollow_confirm_message))
                .setNegativeButton(getString(R.string.profile_edit_cancel), null)
                .setPositiveButton(getString(R.string.unfollow_action), (dialog, which) -> unfollowUser())
                .show();
    }

    private void followUser() {
        setFollowLoading(true);

        String followDocId = buildFollowDocId(currentUserId, targetUserId);
        Follow follow = new Follow(followDocId, currentUserId, targetUserId);

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .document(followDocId)
                .set(follow)
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }
                    isFollowing = true;
                    setFollowLoading(false);
                    updateFollowButtonUI();
                    refreshProfileStats();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    setFollowLoading(false);
                    Toast.makeText(requireContext(), getString(R.string.follow_action_failed), Toast.LENGTH_SHORT).show();
                });
    }

    private void unfollowUser() {
        setFollowLoading(true);

        String followDocId = buildFollowDocId(currentUserId, targetUserId);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .document(followDocId)
                .delete()
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }
                    isFollowing = false;
                    setFollowLoading(false);
                    updateFollowButtonUI();
                    refreshProfileStats();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    setFollowLoading(false);
                    Toast.makeText(requireContext(), getString(R.string.follow_action_failed), Toast.LENGTH_SHORT).show();
                });
    }

    private void refreshFollowState() {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            updateFollowButtonUI();
            return;
        }

        if (currentUserId.equals(targetUserId)) {
            btnFollow.setEnabled(false);
            btnFollow.setText(getString(R.string.follow_own_profile));
            return;
        }

        String followDocId = buildFollowDocId(currentUserId, targetUserId);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .document(followDocId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    isFollowing = snapshot.exists();
                    updateFollowButtonUI();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    updateFollowButtonUI();
                });
    }

    private void setFollowLoading(boolean loading) {
        isFollowLoading = loading;
        btnFollow.setEnabled(!loading);
        if (loading) {
            btnFollow.setText(getString(R.string.follow_loading));
        } else {
            updateFollowButtonUI();
        }
    }

    private void updateFollowButtonUI() {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            btnFollow.setText(getString(R.string.follow));
            btnFollow.setEnabled(false);
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            btnFollow.setText(getString(R.string.follow_own_profile));
            btnFollow.setEnabled(false);
            return;
        }
        btnFollow.setEnabled(!isFollowLoading);
        btnFollow.setText(isFollowing ? getString(R.string.following) : getString(R.string.follow));
    }

    private String buildFollowDocId(String followerId, String followingId) {
        return followerId + "_" + followingId;
    }

    private void refreshProfileStats() {
        if (TextUtils.isEmpty(targetUserId)) {
            return;
        }

        // followers = users who follow target (followingId == targetUserId)
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followingId", targetUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    int followerCount = query.size();
                    tvFollowed.setText(followerCount + " Followers");
                });

        // following = users target follows (followerId == targetUserId)
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followerId", targetUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    int followingCount = query.size();
                    tvFollower.setText(followingCount + " Following");
                });

        // posts count of target user
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POSTS)
                .whereEqualTo("userId", targetUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvBlog.setText(query.size() + " Posts");
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        avatarExecutor.shutdownNow();
    }
}

