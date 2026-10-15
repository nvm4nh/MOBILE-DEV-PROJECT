package com.seniortribe.app.models;

import java.io.Serializable;

public class ScheduleSession implements Serializable {
    private String scheduleId;
    private String needId;
    private long proposedTime;
    private boolean agreedByElderly;
    private String status; // PROPOSED, AGREED, CANCELLED
    private String location;
    private String notes;
    private String proposedBy;

    public ScheduleSession() {}

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }

    public long getProposedTime() { return proposedTime; }
    public void setProposedTime(long proposedTime) { this.proposedTime = proposedTime; }

    public boolean isAgreedByElderly() { return agreedByElderly; }
    public void setAgreedByElderly(boolean agreedByElderly) { this.agreedByElderly = agreedByElderly; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
}
