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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_STORY = 1;

    /**
     * Một user — nhiều story; trên trang chủ chỉ hiển thị một vòng avatar.
     */
    public static final class StoryGroup {
        @NonNull
        private final String userId;
        @NonNull
        private final List<Story> stories;

        public StoryGroup(@NonNull String userId, @NonNull List<Story> stories) {
            this.userId = userId;
            this.stories = stories;
        }

        @NonNull
        public String getUserId() {
            return userId;
        }

        /** Cũ nhất trước → mới nhất sau (thứ tự xem trong popup). */
        @NonNull
        public List<Story> getStories() {
            return stories;
        }
    }

    private final Context context;
    private List<StoryGroup> storyGroups = new ArrayList<>();
    private final Map<String, User> userCache = new HashMap<>();
    private final OnStoryClickListener listener;
    private final String currentUserId;

    public interface OnStoryClickListener {
        void onStoryClick(@NonNull List<Story> storiesForUser, @NonNull User user);

        void onAddStoryClick();
    }

    public StoryAdapter(Context context, OnStoryClickListener listener) {
        this.context = context;
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
        list = reorderOwnStoriesFirst(list);
        this.storyGroups = buildGroups(list);
        this.storyGroups = reorderGroupsOwnFirst(this.storyGroups);
        notifyDataSetChanged();
        loadAllUserInfo();
    }

    /** Story của tài khoản hiện tại đứng đầu danh sách phẳng trước khi gộp nhóm. */
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

    /**
     * Gộp theo userId, giữ thứ tự xuất hiện user lần đầu; trong mỗi nhóm sort theo thời gian tạo tăng dần (xem cũ → mới).
     */
    @NonNull
    private List<StoryGroup> buildGroups(@NonNull List<Story> flat) {
        Map<String, List<Story>> byUser = new LinkedHashMap<>();
        List<String> userOrder = new ArrayList<>();
        for (Story s : flat) {
            String uid = s.getUserId();
            if (uid == null) {
                continue;
            }
            if (!byUser.containsKey(uid)) {
                userOrder.add(uid);
                byUser.put(uid, new ArrayList<>());
            }
            byUser.get(uid).add(s);
        }
        List<StoryGroup> groups = new ArrayList<>();
        for (String uid : userOrder) {
            List<Story> lst = byUser.get(uid);
            if (lst == null || lst.isEmpty()) {
                continue;
            }
            Collections.sort(lst, (a, b) -> {
                Date da = a.getCreatedAt();
                Date db = b.getCreatedAt();
                if (da == null && db == null) {
                    return 0;
                }
                if (da == null) {
                    return -1;
                }
                if (db == null) {
                    return 1;
                }
                return da.compareTo(db);
            });
            groups.add(new StoryGroup(uid, lst));
        }
        return groups;
    }

    @NonNull
    private List<StoryGroup> reorderGroupsOwnFirst(@NonNull List<StoryGroup> groups) {
        if (currentUserId == null) {
            return groups;
        }
        List<StoryGroup> own = new ArrayList<>();
        List<StoryGroup> rest = new ArrayList<>();
        for (StoryGroup g : groups) {
            if (currentUserId.equals(g.getUserId())) {
                own.add(g);
            } else {
                rest.add(g);
            }
        }
        own.addAll(rest);
        return own;
    }

    private void loadAllUserInfo() {
        for (StoryGroup group : storyGroups) {
            String uid = group.getUserId();
            if (!userCache.containsKey(uid)) {
                FirebaseFirestore.getInstance()
                        .collection(FirebaseManager.COLLECTION_USERS)
                        .document(uid)
                        .get()
                        .addOnSuccessListener(doc -> {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                user.setId(doc.getId());
                                Log.d("STORY", "Loaded user: " + user.getFullName());
                                userCache.put(uid, user);
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
        return storyGroups.size() + 1;
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
            if (idx >= 0 && idx < storyGroups.size()) {
                StoryGroup group = storyGroups.get(idx);
                User user = userCache.get(group.getUserId());
                ((StoryViewHolder) holder).bind(group, user);
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

        void bind(@NonNull StoryGroup group, @Nullable User user) {
            if (user != null) {
                username.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
                UserAvatarLoader.load(avatar, user.getAvatarUrl());
            } else {
                username.setText("User");
                avatar.setImageResource(R.drawable.avatar_placeholder);
            }

            boolean allViewed = true;
            for (Story s : group.getStories()) {
                boolean seen = currentUserId != null
                        && s.getViewedBy() != null
                        && s.getViewedBy().contains(currentUserId);
                if (!seen) {
                    allViewed = false;
                    break;
                }
            }

            if (allViewed) {
                storyRing.setBackgroundResource(R.drawable.story_ring_gray);
            } else {
                storyRing.setBackgroundResource(R.drawable.story_ring);
            }
            storyRing.setAlpha(1f);
            storyRing.setVisibility(View.VISIBLE);

            itemView.setOnClickListener(v -> {
                if (listener != null && user != null) {
                    listener.onStoryClick(new ArrayList<>(group.getStories()), user);
                }
            });
        }
    }
}
