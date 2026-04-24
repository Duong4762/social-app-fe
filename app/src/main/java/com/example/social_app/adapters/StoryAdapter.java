package com.example.social_app.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

public class StoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_STORY = 1;

    private final Context context;
    private List<Story> stories;
    private final Map<String, User> userCache = new HashMap<>();
    private final OnStoryClickListener listener;
    private final String currentUserId;

    public interface OnStoryClickListener {
        void onStoryClick(Story story, User user);

        void onAddStoryClick();
    }

    public StoryAdapter(Context context, OnStoryClickListener listener) {
        this.context = context;
        this.stories = new ArrayList<>();
        this.listener = listener;
        this.currentUserId = FirebaseManager.getInstance().getAuth().getUid();

        loadCurrentUser();
    }

    private void loadCurrentUser() {
        if (currentUserId == null) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    User me = doc.toObject(User.class);
                    if (me != null) {
                        me.setId(doc.getId());
                        userCache.put(currentUserId, me);
                        notifyDataSetChanged();
                    }
                });
    }

    public void setStories(List<Story> stories) {
        List<Story> list = stories != null ? new ArrayList<>(stories) : new ArrayList<>();
        this.stories = reorderOwnStoriesFirst(list);
        notifyDataSetChanged();
        loadAllUserInfo();
    }

    /** Story của tài khoản hiện tại đứng ngay sau ô “Thêm”. */
    @NonNull
    private List<Story> reorderOwnStoriesFirst(@NonNull List<Story> input) {
        if (currentUserId == null) {
            return input;
        }
        List<Story> own = new ArrayList<>();
        List<Story> rest = new ArrayList<>();
        for (Story s : input) {
            if (currentUserId.equals(s.getUserId())) {
                own.add(s);
            } else {
                rest.add(s);
            }
        }
        own.addAll(rest);
        return own;
    }

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

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_ADD : TYPE_STORY;
    }

    @Override
    public int getItemCount() {
        return stories.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_story_add, parent, false);
            return new AddStoryViewHolder(v);
        }
        View v = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false);
        return new StoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddStoryViewHolder) {
            ((AddStoryViewHolder) holder).bind(listener);
        } else if (holder instanceof StoryViewHolder) {
            int idx = position - 1;
            if (idx >= 0 && idx < stories.size()) {
                Story story = stories.get(idx);
                User user = userCache.get(story.getUserId());
                ((StoryViewHolder) holder).bind(story, user);
            }
        }
    }

    static final class AddStoryViewHolder extends RecyclerView.ViewHolder {

        AddStoryViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        void bind(OnStoryClickListener l) {
            itemView.setOnClickListener(v -> {
                if (l != null) {
                    l.onAddStoryClick();
                }
            });
        }
    }

    final class StoryViewHolder extends RecyclerView.ViewHolder {
        private final View storyRing;
        private final com.google.android.material.imageview.ShapeableImageView avatar;
        private final TextView username;

        StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            storyRing = itemView.findViewById(R.id.story_ring);
            avatar = itemView.findViewById(R.id.story_avatar);
            username = itemView.findViewById(R.id.story_username);
        }

        void bind(@NonNull Story story, @Nullable User user) {
            if (user != null) {
                username.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
                UserAvatarLoader.load(avatar, user.getAvatarUrl());
            } else {
                username.setText("User");
                avatar.setImageResource(R.drawable.avatar_placeholder);
            }

            boolean ownStory = currentUserId != null && currentUserId.equals(story.getUserId());
            boolean isViewed = story.getViewedBy() != null && story.getViewedBy().contains(currentUserId);

            if (ownStory || isViewed) {
                storyRing.setBackgroundResource(R.drawable.story_ring_gray);
            } else {
                storyRing.setBackgroundResource(R.drawable.story_ring);
            }
            storyRing.setAlpha(1f);
            storyRing.setVisibility(View.VISIBLE);

            itemView.setOnClickListener(v -> {
                if (listener != null && user != null) {
                    listener.onStoryClick(story, user);
                }
            });
        }
    }
}
