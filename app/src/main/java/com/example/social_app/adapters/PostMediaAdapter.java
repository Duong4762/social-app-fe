package com.example.social_app.adapters;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.PostMedia;

import java.util.List;

public class PostMediaAdapter extends RecyclerView.Adapter<PostMediaAdapter.ViewHolder> {

    private List<PostMedia> mediaList;

    public PostMediaAdapter(List<PostMedia> mediaList) {
        this.mediaList = mediaList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_image_slide, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostMedia media = mediaList.get(position);
        boolean isVideo = media.getMediaType() != null && media.getMediaType().equalsIgnoreCase("VIDEO");
        
        // Thumbnail cho cả ảnh và video
        Glide.with(holder.itemView.getContext())
                .load(media.getMediaUrl())
                .centerCrop()
                .into(holder.imageView);
                
        holder.playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        // Click để xem chi tiết (Popup) - Gán cho cả root và image để chắc chắn nhận được click
        View.OnClickListener clickListener = v -> showMediaPopup(v.getContext(), media);
        holder.itemView.setOnClickListener(clickListener);
        holder.imageView.setOnClickListener(clickListener);
    }

    private void showMediaPopup(Context context, PostMedia media) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_media_viewer);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }

        ImageView imageView = dialog.findViewById(R.id.fullscreen_image);
        VideoView videoView = dialog.findViewById(R.id.fullscreen_video);
        ImageView btnClose = dialog.findViewById(R.id.btn_close_viewer);
        ImageView playIcon = dialog.findViewById(R.id.ic_play_video);

        boolean isVideo = media.getMediaType() != null && media.getMediaType().equalsIgnoreCase("VIDEO");

        if (isVideo) {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            playIcon.setVisibility(View.VISIBLE);
            
            // Thiết lập MediaController
            MediaController mediaController = new MediaController(context);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.parse(media.getMediaUrl()));
            
            playIcon.setOnClickListener(v -> {
                playIcon.setVisibility(View.GONE);
                videoView.start();
            });

            videoView.setOnPreparedListener(mp -> {
                // Video đã sẵn sàng, có thể tự động chạy hoặc chờ click
                playIcon.setVisibility(View.VISIBLE);
            });

            videoView.setOnCompletionListener(mp -> playIcon.setVisibility(View.VISIBLE));
            
            videoView.setOnClickListener(v -> {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    playIcon.setVisibility(View.VISIBLE);
                } else {
                    videoView.start();
                    playIcon.setVisibility(View.GONE);
                }
            });
        } else {
            videoView.setVisibility(View.GONE);
            playIcon.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(media.getMediaUrl())
                    .into(imageView);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public int getItemCount() {
        return mediaList != null ? mediaList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView playIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.slide_image);
            playIcon = itemView.findViewById(R.id.ic_play);
        }
    }
}
