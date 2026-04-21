package com.example.social_app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.widget.VideoView;
import android.widget.FrameLayout;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
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

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for creating a new post or editing an existing one.
 */
public class NewPostFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_VIDEO_REQUEST = 2;

    private EditText postInput;
    private TextView newPostTitle;
    private ImageButton cameraButton;
    private Button uploadImageButton, uploadVideoButton, postButton;
    private ImageButton cancelButton;
    private ImageView userAvatar;
    private Spinner privacySpinner;

    private LinearLayout photoPreviewContainer, videoPreviewContainer;
    private View photoSectionTitle, videoSectionTitle, photoScrollView, videoScrollView;
    private LinearLayout addLocationRow, tagPeopleRow;

    private TextView locationText;
    private NewPostViewModel newPostViewModel;
    private List<String> taggedPeople;
    private String editingPostId;
    private String privacyLevel = "Public";
    private String selectedLocation = "";

    private static final int MAX_CHARACTERS = 280;

    public static NewPostFragment newInstance() {
        return new NewPostFragment();
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
        // Use requireActivity() to share ViewModel across configuration changes (like Camera intent)
        newPostViewModel = new ViewModelProvider(requireActivity()).get(NewPostViewModel.class);
        taggedPeople = new ArrayList<>();

        if (getArguments() != null) {
            editingPostId = getArguments().getString("edit_post_id");
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

        if (editingPostId != null) {
            newPostViewModel.loadPostForEdit(editingPostId);
            newPostTitle.setText(R.string.edit_post);
            postButton.setText(R.string.save);
        }

        updatePostButtonState();
        renderMediaPreview();
        return view;
    }

    private void initializeViews(View view) {
        postInput = view.findViewById(R.id.post_input);
        newPostTitle = view.findViewById(R.id.new_post_title);
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
        locationText = addLocationRow.findViewById(R.id.action_text);
        tagPeopleRow = view.findViewById(R.id.tag_people_row);

        // Load Real User Data from Firestore
        loadUserData();

        // Character limit
        postInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARACTERS)});

        // Setup privacy spinner
        ArrayAdapter<CharSequence> privacyAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.privacy_options,
                R.layout.item_audience
        );
        privacyAdapter.setDropDownViewResource(R.layout.item_dropdown_audience);
        privacySpinner.setAdapter(privacyAdapter);

        postButton.setEnabled(false);
    }

    private void loadUserData() {
        FirebaseUser currentUser = FirebaseManager.getInstance().getAuth().getCurrentUser();
        if (currentUser != null) {
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String avatarUrl = documentSnapshot.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).circleCrop().into(userAvatar);
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
            postButton.setText(isPosting ? getString(R.string.posting) : (editingPostId != null ? getString(R.string.save) : getString(R.string.post)));
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), editingPostId != null ? "Cập nhật thành công!" : getString(R.string.post_created), Toast.LENGTH_SHORT).show();
                newPostViewModel.clearData();
                requireActivity().getSupportFragmentManager().popBackStack();
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
                
                // Hiển thị Location cũ
                if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                    selectedLocation = post.getLocation();
                    locationText.setText(selectedLocation);
                    locationText.setTextColor(getResources().getColor(R.color.accent_purple));
                }
                
                // Hiển thị Audience (Visibility) cũ
                if (post.getVisibility() != null) {
                    String visibility = post.getVisibility();
                    ArrayAdapter adapter = (ArrayAdapter) privacySpinner.getAdapter();
                    if (adapter != null) {
                        for (int i = 0; i < adapter.getCount(); i++) {
                            String itemText = adapter.getItem(i).toString();
                            if (itemText.equalsIgnoreCase(visibility)) {
                                privacySpinner.setSelection(i);
                                break;
                            }
                        }
                    }
                }

                // Gán taggedPeople cũ
                if (post.getTaggedUsers() != null) {
                    taggedPeople = new ArrayList<>(post.getTaggedUsers());
                }
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

        newPostViewModel.getSelectedMedias().observe(getViewLifecycleOwner(), uris -> {
            renderMediaPreview();
            updatePostButtonState();
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
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, PICK_IMAGE_REQUEST);
        } else {
            Toast.makeText(getContext(), "Máy ảnh không khả dụng", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLocationPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.add_location))
                .setItems(new String[]{"Vị trí hiện tại", "Tìm kiếm địa điểm"}, (dialog, which) -> {
                    selectedLocation = (which == 0) ? "Vị trí hiện tại" : "Hà Nội, Việt Nam";
                    locationText.setText(selectedLocation);
                    // Sử dụng màu accent để làm nổi bật vị trí đã chọn
                    locationText.setTextColor(getResources().getColor(R.color.accent_purple));
                    Toast.makeText(getContext(), "Đã thêm: " + selectedLocation, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openPeopleTagger() {
        Toast.makeText(getContext(), "Tag people - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void createPost() {
        String content = postInput.getText().toString().trim();
        List<Uri> selectedMedias = newPostViewModel.getSelectedMedias().getValue();
        List<String> existingMediaUrls = newPostViewModel.getExistingMediaUrls().getValue();

        if (content.isEmpty() && (selectedMedias == null || selectedMedias.isEmpty()) && (existingMediaUrls == null || existingMediaUrls.isEmpty())) {
            Toast.makeText(getContext(), "Nội dung không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingPostId != null) {
            newPostViewModel.updatePost(editingPostId, content, selectedMedias != null ? selectedMedias : new ArrayList<>(), existingMediaUrls != null ? existingMediaUrls : new ArrayList<>(), privacyLevel, selectedLocation, taggedPeople);
        } else {
            newPostViewModel.createPost(content, selectedMedias != null ? selectedMedias : new ArrayList<>(), selectedLocation, taggedPeople, privacyLevel);
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(requireContext())
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
        newPostViewModel.clearData();
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private boolean hasUnsavedChanges() {
        List<Uri> selectedMedias = newPostViewModel.getSelectedMedias().getValue();
        return !postInput.getText().toString().isEmpty() || (selectedMedias != null && !selectedMedias.isEmpty());
    }

    private void updatePostButtonState() {
        List<Uri> selectedMedias = newPostViewModel.getSelectedMedias().getValue();
        List<String> existingMediaUrls = newPostViewModel.getExistingMediaUrls().getValue();
        
        boolean hasContent = !postInput.getText().toString().trim().isEmpty() 
                || (selectedMedias != null && !selectedMedias.isEmpty()) 
                || (existingMediaUrls != null && !existingMediaUrls.isEmpty());
        postButton.setEnabled(hasContent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                if (data != null && (data.getData() != null || data.getClipData() != null)) {
                    handleImagePicked(data);
                } else if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                    // Xử lý ảnh chụp từ camera
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                    Uri uri = saveImageToInternalStorage(imageBitmap);
                    if (uri != null) {
                        newPostViewModel.addSelectedMedia(uri);
                    }
                }
            } else if (requestCode == PICK_VIDEO_REQUEST && data != null) {
                handleVideoPicked(data);
            }
            renderMediaPreview();
            updatePostButtonState();
        }
    }

    private Uri saveImageToInternalStorage(Bitmap bitmap) {
        File file = new File(requireContext().getCacheDir(), "camera_image_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleImagePicked(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                newPostViewModel.addSelectedMedia(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            newPostViewModel.addSelectedMedia(data.getData());
        }
    }

    private void handleVideoPicked(Intent data) {
        if (data.getData() != null) {
            newPostViewModel.addSelectedMedia(data.getData());
        }
    }

    private void renderMediaPreview() {
        if (photoPreviewContainer == null || videoPreviewContainer == null) return;
        
        photoPreviewContainer.removeAllViews();
        videoPreviewContainer.removeAllViews();

        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        List<Uri> selectedUris = newPostViewModel.getSelectedMedias().getValue();

        int photoCount = 0;
        int videoCount = 0;

        if (existingUrls != null) {
            for (int i = 0; i < existingUrls.size(); i++) {
                String url = existingUrls.get(i);
                boolean isVideo = url.toLowerCase().contains("video") || url.endsWith(".mp4") || url.endsWith(".mov");
                if (isVideo) {
                    addMediaToLayout(url, true, i, videoPreviewContainer);
                    videoCount++;
                } else {
                    addMediaToLayout(url, true, i, photoPreviewContainer);
                    photoCount++;
                }
            }
        }

        if (selectedUris != null) {
            for (int i = 0; i < selectedUris.size(); i++) {
                Uri uri = selectedUris.get(i);
                String mimeType = requireContext().getContentResolver().getType(uri);
                boolean isVideo = (mimeType != null && mimeType.startsWith("video/")) 
                        || uri.toString().toLowerCase().contains(".mp4") 
                        || uri.toString().toLowerCase().contains(".mov");

                if (isVideo) {
                    addMediaToLayout(uri, false, i, videoPreviewContainer);
                    videoCount++;
                } else {
                    addMediaToLayout(uri, false, i, photoPreviewContainer);
                    photoCount++;
                }
            }
        }

        // Cập nhật hiển thị tiêu đề và scrollview
        photoSectionTitle.setVisibility(photoCount > 0 ? View.VISIBLE : View.GONE);
        photoScrollView.setVisibility(photoCount > 0 ? View.VISIBLE : View.GONE);
        videoSectionTitle.setVisibility(videoCount > 0 ? View.VISIBLE : View.GONE);
        videoScrollView.setVisibility(videoCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void addMediaToLayout(Object source, boolean isExisting, int index, LinearLayout container) {
        View mediaItem = LayoutInflater.from(getContext()).inflate(R.layout.item_media_preview, container, false);
        ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
        ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
        ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(250, 250);
        params.setMargins(0, 0, 16, 0);
        mediaItem.setLayoutParams(params);

        Glide.with(this).load(source).centerCrop().into(imgMedia);

        String path = source.toString().toLowerCase();
        boolean isVideo = path.contains("video") || path.contains(".mp4") || path.contains(".mov") || path.contains(".mkv") || path.contains(".3gp");
        
        // Better video detection for Uris
        if (source instanceof Uri) {
            String mimeType = requireContext().getContentResolver().getType((Uri) source);
            if (mimeType != null && mimeType.startsWith("video/")) {
                isVideo = true;
            }
        }
        
        playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        final boolean finalIsVideo = isVideo;
        imgMedia.setOnClickListener(v -> showMediaFullscreen(source, finalIsVideo));

        btnRemove.setOnClickListener(v -> {
            if (isExisting) {
                newPostViewModel.removeExistingMedia(index);
            } else {
                newPostViewModel.removeSelectedMedia(index);
            }
        });

        container.addView(mediaItem);
    }

    private void showMediaFullscreen(Object source, boolean isVideo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_video_preview, null);
        VideoView videoView = view.findViewById(R.id.video_view_preview);
        ImageView imageView = view.findViewById(R.id.image_view_preview);
        ImageButton btnClose = view.findViewById(R.id.btn_close_preview);

        if (isVideo) {
            videoView.setVisibility(View.VISIBLE);
            if (imageView != null) imageView.setVisibility(View.GONE);
            
            Uri videoUri = source instanceof Uri ? (Uri) source : Uri.parse((String) source);
            videoView.setVideoURI(videoUri);
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
            });
            videoView.setOnClickListener(v -> {
                if (videoView.isPlaying()) videoView.pause();
                else videoView.start();
            });
        } else {
            videoView.setVisibility(View.GONE);
            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(source)
                        .into(imageView);
            }
        }
        
        AlertDialog dialog = builder.setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
