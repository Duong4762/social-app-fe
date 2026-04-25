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
import com.example.social_app.data.model.Report;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.data.model.Story;
import com.example.social_app.utils.StoryRingUi;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.example.social_app.viewmodels.StoryViewModel;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.lifecycle.ViewModelProvider;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;
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
    @Nullable
    private View otherProfileStoryRing;
    private ImageView btnBack;
    private ImageView btnMore;
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
    private void createFollowNotification(String receiverId, String followerId) {
        // receiverId: người nhận thông báo (người được follow)
        // followerId: người follow
        if (receiverId == null || followerId == null) return;
        if (receiverId.equals(followerId)) return; // Không tự thông báo cho chính mình

        String notificationId = "follow_" + followerId + "_" + receiverId;

        Map<String, Object> notification = new HashMap<>();
        notification.put("id", notificationId);
        notification.put("userId", receiverId);
        notification.put("actorId", followerId);
        notification.put("type", "FOLLOW");
        notification.put("referenceId", followerId);
        notification.put("isRead", false);
        notification.put("createdAt", FieldValue.serverTimestamp());

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .set(notification)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("OtherProfile", "Follow notification created: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("OtherProfile", "Failed to create notification", e);
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        bindViews(view);
        setupActions();
        loadUserProfile();

        StoryViewModel storyVm = new ViewModelProvider(requireActivity()).get(StoryViewModel.class);
        storyVm.getStories().observe(getViewLifecycleOwner(), stories -> {
            applyOtherProfileStoryRing(stories);
            if (postAdapter != null) {
                postAdapter.setStoriesForAvatarRings(stories);
            }
        });
    }

    private void applyOtherProfileStoryRing(@Nullable List<Story> stories) {
        if (otherProfileStoryRing == null || TextUtils.isEmpty(targetUserId)) {
            return;
        }
        StoryRingUi.Tone tone = StoryRingUi.toneForUser(targetUserId, stories, currentUserId);
        StoryRingUi.apply(otherProfileStoryRing, tone, 84f);
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
        otherProfileStoryRing = view.findViewById(R.id.other_profile_story_ring);
        btnBack = view.findViewById(R.id.btnBack);
        btnMore = view.findViewById(R.id.btnMore);
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
        if (btnMore != null) {
            btnMore.setOnClickListener(this::showProfileOptionsMenu);
        }
        imgAvatar.setOnClickListener(v -> openAvatarPreviewOnly());
        btnFollow.setOnClickListener(v -> onFollowButtonClicked());
        btnChat.setOnClickListener(v -> openChatWithProfileUser());
    }

    private void showProfileOptionsMenu(@NonNull View anchor) {
        if (!isAdded() || TextUtils.isEmpty(targetUserId)) {
            return;
        }
        if (!TextUtils.isEmpty(currentUserId) && currentUserId.equals(targetUserId)) {
            return;
        }
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        popup.getMenu().add(getString(R.string.menu_report));
        popup.setOnMenuItemClickListener(item -> {
            if (getString(R.string.menu_report).contentEquals(item.getTitle())) {
                showReportUserDetailDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showReportUserDetailDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.report_reason_hint);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.addView(input);
        input.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.report_reason_title)
                .setView(container)
                .setPositiveButton(R.string.report_submit, (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = getString(R.string.report_reason_other);
                    }
                    submitUserReport(reason);
                })
                .setNegativeButton(R.string.cancel_action, null)
                .show();
    }


    private void submitUserReport(@NonNull String reason) {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(targetUserId)) {
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        com.google.firebase.firestore.DocumentReference reportRef = FirebaseManager.getInstance()
                .getFirestore()
                .collection(FirebaseManager.COLLECTION_REPORTS)
                .document();
        Report report = new Report();
        report.setId(reportRef.getId());
        report.setReporterId(currentUserId);
        report.setTargetId(targetUserId);
        report.setType("USER");
        report.setStatus("UNPROCESSED");
        report.setReason(reason);
        reportRef.set(report)
                .addOnSuccessListener(unused ->
                        Toast.makeText(requireContext(), getString(R.string.report_thanks), Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Gui report that bai", Toast.LENGTH_SHORT).show());
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
            Toast.makeText(requireContext(), getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(requireContext(), getString(R.string.user_info_not_found), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindProfile(user);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), getString(R.string.user_load_failed), Toast.LENGTH_SHORT).show();
                });

        refreshFollowState();
        refreshProfileStats();
        loadTabContent();
    }

    private void bindProfile(User user) {
        profileUser = user;
        String fullName = safeOrDefault(user.getFullName(), getString(R.string.default_user_name));
        String username = safeOrDefault(user.getUsername(), getString(R.string.default_username));
        String bio = safeOrDefault(user.getBio(), getString(R.string.default_bio));

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
                    // Tạo notification cho người được follow
                    createFollowNotification(targetUserId, currentUserId);

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
        Map<String, Object> notification = new HashMap<>();
        notification.put("id", notificationId);
        notification.put("userId", targetUserId);
        notification.put("type", "FOLLOW");
        notification.put("actorId", currentUserId);
        notification.put("referenceId", currentUserId); // Follow thì reference chính là người follow
        notification.put("isRead", false);
        notification.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .set(notification, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    // Gửi Push Notification
                    FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_USERS).document(currentUserId).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    String actorName = userDoc.getString("fullName");
                                    if (actorName == null || actorName.isEmpty()) actorName = userDoc.getString("username");
                                    String title = "Người theo dõi mới";
                                    String body = actorName + " đã bắt đầu theo dõi bạn";
                                    sendPushNotification(targetUserId, title, body, "FOLLOW", currentUserId);
                                }
                            });
                });
    }

    private void sendPushNotification(String targetUserId, String title, String body, String type, String refId) {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS).document(targetUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String token = documentSnapshot.getString("fcmToken");
                        if (token != null && !token.isEmpty()) {
                            com.example.social_app.firebase.FcmSender.sendNotification(token, title, body, type, refId, targetUserId);
                        }
                    }
                });
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
                    tvFollowed.setText(getString(R.string.followers_count_label, String.valueOf(followerCount)));
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
                    tvFollower.setText(getString(R.string.following_count_label, String.valueOf(followingCount)));
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
                    tvBlog.setText(getString(R.string.posts_count_label, String.valueOf(query.size())));
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
                Toast.makeText(requireContext(), getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
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
        tabThreads.setSelected("posts".equals(selectedTab));
        tabReplies.setSelected("followers".equals(selectedTab));
        tabReposts.setSelected("following".equals(selectedTab));

        TextView selectedView = tabThreads;
        if ("followers".equals(selectedTab)) {
            selectedView = tabReplies;
        } else if ("following".equals(selectedTab)) {
            selectedView = tabReposts;
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
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)));
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
                .setTitle(getString(R.string.delete_post_title))
                .setMessage(getString(R.string.delete_post_confirm))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    newPostViewModel.deletePost(post.getId());
                    loadTabContent();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    // ==================== REPORT BÀI VIẾT ====================

    @Override
    public void onReportPostClicked(Post post) {
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập để báo cáo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId.equals(post.getUserId())) {
            Toast.makeText(requireContext(), "Bạn không thể báo cáo bài viết của chính mình", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] reasons = {
                "Nội dung không phù hợp",
                "Spam",
                "Quấy rối",
                "Thông tin sai sự thật",
                "Lý do khác"
        };

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Báo cáo bài viết")
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];

                    if (which == 4) {
                        showReportDetailInputDialog(post, selectedReason);
                    } else {
                        homeViewModel.reportPost(post, selectedReason);
                        Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showReportDetailInputDialog(Post post, String baseReason) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Nhập chi tiết lý do");
        input.setPadding(40, 20, 40, 20);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Nhập chi tiết lý do báo cáo")
                .setView(input)
                .setPositiveButton("Gửi", (dialog, which) -> {
                    String detail = input.getText().toString().trim();
                    String finalReason = detail.isEmpty() ? baseReason : baseReason + ": " + detail;
                    homeViewModel.reportPost(post, finalReason);
                    Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}

