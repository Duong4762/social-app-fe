package com.example.social_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;

import java.util.ArrayList;
import java.util.List;

public class AdminReportedUserAdapter extends RecyclerView.Adapter<AdminReportedUserAdapter.ReportedUserViewHolder> {

    public interface OnAdminActionListener {
        void onToggleBan(User user, int reportCount);
    }

    public static class ReportedUserItem {
        private final User user;
        private final int reportCount;

        public ReportedUserItem(User user, int reportCount) {
            this.user = user;
            this.reportCount = reportCount;
        }

        public User getUser() {
            return user;
        }

        public int getReportCount() {
            return reportCount;
        }
    }

    private final List<ReportedUserItem> items = new ArrayList<>();
    private final OnAdminActionListener listener;

    public AdminReportedUserAdapter(OnAdminActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReportedUserItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportedUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_reported_user, parent, false);
        return new ReportedUserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportedUserViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ReportedUserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvUsername;
        private final TextView tvEmail;
        private final TextView tvReportCount;
        private final TextView tvBanStatus;
        private final Button btnBanToggle;

        ReportedUserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvReportCount = itemView.findViewById(R.id.tvReportCount);
            tvBanStatus = itemView.findViewById(R.id.tvBanStatus);
            btnBanToggle = itemView.findViewById(R.id.btnBanToggle);
        }

        void bind(ReportedUserItem item) {
            User user = item.getUser();
            boolean isBanned = user.isBanned();
            tvName.setText(user.getFullName() != null ? user.getFullName() : "Unknown");
            tvUsername.setText("@" + (user.getUsername() != null ? user.getUsername() : "user"));
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");
            tvReportCount.setText("Reports: " + item.getReportCount());
            tvBanStatus.setText(isBanned ? "Status: Banned" : "Status: Active");
            btnBanToggle.setText(isBanned ? "Unban" : "Ban");
            btnBanToggle.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleBan(user, item.getReportCount());
                }
            });
        }
    }
}

