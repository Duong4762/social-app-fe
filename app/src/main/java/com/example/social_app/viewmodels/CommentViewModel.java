package com.example.social_app.viewmodels;

import static com.example.social_app.utils.MockDataGenerator.generateMockComments;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.data.model.Comment;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
     * Send a new comment to Firestore with notification
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
                            .update("commentCount", FieldValue.increment(1));

                    // Tạo notification cho chủ bài viết (nếu không phải comment của chính mình)
                    createCommentNotification(postId, userId, text);

                    loadComments(postId);
                })
                .addOnFailureListener(e -> error.setValue("Lỗi gửi bình luận: " + e.getMessage()));
    }

    /**
     * Tạo notification khi có comment mới
     */
    private void createCommentNotification(String postId, String commenterId, String commentText) {
        // Lấy thông tin chủ bài viết
        db.collection(FirebaseManager.COLLECTION_POSTS)
                .document(postId)
                .get()
                .addOnSuccessListener(postDoc -> {
                    if (!postDoc.exists()) return;

                    String postOwnerId = postDoc.getString("userId");
                    if (postOwnerId == null) return;
                    if (postOwnerId.equals(commenterId)) return; // Không tự thông báo

                    String notificationId = "comment_" + postId + "_" + commenterId;

                    Map<String, Object> notification = new HashMap<>();
                    notification.put("id", notificationId);
                    notification.put("userId", postOwnerId);      // Người nhận (chủ bài viết)
                    notification.put("type", "COMMENT");
                    notification.put("referenceId", commenterId);  // Người comment
                    notification.put("isRead", false);
                    notification.put("createdAt", FieldValue.serverTimestamp());

                    // Thêm preview nội dung comment
                    String preview = commentText.length() > 50 ? commentText.substring(0, 50) + "..." : commentText;
                    notification.put("contentPreview", preview);

                    db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                            .document(notificationId)
                            .set(notification)
                            .addOnSuccessListener(aVoid -> {
                                android.util.Log.d("CommentViewModel", "Comment notification created");
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e("CommentViewModel", "Failed to create comment notification", e);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CommentViewModel", "Failed to get post owner", e);
                });
    }

    /**
     * Tạo notification khi có reply vào comment của người khác
     */
    private void createReplyNotification(String parentCommentId, String replierId, String replyText) {
        db.collection(FirebaseManager.COLLECTION_COMMENTS)
                .document(parentCommentId)
                .get()
                .addOnSuccessListener(commentDoc -> {
                    if (!commentDoc.exists()) return;

                    String parentCommentUserId = commentDoc.getString("userId");
                    String postId = commentDoc.getString("postId");

                    if (parentCommentUserId == null) return;
                    if (parentCommentUserId.equals(replierId)) return; // Không tự thông báo

                    String notificationId = "reply_" + parentCommentId + "_" + replierId;

                    Map<String, Object> notification = new HashMap<>();
                    notification.put("id", notificationId);
                    notification.put("userId", parentCommentUserId);  // Người nhận (chủ comment gốc)
                    notification.put("type", "REPLY");
                    notification.put("referenceId", replierId);       // Người reply
                    notification.put("postId", postId);
                    notification.put("isRead", false);
                    notification.put("createdAt", FieldValue.serverTimestamp());

                    String preview = replyText.length() > 50 ? replyText.substring(0, 50) + "..." : replyText;
                    notification.put("contentPreview", preview);

                    db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                            .document(notificationId)
                            .set(notification);
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
                            .update("commentCount", FieldValue.increment(1));
                    createCommentNotification(postId, userId, contentUrl);
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

        if (isLiking) {
            likedCommentIds.add(commentId);
            comment.setLikeCount(comment.getLikeCount() + 1);
        } else {
            likedCommentIds.remove(commentId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        }
        comments.setValue(comments.getValue());

        db.collection(FirebaseManager.COLLECTION_COMMENTS).document(commentId)
                .update("likeCount", FieldValue.increment(isLiking ? 1 : -1))
                .addOnFailureListener(e -> {
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

    public void loadMoreComments(String postId) {
        isLoading.setValue(false);
    }

    public void loadMoreReplies(String commentId) {
        error.setValue(null);
    }

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
}