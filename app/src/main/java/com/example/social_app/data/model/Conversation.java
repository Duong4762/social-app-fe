package com.example.social_app.data.model;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Conversation (Cuộc hội thoại)
 * Firestore collection: conversations
 *
 * Đại diện cho một phòng chat (1-1 hoặc nhóm).
 * - createdBy: ID của người dùng tạo cuộc hội thoại
 * - name: tên nhóm (null nếu là chat 1-1)
 * - isGroup: true nếu là nhóm chat
 */
public class Conversation {

    private String id;
    private String name;        // null nếu 1-1
    private boolean isGroup;
    private String createdBy;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    public Conversation() {}

    public Conversation(String id, String name, boolean isGroup, String createdBy) {
        this.id = id;
        this.name = name;
        this.isGroup = isGroup;
        this.createdBy = createdBy;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @PropertyName("isGroup")
    public boolean isGroup() { return isGroup; }

    @PropertyName("isGroup")
    public void setGroup(boolean group) { isGroup = group; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
