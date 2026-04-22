package com.example.social_app.fragments;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.PostSearchAdapter;
import com.example.social_app.adapters.UserSearchAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.CloudinaryUploadUtil;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private TextView tvUsernameTop;
    private TextView tvName;
    private TextView tvHandle;
    private TextView tvBio;
    private TextView tvChangeAvatar;
    private TextView tabThreads;
    private TextView tabReplies;
    private TextView tabReposts;
    private TextView tvBlog;
    private TextView tvFollowed;
    private TextView tvFollower;
    private TextView tabEmptyState;
    private View tabIndicator;
    private View tabLoading;
    private ImageView imgAvatar;
    private Button btnEditProfile;
    private RecyclerView rvPosts;

    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();
    private FirebaseUser currentAuthUser;
    private String currentUserId;
    private User currentUserProfile;
    private Uri pendingAvatarUri;
    private Uri cameraOutputUri;
    private AlertDialog avatarDialog;
    private ImageView avatarLargePreview;
    private TextView tvChangeAvatarAction;
    private Button btnSaveAvatar;
    private String currentAvatarUrl;
    private boolean isAvatarUploading = false;
    private String selectedTab = "posts";
    private PostSearchAdapter postAdapter;
    private UserSearchAdapter userAdapter;

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || !isAdded()) {
                    return;
                }
                pendingAvatarUri = uri;
                if (avatarLargePreview != null) {
                    avatarLargePreview.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (!success || cameraOutputUri == null || !isAdded()) {
                    return;
                }
                pendingAvatarUri = cameraOutputUri;
                if (avatarLargePreview != null) {
                    avatarLargePreview.setImageURI(cameraOutputUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else if (isAdded()) {
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.profile_camera_permission_denied),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

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
        tvChangeAvatar = view.findViewById(R.id.tvChangeAvatar);
        tabThreads = view.findViewById(R.id.tabThreads);
        tabReplies = view.findViewById(R.id.tabReplies);
        tabReposts = view.findViewById(R.id.tabReposts);
        tvBlog = view.findViewById(R.id.tvBlog);
        tvFollowed = view.findViewById(R.id.tvFollowed);
        tvFollower = view.findViewById(R.id.tvFollower);
        tabEmptyState = view.findViewById(R.id.tabEmptyState);
        tabIndicator = view.findViewById(R.id.tabIndicator);
        tabLoading = view.findViewById(R.id.tabLoading);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        rvPosts = view.findViewById(R.id.rvPosts);

        imgAvatar.setOnClickListener(v -> openAvatarPreviewDialog());
        tvChangeAvatar.setOnClickListener(v -> openAvatarPreviewDialog());
        btnEditProfile.setOnClickListener(v -> openEditProfileDialog());

        setupRecycler();
        setupTabs();
    }

    private void loadCurrentUserProfile() {
        FirebaseManager firebaseManager = FirebaseManager.getInstance();
        FirebaseUser authUser = firebaseManager.getAuth().getCurrentUser();
        currentAuthUser = authUser;

        if (authUser == null) {
            Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = authUser.getUid();

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
                    user.setId(authUser.getUid());
                    currentUserProfile = user;

                    bindProfile(user, authUser);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), "Không tải được hồ sơ người dùng", Toast.LENGTH_SHORT).show();
                    bindFallbackProfile(authUser);
                });

        refreshProfileStats();
        loadTabContent();
    }

    private void bindProfile(User user, FirebaseUser authUser) {
        String fullName = safeOrDefault(user.getFullName(), "Người dùng");
        String username = safeOrDefault(user.getUsername(), authUser.getEmail() != null ? authUser.getEmail() : "username");
        String bio = safeOrDefault(user.getBio(), "Chưa có tiểu sử");

        tvName.setText(fullName);
        tvHandle.setText("@" + username.replace("@", ""));
        tvUsernameTop.setText(username);
        tvBio.setText(bio);

        currentAvatarUrl = user.getAvatarUrl();
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
        currentAvatarUrl = "";
        imgAvatar.setImageResource(R.drawable.avatar_placeholder);

        User fallbackUser = new User();
        fallbackUser.setId(authUser.getUid());
        fallbackUser.setFullName("Người dùng");
        fallbackUser.setUsername(username);
        fallbackUser.setEmail(email != null ? email : "");
        fallbackUser.setBio("Chưa có tiểu sử");
        fallbackUser.setAvatarUrl("");
        currentUserProfile = fallbackUser;
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

    private void openAvatarPreviewDialog() {
        if (!isAdded()) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_avatar_preview, null, false);

        avatarLargePreview = dialogView.findViewById(R.id.imgAvatarLarge);
        tvChangeAvatarAction = dialogView.findViewById(R.id.tvChangeAvatarAction);
        btnSaveAvatar = dialogView.findViewById(R.id.btnSaveAvatar);

        if (pendingAvatarUri != null) {
            avatarLargePreview.setImageURI(pendingAvatarUri);
        } else {
            avatarLargePreview.setImageDrawable(imgAvatar.getDrawable());
        }

        tvChangeAvatarAction.setOnClickListener(v -> showImageSourceChooser());
        btnSaveAvatar.setOnClickListener(v -> saveAvatarChange());

        avatarDialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_open_avatar_preview))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.profile_close), null)
                .create();
        avatarDialog.setOnDismissListener(dialog -> {
            avatarLargePreview = null;
            btnSaveAvatar = null;
            tvChangeAvatarAction = null;
            if (!isAvatarUploading) {
                pendingAvatarUri = null;
            }
        });
        avatarDialog.show();
    }

    private void showImageSourceChooser() {
        if (!isAdded()) {
            return;
        }
        String[] options = {
                getString(R.string.profile_pick_camera),
                getString(R.string.profile_pick_gallery)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_pick_image_source))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraAndOpen();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void requestCameraAndOpen() {
        if (!isAdded()) {
            return;
        }
        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        if (!isAdded()) {
            return;
        }
        try {
            File cacheDir = new File(requireContext().getCacheDir(), "images");
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            }
            String fileName = String.format(Locale.US, "avatar_%d.jpg", System.currentTimeMillis());
            File imageFile = new File(cacheDir, fileName);
            cameraOutputUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    imageFile
            );
            cameraLauncher.launch(cameraOutputUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Khong the mo camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAvatarChange() {
        if (!isAdded()) {
            return;
        }
        if (pendingAvatarUri == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_save_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentAuthUser == null || TextUtils.isEmpty(currentUserId)) {
            Toast.makeText(requireContext(), "Phien dang nhap khong hop le", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnSaveAvatar != null) {
            btnSaveAvatar.setEnabled(false);
            btnSaveAvatar.setText(getString(R.string.posting));
        }
        if (tvChangeAvatarAction != null) {
            tvChangeAvatarAction.setEnabled(false);
            tvChangeAvatarAction.setAlpha(0.5f);
        }
        isAvatarUploading = true;

        String cloudName = getString(R.string.cloudinary_cloud_name).trim();
        String uploadPreset = getString(R.string.cloudinary_upload_preset).trim();
        if (TextUtils.isEmpty(cloudName) || TextUtils.isEmpty(uploadPreset)) {
            if (btnSaveAvatar != null) {
                btnSaveAvatar.setEnabled(true);
                btnSaveAvatar.setText(getString(R.string.profile_save_avatar));
            }
            Toast.makeText(requireContext(), getString(R.string.cloudinary_config_missing), Toast.LENGTH_LONG).show();
            return;
        }

        CloudinaryUploadUtil.uploadImage(
                requireContext(),
                pendingAvatarUri,
                cloudName,
                uploadPreset,
                new CloudinaryUploadUtil.UploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        FirebaseManager.getInstance().getFirestore()
                                .collection(FirebaseManager.COLLECTION_USERS)
                                .document(currentUserId)
                                .update("avatarUrl", secureUrl)
                                .addOnSuccessListener(unused -> {
                                    if (!isAdded()) {
                                        return;
                                    }
                                    pendingAvatarUri = null;
                                    currentAvatarUrl = secureUrl;
                                    loadAvatar(secureUrl);
                                    isAvatarUploading = false;
                                    if (btnSaveAvatar != null) {
                                        btnSaveAvatar.setEnabled(true);
                                        btnSaveAvatar.setText(getString(R.string.profile_save_avatar));
                                    }
                                    if (tvChangeAvatarAction != null) {
                                        tvChangeAvatarAction.setEnabled(true);
                                        tvChangeAvatarAction.setAlpha(1f);
                                    }
                                    Toast.makeText(requireContext(), getString(R.string.profile_avatar_updated), Toast.LENGTH_SHORT).show();
                                    if (avatarDialog != null) {
                                        avatarDialog.dismiss();
                                    }
                                })
                                .addOnFailureListener(e -> onAvatarSaveFailed());
                    }

                    @Override
                    public void onError(String message, Throwable throwable) {
                        onAvatarSaveFailed(message);
                    }
                }
        );
    }

    private void onAvatarSaveFailed() {
        onAvatarSaveFailed(getString(R.string.profile_avatar_update_failed));
    }

    private void onAvatarSaveFailed(String message) {
        if (!isAdded()) {
            return;
        }
        isAvatarUploading = false;
        if (btnSaveAvatar != null) {
            btnSaveAvatar.setEnabled(true);
            btnSaveAvatar.setText(getString(R.string.profile_save_avatar));
        }
        if (tvChangeAvatarAction != null) {
            tvChangeAvatarAction.setEnabled(true);
            tvChangeAvatarAction.setAlpha(1f);
        }
        String errorText = TextUtils.isEmpty(message)
                ? getString(R.string.profile_avatar_update_failed)
                : getString(R.string.profile_avatar_update_failed) + ": " + message;
        Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show();
    }

    private void openEditProfileDialog() {
        if (!isAdded() || currentAuthUser == null) {
            return;
        }

        if (currentUserProfile == null) {
            loadCurrentUserProfile();
            Toast.makeText(requireContext(), getString(R.string.loading), Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_profile, null, false);

        EditText edtFullName = dialogView.findViewById(R.id.edtFullName);
        EditText edtUsername = dialogView.findViewById(R.id.edtUsername);
        EditText edtEmail = dialogView.findViewById(R.id.edtEmail);
        EditText edtBio = dialogView.findViewById(R.id.edtBio);
        EditText edtGender = dialogView.findViewById(R.id.edtGender);
        EditText edtDateOfBirth = dialogView.findViewById(R.id.edtDateOfBirth);
        ImageView imgEditProfileAvatar = dialogView.findViewById(R.id.imgEditProfileAvatar);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditProfile);
        Button btnDone = dialogView.findViewById(R.id.btnDoneEditProfile);

        imgEditProfileAvatar.setImageDrawable(imgAvatar.getDrawable());

        edtFullName.setText(safeOrDefault(currentUserProfile.getFullName(), ""));
        edtUsername.setText(safeOrDefault(currentUserProfile.getUsername(), ""));
        edtEmail.setText(safeOrDefault(currentUserProfile.getEmail(), ""));
        edtBio.setText(safeOrDefault(currentUserProfile.getBio(), ""));
        edtGender.setText(safeOrDefault(currentUserProfile.getGender(), ""));
        edtDateOfBirth.setText(safeOrDefault(currentUserProfile.getDateOfBirth(), ""));

        AlertDialog editDialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_edit_title))
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> editDialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String fullName = edtFullName.getText().toString().trim();
            String username = edtUsername.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String bio = edtBio.getText().toString().trim();
            String gender = edtGender.getText().toString().trim();
            String dateOfBirth = edtDateOfBirth.getText().toString().trim();

            if (TextUtils.isEmpty(username)) {
                edtUsername.setError(getString(R.string.profile_edit_required_username));
                return;
            }
            if (TextUtils.isEmpty(email)) {
                edtEmail.setError(getString(R.string.profile_edit_required_email));
                return;
            }

            btnDone.setEnabled(false);
            btnDone.setText(getString(R.string.posting));
            saveProfileChanges(editDialog, btnDone, fullName, username, email, bio, gender, dateOfBirth);
        });

        editDialog.show();
    }

    private void saveProfileChanges(
            AlertDialog dialog,
            Button btnDone,
            String fullName,
            String username,
            String email,
            String bio,
            String gender,
            String dateOfBirth
    ) {
        if (!isAdded() || TextUtils.isEmpty(currentUserId)) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("username", username);
        updates.put("email", email);
        updates.put("bio", bio);
        updates.put("gender", gender);
        updates.put("dateOfBirth", dateOfBirth);

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(currentUserId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }
                    currentUserProfile.setFullName(fullName);
                    currentUserProfile.setUsername(username);
                    currentUserProfile.setEmail(email);
                    currentUserProfile.setBio(bio);
                    currentUserProfile.setGender(gender);
                    currentUserProfile.setDateOfBirth(dateOfBirth);
                    bindProfile(currentUserProfile, currentAuthUser);
                    Toast.makeText(requireContext(), getString(R.string.profile_edit_save_success), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    btnDone.setEnabled(true);
                    btnDone.setText(getString(R.string.profile_edit_done));
                    String message = getString(R.string.profile_edit_save_failed) + ": " + e.getMessage();
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
    }

    private String safeOrDefault(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private void refreshProfileStats() {
        if (TextUtils.isEmpty(currentUserId)) {
            return;
        }

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followingId", currentUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvFollowed.setText(query.size() + " Followers");
                });

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followerId", currentUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvFollower.setText(query.size() + " Following");
                });

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POSTS)
                .whereEqualTo("userId", currentUserId)
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
        postAdapter = new PostSearchAdapter(requireContext(), post ->
                Toast.makeText(requireContext(), "View post", Toast.LENGTH_SHORT).show());
        userAdapter = new UserSearchAdapter(requireContext(), new UserSearchAdapter.OnUserActionListener() {
            @Override
            public void onUserClicked(User user) {
                if (user == null || user.getId() == null || user.getId().isEmpty()) {
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
        tabThreads.setTextSize(14f);
        tabReplies.setTextSize(14f);
        tabReposts.setTextSize(14f);

        TextView selectedView = tabThreads;
        if ("followers".equals(selectedTab)) {
            selectedView = tabReplies;
            tabReplies.setTextColor(requireContext().getColor(R.color.text));
        } else if ("following".equals(selectedTab)) {
            selectedView = tabReposts;
            tabReposts.setTextColor(requireContext().getColor(R.color.text));
        } else {
            tabThreads.setTextColor(requireContext().getColor(R.color.text));
        }

        final TextView finalSelectedView = selectedView;
        tabIndicator.post(() -> {
            float center = finalSelectedView.getX() + finalSelectedView.getWidth() / 2f;
            tabIndicator.setX(center - tabIndicator.getWidth() / 2f);
        });
    }

    private void loadTabContent() {
        if (TextUtils.isEmpty(currentUserId) || !isAdded()) {
            return;
        }
        setTabLoading(true);
        tabEmptyState.setVisibility(View.GONE);

        if ("posts".equals(selectedTab)) {
            rvPosts.setAdapter(postAdapter);
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_POSTS)
                    .whereEqualTo("userId", currentUserId)
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
                .whereEqualTo(field, currentUserId)
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
    public void onDestroyView() {
        super.onDestroyView();
        if (avatarDialog != null) {
            avatarDialog.dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        avatarExecutor.shutdownNow();
    }
}

