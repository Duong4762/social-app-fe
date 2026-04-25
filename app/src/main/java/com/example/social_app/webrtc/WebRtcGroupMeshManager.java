package com.example.social_app.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.social_app.repository.VoiceCallRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Giữ một {@link WebRtcMeshPeerSession} cho mỗi peer đã tham gia (mesh full).
 */
public final class WebRtcGroupMeshManager {

    private final Context appContext;
    private final VoiceCallRepository repository;
    private final String callId;
    private final String myUid;
    private final Runnable onError;
    private final Map<String, WebRtcMeshPeerSession> peers = new HashMap<>();
    private final WebRtcMeshMediaShare mediaShare;

    public WebRtcGroupMeshManager(
            @NonNull Context context,
            @NonNull VoiceCallRepository repository,
            @NonNull String callId,
            @NonNull String myUid,
            @NonNull Runnable onError
    ) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.callId = callId;
        this.myUid = myUid;
        this.onError = onError;
        this.mediaShare = new WebRtcMeshMediaShare(context);
    }

    public void setMeshVideoCallback(@Nullable WebRtcMeshMediaShare.VideoCallback callback) {
        mediaShare.setCallback(callback);
    }

    public void setLocalCameraEnabled(boolean enabled) {
        mediaShare.setCameraEnabled(enabled);
    }

    public void switchLocalCamera() {
        mediaShare.switchCamera();
    }

    /** Đồng bộ danh sách uid đã trong cuộc gọi (đã CONNECTED trên session). */
    public synchronized void syncRemotePeers(@NonNull Set<String> joinedIncludingSelf) {
        for (String remote : joinedIncludingSelf) {
            if (remote == null || remote.equals(myUid)) {
                continue;
            }
            if (!peers.containsKey(remote)) {
                WebRtcMeshPeerSession s = new WebRtcMeshPeerSession(
                        appContext,
                        repository,
                        callId,
                        myUid,
                        remote,
                        onError,
                        mediaShare
                );
                s.start();
                peers.put(remote, s);
            }
        }
        for (String existing : new HashMap<>(peers).keySet()) {
            if (!joinedIncludingSelf.contains(existing) || existing.equals(myUid)) {
                WebRtcMeshPeerSession s = peers.remove(existing);
                if (s != null) {
                    s.dispose();
                }
            }
        }
    }

    public synchronized void dispose() {
        for (WebRtcMeshPeerSession s : peers.values()) {
            s.dispose();
        }
        peers.clear();
        mediaShare.dispose();
    }
}
