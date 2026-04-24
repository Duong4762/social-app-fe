package com.example.social_app.data.model;

import androidx.annotation.Nullable;

/**
 * Document {@code voice_calls/{callId}} — tín hiệu cuộc gọi giữa hai user.
 */
public class VoiceCallSession {

    public static final String STATE_RINGING = "RINGING";
    public static final String STATE_CONNECTED = "CONNECTED";
    public static final String STATE_DECLINED = "DECLINED";
    public static final String STATE_CANCELLED = "CANCELLED";
    public static final String STATE_ENDED = "ENDED";

    private String callerId;
    private String calleeId;
    private String callerName;
    private String callerAvatarUrl;
    private String calleeName;
    private String calleeAvatarUrl;
    private String state;
    private String conversationId;
    /** SDP offer (caller) — WebRTC. */
    @Nullable
    private String sdpOffer;
    /** SDP answer (callee) — WebRTC. */
    @Nullable
    private String sdpAnswer;

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getCalleeId() {
        return calleeId;
    }

    public void setCalleeId(String calleeId) {
        this.calleeId = calleeId;
    }

    @Nullable
    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(@Nullable String callerName) {
        this.callerName = callerName;
    }

    @Nullable
    public String getCallerAvatarUrl() {
        return callerAvatarUrl;
    }

    public void setCallerAvatarUrl(@Nullable String callerAvatarUrl) {
        this.callerAvatarUrl = callerAvatarUrl;
    }

    @Nullable
    public String getCalleeName() {
        return calleeName;
    }

    public void setCalleeName(@Nullable String calleeName) {
        this.calleeName = calleeName;
    }

    @Nullable
    public String getCalleeAvatarUrl() {
        return calleeAvatarUrl;
    }

    public void setCalleeAvatarUrl(@Nullable String calleeAvatarUrl) {
        this.calleeAvatarUrl = calleeAvatarUrl;
    }

    @Nullable
    public String getState() {
        return state;
    }

    public void setState(@Nullable String state) {
        this.state = state;
    }

    @Nullable
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(@Nullable String conversationId) {
        this.conversationId = conversationId;
    }

    @Nullable
    public String getSdpOffer() {
        return sdpOffer;
    }

    public void setSdpOffer(@Nullable String sdpOffer) {
        this.sdpOffer = sdpOffer;
    }

    @Nullable
    public String getSdpAnswer() {
        return sdpAnswer;
    }

    public void setSdpAnswer(@Nullable String sdpAnswer) {
        this.sdpAnswer = sdpAnswer;
    }
}
