package com.example.social_app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.social_app.R;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.viewmodels.NewPostViewModel;

import java.util.ArrayList;
import java.util.List;

public class NewPostFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_VIDEO_REQUEST = 2;

    private EditText postInput;
    private TextView userName;
    private ImageButton cameraButton;
    private Button uploadImageButton, uploadVideoButton, postButton;
    private ImageButton cancelButton;
    private ImageView userAvatar;

    private LinearLayout photoPreviewContainer;
    private LinearLayout videoPreviewContainer;
    private TextView photoSectionTitle;
    private TextView videoSectionTitle;
    private View photoScrollView;
    private View videoScrollView;

    private NewPostViewModel newPostViewModel;
    private List<Uri> selectedMedias = new ArrayList<>();
    private Uri cameraImageUri;
    private String mEditPostId = null;

    private static final int MAX_CHARACTERS = 280;
    private static final int CAMERA_REQUEST = 3;
    private static final int CAMERA_PERMISSION_REQUEST = 4;

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 15 * 1024 * 1024; // 15MB

    public static NewPostFragment newInstance() {
        return new NewPostFragment();
    }

    public static NewPostFragment newInstanceWithImage() {
        NewPostFragment fragment = new NewPostFragment();
        Bundle args = new Bundle();
        args.putBoolean("open_picker", true);
        fragment.setArguments(args);
        return fragment;
    }

    public static NewPostFragment newInstanceForEdit(String postId) {
        NewPostFragment fragment = new NewPostFragment();
        Bundle args = new Bundle();
        args.putString("edit_post_id", postId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        selectedMedias = new ArrayList<>();

        if (getArguments() != null) {
            mEditPostId = getArguments().getString("edit_post_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_post, container, false);
        initializeViews(view);
        setupListeners();
        setupObservers();

        if (mEditPostId != null) {
            loadPostDataForEdit(mEditPostId);
            postButton.setText(R.string.save);
            TextView titleView = view.findViewById(R.id.new_post_title);
            if (titleView != null) titleView.setText(R.string.edit_post);
        } else {
            updatePostButtonState();
        }

        renderMediaPreview();

        // Kiểm tra xem có cần mở picker ngay không
        if (getArguments() != null && getArguments().getBoolean("open_picker", false)) {
            getArguments().remove("open_picker");
            view.post(this::pickImage);
        }

        return view;
    }

    private void loadPostDataForEdit(String postId) {
        newPostViewModel.loadPostForEdit(postId);
        
        newPostViewModel.getEditingPost().observe(getViewLifecycleOwner(), post -> {
            if (post != null && mEditPostId != null) {
                if (postInput.getText().toString().isEmpty()) {
                    postInput.setText(post.getCaption());
                }
                updatePostButtonState();
            }
        });

        newPostViewModel.getEditingMedia().observe(getViewLifecycleOwner(), mediaList -> {
            if (mediaList != null && !mediaList.isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (com.example.social_app.data.model.PostMedia media : mediaList) {
                    urls.add(media.getMediaUrl());
                }
                newPostViewModel.setExistingMediaUrls(urls);
            }
        });
    }

    private void initializeViews(View view) {
        postInput = view.findViewById(R.id.post_input);
        userName = view.findViewById(R.id.user_name);
        cameraButton = view.findViewById(R.id.camera_button);
        uploadImageButton = view.findViewById(R.id.upload_image_button);
        uploadVideoButton = view.findViewById(R.id.upload_video_button);
        userAvatar = view.findViewById(R.id.user_avatar);
        postButton = view.findViewById(R.id.post_button);
        cancelButton = view.findViewById(R.id.cancel_button);

        photoPreviewContainer = view.findViewById(R.id.photo_preview_container);
        videoPreviewContainer = view.findViewById(R.id.video_preview_container);
        photoSectionTitle = view.findViewById(R.id.photo_section_title);
        videoSectionTitle = view.findViewById(R.id.video_section_title);
        photoScrollView = view.findViewById(R.id.photo_scroll_view);
        videoScrollView = view.findViewById(R.id.video_scroll_view);

        loadCurrentUserAvatar();

        if (postInput != null) {
            postInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARACTERS)});
        }

        if (postButton != null) {
            postButton.setEnabled(false);
        }
    }

    private void loadCurrentUserAvatar() {
        String uid = com.example.social_app.firebase.FirebaseManager.getInstance().getAuth().getUid();
        
        // Initial placeholder
        if (userAvatar != null) {
            userAvatar.setImageResource(R.drawable.avatar_placeholder);
        }
        if (userName != null) {
            userName.setText("Loading...");
        }

        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection(com.example.social_app.firebase.FirebaseManager.COLLECTION_USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (isAdded() && documentSnapshot.exists()) {
                            String avatarUrl = documentSnapshot.getString("avatarUrl");
                            String name = documentSnapshot.getString("fullName");
                            
                            UserAvatarLoader.load(userAvatar, avatarUrl);
                            if (userName != null) {
                                userName.setText(name != null ? name : "Unknown User");
                            }
                        } else if (isAdded()) {
                            UserAvatarLoader.load(userAvatar, null);
                            if (userName != null) userName.setText("Unknown User");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            UserAvatarLoader.load(userAvatar, null);
                            if (userName != null) userName.setText("Unknown User");
                        }
                    });
        }
    }

    private void setupListeners() {
        postInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePostButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        uploadImageButton.setOnClickListener(v -> pickImage());
        uploadVideoButton.setOnClickListener(v -> pickVideo());
        cameraButton.setOnClickListener(v -> openCamera());

        postButton.setOnClickListener(v -> createPost());
        cancelButton.setOnClickListener(v -> handleCancel());
    }

    private void setupObservers() {
        newPostViewModel.getIsPosting().observe(getViewLifecycleOwner(), isPosting -> {
            postButton.setEnabled(!isPosting);
            String buttonText;
            if (isPosting) {
                buttonText = getString(R.string.loading_generic);
            } else {
                buttonText = (mEditPostId != null) ? getString(R.string.save) : getString(R.string.post);
            }
            postButton.setText(buttonText);
            cancelButton.setEnabled(!isPosting);
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), mEditPostId != null ? getString(R.string.update_post_success) : getString(R.string.post_created), Toast.LENGTH_SHORT).show();
                returnToHome();
            }
        });

        newPostViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        newPostViewModel.getExistingMediaUrls().observe(getViewLifecycleOwner(), urls -> {
            renderMediaPreview();
            updatePostButtonState();
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_images)), PICK_IMAGE_REQUEST);
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_video)), PICK_VIDEO_REQUEST);
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        
        java.io.File photoFile = null;
        try {
            String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            java.io.File storageDir = new java.io.File(requireContext().getExternalFilesDir(null), "Pictures");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            photoFile = java.io.File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (java.io.IOException ex) {
            Toast.makeText(getContext(), getString(R.string.error_creating_file, ex.getMessage()), Toast.LENGTH_SHORT).show();
        }

        if (photoFile != null) {
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile);
            takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(takePictureIntent, CAMERA_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(getContext(), R.string.profile_camera_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createPost() {
        String content = postInput.getText().toString().trim();
        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        if (existingUrls == null) existingUrls = new ArrayList<>();

        if (content.isEmpty() && selectedMedias.isEmpty() && existingUrls.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.comment_empty_error), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use default values for removed features
        String defaultPrivacy = "EVERYONE";
        String defaultLocation = null;
        List<String> defaultTagged = new ArrayList<>();

        if (mEditPostId != null) {
            newPostViewModel.updatePost(mEditPostId, content, selectedMedias, existingUrls, defaultPrivacy, defaultLocation, defaultTagged);
        } else {
            newPostViewModel.createPost(content, selectedMedias, defaultLocation, defaultTagged, defaultPrivacy);
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.discard_post_question)
                    .setMessage(R.string.discard_post_confirm)
                    .setPositiveButton(R.string.discard, (dialog, which) -> returnToHome())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            returnToHome();
        }
    }

    private void returnToHome() {
        if (getFragmentManager() != null) {
            getFragmentManager().popBackStack();
        }
    }

    private boolean hasUnsavedChanges() {
        return !postInput.getText().toString().isEmpty() || !selectedMedias.isEmpty();
    }

    private void updatePostButtonState() {
        boolean hasContent = !postInput.getText().toString().trim().isEmpty() || !selectedMedias.isEmpty();
        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        if (existingUrls != null && !existingUrls.isEmpty()) hasContent = true;
        postButton.setEnabled(hasContent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                handleImagePicked(data);
            } else if (requestCode == PICK_VIDEO_REQUEST && data != null) {
                handleVideoPicked(data);
            } else if (requestCode == CAMERA_REQUEST) {
                if (cameraImageUri != null) {
                    long size = getFileSize(cameraImageUri);
                    if (size > MAX_IMAGE_SIZE) {
                        Toast.makeText(getContext(), getString(R.string.error_image_too_large), Toast.LENGTH_LONG).show();
                    } else if (!selectedMedias.contains(cameraImageUri)) {
                        selectedMedias.add(cameraImageUri);
                    }
                }
            }
            renderMediaPreview();
            updatePostButtonState();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraImageUri != null) {
            outState.putParcelable("cameraImageUri", cameraImageUri);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            cameraImageUri = savedInstanceState.getParcelable("cameraImageUri");
        }
    }

    private void handleImagePicked(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                validateAndAddMedia(uri, MAX_IMAGE_SIZE, getString(R.string.error_image_too_large));
            }
        } else if (data.getData() != null) {
            validateAndAddMedia(data.getData(), MAX_IMAGE_SIZE, getString(R.string.error_image_too_large));
        }
    }

    private void handleVideoPicked(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                validateAndAddMedia(uri, MAX_VIDEO_SIZE, getString(R.string.error_video_too_large));
            }
        } else if (data.getData() != null) {
            validateAndAddMedia(data.getData(), MAX_VIDEO_SIZE, getString(R.string.error_video_too_large));
        }
    }

    private void validateAndAddMedia(Uri uri, long maxSize, String errorMessage) {
        long size = getFileSize(uri);
        if (size > maxSize) {
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
        } else {
            selectedMedias.add(uri);
        }
    }

    private long getFileSize(Uri uri) {
        try {
            android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                long size = cursor.getLong(sizeIndex);
                cursor.close();
                return size;
            }
        } catch (Exception e) {
            android.util.Log.e("NewPostFragment", "Error getting file size", e);
        }
        return 0;
    }

    private String getMimeType(Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals(android.content.ContentResolver.SCHEME_CONTENT)) {
            android.content.ContentResolver cr = getContext().getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }

    private void renderMediaPreview() {
        photoPreviewContainer.removeAllViews();
        videoPreviewContainer.removeAllViews();
        
        LayoutInflater inflater = LayoutInflater.from(getContext());
        
        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        if (existingUrls != null) {
            for (int i = 0; i < existingUrls.size(); i++) {
                String url = existingUrls.get(i);
                boolean isVideo = url.contains("video/upload") || url.toLowerCase().endsWith(".mp4");
                
                View mediaItem = inflater.inflate(R.layout.item_media_preview, isVideo ? videoPreviewContainer : photoPreviewContainer, false);
                ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
                ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
                ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

                com.bumptech.glide.Glide.with(this).load(url).into(imgMedia);
                playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

                imgMedia.setOnClickListener(v -> showMediaFullscreen(url, isVideo));

                int idx = i;
                btnRemove.setOnClickListener(v -> newPostViewModel.removeExistingMedia(idx));
                
                if (isVideo) videoPreviewContainer.addView(mediaItem);
                else photoPreviewContainer.addView(mediaItem);
            }
        }

        for (int i = 0; i < selectedMedias.size(); i++) {
            Uri mediaUri = selectedMedias.get(i);
            String mimeType = getMimeType(mediaUri);
            boolean isVideo = (mimeType != null && mimeType.startsWith("video/")) || mediaUri.toString().toLowerCase().contains("video");

            View mediaItem = inflater.inflate(R.layout.item_media_preview, isVideo ? videoPreviewContainer : photoPreviewContainer, false);
            ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
            ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
            ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

            imgMedia.setImageURI(mediaUri);
            playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

            imgMedia.setOnClickListener(v -> showMediaFullscreen(mediaUri.toString(), isVideo));

            int idx = i;
            btnRemove.setOnClickListener(v -> {
                selectedMedias.remove(idx);
                renderMediaPreview();
                updatePostButtonState();
            });

            if (isVideo) videoPreviewContainer.addView(mediaItem);
            else photoPreviewContainer.addView(mediaItem);
        }

        boolean hasPhotos = photoPreviewContainer.getChildCount() > 0;
        boolean hasVideos = videoPreviewContainer.getChildCount() > 0;
        
        photoSectionTitle.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        photoScrollView.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        videoSectionTitle.setVisibility(hasVideos ? View.VISIBLE : View.GONE);
        videoScrollView.setVisibility(hasVideos ? View.VISIBLE : View.GONE);
    }

    private void showMediaFullscreen(String mediaUrl, boolean isVideo) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_media_viewer, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        ImageView fullscreenImage = dialogView.findViewById(R.id.fullscreen_image);
        android.widget.VideoView fullscreenVideo = dialogView.findViewById(R.id.fullscreen_video);
        ImageView icPlay = dialogView.findViewById(R.id.ic_play_video);
        View btnClose = dialogView.findViewById(R.id.btn_close_viewer);

        if (isVideo) {
            fullscreenVideo.setVisibility(View.VISIBLE);
            fullscreenImage.setVisibility(View.GONE);
            icPlay.setVisibility(View.VISIBLE);

            fullscreenVideo.setVideoPath(mediaUrl);
            fullscreenVideo.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                icPlay.setVisibility(View.GONE);
                fullscreenVideo.start();
            });
            
            fullscreenVideo.setOnClickListener(v -> {
                if (fullscreenVideo.isPlaying()) {
                    fullscreenVideo.pause();
                    icPlay.setVisibility(View.VISIBLE);
                } else {
                    fullscreenVideo.start();
                    icPlay.setVisibility(View.GONE);
                }
            });
        } else {
            fullscreenImage.setVisibility(View.VISIBLE);
            fullscreenVideo.setVisibility(View.GONE);
            icPlay.setVisibility(View.GONE);
            com.bumptech.glide.Glide.with(this).load(mediaUrl).into(fullscreenImage);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}