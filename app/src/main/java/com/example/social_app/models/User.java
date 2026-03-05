package com.example.social_app.models;

/**
 * Model class representing a user in the social app.
 * Contains user profile information.
 */
public class User {
    private String id;
    private String name;
    private String avatarUrl;
    private String bio;

    public User(String id, String name, String avatarUrl, String bio) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}

