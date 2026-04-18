package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class PostMedia {

    private String id;
    private String postId;
    private String mediaUrl;
    private String mediaType;   // IMAGE | VIDEO
    private int order;

    @ServerTimestamp
    private Date createdAt;

    public PostMedia() {}

    public PostMedia(String id, String postId, String mediaUrl,
                     String mediaType, int order) {
        this.id = id;
        this.postId = postId;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.order = order;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
