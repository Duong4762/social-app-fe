package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.VideoView;
import android.net.Uri;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.PostMedia;

import java.util.ArrayList;
import java.util.List;

public class PostImageAdapter extends RecyclerView.Adapter<PostImageAdapter.ImageViewHolder> {

    private final Context context;
    private final List<PostMedia> mediaList = new ArrayList<>();

    public PostImageAdapter(Context context) {
        this.context = context;
    }

    public void setMediaList(List<PostMedia> newList) {
        mediaList.clear();
        mediaList.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_image_slide, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        PostMedia media = mediaList.get(position);
        
        // Reset visibility
        holder.videoView.setVisibility(View.GONE);
        holder.imageView.setVisibility(View.VISIBLE);

        // Show/hide play icon for videos
        boolean isVideo = "VIDEO".equalsIgnoreCase(media.getMediaType());
        holder.icPlay.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        Glide.with(context)
                .load(media.getMediaUrl())
                .placeholder(R.drawable.bg_nav_item_selected)
                .centerCrop()
                .into(holder.imageView);

        if (isVideo) {
            holder.icPlay.setOnClickListener(v -> {
                holder.icPlay.setVisibility(View.GONE);
                holder.imageView.setVisibility(View.GONE);
                holder.videoView.setVisibility(View.VISIBLE);

                holder.videoView.setVideoURI(Uri.parse(media.getMediaUrl()));
                
                // Add media controls (optional, can be customized)
                MediaController mediaController = new MediaController(context);
                mediaController.setAnchorView(holder.videoView);
                holder.videoView.setMediaController(mediaController);

                holder.videoView.start();
            });

            holder.videoView.setOnCompletionListener(mp -> {
                holder.videoView.setVisibility(View.GONE);
                holder.imageView.setVisibility(View.VISIBLE);
                holder.icPlay.setVisibility(View.VISIBLE);
            });
        }
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView icPlay;
        VideoView videoView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.slide_image);
            icPlay = itemView.findViewById(R.id.ic_play);
            videoView = itemView.findViewById(R.id.video_view);
        }
    }
}
