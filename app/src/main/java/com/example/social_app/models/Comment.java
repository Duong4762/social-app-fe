package com.example.social_app.models;

import java.util.List;

/**
 * Model class representing a comment in the social app.
 * Contains information about a comment including user, text, timestamp, likes, and replies.
 */
public class Comment {
    private String id;
    private User user;
    private String text;
    private long timestamp;
    private String location;
    private int likeCount;
    private boolean isLiked;
    private List<Comment> replies;
    private boolean hasMoreReplies;

    public Comment(String id, User user, String text, long timestamp, int likeCount, List<Comment> replies, boolean hasMoreReplies) {
        this.id = id;
        this.user = user;
        this.text = text;
        this.timestamp = timestamp;
        this.location = null;
        this.likeCount = likeCount;
        this.isLiked = false;
        this.replies = replies;
        this.hasMoreReplies = hasMoreReplies;
    }

    // ...existing code...
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public boolean isLiked() {
        return isLiked;
    }

    public void setLiked(boolean liked) {
        isLiked = liked;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public void setReplies(List<Comment> replies) {
        this.replies = replies;
    }

    public boolean isHasMoreReplies() {
        return hasMoreReplies;
    }

    public void setHasMoreReplies(boolean hasMoreReplies) {
        this.hasMoreReplies = hasMoreReplies;
    }
}
