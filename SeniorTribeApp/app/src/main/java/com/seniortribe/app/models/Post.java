package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

/**
 * Bài đăng trên Bảng tin (F04, F05) — document trong collection {@code posts}.
 *
 * Field trên Firestore: title, content, orgId, orgName, orgVerified, authorUid, authorName,
 * eventTime, eventPlace (không bắt buộc), hidden, createdAt, updatedAt. Bài bị ẩn ({@code hidden = true}) không hiện trên bảng tin.
 * Bản sao để xem offline nằm trong SQLite (AppDatabaseHelper), không lưu thông tin riêng.
 */
@IgnoreExtraProperties
public class Post implements Serializable {

    @DocumentId
    private String id;
    private String title;
    private String content;
    private String orgId;
    private String orgName;
    private String authorUid;
    private String authorName;
    /** Tổ chức đã được xác minh (lấy từ organizations/{orgId}.verified lúc đăng, rules kiểm tra lại). */
    private boolean orgVerified;
    /** "Thông tin hoạt động" trên màn chi tiết (không bắt buộc). */
    private String eventTime;
    private String eventPlace;
    private boolean hidden;
    @ServerTimestamp
    private Date createdAt;
    private Date updatedAt;

    /** true khi bài được đọc từ SQLite (không có mạng), không lưu lên Firestore. */
    private transient boolean cached;

    public Post() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getAuthorUid() { return authorUid; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public boolean isOrgVerified() { return orgVerified; }
    public void setOrgVerified(boolean orgVerified) { this.orgVerified = orgVerified; }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    public String getEventPlace() { return eventPlace; }
    public void setEventPlace(String eventPlace) { this.eventPlace = eventPlace; }

    /** Có ít nhất một dòng trong ô "Thông tin hoạt động". */
    @Exclude
    public boolean hasEventInfo() {
        return (eventTime != null && !eventTime.trim().isEmpty())
                || (eventPlace != null && !eventPlace.trim().isEmpty());
    }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Exclude
    public boolean isCached() { return cached; }
    @Exclude
    public void setCached(boolean cached) { this.cached = cached; }

    /** Thời điểm đăng (ms). Bài vừa đăng chưa có giờ máy chủ thì coi như "vừa xong". */
    @Exclude
    public long getCreatedAtMillis() {
        return createdAt != null ? createdAt.getTime() : System.currentTimeMillis();
    }

    /** Tên tổ chức để hiển thị; giữ tên hàm cũ cho các màn hình đang dùng. */
    @Exclude
    public String getAuthorOrg() { return orgName; }
}
