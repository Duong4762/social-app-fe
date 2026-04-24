package com.example.social_app.viewmodels;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.social_app.data.model.Story;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.CloudinaryUploadUtil;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class StoryViewModel extends ViewModel {

    private final MutableLiveData<List<Story>> stories = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isUploading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> uploadSuccess = new MutableLiveData<>(null);

    private final FirebaseFirestore db;
    private final FirebaseManager firebaseManager;

    public StoryViewModel() {
        firebaseManager = FirebaseManager.getInstance();
        db = firebaseManager.getFirestore();
    }

    public LiveData<List<Story>> getStories() { return stories; }
    public LiveData<Boolean> getIsUploading() { return isUploading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getUploadSuccess() { return uploadSuccess; }

    // ================== LOAD STORIES ==================
    public void loadStories() {
        String currentUserId = firebaseManager.getAuth().getUid();
        if (currentUserId == null) return;

        db.collection(FirebaseManager.COLLECTION_STORIES)
                .whereGreaterThan("expiresAt", new Date())
                .get()
                .addOnSuccessListener(query -> {
                    List<Story> storyList = new ArrayList<>();

                    for (var doc : query.getDocuments()) {
                        Story story = doc.toObject(Story.class);
                        if (story != null) {
                            story.setId(doc.getId());

                            if (story.getExpiresAt() == null || story.getExpiresAt().after(new Date())) {
                                storyList.add(story);
                            }
                        }
                    }

                    storyList.sort((s1, s2) -> {
                        Date d1 = s1.getCreatedAt();
                        Date d2 = s2.getCreatedAt();

                        if (d1 == null && d2 == null) return 0;
                        if (d1 == null) return 1;
                        if (d2 == null) return -1;

                        return d2.compareTo(d1);
                    });

                    stories.setValue(storyList);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi tải story: " + e.getMessage());
                });
    }

    // ================== UPLOAD ==================
    public void uploadStory(Uri mediaUri, String mediaType, int duration) {
        String userId = firebaseManager.getAuth().getUid();
        if (userId == null) {
            error.setValue("Vui lòng đăng nhập");
            return;
        }

        isUploading.setValue(true);
        error.setValue(null);

        CloudinaryUploadUtil.uploadMedia(
                firebaseManager.getStorage().getApp().getApplicationContext(),
                mediaUri,
                new CloudinaryUploadUtil.UploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        createStoryInFirestore(userId, secureUrl, mediaType, duration);
                    }

                    @Override
                    public void onError(String message, Throwable throwable) {
                        isUploading.setValue(false);
                        error.setValue("Upload thất bại: " + message);
                    }
                }
        );
    }

    // ================== CREATE STORY ==================
    private void createStoryInFirestore(String userId, String mediaUrl, String mediaType, int duration) {
        String storyId = UUID.randomUUID().toString();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 24);
        Date expiresAt = calendar.getTime();

        Story story = new Story(storyId, userId, mediaUrl, mediaType, duration);
        story.setExpiresAt(expiresAt);
        story.setCreatedAt(new Date());
        story.setViewCount(0);

        db.collection(FirebaseManager.COLLECTION_STORIES)
                .document(storyId)
                .set(story)
                .addOnSuccessListener(aVoid -> {
                    isUploading.setValue(false);
                    uploadSuccess.setValue(true);
                    loadStories();
                })
                .addOnFailureListener(e -> {
                    isUploading.setValue(false);
                    error.setValue("Lưu story thất bại: " + e.getMessage());
                });
    }

    // ================== VIEW COUNT ==================
    public void incrementViewCount(String storyId) {
        db.collection(FirebaseManager.COLLECTION_STORIES)
                .document(storyId)
                .update("viewCount", FieldValue.increment(1));
    }

    // ================== CLEAN EXPIRED ==================
    public void cleanExpiredStories() {
        db.collection(FirebaseManager.COLLECTION_STORIES)
                .whereLessThan("expiresAt", new Date())
                .get()
                .addOnSuccessListener(query -> {
                    for (var doc : query.getDocuments()) {
                        doc.getReference().delete();
                    }
                });
    }


}
