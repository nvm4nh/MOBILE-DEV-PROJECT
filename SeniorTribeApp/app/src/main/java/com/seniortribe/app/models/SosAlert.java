package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E09 · Một báo khẩn trong needs_assignments/{assignmentId}/sos (chỉ đọc).
 *
 * Báo khẩn KHÔNG thay cho việc gọi 115 / 113 / 114; nó giúp người còn lại trong lần phối hợp biết
 * ngay chuyện gì đang xảy ra để liên hệ người nhà của cụ, hỗ trợ thêm. Trạng thái: OPEN → ACK
 * (bên kia đã nhận) → RESOLVED (đã xử lý xong, có ghi chú).
 */
@IgnoreExtraProperties
public class SosAlert {

    public static final String OPEN = "OPEN";
    public static final String ACK = "ACK";
    public static final String RESOLVED = "RESOLVED";

    public static final String KIND_MEDICAL = "MEDICAL";
    public static final String KIND_SAFETY = "SAFETY";
    public static final String KIND_FIRE = "FIRE";
    public static final String KIND_OTHER = "OTHER";

    @DocumentId
    private String alertId;
    private String byUid;
    private String byName;
    private String kind;
    private String note;
    private String status;
    private String calledNumber;
    private GeoPoint location;
    private Double accuracyM;
    private Date createdAt;
    private String ackByUid;
    private String ackByName;
    private Date ackAt;
    private String resolvedByUid;
    private String resolvedByName;
    private Date resolvedAt;
    private String resolveNote;

    public SosAlert() {}

    @Exclude
    public long getCreatedAtMillis() { return createdAt != null ? createdAt.getTime() : System.currentTimeMillis(); }

    @Exclude
    public boolean isOpen() { return OPEN.equals(status); }

    @Exclude
    public boolean isResolved() { return RESOLVED.equals(status); }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }

    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCalledNumber() { return calledNumber; }
    public void setCalledNumber(String calledNumber) { this.calledNumber = calledNumber; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public Double getAccuracyM() { return accuracyM; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getAckByUid() { return ackByUid; }
    public void setAckByUid(String ackByUid) { this.ackByUid = ackByUid; }

    public String getAckByName() { return ackByName; }
    public void setAckByName(String ackByName) { this.ackByName = ackByName; }

    public Date getAckAt() { return ackAt; }
    public void setAckAt(Date ackAt) { this.ackAt = ackAt; }

    public String getResolvedByUid() { return resolvedByUid; }
    public void setResolvedByUid(String resolvedByUid) { this.resolvedByUid = resolvedByUid; }

    public String getResolvedByName() { return resolvedByName; }
    public void setResolvedByName(String resolvedByName) { this.resolvedByName = resolvedByName; }

    public Date getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Date resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolveNote() { return resolveNote; }
    public void setResolveNote(String resolveNote) { this.resolveNote = resolveNote; }
}
