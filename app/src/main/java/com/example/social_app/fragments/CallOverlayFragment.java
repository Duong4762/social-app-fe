package com.example.social_app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.adapters.CallParticipantGridAdapter;
import com.example.social_app.data.model.User;
import com.example.social_app.data.model.UserCallInbox;
import com.example.social_app.data.model.VoiceCallSession;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.repository.VoiceCallRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.webrtc.WebRtcAudioSession;
import com.example.social_app.webrtc.WebRtcGroupMeshManager;
import com.example.social_app.webrtc.WebRtcMeshMediaShare;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Overlay toàn màn hình: cuộc gọi đến / đang gọi / đang nói chuyện. Lắng nghe {@code user_call_inbox}.
 */
public class CallOverlayFragment extends Fragment {

    private static final int REQ_CALL_MEDIA = 9002;
    private static final int REQ_CAMERA_FOR_CALL = 9003;
    /** Không có frame trong khoảng này → tắt chế độ video remote (SDP vẫn có thể gửi vài frame đen / keyframe rác). */
    private static final long P2P_REMOTE_VIDEO_IDLE_MS = 1500L;
    /** Khoảng cách tối đa giữa hai frame để vẫn tính vào một “chuỗi” hợp lệ. */
    private static final long P2P_REMOTE_BURST_GAP_MS = 350L;
    /** Cần ít nhất số frame liên tiếp (trong burst) mới bật UI video — tránh 1–2 frame rồi fullscreen đen. */
    private static final int P2P_REMOTE_MIN_FRAMES_FOR_UI = 12;

    private VoiceCallRepository repository;
    private ListenerRegistration inboxRegistration;
    private ListenerRegistration callRegistration;
    private String myUid;
    private UserCallInbox lastInbox;
    private String listenedCallId;
    private String previousVoiceState;
    private String previousGroupJoinedSig = "";

    private View overlayRoot;
    private LinearLayout singlePeerBlock;
    private LinearLayout groupHeader;
    private ShapeableImageView groupIcon;
    private TextView groupNameView;
    private ShapeableImageView peerAvatar;
    private TextView peerNameView;
    private TextView statusView;
    private RecyclerView participantGrid;
    private View rowIncoming;
    private View rowActive;
    private MaterialButton btnAccept;
    private MaterialButton btnDecline;
    private ImageButton btnCamera;
    private ImageButton btnSwitchCamera;
    private ImageButton btnSpeaker;
    private ImageButton btnHangup;

    private SurfaceViewRenderer remoteVideoView;
    private SurfaceViewRenderer localPipView;
    private boolean rtcVideoRenderersInited;
    @Nullable
    private VideoTrack p2pRemoteVideoTrack;
    @Nullable
    private VideoTrack p2pLocalVideoTrack;
    private boolean p2pSelfCameraOn;
    /** true chỉ khi đã nhận đủ frame liên tiếp (burst) — tránh fullscreen đen do keyframe/thoại-only. */
    private boolean p2pRemoteReceivingVideo;
    private long p2pRemoteVideoLastFrameMs;
    private long p2pRemoteLastBurstFrameMs;
    private int p2pRemoteFrameStreak;
    private final Runnable p2pRemoteVideoIdleRunnable = this::onP2pRemoteVideoIdleCheck;
    private final VideoSink p2pRemoteFrameWatcher = new VideoSink() {
        @Override
        public void onFrame(VideoFrame frame) {
            long now = SystemClock.elapsedRealtime();
            CallOverlayFragment host = CallOverlayFragment.this;
            if (now - host.p2pRemoteLastBurstFrameMs > P2P_REMOTE_BURST_GAP_MS) {
                host.p2pRemoteFrameStreak = 0;
            }
            host.p2pRemoteLastBurstFrameMs = now;
            host.p2pRemoteVideoLastFrameMs = now;
            host.p2pRemoteFrameStreak++;

            boolean wasReceiving = host.p2pRemoteReceivingVideo;
            boolean crossed = host.p2pRemoteFrameStreak >= P2P_REMOTE_MIN_FRAMES_FOR_UI;
            if (crossed && !wasReceiving) {
                host.p2pRemoteReceivingVideo = true;
            }

            View anchor = host.overlayRoot != null ? host.overlayRoot
                    : (host.remoteVideoView != null ? host.remoteVideoView : null);
            if (anchor != null) {
                anchor.removeCallbacks(host.p2pRemoteVideoIdleRunnable);
                anchor.postDelayed(host.p2pRemoteVideoIdleRunnable, P2P_REMOTE_VIDEO_IDLE_MS);
            }
            if (crossed && !wasReceiving && anchor != null) {
                anchor.post(host::applyP2pVideoLayout);
            }
        }
    };

    private CallParticipantGridAdapter gridAdapter;

    private AudioManager audioManager;
    private boolean speakerOn;
    private Ringtone incomingRingtone;

    @Nullable
    private VoiceCallSession lastVoiceSession;
    @Nullable
    private WebRtcAudioSession webRtcSession;
    @Nullable
    private WebRtcGroupMeshManager meshManager;
    @Nullable
    private String webRtcActiveCallId;

    public CallOverlayFragment() {
        super(R.layout.fragment_call_overlay);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VoiceCallRepository();
        overlayRoot = view.findViewById(R.id.call_overlay_root);
        singlePeerBlock = view.findViewById(R.id.call_single_peer_block);
        groupHeader = view.findViewById(R.id.call_group_header);
        groupIcon = view.findViewById(R.id.call_group_icon);
        peerAvatar = view.findViewById(R.id.call_peer_avatar);
        groupNameView = view.findViewById(R.id.call_group_name);
        peerNameView = view.findViewById(R.id.call_peer_name);
        statusView = view.findViewById(R.id.call_status_text);
        participantGrid = view.findViewById(R.id.call_participant_grid);
        rowIncoming = view.findViewById(R.id.call_row_incoming_actions);
        rowActive = view.findViewById(R.id.call_row_active_controls);
        btnAccept = view.findViewById(R.id.call_btn_accept);
        btnDecline = view.findViewById(R.id.call_btn_decline);
        btnCamera = view.findViewById(R.id.call_btn_camera);
        btnSwitchCamera = view.findViewById(R.id.call_btn_switch_camera);
        btnSpeaker = view.findViewById(R.id.call_btn_speaker);
        btnHangup = view.findViewById(R.id.call_btn_hangup);
        remoteVideoView = view.findViewById(R.id.call_remote_video);
        localPipView = view.findViewById(R.id.call_local_pip);

        gridAdapter = new CallParticipantGridAdapter();
        participantGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        participantGrid.setAdapter(gridAdapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            hideFullOverlay();
            return;
        }
        myUid = user.getUid();
        audioManager = (AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);

        btnAccept.setOnClickListener(v -> onAcceptClicked());
        btnDecline.setOnClickListener(v -> onDeclineClicked());
        btnHangup.setOnClickListener(v -> onHangupClicked());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> onToggleCameraClicked());
        }
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> onSwitchCameraClicked());
        }

        hideFullOverlay();

        inboxRegistration = repository.listenMyInbox(myUid, new VoiceCallRepository.InboxSnapshotCallback() {
            @Override
            public void onInbox(@NonNull UserCallInbox inbox) {
                applyInbox(inbox);
            }

            @Override
            public void onError(@NonNull Exception e) {
                // im lặng: lỗi mạng tạm thời
            }
        });
    }

    @Override
    public void onDestroyView() {
        disposeWebRtc();
        releaseRtcVideoViews();
        stopInboxListener();
        stopCallListener();
        stopIncomingRingtone();
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        overlayRoot = null;
        singlePeerBlock = null;
        groupHeader = null;
        groupIcon = null;
        groupNameView = null;
        peerAvatar = null;
        peerNameView = null;
        statusView = null;
        participantGrid = null;
        gridAdapter = null;
        rowIncoming = null;
        rowActive = null;
        btnAccept = null;
        btnDecline = null;
        btnSpeaker = null;
        btnHangup = null;
        btnCamera = null;
        btnSwitchCamera = null;
        remoteVideoView = null;
        localPipView = null;
        super.onDestroyView();
    }

    private void applyInbox(@NonNull UserCallInbox inbox) {
        lastInbox = inbox;
        if (!isAdded()) {
            return;
        }
        if (!inbox.isActive()) {
            disposeWebRtc();
            stopIncomingRingtone();
            stopCallListener();
            hideFullOverlay();
            return;
        }

        showFullOverlay();
        boolean group = inbox.isGroupCall();
        if (group) {
            if (singlePeerBlock != null) {
                singlePeerBlock.setVisibility(View.GONE);
            }
            if (groupHeader != null) {
                groupHeader.setVisibility(View.VISIBLE);
            }
            if (groupNameView != null) {
                groupNameView.setText(nonEmpty(inbox.getGroupDisplayName(), getString(R.string.unknown_user)));
            }
            if (groupIcon != null) {
                groupIcon.setImageResource(R.drawable.ic_group_chat);
            }
            refreshParticipantTilesFromSession();
        } else {
            if (singlePeerBlock != null) {
                singlePeerBlock.setVisibility(View.VISIBLE);
            }
            if (groupHeader != null) {
                groupHeader.setVisibility(View.GONE);
            }
            if (participantGrid != null) {
                participantGrid.setVisibility(View.GONE);
            }
            UserAvatarLoader.load(peerAvatar, emptyToNull(inbox.getPeerAvatarUrl()));
            peerNameView.setText(nonEmpty(inbox.getPeerName(), getString(R.string.unknown_user)));
        }

        String phase = inbox.getPhase();
        if (UserCallInbox.PHASE_INCOMING.equals(phase)) {
            rowIncoming.setVisibility(View.VISIBLE);
            rowActive.setVisibility(View.GONE);
            if (statusView != null) {
                statusView.setVisibility(View.VISIBLE);
            }
            if (group) {
                statusView.setText(R.string.call_status_group_incoming);
            } else {
                statusView.setText(R.string.call_status_incoming);
            }
            startIncomingRingtoneIfNeeded();
            attachCallListener(inbox.getCallId());
        } else if (UserCallInbox.PHASE_OUTGOING.equals(phase)) {
            rowIncoming.setVisibility(View.GONE);
            rowActive.setVisibility(View.VISIBLE);
            if (statusView != null) {
                statusView.setVisibility(View.VISIBLE);
            }
            if (group) {
                statusView.setText(R.string.call_status_group_waiting);
            } else {
                statusView.setText(R.string.call_status_waiting);
            }
            stopIncomingRingtone();
            attachCallListener(inbox.getCallId());
        } else if (UserCallInbox.PHASE_CONNECTED.equals(phase)) {
            rowIncoming.setVisibility(View.GONE);
            rowActive.setVisibility(View.VISIBLE);
            statusView.setText(R.string.call_status_connected);
            if (!group) {
                syncP2pCallStatusLineForVideo();
            } else if (statusView != null) {
                statusView.setVisibility(View.VISIBLE);
            }
            stopIncomingRingtone();
            attachCallListener(inbox.getCallId());
            maybeStartWebRtcAudio();
        } else {
            rowIncoming.setVisibility(View.GONE);
            rowActive.setVisibility(View.GONE);
        }
        updateSpeakerButtonUi();
    }

    private void attachCallListener(@Nullable String callId) {
        if (callId == null || callId.isEmpty()) {
            return;
        }
        if (callId.equals(listenedCallId) && callRegistration != null) {
            return;
        }
        stopCallListener();
        listenedCallId = callId;
        previousVoiceState = null;
        previousGroupJoinedSig = "";
        callRegistration = repository.listenCall(callId, new VoiceCallRepository.CallSnapshotCallback() {
            @Override
            public void onCall(@Nullable VoiceCallSession session) {
                lastVoiceSession = session;
                if (session == null) {
                    return;
                }
                handleVoiceCallSnapshot(session);
            }

            @Override
            public void onError(@NonNull Exception e) {
                // ignore
            }
        });
    }

    private void handleVoiceCallSnapshot(@NonNull VoiceCallSession session) {
        String state = session.getState();
        if (state == null) {
            return;
        }

        if (session.isGroupCall()) {
            String joinedSig = joinSignature(session.getJoinedMemberIds());
            boolean sameState = state.equals(previousVoiceState);
            boolean sameJoin = joinedSig.equals(previousGroupJoinedSig);
            if (sameState && sameJoin) {
                return;
            }
            previousVoiceState = state;
            previousGroupJoinedSig = joinedSig;

            if (VoiceCallSession.STATE_DECLINED.equals(state) && myUid.equals(session.getCallerId())) {
                Toast.makeText(requireContext(), R.string.call_peer_declined, Toast.LENGTH_SHORT).show();
            } else if (VoiceCallSession.STATE_CANCELLED.equals(state)) {
                Toast.makeText(requireContext(), R.string.call_peer_cancelled, Toast.LENGTH_SHORT).show();
            } else if (VoiceCallSession.STATE_ENDED.equals(state)) {
                Toast.makeText(requireContext(), R.string.call_ended, Toast.LENGTH_SHORT).show();
            }

            refreshParticipantTilesFromSession();
            if (VoiceCallSession.STATE_CONNECTED.equals(state)) {
                maybeStartWebRtcAudio();
            }
            return;
        }

        if (state.equals(previousVoiceState)) {
            return;
        }
        previousVoiceState = state;

        if (VoiceCallSession.STATE_DECLINED.equals(state) && myUid.equals(session.getCallerId())) {
            Toast.makeText(requireContext(), R.string.call_peer_declined, Toast.LENGTH_SHORT).show();
        } else if (VoiceCallSession.STATE_CANCELLED.equals(state) && myUid.equals(session.getCalleeId())) {
            Toast.makeText(requireContext(), R.string.call_peer_cancelled, Toast.LENGTH_SHORT).show();
        } else if (VoiceCallSession.STATE_ENDED.equals(state)) {
            Toast.makeText(requireContext(), R.string.call_ended, Toast.LENGTH_SHORT).show();
        }

        if (VoiceCallSession.STATE_CONNECTED.equals(state)) {
            maybeStartWebRtcAudio();
        }
    }

    private void refreshParticipantTilesFromSession() {
        if (participantGrid == null || gridAdapter == null || lastInbox == null || !lastInbox.isGroupCall()) {
            if (participantGrid != null) {
                participantGrid.setVisibility(View.GONE);
            }
            return;
        }
        participantGrid.setVisibility(View.VISIBLE);

        List<String> uids = new ArrayList<>();
        if (lastVoiceSession != null) {
            uids.addAll(lastVoiceSession.getJoinedMemberIds());
        }
        if (uids.isEmpty() && UserCallInbox.PHASE_OUTGOING.equals(lastInbox.getPhase())) {
            uids.add(myUid);
        }
        Collections.sort(uids);
        if (uids.isEmpty()) {
            gridAdapter.setTiles(Collections.emptyList());
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<CallParticipantGridAdapter.Tile> acc = new ArrayList<>();
        for (int i = 0; i < uids.size(); i++) {
            acc.add(null);
        }
        AtomicInteger done = new AtomicInteger(0);
        for (int i = 0; i < uids.size(); i++) {
            final int index = i;
            String uid = uids.get(i);
            db.collection(FirebaseManager.COLLECTION_USERS).document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (!isAdded()) {
                            return;
                        }
                        String display = uid;
                        String avatar = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            DocumentSnapshot doc = task.getResult();
                            User u = doc.toObject(User.class);
                            if (u != null) {
                                if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
                                    display = u.getFullName().trim();
                                } else if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) {
                                    display = u.getUsername().trim();
                                }
                                if (u.getAvatarUrl() != null && !u.getAvatarUrl().trim().isEmpty()) {
                                    avatar = u.getAvatarUrl().trim();
                                }
                            }
                        }
                        if (uid.equals(myUid)) {
                            FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
                            if (me != null && me.getDisplayName() != null && !me.getDisplayName().trim().isEmpty()) {
                                display = me.getDisplayName().trim();
                            }
                        }
                        synchronized (acc) {
                            acc.set(index, new CallParticipantGridAdapter.Tile(uid, display, avatar));
                        }
                        if (done.incrementAndGet() == uids.size()) {
                            List<CallParticipantGridAdapter.Tile> ordered = new ArrayList<>();
                            synchronized (acc) {
                                for (CallParticipantGridAdapter.Tile t : acc) {
                                    if (t != null) {
                                        ordered.add(t);
                                    }
                                }
                            }
                            if (isAdded() && gridAdapter != null) {
                                gridAdapter.setTiles(ordered);
                            }
                        }
                    });
        }
    }

    @NonNull
    private static String joinSignature(@NonNull List<String> ids) {
        List<String> c = new ArrayList<>(ids);
        Collections.sort(c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(c.get(i));
        }
        return sb.toString();
    }

    private void maybeStartWebRtcAudio() {
        if (lastInbox == null || !lastInbox.isActive()) {
            return;
        }
        if (!UserCallInbox.PHASE_CONNECTED.equals(lastInbox.getPhase())) {
            return;
        }
        if (lastVoiceSession == null || !VoiceCallSession.STATE_CONNECTED.equals(lastVoiceSession.getState())) {
            return;
        }
        String callId = lastInbox.getCallId();
        if (callId == null || callId.isEmpty()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CALL_MEDIA);
            return;
        }

        if (lastVoiceSession.isGroupCall()) {
            if (meshManager != null && callId.equals(webRtcActiveCallId)) {
                meshManager.syncRemotePeers(new HashSet<>(lastVoiceSession.getJoinedMemberIds()));
                return;
            }
            startGroupMeshInternal(callId);
            return;
        }

        if (webRtcSession != null && callId.equals(webRtcActiveCallId)) {
            return;
        }
        startWebRtcAudioInternal(callId);
    }

    private void startGroupMeshInternal(@NonNull String callId) {
        if (lastVoiceSession == null) {
            return;
        }
        disposeWebRtc();
        webRtcActiveCallId = callId;
        meshManager = new WebRtcGroupMeshManager(
                requireContext(),
                repository,
                callId,
                myUid,
                () -> Toast.makeText(requireContext(), R.string.call_webrtc_error, Toast.LENGTH_SHORT).show()
        );
        meshManager.setMeshVideoCallback(new WebRtcMeshMediaShare.VideoCallback() {
            @Override
            public void onEglReady(@NonNull EglBase eglBase) {
                if (!isAdded() || gridAdapter == null) {
                    return;
                }
                gridAdapter.setEglBaseContext(eglBase.getEglBaseContext());
                gridAdapter.setMyUid(myUid);
            }

            @Override
            public void onLocalVideoTrack(@NonNull VideoTrack track) {
                if (!isAdded() || gridAdapter == null) {
                    return;
                }
                gridAdapter.setLocalSelfVideoTrack(track);
            }

            @Override
            public void onRemoteVideoTrack(@NonNull String remoteUid, @NonNull VideoTrack track) {
                if (!isAdded() || gridAdapter == null) {
                    return;
                }
                gridAdapter.setRemoteVideoForUser(remoteUid, track);
            }

            @Override
            public void onRemoteVideoTrackRemoved(@NonNull String remoteUid) {
                if (!isAdded() || gridAdapter == null) {
                    return;
                }
                gridAdapter.clearRemoteVideoForUser(remoteUid);
            }
        });
        meshManager.syncRemotePeers(new HashSet<>(lastVoiceSession.getJoinedMemberIds()));
    }

    private void startWebRtcAudioInternal(@NonNull String callId) {
        if (lastVoiceSession == null) {
            return;
        }
        disposeWebRtc();
        webRtcActiveCallId = callId;
        String callerId = lastVoiceSession.getCallerId();
        boolean isCaller = callerId != null && myUid.equals(callerId);
        webRtcSession = new WebRtcAudioSession(
                requireContext(),
                repository,
                callId,
                myUid,
                isCaller,
                () -> Toast.makeText(requireContext(), R.string.call_webrtc_error, Toast.LENGTH_SHORT).show()
        );
        webRtcSession.setP2pCallVideoCallback(new WebRtcAudioSession.P2pCallVideoCallback() {
            @Override
            public void onEglReady(@NonNull EglBase egl) {
                if (!isAdded()) {
                    return;
                }
                initP2pVideoRenderers(egl.getEglBaseContext());
            }

            @Override
            public void onLocalVideoTrack(@NonNull VideoTrack track) {
                if (!isAdded()) {
                    return;
                }
                p2pLocalVideoTrack = track;
                View v = remoteVideoView != null ? remoteVideoView : overlayRoot;
                if (v != null) {
                    v.post(CallOverlayFragment.this::applyP2pVideoLayout);
                }
            }

            @Override
            public void onRemoteVideoTrack(@NonNull VideoTrack track) {
                if (!isAdded()) {
                    return;
                }
                detachP2pRemoteVideoWatcher();
                p2pRemoteVideoTrack = track;
                p2pRemoteReceivingVideo = false;
                p2pRemoteVideoLastFrameMs = 0;
                p2pRemoteLastBurstFrameMs = 0L;
                p2pRemoteFrameStreak = 0;
                track.addSink(p2pRemoteFrameWatcher);
                View v = remoteVideoView != null ? remoteVideoView : overlayRoot;
                if (v != null) {
                    v.post(CallOverlayFragment.this::applyP2pVideoLayout);
                }
            }

            @Override
            public void onRemoteVideoCleared() {
                if (!isAdded()) {
                    return;
                }
                clearP2pVideoSinks();
                detachP2pRemoteVideoWatcher();
                p2pRemoteVideoTrack = null;
                p2pRemoteReceivingVideo = false;
                p2pRemoteFrameStreak = 0;
                p2pRemoteLastBurstFrameMs = 0L;
                View v = remoteVideoView != null ? remoteVideoView : overlayRoot;
                if (v != null) {
                    v.post(CallOverlayFragment.this::applyP2pVideoLayout);
                }
            }
        });
        webRtcSession.start();
    }

    private void disposeWebRtc() {
        clearCallVideoUi();
        if (meshManager != null) {
            meshManager.dispose();
            meshManager = null;
        }
        if (webRtcSession != null) {
            webRtcSession.dispose();
            webRtcSession = null;
        }
        webRtcActiveCallId = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALL_MEDIA) {
            boolean micOk = false;
            for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    micOk = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    break;
                }
            }
            if (micOk) {
                maybeStartWebRtcAudio();
            } else {
                Toast.makeText(requireContext(), R.string.call_need_mic_permission, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_CAMERA_FOR_CALL) {
            boolean camOk = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (camOk) {
                disposeWebRtc();
                maybeStartWebRtcAudio();
            } else {
                Toast.makeText(requireContext(), R.string.call_need_camera_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onAcceptClicked() {
        if (lastInbox == null || lastInbox.getCallId() == null) {
            return;
        }
        stopIncomingRingtone();
        Task<Void> task = lastInbox.isGroupCall()
                ? repository.acceptGroupVoiceCall(lastInbox.getCallId(), myUid)
                : repository.acceptCall(lastInbox.getCallId(), myUid);
        task.addOnFailureListener(e -> Toast.makeText(
                requireContext(),
                R.string.call_action_failed,
                Toast.LENGTH_SHORT
        ).show());
    }

    private void onDeclineClicked() {
        if (lastInbox == null || lastInbox.getCallId() == null) {
            return;
        }
        stopIncomingRingtone();
        repository.declineCall(lastInbox.getCallId(), myUid)
                .addOnFailureListener(e -> Toast.makeText(
                        requireContext(),
                        R.string.call_action_failed,
                        Toast.LENGTH_SHORT
                ).show());
    }

    private void onHangupClicked() {
        if (lastInbox == null || lastInbox.getCallId() == null) {
            return;
        }
        String phase = lastInbox.getPhase();
        String callId = lastInbox.getCallId();
        stopIncomingRingtone();
        if (UserCallInbox.PHASE_INCOMING.equals(phase)) {
            repository.declineCall(callId, myUid)
                    .addOnFailureListener(e -> Toast.makeText(
                            requireContext(),
                            R.string.call_action_failed,
                            Toast.LENGTH_SHORT
                    ).show());
        } else if (UserCallInbox.PHASE_OUTGOING.equals(phase)) {
            repository.cancelOutgoingCall(callId, myUid)
                    .addOnFailureListener(e -> Toast.makeText(
                            requireContext(),
                            R.string.call_action_failed,
                            Toast.LENGTH_SHORT
                    ).show());
        } else if (UserCallInbox.PHASE_CONNECTED.equals(phase)) {
            repository.endConnectedCall(callId, myUid)
                    .addOnFailureListener(e -> Toast.makeText(
                            requireContext(),
                            R.string.call_action_failed,
                            Toast.LENGTH_SHORT
                    ).show());
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(speakerOn);
        }
        updateSpeakerButtonUi();
    }

    private void updateSpeakerButtonUi() {
        if (btnSpeaker == null) {
            return;
        }
        btnSpeaker.setAlpha(speakerOn ? 1f : 0.55f);
    }

    private void initP2pVideoRenderers(@NonNull EglBase.Context eglCtx) {
        if (remoteVideoView == null || localPipView == null || rtcVideoRenderersInited) {
            return;
        }
        remoteVideoView.init(eglCtx, null);
        remoteVideoView.setMirror(false);
        remoteVideoView.setEnableHardwareScaler(true);
        remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        // Không dùng setZOrderMediaOverlay(true) cho full-screen: SurfaceView có thể vẽ lên trên
        // toàn bộ overlay (kể cả nút), nhìn như màn hình đen kịt.
        remoteVideoView.setZOrderMediaOverlay(false);
        localPipView.init(eglCtx, null);
        localPipView.setMirror(true);
        localPipView.setEnableHardwareScaler(true);
        localPipView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localPipView.setZOrderMediaOverlay(true);
        rtcVideoRenderersInited = true;
    }

    private void clearP2pVideoSinks() {
        if (p2pRemoteVideoTrack != null && remoteVideoView != null) {
            p2pRemoteVideoTrack.removeSink(remoteVideoView);
        }
        if (p2pLocalVideoTrack != null && localPipView != null) {
            p2pLocalVideoTrack.removeSink(localPipView);
        }
        if (p2pLocalVideoTrack != null && remoteVideoView != null) {
            p2pLocalVideoTrack.removeSink(remoteVideoView);
        }
    }

    private void detachP2pRemoteVideoWatcher() {
        View anchor = overlayRoot != null ? overlayRoot
                : (remoteVideoView != null ? remoteVideoView : getView());
        if (anchor != null) {
            anchor.removeCallbacks(p2pRemoteVideoIdleRunnable);
        }
        if (p2pRemoteVideoTrack != null) {
            p2pRemoteVideoTrack.removeSink(p2pRemoteFrameWatcher);
        }
    }

    private void onP2pRemoteVideoIdleCheck() {
        if (!isAdded() || p2pRemoteVideoTrack == null) {
            return;
        }
        long idle = SystemClock.elapsedRealtime() - p2pRemoteVideoLastFrameMs;
        if (idle >= P2P_REMOTE_VIDEO_IDLE_MS && p2pRemoteReceivingVideo) {
            p2pRemoteReceivingVideo = false;
            p2pRemoteFrameStreak = 0;
            p2pRemoteLastBurstFrameMs = 0L;
            applyP2pVideoLayout();
        }
    }

    /**
     * Gọi 1–1, phase connected: có video (local hoặc remote bật) thì ẩn dòng trạng thái (vd. "Đã kết nối");
     * chỉ thoại / chưa cam thì hiện lại.
     */
    private void syncP2pCallStatusLineForVideo() {
        if (statusView == null || lastInbox == null || lastInbox.isGroupCall()) {
            return;
        }
        boolean connected = UserCallInbox.PHASE_CONNECTED.equals(lastInbox.getPhase());
        boolean remoteLive = p2pRemoteVideoTrack != null && p2pRemoteReceivingVideo;
        boolean anyVideo = remoteLive
                || (p2pLocalVideoTrack != null && p2pLocalVideoTrack.enabled());
        if (connected && anyVideo) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setVisibility(View.VISIBLE);
        }
    }

    private void applyP2pVideoLayout() {
        if (lastInbox == null || lastInbox.isGroupCall() || remoteVideoView == null || localPipView == null
                || singlePeerBlock == null) {
            return;
        }
        boolean remoteOn = p2pRemoteVideoTrack != null && p2pRemoteReceivingVideo;
        boolean localOn = p2pLocalVideoTrack != null && p2pLocalVideoTrack.enabled();

        clearP2pVideoSinks();

        if (!remoteOn && !localOn) {
            remoteVideoView.setVisibility(View.GONE);
            localPipView.setVisibility(View.GONE);
            singlePeerBlock.setVisibility(View.VISIBLE);
            syncP2pCallStatusLineForVideo();
            return;
        }

        remoteVideoView.setVisibility(View.VISIBLE);
        singlePeerBlock.setVisibility(View.GONE);

        if (remoteOn && localOn) {
            p2pRemoteVideoTrack.addSink(remoteVideoView);
            p2pLocalVideoTrack.addSink(localPipView);
            localPipView.setVisibility(View.VISIBLE);
        } else if (remoteOn) {
            p2pRemoteVideoTrack.addSink(remoteVideoView);
            localPipView.setVisibility(View.GONE);
        } else {
            p2pLocalVideoTrack.addSink(remoteVideoView);
            localPipView.setVisibility(View.GONE);
        }
        syncP2pCallStatusLineForVideo();
    }

    private void clearCallVideoUi() {
        clearP2pVideoSinks();
        detachP2pRemoteVideoWatcher();
        p2pRemoteVideoTrack = null;
        p2pRemoteReceivingVideo = false;
        p2pRemoteFrameStreak = 0;
        p2pRemoteLastBurstFrameMs = 0L;
        p2pLocalVideoTrack = null;
        p2pSelfCameraOn = false;
        if (remoteVideoView != null) {
            remoteVideoView.setVisibility(View.GONE);
        }
        if (localPipView != null) {
            localPipView.setVisibility(View.GONE);
        }
        if (singlePeerBlock != null && lastInbox != null && !lastInbox.isGroupCall()) {
            singlePeerBlock.setVisibility(View.VISIBLE);
        }
        syncP2pCallStatusLineForVideo();
        if (gridAdapter != null) {
            gridAdapter.clearAllVideoTracks();
        }
        updateCameraButtonsUi();
    }

    private void updateCameraButtonsUi() {
        if (btnCamera != null) {
            btnCamera.setAlpha(p2pSelfCameraOn ? 1f : 0.55f);
        }
    }

    private void onToggleCameraClicked() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FOR_CALL);
            return;
        }
        p2pSelfCameraOn = !p2pSelfCameraOn;
        if (webRtcSession != null) {
            webRtcSession.setCameraEnabled(p2pSelfCameraOn);
        }
        if (meshManager != null) {
            meshManager.setLocalCameraEnabled(p2pSelfCameraOn);
        }
        View postTarget = btnCamera != null ? btnCamera : overlayRoot;
        if (postTarget != null) {
            postTarget.post(this::applyP2pVideoLayout);
            postTarget.post(() -> {
                if (gridAdapter != null) {
                    gridAdapter.notifyDataSetChanged();
                }
            });
        }
        updateCameraButtonsUi();
    }

    private void onSwitchCameraClicked() {
        if (webRtcSession != null) {
            webRtcSession.switchCamera();
        }
        if (meshManager != null) {
            meshManager.switchLocalCamera();
        }
    }

    private void releaseRtcVideoViews() {
        if (rtcVideoRenderersInited) {
            if (remoteVideoView != null) {
                remoteVideoView.release();
            }
            if (localPipView != null) {
                localPipView.release();
            }
        }
        rtcVideoRenderersInited = false;
    }

    private void startIncomingRingtoneIfNeeded() {
        if (incomingRingtone != null && incomingRingtone.isPlaying()) {
            return;
        }
        try {
            Uri uri = RingtoneManager.getActualDefaultRingtoneUri(
                    requireContext(),
                    RingtoneManager.TYPE_RINGTONE
            );
            if (uri == null) {
                return;
            }
            incomingRingtone = RingtoneManager.getRingtone(requireContext(), uri);
            if (incomingRingtone != null) {
                incomingRingtone.play();
            }
        } catch (Exception ignored) {
        }
    }

    private void stopIncomingRingtone() {
        if (incomingRingtone != null) {
            try {
                incomingRingtone.stop();
            } catch (Exception ignored) {
            }
            incomingRingtone = null;
        }
    }

    private void showFullOverlay() {
        if (!isAdded()) {
            return;
        }
        android.app.Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        View host = activity.findViewById(R.id.call_overlay_host);
        if (host != null) {
            host.setVisibility(View.VISIBLE);
        }
        if (overlayRoot != null) {
            overlayRoot.setVisibility(View.VISIBLE);
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setBottomNavigationHiddenForOverlay(true);
        }
    }

    private void hideFullOverlay() {
        stopIncomingRingtone();
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        speakerOn = false;
        if (overlayRoot != null) {
            overlayRoot.setVisibility(View.GONE);
        }
        android.app.Activity activity = isAdded() ? getActivity() : null;
        if (activity != null) {
            View host = activity.findViewById(R.id.call_overlay_host);
            if (host != null) {
                host.setVisibility(View.GONE);
            }
            if (activity instanceof MainActivity) {
                MainActivity main = (MainActivity) activity;
                main.setBottomNavigationHiddenForOverlay(main.isFullScreenChatOverlayVisible());
            }
        }
    }

    private void stopInboxListener() {
        if (inboxRegistration != null) {
            inboxRegistration.remove();
            inboxRegistration = null;
        }
    }

    private void stopCallListener() {
        if (callRegistration != null) {
            callRegistration.remove();
            callRegistration = null;
        }
        listenedCallId = null;
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s;
    }

    @NonNull
    private static String nonEmpty(@Nullable String value, @NonNull String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
