package com.example.social_app.fragments;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import com.example.social_app.fragments.OtherProfileFragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.adapters.UserSearchAdapter;
import com.example.social_app.data.model.Follow;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.utils.CloudinaryUploadUtil;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.example.social_app.widgets.AvatarCropperView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Calendar;

import androidx.lifecycle.ViewModelProvider;

public class ProfileFragment extends Fragment implements PostAdapter.OnPostActionListener {

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

    private FirebaseUser currentAuthUser;
    private String currentUserId;
    private User currentUserProfile;
    private Uri pendingAvatarUri;
    private Uri cameraOutputUri;
    private AlertDialog avatarDialog;
    private AvatarCropperView avatarCropperView;
    private TextView tvChangeAvatarAction;
    private Button btnSaveAvatar;
    private String currentAvatarUrl;
    private boolean isAvatarUploading = false;
    private String selectedTab = "posts";
    private PostAdapter postAdapter;
    private UserSearchAdapter userAdapter;
    private final Set<String> myFollowingUserIds = new HashSet<>();
    private HomeViewModel homeViewModel;
    private NewPostViewModel newPostViewModel;

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || !isAdded()) {
                    return;
                }
                pendingAvatarUri = uri;
                if (avatarCropperView != null) {
                    avatarCropperView.setImageUri(uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (!success || cameraOutputUri == null || !isAdded()) {
                    return;
                }
                pendingAvatarUri = cameraOutputUri;
                if (avatarCropperView != null) {
                    avatarCropperView.setImageUri(cameraOutputUri);
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
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
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
            Toast.makeText(requireContext(), getString(R.string.session_expired), Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = authUser.getUid();
        if (userAdapter != null) {
            userAdapter.setCurrentUserId(currentUserId);
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
                    user.setId(authUser.getUid());
                    currentUserProfile = user;

                    bindProfile(user, authUser);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), getString(R.string.profile_load_failed), Toast.LENGTH_SHORT).show();
                    bindFallbackProfile(authUser);
                });

        refreshProfileStats();
        loadTabContent();
    }

    private void bindProfile(User user, FirebaseUser authUser) {
        String fullName = safeOrDefault(user.getFullName(), getString(R.string.default_user_name));
        String username = safeOrDefault(user.getUsername(), authUser.getEmail() != null ? authUser.getEmail() : getString(R.string.default_username));
        String bio = safeOrDefault(user.getBio(), getString(R.string.default_bio));

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
                : getString(R.string.default_username);

        tvName.setText(getString(R.string.default_user_name));
        tvHandle.setText("@" + username);
        tvUsernameTop.setText(username);
        tvBio.setText(getString(R.string.default_bio));
        currentAvatarUrl = "";
        loadAvatar("");

        User fallbackUser = new User();
        fallbackUser.setId(authUser.getUid());
        fallbackUser.setFullName(getString(R.string.default_user_name));
        fallbackUser.setUsername(username);
        fallbackUser.setEmail(email != null ? email : "");
        fallbackUser.setBio(getString(R.string.default_bio));
        fallbackUser.setAvatarUrl("");
        currentUserProfile = fallbackUser;
    }

    private void loadAvatar(String avatarUrl) {
        UserAvatarLoader.load(imgAvatar, avatarUrl);
    }

    private void openAvatarPreviewDialog() {
        if (!isAdded()) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_avatar_preview, null, false);

        avatarCropperView = dialogView.findViewById(R.id.avatarCropperView);
        tvChangeAvatarAction = dialogView.findViewById(R.id.tvChangeAvatarAction);
        btnSaveAvatar = dialogView.findViewById(R.id.btnSaveAvatar);

        if (pendingAvatarUri != null) {
            avatarCropperView.setImageUri(pendingAvatarUri);
        } else {
            avatarCropperView.setBitmap(drawableToBitmap(imgAvatar.getDrawable()));
        }

        tvChangeAvatarAction.setOnClickListener(v -> showImageSourceChooser());
        btnSaveAvatar.setOnClickListener(v -> saveAvatarChange());

        avatarDialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.profile_open_avatar_preview))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.profile_close), null)
                .create();
        avatarDialog.setOnDismissListener(dialog -> {
            avatarCropperView = null;
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
            Toast.makeText(requireContext(), getString(R.string.profile_camera_open_failed), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), getString(R.string.session_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        if (avatarCropperView == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_save_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uploadUri = createCroppedAvatarTempUri();
        if (uploadUri == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_save_empty), Toast.LENGTH_SHORT).show();
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
        CloudinaryUploadUtil.UploadCallback callback = new CloudinaryUploadUtil.UploadCallback() {
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
        };

        if (!TextUtils.isEmpty(cloudName) && !TextUtils.isEmpty(uploadPreset)) {
            CloudinaryUploadUtil.uploadMedia(
                    requireContext(),
                    uploadUri,
                    cloudName,
                    uploadPreset,
                    callback
            );
            return;
        }

        // Fallback: dùng config mặc định trong CloudinaryUploadUtil khi strings chưa cấu hình.
        CloudinaryUploadUtil.uploadMedia(
                requireContext(),
                uploadUri,
                callback
        );
    }

    @Nullable
    private Uri createCroppedAvatarTempUri() {
        if (!isAdded() || avatarCropperView == null) {
            return null;
        }
        Bitmap croppedBitmap = avatarCropperView.getCroppedCircularBitmap(1024);
        if (croppedBitmap == null) {
            return null;
        }

        try {
            File cacheDir = new File(requireContext().getCacheDir(), "images");
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            }
            File outputFile = new File(
                    cacheDir,
                    String.format(Locale.US, "avatar_crop_%d.png", System.currentTimeMillis())
            );
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }
            return FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    outputFile
            );
        } catch (IOException e) {
            return null;
        } finally {
            croppedBitmap.recycle();
        }
    }

    @Nullable
    private Bitmap drawableToBitmap(@Nullable Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 512;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 512;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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
        edtDateOfBirth.setText(formatDateForDisplay(currentUserProfile.getDateOfBirth()));
        setupDateOfBirthPicker(edtDateOfBirth);

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
            String dateOfBirthDisplay = edtDateOfBirth.getText().toString().trim();
            String dateOfBirthStore = formatDateForStorage(dateOfBirthDisplay);

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
            saveProfileChanges(editDialog, btnDone, fullName, username, email, bio, gender, dateOfBirthStore);
        });

        editDialog.show();
        if (editDialog.getWindow() != null) {
            editDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void setupDateOfBirthPicker(@NonNull EditText edtDateOfBirth) {
        View.OnClickListener openDatePicker = v -> {
            Calendar calendar = Calendar.getInstance();
            String currentDob = edtDateOfBirth.getText() != null
                    ? edtDateOfBirth.getText().toString().trim()
                    : "";
            if (!TextUtils.isEmpty(currentDob) && currentDob.matches("\\d{2}/\\d{2}/\\d{4}")) {
                try {
                    String[] parts = currentDob.split("/");
                    calendar.set(
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[0])
                    );
                } catch (Exception ignored) {
                    // Keep current date when parsing fails.
                }
            }

            DatePickerDialog picker = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> edtDateOfBirth.setText(
                            String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    ),
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            picker.show();
        };
        edtDateOfBirth.setOnClickListener(openDatePicker);
        edtDateOfBirth.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                openDatePicker.onClick(v);
            }
        });
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

    private String formatDateForDisplay(String storageDate) {
        if (TextUtils.isEmpty(storageDate) || !storageDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return storageDate;
        }
        try {
            String[] parts = storageDate.split("-");
            return String.format(Locale.US, "%s/%s/%s", parts[2], parts[1], parts[0]);
        } catch (Exception e) {
            return storageDate;
        }
    }

    private String formatDateForStorage(String displayDate) {
        if (TextUtils.isEmpty(displayDate) || !displayDate.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return displayDate;
        }
        try {
            String[] parts = displayDate.split("/");
            return String.format(Locale.US, "%s-%s-%s", parts[2], parts[1], parts[0]);
        } catch (Exception e) {
            return displayDate;
        }
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
                    tvFollowed.setText(getString(R.string.followers_count_label, String.valueOf(query.size())));
                });

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followerId", currentUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvFollower.setText(getString(R.string.following_count_label, String.valueOf(query.size())));
                });

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POSTS)
                .whereEqualTo("userId", currentUserId)
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
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(user.getId()))
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onFollowClicked(User user, int position) {
                onFollowUserFromList(user);
            }
        });
        userAdapter.setCurrentUserId(currentUserId);
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

        tabThreads.setTextSize(14f);
        tabReplies.setTextSize(14f);
        tabReposts.setTextSize(14f);

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
                            refreshMyFollowingState();
                            setTabLoading(false);
                            showEmptyIfNeeded(users.isEmpty());
                        }
                    })
                    .addOnFailureListener(e -> {
                        remaining[0]--;
                        if (remaining[0] == 0 && isAdded()) {
                            userAdapter.setUsers(users);
                            refreshMyFollowingState();
                            setTabLoading(false);
                            showEmptyIfNeeded(users.isEmpty());
                        }
                    });
        }
    }

    private void refreshMyFollowingState() {
        if (TextUtils.isEmpty(currentUserId) || userAdapter == null) {
            return;
        }
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .whereEqualTo("followerId", currentUserId)
                .get()
                .addOnSuccessListener(query -> {
                    myFollowingUserIds.clear();
                    query.getDocuments().forEach(doc -> {
                        String followingId = doc.getString("followingId");
                        if (!TextUtils.isEmpty(followingId)) {
                            myFollowingUserIds.add(followingId);
                        }
                    });
                    if (isAdded() && userAdapter != null) {
                        userAdapter.setFollowedUserIds(myFollowingUserIds);
                    }
                });
    }

    private void onFollowUserFromList(@Nullable User user) {
        if (user == null || TextUtils.isEmpty(user.getId()) || TextUtils.isEmpty(currentUserId)) {
            return;
        }
        String targetUserId = user.getId();
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        boolean isFollowing = myFollowingUserIds.contains(targetUserId);
        String followDocId = currentUserId + "_" + targetUserId;

        if (isFollowing) {
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_FOLLOWS)
                    .document(followDocId)
                    .delete()
                    .addOnSuccessListener(unused -> {
                        myFollowingUserIds.remove(targetUserId);
                        if (userAdapter != null) {
                            userAdapter.updateFollowState(targetUserId, false);
                        }
                        if ("following".equals(selectedTab)) {
                            loadTabContent();
                        }
                    });
            return;
        }

        Follow follow = new Follow(followDocId, currentUserId, targetUserId);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_FOLLOWS)
                .document(followDocId)
                .set(follow)
                .addOnSuccessListener(unused -> {
                    myFollowingUserIds.add(targetUserId);
                    if (userAdapter != null) {
                        userAdapter.updateFollowState(targetUserId, true);
                    }
                    if ("following".equals(selectedTab)) {
                        loadTabContent();
                    }
                });
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
            // Already on ProfileFragment, just refresh if needed or do nothing
            loadTabContent();
        } else {
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

    @Override
    public void onReportPostClicked(Post post) {
        String[] reasons = {
                getString(R.string.report_reason_inappropriate),
                getString(R.string.report_reason_spam),
                getString(R.string.report_reason_harassment),
                getString(R.string.report_reason_false_info),
                getString(R.string.report_reason_other)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.report_post_title))
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];
                    showReportDetailDialog(post, selectedReason);
                })
                .setNegativeButton(getString(R.string.cancel_action), null)
                .show();
    }

    private void showReportDetailDialog(Post post, String baseReason) {
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
                    String detail = input.getText().toString().trim();
                    String finalReason = detail.isEmpty() ? baseReason : baseReason + ": " + detail;
                    homeViewModel.reportPost(post, finalReason);
                    Toast.makeText(requireContext(), getString(R.string.report_thanks), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel_action, null)
                .show();
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
    }
}

