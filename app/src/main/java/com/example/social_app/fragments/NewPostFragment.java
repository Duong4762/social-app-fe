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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
    private ImageButton cameraButton;
    private Button uploadImageButton, uploadVideoButton, postButton;
    private ImageButton cancelButton;
    private ImageView userAvatar;
    private Spinner privacySpinner;

    private LinearLayout photoPreviewContainer;
    private LinearLayout videoPreviewContainer;
    private TextView photoSectionTitle;
    private TextView videoSectionTitle;
    private View photoScrollView;
    private View videoScrollView;
    private LinearLayout addLocationRow, tagPeopleRow;

    private NewPostViewModel newPostViewModel;
    private List<Uri> selectedMedias = new ArrayList<>();
    private Uri cameraImageUri;
    private String selectedLocation = null;
    private List<String> taggedPeople = new ArrayList<>();
    private String privacyLevel = "Everyone";
    private String mEditPostId = null;

    private static final int MAX_CHARACTERS = 280;
    private static final int CAMERA_REQUEST = 3;
    private static final int CAMERA_PERMISSION_REQUEST = 4;

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
        taggedPeople = new ArrayList<>();

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
            postButton.setText(R.string.update);
        } else {
            updatePostButtonState();
        }

        renderMediaPreview();

        // Kiểm tra xem có cần mở picker ngay không
        if (getArguments() != null && getArguments().getBoolean("open_picker", false)) {
            // Xóa flag để tránh mở lại khi xoay màn hình hoặc quay lại fragment
            getArguments().remove("open_picker");
            view.post(this::pickImage);
        }

        return view;
    }

    private void loadPostDataForEdit(String postId) {
        newPostViewModel.loadPostForEdit(postId);
        
        newPostViewModel.getEditingPost().observe(getViewLifecycleOwner(), post -> {
            if (post != null && mEditPostId != null) {
                postInput.setText(post.getCaption());
                selectedLocation = post.getLocation();
                if (selectedLocation != null && !selectedLocation.isEmpty()) {
                    TextView locationText = addLocationRow.findViewById(R.id.action_text);
                    if (locationText != null) locationText.setText(selectedLocation);
                }

                // Load tagged users
                if (post.getTaggedUsers() != null) {
                    taggedPeople = new ArrayList<>(post.getTaggedUsers());
                    TextView tagText = tagPeopleRow.findViewById(R.id.action_text);
                    if (tagText != null && !taggedPeople.isEmpty()) {
                        tagText.setText("Đã gắn thẻ " + taggedPeople.size() + " người");
                    }
                }
                
                // Map Firestore visibility back to Spinner
                String visibility = post.getVisibility();
                if (visibility != null && privacySpinner != null) {
                    int selection = 0;
                    switch (visibility.toUpperCase()) {
                        case "PUBLIC":
                        case "EVERYONE":
                            selection = 0;
                            break;
                        case "FRIENDS":
                            selection = 1;
                            break;
                        case "FRIENDS_ONLY":
                            selection = 2;
                            break;
                        case "PRIVATE":
                            selection = 3;
                            break;
                    }
                    if (selection < privacySpinner.getCount()) {
                        privacySpinner.setSelection(selection);
                    }
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
        
        newPostViewModel.getExistingMediaUrls().observe(getViewLifecycleOwner(), urls -> {
             renderMediaPreview();
        });
    }

    private void initializeViews(View view) {
        postInput = view.findViewById(R.id.post_input);
        cameraButton = view.findViewById(R.id.camera_button);
        uploadImageButton = view.findViewById(R.id.upload_image_button);
        uploadVideoButton = view.findViewById(R.id.upload_video_button);
        privacySpinner = view.findViewById(R.id.privacy_spinner);
        userAvatar = view.findViewById(R.id.user_avatar);
        postButton = view.findViewById(R.id.post_button);
        cancelButton = view.findViewById(R.id.cancel_button);

        photoPreviewContainer = view.findViewById(R.id.photo_preview_container);
        videoPreviewContainer = view.findViewById(R.id.video_preview_container);
        photoSectionTitle = view.findViewById(R.id.photo_section_title);
        videoSectionTitle = view.findViewById(R.id.video_section_title);
        photoScrollView = view.findViewById(R.id.photo_scroll_view);
        videoScrollView = view.findViewById(R.id.video_scroll_view);

        addLocationRow = view.findViewById(R.id.add_location_row);
        tagPeopleRow = view.findViewById(R.id.tag_people_row);

        if (userAvatar != null) {
            UserAvatarLoader.load(userAvatar, null);
        }

        if (postInput != null) {
            postInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARACTERS)});
        }

        // Setup privacy spinner an toàn hơn
        try {
            ArrayAdapter<CharSequence> privacyAdapter = ArrayAdapter.createFromResource(
                    requireContext(),
                    R.array.privacy_options,
                    android.R.layout.simple_spinner_item
            );
            privacyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            if (privacySpinner != null) {
                privacySpinner.setAdapter(privacyAdapter);
            }
        } catch (Exception e) {
            android.util.Log.e("NewPostFragment", "Spinner error", e);
        }

        if (postButton != null) {
            postButton.setEnabled(false);
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

        // Hành động: Add Location, Tag People
        addLocationRow.setOnClickListener(v -> openLocationPicker());
        tagPeopleRow.setOnClickListener(v -> openPeopleTagger());

        privacySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                privacyLevel = parent.getItemAtPosition(position).toString();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        postButton.setOnClickListener(v -> createPost());
        cancelButton.setOnClickListener(v -> handleCancel());
    }

    private void setupObservers() {
        newPostViewModel.getIsPosting().observe(getViewLifecycleOwner(), isPosting -> {
            postButton.setEnabled(!isPosting);
            String buttonText;
            if (isPosting) {
                buttonText = "Đang lưu...";
            } else {
                buttonText = (mEditPostId != null) ? "Cập nhật" : getString(R.string.post);
            }
            postButton.setText(buttonText);
            cancelButton.setEnabled(!isPosting);
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), mEditPostId != null ? "Cập nhật thành công!" : getString(R.string.post_created), Toast.LENGTH_SHORT).show();
                returnToHome();
            }
        });

        newPostViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        newPostViewModel.getEditingPost().observe(getViewLifecycleOwner(), post -> {
            if (post != null) {
                postInput.setText(post.getCaption());
                selectedLocation = post.getLocation();
                if (selectedLocation != null && !selectedLocation.isEmpty()) {
                    TextView locationText = addLocationRow.findViewById(R.id.action_text);
                    if (locationText != null) locationText.setText(selectedLocation);
                }
                
                // Set privacy
                if (post.getVisibility() != null) {
                    privacyLevel = post.getVisibility();
                    // Update spinner selection if needed
                }
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
            // Đảm bảo thư mục "Pictures" tồn tại và khớp với file_paths.xml
            java.io.File storageDir = new java.io.File(requireContext().getExternalFilesDir(null), "Pictures");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            photoFile = java.io.File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (java.io.IOException ex) {
            Toast.makeText(getContext(), "Error creating file: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("NewPostFragment", "Camera file error", ex);
        }

        if (photoFile != null) {
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile);
            takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri);
            
            // Cấp quyền cho các app có thể xử lý Intent này
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
                Toast.makeText(getContext(), "Bạn cần cấp quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openLocationPicker() {
        List<String> options = new ArrayList<>();
        options.add("Sử dụng vị trí hiện tại");
        options.add("Nhập địa điểm thủ công");
        if (selectedLocation != null) {
            options.add("Xóa vị trí");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.add_location))
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        selectedLocation = "Hà Nội, Việt Nam"; // Giả lập vị trí hiện tại
                        ((TextView) addLocationRow.findViewById(R.id.action_text)).setText(selectedLocation);
                    } else if (which == 1) {
                        showManualLocationInput();
                    } else if (which == 2) {
                        selectedLocation = null;
                        ((TextView) addLocationRow.findViewById(R.id.action_text)).setText(R.string.add_location);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showManualLocationInput() {
        final EditText input = new EditText(getContext());
        input.setHint("Nhập địa điểm...");
        if (selectedLocation != null && !selectedLocation.equals("Hà Nội, Việt Nam")) {
            input.setText(selectedLocation);
        }
        
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(40, 20, 40, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        new AlertDialog.Builder(getContext())
                .setTitle("Nhập địa điểm")
                .setView(container)
                .setPositiveButton("Lưu", (d, w) -> {
                    String loc = input.getText().toString().trim();
                    if (!loc.isEmpty()) {
                        selectedLocation = loc;
                        ((TextView) addLocationRow.findViewById(R.id.action_text)).setText(selectedLocation);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void openPeopleTagger() {
        TagPeopleFragment tagFragment = TagPeopleFragment.newInstance(new ArrayList<>(taggedPeople));
        tagFragment.setOnPeopleTaggedListener(userIds -> {
            taggedPeople = userIds;
            TextView tagText = tagPeopleRow.findViewById(R.id.action_text);
            if (userIds.isEmpty()) {
                tagText.setText(R.string.tag_people);
            } else {
                tagText.setText("Đã gắn thẻ " + userIds.size() + " người");
            }
        });
        
        getParentFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, tagFragment)
                .addToBackStack(null)
                .commit();
    }

    private void createPost() {
        String content = postInput.getText().toString().trim();
        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        if (existingUrls == null) existingUrls = new ArrayList<>();

        if (content.isEmpty() && selectedMedias.isEmpty() && existingUrls.isEmpty()) {
            Toast.makeText(getContext(), "Post content cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (mEditPostId != null) {
            newPostViewModel.updatePost(mEditPostId, content, selectedMedias, existingUrls, privacyLevel, selectedLocation, taggedPeople);
        } else {
            newPostViewModel.createPost(content, selectedMedias, selectedLocation, taggedPeople, privacyLevel);
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

    // region Media Preview
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                handleImagePicked(data);
            } else if (requestCode == PICK_VIDEO_REQUEST && data != null) {
                handleVideoPicked(data);
            } else if (requestCode == CAMERA_REQUEST) {
                // Kiểm tra cameraImageUri có null không (do Fragment bị recreation)
                if (cameraImageUri != null) {
                    if (!selectedMedias.contains(cameraImageUri)) {
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
                selectedMedias.add(uri);
            }
        } else if (data.getData() != null) {
            selectedMedias.add(data.getData());
        }
    }

    private void handleVideoPicked(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                selectedMedias.add(uri);
            }
        } else if (data.getData() != null) {
            selectedMedias.add(data.getData());
        }
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

    /**
     * Render media preview separated by type
     */
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

        // Update Visibility of sections
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
    // endregion
}