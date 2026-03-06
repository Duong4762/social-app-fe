package com.example.social_app.models;

/**
 * Model class representing a post in the social app.
 * Contains all information about a user's post including content, images, and engagement metrics.
 */
public class Post {
    private String id;
    private User user;
    private String content;
    private String imageUrl;
    private String location;
    private long timestamp;
    private int likeCount;
    private int commentCount;
    private int shareCount;
    private boolean isLiked;
    private boolean isBookmarked;

    public Post(String id, User user, String content, String imageUrl, String location,
                long timestamp, int likeCount, int commentCount, int shareCount) {
        this.id = id;
        this.user = user;
        this.content = content;
        this.imageUrl = imageUrl;
        this.location = location;
        this.timestamp = timestamp;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.shareCount = shareCount;
        this.isLiked = false;
    }

    // Getters
    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getShareCount() {
        return shareCount;
    }

    public boolean isLiked() {
        return isLiked;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }

    public void setLiked(boolean liked) {
        isLiked = liked;
    }

    public boolean isBookmarked() { return isBookmarked; }
    public void setBookmarked(boolean bookmarked) { this.isBookmarked = bookmarked; }

    /**
     * Toggles the like state of the post and updates like count accordingly.
     */
    public void toggleLike() {
        this.isLiked = !this.isLiked;
        this.likeCount += this.isLiked ? 1 : -1;
    }
}

