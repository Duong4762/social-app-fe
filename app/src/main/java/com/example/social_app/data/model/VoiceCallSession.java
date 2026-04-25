package com.example.social_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.Exclude;

import java.util.Collections;
import java.util.List;

/**
 * Document {@code voice_calls/{callId}} — gọi 1-1 hoặc gọi nhóm (mesh).
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

    /** Gọi nhóm: true. */
    @Nullable
    private Boolean groupCall;
    @Nullable
    private String groupDisplayName;
    @Nullable
    private String groupConversationId;
    @Nullable
    private List<String> groupMemberIds;
    @Nullable
    private List<String> joinedMemberIds;

    @Exclude
    public boolean isGroupCall() {
        return Boolean.TRUE.equals(groupCall);
    }

    @Nullable
    public Boolean getGroupCall() {
        return groupCall;
    }

    public void setGroupCall(@Nullable Boolean groupCall) {
        this.groupCall = groupCall;
    }

    @Nullable
    public String getGroupDisplayName() {
        return groupDisplayName;
    }

    public void setGroupDisplayName(@Nullable String groupDisplayName) {
        this.groupDisplayName = groupDisplayName;
    }

    @Nullable
    public String getGroupConversationId() {
        return groupConversationId;
    }

    public void setGroupConversationId(@Nullable String groupConversationId) {
        this.groupConversationId = groupConversationId;
    }

    @NonNull
    public List<String> getGroupMemberIds() {
        return groupMemberIds != null ? groupMemberIds : Collections.emptyList();
    }

    public void setGroupMemberIds(@Nullable List<String> groupMemberIds) {
        this.groupMemberIds = groupMemberIds;
    }

    @NonNull
    public List<String> getJoinedMemberIds() {
        return joinedMemberIds != null ? joinedMemberIds : Collections.emptyList();
    }

    public void setJoinedMemberIds(@Nullable List<String> joinedMemberIds) {
        this.joinedMemberIds = joinedMemberIds;
    }

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
