package com.example.social_app.viewmodels;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.PostMedia;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.CloudinaryUploadUtil;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ViewModel for managing new post creation.
 * Handles post content and media uploads.
 */
public class NewPostViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> isPosting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> postSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Post> editingPost = new MutableLiveData<>();
    private final MutableLiveData<List<PostMedia>> editingMedia = new MutableLiveData<>();

    private final MutableLiveData<List<Uri>> selectedMedias = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> existingMediaUrls = new MutableLiveData<>(new ArrayList<>());

    private final FirebaseFirestore db;
    private final FirebaseManager firebaseManager;

    public NewPostViewModel(@NonNull Application application) {
        super(application);
        firebaseManager = FirebaseManager.getInstance();
        db = firebaseManager.getFirestore();
        CloudinaryUploadUtil.setCloudinaryConfig(CloudinaryUploadUtil.CLOUD_NAME, CloudinaryUploadUtil.UPLOAD_PRESET);
    }

    public LiveData<Boolean> getIsPosting() { return isPosting; }
    public LiveData<Boolean> getPostSuccess() { return postSuccess; }
    public LiveData<String> getError() { return error; }
    public LiveData<Post> getEditingPost() { return editingPost; }
    public LiveData<List<PostMedia>> getEditingMedia() { return editingMedia; }

    public LiveData<List<Uri>> getSelectedMedias() { return selectedMedias; }
    public LiveData<List<String>> getExistingMediaUrls() { return existingMediaUrls; }

    public void addSelectedMedia(Uri uri) {
        List<Uri> current = selectedMedias.getValue();
        if (current != null) {
            current.add(uri);
            selectedMedias.setValue(new ArrayList<>(current));
        }
    }

    public void removeSelectedMedia(int index) {
        List<Uri> current = selectedMedias.getValue();
        if (current != null && index < current.size()) {
            current.remove(index);
            selectedMedias.setValue(new ArrayList<>(current));
        }
    }

    public void setExistingMediaUrls(List<String> urls) {
        existingMediaUrls.setValue(new ArrayList<>(urls));
    }

    public void removeExistingMedia(int index) {
        List<String> current = existingMediaUrls.getValue();
        if (current != null && index < current.size()) {
            current.remove(index);
            existingMediaUrls.setValue(new ArrayList<>(current));
        }
    }

    public void loadPostForEdit(String postId) {
        db.collection(FirebaseManager.COLLECTION_POSTS).document(postId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Post post = documentSnapshot.toObject(Post.class);
                    if (post != null) {
                        editingPost.setValue(post);
                        loadMediaForPost(postId);
                    }
                })
                .addOnFailureListener(e -> error.setValue("Lỗi tải bài viết: " + e.getMessage()));
    }

    private void loadMediaForPost(String postId) {
        db.collection(FirebaseManager.COLLECTION_POST_MEDIA)
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PostMedia> mediaList = queryDocumentSnapshots.toObjects(PostMedia.class);
                    mediaList.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));
                    editingMedia.setValue(mediaList);
                })
                .addOnFailureListener(e -> error.setValue("Lỗi tải media: " + e.getMessage()));
    }

    public void createPost(String content, List<Uri> mediaUris, String location, List<String> taggedPeople, String privacyLevel) {
        if (Boolean.TRUE.equals(isPosting.getValue())) return;

        isPosting.setValue(true);
        String userId = firebaseManager.getAuth().getUid();
        
        if (userId == null) {
            error.setValue("Bạn cần đăng nhập để thực hiện chức năng này");
            isPosting.setValue(false);
            return;
        }

        if (mediaUris.isEmpty()) {
            savePostToFirestore(userId, content, privacyLevel, new ArrayList<>(), location, taggedPeople);
        } else {
            uploadMediaAndSavePost(userId, content, privacyLevel, mediaUris, location, taggedPeople);
        }
    }

    private void uploadMediaAndSavePost(String userId, String content, String privacyLevel, List<Uri> mediaUris, String location, List<String> taggedPeople) {
        List<PostMedia> uploadedMediaList = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);
        int total = mediaUris.size();

        for (int i = 0; i < total; i++) {
            Uri uri = mediaUris.get(i);
            int order = i;
            String mimeType = getApplication().getContentResolver().getType(uri);
            String mediaType = (mimeType != null && mimeType.startsWith("video/")) ? "VIDEO" : "IMAGE";

            CloudinaryUploadUtil.uploadMedia(getApplication(), uri, new CloudinaryUploadUtil.UploadCallback() {
                @Override
                public void onSuccess(String secureUrl, String publicId) {
                    PostMedia media = new PostMedia(UUID.randomUUID().toString(), null, secureUrl, mediaType, order);
                    synchronized (uploadedMediaList) {
                        uploadedMediaList.add(media);
                    }
                    
                    if (uploadCount.incrementAndGet() == total) {
                        savePostToFirestore(userId, content, privacyLevel, uploadedMediaList, location, taggedPeople);
                    }
                }

                @Override
                public void onError(String message, Throwable throwable) {
                    if (Boolean.TRUE.equals(isPosting.getValue())) {
                        isPosting.postValue(false);
                        error.postValue("Lỗi upload media: " + message);
                    }
                }
            });
        }
    }

    private void savePostToFirestore(String userId, String content, String privacyLevel, List<PostMedia> mediaList, String location, List<String> taggedPeople) {
        String postId = db.collection(FirebaseManager.COLLECTION_POSTS).document().getId();
        
        Post post = new Post();
        post.setId(postId);
        post.setUserId(userId);
        post.setCaption(content);
        post.setVisibility(privacyLevel != null ? privacyLevel.toUpperCase() : "EVERYONE");
        post.setLocation(location);
        post.setTaggedUsers(taggedPeople);
        post.setLikeCount(0);
        post.setCommentCount(0);

        WriteBatch batch = db.batch();
        batch.set(db.collection(FirebaseManager.COLLECTION_POSTS).document(postId), post);
        
        for (PostMedia media : mediaList) {
            media.setPostId(postId);
            batch.set(db.collection(FirebaseManager.COLLECTION_POST_MEDIA).document(media.getId()), media);
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    postSuccess.setValue(true);
                    isPosting.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi lưu bài viết: " + e.getMessage());
                    isPosting.setValue(false);
                });
    }

    public void updatePost(String postId, String content, List<Uri> newMediaUris, List<String> existingMediaUrls, String privacyLevel, String location, List<String> taggedPeople) {
        if (Boolean.TRUE.equals(isPosting.getValue())) return;
        isPosting.setValue(true);

        if (newMediaUris.isEmpty()) {
            updatePostInFirestore(postId, content, privacyLevel, new ArrayList<>(), existingMediaUrls, location, taggedPeople);
        } else {
            uploadMediaAndEditPost(postId, content, privacyLevel, newMediaUris, existingMediaUrls, location, taggedPeople);
        }
    }

    private void uploadMediaAndEditPost(String postId, String content, String privacyLevel, List<Uri> newMediaUris, List<String> existingMediaUrls, String location, List<String> taggedPeople) {
        List<PostMedia> newlyUploadedMediaList = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);
        int total = newMediaUris.size();

        for (int i = 0; i < total; i++) {
            Uri uri = newMediaUris.get(i);
            int orderOffset = existingMediaUrls.size();
            int order = orderOffset + i;
            String mimeType = getApplication().getContentResolver().getType(uri);
            String mediaType = (mimeType != null && mimeType.startsWith("video/")) ? "VIDEO" : "IMAGE";

            CloudinaryUploadUtil.uploadMedia(getApplication(), uri, new CloudinaryUploadUtil.UploadCallback() {
                @Override
                public void onSuccess(String secureUrl, String publicId) {
                    PostMedia media = new PostMedia(UUID.randomUUID().toString(), postId, secureUrl, mediaType, order);
                    synchronized (newlyUploadedMediaList) {
                        newlyUploadedMediaList.add(media);
                    }
                    if (uploadCount.incrementAndGet() == total) {
                        updatePostInFirestore(postId, content, privacyLevel, newlyUploadedMediaList, existingMediaUrls, location, taggedPeople);
                    }
                }

                @Override
                public void onError(String message, Throwable throwable) {
                    isPosting.postValue(false);
                    error.postValue("Lỗi upload media: " + message);
                }
            });
        }
    }

    private void updatePostInFirestore(String postId, String content, String privacyLevel, List<PostMedia> newMediaList, List<String> existingMediaUrls, String location, List<String> taggedPeople) {
        WriteBatch batch = db.batch();
        
        batch.update(db.collection(FirebaseManager.COLLECTION_POSTS).document(postId),
                "caption", content,
                "visibility", privacyLevel != null ? privacyLevel.toUpperCase() : "EVERYONE",
                "location", location,
                "taggedUsers", taggedPeople,
                "updatedAt", com.google.firebase.Timestamp.now());

        db.collection(FirebaseManager.COLLECTION_POST_MEDIA)
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }

                    for (int i = 0; i < existingMediaUrls.size(); i++) {
                        String url = existingMediaUrls.get(i);
                        String mediaType = (url.contains("video/upload") || url.endsWith(".mp4")) ? "VIDEO" : "IMAGE";
                        PostMedia media = new PostMedia(UUID.randomUUID().toString(), postId, url, mediaType, i);
                        batch.set(db.collection(FirebaseManager.COLLECTION_POST_MEDIA).document(media.getId()), media);
                    }

                    for (PostMedia media : newMediaList) {
                        batch.set(db.collection(FirebaseManager.COLLECTION_POST_MEDIA).document(media.getId()), media);
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                postSuccess.setValue(true);
                                isPosting.setValue(false);
                            })
                            .addOnFailureListener(e -> {
                                error.setValue("Lỗi cập nhật bài viết: " + e.getMessage());
                                isPosting.setValue(false);
                            });
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi khi xử lý media cũ: " + e.getMessage());
                    isPosting.setValue(false);
                });
    }

    public void deletePost(String postId) {
        if (Boolean.TRUE.equals(isPosting.getValue())) return;
        isPosting.setValue(true);

        WriteBatch batch = db.batch();
        batch.delete(db.collection(FirebaseManager.COLLECTION_POSTS).document(postId));

        db.collection(FirebaseManager.COLLECTION_POST_MEDIA)
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                postSuccess.setValue(true);
                                isPosting.setValue(false);
                            })
                            .addOnFailureListener(e -> {
                                error.setValue("Lỗi khi xóa bài viết: " + e.getMessage());
                                isPosting.setValue(false);
                            });
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi khi tìm media để xóa: " + e.getMessage());
                    isPosting.setValue(false);
                });
    }

    public void resetPostSuccess() {
        postSuccess.setValue(false);
    }

    public void clearData() {
        selectedMedias.setValue(new ArrayList<>());
        existingMediaUrls.setValue(new ArrayList<>());
        editingPost.setValue(null);
        editingMedia.setValue(new ArrayList<>());
        isPosting.setValue(false);
        postSuccess.setValue(false);
        error.setValue(null);
    }
}