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

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Comment;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.MockDataGenerator;
import com.example.social_app.utils.UserAvatarLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView Adapter for displaying comments.
 * Supports nested replies with indentation.
 * Handles like, reply, and view more replies interactions.
 * Facebook-like comment display with proper UI.
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments;
    private Context context;
    private OnCommentActionListener actionListener;
    private final Set<String> likedCommentIds = new HashSet<>();
    private static final String TAG = "CommentAdapter";

    /**
     * Interface for handling comment interactions.
     */
    public interface OnCommentActionListener {
        void onLikeClicked(Comment comment, int position);
        void onReplyClicked(Comment comment);
        void onViewMoreRepliesClicked(Comment comment);
        void onCommentLongPressed(Comment comment, int position);
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
            String preview = comments.size() > 0 ? comments.get(0).getContent() : "none";
            if (preview != null && preview.length() > 30) {
                preview = preview.substring(0, 30) + "...";
            }
            android.util.Log.d("CommentAdapter", "  First comment: " + preview);
        } else {
            this.comments = new ArrayList<>();
            android.util.Log.w("CommentAdapter", "setComments() called with null, creating empty list");
        }
        notifyDataSetChanged();
        android.util.Log.d("CommentAdapter", "✅ notifyDataSetChanged() called. RecyclerView will refresh with " + this.comments.size() + " items");
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
        android.util.Log.d("CommentAdapter", "═══ onCreateViewHolder() CALLED ═══");
        android.util.Log.d("CommentAdapter", "Parent: " + parent.getClass().getSimpleName());
        android.util.Log.d("CommentAdapter", "Parent width: " + parent.getWidth() + "px");

        View view = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);

        android.util.Log.d("CommentAdapter", "View inflated: " + view.getClass().getSimpleName());
        android.util.Log.d("CommentAdapter", "View width: " + view.getWidth() + "px");
        android.util.Log.d("CommentAdapter", "View height: " + view.getHeight() + "px");
        android.util.Log.d("CommentAdapter", "═══ ViewHolder created ═══");

        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        android.util.Log.d("CommentAdapter", "onBindViewHolder() at position " + position);
        if (position >= 0 && position < comments.size()) {
            Comment comment = comments.get(position);
            holder.bind(comment, position);
            android.util.Log.d("CommentAdapter", "✅ Binding complete for position " + position);
        } else {
            android.util.Log.w("CommentAdapter", "❌ Invalid position: " + position);
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
        private TextView viewMoreReplies;
        private View mediaContainer;
        private ImageView mediaImage;
        private ImageView gifIndicator;

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

            // Media views
            mediaContainer = itemView.findViewById(R.id.comment_media_container);
            mediaImage = itemView.findViewById(R.id.comment_media_image);
            gifIndicator = itemView.findViewById(R.id.comment_gif_indicator);

            // Interaction views
            likeCount = itemView.findViewById(R.id.comment_like_count);
            replyCount = itemView.findViewById(R.id.comment_reply_count);
            likeButton = itemView.findViewById(R.id.comment_like_button);
            replyButton = itemView.findViewById(R.id.comment_reply_button);

            // View more replies
            viewMoreReplies = itemView.findViewById(R.id.view_more_replies);
            android.util.Log.d("CommentAdapter", "✅ onCreateViewHolder() - item view created");
        }

        public void bind(Comment comment, int position) {
            if (comment == null) {
                Log.e(TAG, "Comment is null at position " + position);
                return;
            }

            String userName = MockDataGenerator.getUserDisplayName(comment.getUserId());
            String text = comment.getContent() == null ? "" : comment.getContent();
            android.util.Log.d("CommentAdapter", "┌─── bind() START position=" + position + " ───┐");
            android.util.Log.d("CommentAdapter", "│ User: " + userName);
            android.util.Log.d("CommentAdapter", "│ Text: " + (text.length() > 30 ? text.substring(0, 30) + "..." : text));

            // === BASIC COMMENT INFO ===
            bindUserInfo(userName, comment.getCreatedAt() != null ? comment.getCreatedAt().getTime() : System.currentTimeMillis());
            android.util.Log.d("CommentAdapter", "│ ✅ bindUserInfo done");

            bindCommentText(text);
            android.util.Log.d("CommentAdapter", "│ ✅ bindCommentText done");

            bindCommentMedia(comment);
            android.util.Log.d("CommentAdapter", "│ ✅ bindCommentMedia done");

            bindUserAvatar(comment, userName);
            android.util.Log.d("CommentAdapter", "│ ✅ bindUserAvatar done");

            // === OPTIONAL: VERIFIED BADGE ===
            bindVerifiedBadge(false);
            android.util.Log.d("CommentAdapter", "│ ✅ bindVerifiedBadge done");

            // === OPTIONAL: LOCATION ===
            bindLocation(null);
            android.util.Log.d("CommentAdapter", "│ ✅ bindLocation done");

            // === INTERACTIONS ===
            bindLikeInteraction(comment, position);
            android.util.Log.d("CommentAdapter", "│ ✅ bindLikeInteraction done");

            bindReplyInteraction(comment, position);
            android.util.Log.d("CommentAdapter", "│ ✅ bindReplyInteraction done");

            bindMoreReplies(comment);
            android.util.Log.d("CommentAdapter", "│ ✅ bindMoreReplies done");

            // === LONG PRESS FOR OPTIONS ===
            bindLongPressListener(comment, position);
            android.util.Log.d("CommentAdapter", "│ ✅ bindLongPressListener done");
            android.util.Log.d("CommentAdapter", "└─── bind() END ───┘");
        }

        private void bindUserInfo(String userName, Long timestamp) {
            if (username != null) {
                username.setText(userName);
                username.setVisibility(View.VISIBLE);
            }
            if (timestamp != null && this.timestamp != null) {
                String timeStr = formatTimestamp(timestamp);
                this.timestamp.setText(timeStr);
                this.timestamp.setVisibility(View.VISIBLE);
            }
        }

        private void bindCommentText(String text) {
            if (commentText != null) {
                if (text == null || text.isEmpty()) {
                    commentText.setVisibility(View.GONE);
                } else {
                    commentText.setText(text);
                    commentText.setVisibility(View.VISIBLE);
                }
            }
        }

        private void bindCommentMedia(Comment comment) {
            if (mediaContainer == null || mediaImage == null) return;

            String mediaUrl = comment.getMediaUrl();
            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                mediaContainer.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(mediaUrl)
                        .centerCrop()
                        .into(mediaImage);

                if (gifIndicator != null) {
                    gifIndicator.setVisibility("gif".equals(comment.getMediaType()) ? View.VISIBLE : View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }
        }

        private void bindUserAvatar(Comment comment, String userName) {
            if (avatar != null) {
                User user = MockDataGenerator.getUserById(comment.getUserId());
                UserAvatarLoader.load(avatar, user != null ? user.getAvatarUrl() : null);
                avatar.setContentDescription(
                        context.getString(R.string.user_avatar) + " - " + userName
                );
            }
        }

        private void bindVerifiedBadge(boolean isVerified) {
            if (verifiedBadge != null) {
                verifiedBadge.setVisibility(isVerified ? View.VISIBLE : View.GONE);
            }
        }

        private void bindLocation(String locationText) {
            if (location != null) {
                if (locationText != null && !locationText.isEmpty()) {
                    location.setText(locationText);
                    location.setVisibility(View.VISIBLE);
                } else {
                    location.setVisibility(View.GONE);
                }
            }
        }

        private void bindLikeInteraction(Comment comment, int position) {
            // Like count
            if (likeCount != null) {
                long count = comment.getLikeCount();
                likeCount.setText(count > 0 ? String.valueOf(count) : "");
                likeCount.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }

            // Like button
            if (likeButton != null) {
                boolean isLiked = likedCommentIds.contains(comment.getId());
                likeButton.setSelected(isLiked);
                likeButton.setImageResource(
                        isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart
                );
                likeButton.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onLikeClicked(comment, position);
                        if (isLiked) {
                            likedCommentIds.remove(comment.getId());
                        } else {
                            likedCommentIds.add(comment.getId());
                        }
                        updateLikeState(!isLiked);
                    }
                });
                likeButton.setContentDescription(isLiked ? "Unlike" : "Like");
            }
        }

        private void bindReplyInteraction(Comment comment, int position) {
            if (replyButton != null) {
                replyButton.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onReplyClicked(comment);
                    }
                });
                replyButton.setContentDescription("Reply to comment");
            }

            if (replyCount != null) {
                int replyCountValue = countReplies(comment.getId());
                if (replyCountValue > 0) {
                    replyCount.setText(String.valueOf(replyCountValue));
                    replyCount.setVisibility(View.VISIBLE);
                } else {
                    replyCount.setVisibility(View.GONE);
                }
            }
        }

        private void bindMoreReplies(Comment comment) {
            if (viewMoreReplies != null) {
                int replyCountValue = countReplies(comment.getId());

                if (replyCountValue > 0) {
                    viewMoreReplies.setVisibility(View.VISIBLE);
                    String text = replyCountValue + " repl" + (replyCountValue > 1 ? "ies" : "y");
                    viewMoreReplies.setText(text);
                    viewMoreReplies.setOnClickListener(v -> {
                        if (actionListener != null) {
                            actionListener.onViewMoreRepliesClicked(comment);
                        }
                    });
                } else {
                    viewMoreReplies.setVisibility(View.GONE);
                }
            }
        }

        private int countReplies(String commentId) {
            if (commentId == null) {
                return 0;
            }
            int count = 0;
            for (Comment item : comments) {
                if (commentId.equals(item.getParentId())) {
                    count++;
                }
            }
            return count;
        }

        private void bindLongPressListener(Comment comment, int position) {
            itemView.setOnLongClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCommentLongPressed(comment, position);
                }
                return true;
            });
        }

        private void updateLikeState(boolean isNowLiked) {
            if (likeButton != null) {
                likeButton.setSelected(isNowLiked);
                likeButton.setImageResource(
                        isNowLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart
                );
            }
        }

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




