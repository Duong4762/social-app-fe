package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Post (Bài đăng)
 * Firestore collection: posts
 *
 * Lưu trữ thông tin chung của một bài viết.
 * Là thực thể cha của PostMedia, PostLike và Comment.
 * - userId: ID của người dùng sở hữu bài đăng
 * - visibility: "PUBLIC" | "FRIENDS" | "PRIVATE"
 */
public class Post {

    private String id;
    private String userId;
    private String caption;
    private String visibility;   // PUBLIC | FRIENDS | PRIVATE
    private long likeCount;
    private long commentCount;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    public Post() {}

    public Post(String id, String userId, String caption,
                String visibility, long likeCount, long commentCount) {
        this.id = id;
        this.userId = userId;
        this.caption = caption;
        this.visibility = visibility;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }

    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
