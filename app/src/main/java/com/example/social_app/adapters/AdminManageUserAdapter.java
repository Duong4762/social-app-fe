package com.example.social_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminManageUserAdapter extends RecyclerView.Adapter<AdminManageUserAdapter.UserViewHolder> {

    public interface Listener {
        void onEdit(User user);
        void onToggleBan(User user);
    }

    private final List<User> items = new ArrayList<>();
    private final Listener listener;
    private final Set<String> expandedUserIds = new HashSet<>();

    public AdminManageUserAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<User> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvUserName;
        private final TextView tvUserRole;
        private final TextView tvStatus;
        private final TextView tvDetailEmail;
        private final TextView tvDetailBio;
        private final TextView tvDetailGender;
        private final TextView tvDetailDob;
        private final MaterialButton btnView;
        private final MaterialButton btnEdit;
        private final MaterialButton btnBan;
        private final MaterialButton btnHideDetails;
        private final View layoutDetails;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDetailEmail = itemView.findViewById(R.id.tvDetailEmail);
            tvDetailBio = itemView.findViewById(R.id.tvDetailBio);
            tvDetailGender = itemView.findViewById(R.id.tvDetailGender);
            tvDetailDob = itemView.findViewById(R.id.tvDetailDob);
            btnView = itemView.findViewById(R.id.btnView);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnBan = itemView.findViewById(R.id.btnBan);
            btnHideDetails = itemView.findViewById(R.id.btnHideDetails);
            layoutDetails = itemView.findViewById(R.id.layoutDetails);
        }

        void bind(User user) {
            String userId = user.getId() == null ? "" : user.getId();
            boolean expanded = expandedUserIds.contains(userId);
            tvUserName.setText(user.getFullName() == null || user.getFullName().isEmpty() ? "Unknown" : user.getFullName());
            tvUserRole.setText(user.getRole() == null || user.getRole().isEmpty() ? "USER" : user.getRole());
            tvStatus.setText(user.isBanned() ? "BANNED" : "ACTIVE");
            tvStatus.setTextColor(itemView.getContext().getColor(user.isBanned() ? R.color.accent_red : R.color.accent_green));

            tvDetailEmail.setText("Email: " + safe(user.getEmail()));
            tvDetailBio.setText("Bio: " + safe(user.getBio()));
            tvDetailGender.setText("Gender: " + safe(user.getGender()));
            tvDetailDob.setText("Date of birth: " + safe(user.getDateOfBirth()));

            layoutDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
            btnEdit.setVisibility(expanded ? View.VISIBLE : View.GONE);
            btnView.setText(expanded ? "Viewed" : "View info");
            btnBan.setText(user.isBanned() ? "Unban" : "Ban");

            btnView.setOnClickListener(v -> {
                if (userId.isEmpty()) {
                    return;
                }
                expandedUserIds.add(userId);
                notifyItemChanged(getBindingAdapterPosition());
            });

            btnHideDetails.setOnClickListener(v -> {
                expandedUserIds.remove(userId);
                notifyItemChanged(getBindingAdapterPosition());
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(user);
                }
            });

            btnBan.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleBan(user);
                }
            });
        }

        private String safe(String text) {
            return text == null || text.trim().isEmpty() ? "-" : text.trim();
        }
    }
}
