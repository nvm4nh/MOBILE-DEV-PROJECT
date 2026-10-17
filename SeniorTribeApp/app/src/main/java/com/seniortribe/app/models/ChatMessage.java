package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Một tin nhắn trong needs_assignments/{assignmentId}/messages (F11).
 *
 * Chat gắn theo lần tiếp nhận, không theo nhu cầu: khi nhu cầu bị trả lại và tổ chức khác
 * nhận, luồng mới bắt đầu trống và người phụ trách cũ không đọc được (R08).
 */
@IgnoreExtraProperties
public class ChatMessage {

    @DocumentId
    private String messageId;
    private String senderUid;
    private String senderName;
    private String text;
    @ServerTimestamp
    private Date at;

    public ChatMessage() {}

    /** Thời điểm gửi; tin vừa gửi chưa có timestamp của máy chủ thì coi như bây giờ. */
    @Exclude
    public long getTimestamp() {
        return at != null ? at.getTime() : System.currentTimeMillis();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Date getAt() { return at; }
    public void setAt(Date at) { this.at = at; }
}
