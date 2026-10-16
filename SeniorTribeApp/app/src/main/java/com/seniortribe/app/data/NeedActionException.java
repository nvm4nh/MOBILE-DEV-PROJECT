package com.seniortribe.app.data;

/**
 * Lỗi nghiệp vụ khi thao tác với nhu cầu (sai trạng thái, không đủ quyền, đã bị nhận...).
 *
 * Dùng RuntimeException thay vì FirebaseFirestoreException để transaction
 * KHÔNG tự chạy lại nhiều lần khi điều kiện nghiệp vụ không thỏa.
 * Message là câu tiếng Việt hiển thị trực tiếp cho người dùng.
 */
public class NeedActionException extends IllegalStateException {
    public NeedActionException(String message) {
        super(message);
    }
}
