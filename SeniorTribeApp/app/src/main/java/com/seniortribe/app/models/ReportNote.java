package com.seniortribe.app.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

/**
 * E03 · Một dòng trong quá trình xử lý phản ánh (feedback_reports/{id}/notes/{noteId}).
 *  START: điều phối viên bắt đầu xử lý · ASK: điều phối viên hỏi thêm
 *  INFO: người gửi bổ sung thông tin · CLOSE: điều phối viên đóng phản ánh kèm kết quả
 */
@IgnoreExtraProperties
public class ReportNote {

    public static final String KIND_START = "START";
    public static final String KIND_ASK = "ASK";
    public static final String KIND_INFO = "INFO";
    public static final String KIND_CLOSE = "CLOSE";

    public static final String ROLE_REVIEWER = "REVIEWER";
    public static final String ROLE_REPORTER = "REPORTER";

    @DocumentId
    private String noteId;
    private String kind;
    private String text;
    private String byUid;
    private String byName;
    private String byRole;
    private Date at;

    public ReportNote() {}

    @Exclude
    public long getAtMillis() { return at != null ? at.getTime() : System.currentTimeMillis(); }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }
    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }
    public String getByRole() { return byRole; }
    public void setByRole(String byRole) { this.byRole = byRole; }
    public Date getAt() { return at; }
    public void setAt(Date at) { this.at = at; }
}
