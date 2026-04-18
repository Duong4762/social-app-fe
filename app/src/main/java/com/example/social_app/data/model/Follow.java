package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Follow (Theo dõi)
 * Firestore collection: follows
 *
 * Quản lý việc theo dõi lẫn nhau giữa các người dùng.
 * - followerId: ID của người đi theo dõi
 * - followingId: ID của người được theo dõi
 *
 * Gợi ý composite Document ID: "{followerId}_{followingId}"
 */
public class Follow {

    private String id;
    private String followerId;
    private String followingId;

    @ServerTimestamp
    private Date createdAt;

    public Follow() {}

    public Follow(String id, String followerId, String followingId) {
        this.id = id;
        this.followerId = followerId;
        this.followingId = followingId;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFollowerId() { return followerId; }
    public void setFollowerId(String followerId) { this.followerId = followerId; }

    public String getFollowingId() { return followingId; }
    public void setFollowingId(String followingId) { this.followingId = followingId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
