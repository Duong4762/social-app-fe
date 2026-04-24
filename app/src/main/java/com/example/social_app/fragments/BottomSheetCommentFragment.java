package com.example.social_app.fragments;

import android.os.Bundle;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.adapters.CommentAdapter;
import com.example.social_app.data.model.Comment;
import com.example.social_app.viewmodels.CommentViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetCommentFragment extends BottomSheetDialogFragment implements CommentAdapter.OnCommentActionListener {

    private static final String POST_ID_KEY = "post_id";
    private static final int SCROLL_THRESHOLD = 5;
    private static final int CHARACTER_LIMIT = 280;

    private RecyclerView commentsRecyclerView;
    private EditText commentInput;
    private ImageButton sendButton, emojiButton, attachMediaButton;
    private ImageView composeAvatar;
    private TextView userName;
    private SwipeRefreshLayout swipeRefresh;
    private TextView charCountText;
    private LinearLayout actionButtonsSection;
    private MaterialButton mediaSendButton;
    private android.net.Uri selectedMediaUri;
    private String selectedMediaType;
    private View selectedMediaContainer;
    private ImageView selectedMediaPreview;
    private ImageButton selectedMediaRemoveButton;
    private static final int MEDIA_PERMISSION_REQUEST = 101;
    private final List<PickerMediaItem> pickerMediaItems = new ArrayList<>();
    private MediaPickerAdapter mediaPickerAdapter;

    private CommentAdapter commentAdapter;
    private CommentViewModel commentViewModel;
    private String postId;
    private String replyingToUserId = null;
    private String replyingToCommentId = null;
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
        swipeRefresh = view.findViewById(R.id.swipe_refresh_layout);

        // Compose
        composeAvatar = view.findViewById(R.id.compose_avatar);
        commentInput = view.findViewById(R.id.compose_comment_input);
        charCountText = view.findViewById(R.id.compose_char_count_text);

        // Action buttons
        actionButtonsSection = view.findViewById(R.id.compose_action_buttons_section);
        attachMediaButton = view.findViewById(R.id.compose_attach_media_button);
        emojiButton = view.findViewById(R.id.compose_emoji_button);
        sendButton = view.findViewById(R.id.compose_send_button);
        mediaSendButton = view.findViewById(R.id.media_send_button);
        selectedMediaContainer = view.findViewById(R.id.compose_selected_media_container);
        selectedMediaPreview = view.findViewById(R.id.compose_selected_media_preview);
        selectedMediaRemoveButton = view.findViewById(R.id.compose_selected_media_remove);

        if (selectedMediaRemoveButton != null) {
            selectedMediaRemoveButton.setOnClickListener(v -> {
                selectedMediaUri = null;
                selectedMediaType = null;
                updateSelectedMediaPreview();
                updateSendButtonState();
            });
        }
        if (mediaSendButton != null) {
            mediaSendButton.setVisibility(View.GONE);
        }

        if (composeAvatar != null) {
            UserAvatarLoader.load(composeAvatar, null);
        }
        if (charCountText != null) {
            updateCharacterCount(0);
        }

        // RecyclerView - maximize vertical space
        if (commentsRecyclerView != null && layoutManager == null) {
            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
            commentsRecyclerView.setLayoutManager(layoutManager);
            commentsRecyclerView.setClipToPadding(false);  // Allow content to scroll without padding
        }
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
        if (mediaSendButton != null) {
            mediaSendButton.setOnClickListener(v -> sendComment());
        }
        if (emojiButton != null) emojiButton.setOnClickListener(v -> openEmojiPicker());
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

                    updateSendButtonState();

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
            if (comments != null) {
                commentAdapter.setComments(comments);
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
            Toast.makeText(getContext(), R.string.error_post_id_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMoreComments() {
        commentViewModel.loadMoreComments(postId);
    }

    private void sendComment() {
        if (commentInput == null) {
            Toast.makeText(getContext(), R.string.error_comment_input_not_available, Toast.LENGTH_SHORT).show();
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
            commentViewModel.sendComment(postId, commentText, replyingToCommentId);
            resetCommentInput();
        }
    }

    private void uploadMediaAndSendComment(String text) {
        if (selectedMediaUri == null) return;

        Toast.makeText(getContext(), R.string.uploading_media, Toast.LENGTH_SHORT).show();
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
                        commentViewModel.sendCommentWithMedia(
                                postId,
                                commentText,
                                secureUrl,
                                "image"
                        );
                        resetCommentInput();
                    }

                    @Override
                    public void onError(String message, Throwable throwable) {
                        Toast.makeText(getContext(), getString(R.string.upload_failed, message), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void resetCommentInput() {
        if (commentInput != null) commentInput.setText("");
        selectedMediaUri = null;
        selectedMediaType = null;
        replyingToUserId = null;
        replyingToCommentId = null;
        updateSelectedMediaPreview();
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
    private void openFileChooser() {
        showMediaSourcePickerFullscreen();
    }

    private void updateSendButtonState() {
        if (sendButton == null || commentInput == null) return;
        boolean hasText = commentInput.getText().toString().trim().length() > 0;
        boolean hasMedia = selectedMediaUri != null;
        sendButton.setEnabled(hasText || hasMedia);
        sendButton.setVisibility(View.VISIBLE);
        if (mediaSendButton != null) {
            mediaSendButton.setVisibility(View.GONE);
            mediaSendButton.setEnabled(false);
        }
    }

    private void updateSelectedMediaPreview() {
        if (selectedMediaContainer == null || selectedMediaPreview == null) return;
        if (selectedMediaUri == null) {
            selectedMediaContainer.setVisibility(View.GONE);
            return;
        }
        selectedMediaContainer.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(selectedMediaUri)
                .fitCenter()
                .into(selectedMediaPreview);
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
        MaterialButton sendBtn = dialog.findViewById(R.id.media_picker_done);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        sendBtn.setVisibility(View.GONE);

        mediaPickerAdapter = new MediaPickerAdapter((item, position) -> {
            if (item.uri == null) return;
            selectedMediaUri = item.uri;
            selectedMediaType = item.isVideo ? "video" : "image";
            updateSelectedMediaPreview();
            updateSendButtonState();
            dialog.dismiss();
        });

        mediaGrid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mediaGrid.setAdapter(mediaPickerAdapter);
        loadDeviceMediaItems();
        dialog.show();
    }

    private void loadDeviceMediaItems() {
        pickerMediaItems.clear();
        if (!canReadImages()) {
            requestMediaPermissions();
            if (mediaPickerAdapter != null) mediaPickerAdapter.notifyDataSetChanged();
            return;
        }

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
        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int count = 0;
            while (cursor.moveToNext() && count < 200) {
                long id = cursor.getLong(idColumn);
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                pickerMediaItems.add(new PickerMediaItem(uri, false));
                count++;
            }
            cursor.close();
        }
        if (mediaPickerAdapter != null) mediaPickerAdapter.notifyDataSetChanged();
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

    private void requestMediaPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, MEDIA_PERMISSION_REQUEST);
            return;
        }
        requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, MEDIA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST) {
            loadDeviceMediaItems();
        }
    }

    private final class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.MediaVH> {
        private final OnPickerItemClickListener listener;

        MediaPickerAdapter(OnPickerItemClickListener listener) {
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
            Glide.with(BottomSheetCommentFragment.this)
                    .load(item.uri)
                    .thumbnail(0.2f)
                    .centerCrop()
                    .into(holder.thumb);

            holder.videoBadge.setVisibility(View.GONE);
            holder.videoDuration.setVisibility(View.GONE);
            boolean isSelected = item.uri != null && item.uri.equals(selectedMediaUri);
            holder.selectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.selectedCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
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

    private interface OnPickerItemClickListener {
        void onItemClick(PickerMediaItem item, int position);
    }

    private static final class PickerMediaItem {
        final Uri uri;
        final boolean isVideo;

        PickerMediaItem(@Nullable Uri uri, boolean isVideo) {
            this.uri = uri;
            this.isVideo = isVideo;
        }
    }

    @Override
    public void onUserClicked(String userId) {
        if (userId == null || userId.trim().isEmpty()) return;
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        androidx.fragment.app.Fragment fragment;
        if (userId.equals(currentUserId)) {
            fragment = new ProfileFragment();
        } else {
            fragment = OtherProfileFragment.newInstance(userId);
        }
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
        dismiss();
    }

    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    @Override
    public void onReplyClicked(Comment comment, String userName) {
        if (comment == null) {
            Toast.makeText(getContext(), R.string.error_cannot_reply, Toast.LENGTH_SHORT).show();
            return;
        }
        replyingToUserId = userName;
        replyingToCommentId = comment.getId();
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
        builder.setTitle(R.string.report_reason_title);

        boolean isOwnComment = isOwnedByCurrentUser(comment);
        java.util.List<String> options = new java.util.ArrayList<>();
        if (isOwnComment) {
            options.add(getString(R.string.menu_edit));
            options.add(getString(R.string.delete));
        }
        options.add(getString(R.string.menu_report));
        options.add(getString(R.string.action_share));
        options.add(getString(R.string.copy_action)); // Assuming we'll add copy_action
        options.add(getString(R.string.cancel_action));

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
                .setTitle(R.string.delete_post_title)
                .setMessage(R.string.delete_post_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    commentViewModel.deleteComment(comment.getId());
                    commentAdapter.removeComment(position);
                    Toast.makeText(getContext(), R.string.comment_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel_action, null)
                .show();
    }
    private void reportComment(Comment comment) {
        String[] reasons = {
                getString(R.string.report_reason_inappropriate),
                getString(R.string.report_reason_spam),
                getString(R.string.report_reason_harassment),
                getString(R.string.report_reason_false_info),
                getString(R.string.report_reason_other)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.report_post_title)) // Có thể dùng chung title báo cáo
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];
                    showReportDetailDialog(comment, selectedReason);
                })
                .setNegativeButton(getString(R.string.cancel_action), null)
                .show();
    }

    private void showReportDetailDialog(Comment comment, String baseReason) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.report_reason_hint);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.addView(input);
        input.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.report_reason_title)
                .setView(container)
                .setPositiveButton(R.string.report_submit, (dialog, which) -> {
                    String detail = input.getText().toString().trim();
                    String finalReason = detail.isEmpty() ? baseReason : baseReason + ": " + detail;
                    commentViewModel.reportComment(comment, finalReason);
                    Toast.makeText(requireContext(), getString(R.string.report_thanks), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel_action, null)
                .show();
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
        Toast.makeText(getContext(), R.string.comment_copied, Toast.LENGTH_SHORT).show();
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