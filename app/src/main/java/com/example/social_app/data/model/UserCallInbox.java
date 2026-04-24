package com.example.social_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Document {@code user_call_inbox/{userId}} — hộp thư cuộc gọi cho từng user.
 */
public class UserCallInbox {

    public static final String PHASE_IDLE = "idle";
    public static final String PHASE_INCOMING = "incoming";
    public static final String PHASE_OUTGOING = "outgoing";
    public static final String PHASE_CONNECTED = "connected";

    private String callId;
    private String phase;
    private String peerUid;
    private String peerName;
    private String peerAvatarUrl;

    @NonNull
    public static UserCallInbox idle() {
        UserCallInbox i = new UserCallInbox();
        i.phase = PHASE_IDLE;
        i.callId = "";
        return i;
    }

    @NonNull
    public static UserCallInbox fromSnapshot(@NonNull DocumentSnapshot snap) {
        UserCallInbox i = new UserCallInbox();
        i.callId = snap.getString("callId");
        i.phase = snap.getString("phase");
        i.peerUid = snap.getString("peerUid");
        i.peerName = snap.getString("peerName");
        i.peerAvatarUrl = snap.getString("peerAvatarUrl");
        if (i.phase == null) {
            i.phase = PHASE_IDLE;
        }
        if (i.callId == null) {
            i.callId = "";
        }
        return i;
    }

    public boolean isActive() {
        return callId != null && !callId.isEmpty()
                && phase != null
                && !PHASE_IDLE.equals(phase);
    }

    @Nullable
    public String getCallId() {
        return callId;
    }

    public void setCallId(@Nullable String callId) {
        this.callId = callId;
    }

    @Nullable
    public String getPhase() {
        return phase;
    }

    public void setPhase(@Nullable String phase) {
        this.phase = phase;
    }

    @Nullable
    public String getPeerUid() {
        return peerUid;
    }

    public void setPeerUid(@Nullable String peerUid) {
        this.peerUid = peerUid;
    }

    @Nullable
    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(@Nullable String peerName) {
        this.peerName = peerName;
    }

    @Nullable
    public String getPeerAvatarUrl() {
        return peerAvatarUrl;
    }

    public void setPeerAvatarUrl(@Nullable String peerAvatarUrl) {
        this.peerAvatarUrl = peerAvatarUrl;
    }
}
