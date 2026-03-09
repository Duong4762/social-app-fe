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
import com.example.social_app.viewmodels.NewPostViewModel;

import java.util.ArrayList;
import java.util.List;

public class NewPostFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_VIDEO_REQUEST = 2;

    private EditText postInput;
    private ImageButton cameraButton;
    private Button uploadImageButton, uploadVideoButton, postButton;
    private TextView cancelButton;
    private ImageView userAvatar;
    private Spinner privacySpinner;

    private LinearLayout mediaPreviewContainer;
    private LinearLayout addLocationRow, tagPeopleRow;

    private NewPostViewModel newPostViewModel;
    private List<Uri> selectedMedias;
    private String selectedLocation = null;
    private List<String> taggedPeople;
    private String privacyLevel = "Everyone";

    private static final int MAX_CHARACTERS = 280;

    public static NewPostFragment newInstance() {
        return new NewPostFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        selectedMedias = new ArrayList<>();
        taggedPeople = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_post, container, false);
        initializeViews(view);
        setupListeners();
        setupObservers();
        updatePostButtonState();
        renderMediaPreview();
        return view;
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

        mediaPreviewContainer = view.findViewById(R.id.media_preview_container);

        // Các row hành động "Add Location", "Tag People" phải là LinearLayout có id riêng
        addLocationRow = view.findViewById(R.id.add_location_row);
        tagPeopleRow = view.findViewById(R.id.tag_people_row);

        // Set placeholder avatar
        userAvatar.setImageResource(R.drawable.avatar_placeholder);

        // Set giới hạn ký tự
        postInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARACTERS)});

        // Setup privacy spinner
        ArrayAdapter<CharSequence> privacyAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.privacy_options,
                R.layout.item_audience
        );
        privacyAdapter.setDropDownViewResource(R.layout.item_dropdown_audience);
        privacySpinner.setAdapter(privacyAdapter);

        // Set initial post button state
        postButton.setEnabled(false);
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
            postButton.setText(isPosting ? getString(R.string.posting) : getString(R.string.post));
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), getString(R.string.post_created), Toast.LENGTH_SHORT).show();
                if (getFragmentManager() != null) {
                    getFragmentManager().popBackStack();
                }
            }
        });

        newPostViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
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
        Toast.makeText(getContext(), "Camera - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void openLocationPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.add_location))
                .setItems(new String[]{getString(R.string.current_location), getString(R.string.search_location)}, (dialog, which) -> {
                    if (which == 0) {
                        selectedLocation = getString(R.string.current_location);
                    } else {
                        selectedLocation = getString(R.string.custom_location);
                    }
                })
                .show();
    }

    private void openPeopleTagger() {
        Toast.makeText(getContext(), "Tag people - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void createPost() {
        String content = postInput.getText().toString().trim();
        if (content.isEmpty() && selectedMedias.isEmpty()) {
            Toast.makeText(getContext(), "Post content cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        newPostViewModel.createPost(content, selectedMedias, selectedLocation, taggedPeople, privacyLevel);
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
        postButton.setEnabled(hasContent);
    }

    // region Media Preview
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                handleImagePicked(data);
            } else if (requestCode == PICK_VIDEO_REQUEST) {
                handleVideoPicked(data);
            }
            renderMediaPreview();
            updatePostButtonState();
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
        if (data.getData() != null) {
            selectedMedias.add(data.getData());
        }
    }

    /**
     * Render media preview in horizontal LinearLayout
     */
    private void renderMediaPreview() {
        mediaPreviewContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < selectedMedias.size(); i++) {
            Uri mediaUri = selectedMedias.get(i);
            View mediaItem = inflater.inflate(R.layout.item_media_preview, mediaPreviewContainer, false);

            ImageView imgMedia = mediaItem.findViewById(R.id.media_preview_image);
            ImageButton btnRemove = mediaItem.findViewById(R.id.media_remove_button);
            ImageView playIcon = mediaItem.findViewById(R.id.ic_play);

            // TODO: Replace with Glide/Picasso if needed
            imgMedia.setImageURI(mediaUri);

            // Hiện icon play nếu là video
            String uriString = mediaUri.toString();
            if (uriString.contains("video") || uriString.endsWith(".mp4") || uriString.endsWith(".avi") || uriString.endsWith(".mov")) {
                playIcon.setVisibility(View.VISIBLE);
            } else {
                playIcon.setVisibility(View.GONE);
            }

            int idx = i;
            btnRemove.setOnClickListener(v -> {
                selectedMedias.remove(idx);
                renderMediaPreview();
                updatePostButtonState();
            });

            mediaPreviewContainer.addView(mediaItem);
        }
    }
    // endregion
}