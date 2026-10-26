package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * Một dòng lịch sử trong needs_summary/{needId}/events (F15).
 * Chỉ thêm, không sửa / xóa (Security Rules), nên dùng được làm nhật ký đối soát.
 */
@IgnoreExtraProperties
public class NeedEvent {

    @DocumentId
    private String eventId;
    private String type;
    private String fromStatus;
    private String toStatus;
    private String byUid;
    private String byName;
    private String byOrgId;         // tổ chức của người thao tác (để biết bên nào gửi yêu cầu, E01)
    private String note;
    private Date at;

    public NeedEvent() {}

    @Exclude
    public long getAtMillis() { return at != null ? at.getTime() : 0L; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }

    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }

    public String getByOrgId() { return byOrgId; }
    public void setByOrgId(String byOrgId) { this.byOrgId = byOrgId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Date getAt() { return at; }
    public void setAt(Date at) { this.at = at; }
}
