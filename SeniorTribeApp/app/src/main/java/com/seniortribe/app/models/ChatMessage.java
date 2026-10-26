package com.seniortribe.app.models;

import com.google.firebase.firestore.Blob;
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
    @Exclude
    private boolean pending;        // còn chờ máy chủ xác nhận, không lưu lên Firestore

    // E06 · Tin ảnh / tệp (text khi đó là chú thích, có thể rỗng)
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_FILE = "FILE";

    private String type;            // null với tin cũ = TEXT
    private String attachmentId;
    private String fileName;
    private String mimeType;
    private long size;
    private Blob thumb;             // ảnh xem trước nhỏ (≤ 64 KB), chỉ có ở tin ảnh

    public ChatMessage() {}

    @Exclude
    public boolean isImage() { return TYPE_IMAGE.equals(type) && attachmentId != null; }

    @Exclude
    public boolean isFile() { return TYPE_FILE.equals(type) && attachmentId != null; }

    @Exclude
    public boolean hasAttachment() { return isImage() || isFile(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAttachmentId() { return attachmentId; }
    public void setAttachmentId(String attachmentId) { this.attachmentId = attachmentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public Blob getThumb() { return thumb; }
    public void setThumb(Blob thumb) { this.thumb = thumb; }

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

    @Exclude
    public boolean isPending() { return pending; }

    @Exclude
    public void setPending(boolean pending) { this.pending = pending; }

    public Date getAt() { return at; }
    public void setAt(Date at) { this.at = at; }
}
