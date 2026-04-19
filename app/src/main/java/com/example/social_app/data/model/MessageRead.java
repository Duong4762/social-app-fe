package com.example.social_app.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

@IgnoreExtraProperties
public class MessageRead {

    /** ID document = "{messageId}_{userId}" — không lưu field id trong Firestore. */
    private String id;
    private String messageId;
    /** Người đã đọc tin (reader). */
    private String userId;
    /** Lọc listener theo hội thoại. */
    private String conversationId;

    @ServerTimestamp
    private Date readAt;

    public MessageRead() {}

    public MessageRead(String id, String messageId, String userId, String conversationId) {
        this.id = id;
        this.messageId = messageId;
        this.userId = userId;
        this.conversationId = conversationId;
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Date getReadAt() {
        return readAt;
    }

    public void setReadAt(Date readAt) {
        this.readAt = readAt;
    }
}
