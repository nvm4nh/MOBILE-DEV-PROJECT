package com.seniortribe.app.models;

/** E07 · Dữ liệu người dùng nhập khi tạo sự kiện (chưa ghi lên Firestore). */
public class EventForm {
    public String title;
    public String content;
    public String place;
    public String area;
    public long startAt;
    public long endAt;
    public int capacity;
    public boolean allowCancel = true;
    public boolean openToOtherOrgs = true;
}
