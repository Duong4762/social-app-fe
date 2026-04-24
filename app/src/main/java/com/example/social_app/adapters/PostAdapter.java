package com.example.social_app.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.PostMedia;
import com.example.social_app.data.model.User;
import com.example.social_app.data.model.Post;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.MockDataGenerator;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
    private String currentUserAvatarUrl;
    private boolean useSearchLayout = false;

    public interface OnPostActionListener {
        void onUserClicked(String userId);
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post);
        default void onShareClicked(Post post) {}
        default void onBookmarkClicked(Post post) {}
        void onComposerPostClicked(String content);
        void onComposerClicked();  // NEW: Handle composer clicks to open new post creation
        void onComposerImageClicked(); // NEW: Handle image button clicks to pick image
        void onEditPostClicked(Post post);
        void onDeletePostClicked(Post post);
        void onReportPostClicked(Post post);
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
        notifyDataSetChanged();
    }

    public void setCurrentUserAvatarUrl(String avatarUrl) {
        this.currentUserAvatarUrl = avatarUrl;
        notifyItemChanged(0);
    }

    public void setUseSearchLayout(boolean useSearchLayout) {
        this.useSearchLayout = useSearchLayout;
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
        if (useSearchLayout) {
            return VIEW_TYPE_POST;
        }
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
            int postPosition = useSearchLayout ? position : position - 1;
            Post post = posts.get(postPosition);
            postHolder.bind(post, postPosition);
        }
    }

    @Override
    public int getItemCount() {
        // In search/profile layout we show only posts (without composer).
        return useSearchLayout ? posts.size() : posts.size() + 1;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof PostViewHolder) {
            ((PostViewHolder) holder).clearRealtimeLikeListeners();
        }
        super.onViewRecycled(holder);
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
            UserAvatarLoader.load(composerAvatar, currentUserAvatarUrl);
        }
    }

    /**
     * ViewHolder for individual post items in the feed.
     */
    private class PostViewHolder extends RecyclerView.ViewHolder {
        private ImageView userAvatar;
        private TextView username;
        private TextView timestamp;
        private TextView postContent;
        private ImageView likeIcon;
        private TextView likeCount;
        private ImageView commentIcon;
        private TextView commentCount;
        private LinearLayout likeContainer;
        private LinearLayout commentContainer;
        private FrameLayout postMediaContainer;
        private Post boundPost;
        private int boundPosition;
        private ListenerRegistration likeCountRegistration;
        private ListenerRegistration likeStatusRegistration;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.post_user_avatar);
            username = itemView.findViewById(R.id.post_username);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            postContent = itemView.findViewById(R.id.post_content);
            likeIcon = itemView.findViewById(R.id.post_like_icon);
            likeCount = itemView.findViewById(R.id.post_like_count);
            commentIcon = itemView.findViewById(R.id.post_comment_icon);
            commentCount = itemView.findViewById(R.id.post_comment_count);
            likeContainer = itemView.findViewById(R.id.post_like_container);
            commentContainer = itemView.findViewById(R.id.post_comment_container);
            postMediaContainer = itemView.findViewById(R.id.post_media_container);
        }

        void bind(Post post, int position) {
            clearRealtimeLikeListeners();
            this.boundPost = post;
            this.boundPosition = position;
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

            // Set post content
            postContent.setText(post.getCaption());

            UserAvatarLoader.load(userAvatar, postUser != null ? postUser.getAvatarUrl() : null);

            // Load media from Firestore
            loadPostMedia(post.getId(), post);

            // Set engagement counts
            likeCount.setText(String.valueOf(post.getLikeCount()));
            commentCount.setText(String.valueOf(post.getCommentCount()));

            // Update like icon based on liked state
            updateLikeIcon(post);
            setupRealtimeLike(post);

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
                String postId = post.getId();
                if (postId != null) {
                    boolean isLikedNow = toggleLiked(postId);
                    long nextCount = isLikedNow ? post.getLikeCount() + 1 : Math.max(0, post.getLikeCount() - 1);
                    post.setLikeCount(nextCount);
                    likeCount.setText(String.valueOf(nextCount));
                    updateLikeIcon(post);
                }
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
        }

        private void loadPostMedia(String postId, Post post) {
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
                            mediaList.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));
                            postMediaContainer.setVisibility(View.VISIBLE);
                            renderMediaCollage(mediaList, post);
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

        private void setupRealtimeLike(Post post) {
            if (post == null || post.getId() == null) return;

            String postId = post.getId();
            String currentUserId = FirebaseManager.getInstance().getAuth().getUid();

            likeCountRegistration = FirebaseFirestore.getInstance()
                    .collection(FirebaseManager.COLLECTION_POSTS)
                    .document(postId)
                    .addSnapshotListener((snapshot, e) -> {
                        if (e != null || snapshot == null || !snapshot.exists()) return;
                        Long likeCountValue = snapshot.getLong("likeCount");
                        if (likeCountValue == null) likeCountValue = 0L;
                        post.setLikeCount(likeCountValue);
                        likeCount.setText(String.valueOf(likeCountValue));
                    });

            if (currentUserId != null) {
                String likeDocId = postId + "_" + currentUserId;
                likeStatusRegistration = FirebaseFirestore.getInstance()
                        .collection(FirebaseManager.COLLECTION_POST_LIKES)
                        .document(likeDocId)
                        .addSnapshotListener((snapshot, e) -> {
                            if (e != null) return;
                            boolean isLiked = snapshot != null && snapshot.exists();
                            if (isLiked) likedPostIds.add(postId);
                            else likedPostIds.remove(postId);
                            updateLikeIcon(post);
                        });
            }
        }

        private void clearRealtimeLikeListeners() {
            if (likeCountRegistration != null) {
                likeCountRegistration.remove();
                likeCountRegistration = null;
            }
            if (likeStatusRegistration != null) {
                likeStatusRegistration.remove();
                likeStatusRegistration = null;
            }
        }

        private void renderMediaCollage(List<PostMedia> mediaList, Post post) {
            postMediaContainer.removeAllViews();
            if (mediaList == null || mediaList.isEmpty()) {
                postMediaContainer.setVisibility(View.GONE);
                return;
            }
            postMediaContainer.post(() -> {
                int width = postMediaContainer.getWidth();
                if (width <= 0) return;
                int count = mediaList.size();
                int visibleCount = Math.min(4, count);
                int height;
                if (visibleCount == 1) height = width;
                else if (visibleCount == 2) height = width / 2;
                else height = width;

                ViewGroup.LayoutParams lp = postMediaContainer.getLayoutParams();
                lp.height = height;
                postMediaContainer.setLayoutParams(lp);

                if (visibleCount == 1) {
                    postMediaContainer.addView(createTile(post, mediaList.get(0), count > 4 ? count - 4 : 0));
                } else if (visibleCount == 2) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setLayoutParams(new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));
                    row.addView(createWeightedTile(post, mediaList.get(0), 1f, 0));
                    row.addView(createSpacer(2, 0));
                    row.addView(createWeightedTile(post, mediaList.get(1), 1f, 0));
                    postMediaContainer.addView(row);
                } else if (visibleCount == 3) {
                    LinearLayout root = new LinearLayout(context);
                    root.setOrientation(LinearLayout.HORIZONTAL);
                    root.setLayoutParams(new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));

                    LinearLayout left = new LinearLayout(context);
                    left.setOrientation(LinearLayout.VERTICAL);
                    left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    left.addView(createWeightedTile(post, mediaList.get(0), 1f, 0));
                    left.addView(createSpacer(0, 2));
                    left.addView(createWeightedTile(post, mediaList.get(1), 1f, 0));

                    FrameLayout right = new FrameLayout(context);
                    right.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    right.addView(createTile(post, mediaList.get(2), 0));

                    root.addView(left);
                    root.addView(createSpacer(2, 0));
                    root.addView(right);
                    postMediaContainer.addView(root);
                } else {
                    LinearLayout root = new LinearLayout(context);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setLayoutParams(new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));

                    LinearLayout top = new LinearLayout(context);
                    top.setOrientation(LinearLayout.HORIZONTAL);
                    top.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                    ));
                    top.addView(createWeightedTile(post, mediaList.get(0), 1f, 0));
                    top.addView(createSpacer(2, 0));
                    top.addView(createWeightedTile(post, mediaList.get(1), 1f, 0));

                    LinearLayout bottom = new LinearLayout(context);
                    bottom.setOrientation(LinearLayout.HORIZONTAL);
                    bottom.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                    ));
                    bottom.addView(createWeightedTile(post, mediaList.get(2), 1f, 0));
                    bottom.addView(createSpacer(2, 0));
                    int remaining = count > 4 ? (count - 4) : 0;
                    bottom.addView(createWeightedTile(post, mediaList.get(3), 1f, remaining));

                    root.addView(top);
                    root.addView(createSpacer(0, 2));
                    root.addView(bottom);
                    postMediaContainer.addView(root);
                }
            });
        }

        private View createWeightedTile(Post post, PostMedia media, float weight, int moreCount) {
            FrameLayout tile = createTile(post, media, moreCount);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
            tile.setLayoutParams(lp);
            return tile;
        }

        private View createSpacer(int widthPx, int heightPx) {
            View spacer = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    widthPx > 0 ? widthPx : LinearLayout.LayoutParams.MATCH_PARENT,
                    heightPx > 0 ? heightPx : LinearLayout.LayoutParams.MATCH_PARENT
            );
            if (widthPx > 0) lp = new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT);
            if (heightPx > 0) lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            spacer.setLayoutParams(lp);
            return spacer;
        }

        private FrameLayout createTile(Post post, PostMedia media, int moreCount) {
            FrameLayout tile = new FrameLayout(context);
            FrameLayout.LayoutParams tileLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            tile.setLayoutParams(tileLp);

            ImageView image = new ImageView(context);
            image.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(context).load(media.getMediaUrl()).centerCrop().into(image);
            tile.addView(image);

            boolean isVideo = media.getMediaType() != null && media.getMediaType().equalsIgnoreCase("VIDEO");
            if (isVideo) {
                ImageView play = new ImageView(context);
                FrameLayout.LayoutParams playLp = new FrameLayout.LayoutParams(
                        (int) (28 * context.getResources().getDisplayMetrics().density),
                        (int) (28 * context.getResources().getDisplayMetrics().density)
                );
                playLp.gravity = android.view.Gravity.CENTER;
                play.setLayoutParams(playLp);
                play.setImageResource(R.drawable.ic_play);
                play.setColorFilter(Color.WHITE);
                tile.addView(play);
            }

            if (moreCount > 0) {
                TextView overlay = new TextView(context);
                overlay.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
                overlay.setBackgroundColor(0x88000000);
                overlay.setTextColor(Color.WHITE);
                overlay.setTextSize(24f);
                overlay.setText("+" + moreCount);
                overlay.setGravity(android.view.Gravity.CENTER);
                tile.addView(overlay);
            }

            tile.setOnClickListener(v -> showMediaPopupWithActions(post, media));
            return tile;
        }

        private void showMediaPopupWithActions(Post post, PostMedia media) {
            android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_post_media_overlay);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            }

            ImageView imageView = dialog.findViewById(R.id.overlay_fullscreen_image);
            VideoView videoView = dialog.findViewById(R.id.overlay_fullscreen_video);
            ImageView btnClose = dialog.findViewById(R.id.overlay_btn_close);
            ImageView playIcon = dialog.findViewById(R.id.overlay_ic_play_video);
            View likeContainer = dialog.findViewById(R.id.overlay_like_container);
            View commentContainer = dialog.findViewById(R.id.overlay_comment_container);
            ImageView likeIcon = dialog.findViewById(R.id.overlay_like_icon);
            TextView likeCountText = dialog.findViewById(R.id.overlay_like_count);
            TextView commentCountText = dialog.findViewById(R.id.overlay_comment_count);

            long displayedLikeCount;
            try {
                displayedLikeCount = Long.parseLong(this.likeCount.getText().toString());
            } catch (Exception ignored) {
                displayedLikeCount = post.getLikeCount();
            }
            final long[] overlayLikeCount = new long[]{displayedLikeCount};
            final boolean[] overlayLiked = new boolean[]{Boolean.TRUE.equals(this.likeIcon.getTag())};
            updateOverlayLikeUi(likeIcon, likeCountText, overlayLiked[0], overlayLikeCount[0]);
            commentCountText.setText(String.valueOf(post.getCommentCount()));

            boolean isVideo = media.getMediaType() != null && media.getMediaType().equalsIgnoreCase("VIDEO");
            if (isVideo) {
                imageView.setVisibility(View.GONE);
                videoView.setVisibility(View.VISIBLE);
                playIcon.setVisibility(View.VISIBLE);
                MediaController controller = new MediaController(context);
                controller.setAnchorView(videoView);
                videoView.setMediaController(controller);
                videoView.setVideoURI(Uri.parse(media.getMediaUrl()));
                playIcon.setOnClickListener(v -> {
                    playIcon.setVisibility(View.GONE);
                    videoView.start();
                });
                videoView.setOnPreparedListener(mp -> playIcon.setVisibility(View.VISIBLE));
                videoView.setOnCompletionListener(mp -> playIcon.setVisibility(View.VISIBLE));
                videoView.setOnClickListener(v -> {
                    if (videoView.isPlaying()) {
                        videoView.pause();
                        playIcon.setVisibility(View.VISIBLE);
                    } else {
                        videoView.start();
                        playIcon.setVisibility(View.GONE);
                    }
                });
            } else {
                videoView.setVisibility(View.GONE);
                playIcon.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                Glide.with(context).load(media.getMediaUrl()).into(imageView);
            }

            likeContainer.setOnClickListener(v -> {
                // Keep popup UI in sync immediately with current state.
                overlayLiked[0] = !overlayLiked[0];
                if (overlayLiked[0]) {
                    overlayLikeCount[0] = overlayLikeCount[0] + 1;
                } else {
                    overlayLikeCount[0] = Math.max(0, overlayLikeCount[0] - 1);
                }
                updateOverlayLikeUi(likeIcon, likeCountText, overlayLiked[0], overlayLikeCount[0]);
                this.likeCount.setText(String.valueOf(overlayLikeCount[0]));
                this.likeIcon.setTag(overlayLiked[0]);
                if (overlayLiked[0]) {
                    this.likeIcon.setImageResource(R.drawable.ic_heart_filled);
                    this.likeIcon.setColorFilter(context.getResources().getColor(R.color.accent_red, null));
                } else {
                    this.likeIcon.setImageResource(R.drawable.ic_heart);
                    this.likeIcon.clearColorFilter();
                }

                if (actionListener != null && boundPost != null) {
                    actionListener.onLikeClicked(boundPost, boundPosition);
                }
            });
            commentContainer.setOnClickListener(v -> {
                if (actionListener != null && boundPost != null) {
                    actionListener.onCommentClicked(boundPost);
                }
            });

            btnClose.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }

        private void updateOverlayLikeUi(ImageView likeIcon, TextView likeCountText, boolean isLiked, long likeCount) {
            likeCountText.setText(String.valueOf(likeCount));
            if (isLiked) {
                likeIcon.setImageResource(R.drawable.ic_heart_filled);
                likeIcon.setColorFilter(context.getResources().getColor(R.color.accent_red, null));
            } else {
                likeIcon.setImageResource(R.drawable.ic_heart);
                likeIcon.setColorFilter(context.getResources().getColor(android.R.color.white, null));
            }
        }

        /**
         * Updates the like icon visual state.
         */
        private void updateLikeIcon(Post post) {
            boolean isLiked = likedPostIds.contains(post.getId());
            likeIcon.setTag(isLiked);
            if (isLiked) {
                likeIcon.setImageResource(R.drawable.ic_heart_filled);
                likeIcon.setColorFilter(context.getResources().getColor(R.color.accent_red, null));
            } else {
                likeIcon.setImageResource(R.drawable.ic_heart);
                // Reset to default color
                likeIcon.clearColorFilter();
            }
        }

    }
}


