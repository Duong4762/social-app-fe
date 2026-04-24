package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

public class Story implements Serializable {

    private String id;
    private String userId;
    private String mediaUrl;
    private String mediaType; // IMAGE | VIDEO
    private int duration; // thời gian hiển thị (giây)
    private int viewCount;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date expiresAt;

    public Story() {}

    public Story(String id, String userId, String mediaUrl, String mediaType, int duration) {
        this.id = id;
        this.userId = userId;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.duration = duration;
        this.viewCount = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public boolean isValid() {
        if (expiresAt == null) return true;
        return expiresAt.after(new Date());
    }
}