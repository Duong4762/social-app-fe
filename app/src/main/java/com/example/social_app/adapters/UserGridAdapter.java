package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.UserAvatarLoader;

import java.util.ArrayList;
import java.util.List;

public class UserGridAdapter extends RecyclerView.Adapter<UserGridAdapter.UserGridViewHolder> {

    private Context context;
    private List<User> users;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserGridAdapter(Context context, OnUserClickListener listener) {
        this.context = context;
        this.users = new ArrayList<>();
        this.listener = listener;
    }

    public void setUsers(List<User> users) {
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserGridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_grid, parent, false);
        return new UserGridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserGridViewHolder holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class UserGridViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView username;

        UserGridViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.grid_avatar);
            username = itemView.findViewById(R.id.grid_username);
        }

        void bind(User user) {
            username.setText("@" + (user.getUsername() != null ? user.getUsername() : "user"));
            UserAvatarLoader.load(avatar, user.getAvatarUrl());
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(user);
            });
        }
    }
}