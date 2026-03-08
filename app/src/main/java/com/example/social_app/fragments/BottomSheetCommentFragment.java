package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.social_app.R;
import com.example.social_app.adapters.CommentAdapter;
import com.example.social_app.models.Comment;
import com.example.social_app.viewmodels.CommentViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.List;

/**
 * BottomSheetCommentFragment displays comments for a specific post in a bottom sheet.
 * Features:
 * - Displays comments in a scrollable RecyclerView
 * - Supports infinite scrolling with pagination
 * - Allows composing and sending new comments
 * - Supports liking comments and viewing replies
 * - Implements swipe-to-refresh functionality
 * - Enables swipe-down gesture to dismiss the bottom sheet
 * - Material Design bottom sheet behavior
 */
public class BottomSheetCommentFragment extends BottomSheetDialogFragment implements CommentAdapter.OnCommentActionListener {

    private static final String POST_ID_KEY = "post_id";
    private static final int SCROLL_THRESHOLD = 5;
    private static final int CHARACTER_LIMIT = 280;

    private RecyclerView commentsRecyclerView;
    private EditText commentInput;
    private ImageButton sendButton, emojiButton, gifButton, attachMediaButton, removeAttachmentBtn;
    private ImageView composeAvatar, attachmentPreviewImage;
    private Spinner sortSpinner;
    private SwipeRefreshLayout swipeRefresh;
    private TextView charCountText, attachmentFileName, attachmentFileSize;
    private LinearLayout attachmentPreviewContainer, actionButtonsSection;
    private View inputFieldContainer, dragHandle;

    private CommentAdapter commentAdapter;
    private CommentViewModel commentViewModel;
    private String postId;
    private String replyingToUserId = null;
    private boolean isLoadingMore = false;
    private LinearLayoutManager layoutManager;
    private String attachedFilePath = null;
    private BottomSheetBehavior<?> bottomSheetBehavior;

    /**
     * Factory method to create a new BottomSheetCommentFragment instance.
     * @param postId The ID of the post whose comments to display
     * @return A new BottomSheetCommentFragment instance
     */
    public static BottomSheetCommentFragment newInstance(String postId) {
        BottomSheetCommentFragment fragment = new BottomSheetCommentFragment();
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment, container, false);
        initializeViews(view);
        setupAdapters();
        setupScrollListener(commentsRecyclerView);
        setupListeners();
        setupObservers();
        loadComments();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup BottomSheetBehavior for swipe-to-dismiss
        setupBottomSheetBehavior(view);
    }

    /**
     * Setup BottomSheetBehavior with drag and dismiss options.
     * Enables swipe-down gesture to dismiss the bottom sheet.
     */
    private void setupBottomSheetBehavior(View view) {
        // Get the root view of the bottom sheet dialog
        // The view hierarchy is: BottomSheetDialog -> CoordinatorLayout -> content
        ViewGroup parent = (ViewGroup) view.getParent();

        // Find the CoordinatorLayout that contains our content
        View bottomSheetContainer = null;
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                // The BottomSheetDialog's content is typically in a FrameLayout
                if (child.getId() == android.R.id.custom) {
                    bottomSheetContainer = child;
                    break;
                }
            }

            // If we found the container, set up the behavior
            if (bottomSheetContainer != null) {
                try {
                    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetContainer);

                    // Enable drag and dismiss
                    bottomSheetBehavior.setDraggable(true);
                    bottomSheetBehavior.setFitToContents(false);
                    bottomSheetBehavior.setHideable(true);

                    // Set initial state to expanded
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                    // Set save flags to preserve state
                    bottomSheetBehavior.setSaveFlags(BottomSheetBehavior.SAVE_ALL);

                    // Add behavior callback to handle dismissal
                    bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bottomSheet, int newState) {
                            // When bottom sheet is hidden, dismiss the dialog
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                dismiss();
                            }
                        }

                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                            // Provides visual feedback as user drags
                            // slideOffset ranges from 0 (collapsed) to 1 (expanded)
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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

        // Setup RecyclerView with LinearLayoutManager
        layoutManager = new LinearLayoutManager(getContext());
        commentsRecyclerView.setLayoutManager(layoutManager);

        // Set placeholder avatar
        composeAvatar.setImageResource(R.drawable.avatar_placeholder);

        // Initialize character count display
        updateCharacterCount(0);

        // Setup sort spinner
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
        android.util.Log.d("CommentSheet", "setupAdapters() called - creating CommentAdapter");
        commentAdapter = new CommentAdapter(getContext(), this);
        commentsRecyclerView.setAdapter(commentAdapter);
        android.util.Log.d("CommentSheet", "Adapter set to RecyclerView successfully");
    }

    /**
     * Setup scroll listener for infinite scrolling / pagination.
     */
    public void setupScrollListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

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
        sendButton.setOnClickListener(v -> sendComment());
        sendButton.setEnabled(false);

        emojiButton.setOnClickListener(v -> openEmojiPicker());
        gifButton.setOnClickListener(v -> openGifPicker());
        attachMediaButton.setOnClickListener(v -> openFileChooser());
        removeAttachmentBtn.setOnClickListener(v -> removeAttachment());

        // Event handling: When clicking "what's new?" input field, switch to new post layout
        commentInput.setOnClickListener(v -> {
            android.util.Log.d("CommentSheet", "commentInput clicked - switching to NewPostFragment");
            switchToNewPostFragment();
        });

        commentInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentLength = s.length();
                updateCharacterCount(currentLength);

                boolean hasContent = s.toString().trim().length() > 0;
                boolean withinLimit = currentLength <= CHARACTER_LIMIT;
                sendButton.setEnabled(hasContent && withinLimit);

                if (currentLength >= CHARACTER_LIMIT * 0.9) {
                    charCountText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                } else if (currentLength >= CHARACTER_LIMIT) {
                    charCountText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                } else {
                    charCountText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        commentInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(CHARACTER_LIMIT)
        });

        swipeRefresh.setOnRefreshListener(this::loadComments);

        commentInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && replyingToUserId != null) {
                commentInput.setText("@" + replyingToUserId + " ");
                commentInput.setSelection(commentInput.getText().length());
            }
        });
    }

    /**
     * Switch from comment sheet to new post fragment layout.
     * Closes the bottom sheet and opens the new post creation interface.
     */
    private void switchToNewPostFragment() {
        android.util.Log.d("CommentSheet", "switchToNewPostFragment() called");

        // Dismiss the current bottom sheet
        dismiss();

        // Open the new post fragment
        NewPostFragment newPostFragment = new NewPostFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, newPostFragment)
                .addToBackStack(null)
                .commit();

        android.util.Log.d("CommentSheet", "NewPostFragment opened");
    }

    /**
     * Setup observers for ViewModel live data.
     */
    private void setupObservers() {
        android.util.Log.d("CommentSheet", "setupObservers() called");

        commentViewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            android.util.Log.d("CommentSheet", "Comments observer triggered. Count: " + (comments != null ? comments.size() : "null"));
            if (comments != null && !comments.isEmpty()) {
                android.util.Log.d("CommentSheet", "Setting " + comments.size() + " comments to adapter");
                commentAdapter.setComments(comments);
                swipeRefresh.setRefreshing(false);
            } else {
                android.util.Log.d("CommentSheet", "No comments received or empty list");
            }
        });

        commentViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                android.util.Log.e("CommentSheet", "Error loading comments: " + error);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
            }
        });

        commentViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            android.util.Log.d("CommentSheet", "Loading state changed: " + isLoading);
            if (isLoading) {
                swipeRefresh.setRefreshing(true);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    /**
     * Load initial comments for the post.
     */
    private void loadComments() {
        android.util.Log.d("CommentSheet", "loadComments() called with postId: " + postId);
        if (postId != null && !postId.isEmpty()) {
            commentViewModel.loadComments(postId);
            android.util.Log.d("CommentSheet", "ViewModel.loadComments() triggered");
        } else {
            android.util.Log.e("CommentSheet", "ERROR: postId is null or empty!");
        }
    }

    /**
     * Load more comments (pagination for infinite scroll).
     */
    private void loadMoreComments() {
        android.util.Log.d("CommentSheet", "loadMoreComments() called");
        commentViewModel.loadMoreComments(postId);
    }

    /**
     * Send a new comment on the post.
     */
    private void sendComment() {
        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

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
     * Open file chooser to select attachment.
     */
    private void openFileChooser() {
        Toast.makeText(getContext(), "File picker - Coming soon", Toast.LENGTH_SHORT).show();
    }

    /**
     * Remove the currently attached file.
     */
    private void removeAttachment() {
        attachedFilePath = null;
        updateAttachmentUI(false);
        Toast.makeText(getContext(), "Attachment removed", Toast.LENGTH_SHORT).show();
    }

    /**
     * Update character count display.
     */
    private void updateCharacterCount(int currentLength) {
        if (charCountText != null) {
            charCountText.setText(String.format(java.util.Locale.getDefault(), "%d/%d", currentLength, CHARACTER_LIMIT));
        }
    }

    /**
     * Update attachment UI visibility.
     */
    private void updateAttachmentUI(boolean hasAttachment) {
        if (hasAttachment) {
            attachmentPreviewContainer.setVisibility(View.VISIBLE);
        } else {
            attachmentPreviewContainer.setVisibility(View.GONE);
            attachedFilePath = null;
        }
    }

    /**
     * Handle like button click on a comment.
     */
    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    /**
     * Handle reply button click on a comment.
     */
    @Override
    public void onReplyClicked(Comment comment) {
        replyingToUserId = comment.getUser().getName();
        commentInput.requestFocus();
    }

    /**
     * Handle "view more replies" click.
     */
    @Override
    public void onViewMoreRepliesClicked(Comment comment) {
        commentViewModel.loadMoreReplies(comment.getId());
    }
}









