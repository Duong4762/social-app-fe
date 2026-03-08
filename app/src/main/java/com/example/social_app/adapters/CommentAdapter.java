package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.models.Comment;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying comments.
 * Supports nested replies with indentation.
 * Handles like, reply, and view more replies interactions.
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments;
    private Context context;
    private OnCommentActionListener actionListener;

    /**
     * Interface for handling comment interactions.
     */
    public interface OnCommentActionListener {
        void onLikeClicked(Comment comment, int position);
        void onReplyClicked(Comment comment);
        void onViewMoreRepliesClicked(Comment comment);
    }

    /**
     * Constructor for CommentAdapter.
     *
     * @param context Android context
     * @param actionListener Listener for comment actions
     */
    public CommentAdapter(Context context, OnCommentActionListener actionListener) {
        this.context = context;
        this.comments = new ArrayList<>();
        this.actionListener = actionListener;
    }

    /**
     * Updates the adapter with a new list of comments.
     * @param comments New list of comments
     */
    public void setComments(List<Comment> comments) {
        if (comments != null) {
            this.comments = new ArrayList<>(comments);
            android.util.Log.d("CommentAdapter", "setComments() called with " + this.comments.size() + " comments");
        } else {
            this.comments = new ArrayList<>();
            android.util.Log.w("CommentAdapter", "setComments() called with null, creating empty list");
        }
        notifyDataSetChanged();
        android.util.Log.d("CommentAdapter", "notifyDataSetChanged() called. RecyclerView will refresh");
    }

    /**
     * Adds a single comment to the top of the list.
     * @param comment Comment to add
     */
    public void addComment(Comment comment) {
        this.comments.add(0, comment);
        notifyItemInserted(0);
    }

    /**
     * Removes a comment at the specified position.
     * @param position Position to remove
     */
    public void removeComment(int position) {
        if (position >= 0 && position < comments.size()) {
            this.comments.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * Updates a comment at the specified position.
     * @param position Position to update
     * @param comment Updated comment
     */
    public void updateComment(int position, Comment comment) {
        if (position >= 0 && position < comments.size()) {
            this.comments.set(position, comment);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.util.Log.d("CommentAdapter", "onCreateViewHolder() called");
        View view = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        if (position < comments.size()) {
            Comment comment = comments.get(position);
            android.util.Log.d("CommentAdapter", "onBindViewHolder() at position " + position + ": " + comment.getText().substring(0, Math.min(30, comment.getText().length())) + "...");
            holder.bind(comment, position);
        }
    }

    @Override
    public int getItemCount() {
        int count = comments.size();
        android.util.Log.d("CommentAdapter", "getItemCount() returning: " + count);
        return count;
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private ImageView verifiedBadge;
        private TextView username;
        private TextView commentText;
        private TextView timestamp;
        private TextView location;
        private TextView likeCount;
        private TextView replyCount;
        private ImageButton likeButton;
        private ImageButton replyButton;
        private ImageButton shareButton;
        private ImageButton bookmarkButton;
        private TextView viewMoreReplies;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            // Basic views
            avatar = itemView.findViewById(R.id.comment_avatar);
            username = itemView.findViewById(R.id.comment_username);
            commentText = itemView.findViewById(R.id.comment_text);
            timestamp = itemView.findViewById(R.id.comment_timestamp);

            // Optional views
            verifiedBadge = itemView.findViewById(R.id.verified_badge);
            location = itemView.findViewById(R.id.comment_location);

            // Interaction views
            likeCount = itemView.findViewById(R.id.comment_like_count);
            replyCount = itemView.findViewById(R.id.comment_reply_count);
            likeButton = itemView.findViewById(R.id.comment_like_button);
            replyButton = itemView.findViewById(R.id.comment_reply_button);
            shareButton = itemView.findViewById(R.id.comment_share_button);
            bookmarkButton = itemView.findViewById(R.id.comment_bookmark_button);

            // View more replies
            viewMoreReplies = itemView.findViewById(R.id.view_more_replies);
        }

        /**
         * Binds comment data to ViewHolder views.
         * @param comment Comment to bind
         * @param position Position in adapter
         */
        public void bind(Comment comment, int position) {
            String userName = comment.getUser().getName();

            // === BASIC COMMENT INFO ===
            username.setText(userName);
            commentText.setText(comment.getText());
            timestamp.setText(formatTimestamp(comment.getTimestamp()));

            // Set avatar
            avatar.setImageResource(R.drawable.avatar_placeholder);
            avatar.setContentDescription(
                    context.getString(R.string.user_avatar) + " - " + userName
            );

            // === OPTIONAL: VERIFIED BADGE ===
            if (comment.getUser().isVerified()) {
                verifiedBadge.setVisibility(View.VISIBLE);
                verifiedBadge.setContentDescription(userName + " is verified");
            } else {
                verifiedBadge.setVisibility(View.GONE);
            }

            // === OPTIONAL: LOCATION ===
            if (comment.getLocation() != null && !comment.getLocation().isEmpty()) {
                location.setText(comment.getLocation());
                // Cast parent to View to access setVisibility()
                View locationSection = (View) location.getParent();
                if (locationSection != null) {
                    locationSection.setVisibility(View.VISIBLE);
                }
            } else {
                // Cast parent to View to access setVisibility()
                View locationSection = (View) location.getParent();
                if (locationSection != null) {
                    locationSection.setVisibility(View.GONE);
                }
            }

            // === LIKE INTERACTION ===
            likeCount.setText(String.valueOf(comment.getLikeCount()));
            boolean isLiked = comment.isLiked();
            likeButton.setSelected(isLiked);
            likeButton.setImageResource(
                    isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart
            );
            likeButton.setContentDescription(
                    (isLiked ? "Unlike" : "Like") + " comment by " + userName
            );
            likeButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onLikeClicked(comment, position);
                }
            });

            // === REPLY INTERACTION ===
            if (comment.getReplies() != null) {
                int replyCountValue = comment.getReplies().size();
                if (replyCountValue > 0) {
                    replyCount.setText(String.valueOf(replyCountValue));
                    replyCount.setVisibility(View.VISIBLE);
                } else {
                    replyCount.setVisibility(View.GONE);
                }
            } else {
                replyCount.setVisibility(View.GONE);
            }

            replyButton.setContentDescription("Reply to " + userName);
            replyButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReplyClicked(comment);
                }
            });

            // === SHARE BUTTON ===
            shareButton.setContentDescription("Share comment by " + userName);
            shareButton.setOnClickListener(v -> {
                // TODO: Implement share functionality
            });

            // === BOOKMARK BUTTON ===
            bookmarkButton.setContentDescription("Bookmark comment");
            bookmarkButton.setOnClickListener(v -> {
                boolean isBookmarked = v.isSelected();
                v.setSelected(!isBookmarked);
                bookmarkButton.setImageResource(
                        !isBookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark
                );
            });

            // === VIEW MORE REPLIES ===
            if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                viewMoreReplies.setVisibility(View.VISIBLE);
                int replyCountValue = comment.getReplies().size();
                viewMoreReplies.setText(replyCountValue + " repl" + (replyCountValue > 1 ? "ies" : "y"));
                viewMoreReplies.setContentDescription(
                        replyCountValue + " repl" + (replyCountValue > 1 ? "ies" : "y") + " to this comment"
                );
            } else if (comment.isHasMoreReplies()) {
                viewMoreReplies.setVisibility(View.VISIBLE);
                viewMoreReplies.setText(R.string.view_more_replies);
                viewMoreReplies.setContentDescription(context.getString(R.string.view_more_replies));
                viewMoreReplies.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onViewMoreRepliesClicked(comment);
                    }
                });
            } else {
                viewMoreReplies.setVisibility(View.GONE);
            }
        }

        /**
         * Formats timestamp into human-readable format.
         * @param timestamp Timestamp in milliseconds
         * @return Formatted time string (e.g., "2 HOURS AGO")
         */
        private String formatTimestamp(long timestamp) {
            long currentTime = System.currentTimeMillis();
            long difference = currentTime - timestamp;

            long minute = 60000;
            long hour = minute * 60;
            long day = hour * 24;

            if (difference < minute) {
                return "just now";
            } else if (difference < hour) {
                long minutes = difference / minute;
                return minutes + " MIN" + (minutes > 1 ? "S" : "") + " AGO";
            } else if (difference < day) {
                long hours = difference / hour;
                return hours + " HOUR" + (hours > 1 ? "S" : "") + " AGO";
            } else {
                long days = difference / day;
                return days + " DAY" + (days > 1 ? "S" : "") + " AGO";
            }
        }
    }
}




