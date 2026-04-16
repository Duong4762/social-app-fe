package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: ConversationMember (Thành viên cuộc trò chuyện)
 * Firestore collection: conversation_members
 *
 * Xác định những ai đang ở trong một cuộc hội thoại.
 * - conversationId: ID của cuộc hội thoại
 * - userId: ID của thành viên
 * - role: "MEMBER" | "ADMIN"
 *
 * Gợi ý composite Document ID: "{conversationId}_{userId}"
 */
public class ConversationMember {

    private String id;
    private String conversationId;
    private String userId;
    private String role;   // MEMBER | ADMIN

    @ServerTimestamp
    private Date joinedAt;

    public ConversationMember() {}

    public ConversationMember(String id, String conversationId,
                              String userId, String role) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Date getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Date joinedAt) { this.joinedAt = joinedAt; }
}
