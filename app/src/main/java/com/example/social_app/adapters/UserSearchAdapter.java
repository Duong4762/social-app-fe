package com.example.social_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    private Context context;
    private List<User> users;
    private OnUserActionListener actionListener;

    public interface OnUserActionListener {
        void onUserClicked(User user);
        void onFollowClicked(User user, int position);
    }

    public UserSearchAdapter(Context context, OnUserActionListener listener) {
        this.context = context;
        this.users = new ArrayList<>();
        this.actionListener = listener;
    }

    public void setUsers(List<User> users) {
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(users.get(position), position);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView username;
        TextView fullName;
        Button followButton;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            username = itemView.findViewById(R.id.user_username);
            fullName = itemView.findViewById(R.id.user_fullname);
            followButton = itemView.findViewById(R.id.follow_button);
        }

        void bind(User user, int position) {
            username.setText("@" + (user.getUsername() != null ? user.getUsername() : "user"));
            fullName.setText(user.getFullName() != null ? user.getFullName() : "User Name");
            avatar.setImageResource(R.drawable.avatar_placeholder);
            followButton.setText("Follow");
            followButton.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onFollowClicked(user, position);
            });
            itemView.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onUserClicked(user);
            });
        }
    }
}