package com.example.social_app.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.social_app.repository.VoiceCallRepository;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Một cạnh mesh WebRTC (audio) với {@code remoteUid}; SDP/ICE trên
 * {@code voice_calls/{callId}/webrtc_edges/{edgeId}}.
 */
public final class WebRtcMeshPeerSession {

    private static final Object INIT_LOCK = new Object();
    private static boolean libraryInitialized;

    private final Context appContext;
    private final VoiceCallRepository repository;
    private final String callId;
    private final String edgeId;
    private final String myUid;
    private final String remoteUid;
    private final boolean isOfferer;
    private final Runnable onError;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Queue<IceCandidate> pendingRemoteIce = new ArrayDeque<>();
    private final AtomicBoolean offerPosted = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private ListenerRegistration edgeListener;
    private ListenerRegistration iceListener;
    private PeerConnectionFactory factory;
    private EglBase eglBase;
    private PeerConnection peerConnection;
    private AudioTrack localAudioTrack;
    private final boolean usingSharedFactory;
    @Nullable
    private final WebRtcMeshMediaShare mediaShare;

    private String lastSeenRemoteOffer = "";
    private String lastSeenRemoteAnswer = "";

    public WebRtcMeshPeerSession(
            @NonNull Context context,
            @NonNull VoiceCallRepository repository,
            @NonNull String callId,
            @NonNull String myUid,
            @NonNull String remoteUid,
            @NonNull Runnable onError,
            @Nullable WebRtcMeshMediaShare mediaShare
    ) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.callId = callId;
        this.myUid = myUid;
        this.remoteUid = remoteUid;
        this.edgeId = VoiceCallRepository.meshEdgeId(myUid, remoteUid);
        this.isOfferer = myUid.compareTo(remoteUid) < 0;
        this.onError = onError;
        this.mediaShare = mediaShare;
        this.usingSharedFactory = mediaShare != null;
    }

    public void start() {
        main.post(() -> {
            if (disposed.get()) {
                return;
            }
            ensureLibraryInitialized(appContext);
            try {
                buildPeerConnectionFactory();
                buildPeerConnection();
                attachLocalAudio();
                wireEdgeSignaling();
                wireIceListener();
                if (isOfferer) {
                    postCreateOffer();
                }
            } catch (Exception e) {
                onError.run();
            }
        });
    }

    public void dispose() {
        disposed.set(true);
        main.post(this::disposeInternal);
    }

    private void disposeInternal() {
        if (iceListener != null) {
            iceListener.remove();
            iceListener = null;
        }
        if (edgeListener != null) {
            edgeListener.remove();
            edgeListener = null;
        }
        pendingRemoteIce.clear();
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        if (!usingSharedFactory && factory != null) {
            factory.dispose();
            factory = null;
        } else {
            factory = null;
        }
        if (!usingSharedFactory && eglBase != null) {
            eglBase.release();
            eglBase = null;
        } else {
            eglBase = null;
        }
    }

    private static void ensureLibraryInitialized(@NonNull Context context) {
        synchronized (INIT_LOCK) {
            if (libraryInitialized) {
                return;
            }
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions());
            libraryInitialized = true;
        }
    }

    private void buildPeerConnectionFactory() {
        if (mediaShare != null) {
            mediaShare.prepare();
            eglBase = mediaShare.getEglBase();
            factory = mediaShare.getFactory();
            return;
        }
        eglBase = EglBase.create();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory enc = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory dec = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(enc)
                .setVideoDecoderFactory(dec)
                .createPeerConnectionFactory();
    }

    private void buildPeerConnection() {
        List<PeerConnection.IceServer> iceServers = java.util.Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        );
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        MediaConstraints pcConstraints = new MediaConstraints();
        peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                if (disposed.get()) {
                    return;
                }
                repository.pushMeshEdgeIceCandidate(
                        callId,
                        edgeId,
                        myUid,
                        candidate.sdpMid,
                        candidate.sdpMLineIndex,
                        candidate.sdp
                ).addOnFailureListener(e -> main.post(onError));
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            @Override
            public void onAddStream(MediaStream stream) {
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
            }

            @Override
            public void onDataChannel(DataChannel dc) {
            }

            @Override
            public void onRenegotiationNeeded() {
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                if (receiver.track() instanceof AudioTrack) {
                    receiver.track().setEnabled(true);
                } else if (receiver.track() instanceof VideoTrack) {
                    VideoTrack vt = (VideoTrack) receiver.track();
                    if (mediaShare != null) {
                        mediaShare.notifyRemoteVideo(remoteUid, vt);
                    }
                }
            }
        });
        if (peerConnection == null) {
            throw new IllegalStateException("PeerConnection null");
        }
    }

    private void attachLocalAudio() {
        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource source = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("mesh_a_" + remoteUid, source);
        localAudioTrack.setEnabled(true);
        peerConnection.addTrack(localAudioTrack, Collections.singletonList("mesh_s_" + remoteUid));
        if (mediaShare != null) {
            VideoTrack v = mediaShare.getLocalVideoTrack();
            if (v != null) {
                peerConnection.addTrack(v, Collections.singletonList("mesh_s_" + remoteUid));
            } else {
                peerConnection.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
            }
        }
    }

    private void wireEdgeSignaling() {
        edgeListener = repository.listenMeshEdge(callId, edgeId, new VoiceCallRepository.MeshEdgeCallback() {
            @Override
            public void onEdge(@Nullable String sdpOffer, @Nullable String sdpAnswer) {
                if (disposed.get()) {
                    return;
                }
                String offer = nonNull(sdpOffer);
                String answer = nonNull(sdpAnswer);
                if (isOfferer) {
                    if (!answer.isEmpty() && !answer.equals(lastSeenRemoteAnswer)) {
                        lastSeenRemoteAnswer = answer;
                        applyRemoteAnswer(answer);
                    }
                } else {
                    if (!offer.isEmpty() && !offer.equals(lastSeenRemoteOffer)) {
                        lastSeenRemoteOffer = offer;
                        applyRemoteOfferAndAnswer(offer);
                    }
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                main.post(onError);
            }
        });
    }

    private void wireIceListener() {
        iceListener = repository.listenMeshEdgeIce(callId, edgeId, myUid, new VoiceCallRepository.IceCandidateCallback() {
            @Override
            public void onIceCandidate(
                    @NonNull String fromUid,
                    @Nullable String sdpMid,
                    int sdpMLineIndex,
                    @NonNull String candidateSdp
            ) {
                if (disposed.get() || myUid.equals(fromUid)) {
                    return;
                }
                String mid = sdpMid != null ? sdpMid : "";
                IceCandidate ice = new IceCandidate(mid, sdpMLineIndex, candidateSdp);
                main.post(() -> queueOrAddRemoteIce(ice));
            }

            @Override
            public void onError(@NonNull Exception e) {
                main.post(onError);
            }
        });
    }

    private void postCreateOffer() {
        MediaConstraints offerConstraints = new MediaConstraints();
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(@NonNull SessionDescription localOffer) {
                if (disposed.get()) {
                    return;
                }
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        if (disposed.get() || !offerPosted.compareAndSet(false, true)) {
                            return;
                        }
                        repository.publishMeshEdgeOffer(callId, edgeId, localOffer.description)
                                .addOnFailureListener(e -> main.post(onError));
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        main.post(onError);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        main.post(onError);
                    }
                }, localOffer);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String s) {
                main.post(onError);
            }

            @Override
            public void onSetFailure(String s) {
                main.post(onError);
            }
        }, offerConstraints);
    }

    private void applyRemoteOfferAndAnswer(@NonNull String offerSdp) {
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }

            @Override
            public void onSetSuccess() {
                drainPendingIce();
                createAnswerAfterRemoteOffer();
            }

            @Override
            public void onCreateFailure(String s) {
                main.post(onError);
            }

            @Override
            public void onSetFailure(String s) {
                main.post(onError);
            }
        }, offer);
    }

    private void createAnswerAfterRemoteOffer() {
        MediaConstraints answerConstraints = new MediaConstraints();
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(@NonNull SessionDescription localAnswer) {
                if (disposed.get()) {
                    return;
                }
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        repository.publishMeshEdgeAnswer(callId, edgeId, localAnswer.description)
                                .addOnFailureListener(e -> main.post(onError));
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        main.post(onError);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        main.post(onError);
                    }
                }, localAnswer);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String s) {
                main.post(onError);
            }

            @Override
            public void onSetFailure(String s) {
                main.post(onError);
            }
        }, answerConstraints);
    }

    private void applyRemoteAnswer(@NonNull String answerSdp) {
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerSdp);
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }

            @Override
            public void onSetSuccess() {
                drainPendingIce();
            }

            @Override
            public void onCreateFailure(String s) {
                main.post(onError);
            }

            @Override
            public void onSetFailure(String s) {
                main.post(onError);
            }
        }, answer);
    }

    private void queueOrAddRemoteIce(@NonNull IceCandidate candidate) {
        if (peerConnection == null || disposed.get()) {
            return;
        }
        if (peerConnection.getRemoteDescription() == null) {
            pendingRemoteIce.add(candidate);
            return;
        }
        peerConnection.addIceCandidate(candidate);
    }

    private void drainPendingIce() {
        if (peerConnection == null) {
            return;
        }
        while (!pendingRemoteIce.isEmpty()) {
            IceCandidate c = Objects.requireNonNull(pendingRemoteIce.poll());
            peerConnection.addIceCandidate(c);
        }
    }

    @NonNull
    private static String nonNull(@Nullable String s) {
        return s != null ? s : "";
    }
}
