package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: MessageRead (Trạng thái xem tin nhắn)
 * Firestore collection: message_reads
 *
 * Theo dõi xem một người dùng cụ thể đã đọc một tin nhắn nào đó chưa.
 * - messageId: ID của tin nhắn
 * - userId: ID của người dùng đã đọc
 * - readAt: thời điểm đọc tin nhắn
 *
 * Document ID tổng hợp (composite key): "{messageId}_{userId}"
 */
public class MessageRead {

    private String id;          // composite: "{messageId}_{userId}"
    private String messageId;
    private String userId;

    @ServerTimestamp
    private Date readAt;

    public MessageRead() {}

    public MessageRead(String id, String messageId, String userId) {
        this.id = id;
        this.messageId = messageId;
        this.userId = userId;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getReadAt() { return readAt; }
    public void setReadAt(Date readAt) { this.readAt = readAt; }
}
