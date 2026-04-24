package com.example.social_app.adapters;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.utils.UserAvatarLoader;

import java.util.ArrayList;
import java.util.List;

public class MessagesConversationAdapter extends RecyclerView.Adapter<MessagesConversationAdapter.VH> {

    public static final class Item {
        /** Firestore conversation document id. */
        public final String conversationId;
        /** UID người đối thoại (1-1). */
        public final String peerUserId;
        public final String name;
        public final String preview;
        public final String time;
        public final boolean unread;
        public final boolean showPhotoIcon;
        /** Nullable; empty or invalid falls back inside {@link UserAvatarLoader}. */
        public final String avatarUrl;
        /** Sắp xếp danh sách (last message hoặc cập nhật hội thoại). */
        public final long lastActivityMillis;
        /** true = nhóm chat (không có một peer đơn). */
        public final boolean isGroup;

        public Item(
                String conversationId,
                String peerUserId,
                String name,
                String preview,
                String time,
                boolean unread,
                boolean showPhotoIcon,
                String avatarUrl,
                long lastActivityMillis,
                boolean isGroup) {
            this.conversationId = conversationId;
            this.peerUserId = peerUserId != null ? peerUserId : "";
            this.name = name;
            this.preview = preview;
            this.time = time;
            this.unread = unread;
            this.showPhotoIcon = showPhotoIcon;
            this.avatarUrl = avatarUrl;
            this.lastActivityMillis = lastActivityMillis;
            this.isGroup = isGroup;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private OnConversationClickListener clickListener;

    public interface OnConversationClickListener {
        void onConversationClick(Item item);
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.clickListener = listener;
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_conversation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item item = items.get(position);
        h.name.setText(item.name);
        h.preview.setText(item.preview);
        int previewColor = themeAttrColor(
                h.preview,
                item.unread
                        ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOnSurfaceVariant);
        h.preview.setTextColor(previewColor);
        ImageViewCompat.setImageTintList(h.photoIcon, ColorStateList.valueOf(previewColor));
        h.time.setText(item.time);
        h.unreadDot.setVisibility(item.unread ? View.VISIBLE : View.GONE);
        h.photoIcon.setVisibility(item.showPhotoIcon ? View.VISIBLE : View.GONE);
        if (item.isGroup) {
            h.avatar.setImageResource(R.drawable.ic_group_chat);
        } else {
            UserAvatarLoader.load(h.avatar, item.avatarUrl);
        }
        boolean last = position == getItemCount() - 1;
        h.divider.setVisibility(last ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onConversationClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int themeAttrColor(@NonNull View view, int attrRes) {
        TypedArray a = view.getContext().obtainStyledAttributes(new int[]{attrRes});
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView preview;
        final TextView time;
        final View unreadDot;
        final ImageView photoIcon;
        final ImageView avatar;
        final View divider;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.conversation_name);
            preview = itemView.findViewById(R.id.conversation_preview);
            time = itemView.findViewById(R.id.conversation_time);
            unreadDot = itemView.findViewById(R.id.unread_dot);
            photoIcon = itemView.findViewById(R.id.preview_photo_icon);
            avatar = itemView.findViewById(R.id.conversation_avatar);
            divider = itemView.findViewById(R.id.row_divider);
        }
    }
}
