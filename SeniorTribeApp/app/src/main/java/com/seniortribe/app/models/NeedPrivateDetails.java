package com.seniortribe.app.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * Thông tin riêng của người được hỗ trợ (collection "needs_private", cùng id với bản tóm tắt).
 *
 * Chỉ thành viên đơn vị ghi nhận, và đại diện được giao sau khi đã chốt lịch, mới đọc được
 * (kiểm tra bằng Security Rules trên máy chủ). Lớp này không implements Serializable
 * và không được lưu xuống thiết bị (không đưa vào bản nháp hay SQLite).
 */
@IgnoreExtraProperties
public class NeedPrivateDetails {

    private String elderlyName;
    private String contact;
    private String address;
    private String recorderOrgId;

    public NeedPrivateDetails() {}

    public String getElderlyName() { return elderlyName; }
    public void setElderlyName(String elderlyName) { this.elderlyName = elderlyName; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRecorderOrgId() { return recorderOrgId; }
    public void setRecorderOrgId(String recorderOrgId) { this.recorderOrgId = recorderOrgId; }
}
