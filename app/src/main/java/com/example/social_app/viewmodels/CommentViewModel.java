package com.example.social_app.viewmodels;

import static com.example.social_app.utils.MockDataGenerator.generateMockComments;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.models.Comment;
import com.example.social_app.utils.MockDataGenerator;

import java.util.List;

/**
 * ViewModel for managing comment data and operations.
 * Handles fetching, posting, liking, and replying to comments.
 */
public class CommentViewModel extends ViewModel {

    private final MutableLiveData<List<Comment>> comments = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    public LiveData<List<Comment>> getComments() {
        return comments;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Load comments for a specific post.
     */
    public void loadComments(String postId) {
        isLoading.setValue(true);
        android.util.Log.d("CommentViewModel", "loadComments() called for postId: " + postId);

        // Load comments on a background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // Simulate network delay
                Thread.sleep(500);

                // Generate mock comments
                List<Comment> mockComments = generateMockComments();
                android.util.Log.d("CommentViewModel", "Generated " + mockComments.size() + " mock comments");

                // Update LiveData on main thread
                comments.postValue(mockComments);
                error.postValue(null);
            } catch (InterruptedException e) {
                android.util.Log.e("CommentViewModel", "Error loading comments", e);
                error.postValue("Failed to load comments: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }

    /**
     * Send a new comment on a post.
     */
    public void sendComment(String postId, String text) {
        try {
            // Simulate network request
            Comment newComment = new Comment(
                    "comment_" + System.currentTimeMillis(),
                    MockDataGenerator.generateMockUser(),
                    text,
                    System.currentTimeMillis(),
                    0,
                    null,
                    false
            );

            List<Comment> currentComments = comments.getValue();
            if (currentComments != null) {
                currentComments.add(0, newComment);
                comments.setValue(currentComments);
            }
            error.setValue(null);
        } catch (Exception e) {
            error.setValue("Failed to send comment: " + e.getMessage());
        }
    }

    /**
     * Toggle like status for a comment.
     */
    public void toggleLike(Comment comment) {
        try {
            comment.setLiked(!comment.isLiked());
            if (comment.isLiked()) {
                comment.setLikeCount(comment.getLikeCount() + 1);
            } else {
                comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            }
            comments.setValue(comments.getValue()); // Trigger update
        } catch (Exception e) {
            error.setValue("Failed to like comment: " + e.getMessage());
        }
    }

    /**
     * Load more comments for pagination (infinite scroll).
     * Appends new comments to the existing list.
     */
    public void loadMoreComments(String postId) {
        isLoading.setValue(true);
        try {
            // Simulate network delay
            Thread.sleep(500);

            // Generate mock comments
            List<Comment> newComments = generateMockComments();

            // Append to existing comments
            List<Comment> currentComments = comments.getValue();
            if (currentComments != null) {
                currentComments.addAll(newComments);
                comments.setValue(currentComments);
            }
            error.setValue(null);
        } catch (InterruptedException e) {
            error.setValue("Failed to load more comments: " + e.getMessage());
        } finally {
            isLoading.setValue(false);
        }
    }

    /**
     * Load more replies for a comment.
     */
    public void loadMoreReplies(String commentId) {
        isLoading.setValue(true);
        try {
            // Simulate network delay
            Thread.sleep(500);

            // Generate mock replies
            List<Comment> mockReplies = generateMockComments();

            // Find the comment and add replies
            List<Comment> currentComments = comments.getValue();
            if (currentComments != null) {
                for (Comment comment : currentComments) {
                    if (comment.getId().equals(commentId)) {
                        comment.setReplies(mockReplies);
                        comment.setHasMoreReplies(false);
                        break;
                    }
                }
                comments.setValue(currentComments);
            }
            error.setValue(null);
        } catch (InterruptedException e) {
            error.setValue("Failed to load replies: " + e.getMessage());
        } finally {
            isLoading.setValue(false);
        }
    }

    /**
     * Delete a comment by ID.
     */
    public void deleteComment(String commentId) {
        try {
            List<Comment> currentComments = comments.getValue();
            if (currentComments != null) {
                currentComments.removeIf(comment -> comment.getId().equals(commentId));
                comments.setValue(currentComments);
                android.util.Log.d("CommentViewModel", "Comment deleted: " + commentId);
            }
            error.setValue(null);
        } catch (Exception e) {
            error.setValue("Failed to delete comment: " + e.getMessage());
            android.util.Log.e("CommentViewModel", "Error deleting comment", e);
        }
    }
}
