package com.example.social_app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.data.model.Post;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

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
            post.setLikeCount(post.getLikeCount() + 1);
            
            // Add to post_likes collection
            java.util.Map<String, Object> likeData = new java.util.HashMap<>();
            likeData.put("postId", postId);
            likeData.put("userId", userId);
            likeData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).set(likeData);
        } else {
            likedPostIds.remove(postId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            
            // Remove from post_likes collection
            db.collection(FirebaseManager.COLLECTION_POST_LIKES).document(postId + "_" + userId).delete();
        }
        posts.setValue(posts.getValue()); // Notify UI

        // Update Firestore
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId)
                .update("likeCount", com.google.firebase.firestore.FieldValue.increment(isLiking ? 1 : -1));
    }

    public void toggleBookmark(Post post) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) return;

        String postId = post.getId();
        boolean isBookmarking = !bookmarkedPostIds.contains(postId);

        if (isBookmarking) {
            bookmarkedPostIds.add(postId);
            
            java.util.Map<String, Object> bookmarkData = new java.util.HashMap<>();
            bookmarkData.put("postId", postId);
            bookmarkData.put("userId", userId);
            bookmarkData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection("bookmarks").document(postId + "_" + userId).set(bookmarkData);
        } else {
            bookmarkedPostIds.remove(postId);
            db.collection("bookmarks").document(postId + "_" + userId).delete();
        }
        posts.setValue(posts.getValue()); // Notify UI
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
        db.collection("bookmarks")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    bookmarkedPostIds.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        bookmarkedPostIds.add(doc.getString("postId"));
                    }
                    posts.setValue(posts.getValue());
                });
    }
}
