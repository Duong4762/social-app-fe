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

public class BottomSheetCommentFragment extends BottomSheetDialogFragment implements CommentAdapter.OnCommentActionListener {

    private static final String POST_ID_KEY = "post_id";
    private static final int SCROLL_THRESHOLD = 5;
    private static final int CHARACTER_LIMIT = 280;

    private RecyclerView commentsRecyclerView;
    private EditText commentInput;
    private ImageButton sendButton, emojiButton, gifButton, attachMediaButton;
    private ImageView composeAvatar;
    private Spinner sortSpinner;
    private SwipeRefreshLayout swipeRefresh;
    private TextView charCountText;
    private LinearLayout actionButtonsSection;

    private CommentAdapter commentAdapter;
    private CommentViewModel commentViewModel;
    private String postId;
    private String replyingToUserId = null;
    private boolean isLoadingMore = false;
    private LinearLayoutManager layoutManager;
    private BottomSheetBehavior<?> bottomSheetBehavior;

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

    private void initializeViews(View view) {
        // Comment list
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view);
        sortSpinner = view.findViewById(R.id.comment_sort_spinner);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_layout);

        // Compose
        composeAvatar = view.findViewById(R.id.compose_avatar);
        commentInput = view.findViewById(R.id.compose_comment_input);
        charCountText = view.findViewById(R.id.compose_char_count_text);

        // Action buttons
        actionButtonsSection = view.findViewById(R.id.compose_action_buttons_section);
        attachMediaButton = view.findViewById(R.id.compose_attach_media_button);
        gifButton = view.findViewById(R.id.compose_gif_button);
        emojiButton = view.findViewById(R.id.compose_emoji_button);
        sendButton = view.findViewById(R.id.compose_send_button);

        if (composeAvatar != null) {
            composeAvatar.setImageResource(R.drawable.avatar_placeholder);
        }
        if (charCountText != null) {
            updateCharacterCount(0);
        }
        if (sortSpinner != null) {
            setupSortSpinner();
        }
        // RecyclerView - maximize vertical space
        if (commentsRecyclerView != null && layoutManager == null) {
            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
            commentsRecyclerView.setLayoutManager(layoutManager);
            commentsRecyclerView.setClipToPadding(false);  // Allow content to scroll without padding
        }
    }

    private void setupSortSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.comment_sort_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(adapter);
    }

    private void setupAdapters() {
        commentAdapter = new CommentAdapter(getContext(), this);
        commentsRecyclerView.setAdapter(commentAdapter);
    }

    private void setupScrollListener(RecyclerView recyclerView) {
        if (recyclerView == null) return;
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

    private void setupListeners() {
        if (sendButton != null) {
            sendButton.setOnClickListener(v -> sendComment());
            sendButton.setEnabled(false);
        }
        if (emojiButton != null) emojiButton.setOnClickListener(v -> openEmojiPicker());
        if (gifButton != null) gifButton.setOnClickListener(v -> openGifPicker());
        if (attachMediaButton != null) attachMediaButton.setOnClickListener(v -> openFileChooser());

        if (commentInput != null) {
            // Auto-scroll when focus to keep compose section visible above keyboard
            commentInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // Scroll to bottom when keyboard appears
                    if (commentsRecyclerView != null && commentAdapter != null && commentAdapter.getItemCount() > 0) {
                        commentsRecyclerView.smoothScrollToPosition(commentAdapter.getItemCount() - 1);
                    }

                    // Set reply mention if replying
                    if (replyingToUserId != null) {
                        commentInput.setText("@" + replyingToUserId + " ");
                        commentInput.setSelection(commentInput.getText().length());
                    }
                }
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
                    if (sendButton != null) {
                        sendButton.setEnabled(hasContent && withinLimit);
                    }

                    if (charCountText != null) {
                        if (currentLength >= CHARACTER_LIMIT * 0.9) {
                            charCountText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                        } else if (currentLength >= CHARACTER_LIMIT) {
                            charCountText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        } else {
                            charCountText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                        }
                    }
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });

            commentInput.setFilters(new android.text.InputFilter[]{
                    new android.text.InputFilter.LengthFilter(CHARACTER_LIMIT)
            });
        }

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadComments);
        }
    }

    private void setupObservers() {
        commentViewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            if (comments != null && !comments.isEmpty()) {
                commentAdapter.setComments(comments);
            } else {
                if (commentAdapter != null) {
                    commentAdapter.setComments(new java.util.ArrayList<>());
                }
            }
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        });

        commentViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        });

        commentViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (!isLoading) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                isLoadingMore = false;
            }
        });
    }

    private void loadComments() {
        if (postId != null && !postId.isEmpty()) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
            commentViewModel.loadComments(postId);
        } else {
            Toast.makeText(getContext(), "Error: Post ID not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMoreComments() {
        commentViewModel.loadMoreComments(postId);
    }

    private void sendComment() {
        if (commentInput == null) {
            Toast.makeText(getContext(), "Error: Comment input not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String commentText = replyingToUserId != null
                ? text.replace("@" + replyingToUserId + " ", "").trim()
                : text;
        if (commentText.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        commentViewModel.sendComment(postId, commentText);
        commentInput.setText("");
        replyingToUserId = null;
    }

    private void updateCharacterCount(int currentLength) {
        if (charCountText != null) {
            charCountText.setText(String.format(java.util.Locale.getDefault(), "%d/%d", currentLength, CHARACTER_LIMIT));
        }
    }

    // Placeholders for future features
    private void openEmojiPicker() {
        Toast.makeText(getContext(), "Emoji picker - Coming soon", Toast.LENGTH_SHORT).show();
    }
    private void openGifPicker() {
        Toast.makeText(getContext(), "GIF picker - Coming soon", Toast.LENGTH_SHORT).show();
    }
    private void openFileChooser() {
        Toast.makeText(getContext(), "File picker - Coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    @Override
    public void onReplyClicked(Comment comment) {
        if (comment == null || comment.getUser() == null) {
            Toast.makeText(getContext(), "Error: Cannot reply to this comment", Toast.LENGTH_SHORT).show();
            return;
        }
        replyingToUserId = comment.getUser().getName();
        if (commentInput != null) commentInput.requestFocus();
    }

    @Override
    public void onViewMoreRepliesClicked(Comment comment) {
        commentViewModel.loadMoreReplies(comment.getId());
    }

    @Override
    public void onCommentLongPressed(Comment comment, int position) {
        showCommentOptionsMenu(comment, position);
    }

    private void showCommentOptionsMenu(Comment comment, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Comment options");

        boolean isOwnComment = isOwnedByCurrentUser(comment);
        java.util.List<String> options = new java.util.ArrayList<>();
        if (isOwnComment) {
            options.add("Edit");
            options.add("Delete");
        }
        options.add("Report");
        options.add("Share");
        options.add("Copy");
        options.add("Cancel");

        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            if (which == 0 && isOwnComment) {
                editComment(comment);
            } else if (which == 1 && isOwnComment) {
                deleteComment(comment, position);
            } else if (!isOwnComment && which == 0) {
                reportComment(comment);
            } else if (!isOwnComment && which == 1) {
                shareComment(comment);
            } else if (!isOwnComment && which == 2) {
                copyCommentText(comment);
            }
        });
        builder.show();
    }

    private boolean isOwnedByCurrentUser(Comment comment) {
        // TODO: Implement check against current user ID
        return false;
    }
    private void editComment(Comment comment) {
        Toast.makeText(getContext(), "Edit comment - Coming soon", Toast.LENGTH_SHORT).show();
    }
    private void deleteComment(Comment comment, int position) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete comment?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    commentViewModel.deleteComment(comment.getId());
                    commentAdapter.removeComment(position);
                    Toast.makeText(getContext(), "Comment deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void reportComment(Comment comment) {
        Toast.makeText(getContext(), "Comment reported", Toast.LENGTH_SHORT).show();
    }
    private void shareComment(Comment comment) {
        android.content.Intent sendIntent = new android.content.Intent();
        sendIntent.setAction(android.content.Intent.ACTION_SEND);
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, comment.getText());
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }
    private void copyCommentText(Comment comment) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("comment", comment.getText());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Comment copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }
    }
}