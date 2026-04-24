package com.example.social_app.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Notification (Thông báo)
 * Firestore collection: notifications
 *
 * Các thông báo gửi đến cho người dùng.
 * - userId: ID của người nhận thông báo
 * - type: "FOLLOW" | "LIKE" | "COMMENT" | "MESSAGE"
 * - referenceId: ID linh hoạt trỏ tới Post, Comment hoặc User tùy theo type
 *                (không ràng buộc cứng, chỉ là String)
 * - isRead: trạng thái đã đọc hay chưa
 */
public class Notification {

    @DocumentId
    private String id;
    private String userId;        // ID của người nhận thông báo
    private String actorId;       // ID của người gây ra hành động (người like, comment...)
    private String type;          // FOLLOW | LIKE | COMMENT | MESSAGE
    private String referenceId;   // flexible reference: postId / commentId / userId
    private boolean isRead;

    @ServerTimestamp
    private Date createdAt;

    public Notification() {}

    public Notification(String id, String userId, String actorId, String type,
                        String referenceId, boolean isRead) {
        this.id = id;
        this.userId = userId;
        this.actorId = actorId;
        this.type = type;
        this.referenceId = referenceId;
        this.isRead = isRead;
    }

    // Getters & Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    @PropertyName("isRead")
    public boolean isRead() {
        return isRead;
    }

    @PropertyName("isRead")
    public void setRead(boolean read) {
        isRead = read;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}