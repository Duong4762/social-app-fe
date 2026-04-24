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
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.MockDataGenerator;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.firestore.FirebaseFirestore;

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
        void onUserClicked(String userId);
        void onLikeClicked(Comment comment, int position);
        void onReplyClicked(Comment comment, String userName);
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
        this.comments.clear();
        if (comments != null) {
            // Lấy tất cả bình luận gốc
            List<Comment> rootComments = new ArrayList<>();
            for (Comment c : comments) {
                if (c.getParentId() == null) {
                    rootComments.add(c);
                }
            }
            
            // Sắp xếp bình luận gốc: Mới nhất lên đầu
            rootComments.sort((c1, c2) -> {
                long t1 = c1.getCreatedAt() != null ? c1.getCreatedAt().getTime() : 0;
                long t2 = c2.getCreatedAt() != null ? c2.getCreatedAt().getTime() : 0;
                return Long.compare(t2, t1);
            });

            // Xây dựng cây bình luận
            for (Comment root : rootComments) {
                this.comments.add(root);
                addRepliesRecursively(root.getId(), comments, 1);
            }
        }
        notifyDataSetChanged();
    }

    private void addRepliesRecursively(String parentId, List<Comment> allComments, int level) {
        List<Comment> replies = new ArrayList<>();
        for (Comment c : allComments) {
            if (parentId.equals(c.getParentId())) {
                replies.add(c);
            }
        }
        
        // Sắp xếp reply: Cũ nhất lên đầu để đọc theo luồng
        replies.sort((c1, c2) -> {
            long t1 = c1.getCreatedAt() != null ? c1.getCreatedAt().getTime() : 0;
            long t2 = c2.getCreatedAt() != null ? c2.getCreatedAt().getTime() : 0;
            return Long.compare(t1, t2);
        });

        for (Comment reply : replies) {
            this.comments.add(reply);
            addRepliesRecursively(reply.getId(), allComments, level + 1);
        }
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
        private TextView username;
        private TextView commentText;
        private TextView timestamp;
        private TextView likeCount;
        private TextView likeButtonText;
        private TextView replyButtonText;
        private ImageView likeIcon;
        private View commentBubble;
        private View mediaContainer;
        private ImageView mediaImage;
        private ImageView gifIndicator;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.comment_avatar);
            username = itemView.findViewById(R.id.comment_username);
            commentText = itemView.findViewById(R.id.comment_text);
            timestamp = itemView.findViewById(R.id.comment_timestamp);
            commentBubble = itemView.findViewById(R.id.comment_bubble);

            // Media views
            mediaContainer = itemView.findViewById(R.id.comment_media_container);
            mediaImage = itemView.findViewById(R.id.comment_media_image);
            gifIndicator = itemView.findViewById(R.id.comment_gif_indicator);

            // Interaction views
            likeCount = itemView.findViewById(R.id.comment_like_count);
            likeButtonText = itemView.findViewById(R.id.comment_like_button_text);
            replyButtonText = itemView.findViewById(R.id.comment_reply_button_text);
            likeIcon = itemView.findViewById(R.id.comment_like_icon);
        }

        public void bind(Comment comment, int position) {
            if (comment == null) return;

            // Xử lý thụt lề cho reply
            int level = 0;
            String pid = comment.getParentId();
            if (pid != null) {
                level = 1;
                String currentPid = pid;
                int maxDepth = 4; // Facebook thường không thụt lề quá sâu
                int depth = 1;
                while (currentPid != null && depth < maxDepth) {
                    boolean foundParent = false;
                    for (Comment c : comments) {
                        if (c.getId().equals(currentPid)) {
                            currentPid = c.getParentId();
                            if (currentPid != null) {
                                depth++;
                                level = depth;
                            }
                            foundParent = true;
                            break;
                        }
                    }
                    if (!foundParent) break;
                }
            }
            
            int marginStart = (int) (level * 36 * context.getResources().getDisplayMetrics().density);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (params != null) {
                params.leftMargin = marginStart;
                itemView.setLayoutParams(params);
            }

            // Load user data
            User user = MockDataGenerator.getUserById(comment.getUserId());
            long createdAt = comment.getCreatedAt() != null ? comment.getCreatedAt().getTime() : System.currentTimeMillis();

            if (user != null) {
                bindUserInfo(user.getFullName(), createdAt);
                UserAvatarLoader.load(avatar, user.getAvatarUrl());
            } else {
                username.setText("Loading...");
                FirebaseFirestore.getInstance().collection(FirebaseManager.COLLECTION_USERS)
                        .document(comment.getUserId())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (getAdapterPosition() == position && documentSnapshot.exists()) {
                                String name = documentSnapshot.getString("fullName");
                                String avatarUrl = documentSnapshot.getString("avatarUrl");
                                bindUserInfo(name != null ? name : "Unknown User", createdAt);
                                UserAvatarLoader.load(avatar, avatarUrl);
                            }
                        });
            }

            View.OnClickListener userClickListener = v -> {
                if (actionListener != null && comment.getUserId() != null && !comment.getUserId().trim().isEmpty()) {
                    actionListener.onUserClicked(comment.getUserId());
                }
            };
            if (avatar != null) avatar.setOnClickListener(userClickListener);
            if (username != null) username.setOnClickListener(userClickListener);

            if (commentText != null) {
                String mediaType = comment.getMediaType();
                String content = comment.getContent();
                boolean hasTextContent = content != null && !content.trim().isEmpty();
                boolean imageOnlyComment = "image".equalsIgnoreCase(mediaType)
                        && comment.getMediaUrl() != null
                        && !comment.getMediaUrl().isEmpty()
                        && !hasTextContent;
                if (imageOnlyComment) {
                    commentText.setText("");
                    commentText.setVisibility(View.GONE);
                } else {
                    commentText.setVisibility(View.VISIBLE);
                    commentText.setText(content != null ? content : "");
                }
            }
            bindCommentMedia(comment);
            bindLikeInteraction(comment, position);
            
            if (replyButtonText != null) {
                replyButtonText.setOnClickListener(v -> {
                    if (actionListener != null) {
                        String name = username.getText().toString();
                        actionListener.onReplyClicked(comment, name);
                    }
                });
            }

            itemView.setOnLongClickListener(v -> {
                if (actionListener != null) actionListener.onCommentLongPressed(comment, position);
                return true;
            });
        }

        private void bindUserInfo(String userName, long timestampMillis) {
            if (username != null) username.setText(userName);
            if (timestamp != null) {
                timestamp.setText(formatTimestamp(context, timestampMillis));
            }
        }

        private void bindCommentMedia(Comment comment) {
            if (mediaContainer == null || mediaImage == null) return;
            String mediaUrl = comment.getMediaUrl();
            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                mediaContainer.setVisibility(View.VISIBLE);
                Glide.with(context).load(mediaUrl).fitCenter().into(mediaImage);
                if (gifIndicator != null) {
                    gifIndicator.setVisibility("gif".equals(comment.getMediaType()) ? View.VISIBLE : View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }
        }

        private void bindLikeInteraction(Comment comment, int position) {
            boolean isLiked = likedCommentIds.contains(comment.getId());
            
            if (likeButtonText != null) {
                likeButtonText.setText(isLiked ? context.getString(R.string.liked) : context.getString(R.string.like));
                likeButtonText.setTextColor(context.getResources().getColor(
                        isLiked ? R.color.primary_purple : R.color.text_secondary
                ));
                likeButtonText.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onLikeClicked(comment, position);
                        if (isLiked) likedCommentIds.remove(comment.getId());
                        else likedCommentIds.add(comment.getId());
                        notifyItemChanged(position);
                    }
                });
            }

            if (likeIcon != null) {
                likeIcon.setVisibility(comment.getLikeCount() > 0 ? View.VISIBLE : View.GONE);
            }

            if (likeCount != null) {
                likeCount.setText(comment.getLikeCount() > 0 ? String.valueOf(comment.getLikeCount()) : "");
                likeCount.setVisibility(comment.getLikeCount() > 0 ? View.VISIBLE : View.GONE);
            }
        }

        private String formatTimestamp(Context context, long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60000) return context.getString(R.string.just_now);
            if (diff < 3600000) return context.getString(R.string.minutes_ago, (int) (diff / 60000));
            if (diff < 86400000) return context.getString(R.string.hours_ago, (int) (diff / 3600000));
            return context.getString(R.string.days_ago, (int) (diff / 86400000));
        }
    }
}




