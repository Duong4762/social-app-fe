package com.example.social_app.viewmodels;

import static com.example.social_app.utils.MockDataGenerator.generateMockComments;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.data.model.Comment;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ViewModel for managing comment data and operations.
 * Handles fetching, posting, liking, and replying to comments.
 */
public class CommentViewModel extends ViewModel {

    private final MutableLiveData<List<Comment>> comments = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final Set<String> likedCommentIds = new HashSet<>();

    public LiveData<List<Comment>> getComments() {
        return comments;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    private final FirebaseFirestore db;
    private final FirebaseManager firebaseManager;

    public CommentViewModel() {
        firebaseManager = FirebaseManager.getInstance();
        db = firebaseManager.getFirestore();
    }

    /**
     * Load comments for a specific post from Firestore.
     */
    public void loadComments(String postId) {
        isLoading.setValue(true);
        db.collection(FirebaseManager.COLLECTION_COMMENTS)
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Comment> fetchedComments = queryDocumentSnapshots.toObjects(Comment.class);
                    // Sort manually to avoid index requirement error
                    fetchedComments.sort((c1, c2) -> {
                        if (c1.getCreatedAt() == null || c2.getCreatedAt() == null) return 0;
                        return c2.getCreatedAt().compareTo(c1.getCreatedAt());
                    });
                    comments.setValue(fetchedComments);
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi tải bình luận: " + e.getMessage());
                    isLoading.setValue(false);
                });
    }

    /**
     * Send a new comment to Firestore.
     */
    public void sendComment(String postId, String text) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) {
            error.setValue("Vui lòng đăng nhập");
            return;
        }

        String commentId = UUID.randomUUID().toString();
        Comment newComment = new Comment(commentId, postId, userId, null, text, 0);

        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .set(newComment)
                .addOnSuccessListener(aVoid -> {
                    // Update like count in Post document (optional, but good for UI)
                    db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                            .update("commentCount", com.google.firebase.firestore.FieldValue.increment(1));
                    loadComments(postId); // Refresh list
                })
                .addOnFailureListener(e -> error.setValue("Lỗi gửi bình luận: " + e.getMessage()));
    }

    /**
     * Toggle like status for a comment.
     */
    public void toggleLike(Comment comment) {
        try {
            String commentId = comment.getId();
            if (commentId != null && likedCommentIds.add(commentId)) {
                comment.setLikeCount(comment.getLikeCount() + 1);
            } else {
                likedCommentIds.remove(commentId);
                comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            }
            comments.setValue(comments.getValue()); // Trigger update
        } catch (Exception e) {
            error.setValue("Failed to like comment: " + e.getMessage());
        }
    }

    /**
     * Load more comments for pagination (infinite scroll).
     */
    public void loadMoreComments(String postId) {
        // Implementation for pagination if needed
        isLoading.setValue(false);
    }

    /**
     * Load more replies for a comment.
     */
    public void loadMoreReplies(String commentId) {
        // No-op in mock mode: replies are represented by parentId relation.
        error.setValue(null);
    }

    /**
     * Delete a comment by ID.
     */
    public void deleteComment(String commentId) {
        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    List<Comment> currentComments = comments.getValue();
                    if (currentComments != null) {
                        currentComments.removeIf(comment -> comment.getId().equals(commentId));
                        comments.setValue(currentComments);
                    }
                })
                .addOnFailureListener(e -> error.setValue("Lỗi xóa bình luận: " + e.getMessage()));
    }
}
