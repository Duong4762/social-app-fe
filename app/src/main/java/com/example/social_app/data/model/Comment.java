package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Comment (Bình luận)
 * Firestore collection: comments
 *
 * Bình luận vào các bài đăng hoặc phản hồi (reply) bình luận khác.
 * - postId: ID của bài đăng chứa bình luận
 * - userId: ID của người dùng viết bình luận
 * - parentId: null nếu là bình luận gốc; chứa ID của bình luận gốc nếu là reply
 */
public class Comment {

    private String id;
    private String postId;
    private String userId;
    private String parentId;   // null = bình luận gốc; non-null = reply
    private String content;
    private String mediaUrl;
    private String mediaType; // "image" or "video" or "gif"
    private long likeCount;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    public Comment() {}

    public Comment(String id, String postId, String userId,
                   String parentId, String content, long likeCount) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.parentId = parentId;
        this.content = content;
        this.likeCount = likeCount;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
