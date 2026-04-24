package com.example.social_app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.data.model.Post;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import com.example.social_app.data.model.Report;
import com.google.firebase.firestore.DocumentReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<List<Post>> posts = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final FirebaseFirestore db;
    private final FirebaseManager firebaseManager;
    private DocumentSnapshot lastVisible;
    private boolean isLastPage = false;
    private final Set<String> likedPostIds = new HashSet<>();
    private final Set<String> bookmarkedPostIds = new HashSet<>();

    public HomeViewModel() {
        firebaseManager = FirebaseManager.getInstance();
        db = firebaseManager.getFirestore();
    }

    public LiveData<List<Post>> getPosts() { return posts; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }

    public Set<String> getLikedPostIds() { return likedPostIds; }
    public Set<String> getBookmarkedPostIds() { return bookmarkedPostIds; }

    public void loadPosts(boolean isRefresh) {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        if (!isRefresh && isLastPage) return;

        isLoading.setValue(true);

        Query query = db.collection(FirebaseManager.COLLECTION_POSTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10);

        if (!isRefresh && lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> newPosts = queryDocumentSnapshots.toObjects(Post.class);

                    if (isRefresh) {
                        posts.setValue(newPosts);
                        likedPostIds.clear();
                        bookmarkedPostIds.clear();
                        loadUserEngagement();
                    } else {
                        List<Post> currentPosts = posts.getValue();
                        if (currentPosts == null) currentPosts = new ArrayList<>();
                        currentPosts.addAll(newPosts);
                        posts.setValue(currentPosts);
                    }

                    if (queryDocumentSnapshots.size() < 10) {
                        isLastPage = true;
                    } else {
                        lastVisible = queryDocumentSnapshots.getDocuments()
                                .get(queryDocumentSnapshots.size() - 1);
                        isLastPage = false;
                    }

                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi tải bài viết: " + e.getMessage());
                    isLoading.setValue(false);
                });
    }

    // ==================== TOGGLE LIKE WITH NOTIFICATION ====================

    public void toggleLike(Post post) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) return;

        String postId = post.getId();
        boolean isLiking = !likedPostIds.contains(postId);

        if (isLiking) {
            likedPostIds.add(postId);

            // Add to post_likes collection
            Map<String, Object> likeData = new HashMap<>();
            likeData.put("postId", postId);
            likeData.put("userId", userId);
            likeData.put("createdAt", FieldValue.serverTimestamp());
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).set(likeData);

            // Tạo notification cho chủ bài viết (CHỈ KHI LIKE)
            createLikeNotification(postId, post.getUserId(), userId);
        } else {
            likedPostIds.remove(postId);

            // Remove from post_likes collection
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).delete();
        }

        // Update Firestore
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                .update("likeCount", FieldValue.increment(isLiking ? 1 : -1));
    }

    /**
     * Tạo notification khi có người like bài viết
     */
    private void createLikeNotification(String postId, String postOwnerId, String likerId) {
        if (postOwnerId == null || likerId == null) return;
        if (postOwnerId.equals(likerId)) return; // Không tự thông báo cho chính mình

        String notificationId = "like_" + postId + "_" + likerId;

        Map<String, Object> notification = new HashMap<>();
        notification.put("id", notificationId);
        notification.put("userId", postOwnerId);      // Người nhận thông báo (chủ bài viết)
        notification.put("type", "LIKE");
        notification.put("referenceId", likerId);     // Người like
        notification.put("isRead", false);
        notification.put("createdAt", FieldValue.serverTimestamp());

        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .set(notification)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("HomeViewModel", "Like notification created: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("HomeViewModel", "Failed to create like notification", e);
                });
    }

    // ==================== END LIKE NOTIFICATION ====================

    public void toggleBookmark(Post post) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null || post == null || post.getId() == null) return;

        String postId = post.getId();
        boolean isBookmarking = !bookmarkedPostIds.contains(postId);

        if (isBookmarking) {
            bookmarkedPostIds.add(postId);
        } else {
            bookmarkedPostIds.remove(postId);
        }
        posts.setValue(posts.getValue());

        String docId = postId + "_" + userId;
        if (isBookmarking) {
            Map<String, Object> bookmarkData = new HashMap<>();
            bookmarkData.put("postId", postId);
            bookmarkData.put("userId", userId);
            bookmarkData.put("createdAt", FieldValue.serverTimestamp());
            db.collection(FirebaseManager.COLLECTION_BOOKMARKS).document(docId).set(bookmarkData)
                    .addOnFailureListener(e -> {
                        bookmarkedPostIds.remove(postId);
                        posts.setValue(posts.getValue());
                    });
        } else {
            db.collection(FirebaseManager.COLLECTION_BOOKMARKS).document(docId).delete()
                    .addOnFailureListener(e -> {
                        bookmarkedPostIds.add(postId);
                        posts.setValue(posts.getValue());
                    });
        }
    }

    public void incrementShareCount(Post post) {
        if (post == null || post.getId() == null) return;

        String postId = post.getId();
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                .update("shareCount", FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> {
                    List<Post> currentPosts = posts.getValue();
                    if (currentPosts != null) {
                        for (Post p : currentPosts) {
                            if (p.getId().equals(postId)) {
                                p.setShareCount(p.getShareCount() + 1);
                                break;
                            }
                        }
                        posts.setValue(currentPosts);
                    }
                });
    }

    public void reportPost(Post post, String reason) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null || post == null) return;

        DocumentReference reportRef = db.collection(FirebaseManager.COLLECTION_REPORTS).document();
        Report report = new Report();
        report.setId(reportRef.getId());
        report.setReporterId(userId);
        report.setTargetId(post.getId());
        report.setType("POST");
        report.setStatus("UNPROCESSED");
        report.setReason(reason);

        reportRef.set(report)
                .addOnSuccessListener(aVoid -> {})
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi khi gửi báo cáo: " + e.getMessage());
                });
    }

    public void reportUser(com.example.social_app.data.model.User user, String reason) {
        String userId = firebaseManager.getAuth().getUid();

        if (userId == null) {
            error.setValue("Vui lòng đăng nhập để báo cáo");
            return;
        }

        if (user == null || user.getId() == null) {
            error.setValue("Không thể báo cáo người dùng này");
            return;
        }

        if (userId.equals(user.getId())) {
            error.setValue("Bạn không thể báo cáo chính mình");
            return;
        }

        db.collection(FirebaseManager.COLLECTION_REPORTS)
                .whereEqualTo("reporterId", userId)
                .whereEqualTo("targetId", user.getId())
                .whereEqualTo("type", "USER")
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        error.setValue("Bạn đã báo cáo người dùng này rồi");
                        return;
                    }

                    String reportId = java.util.UUID.randomUUID().toString();
                    Report report = new Report();
                    report.setId(reportId);
                    report.setReporterId(userId);
                    report.setTargetId(user.getId());
                    report.setType("USER");
                    report.setReason(reason);
                    report.setStatus("UNPROCESSED");
                    report.setCreatedAt(new java.util.Date());

                    db.collection(FirebaseManager.COLLECTION_REPORTS)
                            .document(reportId)
                            .set(report)
                            .addOnSuccessListener(aVoid -> {
                                android.util.Log.d("HomeViewModel", "User report submitted: " + reportId);
                            })
                            .addOnFailureListener(e -> {
                                error.setValue("Gửi báo cáo thất bại: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi kiểm tra báo cáo: " + e.getMessage());
                });
    }

    private void loadUserEngagement() {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) return;

        db.collection(FirebaseManager.COLLECTION_POST_LIKES)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    likedPostIds.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        likedPostIds.add(doc.getString("postId"));
                    }
                    posts.setValue(posts.getValue());
                });

        db.collection(FirebaseManager.COLLECTION_BOOKMARKS)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    bookmarkedPostIds.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String pId = doc.getString("postId");
                        if (pId != null) bookmarkedPostIds.add(pId);
                    }
                    posts.setValue(posts.getValue());
                });
    }
}