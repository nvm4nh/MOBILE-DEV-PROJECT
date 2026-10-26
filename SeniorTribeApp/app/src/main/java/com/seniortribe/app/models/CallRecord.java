package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E06 · Một cuộc gọi thoại / video trong needs_assignments/{assignmentId}/calls (chỉ đọc).
 *
 * Document vừa là kênh trao đổi tín hiệu WebRTC (offer / answer) vừa là nhật ký cuộc gọi
 * hiện trong chat ("Cuộc gọi video · 3 phút", "Cuộc gọi nhỡ"). Nhật ký này không làm giả được
 * vì Security Rules chỉ cho đổi trạng thái theo đúng vai người gọi / người nghe.
 */
@IgnoreExtraProperties
public class CallRecord {

    public static final String RINGING = "RINGING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String MISSED = "MISSED";
    public static final String ENDED = "ENDED";

    /** Sau thời gian này mà chưa ai nghe thì coi như gọi nhỡ. */
    public static final long RING_TIMEOUT_MS = 45_000;

    @DocumentId
    private String callId;
    private String callerUid;
    private String callerName;
    private String calleeUid;
    private boolean video;
    private String status;
    private Date createdAt;
    private Date answeredAt;
    private Date endedAt;
    private String endedByUid;
    private Long durationSec;

    public CallRecord() {}

    @Exclude
    public boolean isRinging() { return RINGING.equals(status); }

    @Exclude
    public boolean isLive() { return RINGING.equals(status) || ACCEPTED.equals(status); }

    @Exclude
    public long getCreatedAtMillis() { return createdAt != null ? createdAt.getTime() : System.currentTimeMillis(); }

    @Exclude
    public long getEndedAtMillis() { return endedAt != null ? endedAt.getTime() : 0L; }

    /** Cuộc gọi đang đổ chuông mà còn mới (chưa quá thời gian chờ), để hiện màn hình nghe máy. */
    @Exclude
    public boolean isFreshRinging(long now) {
        return isRinging() && now - getCreatedAtMillis() < RING_TIMEOUT_MS;
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getCallerUid() { return callerUid; }
    public void setCallerUid(String callerUid) { this.callerUid = callerUid; }

    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }

    public String getCalleeUid() { return calleeUid; }
    public void setCalleeUid(String calleeUid) { this.calleeUid = calleeUid; }

    public boolean isVideo() { return video; }
    public void setVideo(boolean video) { this.video = video; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Date answeredAt) { this.answeredAt = answeredAt; }

    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date endedAt) { this.endedAt = endedAt; }

    public String getEndedByUid() { return endedByUid; }
    public void setEndedByUid(String endedByUid) { this.endedByUid = endedByUid; }

    public Long getDurationSec() { return durationSec; }
    public void setDurationSec(Long durationSec) { this.durationSec = durationSec; }
}
