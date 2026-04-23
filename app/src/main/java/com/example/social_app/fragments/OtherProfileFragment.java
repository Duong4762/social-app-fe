package com.example.social_app.fragments;

import android.app.AlertDialog;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.adapters.UserSearchAdapter;
import com.example.social_app.data.model.Follow;
import com.example.social_app.data.model.Notification;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.ViewModelProvider;

public class OtherProfileFragment extends Fragment implements PostAdapter.OnPostActionListener {

    private static final String ARG_USER_ID = "user_id";

    private TextView tvUsernameTop;
    private TextView tvName;
    private TextView tvHandle;
    private TextView tvBio;
    private TextView tvBlog;
    private TextView tvFollowed;
    private TextView tvFollower;
    private TextView tabThreads;
    private TextView tabReplies;
    private TextView tabReposts;
    private TextView tabEmptyState;
    private ImageView imgAvatar;
    private ImageView btnBack;
    private View tabIndicator;
    private View tabLoading;
    private RecyclerView rvPosts;
    private Button btnFollow;
    private Button btnChat;

    private String targetUserId;
    private String currentUserId;
    private boolean isFollowing = false;
    private boolean isFollowLoading = false;
    private String selectedTab = "posts";
    private PostAdapter postAdapter;
    private UserSearchAdapter userAdapter;
    private User profileUser;
    private HomeViewModel homeViewModel;
    private NewPostViewModel newPostViewModel;

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
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
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
        tabThreads = view.findViewById(R.id.tabThreads);
        tabReplies = view.findViewById(R.id.tabReplies);
        tabReposts = view.findViewById(R.id.tabReposts);
        tabEmptyState = view.findViewById(R.id.tabEmptyState);
        imgAvatar = view.findViewById(R.id.imgAvatar);
        btnBack = view.findViewById(R.id.btnBack);
        tabIndicator = view.findViewById(R.id.tabIndicator);
        tabLoading = view.findViewById(R.id.tabLoading);
        rvPosts = view.findViewById(R.id.rvPosts);
        btnFollow = view.findViewById(R.id.btnFollow);
        btnChat = view.findViewById(R.id.btnChat);
        btnChat.setBackgroundTintList(null);
        btnChat.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_follow_button));
        btnChat.setTextColor(requireContext().getColor(R.color.white));

        setupRecycler();
        setupTabs();
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        imgAvatar.setOnClickListener(v -> openAvatarPreviewOnly());
        btnFollow.setOnClickListener(v -> onFollowButtonClicked());
        btnChat.setOnClickListener(v -> openChatWithProfileUser());
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
        if (userAdapter != null) {
            userAdapter.setCurrentUserId(currentUserId);
            userAdapter.setHideFollowButtonForSelf(true);
        }

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
        loadTabContent();
    }

    private void bindProfile(User user) {
        profileUser = user;
        String fullName = safeOrDefault(user.getFullName(), "User");
        String username = safeOrDefault(user.getUsername(), "username");
        String bio = safeOrDefault(user.getBio(), "No bio yet");

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
        UserAvatarLoader.load(imgAvatar, avatarUrl);
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
                    createFollowNotification();
                    if (!isAdded()) {
                        return;
                    }
                    isFollowing = true;
                    setFollowLoading(false);
                    updateFollowButtonUI();
                    refreshProfileStats();
                    loadTabContent();
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
                    removeFollowNotification();
                    if (!isAdded()) {
                        return;
                    }
                    isFollowing = false;
                    setFollowLoading(false);
                    updateFollowButtonUI();
                    refreshProfileStats();
                    loadTabContent();
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
        btnFollow.setBackgroundTintList(null);
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            btnFollow.setText(getString(R.string.follow));
            btnFollow.setBackgroundResource(R.drawable.bg_follow_button);
            btnFollow.setTextColor(requireContext().getColor(R.color.white));
            btnFollow.setEnabled(false);
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            btnFollow.setText(getString(R.string.follow_own_profile));
            btnFollow.setBackgroundResource(R.drawable.bg_following_button);
            btnFollow.setTextColor(requireContext().getColor(R.color.black));
            btnFollow.setEnabled(false);
            return;
        }
        btnFollow.setEnabled(!isFollowLoading);
        btnFollow.setText(isFollowing ? getString(R.string.following) : getString(R.string.follow));
        btnFollow.setBackgroundResource(isFollowing
                ? R.drawable.bg_following_button
                : R.drawable.bg_follow_button);
        btnFollow.setTextColor(requireContext().getColor(isFollowing ? R.color.black : R.color.white));
    }

    private void openChatWithProfileUser() {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            Toast.makeText(requireContext(), getString(R.string.follow_action_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        btnChat.setEnabled(false);
        ConversationRepository conversationRepository = new ConversationRepository(requireContext());
        String peerName = profileUser != null && !TextUtils.isEmpty(profileUser.getFullName())
                ? profileUser.getFullName()
                : safeOrDefault(
                profileUser != null ? profileUser.getUsername() : null,
                "User"
        );
        String peerAvatarUrl = profileUser != null ? profileUser.getAvatarUrl() : null;
        conversationRepository.findExistingDirectConversationId(currentUserId, targetUserId)
                .addOnSuccessListener(existingConversationId -> {
                    if (!isAdded()) {
                        return;
                    }
                    String conversationId = !TextUtils.isEmpty(existingConversationId)
                            ? existingConversationId
                            : ConversationRepository.buildDirectConversationId(currentUserId, targetUserId);
                    ((MainActivity) getActivity()).openChatDetail(
                            conversationId,
                            peerName,
                            peerAvatarUrl,
                            targetUserId
                    );
                    btnChat.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    String fallbackConversationId = ConversationRepository.buildDirectConversationId(currentUserId, targetUserId);
                    ((MainActivity) getActivity()).openChatDetail(
                            fallbackConversationId,
                            peerName,
                            peerAvatarUrl,
                            targetUserId
                    );
                    btnChat.setEnabled(true);
                });
    }

    private String buildFollowDocId(String followerId, String followingId) {
        return followerId + "_" + followingId;
    }

    private String buildFollowNotificationId() {
        return "follow_" + currentUserId + "_" + targetUserId;
    }

    private void createFollowNotification() {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            return;
        }

        String notificationId = buildFollowNotificationId();
        Notification notification = new Notification(
                notificationId,
                targetUserId,
                "FOLLOW",
                currentUserId,
                false
        );

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .set(notification);
    }

    private void removeFollowNotification() {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            return;
        }

        String notificationId = buildFollowNotificationId();
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .delete();
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

    private void setupRecycler() {
        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        postAdapter = new PostAdapter(requireContext(), this);
        postAdapter.setUseSearchLayout(true);
        userAdapter = new UserSearchAdapter(requireContext(), new UserSearchAdapter.OnUserActionListener() {
            @Override
            public void onUserClicked(User user) {
                if (user == null || user.getId() == null || user.getId().isEmpty()) {
                    return;
                }
                if (!TextUtils.isEmpty(currentUserId) && currentUserId.equals(user.getId())) {
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.nav_host_fragment, new ProfileFragment())
                            .addToBackStack(null)
                            .commit();
                    return;
                }
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(user.getId()))
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onFollowClicked(User user, int position) {
                Toast.makeText(requireContext(), "Follow from list - Coming soon", Toast.LENGTH_SHORT).show();
            }
        });
        userAdapter.setHideFollowButtonForSelf(true);
        rvPosts.setAdapter(postAdapter);
    }

    private void setupTabs() {
        tabThreads.setOnClickListener(v -> {
            selectedTab = "posts";
            updateTabUI();
            loadTabContent();
        });
        tabReplies.setOnClickListener(v -> {
            selectedTab = "followers";
            updateTabUI();
            loadTabContent();
        });
        tabReposts.setOnClickListener(v -> {
            selectedTab = "following";
            updateTabUI();
            loadTabContent();
        });
        updateTabUI();
    }

    private void updateTabUI() {
        tabThreads.setTextColor(requireContext().getColor(R.color.text));
        tabReplies.setTextColor(requireContext().getColor(R.color.muted));
        tabReposts.setTextColor(requireContext().getColor(R.color.muted));

        TextView selectedView = tabThreads;
        if ("followers".equals(selectedTab)) {
            selectedView = tabReplies;
            tabReplies.setTextColor(requireContext().getColor(R.color.text));
        } else if ("following".equals(selectedTab)) {
            selectedView = tabReposts;
            tabReposts.setTextColor(requireContext().getColor(R.color.text));
        }

        final TextView finalSelectedView = selectedView;
        tabIndicator.post(() -> {
            float center = finalSelectedView.getX() + finalSelectedView.getWidth() / 2f;
            tabIndicator.setX(center - tabIndicator.getWidth() / 2f);
        });
    }

    private void loadTabContent() {
        if (TextUtils.isEmpty(targetUserId) || !isAdded()) {
            return;
        }
        setTabLoading(true);
        tabEmptyState.setVisibility(View.GONE);

        if ("posts".equals(selectedTab)) {
            rvPosts.setAdapter(postAdapter);
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_POSTS)
                    .whereEqualTo("userId", targetUserId)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!isAdded()) {
                            return;
                        }
                        List<Post> posts = new ArrayList<>();
                        query.getDocuments().forEach(doc -> {
                            Post post = doc.toObject(Post.class);
                            if (post != null) {
                                post.setId(doc.getId());
                                posts.add(post);
                            }
                        });
                        postAdapter.setPosts(posts);
                        setTabLoading(false);
                        showEmptyIfNeeded(posts.isEmpty());
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) {
                            return;
                        }
                        postAdapter.setPosts(new ArrayList<>());
                        setTabLoading(false);
                        showEmptyIfNeeded(true);
                    });
            return;
        }

        rvPosts.setAdapter(userAdapter);
        String field = "followers".equals(selectedTab) ? "followingId" : "followerId";
        String idFieldToLoad = "followers".equals(selectedTab) ? "followerId" : "followingId";

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo(field, targetUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    query.getDocuments().forEach(doc -> {
                        String uid = doc.getString(idFieldToLoad);
                        if (uid != null && !uid.isEmpty()) {
                            ids.add(uid);
                        }
                    });
                    loadUsersByIds(ids);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    userAdapter.setUsers(new ArrayList<>());
                    setTabLoading(false);
                    showEmptyIfNeeded(true);
                });
    }

    private void loadUsersByIds(List<String> userIds) {
        if (!isAdded()) {
            return;
        }
        if (userIds == null || userIds.isEmpty()) {
            userAdapter.setUsers(new ArrayList<>());
            setTabLoading(false);
            showEmptyIfNeeded(true);
            return;
        }

        List<User> users = new ArrayList<>();
        int[] remaining = {userIds.size()};
        for (String uid : userIds) {
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                if (TextUtils.isEmpty(user.getId())) {
                                    user.setId(doc.getId());
                                }
                                users.add(user);
                            }
                        }
                        remaining[0]--;
                        if (remaining[0] == 0 && isAdded()) {
                            userAdapter.setUsers(users);
                            setTabLoading(false);
                            showEmptyIfNeeded(users.isEmpty());
                        }
                    })
                    .addOnFailureListener(e -> {
                        remaining[0]--;
                        if (remaining[0] == 0 && isAdded()) {
                            userAdapter.setUsers(users);
                            setTabLoading(false);
                            showEmptyIfNeeded(users.isEmpty());
                        }
                    });
        }
    }

    private void setTabLoading(boolean loading) {
        tabLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvPosts.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    private void showEmptyIfNeeded(boolean isEmpty) {
        if (!isEmpty) {
            tabEmptyState.setVisibility(View.GONE);
            return;
        }

        if ("followers".equals(selectedTab)) {
            tabEmptyState.setText(getString(R.string.profile_empty_followers));
        } else if ("following".equals(selectedTab)) {
            tabEmptyState.setText(getString(R.string.profile_empty_following));
        } else {
            tabEmptyState.setText(getString(R.string.profile_empty_posts));
        }
        tabEmptyState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onUserClicked(String userId) {
        if (userId == null || userId.isEmpty()) return;
        if (userId.equals(currentUserId)) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        } else if (!userId.equals(targetUserId)) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(userId))
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onLikeClicked(Post post, int position) {
        homeViewModel.toggleLike(post);
    }

    @Override
    public void onCommentClicked(Post post) {
        BottomSheetCommentFragment bottomSheetCommentFragment = BottomSheetCommentFragment.newInstance(post.getId());
        bottomSheetCommentFragment.show(getParentFragmentManager(), "comments_bottom_sheet");
    }

    @Override
    public void onShareClicked(Post post) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, post.getCaption());
        startActivity(android.content.Intent.createChooser(shareIntent, "Share post"));
    }

    @Override
    public void onBookmarkClicked(Post post) {
        homeViewModel.toggleBookmark(post);
    }

    @Override
    public void onComposerPostClicked(String content) {}

    @Override
    public void onComposerClicked() {}

    @Override
    public void onComposerImageClicked() {}

    @Override
    public void onEditPostClicked(Post post) {
        NewPostFragment editFragment = NewPostFragment.newInstanceForEdit(post.getId());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, editFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDeletePostClicked(Post post) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc chắn muốn xóa bài viết này?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    newPostViewModel.deletePost(post.getId());
                    loadTabContent();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onReportPostClicked(Post post) {
        String[] reasons = {"Nội dung không phù hợp", "Spam", "Quấy rối", "Thông tin sai lệch", "Khác"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Báo cáo bài viết")
                .setItems(reasons, (dialog, which) -> {
                    homeViewModel.reportPost(post, reasons[which]);
                    Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}

