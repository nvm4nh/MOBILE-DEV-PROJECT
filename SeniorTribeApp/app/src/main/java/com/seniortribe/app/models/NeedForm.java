package com.seniortribe.app.models;

/**
 * Dữ liệu người dùng nhập trên màn hình Ghi nhận nhu cầu (F06).
 * Gom lại thành một object để chuyển từ Activity sang ViewModel / Repository.
 */
public class NeedForm {

    // Phần tóm tắt (chia sẻ trong mạng lưới sau khi duyệt)
    public String title;
    public String topic;
    public String content;
    public String area;
    public String desiredTime;
    public String source;

    // Sự đồng ý
    public boolean consentRecorded;
    public String consentMethod;

    // Thông tin riêng (lưu tách sang needs_private)
    public String elderlyName;
    public String contact;
    public String address;
}
