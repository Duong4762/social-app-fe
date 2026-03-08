package com.example.social_app.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.social_app.R;

import java.util.List;

/**
 * Adapter for displaying media preview in GridView (NewPostFragment).
 * Shows selected images/videos with remove buttons.
 */
public class MediaPreviewAdapter extends BaseAdapter {

    private Context context;
    private List<Uri> mediaUris;
    private OnMediaRemovedListener onMediaRemovedListener;

    /**
     * Interface for handling media removal.
     */
    public interface OnMediaRemovedListener {
        void onMediaRemoved(int position);
    }

    /**
     * Constructor for MediaPreviewAdapter.
     *
     * @param context Android context
     * @param mediaUris List of media URIs to display
     * @param listener Listener for media removal
     */
    public MediaPreviewAdapter(Context context, List<Uri> mediaUris, OnMediaRemovedListener listener) {
        this.context = context;
        this.mediaUris = mediaUris;
        this.onMediaRemovedListener = listener;
    }

    @Override
    public int getCount() {
        return mediaUris != null ? mediaUris.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mediaUris != null && position < mediaUris.size() ? mediaUris.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_media_preview, parent, false);
            holder = new ViewHolder();
            holder.imageView = convertView.findViewById(R.id.media_preview_image);
            holder.removeButton = convertView.findViewById(R.id.media_remove_button);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Get media URI
        Uri mediaUri = mediaUris.get(position);

        // Load image (using placeholder for now - replace with actual image loading library)
        holder.imageView.setImageResource(R.drawable.avatar_placeholder);
        holder.imageView.setContentDescription("Media item " + (position + 1));

        // Set remove button listener
        final int itemPosition = position;
        holder.removeButton.setOnClickListener(v -> {
            if (onMediaRemovedListener != null) {
                onMediaRemovedListener.onMediaRemoved(itemPosition);
            }
        });
        holder.removeButton.setContentDescription("Remove media item " + (position + 1));

        return convertView;
    }

    /**
     * ViewHolder pattern for efficient view reuse.
     */
    private static class ViewHolder {
        ImageView imageView;
        ImageButton removeButton;
    }
}
