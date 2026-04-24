package com.example.social_app.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.viewmodels.NewPostViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public class NewPostFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_VIDEO_REQUEST = 2;

    private EditText postInput;
    private ImageButton attachMediaButton;
    private Button postButton;
    private ImageButton cancelButton;
    private ImageView userAvatar;
    private View postingOverlay;

    private LinearLayout mediaPreviewContainer;
    private View mediaScrollView;

    private NewPostViewModel newPostViewModel;
    private List<Uri> selectedMedias = new ArrayList<>();
    private Uri cameraImageUri;
    private String mEditPostId = null;

    private static final int MAX_CHARACTERS = 280;
    private static final int CAMERA_REQUEST = 3;
    private static final int CAMERA_PERMISSION_REQUEST = 4;
    private static final int MEDIA_PERMISSION_REQUEST = 6;
    private final List<PickerMediaItem> pickerMediaItems = new ArrayList<>();
    private MediaPickerAdapter mediaPickerAdapter;

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
            // Xóa flag để tránh mở lại khi xoay màn hình hoặc quay lại fragment
            getArguments().remove("open_picker");
            view.post(this::showMediaSourcePickerFullscreen);
        }

        return view;
    }

    private void loadPostDataForEdit(String postId) {
        newPostViewModel.loadPostForEdit(postId);
        
        newPostViewModel.getEditingPost().observe(getViewLifecycleOwner(), post -> {
            if (post != null && mEditPostId != null) {
                // Chỉ set text nếu input đang trống để tránh ghi đè khi quay lại từ TagPeople
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
        
        newPostViewModel.getExistingMediaUrls().observe(getViewLifecycleOwner(), urls -> {
             renderMediaPreview();
        });
    }

    private void initializeViews(View view) {
        postInput = view.findViewById(R.id.post_input);
        attachMediaButton = view.findViewById(R.id.attach_media_button);
        userAvatar = view.findViewById(R.id.user_avatar);
        postButton = view.findViewById(R.id.post_button);
        cancelButton = view.findViewById(R.id.cancel_button);
        postingOverlay = view.findViewById(R.id.posting_overlay);

        mediaPreviewContainer = view.findViewById(R.id.media_preview_container);
        mediaScrollView = view.findViewById(R.id.media_scroll_view);
        if (userAvatar != null) {
            UserAvatarLoader.load(userAvatar, null);
        }

        if (postInput != null) {
            postInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARACTERS)});
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

        attachMediaButton.setOnClickListener(v -> showMediaSourcePickerFullscreen());

        postButton.setOnClickListener(v -> createPost());
        cancelButton.setOnClickListener(v -> handleCancel());
    }

    private void setupObservers() {
        newPostViewModel.getIsPosting().observe(getViewLifecycleOwner(), isPosting -> {
            postButton.setEnabled(!isPosting);
            if (postingOverlay != null) {
                postingOverlay.setVisibility(isPosting ? View.VISIBLE : View.GONE);
            }
            attachMediaButton.setEnabled(!isPosting);
            postInput.setEnabled(!isPosting);
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

    private void showMediaSourcePickerFullscreen() {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_media_source_picker);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(0);
            dialog.getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
            dialog.getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
        }

        View closeBtn = dialog.findViewById(R.id.media_picker_close);
        RecyclerView mediaGrid = dialog.findViewById(R.id.media_picker_grid);
        Button doneBtn = dialog.findViewById(R.id.media_picker_done);
        LinkedHashSet<Uri> stagedSelected = new LinkedHashSet<>(selectedMedias);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        updateDoneButtonLabel(doneBtn, stagedSelected.size());

        mediaPickerAdapter = new MediaPickerAdapter(stagedSelected, (item, position) -> {
            if (item.isCameraTile) {
                dialog.dismiss();
                openCamera();
                return;
            }
            if (item.uri != null) {
                if (stagedSelected.contains(item.uri)) {
                    stagedSelected.remove(item.uri);
                } else {
                    stagedSelected.add(item.uri);
                }
                mediaPickerAdapter.notifyItemChanged(position);
                updateDoneButtonLabel(doneBtn, stagedSelected.size());
            }
        });
        doneBtn.setOnClickListener(v -> {
            selectedMedias.clear();
            selectedMedias.addAll(stagedSelected);
            renderMediaPreview();
            updatePostButtonState();
            dialog.dismiss();
        });
        mediaGrid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mediaGrid.setAdapter(mediaPickerAdapter);
        loadDeviceMediaItems();

        dialog.show();
    }

    private void updateDoneButtonLabel(@NonNull Button doneButton, int selectedCount) {
        if (selectedCount > 0) {
            doneButton.setText(getString(R.string.profile_edit_done) + " (" + selectedCount + ")");
        } else {
            doneButton.setText(R.string.profile_edit_done);
        }
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
        } else if (requestCode == MEDIA_PERMISSION_REQUEST) {
            loadDeviceMediaItems();
        }
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
            newPostViewModel.updatePost(mEditPostId, content, selectedMedias, existingUrls, "Everyone", null, new ArrayList<>());
        } else {
            newPostViewModel.createPost(content, selectedMedias, null, new ArrayList<>(), "Everyone");
        }
    }

    private void loadDeviceMediaItems() {
        pickerMediaItems.clear();
        pickerMediaItems.add(PickerMediaItem.cameraTile());

        List<String> missingMediaPermissions = getMissingMediaPermissions();
        if (!missingMediaPermissions.isEmpty()) {
            requestPermissions(missingMediaPermissions.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
        }

        if (!hasAnyMediaPermission()) {
            if (mediaPickerAdapter != null) mediaPickerAdapter.notifyDataSetChanged();
            return;
        }

        List<PickerMediaCandidate> candidates = new ArrayList<>();
        if (canReadImages()) {
            queryImages(candidates);
        }
        if (canReadVideos()) {
            queryVideos(candidates);
        }
        candidates.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));

        int maxItems = Math.min(200, candidates.size());
        for (int i = 0; i < maxItems; i++) {
            PickerMediaCandidate candidate = candidates.get(i);
            pickerMediaItems.add(PickerMediaItem.media(candidate.uri, candidate.isVideo, candidate.durationMs));
        }

        if (mediaPickerAdapter != null) mediaPickerAdapter.notifyDataSetChanged();
    }

    private boolean hasAnyMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            boolean hasSelected = androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED;
            return hasSelected || canReadImages() || canReadVideos();
        }
        return canReadImages() || canReadVideos();
    }

    private boolean canReadImages() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canReadVideos() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }, MEDIA_PERMISSION_REQUEST);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO
            }, MEDIA_PERMISSION_REQUEST);
            return;
        }
        requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, MEDIA_PERMISSION_REQUEST);
    }

    private List<String> getMissingMediaPermissions() {
        List<String> missing = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED) {
                missing.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED) {
                missing.add(android.Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) != PackageManager.PERMISSION_GRANTED) {
                missing.add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            }
            return missing;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED) {
                missing.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED) {
                missing.add(android.Manifest.permission.READ_MEDIA_VIDEO);
            }
            return missing;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {
            missing.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return missing;
    }

    private void queryImages(List<PickerMediaCandidate> out) {
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        };
        Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        );
        if (cursor == null) return;
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
        int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumn);
            long dateAdded = cursor.getLong(dateAddedColumn);
            Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            out.add(new PickerMediaCandidate(uri, false, dateAdded, 0L));
        }
        cursor.close();
    }

    private void queryVideos(List<PickerMediaCandidate> out) {
        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
        };
        Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        );
        if (cursor == null) return;
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
        int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
        int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumn);
            long dateAdded = cursor.getLong(dateAddedColumn);
            long durationMs = cursor.getLong(durationColumn);
            Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            out.add(new PickerMediaCandidate(uri, true, dateAdded, durationMs));
        }
        cursor.close();
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

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavigationHiddenForOverlay(true);
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavigationHiddenForOverlay(false);
        }
        super.onDestroyView();
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
     * Render media preview in selected order
     */
    private void renderMediaPreview() {
        mediaPreviewContainer.removeAllViews();
        
        LayoutInflater inflater = LayoutInflater.from(getContext());
        
        List<String> existingUrls = newPostViewModel.getExistingMediaUrls().getValue();
        if (existingUrls != null) {
            for (int i = 0; i < existingUrls.size(); i++) {
                String url = existingUrls.get(i);
                boolean isVideo = url.contains("video/upload") || url.toLowerCase().endsWith(".mp4");
                
                View mediaItem = inflater.inflate(R.layout.item_media_preview, mediaPreviewContainer, false);
                ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
                ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
                ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

                com.bumptech.glide.Glide.with(this).load(url).into(imgMedia);
                playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

                imgMedia.setOnClickListener(v -> showMediaFullscreen(url, isVideo));

                int idx = i;
                btnRemove.setOnClickListener(v -> newPostViewModel.removeExistingMedia(idx));
                mediaPreviewContainer.addView(mediaItem);
            }
        }

        for (int i = 0; i < selectedMedias.size(); i++) {
            Uri mediaUri = selectedMedias.get(i);
            String mimeType = getMimeType(mediaUri);
            boolean isVideo = (mimeType != null && mimeType.startsWith("video/")) || mediaUri.toString().toLowerCase().contains("video");

            View mediaItem = inflater.inflate(R.layout.item_media_preview, mediaPreviewContainer, false);
            ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
            ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
            ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

            Glide.with(this).load(mediaUri).into(imgMedia);
            playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

            imgMedia.setOnClickListener(v -> showMediaFullscreen(mediaUri.toString(), isVideo));

            int idx = i;
            btnRemove.setOnClickListener(v -> {
                selectedMedias.remove(idx);
                renderMediaPreview();
                updatePostButtonState();
            });

            mediaPreviewContainer.addView(mediaItem);
        }

        boolean hasMedia = mediaPreviewContainer.getChildCount() > 0;
        mediaScrollView.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
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

    private static final class PickerMediaItem {
        final Uri uri;
        final boolean isVideo;
        final boolean isCameraTile;
        final long durationMs;

        private PickerMediaItem(@Nullable Uri uri, boolean isVideo, boolean isCameraTile, long durationMs) {
            this.uri = uri;
            this.isVideo = isVideo;
            this.isCameraTile = isCameraTile;
            this.durationMs = durationMs;
        }

        static PickerMediaItem cameraTile() {
            return new PickerMediaItem(null, false, true, 0L);
        }

        static PickerMediaItem media(@NonNull Uri uri, boolean isVideo, long durationMs) {
            return new PickerMediaItem(uri, isVideo, false, durationMs);
        }
    }

    private static final class PickerMediaCandidate {
        final Uri uri;
        final boolean isVideo;
        final long dateAdded;
        final long durationMs;

        PickerMediaCandidate(@NonNull Uri uri, boolean isVideo, long dateAdded, long durationMs) {
            this.uri = uri;
            this.isVideo = isVideo;
            this.dateAdded = dateAdded;
            this.durationMs = durationMs;
        }
    }

    private interface OnPickerItemClickListener {
        void onItemClick(PickerMediaItem item, int position);
    }

    private final class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.MediaVH> {
        private final Set<Uri> stagedSelected;
        private final OnPickerItemClickListener listener;

        MediaPickerAdapter(Set<Uri> stagedSelected, OnPickerItemClickListener listener) {
            this.stagedSelected = stagedSelected;
            this.listener = listener;
        }

        @NonNull
        @Override
        public MediaVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_new_post_media_picker, parent, false);
            return new MediaVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MediaVH holder, int position) {
            PickerMediaItem item = pickerMediaItems.get(position);
            if (item.isCameraTile) {
                holder.thumb.setImageResource(R.drawable.ic_camera);
                holder.thumb.setScaleType(ImageView.ScaleType.CENTER);
                holder.thumb.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.avatar_background));
                holder.thumb.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black));
                holder.videoBadge.setVisibility(View.GONE);
                holder.videoDuration.setVisibility(View.GONE);
                holder.selectedOverlay.setVisibility(View.GONE);
                holder.selectedCheck.setVisibility(View.GONE);
            } else {
                holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.thumb.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.avatar_background));
                holder.thumb.clearColorFilter();
                Glide.with(NewPostFragment.this)
                        .load(item.uri)
                        .thumbnail(0.2f)
                        .centerCrop()
                        .into(holder.thumb);
                holder.videoBadge.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);
                if (item.isVideo) {
                    holder.videoDuration.setText(formatVideoDuration(item.durationMs));
                    holder.videoDuration.setVisibility(View.VISIBLE);
                } else {
                    holder.videoDuration.setVisibility(View.GONE);
                }
                boolean isSelected = item.uri != null && stagedSelected.contains(item.uri);
                holder.selectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                holder.selectedCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
            holder.itemView.setOnClickListener(v -> listener.onItemClick(item, holder.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return pickerMediaItems.size();
        }

        final class MediaVH extends RecyclerView.ViewHolder {
            private final ImageView thumb;
            private final ImageView videoBadge;
            private final TextView videoDuration;
            private final View selectedOverlay;
            private final ImageView selectedCheck;

            MediaVH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.media_picker_thumb);
                videoBadge = itemView.findViewById(R.id.media_picker_video_badge);
                videoDuration = itemView.findViewById(R.id.media_picker_video_duration);
                selectedOverlay = itemView.findViewById(R.id.media_picker_selected_overlay);
                selectedCheck = itemView.findViewById(R.id.media_picker_selected_check);
            }
        }
    }

    private String formatVideoDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
    // endregion
}