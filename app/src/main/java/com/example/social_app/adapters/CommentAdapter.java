package com.example.social_app.adapters;

import android.content.Context;
import android.util.Log;
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
 * RecyclerView Adapter for displaying comments with multiple view types:
 * - VIEW_TYPE_COMMENT: Regular comment
 * - VIEW_TYPE_REPLY: Nested reply to a comment
 * - VIEW_TYPE_VIEW_MORE: "View more replies" button
 */
public class CommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "CommentAdapter";
    private static final int VIEW_TYPE_COMMENT = 0;
    private static final int VIEW_TYPE_REPLY = 1;

    private List<Object> items; // Mix of Comment objects and special markers
    private Context context;
    private OnCommentActionListener actionListener;

    /**
     * Interface for handling comment interactions.
     */
    public interface OnCommentActionListener {
        void onLikeClicked(Comment comment, int position);
        void onReplyClicked(Comment comment);
        void onViewMoreRepliesClicked(Comment comment);
        void onCommentLongPressed(Comment comment, int position);
    }

    public CommentAdapter(Context context, OnCommentActionListener actionListener) {
        this.context = context;
        this.items = new ArrayList<>();
        this.actionListener = actionListener;
    }

    /**
     * Updates the adapter with a new list of comments.
     * Flattens the comment structure: adds comments, then their replies, then "view more" markers
     */
    public void setComments(List<Comment> comments) {
        this.items = new ArrayList<>();

        if (comments != null) {
            for (Comment comment : comments) {
                // Add the main comment
                items.add(comment);

                // Add replies if they exist
                if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                    for (Comment reply : comment.getReplies()) {
                        reply.setIsReply(true);
                        items.add(reply);
                    }
                }
            }
        }

        Log.d(TAG, "setComments() called with " + (comments != null ? comments.size() : 0) + " comments, total items: " + items.size());
        notifyDataSetChanged();
    }

    /**
     * Adds more comments to the end of the list
     */
    public void addMoreComments(List<Comment> moreComments) {
        int previousSize = items.size();

        if (moreComments != null) {
            for (Comment comment : moreComments) {
                items.add(comment);

                if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                    for (Comment reply : comment.getReplies()) {
                        reply.setIsReply(true);
                        items.add(reply);
                    }
                }
            }
        }

        notifyItemRangeInserted(previousSize, items.size() - previousSize);
    }

    /**
     * Removes a comment at the specified position
     */
    public void removeComment(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= items.size()) {
            return VIEW_TYPE_COMMENT;
        }

        Comment comment = (Comment) items.get(position);
        return comment.isReply() ? VIEW_TYPE_REPLY : VIEW_TYPE_COMMENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);

        switch (viewType) {
            case VIEW_TYPE_COMMENT:
                return new CommentViewHolder(inflater.inflate(R.layout.item_comment, parent, false));
            case VIEW_TYPE_REPLY:
                return new ReplyViewHolder(inflater.inflate(R.layout.item_reply, parent, false));
            default:
                return new CommentViewHolder(inflater.inflate(R.layout.item_comment, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position >= items.size()) return;

        Object item = items.get(position);

        if (holder instanceof CommentViewHolder && item instanceof Comment) {
            ((CommentViewHolder) holder).bind((Comment) item, position);
        } else if (holder instanceof ReplyViewHolder && item instanceof Comment) {
            ((ReplyViewHolder) holder).bind((Comment) item, position);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for regular comments
     */
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
        private TextView viewMoreReplies;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.comment_avatar);
            username = itemView.findViewById(R.id.comment_username);
            commentText = itemView.findViewById(R.id.comment_text);
            timestamp = itemView.findViewById(R.id.comment_timestamp);
            verifiedBadge = itemView.findViewById(R.id.verified_badge);
            location = itemView.findViewById(R.id.comment_location);
            likeCount = itemView.findViewById(R.id.comment_like_count);
            replyCount = itemView.findViewById(R.id.comment_reply_count);
            likeButton = itemView.findViewById(R.id.comment_like_button);
            replyButton = itemView.findViewById(R.id.comment_reply_button);
            viewMoreReplies = itemView.findViewById(R.id.view_more_replies);
        }

        public void bind(Comment comment, int position) {
            if (comment == null || comment.getUser() == null) {
                Log.e(TAG, "Comment or User is null at position " + position);
                return;
            }

            // Basic info
            username.setText(comment.getUser().getName());
            commentText.setText(comment.getText());
            timestamp.setText(formatTime(comment.getTimestamp()));

            // Optional info
            if (comment.getLocation() != null && !comment.getLocation().isEmpty()) {
                location.setText(comment.getLocation());
                location.setVisibility(View.VISIBLE);
            } else {
                location.setVisibility(View.GONE);
            }

            if (comment.getUser().isVerified()) {
                verifiedBadge.setVisibility(View.VISIBLE);
            } else {
                verifiedBadge.setVisibility(View.GONE);
            }

            // Interaction counts
            likeCount.setText(String.valueOf(comment.getLikeCount()));
            if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                replyCount.setText(String.valueOf(comment.getReplies().size()));
                replyCount.setVisibility(View.VISIBLE);
            } else {
                replyCount.setVisibility(View.GONE);
            }

            // View more replies
            if (comment.hasMoreReplies()) {
                viewMoreReplies.setVisibility(View.VISIBLE);
                int totalReplies = (comment.getReplies() != null ? comment.getReplies().size() : 0) + 1;
                viewMoreReplies.setText("View " + totalReplies + " more " + (totalReplies == 1 ? "reply" : "replies"));
                viewMoreReplies.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onViewMoreRepliesClicked(comment);
                    }
                });
            } else {
                viewMoreReplies.setVisibility(View.GONE);
            }

            // Button listeners
            likeButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onLikeClicked(comment, position);
                }
            });

            replyButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReplyClicked(comment);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCommentLongPressed(comment, position);
                }
                return true;
            });
        }
    }

    /**
     * ViewHolder for reply comments (nested under main comments)
     */
    class ReplyViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private ImageView verifiedBadge;
        private TextView username;
        private TextView replyText;
        private TextView timestamp;
        private TextView likeCount;
        private ImageButton likeButton;
        private ImageButton replyButton;

        public ReplyViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.reply_avatar);
            username = itemView.findViewById(R.id.reply_username);
            replyText = itemView.findViewById(R.id.reply_text);
            timestamp = itemView.findViewById(R.id.reply_timestamp);
            verifiedBadge = itemView.findViewById(R.id.reply_verified_badge);
            likeCount = itemView.findViewById(R.id.reply_like_count);
            likeButton = itemView.findViewById(R.id.reply_like_button);
            replyButton = itemView.findViewById(R.id.reply_reply_button);
        }

        public void bind(Comment reply, int position) {
            if (reply == null || reply.getUser() == null) {
                Log.e(TAG, "Reply or User is null at position " + position);
                return;
            }

            username.setText(reply.getUser().getName());
            replyText.setText(reply.getText());
            timestamp.setText(formatTime(reply.getTimestamp()));

            if (reply.getUser().isVerified()) {
                verifiedBadge.setVisibility(View.VISIBLE);
            } else {
                verifiedBadge.setVisibility(View.GONE);
            }

            likeCount.setText(String.valueOf(reply.getLikeCount()));

            likeButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onLikeClicked(reply, position);
                }
            });

            replyButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReplyClicked(reply);
                }
            });
        }
    }

    /**
     * Format timestamp to relative time (e.g., "2 hours ago")
     */
    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        } else {
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        }
    }
}
