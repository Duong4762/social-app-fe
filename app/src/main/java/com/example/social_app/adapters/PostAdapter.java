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

import com.example.social_app.R;
import com.example.social_app.data.model.PostMedia;
import com.example.social_app.data.model.User;
import com.example.social_app.data.model.Post;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.MockDataGenerator;
import com.example.social_app.utils.UserAvatarLoader;
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
    private final Set<String> likedPostIds = new HashSet<>();
    private final Set<String> bookmarkedPostIds = new HashSet<>();

    public interface OnPostActionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post);
        void onShareClicked(Post post);
        void onBookmarkClicked(Post post);
        void onComposerPostClicked(String content);
        void onComposerClicked();  // NEW: Handle composer clicks to open new post creation
        void onComposerImageClicked(); // NEW: Handle image button clicks to pick image
        void onEditPostClicked(Post post);
        void onDeletePostClicked(Post post);
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

    /**
     * Adds more posts (for infinite scroll).
     */
    public void addPosts(List<Post> newPosts) {
        int previousSize = posts.size();
        posts.addAll(newPosts);
        notifyItemRangeInserted(previousSize, newPosts.size());
    }

    public void setEngagementData(Set<String> likedIds, Set<String> bookmarkedIds) {
        this.likedPostIds.clear();
        if (likedIds != null) this.likedPostIds.addAll(likedIds);
        
        this.bookmarkedPostIds.clear();
        if (bookmarkedIds != null) this.bookmarkedPostIds.addAll(bookmarkedIds);
        
        notifyDataSetChanged();
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
        if (holder instanceof ComposerViewHolder) {
            ((ComposerViewHolder) holder).bind();
        } else if (holder instanceof PostViewHolder) {
            PostViewHolder postHolder = (PostViewHolder) holder;
            // Position 0 is composer, so actual post index is position - 1
            Post post = posts.get(position - 1);
            postHolder.bind(post, position - 1);
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
        private ImageView composerAvatar;
        private com.google.android.material.button.MaterialButton composerInput;
        private ImageButton composerImageBtn;

        ComposerViewHolder(@NonNull View itemView) {
            super(itemView);
            // Initialize composer views
            composerAvatar = itemView.findViewById(R.id.composer_avatar);
            composerInput = itemView.findViewById(R.id.composer_input);
            composerImageBtn = itemView.findViewById(R.id.composer_image_btn);

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
                    actionListener.onComposerImageClicked();
                }
            });
        }

        void bind() {
            UserAvatarLoader.load(composerAvatar, null);
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
        private ImageView postImage;
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
        private ViewPager2 postViewPager;
        private TextView mediaIndicator;
        private View postMediaContainer;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.post_user_avatar);
            username = itemView.findViewById(R.id.post_username);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            location = itemView.findViewById(R.id.post_location);
            postContent = itemView.findViewById(R.id.post_content);
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
            postViewPager = itemView.findViewById(R.id.post_view_pager);
            mediaIndicator = itemView.findViewById(R.id.media_indicator);
            postMediaContainer = itemView.findViewById(R.id.post_media_container);
        }

        void bind(Post post, int position) {
            // Set user info
            User postUser = MockDataGenerator.getUserById(post.getUserId());
            if (postUser != null) {
                username.setText(postUser.getFullName());
                UserAvatarLoader.load(userAvatar, postUser.getAvatarUrl());
            } else {
                // Nếu không có trong Mock, thử lấy từ Firebase
                username.setText("Loading...");
                FirebaseFirestore.getInstance().collection(FirebaseManager.COLLECTION_USERS)
                        .document(post.getUserId())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String name = documentSnapshot.getString("fullName");
                                String avatar = documentSnapshot.getString("avatarUrl");
                                username.setText(name != null ? name : "Unknown User");
                                UserAvatarLoader.load(userAvatar, avatar);
                            } else {
                                username.setText("Unknown User");
                                UserAvatarLoader.load(userAvatar, null);
                            }
                        })
                        .addOnFailureListener(e -> {
                            username.setText("Unknown User");
                            UserAvatarLoader.load(userAvatar, null);
                        });
            }

            // Set timestamp
            long createdAt = post.getCreatedAt() != null
                    ? post.getCreatedAt().getTime()
                    : System.currentTimeMillis();
            timestamp.setText(MockDataGenerator.getTimeDifferenceString(createdAt));

            // Set location
            if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                location.setVisibility(View.VISIBLE);
                location.setText(post.getLocation());
            } else {
                location.setVisibility(View.GONE);
            }

            // Set post content
            postContent.setText(post.getCaption());

            UserAvatarLoader.load(userAvatar, postUser != null ? postUser.getAvatarUrl() : null);

            // Load media from Firestore
            loadPostMedia(post.getId());

            // Set engagement counts
            likeCount.setText(String.valueOf(post.getLikeCount()));
            commentCount.setText(String.valueOf(post.getCommentCount()));
            shareCount.setText(String.valueOf(post.getShareCount()));

            // Update like icon based on liked state
            updateLikeIcon(post);

            // Update bookmark icon based on bookmark state
            updateBookmarkIcon(post);

            // Setup more options click listener
            ImageView moreOptions = itemView.findViewById(R.id.post_more_options);
            String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
            
            // Chỉ hiển thị dấu ba chấm hoặc chỉ cho phép sửa/xóa nếu là bài viết của mình
            if (currentUserId != null && currentUserId.equals(post.getUserId())) {
                moreOptions.setVisibility(View.VISIBLE);
                moreOptions.setOnClickListener(v -> {
                    android.widget.PopupMenu popup = new android.widget.PopupMenu(context, v);
                    popup.getMenu().add("Chỉnh sửa");
                    popup.getMenu().add("Xóa");
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getTitle().equals("Chỉnh sửa")) {
                            if (actionListener != null) actionListener.onEditPostClicked(post);
                        } else if (item.getTitle().equals("Xóa")) {
                            if (actionListener != null) actionListener.onDeletePostClicked(post);
                        }
                        return true;
                    });
                    popup.show();
                });
            } else {
                moreOptions.setVisibility(View.GONE);
            }

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
                // Toggle bookmark state locally for immediate feedback
                String postId = post.getId();
                if (postId != null) {
                    if (bookmarkedPostIds.contains(postId)) {
                        bookmarkedPostIds.remove(postId);
                    } else {
                        bookmarkedPostIds.add(postId);
                    }
                }
                updateBookmarkIcon(post);
            });
        }

        private void loadPostMedia(String postId) {
            if (postId == null) {
                postMediaContainer.setVisibility(View.GONE);
                return;
            }
            
            // Đặt tag để kiểm tra xem dữ liệu nạp về có đúng cho item này không (tránh lỗi tái sử dụng View của RecyclerView)
            postMediaContainer.setTag(postId);

            FirebaseFirestore.getInstance().collection(FirebaseManager.COLLECTION_POST_MEDIA)
                    .whereEqualTo("postId", postId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        // Kiểm tra nếu tag vẫn khớp với postId hiện tại của ViewHolder
                        if (!postId.equals(postMediaContainer.getTag())) return;

                        List<PostMedia> mediaList = queryDocumentSnapshots.toObjects(PostMedia.class);
                        
                        if (!mediaList.isEmpty()) {
                            // Sắp xếp: IMAGE trước, VIDEO sau, sau đó theo field 'order'
                            mediaList.sort((m1, m2) -> {
                                boolean isM1Video = "VIDEO".equalsIgnoreCase(m1.getMediaType());
                                boolean isM2Video = "VIDEO".equalsIgnoreCase(m2.getMediaType());
                                
                                if (isM1Video != isM2Video) {
                                    return isM1Video ? 1 : -1;
                                }
                                return Integer.compare(m1.getOrder(), m2.getOrder());
                            });

                            postMediaContainer.setVisibility(View.VISIBLE);
                            postViewPager.setVisibility(View.VISIBLE);
                            
                            PostMediaAdapter mediaAdapter = new PostMediaAdapter(mediaList);
                            postViewPager.setAdapter(mediaAdapter);
                            
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
                        if (postId.equals(postMediaContainer.getTag())) {
                            postMediaContainer.setVisibility(View.GONE);
                        }
                    });
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


