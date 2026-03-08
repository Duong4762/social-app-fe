package com.example.social_app.viewmodels;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

/**
 * ViewModel for managing new post creation.
 * Handles post content, media uploads, location, tags, and privacy settings.
 */
public class NewPostViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isPosting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> postSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<Boolean> getIsPosting() {
        return isPosting;
    }

    public LiveData<Boolean> getPostSuccess() {
        return postSuccess;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * Create a new post with content, media, location, tags, and privacy settings.
     */
    public void createPost(
            String content,
            List<Uri> mediaUris,
            String location,
            List<String> taggedPeople,
            String privacyLevel
    ) {
        isPosting.setValue(true);
        try {
            // Simulate network request and media upload
            Thread.sleep(1000);

            // Validate content
            if (content == null || content.trim().isEmpty()) {
                error.setValue("Post content cannot be empty");
                isPosting.setValue(false);
                return;
            }

            // Simulate successful post creation
            postSuccess.setValue(true);
            error.setValue(null);
        } catch (InterruptedException e) {
            error.setValue("Failed to create post: " + e.getMessage());
            postSuccess.setValue(false);
        } finally {
            isPosting.setValue(false);
        }
    }

    /**
     * Reset the post success state.
     */
    public void resetPostSuccess() {
        postSuccess.setValue(false);
    }
}
