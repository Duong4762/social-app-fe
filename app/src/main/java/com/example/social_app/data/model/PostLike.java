package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: PostLike (Thích bài đăng)
 * Firestore collection: post_likes
 *
 * Lưu lại hành động thả tim/like của người dùng cho một bài viết.
 * - userId: ID của người dùng thực hiện like
 * - postId: ID của bài đăng được like
 *
 * Gợi ý composite Document ID: "{postId}_{userId}"
 */
public class PostLike {

    private String id;
    private String postId;
    private String userId;

    @ServerTimestamp
    private Date createdAt;

    public PostLike() {}

    public PostLike(String id, String postId, String userId) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
