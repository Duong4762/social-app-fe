package com.example.social_app.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.Story;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.StoryViewHolder> {

    private Context context;
    private List<Story> stories;
    private Map<String, User> userCache = new HashMap<>();
    private OnStoryClickListener listener;
    private String currentUserId;
    private User currentUser; // 👈 thêm

    public interface OnStoryClickListener {
        void onStoryClick(Story story, User user);
        void onAddStoryClick();
    }

    public StoryAdapter(Context context, OnStoryClickListener listener) {
        this.context = context;
        this.stories = new ArrayList<>();
        this.listener = listener;
        this.currentUserId = FirebaseManager.getInstance().getAuth().getUid();

        loadCurrentUser(); // 👈 load user hiện tại
    }

    // ================== LOAD CURRENT USER ==================
    private void loadCurrentUser() {
        if (currentUserId == null) return;

        FirebaseFirestore.getInstance()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    currentUser = doc.toObject(User.class);
                    if (currentUser != null) {
                        currentUser.setId(doc.getId());
                        userCache.put(currentUserId, currentUser); // cache luôn
                        notifyDataSetChanged();
                    }
                });
    }

    // ================== SET STORIES ==================
    public void setStories(List<Story> stories) {
        this.stories = stories != null ? stories : new ArrayList<>();
        notifyDataSetChanged();
        loadAllUserInfo();
    }

    // ================== LOAD USERS ==================
    private void loadAllUserInfo() {
        for (Story story : stories) {
            if (!userCache.containsKey(story.getUserId())) {
                FirebaseFirestore.getInstance()
                        .collection(FirebaseManager.COLLECTION_USERS)
                        .document(story.getUserId())
                        .get()
                        .addOnSuccessListener(doc -> {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                user.setId(doc.getId());
                                Log.d("STORY", "Loaded user: " + user.getFullName());
                                userCache.put(story.getUserId(), user);
                                notifyDataSetChanged();
                            }
                        });
            }
        }
    }

    // ================== COUNT ==================
    @Override
    public int getItemCount() {
        boolean hasOwnStory = false;

        for (Story s : stories) {
            if (currentUserId != null && currentUserId.equals(s.getUserId())) {
                hasOwnStory = true;
                break;
            }
        }

        return stories.size() + (hasOwnStory ? 0 : 1);
    }

    // ================== CREATE VIEW ==================
    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false);
        return new StoryViewHolder(view);
    }

    // ================== BIND ==================
    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        boolean hasOwnStory = false;

        for (Story s : stories) {
            if (currentUserId != null && currentUserId.equals(s.getUserId())) {
                hasOwnStory = true;
                break;
            }
        }

        if (position == 0 && !hasOwnStory) {
            holder.bindAddStory();
        } else {
            int storyIndex = hasOwnStory ? position : position - 1;

            if (storyIndex >= 0 && storyIndex < stories.size()) {
                Story story = stories.get(storyIndex);
                User user = userCache.get(story.getUserId());
                holder.bind(story, user);
            }
        }
    }

    // ================== VIEW HOLDER ==================
    class StoryViewHolder extends RecyclerView.ViewHolder {
        ImageView storyRing;
        com.google.android.material.imageview.ShapeableImageView avatar;
        TextView username;
        ImageView addStoryBtn;

        StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            storyRing = itemView.findViewById(R.id.story_ring);
            avatar = itemView.findViewById(R.id.story_avatar);
            username = itemView.findViewById(R.id.story_username);
            addStoryBtn = itemView.findViewById(R.id.add_story_btn);
        }

        // ===== STORY NORMAL =====
        void bind(Story story, User user) {
            if (user != null) {
                username.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
                UserAvatarLoader.load(avatar, user.getAvatarUrl());
            } else {
                username.setText("User");
                avatar.setImageResource(R.drawable.avatar_placeholder);
            }

            storyRing.setVisibility(View.VISIBLE);
            addStoryBtn.setVisibility(View.GONE);

            itemView.setOnClickListener(v -> {
                if (listener != null && user != null) {
                    listener.onStoryClick(story, user);
                }
            });
        }

        // ===== ADD STORY =====
        void bindAddStory() {
            username.setText("Thêm story");

            // 🔥 FIX CHÍNH: dùng avatar current user
            if (currentUser != null) {
                UserAvatarLoader.load(avatar, currentUser.getAvatarUrl());
            } else {
                avatar.setImageResource(R.drawable.avatar_placeholder);
            }

            storyRing.setVisibility(View.GONE);
            addStoryBtn.setVisibility(View.VISIBLE);

            addStoryBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddStoryClick();
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddStoryClick();
                }
            });
        }
    }
}