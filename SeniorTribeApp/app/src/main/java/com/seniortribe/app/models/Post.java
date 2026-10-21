package com.seniortribe.app.models;

import java.io.Serializable;

public class Post implements Serializable {
    private String id;
    private String title;
    private String content;
    private String authorOrg;
    private String authorUid;
    private String imageUrl;
    private int likesCount;
    private long createdAt;
    private boolean isCached;

    public Post() {}

    public Post(String id, String title, String content, String authorOrg, String authorUid) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.authorOrg = authorOrg;
        this.authorUid = authorUid;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthorOrg() { return authorOrg; }
    public void setAuthorOrg(String authorOrg) { this.authorOrg = authorOrg; }

    public String getAuthorUid() { return authorUid; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isCached() { return isCached; }
    public void setCached(boolean cached) { isCached = cached; }
}
