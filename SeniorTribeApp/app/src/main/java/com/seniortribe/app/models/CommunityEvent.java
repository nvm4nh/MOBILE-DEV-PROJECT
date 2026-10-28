package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E07 · Sự kiện / hoạt động nhiều người do một tổ chức đăng (community_events/{eventId}).
 *
 * Các tổ chức trong mạng lưới đăng ký theo số thành viên tham gia; registeredCount được cập nhật
 * trong cùng transaction với phiếu đăng ký nên không vượt sức chứa (capacity).
 * Không ghi tên, địa chỉ hay ảnh của người cao tuổi vào sự kiện.
 */
@IgnoreExtraProperties
public class CommunityEvent {

    /** Hủy tham gia phải trước giờ bắt đầu ít nhất 24 giờ (khi allowCancel = true). */
    public static final long CANCEL_DEADLINE_MILLIS = 24L * 60 * 60 * 1000;

    @DocumentId
    private String eventId;
    private String title;
    private String content;
    private String place;
    private String area;
    private String orgId;
    private String orgName;
    private String createdByUid;
    private String createdByName;
    private Date startAt;
    private Date endAt;
    private long capacity;
    private long registeredCount;
    private boolean allowCancel;
    private boolean openToOtherOrgs;
    private boolean cancelled;
    private String lastRegOrgId;
    private Date createdAt;
    private Date updatedAt;

    public CommunityEvent() {}

    @Exclude
    public long getStartMillis() { return startAt != null ? startAt.getTime() : 0L; }

    @Exclude
    public long getEndMillis() { return endAt != null ? endAt.getTime() : getStartMillis(); }

    @Exclude
    public long getRemaining() { return Math.max(0, capacity - registeredCount); }

    @Exclude
    public boolean isFull() { return capacity > 0 && registeredCount >= capacity; }

    @Exclude
    public boolean hasStarted(long now) { return getStartMillis() <= now; }

    @Exclude
    public boolean hasEnded(long now) { return getEndMillis() <= now; }

    /** Còn được hủy / đổi số người: tổ chức cho phép hủy và còn hơn 24 giờ nữa mới bắt đầu. */
    @Exclude
    public boolean canCancelRegistration(long now) {
        return allowCancel && !cancelled && now < getStartMillis() - CANCEL_DEADLINE_MILLIS;
    }

    /** Tỉ lệ đã đăng ký (0..100) để vẽ thanh sức chứa. */
    @Exclude
    public int getFillPercent() {
        if (capacity <= 0) return 0;
        return (int) Math.min(100, Math.round(registeredCount * 100.0 / capacity));
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getCreatedByUid() { return createdByUid; }
    public void setCreatedByUid(String createdByUid) { this.createdByUid = createdByUid; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Date getStartAt() { return startAt; }
    public void setStartAt(Date startAt) { this.startAt = startAt; }
    public Date getEndAt() { return endAt; }
    public void setEndAt(Date endAt) { this.endAt = endAt; }
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    public long getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(long registeredCount) { this.registeredCount = registeredCount; }
    public boolean isAllowCancel() { return allowCancel; }
    public void setAllowCancel(boolean allowCancel) { this.allowCancel = allowCancel; }
    public boolean isOpenToOtherOrgs() { return openToOtherOrgs; }
    public void setOpenToOtherOrgs(boolean openToOtherOrgs) { this.openToOtherOrgs = openToOtherOrgs; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String getLastRegOrgId() { return lastRegOrgId; }
    public void setLastRegOrgId(String lastRegOrgId) { this.lastRegOrgId = lastRegOrgId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
