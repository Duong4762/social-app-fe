package com.example.social_app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.adapters.CommentAdapter;
import com.example.social_app.data.model.Comment;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.MockDataGenerator;
import com.example.social_app.viewmodels.CommentViewModel;

import java.util.ArrayList;
import java.util.List;

public class CommentFragment extends Fragment implements CommentAdapter.OnCommentActionListener {

    private static final String POST_ID_KEY = "post_id";
    private static final int SCROLL_THRESHOLD = 5;
    private static final int CHARACTER_LIMIT = 280;

    private RecyclerView commentsRecyclerView;
    private EditText commentInput;
    private ImageButton sendButton, emojiButton;
    private ImageView composeAvatar;
    private TextView charCountText;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout actionButtonsSection;

    private CommentAdapter commentAdapter;
    private CommentViewModel commentViewModel;
    private String postId;
    private String replyingToUserId = null;
    private boolean isLoadingMore = false;
    private LinearLayoutManager layoutManager;

    // Swipe to dismiss gesture (optional)
    private GestureDetectorCompat gestureDetector;

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
        setupGestureDetector(view);
        setupObservers();
        loadComments();
        return view;
    }

    private void setupObservers() {
        commentViewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            if (comments != null) {
                commentAdapter.setComments(comments);
            }
            swipeRefresh.setRefreshing(false);
            isLoadingMore = false;
        });

        commentViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
            }
        });

        commentViewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null) swipeRefresh.setRefreshing(loading);
        });
    }

    private void initializeViews(View view) {
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_layout);

        composeAvatar = view.findViewById(R.id.compose_avatar);
        commentInput = view.findViewById(R.id.compose_comment_input);
        charCountText = view.findViewById(R.id.compose_char_count_text);

        actionButtonsSection = view.findViewById(R.id.compose_action_buttons_section);
        emojiButton = view.findViewById(R.id.compose_emoji_button);
        sendButton = view.findViewById(R.id.compose_send_button);

        if (composeAvatar != null) {
            UserAvatarLoader.load(composeAvatar, null);
        }
        updateCharacterCount(0);

        layoutManager = new LinearLayoutManager(getContext());
        commentsRecyclerView.setLayoutManager(layoutManager);
    }

    private void setupAdapters() {
        commentAdapter = new CommentAdapter(getContext(), this);
        commentsRecyclerView.setAdapter(commentAdapter);
    }

    private void setupGestureDetector(View view) {
        gestureDetector = new GestureDetectorCompat(getContext(), new SwipeGestureListener());
        view.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                float diffY = e2.getRawY() - e1.getRawY();
                float diffX = e2.getRawX() - e1.getRawX();
                if (Math.abs(diffY) > Math.abs(diffX) &&
                        diffY > 100 && Math.abs(velocityY) > 100) {
                    dismissFragment();
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

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
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (!isLoadingMore && (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - SCROLL_THRESHOLD)
                        && firstVisibleItemPosition >= 0) {
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

        if (commentInput != null) {
            commentInput.addTextChangedListener(new TextWatcher() {
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
                public void afterTextChanged(Editable s) {}
            });

            commentInput.setFilters(new android.text.InputFilter[]{
                    new android.text.InputFilter.LengthFilter(CHARACTER_LIMIT)
            });

            commentInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && replyingToUserId != null) {
                    commentInput.setText("@" + replyingToUserId + " ");
                    commentInput.setSelection(commentInput.getText().length());
                }
            });
        }
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadComments);
        }
    }

    private void updateCharacterCount(int currentLength) {
        if (charCountText != null) {
            charCountText.setText(String.format("%d/%d", currentLength, CHARACTER_LIMIT));
        }
    }

    private void openEmojiPicker() {
        Toast.makeText(getContext(), R.string.emoji_picker_coming_soon, Toast.LENGTH_SHORT).show();
    }

    private void loadComments() {
        if (postId == null) return;
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        
        commentViewModel.loadComments(postId);
    }

    private void loadMoreComments() {
        commentViewModel.loadMoreComments(postId);
    }

    private String replyingToCommentId = null;

    private void sendComment() {
        if (commentInput == null) return;
        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.comment_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // If we are replying, use the parentId
        commentViewModel.sendComment(postId, text, replyingToCommentId);
        
        commentInput.setText("");
        replyingToUserId = null;
        replyingToCommentId = null;
    }

    // ==================== CommentAdapter.OnCommentActionListener Callbacks ====================
    @Override
    public void onLikeClicked(Comment comment, int position) {
        commentViewModel.toggleLike(comment);
    }

    @Override
    public void onReplyClicked(Comment comment, String userName) {
        if (comment == null) return;
        replyingToUserId = userName;
        replyingToCommentId = comment.getId();
        if (commentInput != null) {
            commentInput.requestFocus();
            commentInput.setText("@" + replyingToUserId + " ");
            commentInput.setSelection(commentInput.getText().length());
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
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.report_reason_title);
        boolean isOwnComment = isOwnedByCurrentUser(comment);
        List<String> options = new ArrayList<>();
        if (isOwnComment) {
            options.add(getString(R.string.menu_edit));
            options.add(getString(R.string.delete));
        }
        options.add(getString(R.string.menu_report));
        options.add(getString(R.string.action_share));
        options.add(getString(R.string.copy_action));
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
        // TODO: Check with current user id, demo always return false
        return false;
    }

    private void editComment(Comment comment) {
        Toast.makeText(getContext(), R.string.edit_comment_coming_soon, Toast.LENGTH_SHORT).show();
    }

    private void deleteComment(Comment comment, int position) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        Toast.makeText(getContext(), R.string.comment_reported, Toast.LENGTH_SHORT).show();
    }

    private void shareComment(Comment comment) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, comment.getContent());
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    private void copyCommentText(Comment comment) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("comment", comment.getContent());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.comment_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Đo chiều cao các view nếu cần debug
    }

}