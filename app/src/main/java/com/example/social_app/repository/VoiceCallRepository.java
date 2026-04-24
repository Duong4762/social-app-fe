package com.example.social_app.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.social_app.data.model.UserCallInbox;
import com.example.social_app.data.model.VoiceCallSession;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bắt đầu / chấp nhận / từ chối / kết thúc cuộc gọi qua Firestore (realtime).
 */
public class VoiceCallRepository {

    private final FirebaseFirestore db = FirebaseManager.getInstance().getFirestore();

    @NonNull
    public ListenerRegistration listenMyInbox(
            @NonNull String userId,
            @NonNull InboxSnapshotCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_USER_CALL_INBOX)
                .document(userId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    if (snap == null || !snap.exists()) {
                        callback.onInbox(UserCallInbox.idle());
                        return;
                    }
                    callback.onInbox(UserCallInbox.fromSnapshot(snap));
                });
    }

    @NonNull
    public ListenerRegistration listenCall(
            @NonNull String callId,
            @NonNull CallSnapshotCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    if (snap == null || !snap.exists()) {
                        callback.onCall(null);
                        return;
                    }
                    VoiceCallSession session = snap.toObject(VoiceCallSession.class);
                    callback.onCall(session);
                });
    }

    /**
     * Người gọi bắt đầu cuộc gọi: tạo session + cập nhật inbox hai bên.
     */
    @NonNull
    public Task<String> startOutgoingCall(
            @NonNull String callerId,
            @NonNull String calleeId,
            @Nullable String callerName,
            @Nullable String callerAvatarUrl,
            @Nullable String calleeName,
            @Nullable String calleeAvatarUrl,
            @Nullable String conversationId
    ) {
        String callId = UUID.randomUUID().toString();
        WriteBatch batch = db.batch();

        DocumentReference callRef = db.collection(FirebaseManager.COLLECTION_VOICE_CALLS).document(callId);
        Map<String, Object> call = new HashMap<>();
        call.put("callerId", callerId);
        call.put("calleeId", calleeId);
        call.put("callerName", callerName != null ? callerName : "");
        call.put("callerAvatarUrl", callerAvatarUrl != null ? callerAvatarUrl : "");
        call.put("calleeName", calleeName != null ? calleeName : "");
        call.put("calleeAvatarUrl", calleeAvatarUrl != null ? calleeAvatarUrl : "");
        call.put("state", VoiceCallSession.STATE_RINGING);
        call.put("createdAt", FieldValue.serverTimestamp());
        call.put("updatedAt", FieldValue.serverTimestamp());
        if (conversationId != null && !conversationId.isEmpty()) {
            call.put("conversationId", conversationId);
        }
        batch.set(callRef, call);

        batch.set(
                inboxRef(callerId),
                inboxPayload(callId, UserCallInbox.PHASE_OUTGOING, calleeId, calleeName, calleeAvatarUrl),
                SetOptions.merge()
        );
        batch.set(
                inboxRef(calleeId),
                inboxPayload(callId, UserCallInbox.PHASE_INCOMING, callerId, callerName, callerAvatarUrl),
                SetOptions.merge()
        );

        return batch.commit().continueWith(task -> {
            if (!task.isSuccessful()) {
                Exception ex = task.getException();
                throw ex != null ? ex : new RuntimeException("start call failed");
            }
            return callId;
        });
    }

    @NonNull
    public Task<Void> acceptCall(@NonNull String callId, @NonNull String myUid) {
        return loadCall(callId).continueWithTask(task -> {
            DocumentSnapshot snap = task.getResult();
            if (snap == null || !snap.exists()) {
                return Tasks.forException(new IllegalStateException("call not found"));
            }
            VoiceCallSession s = snap.toObject(VoiceCallSession.class);
            if (s == null || s.getCalleeId() == null || !s.getCalleeId().equals(myUid)) {
                return Tasks.forException(new IllegalStateException("not callee"));
            }
            if (!VoiceCallSession.STATE_RINGING.equals(s.getState())) {
                return Tasks.forException(new IllegalStateException("call not ringing"));
            }
            WriteBatch batch = db.batch();
            batch.update(
                    db.collection(FirebaseManager.COLLECTION_VOICE_CALLS).document(callId),
                    stateConnectedPatch()
            );
            String callerId = s.getCallerId();
            if (callerId == null) {
                return Tasks.forException(new IllegalStateException("no caller"));
            }
            batch.set(
                    inboxRef(callerId),
                    inboxConnectedPayload(
                            callId,
                            myUid,
                            s.getCalleeName(),
                            s.getCalleeAvatarUrl()
                    ),
                    SetOptions.merge()
            );
            batch.set(
                    inboxRef(myUid),
                    inboxConnectedPayload(
                            callId,
                            callerId,
                            s.getCallerName(),
                            s.getCallerAvatarUrl()
                    ),
                    SetOptions.merge()
            );
            return batch.commit();
        });
    }

    @NonNull
    public Task<Void> declineCall(@NonNull String callId, @NonNull String myUid) {
        return finishCallWithState(callId, myUid, VoiceCallSession.STATE_DECLINED, true);
    }

    @NonNull
    public Task<Void> cancelOutgoingCall(@NonNull String callId, @NonNull String myUid) {
        return finishCallWithState(callId, myUid, VoiceCallSession.STATE_CANCELLED, false);
    }

    @NonNull
    public Task<Void> endConnectedCall(@NonNull String callId, @NonNull String myUid) {
        return finishCallWithState(callId, myUid, VoiceCallSession.STATE_ENDED, null);
    }

    /**
     * @param mustBeCallee {@code true} decline, {@code false} cancel (caller), {@code null} either participant ends.
     */
    @NonNull
    private Task<Void> finishCallWithState(
            @NonNull String callId,
            @NonNull String myUid,
            @NonNull String newState,
            @Nullable Boolean mustBeCallee
    ) {
        return loadCall(callId).continueWithTask(task -> {
            DocumentSnapshot snap = task.getResult();
            if (snap == null || !snap.exists()) {
                return Tasks.forException(new IllegalStateException("call not found"));
            }
            VoiceCallSession s = snap.toObject(VoiceCallSession.class);
            if (s == null) {
                return Tasks.forException(new IllegalStateException("bad call"));
            }
            if (mustBeCallee != null) {
                if (mustBeCallee) {
                    if (s.getCalleeId() == null || !s.getCalleeId().equals(myUid)) {
                        return Tasks.forException(new IllegalStateException("not callee"));
                    }
                } else {
                    if (s.getCallerId() == null || !s.getCallerId().equals(myUid)) {
                        return Tasks.forException(new IllegalStateException("not caller"));
                    }
                }
            } else {
                boolean ok = myUid.equals(s.getCallerId()) || myUid.equals(s.getCalleeId());
                if (!ok) {
                    return Tasks.forException(new IllegalStateException("not participant"));
                }
            }
            String cur = s.getState();
            if (VoiceCallSession.STATE_DECLINED.equals(cur)
                    || VoiceCallSession.STATE_CANCELLED.equals(cur)
                    || VoiceCallSession.STATE_ENDED.equals(cur)) {
                return clearInboxesOnly(s.getCallerId(), s.getCalleeId());
            }

            if (VoiceCallSession.STATE_ENDED.equals(newState)) {
                if (!VoiceCallSession.STATE_CONNECTED.equals(cur)) {
                    return clearInboxesOnly(s.getCallerId(), s.getCalleeId());
                }
            } else if (VoiceCallSession.STATE_DECLINED.equals(newState)
                    || VoiceCallSession.STATE_CANCELLED.equals(newState)) {
                if (!VoiceCallSession.STATE_RINGING.equals(cur)) {
                    return clearInboxesOnly(s.getCallerId(), s.getCalleeId());
                }
            }

            WriteBatch batch = db.batch();
            Map<String, Object> patch = new HashMap<>();
            patch.put("state", newState);
            patch.put("updatedAt", FieldValue.serverTimestamp());
            patch.put("sdpOffer", FieldValue.delete());
            patch.put("sdpAnswer", FieldValue.delete());
            batch.update(db.collection(FirebaseManager.COLLECTION_VOICE_CALLS).document(callId), patch);

            String callerId = s.getCallerId();
            String calleeId = s.getCalleeId();
            if (callerId != null) {
                batch.set(inboxRef(callerId), idleInboxPayload(), SetOptions.merge());
            }
            if (calleeId != null) {
                batch.set(inboxRef(calleeId), idleInboxPayload(), SetOptions.merge());
            }
            return batch.commit();
        });
    }

    @NonNull
    private Task<Void> clearInboxesOnly(@Nullable String callerId, @Nullable String calleeId) {
        WriteBatch batch = db.batch();
        if (callerId != null) {
            batch.set(inboxRef(callerId), idleInboxPayload(), SetOptions.merge());
        }
        if (calleeId != null) {
            batch.set(inboxRef(calleeId), idleInboxPayload(), SetOptions.merge());
        }
        return batch.commit();
    }

    @NonNull
    private Task<DocumentSnapshot> loadCall(@NonNull String callId) {
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .get();
    }

    @NonNull
    private DocumentReference inboxRef(@NonNull String uid) {
        return db.collection(FirebaseManager.COLLECTION_USER_CALL_INBOX).document(uid);
    }

    @NonNull
    private Map<String, Object> inboxPayload(
            @NonNull String callId,
            @NonNull String phase,
            @Nullable String peerUid,
            @Nullable String peerName,
            @Nullable String peerAvatarUrl
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("callId", callId);
        m.put("phase", phase);
        m.put("peerUid", peerUid != null ? peerUid : "");
        m.put("peerName", peerName != null ? peerName : "");
        m.put("peerAvatarUrl", peerAvatarUrl != null ? peerAvatarUrl : "");
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    @NonNull
    private Map<String, Object> inboxConnectedPayload(
            @NonNull String callId,
            @Nullable String peerUid,
            @Nullable String peerName,
            @Nullable String peerAvatarUrl
    ) {
        return inboxPayload(callId, UserCallInbox.PHASE_CONNECTED, peerUid, peerName, peerAvatarUrl);
    }

    @NonNull
    private Map<String, Object> idleInboxPayload() {
        Map<String, Object> m = new HashMap<>();
        m.put("phase", UserCallInbox.PHASE_IDLE);
        m.put("callId", "");
        m.put("peerUid", "");
        m.put("peerName", "");
        m.put("peerAvatarUrl", "");
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    @NonNull
    private Map<String, Object> stateConnectedPatch() {
        Map<String, Object> m = new HashMap<>();
        m.put("state", VoiceCallSession.STATE_CONNECTED);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    /** Ghi SDP offer (caller) lên document cuộc gọi — callee lắng nghe để setRemoteDescription. */
    @NonNull
    public Task<Void> publishSdpOffer(@NonNull String callId, @NonNull String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdpOffer", sdp);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .set(m, SetOptions.merge());
    }

    /** Ghi SDP answer (callee). */
    @NonNull
    public Task<Void> publishSdpAnswer(@NonNull String callId, @NonNull String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdpAnswer", sdp);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .set(m, SetOptions.merge());
    }

    /** Đẩy một ICE candidate (trickle) — đối phương lắng nghe subcollection. */
    @NonNull
    public Task<Void> pushIceCandidate(
            @NonNull String callId,
            @NonNull String fromUid,
            @Nullable String sdpMid,
            int sdpMLineIndex,
            @NonNull String candidateSdp
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("fromUid", fromUid);
        m.put("sdpMid", sdpMid != null ? sdpMid : "");
        m.put("sdpMLineIndex", sdpMLineIndex);
        m.put("candidate", candidateSdp);
        m.put("createdAt", FieldValue.serverTimestamp());
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .collection(FirebaseManager.SUBCOLLECTION_VOICE_CALL_WEBRTC_ICE)
                .add(m)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        throw ex != null ? ex : new RuntimeException("ice push failed");
                    }
                    return null;
                });
    }

    /**
     * ICE từ cả hai phía; callback nhận từng candidate mới (chỉ xử lý {@link DocumentChange.Type#ADDED}).
     */
    @NonNull
    public ListenerRegistration listenIceCandidates(
            @NonNull String callId,
            @NonNull IceCandidateCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_VOICE_CALLS)
                .document(callId)
                .collection(FirebaseManager.SUBCOLLECTION_VOICE_CALL_WEBRTC_ICE)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    for (DocumentChange dc : snap.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) {
                            continue;
                        }
                        QueryDocumentSnapshot doc = dc.getDocument();
                        String from = doc.getString("fromUid");
                        String cand = doc.getString("candidate");
                        String mid = doc.getString("sdpMid");
                        Long idx = doc.getLong("sdpMLineIndex");
                        if (from != null && cand != null && idx != null) {
                            callback.onIceCandidate(from, mid, idx.intValue(), cand);
                        }
                    }
                });
    }

    public interface InboxSnapshotCallback {
        void onInbox(@NonNull UserCallInbox inbox);

        void onError(@NonNull Exception e);
    }

    public interface CallSnapshotCallback {
        void onCall(@Nullable VoiceCallSession session);

        void onError(@NonNull Exception e);
    }

    public interface IceCandidateCallback {
        void onIceCandidate(
                @NonNull String fromUid,
                @Nullable String sdpMid,
                int sdpMLineIndex,
                @NonNull String candidateSdp);

        void onError(@NonNull Exception e);
    }
}
