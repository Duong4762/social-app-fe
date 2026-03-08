package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.social_app.R;
import com.example.social_app.adapters.CommentAdapter;
import com.example.social_app.models.Comment;
import com.example.social_app.viewmodels.CommentViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * CommentFragment displays comments for a specific post with scrolling support.
 * Features:
 * - Displays comments in a scrollable RecyclerView
 * - Supports infinite scrolling with pagination
 * - Allows composing and sending new comments
 * - Supports liking comments and viewing replies
 * - Implements swipe-to-refresh functionality
 */
public class CommentFragment extends Fragment implements CommentAdapter.OnCommentActionListener {

    private static final String POST_ID_KEY = "post_id";
    private static final int SCROLL_THRESHOLD = 5; // Load more when 5 items from bottom
    private static final int CHARACTER_LIMIT = 280; // Maximum characters allowed

    // Swipe detection constants
    private static final int SWIPE_MIN_DISTANCE = 100; // Minimum distance to trigger swipe
    private static final int SWIPE_MIN_VELOCITY = 100; // Minimum velocity for swipe

    private RecyclerView commentsRecyclerView;
    private EditText commentInput;
    private ImageButton sendButton, emojiButton, gifButton, attachMediaButton, removeAttachmentBtn;
    private ImageView composeAvatar, attachmentPreviewImage;
    private Spinner sortSpinner;
    private SwipeRefreshLayout swipeRefresh;
    private TextView charCountText, attachmentFileName, attachmentFileSize;
    private LinearLayout attachmentPreviewContainer, actionButtonsSection;
    private View inputFieldContainer;

    private CommentAdapter commentAdapter;
    private CommentViewModel commentViewModel;
    private String postId;
    private String replyingToUserId = null;
    private boolean isLoadingMore = false;
    private LinearLayoutManager layoutManager;
    private String attachedFilePath = null; // Track attached file
    private GestureDetectorCompat gestureDetector; // For swipe-to-dismiss

    /**
     * Factory method to create a new CommentFragment instance.
     * @param postId The ID of the post whose comments to display
     * @return A new CommentFragment instance
     */
    public static CommentFragment newInstance(String postId) {
        CommentFragment fragment = new CommentFragment();
        Bundle args = new Bundle();
        args.putString(POST_ID_KEY, postId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            postId = getArguments().getString(POST_ID_KEY);
        }
        commentViewModel = new ViewModelProvider(this).get(CommentViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment, container, false);
        initializeViews(view);
        setupAdapters();
        setupScrollListener(commentsRecyclerView);
        setupListeners();
        setupGestureDetector(view); // Setup swipe-to-dismiss gesture detection
        setupObservers();
        loadComments();
        return view;
    }

    /**
     * Initialize all UI views from the layout.
     */
    private void initializeViews(View view) {
        // Core compose views
        commentInput = view.findViewById(R.id.comment_input);
        sendButton = view.findViewById(R.id.send_button);
        emojiButton = view.findViewById(R.id.emoji_button);
        gifButton = view.findViewById(R.id.gif_button);
        composeAvatar = view.findViewById(R.id.compose_avatar);
        sortSpinner = view.findViewById(R.id.comment_sort_spinner);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_layout);

        // New compose UI elements
        charCountText = view.findViewById(R.id.char_count_text);
        inputFieldContainer = view.findViewById(R.id.input_field_container);
        attachMediaButton = view.findViewById(R.id.attach_media_button);
        attachmentPreviewContainer = view.findViewById(R.id.attachment_preview_container);
        attachmentPreviewImage = view.findViewById(R.id.attachment_preview_image);
        attachmentFileName = view.findViewById(R.id.attachment_file_name);
        attachmentFileSize = view.findViewById(R.id.attachment_file_size);
        removeAttachmentBtn = view.findViewById(R.id.remove_attachment_btn);
        actionButtonsSection = view.findViewById(R.id.action_buttons_section);

        // Comment feed views
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view);

        // Setup RecyclerView with LinearLayoutManager for vertical scrolling
        layoutManager = new LinearLayoutManager(getContext());
        commentsRecyclerView.setLayoutManager(layoutManager);

        // Set placeholder avatar
        composeAvatar.setImageResource(R.drawable.avatar_placeholder);

        // Initialize character count display
        updateCharacterCount(0);

        // Setup sort spinner with comment sorting options
        setupSortSpinner();
    }

    /**
     * Setup the sort spinner with available sorting options.
     */
    private void setupSortSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.comment_sort_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(adapter);
    }

    /**
     * Setup the RecyclerView adapter with the CommentAdapter.
     */
    private void setupAdapters() {
        commentAdapter = new CommentAdapter(getContext(), this);
        commentsRecyclerView.setAdapter(commentAdapter);
    }

    /**
     * Setup gesture detector for swipe-to-dismiss functionality.
     * Detects swipe-down gestures to close the comment fragment.
     *
     * @param view The root view to attach gesture detection to
     */
    private void setupGestureDetector(View view) {
        gestureDetector = new GestureDetectorCompat(getContext(), new SwipeGestureListener());

        // Attach touch listener to root view to intercept swipe gestures
        view.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    /**
     * Inner class to handle swipe gesture detection.
     * Specifically detects downward swipes to dismiss the fragment.
     */
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                // Calculate the swipe distance
                float diffY = e2.getRawY() - e1.getRawY();
                float diffX = e2.getRawX() - e1.getRawX();

                // Check if swipe is primarily downward (more vertical than horizontal)
                // and meets the minimum distance and velocity thresholds
                if (Math.abs(diffY) > Math.abs(diffX) &&
                    diffY > SWIPE_MIN_DISTANCE &&
                    Math.abs(velocityY) > SWIPE_MIN_VELOCITY) {

                    // Swipe down detected - dismiss the fragment
                    dismissFragment();
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Dismiss the comment fragment by popping from back stack.
     * Provides smooth transition back to the home feed.
     */
    private void dismissFragment() {
        if (getFragmentManager() != null) {
            getFragmentManager().popBackStack();
        }
    }
    public void setupScrollListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Check if we need to load more comments
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Load more when user scrolls to within SCROLL_THRESHOLD items from bottom
                if (!isLoadingMore && (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - SCROLL_THRESHOLD) && firstVisibleItemPosition >= 0) {
                    isLoadingMore = true;
                    loadMoreComments();
                }
            }
        });
    }

    /**
     * Setup all button and input listeners.
     */
    private void setupListeners() {
        // Send button click listener
        sendButton.setOnClickListener(v -> sendComment());
        sendButton.setEnabled(false); // Disabled until input has text

        // Emoji button click listener
        emojiButton.setOnClickListener(v -> openEmojiPicker());

        // GIF button click listener
        gifButton.setOnClickListener(v -> openGifPicker());

        // Attach media button click listener
        attachMediaButton.setOnClickListener(v -> openFileChooser());

        // Remove attachment button click listener
        removeAttachmentBtn.setOnClickListener(v -> removeAttachment());

        // Text watcher for character count and send button enable/disable
        commentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Update character count display
                int currentLength = s.length();
                updateCharacterCount(currentLength);

                // Enable/disable send button based on input and character limit
                boolean hasContent = s.toString().trim().length() > 0;
                boolean withinLimit = currentLength <= CHARACTER_LIMIT;
                sendButton.setEnabled(hasContent && withinLimit);

                // Visual feedback for character limit
                if (currentLength >= CHARACTER_LIMIT * 0.9) { // 90% threshold
                    charCountText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                } else if (currentLength >= CHARACTER_LIMIT) {
                    charCountText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                } else {
                    charCountText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Prevent input when character limit reached
        commentInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(CHARACTER_LIMIT)
        });

        // Swipe to refresh listener
        swipeRefresh.setOnRefreshListener(this::loadComments);

        // Comment input focus listener to auto-tag when replying
        commentInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && replyingToUserId != null) {
                // Auto-tag the user being replied to
                commentInput.setText("@" + replyingToUserId + " ");
                commentInput.setSelection(commentInput.getText().length());
            }
        });
    }

    /**
     * Setup LiveData observers for comment data and state changes.
     */
    private void setupObservers() {
        // Observe comments list changes
        commentViewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            commentAdapter.setComments(comments);
            swipeRefresh.setRefreshing(false);
        });

        // Observe error messages
        commentViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
            }
        });

        // Observe loading state
        commentViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (!isLoading) {
                swipeRefresh.setRefreshing(false);
                isLoadingMore = false;
            }
        });
    }

    /**
     * Load initial comments for the post.
     */
    private void loadComments() {
        swipeRefresh.setRefreshing(true);
        commentViewModel.loadComments(postId);
    }

    /**
     * Load more comments (pagination for infinite scroll).
     */
    private void loadMoreComments() {
        commentViewModel.loadMoreComments(postId);
    }

    /**
     * Send a new comment on the post.
     * Validates input, clears the field, and resets reply state.
     */
    private void sendComment() {
        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove the @username tag if present for sending
        String commentText = text.replace("@" + replyingToUserId + " ", "").trim();
        if (commentText.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        commentViewModel.sendComment(postId, commentText);
        commentInput.setText("");
        replyingToUserId = null;
    }

    /**
     * Open emoji picker dialog (placeholder for future implementation).
     */
    private void openEmojiPicker() {
        Toast.makeText(getContext(), "Emoji picker - Coming soon", Toast.LENGTH_SHORT).show();
    }

    /**
     * Open GIF picker dialog (placeholder for future implementation).
     */
    private void openGifPicker() {
        Toast.makeText(getContext(), "GIF picker - Coming soon", Toast.LENGTH_SHORT).show();
    }

    /**
     * Update character count display and provide visual feedback.
     * Shows current character count vs. limit (0/280).
     *
     * @param currentLength Current length of comment text
     */
    private void updateCharacterCount(int currentLength) {
        if (charCountText != null) {
            charCountText.setText(String.format(java.util.Locale.getDefault(), "%d/%d", currentLength, CHARACTER_LIMIT));
        }
    }

    /**
     * Open file chooser to select attachment (image, video, document).
     * Placeholder for actual file picker implementation.
     */
    private void openFileChooser() {
        // Placeholder: In production, this would open a file picker
        // For now, show a toast about file selection
        Toast.makeText(getContext(), "File picker - Coming soon", Toast.LENGTH_SHORT).show();

        // TODO: Implement actual file picker
        // Intent intent = new Intent(Intent.ACTION_PICK);
        // intent.setType("image/*|video/*");
        // startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    /**
     * Remove the currently attached file and hide preview.
     */
    private void removeAttachment() {
        attachedFilePath = null;
        updateAttachmentUI(false);
        Toast.makeText(getContext(), "Attachment removed", Toast.LENGTH_SHORT).show();
    }

    /**
     * Display preview of attached file (image, video, document).
     * Shows file name, size, and thumbnail if applicable.
     *
     * @param filePath Path to the attached file
     * @param fileName Name of the file
     * @param fileSize Size of the file in bytes
     */
    private void displayAttachmentPreview(String filePath, String fileName, long fileSize) {
        attachedFilePath = filePath;

        // Update file info display
        attachmentFileName.setText(fileName);
        attachmentFileSize.setText(formatFileSize(fileSize));

        // Show preview container
        updateAttachmentUI(true);

        Toast.makeText(getContext(), "File attached: " + fileName, Toast.LENGTH_SHORT).show();
    }

    /**
     * Update attachment UI visibility and state.
     * Shows or hides attachment preview section based on attachment status.
     *
     * @param hasAttachment True if file is attached, false otherwise
     */
    private void updateAttachmentUI(boolean hasAttachment) {
        if (hasAttachment) {
            attachmentPreviewContainer.setVisibility(View.VISIBLE);
            // Adjust layout to show attachment section
            if (actionButtonsSection != null) {
                actionButtonsSection.setVisibility(View.VISIBLE);
            }
        } else {
            attachmentPreviewContainer.setVisibility(View.GONE);
            attachedFilePath = null;
        }
    }

    /**
     * Format file size from bytes to human-readable format.
     * Examples: "2.5 MB", "1.2 KB", "512 B"
     *
     * @param bytes File size in bytes
     * @return Formatted file size string
     */
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";

        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));

        return String.format(java.util.Locale.getDefault(), "%.1f %s",
                bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // ==================== CommentAdapter.OnCommentActionListener Callbacks ====================

    /**
     * Handle like button click on a comment.
     * Toggles the like status and updates the like count.
     */
    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    /**
     * Handle reply button click on a comment.
     * Sets up auto-tagging and focuses the input field.
     */
    @Override
    public void onReplyClicked(Comment comment) {
        replyingToUserId = comment.getUser().getName();
        commentInput.requestFocus();
    }

    /**
     * Handle "view more replies" click to expand nested replies.
     * Loads additional replies for the parent comment.
     */
    @Override
    public void onViewMoreRepliesClicked(Comment comment) {
        commentViewModel.loadMoreReplies(comment.getId());
    }
}

