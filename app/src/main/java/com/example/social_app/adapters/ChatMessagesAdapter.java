package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Message;
import com.example.social_app.utils.UserAvatarLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Danh sách tin nhắn trong một cuộc hội thoại (có dòng phân cách theo ngày).
 */
public class ChatMessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DATE = 0;
    private static final int TYPE_IN = 1;
    private static final int TYPE_OUT = 2;

    private final String currentUserId;
    private String peerAvatarUrl;
    /** ID tin nhắn mà đối phương đã đọc (từ {@code message_reads}). */
    private Set<String> peerReadMessageIds = Collections.emptySet();
    private final List<Row> rows = new ArrayList<>();
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;

    public ChatMessagesAdapter(@NonNull String currentUserId, @Nullable String peerAvatarUrl) {
        this.currentUserId = currentUserId;
        this.peerAvatarUrl = peerAvatarUrl;
        Locale loc = Locale.getDefault();
        this.timeFormat = new SimpleDateFormat("h:mm a", loc);
        this.dateFormat = new SimpleDateFormat("MMM d, yyyy", loc);
    }

    public void setPeerAvatarUrl(String peerAvatarUrl) {
        this.peerAvatarUrl = peerAvatarUrl;
    }

    public void submitMessages(
            @NonNull Context context,
            @NonNull List<Message> messages,
            @Nullable Set<String> peerReadMessageIds) {
        this.peerReadMessageIds = peerReadMessageIds != null
                ? peerReadMessageIds
                : Collections.emptySet();
        rows.clear();
        if (messages.isEmpty()) {
            notifyDataSetChanged();
            return;
        }
        Calendar cal = Calendar.getInstance();
        long lastDayKey = Long.MIN_VALUE;
        for (Message m : messages) {
            Date created = m.getCreatedAt();
            if (created == null) {
                continue;
            }
            cal.setTime(created);
            long dayKey = dayKey(cal);
            if (dayKey != lastDayKey) {
                rows.add(Row.date(formatDateLabel(context, created)));
                lastDayKey = dayKey;
            }
            boolean outgoing = currentUserId.equals(m.getSenderId());
            rows.add(outgoing ? Row.out(m) : Row.in(m));
        }
        notifyDataSetChanged();
    }

    private static long dayKey(Calendar cal) {
        return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR);
    }

    @NonNull
    private String formatDateLabel(@NonNull Context context, @NonNull Date d) {
        Calendar today = Calendar.getInstance();
        Calendar msg = Calendar.getInstance();
        msg.setTime(d);
        if (today.get(Calendar.YEAR) == msg.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
            return context.getString(R.string.chat_label_today).toUpperCase(Locale.getDefault());
        }
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (today.get(Calendar.YEAR) == msg.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
            return context.getString(R.string.time_yesterday).toUpperCase(Locale.getDefault());
        }
        return dateFormat.format(d).toUpperCase(Locale.getDefault());
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).kind;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DATE) {
            return new DateVH(inf.inflate(R.layout.item_chat_date_separator, parent, false));
        }
        if (viewType == TYPE_IN) {
            return new InVH(inf.inflate(R.layout.item_chat_message_inbound, parent, false));
        }
        return new OutVH(inf.inflate(R.layout.item_chat_message_outbound, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof DateVH) {
            ((DateVH) holder).bind(row.dateLabel);
        } else if (holder instanceof InVH) {
            ((InVH) holder).bind(row.message, timeFormat, peerAvatarUrl);
        } else if (holder instanceof OutVH) {
            ((OutVH) holder).bind(row.message, timeFormat, peerReadMessageIds);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static final class Row {
        final int kind;
        final String dateLabel;
        final Message message;

        private Row(int kind, String dateLabel, Message message) {
            this.kind = kind;
            this.dateLabel = dateLabel;
            this.message = message;
        }

        static Row date(String label) {
            return new Row(TYPE_DATE, label, null);
        }

        static Row in(Message m) {
            return new Row(TYPE_IN, null, m);
        }

        static Row out(Message m) {
            return new Row(TYPE_OUT, null, m);
        }
    }

    static final class DateVH extends RecyclerView.ViewHolder {
        final TextView label;

        DateVH(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.chat_date_label);
        }

        void bind(String text) {
            label.setText(text);
        }
    }

    static final class InVH extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView bubble;
        final ImageView image;
        final TextView time;

        InVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.chat_in_avatar);
            bubble = itemView.findViewById(R.id.chat_in_bubble);
            image = itemView.findViewById(R.id.chat_in_image);
            time = itemView.findViewById(R.id.chat_in_time);
        }

        void bind(Message m, SimpleDateFormat tf, String peerAvatar) {
            String body = m.getContent() != null ? m.getContent() : "";
            if ("IMAGE".equalsIgnoreCase(m.getMessageType())) {
                bubble.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);
                Glide.with(itemView)
                        .load(body)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(image);
            } else if ("VIDEO".equalsIgnoreCase(m.getMessageType())) {
                image.setVisibility(View.GONE);
                bubble.setVisibility(View.VISIBLE);
                body = itemView.getContext().getString(R.string.message_preview_sent_video);
                bubble.setText(body);
            } else {
                image.setVisibility(View.GONE);
                bubble.setVisibility(View.VISIBLE);
                bubble.setText(body);
            }
            Date c = m.getCreatedAt();
            time.setText(c != null ? tf.format(c) : "");
            UserAvatarLoader.load(avatar, peerAvatar);
        }
    }

    static final class OutVH extends RecyclerView.ViewHolder {
        final TextView bubble;
        final ImageView image;
        final TextView timeTv;
        final TextView read;

        OutVH(@NonNull View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.chat_out_bubble);
            image = itemView.findViewById(R.id.chat_out_image);
            timeTv = itemView.findViewById(R.id.chat_out_time);
            read = itemView.findViewById(R.id.chat_out_read);
        }

        void bind(Message m, SimpleDateFormat tf, @NonNull Set<String> peerReadIds) {
            String body = m.getContent() != null ? m.getContent() : "";
            if ("IMAGE".equalsIgnoreCase(m.getMessageType())) {
                bubble.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);
                Glide.with(itemView)
                        .load(body)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(image);
            } else if ("VIDEO".equalsIgnoreCase(m.getMessageType())) {
                image.setVisibility(View.GONE);
                bubble.setVisibility(View.VISIBLE);
                body = itemView.getContext().getString(R.string.message_preview_sent_video);
                bubble.setText(body);
            } else {
                image.setVisibility(View.GONE);
                bubble.setVisibility(View.VISIBLE);
                bubble.setText(body);
            }
            Date c = m.getCreatedAt();
            timeTv.setText(c != null ? tf.format(c) : "");
            read.setVisibility(View.VISIBLE);
            String mid = m.getId();
            boolean peerRead = mid != null && peerReadIds.contains(mid);
            read.setText(peerRead
                    ? itemView.getContext().getString(R.string.chat_tick_read)
                    : itemView.getContext().getString(R.string.chat_tick_sent));
        }
    }
}
