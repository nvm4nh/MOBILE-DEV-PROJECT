package com.seniortribe.app.models;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private String messageId;
    private String needId;
    private String senderUid;
    private String senderName;
    private String text;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String needId, String senderUid, String senderName, String text) {
        this.needId = needId;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
