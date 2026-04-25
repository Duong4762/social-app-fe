package com.example.social_app.webrtc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.Manifest;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Một camera / {@link VideoTrack} dùng chung cho toàn bộ cạnh mesh nhóm.
 */
public final class WebRtcMeshMediaShare {

    private static final int[][] MESH_CAPTURE_PROFILES = {
            {640, 480, 24},
            {640, 360, 24},
            {480, 360, 15},
            {320, 240, 15}
    };

    public interface VideoCallback {
        void onEglReady(@NonNull EglBase eglBase);

        void onLocalVideoTrack(@NonNull VideoTrack track);

        void onRemoteVideoTrack(@NonNull String remoteUid, @NonNull VideoTrack track);

        void onRemoteVideoTrackRemoved(@NonNull String remoteUid);
    }

    private static final Object INIT_LOCK = new Object();
    private static boolean libraryInitialized;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private SurfaceTextureHelper surfaceTextureHelper;
    private CameraVideoCapturer capturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    @Nullable
    private VideoCallback callback;

    public WebRtcMeshMediaShare(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setCallback(@Nullable VideoCallback callback) {
        this.callback = callback;
    }

    public void prepare() {
        if (Looper.myLooper() == main.getLooper()) {
            prepareSync();
        } else {
            main.post(this::prepareSync);
        }
    }

    private void prepareSync() {
        if (disposed.get()) {
            return;
        }
        ensureLibraryInitialized(appContext);
        if (factory != null) {
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
        if (callback != null) {
            callback.onEglReady(eglBase);
        }
        tryCreateLocalVideoLocked();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Tạo track camera nếu đã có quyền CAMERA (gọi trên main thread). */
    private void tryCreateLocalVideoLocked() {
        if (factory == null || eglBase == null || localVideoTrack != null) {
            return;
        }
        if (!hasCameraPermission()) {
            return;
        }
        createLocalVideoTrackLocked();
        if (callback != null && localVideoTrack != null) {
            callback.onLocalVideoTrack(localVideoTrack);
        }
    }

    private static boolean tryStartMeshCapture(@NonNull CameraVideoCapturer c) {
        for (int[] p : MESH_CAPTURE_PROFILES) {
            try {
                c.startCapture(p[0], p[1], p[2]);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void disposeFailedMeshCaptureOnlyLocked() {
        if (capturer != null) {
            try {
                capturer.stopCapture();
            } catch (Exception ignored) {
            }
            try {
                capturer.dispose();
            } catch (Exception ignored) {
            }
            capturer = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
    }

    private void createLocalVideoTrackLocked() {
        if (factory == null || eglBase == null || localVideoTrack != null) {
            return;
        }
        Camera2Enumerator enumerator = new Camera2Enumerator(appContext);
        String[] names = enumerator.getDeviceNames();
        if (names == null || names.length == 0) {
            return;
        }
        String chosen = pickCamera(enumerator, names, true);
        if (chosen == null) {
            chosen = names[0];
        }
        capturer = enumerator.createCapturer(chosen, null);
        if (capturer == null) {
            return;
        }
        surfaceTextureHelper = SurfaceTextureHelper.create("mesh_cap", eglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(capturer.isScreencast());
        capturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        if (!tryStartMeshCapture(capturer)) {
            disposeFailedMeshCaptureOnlyLocked();
            return;
        }
        localVideoTrack = factory.createVideoTrack("mesh_local_v", videoSource);
        localVideoTrack.setEnabled(false);
    }

    @Nullable
    private static String pickCamera(
            @NonNull Camera2Enumerator enumerator,
            @NonNull String[] names,
            boolean wantFront
    ) {
        for (String n : names) {
            if (wantFront && enumerator.isFrontFacing(n)) {
                return n;
            }
            if (!wantFront && !enumerator.isFrontFacing(n)) {
                return n;
            }
        }
        return null;
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

    @NonNull
    public PeerConnectionFactory getFactory() {
        if (Looper.myLooper() != main.getLooper()) {
            throw new IllegalStateException("WebRtcMeshMediaShare: main thread only");
        }
        prepareSync();
        return factory;
    }

    @NonNull
    public EglBase getEglBase() {
        if (Looper.myLooper() != main.getLooper()) {
            throw new IllegalStateException("WebRtcMeshMediaShare: main thread only");
        }
        prepareSync();
        return eglBase;
    }

    @Nullable
    public VideoTrack getLocalVideoTrack() {
        if (Looper.myLooper() != main.getLooper()) {
            throw new IllegalStateException("WebRtcMeshMediaShare: main thread only");
        }
        prepareSync();
        tryCreateLocalVideoLocked();
        return localVideoTrack;
    }

    public void setCameraEnabled(boolean enabled) {
        main.post(() -> {
            if (disposed.get()) {
                return;
            }
            tryCreateLocalVideoLocked();
            if (localVideoTrack == null) {
                return;
            }
            localVideoTrack.setEnabled(enabled);
        });
    }

    public void switchCamera() {
        main.post(() -> {
            if (disposed.get() || !(capturer instanceof CameraVideoCapturer)) {
                return;
            }
            try {
                ((CameraVideoCapturer) capturer).switchCamera(null);
            } catch (Exception ignored) {
            }
        });
    }

    void notifyRemoteVideo(@NonNull String remoteUid, @NonNull VideoTrack track) {
        main.post(() -> {
            if (disposed.get() || callback == null) {
                return;
            }
            callback.onRemoteVideoTrack(remoteUid, track);
        });
    }

    void notifyRemoteVideoRemoved(@NonNull String remoteUid) {
        main.post(() -> {
            if (disposed.get() || callback == null) {
                return;
            }
            callback.onRemoteVideoTrackRemoved(remoteUid);
        });
    }

    public void dispose() {
        disposed.set(true);
        main.post(() -> {
            if (localVideoTrack != null) {
                localVideoTrack.dispose();
                localVideoTrack = null;
            }
            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }
            if (capturer != null) {
                try {
                    capturer.stopCapture();
                } catch (Exception ignored) {
                }
                capturer.dispose();
                capturer = null;
            }
            if (surfaceTextureHelper != null) {
                surfaceTextureHelper.dispose();
                surfaceTextureHelper = null;
            }
            if (factory != null) {
                factory.dispose();
                factory = null;
            }
            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }
        });
    }
}
