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
import java.util.HashSet;
import java.util.List;
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
                        // Khi refresh, xóa trạng thái cũ và tải lại engagement mới nhất
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

    public void toggleLike(Post post) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) return;

        String postId = post.getId();
        boolean isLiking = !likedPostIds.contains(postId);

        if (isLiking) {
            likedPostIds.add(postId);
            
            // Add to post_likes collection
            java.util.Map<String, Object> likeData = new java.util.HashMap<>();
            likeData.put("postId", postId);
            likeData.put("userId", userId);
            likeData.put("createdAt", FieldValue.serverTimestamp());
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).set(likeData);
            
            // Send notification to post owner
            sendLikeNotification(post, userId);
        } else {
            likedPostIds.remove(postId);
            
            // Remove from post_likes collection
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).delete();
        }

        // Update Firestore
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                .update("likeCount", FieldValue.increment(isLiking ? 1 : -1));
    }

    public void toggleBookmark(Post post) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null || post == null || post.getId() == null) return;

        String postId = post.getId();
        boolean isBookmarking = !bookmarkedPostIds.contains(postId);

        // Optimistic UI update
        if (isBookmarking) {
            bookmarkedPostIds.add(postId);
        } else {
            bookmarkedPostIds.remove(postId);
        }
        posts.setValue(posts.getValue()); // Trigger UI update immediately

        String docId = postId + "_" + userId;
        if (isBookmarking) {
            java.util.Map<String, Object> bookmarkData = new java.util.HashMap<>();
            bookmarkData.put("postId", postId);
            bookmarkData.put("userId", userId);
            bookmarkData.put("createdAt", FieldValue.serverTimestamp());
            db.collection(FirebaseManager.COLLECTION_BOOKMARKS).document(docId).set(bookmarkData)
                .addOnFailureListener(e -> {
                    // Revert on failure
                    bookmarkedPostIds.remove(postId);
                    posts.setValue(posts.getValue());
                });
        } else {
            db.collection(FirebaseManager.COLLECTION_BOOKMARKS).document(docId).delete()
                .addOnFailureListener(e -> {
                    // Revert on failure
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
                    // Update local list to refresh UI
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
                .addOnSuccessListener(aVoid -> {
                    // Show success or update UI
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi khi gửi báo cáo: " + e.getMessage());
                });
    }

    private void sendLikeNotification(Post post, String currentUserId) {
        if (post.getUserId().equals(currentUserId)) return; // Không tự gửi thông báo cho mình

        java.util.Map<String, Object> notification = new java.util.HashMap<>();
        notification.put("userId", post.getUserId());
        notification.put("actorId", currentUserId);
        notification.put("type", "LIKE");
        notification.put("referenceId", post.getId());
        notification.put("isRead", false);
        notification.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS).add(notification);
    }

    private void loadUserEngagement() {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) return;

        // Load likes
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

        // Load bookmarks
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
