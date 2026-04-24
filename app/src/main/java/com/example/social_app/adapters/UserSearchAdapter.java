package com.example.social_app.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    private final Context context;
    private List<User> users;
    private final OnUserActionListener actionListener;
    private final Set<String> followedUserIds = new HashSet<>();
    private String currentUserId;
    private boolean hideFollowButtonForSelf = false;

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

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setHideFollowButtonForSelf(boolean hideFollowButtonForSelf) {
        this.hideFollowButtonForSelf = hideFollowButtonForSelf;
    }

    public void setFollowedUserIds(Set<String> ids) {
        followedUserIds.clear();
        if (ids != null) {
            followedUserIds.addAll(ids);
        }
        notifyDataSetChanged();
    }

    public void updateFollowState(String userId, boolean isFollowing) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        if (isFollowing) {
            followedUserIds.add(userId);
        } else {
            followedUserIds.remove(userId);
        }
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
        TextView followButton;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            username = itemView.findViewById(R.id.user_username);
            fullName = itemView.findViewById(R.id.user_fullname);
            followButton = itemView.findViewById(R.id.follow_button);
        }

        void bind(User user, int position) {
            String displayName = user.getFullName() != null && !user.getFullName().trim().isEmpty()
                    ? user.getFullName()
                    : context.getString(R.string.unknown_user);
            String userHandle = user.getUsername() != null && !user.getUsername().trim().isEmpty()
                    ? "@" + user.getUsername()
                    : context.getString(R.string.unknown_handle);

            username.setText(displayName);
            fullName.setText(userHandle);
            UserAvatarLoader.load(avatar, user.getAvatarUrl());
            String userId = user.getId();
            boolean isSelf = userId != null && userId.equals(currentUserId);
            boolean isFollowing = userId != null && followedUserIds.contains(userId);
            followButton.setText(isFollowing
                    ? context.getString(R.string.following)
                    : context.getString(R.string.follow));
            int paddingLeft = followButton.getPaddingLeft();
            int paddingTop = followButton.getPaddingTop();
            int paddingRight = followButton.getPaddingRight();
            int paddingBottom = followButton.getPaddingBottom();
            Drawable bg = context.getDrawable(isFollowing
                    ? R.drawable.bg_following_button
                    : R.drawable.bg_follow_button);
            followButton.setBackground(bg);
            followButton.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            followButton.setTextColor(context.getColor(isFollowing ? R.color.black : R.color.white));
            if (isSelf && hideFollowButtonForSelf) {
                followButton.setVisibility(View.GONE);
            } else {
                followButton.setVisibility(View.VISIBLE);
            }
            followButton.setEnabled(!isSelf);
            followButton.setAlpha(isSelf ? 0.5f : 1f);
            followButton.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onFollowClicked(user, position);
            });
            itemView.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onUserClicked(user);
            });
        }
    }
}