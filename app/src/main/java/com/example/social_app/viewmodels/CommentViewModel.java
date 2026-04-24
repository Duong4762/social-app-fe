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
import java.util.Map;
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

    private String currentSortBy = "createdAt"; // Default sort

    public void loadComments(String postId) {
        isLoading.setValue(true);
        db.collection(FirebaseManager.COLLECTION_COMMENTS)
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Comment> fetchedComments = queryDocumentSnapshots.toObjects(Comment.class);
                    comments.setValue(fetchedComments);
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Error loading comments: " + e.getMessage());
                    isLoading.setValue(false);
                });
    }

    /**
     * Send a new comment to Firestore.
     */
    public void sendComment(String postId, String text, String parentId) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) {
            error.setValue("Vui lòng đăng nhập");
            return;
        }

        String commentId = UUID.randomUUID().toString();
        Comment newComment = new Comment(commentId, postId, userId, parentId, text, 0);

        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .set(newComment)
                .addOnSuccessListener(aVoid -> {
                    // Update comment count in Post document
                    db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                            .update("commentCount", com.google.firebase.firestore.FieldValue.increment(1));
                    
                    // Logic gửi thông báo
                    if (parentId != null) {
                        // Nếu là reply, gửi thông báo cho chủ của comment cha
                        sendReplyNotification(parentId, postId, text, userId);
                    } else {
                        // Nếu là comment mới, gửi thông báo cho chủ bài viết
                        sendCommentNotification(postId, text, userId);
                    }
                    
                    loadComments(postId); // Refresh list
                })
                .addOnFailureListener(e -> error.setValue("Lỗi gửi bình luận: " + e.getMessage()));
    }

    private void sendReplyNotification(String parentCommentId, String postId, String text, String currentUserId) {
        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(parentCommentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String parentOwnerId = documentSnapshot.getString("userId");
                        if (parentOwnerId != null && !parentOwnerId.equals(currentUserId)) {
                            Map<String, Object> notification = new java.util.HashMap<>();
                            notification.put("userId", parentOwnerId);
                            notification.put("actorId", currentUserId);
                            notification.put("type", "REPLY_COMMENT");
                            notification.put("referenceId", postId);
                            notification.put("isRead", false);
                            notification.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            
                            db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS).add(notification);
                        }
                    }
                });
    }

    public void sendComment(String postId, String text) {
        sendComment(postId, text, null);
    }

    public void sendCommentWithMedia(String postId, String contentUrl, String mediaUrl, String mediaType) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) {
            error.setValue("Vui lòng đăng nhập");
            return;
        }

        String commentId = UUID.randomUUID().toString();
        Comment newComment = new Comment(commentId, postId, userId, null, contentUrl, 0);
        newComment.setMediaUrl(mediaUrl);
        newComment.setMediaType(mediaType);

        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .set(newComment)
                .addOnSuccessListener(aVoid -> {
                    db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                            .update("commentCount", com.google.firebase.firestore.FieldValue.increment(1));
                    
                    // Send notification
                    sendCommentNotification(postId, "đã gửi một ảnh", userId);
                    
                    loadComments(postId);
                })
                .addOnFailureListener(e -> error.setValue("Lỗi gửi bình luận: " + e.getMessage()));
    }

    /**
     * Toggle like status for a comment.
     */
    public void toggleLike(Comment comment) {
        if (comment == null) return;

        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) {
            error.setValue("Vui lòng đăng nhập để thích");
            return;
        }

        String commentId = comment.getId();
        boolean isLiking = !likedCommentIds.contains(commentId);

        // Optimistic UI Update
        if (isLiking) {
            likedCommentIds.add(commentId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            
            // Send notification to comment owner
            sendCommentLikeNotification(comment, userId);
        } else {
            likedCommentIds.remove(commentId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        }
        comments.setValue(comments.getValue());

        // Firestore Update
        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .update("likeCount", com.google.firebase.firestore.FieldValue.increment(isLiking ? 1 : -1))
                .addOnFailureListener(e -> {
                    // Rollback on failure
                    if (isLiking) {
                        likedCommentIds.remove(commentId);
                        comment.setLikeCount(comment.getLikeCount() - 1);
                    } else {
                        likedCommentIds.add(commentId);
                        comment.setLikeCount(comment.getLikeCount() + 1);
                    }
                    comments.setValue(comments.getValue());
                    error.setValue("Lỗi cập nhật lượt thích");
                });
    }

    private void sendCommentLikeNotification(Comment comment, String currentUserId) {
        if (comment.getUserId().equals(currentUserId)) return;

        Map<String, Object> notification = new java.util.HashMap<>();
        notification.put("userId", comment.getUserId());
        notification.put("actorId", currentUserId);
        notification.put("type", "LIKE_COMMENT");
        notification.put("referenceId", comment.getPostId());
        notification.put("isRead", false);
        notification.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        
        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS).add(notification);
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

    public void reportComment(Comment comment, String reason) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null || comment == null) return;

        com.google.firebase.firestore.DocumentReference reportRef = db.collection(FirebaseManager.COLLECTION_REPORTS).document();
        com.example.social_app.data.model.Report report = new com.example.social_app.data.model.Report();
        report.setId(reportRef.getId());
        report.setReporterId(userId);
        report.setTargetId(comment.getId());
        report.setType("COMMENT");
        report.setStatus("UNPROCESSED");
        report.setReason(reason);

        reportRef.set(report)
                .addOnFailureListener(e -> error.setValue("Lỗi khi gửi báo cáo: " + e.getMessage()));
    }

    private void sendCommentNotification(String postId, String commentText, String currentUserId) {
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String postOwnerId = documentSnapshot.getString("userId");
                        if (postOwnerId != null && !postOwnerId.equals(currentUserId)) {
                            Map<String, Object> notification = new java.util.HashMap<>();
                            notification.put("userId", postOwnerId);
                            notification.put("actorId", currentUserId);
                            notification.put("type", "COMMENT");
                            notification.put("referenceId", postId);
                            notification.put("isRead", false);
                            notification.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            
                            db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS).add(notification);
                        }
                    }
                });
    }
}
