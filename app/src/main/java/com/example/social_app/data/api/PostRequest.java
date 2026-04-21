package com.example.social_app.data.api;

import com.example.social_app.data.model.PostMedia;
import java.util.List;

public class PostRequest {
    private String caption;
    private String visibility;
    private String location;
    private List<PostMedia> media;
    private List<String> taggedUserIds;

    public PostRequest() {}

    public PostRequest(String caption, String visibility, List<PostMedia> media) {
        this.caption = caption;
        this.visibility = visibility;
        this.media = media;
    }

    // Getters and Setters
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<PostMedia> getMedia() { return media; }
    public void setMedia(List<PostMedia> media) { this.media = media; }

    public List<String> getTaggedUserIds() { return taggedUserIds; }
    public void setTaggedUserIds(List<String> taggedUserIds) { this.taggedUserIds = taggedUserIds; }
}
