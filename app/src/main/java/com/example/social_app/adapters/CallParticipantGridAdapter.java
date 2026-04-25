package com.example.social_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.android.material.imageview.ShapeableImageView;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ô vuông avatar (và video trong ô); tên nằm dưới ô trong layout item.
 */
public final class CallParticipantGridAdapter extends RecyclerView.Adapter<CallParticipantGridAdapter.Holder> {

    public static final class Tile {
        public final String uid;
        @NonNull
        public final String displayName;
        @Nullable
        public final String avatarUrl;

        public Tile(@NonNull String uid, @NonNull String displayName, @Nullable String avatarUrl) {
            this.uid = uid;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
        }
    }

    @NonNull
    private List<Tile> tiles = new ArrayList<>();
    @Nullable
    private EglBase.Context eglBaseContext;
    @Nullable
    private String myUid;
    @Nullable
    private VideoTrack localSelfVideoTrack;
    @NonNull
    private final Map<String, VideoTrack> remoteVideoByUid = new HashMap<>();

    public void setTiles(@NonNull List<Tile> next) {
        this.tiles = new ArrayList<>(next);
        notifyDataSetChanged();
    }

    public void setEglBaseContext(@Nullable EglBase.Context ctx) {
        this.eglBaseContext = ctx;
        notifyDataSetChanged();
    }

    public void setMyUid(@Nullable String uid) {
        this.myUid = uid;
    }

    public void setLocalSelfVideoTrack(@Nullable VideoTrack track) {
        this.localSelfVideoTrack = track;
        notifyDataSetChanged();
    }

    public void setRemoteVideoForUser(@NonNull String uid, @NonNull VideoTrack track) {
        remoteVideoByUid.put(uid, track);
        notifyDataSetChanged();
    }

    public void clearRemoteVideoForUser(@NonNull String uid) {
        remoteVideoByUid.remove(uid);
        notifyDataSetChanged();
    }

    public void clearAllVideoTracks() {
        remoteVideoByUid.clear();
        localSelfVideoTrack = null;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return tiles.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_participant_cell, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Tile t = tiles.get(position);
        h.name.setText(t.displayName);
        UserAvatarLoader.load(h.avatar, emptyToNull(t.avatarUrl));

        VideoTrack remote = remoteVideoByUid.get(t.uid);
        VideoTrack self = (myUid != null && myUid.equals(t.uid)) ? localSelfVideoTrack : null;
        VideoTrack show = remote != null ? remote : self;
        boolean videoOn = show != null && show.enabled();

        if (eglBaseContext != null && !h.videoRendererInited) {
            h.videoRenderer.init(eglBaseContext, null);
            h.videoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            h.videoRenderer.setEnableHardwareScaler(true);
            h.videoRenderer.setZOrderMediaOverlay(true);
            h.videoRenderer.setMirror(myUid != null && myUid.equals(t.uid));
            h.videoRendererInited = true;
        }

        if (h.boundVideoTrack != null && h.boundVideoTrack != show) {
            h.boundVideoTrack.removeSink(h.videoRenderer);
            h.boundVideoTrack = null;
        }

        if (videoOn && eglBaseContext != null) {
            h.videoRenderer.setVisibility(View.VISIBLE);
            h.avatar.setVisibility(View.INVISIBLE);
            if (show != null) {
                show.addSink(h.videoRenderer);
                h.boundVideoTrack = show;
            }
        } else {
            if (h.boundVideoTrack != null) {
                h.boundVideoTrack.removeSink(h.videoRenderer);
                h.boundVideoTrack = null;
            }
            h.videoRenderer.setVisibility(View.GONE);
            h.avatar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        if (holder.boundVideoTrack != null) {
            holder.boundVideoTrack.removeSink(holder.videoRenderer);
            holder.boundVideoTrack = null;
        }
        super.onViewRecycled(holder);
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final SurfaceViewRenderer videoRenderer;
        boolean videoRendererInited;
        @Nullable
        VideoTrack boundVideoTrack;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.call_participant_avatar);
            name = itemView.findViewById(R.id.call_participant_name);
            videoRenderer = itemView.findViewById(R.id.call_participant_video);
        }
    }
}
