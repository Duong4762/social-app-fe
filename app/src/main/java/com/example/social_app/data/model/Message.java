package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Message (Tin nhắn)
 * Firestore collection: messages
 *
 * Nội dung tin nhắn gửi đi trong một phòng chat.
 * - conversationId: ID của cuộc hội thoại
 * - senderId: ID của người gửi (liên kết với User.id)
 * - repliedMessageId: null nếu không phải reply; chứa ID của tin nhắn được trả lời
 * - messageType: "TEXT" | "IMAGE" | "VIDEO"
 */
public class Message {

    private String id;
    private String conversationId;
    private String senderId;
    private String repliedMessageId;   // null = tin nhắn mới; non-null = reply
    private String content;
    private String messageType;        // TEXT | IMAGE | VIDEO

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    public Message() {}

    public Message(String id, String conversationId, String senderId,
                   String repliedMessageId, String content, String messageType) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.repliedMessageId = repliedMessageId;
        this.content = content;
        this.messageType = messageType;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRepliedMessageId() { return repliedMessageId; }
    public void setRepliedMessageId(String repliedMessageId) { this.repliedMessageId = repliedMessageId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
