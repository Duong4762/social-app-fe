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

import com.example.social_app.R;
import com.example.social_app.models.Post;
import com.example.social_app.utils.MockDataGenerator;

import java.util.ArrayList;
import java.util.List;

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

    public interface OnPostActionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post);
        void onShareClicked(Post post);
        void onBookmarkClicked(Post post);
        void onComposerPostClicked(String content);
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

        ComposerViewHolder(@NonNull View itemView) {
            super(itemView);
            // Composer layout is managed by the fragment
            // This ViewHolder is kept for future composer functionality
        }

        void bind() {
            // Binding logic for composer view can be implemented here
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

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.post_user_avatar);
            username = itemView.findViewById(R.id.post_username);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            location = itemView.findViewById(R.id.post_location);
            postContent = itemView.findViewById(R.id.post_content);
            postImage = itemView.findViewById(R.id.post_image);
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
        }

        void bind(Post post, int position) {
            // Set user info
            username.setText(post.getUser().getName());

            // Set timestamp
            timestamp.setText(MockDataGenerator.getTimeDifferenceString(post.getTimestamp()));

            // Set location
            if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                location.setText(post.getLocation());
                location.setVisibility(View.VISIBLE);
            } else {
                location.setVisibility(View.GONE);
            }

            // Set post content
            postContent.setText(post.getContent());

            // Set post image (in a real app, use Glide/Picasso for image loading)
            // For now, use a placeholder
            postImage.setImageResource(R.drawable.bg_nav_item_selected);

            // Set engagement counts
            likeCount.setText(String.valueOf(post.getLikeCount()));
            commentCount.setText(String.valueOf(post.getCommentCount()));
            shareCount.setText(String.valueOf(post.getShareCount()));

            // Update like icon based on liked state
            updateLikeIcon(post);

            // Set up click listeners
            likeContainer.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onLikeClicked(post, position);
                }
            });

            commentContainer.setOnClickListener(v -> {
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
        }

        /**
         * Updates the like icon visual state.
         */
        private void updateLikeIcon(Post post) {
            if (post.isLiked()) {
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


