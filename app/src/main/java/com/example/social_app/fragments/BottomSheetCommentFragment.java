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
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.adapters.CommentAdapter;
import com.example.social_app.data.model.Comment;
import com.example.social_app.utils.MockDataGenerator;
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
    private android.net.Uri selectedMediaUri;
    private String selectedMediaType;

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
            UserAvatarLoader.load(composeAvatar, null);
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
        if (text.isEmpty() && selectedMediaUri == null) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedMediaUri != null) {
            uploadMediaAndSendComment(text);
        } else {
            String commentText = replyingToUserId != null
                    ? text.replace("@" + replyingToUserId + " ", "").trim()
                    : text;
            commentViewModel.sendComment(postId, commentText);
            resetCommentInput();
        }
    }

    private void uploadMediaAndSendComment(String text) {
        if (selectedMediaUri == null) return;

        Toast.makeText(getContext(), "Uploading media...", Toast.LENGTH_SHORT).show();
        com.example.social_app.utils.CloudinaryUploadUtil.uploadMedia(
                requireContext(),
                selectedMediaUri,
                new com.example.social_app.utils.CloudinaryUploadUtil.UploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        String commentText = replyingToUserId != null
                                ? text.replace("@" + replyingToUserId + " ", "").trim()
                                : text;
                        
                        // Need to update CommentViewModel to support media
                        // For now, we'll just log and reset
                        android.util.Log.d("CommentFragment", "Media uploaded: " + secureUrl);
                        
                        // If Comment model was updated, we would send it here
                        // For this task, I'll assume CommentViewModel.sendComment can handle or be updated
                        commentViewModel.sendComment(postId, commentText + (commentText.isEmpty() ? "" : " ") + secureUrl);
                        
                        resetCommentInput();
                    }

                    @Override
                    public void onError(String message, Throwable throwable) {
                        Toast.makeText(getContext(), "Upload failed: " + message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void resetCommentInput() {
        if (commentInput != null) commentInput.setText("");
        selectedMediaUri = null;
        selectedMediaType = null;
        replyingToUserId = null;
        updateSendButtonState();
    }

    private void updateCharacterCount(int currentLength) {
        if (charCountText != null) {
            charCountText.setText(String.format(java.util.Locale.getDefault(), "%d/%d", currentLength, CHARACTER_LIMIT));
        }
    }

    // Placeholders for future features
    private void openEmojiPicker() {
        if (commentInput == null) return;
        
        final String[] emojis = {"😀", "😂", "😍", "👍", "🔥", "😮", "😢", "😡", "🎉", "❤️", "✨", "🙏", "😎", "🤔", "👏", "🙌", "💪", "💯"};
        
        android.widget.GridView gridView = new android.widget.GridView(requireContext());
        gridView.setNumColumns(5); // Giảm số cột để tăng chiều rộng mỗi cột
        gridView.setPadding(16, 16, 16, 16);
        gridView.setVerticalSpacing(16);
        gridView.setHorizontalSpacing(16);
        gridView.setStretchMode(android.widget.GridView.STRETCH_COLUMN_WIDTH);
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, emojis) {
            @NonNull
            @Override
            public android.view.View getView(int position, @Nullable android.view.View convertView, @NonNull android.view.ViewGroup parent) {
                android.widget.TextView textView = (android.widget.TextView) super.getView(position, convertView, parent);
                textView.setTextSize(28); // Tăng nhẹ size emoji
                textView.setGravity(android.view.Gravity.CENTER);
                textView.setPadding(0, 0, 0, 0); // Loại bỏ padding mặc định của item
                textView.setBackground(null);
                return textView;
            }
        };
        gridView.setAdapter(adapter);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Chọn Emoji")
                .setView(gridView)
                .create();
                
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedEmoji = emojis[position];
            int start = commentInput.getSelectionStart();
            int end = commentInput.getSelectionEnd();
            commentInput.getText().replace(Math.min(start, end), Math.max(start, end), selectedEmoji);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    private void openGifPicker() {
        // Implement GIF picker (e.g., using Giphy SDK or a simple search)
        // For now, let's use a mock implementation that picks a sample GIF
        selectedMediaUri = android.net.Uri.parse("https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExNHJidmZ3Z3R3Z3R3Z3R3Z3R3Z3R3Z3R3Z3R3Z3R3Z3R3JlcD1ndm1fYnlfaWQmY3Q9Zw/3o7TKMGpxP5OqP6V9u/giphy.gif");
        selectedMediaType = "gif";
        updateSendButtonState();
        Toast.makeText(getContext(), "GIF selected", Toast.LENGTH_SHORT).show();
    }

    private void openFileChooser() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK);
        intent.setType("image/*");
        mediaPickerLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mediaPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                            selectedMediaUri = result.getData().getData();
                            selectedMediaType = "image";
                            updateSendButtonState();
                            Toast.makeText(getContext(), "Image selected", Toast.LENGTH_SHORT).show();
                        }
                    });

    private void updateSendButtonState() {
        if (sendButton == null || commentInput == null) return;
        boolean hasText = commentInput.getText().toString().trim().length() > 0;
        boolean hasMedia = selectedMediaUri != null;
        sendButton.setEnabled(hasText || hasMedia);
    }

    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    @Override
    public void onReplyClicked(Comment comment) {
        if (comment == null) {
            Toast.makeText(getContext(), "Error: Cannot reply to this comment", Toast.LENGTH_SHORT).show();
            return;
        }
        replyingToUserId = MockDataGenerator.getUserDisplayName(comment.getUserId());
        if (commentInput != null) {
            commentInput.requestFocus();
            // Force update text and selection even if already focused
            commentInput.setText("@" + replyingToUserId + " ");
            commentInput.setSelection(commentInput.getText().length());
            
            // Show soft keyboard explicitly
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(commentInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }
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
        String currentUserId = com.example.social_app.firebase.FirebaseManager.getInstance().getAuth().getUid();
        return currentUserId != null && currentUserId.equals(comment.getUserId());
    }
    private void editComment(Comment comment) {
        commentInput.setText(comment.getContent());
        commentInput.requestFocus();
        commentInput.setSelection(commentInput.getText().length());
        
        // Change send button icon to indicate edit mode (optional)
        // Store the fact that we are editing
        final String originalContent = comment.getContent();
        sendButton.setOnClickListener(v -> {
            String newContent = commentInput.getText().toString().trim();
            if (!newContent.isEmpty() && !newContent.equals(originalContent)) {
                commentViewModel.deleteComment(comment.getId()); // In a real app, use an update API
                commentViewModel.sendComment(postId, newContent);
                commentInput.setText("");
                // Restore original listener
                setupListeners();
            }
        });
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
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, comment.getContent());
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }
    private void copyCommentText(Comment comment) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("comment", comment.getContent());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Comment copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
                
                // Mở rộng tối đa ngay khi hiện ra
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                
                // Đặt chiều cao tối thiểu là full màn hình để khung comment không bị lửng lơ
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                
                // Ngăn chặn việc vuốt xuống để ẩn đi nếu đang cuộn RecyclerView
                bottomSheetBehavior.setSkipCollapsed(true);
            }
            
            if (getDialog().getWindow() != null) {
                getDialog().getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                );
            }
        }
    }
}