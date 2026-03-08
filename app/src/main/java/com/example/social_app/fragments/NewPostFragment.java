package com.example.social_app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
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

/**
 * Fragment for creating and composing new posts.
 * Features:
 * - Text input with character limit
 * - Media upload (image/video) with preview
 * - Location tagging
 * - User tagging
 * - Privacy/audience settings
 * - Post creation with loading state
 * - Unsaved changes warning on exit
 */
public class NewPostFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_VIDEO_REQUEST = 2;

    private EditText postInput;
    private ImageButton cameraButton;
    private Button uploadImageButton, uploadVideoButton;
    private Button addLocationButton, tagPeopleButton;
    private Spinner privacySpinner;
    private GridView mediaPreviewGrid;
    private Button postButton, cancelButton;
    private ImageView userAvatar;
    private TextView characterCount;

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
        return view;
    }

    private void initializeViews(View view) {
        postInput = view.findViewById(R.id.post_input);
        cameraButton = view.findViewById(R.id.camera_button);
        uploadImageButton = view.findViewById(R.id.upload_image_button);
        uploadVideoButton = view.findViewById(R.id.upload_video_button);
        addLocationButton = view.findViewById(R.id.add_location_button);
        tagPeopleButton = view.findViewById(R.id.tag_people_button);
        privacySpinner = view.findViewById(R.id.privacy_spinner);
        mediaPreviewGrid = view.findViewById(R.id.media_preview_grid);
        postButton = view.findViewById(R.id.post_button);
        cancelButton = view.findViewById(R.id.cancel_button);
        userAvatar = view.findViewById(R.id.user_avatar);
        characterCount = view.findViewById(R.id.character_count);

        // Set initial button states
        postButton.setEnabled(false);

        // Setup privacy spinner
        ArrayAdapter<CharSequence> privacyAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.privacy_options,
                android.R.layout.simple_spinner_item
        );
        privacyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        privacySpinner.setAdapter(privacyAdapter);

        // Set placeholder avatar
        userAvatar.setImageResource(R.drawable.avatar_placeholder);
    }

    private void setupListeners() {
        // Text input listener for character count and enable/disable post button
        postInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int remaining = MAX_CHARACTERS - s.length();
                characterCount.setText(remaining + "/" + MAX_CHARACTERS);
                characterCount.setTextColor(remaining < 20 ? 0xFFE91E63 : 0xFF999999);
                updatePostButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Upload image button
        uploadImageButton.setOnClickListener(v -> pickImage());

        // Upload video button
        uploadVideoButton.setOnClickListener(v -> pickVideo());

        // Camera button
        cameraButton.setOnClickListener(v -> openCamera());

        // Add location button
        addLocationButton.setOnClickListener(v -> openLocationPicker());

        // Tag people button
        tagPeopleButton.setOnClickListener(v -> openPeopleTagger());

        // Privacy spinner
        privacySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                privacyLevel = parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Post button
        postButton.setOnClickListener(v -> createPost());

        // Cancel button
        cancelButton.setOnClickListener(v -> handleCancel());
    }

    private void setupObservers() {
        // Observe post creation state
        newPostViewModel.getIsPosting().observe(getViewLifecycleOwner(), isPosting -> {
            postButton.setEnabled(!isPosting);
            if (isPosting) {
                postButton.setText("Posting...");
            } else {
                postButton.setText("Post");
            }
        });

        // Observe post creation success
        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), "Post created successfully!", Toast.LENGTH_SHORT).show();
                if (getFragmentManager() != null) {
                    getFragmentManager().popBackStack();
                }
            }
        });

        // Observe errors
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
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGE_REQUEST);
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(Intent.createChooser(intent, "Select Video"), PICK_VIDEO_REQUEST);
    }

    private void openCamera() {
        // Placeholder for camera functionality
        Toast.makeText(getContext(), "Camera - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void openLocationPicker() {
        // Simple location picker dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Add Location")
                .setItems(new String[]{"Current Location", "Search Location"}, (dialog, which) -> {
                    if (which == 0) {
                        selectedLocation = "Current Location";
                        addLocationButton.setText("Location: Current");
                    } else {
                        // Would open search UI
                        selectedLocation = "Custom Location";
                        addLocationButton.setText("Location: Added");
                    }
                })
                .show();
    }

    private void openPeopleTagger() {
        // Placeholder for people tagger
        Toast.makeText(getContext(), "Tag people - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void createPost() {
        String content = postInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(getContext(), "Post content cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        newPostViewModel.createPost(
                content,
                selectedMedias,
                selectedLocation,
                taggedPeople,
                privacyLevel
        );
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Discard Post?")
                    .setMessage("Are you sure you want to discard this post?")
                    .setPositiveButton("Discard", (dialog, which) -> {
                        android.util.Log.d("NewPostFragment", "Discarding post - returning to HomeFragment");
                        returnToHome();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            android.util.Log.d("NewPostFragment", "No unsaved changes - returning to HomeFragment");
            returnToHome();
        }
    }

    /**
     * Returns to the previous fragment using popBackStack.
     * This preserves the state of the previous fragment without reloading.
     */
    private void returnToHome() {
        android.util.Log.d("NewPostFragment", "returnToHome() called - using popBackStack");

        if (getFragmentManager() != null) {
            // Simply pop the back stack to return to the previous fragment
            // This preserves the previous fragment's state without reloading
            getFragmentManager().popBackStack();
            android.util.Log.d("NewPostFragment", "Successfully popped back to previous fragment");
        }
    }

    private void setupScrollListener(androidx.recyclerview.widget.RecyclerView recyclerView) {
        // This is a callback from HomeFragment for scroll listener setup
        // Implementation handled by HomeFragment
    }

    private boolean hasUnsavedChanges() {
        return !postInput.getText().toString().isEmpty() || !selectedMedias.isEmpty();
    }

    private void updatePostButtonState() {
        boolean hasContent = !postInput.getText().toString().trim().isEmpty();
        postButton.setEnabled(hasContent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                handleImagePicked(data);
            } else if (requestCode == PICK_VIDEO_REQUEST) {
                handleVideoPicked(data);
            }
        }
    }

    private void handleImagePicked(Intent data) {
        if (data.getClipData() != null) {
            // Multiple images
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                selectedMedias.add(uri);
            }
        } else if (data.getData() != null) {
            // Single image
            selectedMedias.add(data.getData());
        }
        updatePostButtonState();
    }

    private void handleVideoPicked(Intent data) {
        if (data.getData() != null) {
            selectedMedias.add(data.getData());
        }
        updatePostButtonState();
    }
}
