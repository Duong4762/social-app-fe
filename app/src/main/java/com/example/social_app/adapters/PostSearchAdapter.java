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
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.MockDataGenerator;

import java.util.ArrayList;
import java.util.List;

public class PostSearchAdapter extends RecyclerView.Adapter<PostSearchAdapter.PostViewHolder> {

    private Context context;
    private List<Post> posts;
    private OnPostClickListener clickListener;

    public interface OnPostClickListener {
        void onPostClicked(Post post);
    }

    public PostSearchAdapter(Context context, OnPostClickListener listener) {
        this.context = context;
        this.posts = new ArrayList<>();
        this.clickListener = listener;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts != null ? posts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_search, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar, postImage;
        TextView username, postContent, likeCount, commentCount;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.post_user_avatar);
            postImage = itemView.findViewById(R.id.post_image);
            username = itemView.findViewById(R.id.post_username);
            postContent = itemView.findViewById(R.id.post_content);
            likeCount = itemView.findViewById(R.id.post_like_count);
            commentCount = itemView.findViewById(R.id.post_comment_count);
        }

        void bind(Post post) {
            User user = MockDataGenerator.getUserById(post.getUserId());
            username.setText(user != null ? user.getFullName() : "Unknown User");
            postContent.setText(post.getCaption() != null ? post.getCaption() : "");
            likeCount.setText(String.valueOf(post.getLikeCount()));
            commentCount.setText(String.valueOf(post.getCommentCount()));
            userAvatar.setImageResource(R.drawable.avatar_placeholder);
            postImage.setImageResource(R.drawable.bg_nav_item_selected);
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onPostClicked(post);
            });
        }
    }
}