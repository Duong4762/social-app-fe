package com.example.social_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.ReportViewHolder> {

    public static class Item {
        public String reportId;
        public String targetId;
        public String type;
        public String targetUserId;
        public String targetUsername;
        public String targetLabel;
        public String reporterName;
        public String reason;
        public Date createdAt;
        public String targetAvatarUrl;
        public String reporterAvatarUrl;
        public String postPreviewUrl;
    }

    public interface Listener {
        void onBanTargetUser(Item item);
        void onHandlePostReport(Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminReportAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ReportViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTargetType;
        private final TextView tvTargetLabel;
        private final TextView tvReporter;
        private final TextView tvReason;
        private final TextView tvTime;
        private final ImageView imgTargetAvatar;
        private final ImageView imgReporterAvatar;
        private final ImageView imgPostPreview;
        private final MaterialButton btnBanTargetUser;
        private final MaterialButton btnHandlePostReport;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTargetType = itemView.findViewById(R.id.tvTargetType);
            tvTargetLabel = itemView.findViewById(R.id.tvTargetLabel);
            tvReporter = itemView.findViewById(R.id.tvReporter);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvTime = itemView.findViewById(R.id.tvTime);
            imgTargetAvatar = itemView.findViewById(R.id.imgTargetAvatar);
            imgReporterAvatar = itemView.findViewById(R.id.imgReporterAvatar);
            imgPostPreview = itemView.findViewById(R.id.imgPostPreview);
            btnBanTargetUser = itemView.findViewById(R.id.btnBanTargetUser);
            btnHandlePostReport = itemView.findViewById(R.id.btnHandlePostReport);
        }

        void bind(Item item) {
            String type = item.type == null ? "UNKNOWN" : item.type.toUpperCase(Locale.getDefault());
            tvTargetType.setText(type);
            tvTargetLabel.setText(item.targetLabel == null || item.targetLabel.isEmpty() ? "-" : item.targetLabel);
            tvReporter.setText("Người báo cáo: " + safe(item.reporterName));
            tvReason.setText("Nội dung: " + safe(item.reason));
            tvTime.setText("Thời gian: " + (item.createdAt == null ? "-" : timeFormatter.format(item.createdAt)));
            loadImage(imgTargetAvatar, item.targetAvatarUrl);
            loadImage(imgReporterAvatar, item.reporterAvatarUrl);
            if (item.postPreviewUrl == null || item.postPreviewUrl.trim().isEmpty()) {
                imgPostPreview.setVisibility(View.GONE);
            } else {
                imgPostPreview.setVisibility(View.VISIBLE);
                loadImage(imgPostPreview, item.postPreviewUrl);
            }

            boolean canBanTargetUser = "USER".equalsIgnoreCase(type) && item.targetUserId != null && !item.targetUserId.isEmpty();
            btnBanTargetUser.setVisibility(canBanTargetUser ? View.VISIBLE : View.GONE);
            btnBanTargetUser.setOnClickListener(v -> {
                if (listener != null && canBanTargetUser) {
                    listener.onBanTargetUser(item);
                }
            });

            boolean canHandlePost = "POST".equalsIgnoreCase(type) && item.targetId != null && !item.targetId.isEmpty();
            btnHandlePostReport.setVisibility(canHandlePost ? View.VISIBLE : View.GONE);
            btnHandlePostReport.setOnClickListener(v -> {
                if (listener != null && canHandlePost) {
                    listener.onHandlePostReport(item);
                }
            });
        }

        private String safe(String value) {
            return value == null || value.trim().isEmpty() ? "-" : value.trim();
        }

        private void loadImage(@NonNull ImageView imageView, String url) {
            Glide.with(imageView.getContext())
                    .load(url)
                    .placeholder(R.drawable.avatar_placeholder)
                    .error(R.drawable.avatar_placeholder)
                    .into(imageView);
        }
    }
}
