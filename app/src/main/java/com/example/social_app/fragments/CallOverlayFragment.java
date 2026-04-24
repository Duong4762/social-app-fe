package com.example.social_app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.data.model.UserCallInbox;
import com.example.social_app.data.model.VoiceCallSession;
import com.example.social_app.repository.VoiceCallRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.webrtc.WebRtcAudioSession;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Overlay toàn màn hình: cuộc gọi đến / đang gọi / đang nói chuyện. Lắng nghe {@code user_call_inbox}.
 */
public class CallOverlayFragment extends Fragment {

    private static final int REQ_RECORD_AUDIO = 9002;

    private VoiceCallRepository repository;
    private ListenerRegistration inboxRegistration;
    private ListenerRegistration callRegistration;
    private String myUid;
    private UserCallInbox lastInbox;
    private String listenedCallId;
    private String previousVoiceState;

    private View overlayRoot;
    private ShapeableImageView peerAvatar;
    private TextView peerNameView;
    private TextView statusView;
    private LinearLayout rowIncoming;
    private LinearLayout rowActive;
    private MaterialButton btnAccept;
    private MaterialButton btnDecline;
    private ImageButton btnSpeaker;
    private ImageButton btnHangup;

    private AudioManager audioManager;
    private boolean speakerOn;
    private Ringtone incomingRingtone;

    @Nullable
    private VoiceCallSession lastVoiceSession;
    @Nullable
    private WebRtcAudioSession webRtcSession;
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
        peerAvatar = view.findViewById(R.id.call_peer_avatar);
        peerNameView = view.findViewById(R.id.call_peer_name);
        statusView = view.findViewById(R.id.call_status_text);
        rowIncoming = view.findViewById(R.id.call_row_incoming_actions);
        rowActive = view.findViewById(R.id.call_row_active_controls);
        btnAccept = view.findViewById(R.id.call_btn_accept);
        btnDecline = view.findViewById(R.id.call_btn_decline);
        btnSpeaker = view.findViewById(R.id.call_btn_speaker);
        btnHangup = view.findViewById(R.id.call_btn_hangup);

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
        stopInboxListener();
        stopCallListener();
        stopIncomingRingtone();
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        overlayRoot = null;
        peerAvatar = null;
        peerNameView = null;
        statusView = null;
        rowIncoming = null;
        rowActive = null;
        btnAccept = null;
        btnDecline = null;
        btnSpeaker = null;
        btnHangup = null;
        super.onDestroyView();
    }

    private void applyInbox(@NonNull UserCallInbox inbox) {
        lastInbox = inbox;
        if (!inbox.isActive()) {
            disposeWebRtc();
            stopIncomingRingtone();
            stopCallListener();
            hideFullOverlay();
            return;
        }

        showFullOverlay();
        UserAvatarLoader.load(peerAvatar, emptyToNull(inbox.getPeerAvatarUrl()));
        peerNameView.setText(nonEmpty(inbox.getPeerName(), getString(R.string.unknown_user)));

        String phase = inbox.getPhase();
        if (UserCallInbox.PHASE_INCOMING.equals(phase)) {
            rowIncoming.setVisibility(View.VISIBLE);
            rowActive.setVisibility(View.GONE);
            statusView.setText(R.string.call_status_incoming);
            startIncomingRingtoneIfNeeded();
            attachCallListener(inbox.getCallId());
        } else if (UserCallInbox.PHASE_OUTGOING.equals(phase)) {
            rowIncoming.setVisibility(View.GONE);
            rowActive.setVisibility(View.VISIBLE);
            statusView.setText(R.string.call_status_waiting);
            stopIncomingRingtone();
            attachCallListener(inbox.getCallId());
        } else if (UserCallInbox.PHASE_CONNECTED.equals(phase)) {
            rowIncoming.setVisibility(View.GONE);
            rowActive.setVisibility(View.VISIBLE);
            statusView.setText(R.string.call_status_connected);
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
        if (webRtcSession != null && callId.equals(webRtcActiveCallId)) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        startWebRtcAudioInternal(callId);
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
        webRtcSession.start();
    }

    private void disposeWebRtc() {
        if (webRtcSession != null) {
            webRtcSession.dispose();
            webRtcSession = null;
            webRtcActiveCallId = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) {
                maybeStartWebRtcAudio();
            } else {
                Toast.makeText(requireContext(), R.string.call_need_mic_permission, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void onAcceptClicked() {
        if (lastInbox == null || lastInbox.getCallId() == null) {
            return;
        }
        stopIncomingRingtone();
        repository.acceptCall(lastInbox.getCallId(), myUid)
                .addOnFailureListener(e -> Toast.makeText(
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
        View host = requireActivity().findViewById(R.id.call_overlay_host);
        if (host != null) {
            host.setVisibility(View.VISIBLE);
        }
        if (overlayRoot != null) {
            overlayRoot.setVisibility(View.VISIBLE);
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavigationHiddenForOverlay(true);
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
        View host = requireActivity().findViewById(R.id.call_overlay_host);
        if (host != null) {
            host.setVisibility(View.GONE);
        }
        if (requireActivity() instanceof MainActivity) {
            MainActivity main = (MainActivity) requireActivity();
            main.setBottomNavigationHiddenForOverlay(main.isFullScreenChatOverlayVisible());
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
