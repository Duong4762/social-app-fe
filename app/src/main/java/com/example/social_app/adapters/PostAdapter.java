package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.PostMedia;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.MockDataGenerator;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView Adapter for displaying posts in the social feed.
 * Supports multiple view types including post composer and post items.
 */
public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_COMPOSER = 0;
    private static final int VIEW_TYPE_POST = 1;

    private List<Post> posts;
    private Context context;
    private OnPostActionListener actionListener;
    private Set<String> likedPostIds = new HashSet<>();
    private Set<String> bookmarkedPostIds = new HashSet<>();

    public interface OnPostActionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post);
        void onShareClicked(Post post);
        void onBookmarkClicked(Post post);
        void onComposerPostClicked(String content);
        void onComposerClicked();  // NEW: Handle composer clicks to open new post creation
        void onEditPostClicked(Post post); // NEW: Handle edit post
        void onDeletePostClicked(Post post); // NEW: Handle delete post
    }

    public PostAdapter(Context context, OnPostActionListener actionListener) {
        this.context = context;
        this.posts = new ArrayList<>();
        this.actionListener = actionListener;
    }

    /**
     * Sets the posts data to display.
     */
    public void setPosts(List<Post> posts) {
        this.posts = posts;
        notifyDataSetChanged();
    }

    public void setEngagementData(Set<String> likedIds, Set<String> bookmarkedIds) {
        this.likedPostIds = likedIds;
        this.bookmarkedPostIds = bookmarkedIds;
        notifyDataSetChanged();
    }

    /**
     * Adds more posts (for infinite scroll).
     */
    public void addPosts(List<Post> newPosts) {
        int previousSize = posts.size();
        posts.addAll(newPosts);
        notifyItemRangeInserted(previousSize, newPosts.size());
    }

    public boolean toggleLiked(String postId) {
        if (postId == null) {
            return false;
        }
        if (likedPostIds.contains(postId)) {
            likedPostIds.remove(postId);
            return false;
        }
        likedPostIds.add(postId);
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        // First item is always the composer
        return position == 0 ? VIEW_TYPE_COMPOSER : VIEW_TYPE_POST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_COMPOSER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_post_composer, parent, false);
            return new ComposerViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            PostViewHolder postHolder = (PostViewHolder) holder;
            // Position 0 is composer, so actual post index is position - 1
            Post post = posts.get(position - 1);
            postHolder.bind(post, position - 1);
        } else if (holder instanceof ComposerViewHolder) {
            ((ComposerViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        // +1 for the composer item at the top
        return posts.size() + 1;
    }

    /**
     * ViewHolder for the post composer item at the top of the feed.
     */
    private class ComposerViewHolder extends RecyclerView.ViewHolder {
        private com.google.android.material.button.MaterialButton composerInput;
        private ImageButton composerImageBtn;
        private ImageView composerAvatar;

        ComposerViewHolder(@NonNull View itemView) {
            super(itemView);
            // Initialize composer views
            composerInput = itemView.findViewById(R.id.composer_input);
            composerImageBtn = itemView.findViewById(R.id.composer_image_btn);
            composerAvatar = itemView.findViewById(R.id.composer_avatar);

            // Set up click listeners for composer
            setupComposerListeners();
        }

        private void setupComposerListeners() {
            // Click listener for "What's new?" input button
            composerInput.setOnClickListener(v -> {
                android.util.Log.d("PostAdapter", "Composer input clicked - switching to NewPostFragment");
                if (actionListener != null) {
                    actionListener.onComposerClicked();
                }
            });

            // Click listener for image button (alternative way to create post)
            composerImageBtn.setOnClickListener(v -> {
                android.util.Log.d("PostAdapter", "Composer image button clicked - switching to NewPostFragment");
                if (actionListener != null) {
                    actionListener.onComposerClicked();
                }
            });
        }

        void bind() {
            // Load current user avatar
            String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
            if (currentUserId != null) {
                FirebaseManager.getInstance().getFirestore()
                        .collection(FirebaseManager.COLLECTION_USERS)
                        .document(currentUserId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String avatarUrl = documentSnapshot.getString("avatarUrl");
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(context)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.bg_nav_item_selected)
                                        .circleCrop()
                                        .into(composerAvatar);
                            }
                        });
            }
        }
    }

    /**
     * ViewHolder for individual post items in the feed.
     */
    private class PostViewHolder extends RecyclerView.ViewHolder {
        private ImageView userAvatar;
        private TextView username;
        private TextView timestamp;
        private TextView location;
        private TextView postContent;
        private ViewPager2 postViewPager;
        private TextView mediaIndicator;
        private View postMediaContainer;
        private ImageView likeIcon;
        private TextView likeCount;
        private ImageView commentIcon;
        private TextView commentCount;
        private ImageView shareIcon;
        private TextView shareCount;
        private ImageView bookmarkIcon;
        private LinearLayout likeContainer;
        private LinearLayout commentContainer;
        private LinearLayout shareContainer;
        private ImageButton moreOptions;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.post_user_avatar);
            username = itemView.findViewById(R.id.post_username);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            location = itemView.findViewById(R.id.post_location);
            postContent = itemView.findViewById(R.id.post_content);
            postViewPager = itemView.findViewById(R.id.post_view_pager);
            mediaIndicator = itemView.findViewById(R.id.media_indicator);
            postMediaContainer = itemView.findViewById(R.id.post_media_container);
            likeIcon = itemView.findViewById(R.id.post_like_icon);
            likeCount = itemView.findViewById(R.id.post_like_count);
            commentIcon = itemView.findViewById(R.id.post_comment_icon);
            commentCount = itemView.findViewById(R.id.post_comment_count);
            shareIcon = itemView.findViewById(R.id.post_share_icon);
            shareCount = itemView.findViewById(R.id.post_share_count);
            bookmarkIcon = itemView.findViewById(R.id.post_bookmark_icon);
            likeContainer = itemView.findViewById(R.id.post_like_container);
            commentContainer = itemView.findViewById(R.id.post_comment_container);
            shareContainer = itemView.findViewById(R.id.post_share_container);
            moreOptions = itemView.findViewById(R.id.post_more_options);
        }

        private void bind(Post post, int position) {
            // Load User Info from Firestore instead of MockData
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(post.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            username.setText(user.getUsername() != null ? user.getUsername() : user.getFullName());
                            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                                Glide.with(context)
                                        .load(user.getAvatarUrl())
                                        .circleCrop()
                                        .into(userAvatar);
                            } else {
                                userAvatar.setImageResource(R.drawable.avatar_placeholder);
                            }
                        } else {
                            username.setText("Người dùng");
                            userAvatar.setImageResource(R.drawable.avatar_placeholder);
                        }
                    })
                    .addOnFailureListener(e -> {
                        username.setText("Người dùng");
                        userAvatar.setImageResource(R.drawable.avatar_placeholder);
                    });

            // Set timestamp
            long createdAt = post.getCreatedAt() != null
                    ? post.getCreatedAt().getTime()
                    : System.currentTimeMillis();
            timestamp.setText(MockDataGenerator.getTimeDifferenceString(createdAt));

            // Set location
            location.setVisibility(View.GONE);

            // Set post content
            postContent.setText(post.getCaption());

            // Load Post Media from Firestore
            loadPostMedia(post.getId());

            // Set engagement counts
            likeCount.setText(String.valueOf(post.getLikeCount()));
            commentCount.setText(String.valueOf(post.getCommentCount()));
            shareCount.setText("0");

            // Update like icon based on liked state
            updateLikeIcon(post);

            // Update bookmark icon based on bookmark state
            updateBookmarkIcon(post);

            // Set up click listeners - ONLY ONCE
            likeContainer.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onLikeClicked(post, position);
                }
            });

            commentContainer.setOnClickListener(v -> {
                android.util.Log.d("PostAdapter", "Comment clicked for post: " + post.getId());
                if (actionListener != null) {
                    actionListener.onCommentClicked(post);
                }
            });

            shareContainer.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onShareClicked(post);
                }
            });

            bookmarkIcon.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onBookmarkClicked(post);
                }
            });

            moreOptions.setOnClickListener(v -> {
                showMoreOptionsDialog(post);
            });
        }

        private void loadPostMedia(String postId) {
            FirebaseFirestore db = FirebaseManager.getInstance().getFirestore();
            db.collection(FirebaseManager.COLLECTION_POST_MEDIA)
                    .whereEqualTo("postId", postId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<PostMedia> mediaList = queryDocumentSnapshots.toObjects(PostMedia.class);
                        if (!mediaList.isEmpty()) {
                            // Sort manually to avoid index requirement error
                            mediaList.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

                            postMediaContainer.setVisibility(View.VISIBLE);
                            PostImageAdapter imageAdapter = new PostImageAdapter(context);
                            postViewPager.setAdapter(imageAdapter);
                            imageAdapter.setMediaList(mediaList);

                            if (mediaList.size() > 1) {
                                mediaIndicator.setVisibility(View.VISIBLE);
                                mediaIndicator.setText("1/" + mediaList.size());
                                postViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                                    @Override
                                    public void onPageSelected(int position) {
                                        mediaIndicator.setText((position + 1) + "/" + mediaList.size());
                                    }
                                });
                            } else {
                                mediaIndicator.setVisibility(View.GONE);
                            }
                        } else {
                            postMediaContainer.setVisibility(View.GONE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        postMediaContainer.setVisibility(View.GONE);
                    });
        }

        private void showMoreOptionsDialog(Post post) {
            String[] options = {"Chỉnh sửa", "Xóa"};
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            if (actionListener != null) actionListener.onEditPostClicked(post);
                        } else if (which == 1) {
                            if (actionListener != null) actionListener.onDeletePostClicked(post);
                        }
                    })
                    .show();
        }

        /**
         * Updates the like icon visual state.
         */
        private void updateLikeIcon(Post post) {
            if (likedPostIds.contains(post.getId())) {
                likeIcon.setImageResource(R.drawable.ic_heart_filled);
                likeIcon.setColorFilter(context.getResources().getColor(R.color.accent_red, null));
            } else {
                likeIcon.setImageResource(R.drawable.ic_heart);
                // Reset to default color
                likeIcon.clearColorFilter();
            }
        }

        /**
         * Updates the bookmark icon visual state.
         */
        private void updateBookmarkIcon(Post post) {
            if (bookmarkedPostIds.contains(post.getId())) {
                bookmarkIcon.setImageResource(R.drawable.ic_bookmark_filled);
                // Có thể thêm setColorFilter nếu muốn màu đặc biệt
                // bookmarkIcon.setColorFilter(context.getResources().getColor(R.color.accent_purple, null));
            } else {
                bookmarkIcon.setImageResource(R.drawable.ic_bookmark);
                bookmarkIcon.clearColorFilter();
            }
        }
    }
}


