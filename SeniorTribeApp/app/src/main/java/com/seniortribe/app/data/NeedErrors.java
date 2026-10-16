package com.seniortribe.app.data;

import com.google.firebase.firestore.FirebaseFirestoreException;

/** Đổi lỗi kỹ thuật thành câu thông báo dễ hiểu cho người dùng. */
public final class NeedErrors {

    private NeedErrors() {}

    public static String message(Throwable error) {
        Throwable e = error;
        // Lỗi trong transaction đôi khi bị bọc một lớp
        while (e != null && !(e instanceof NeedActionException)
                && !(e instanceof FirebaseFirestoreException) && e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof NeedActionException) {
            return e.getMessage();
        }
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                    return "Tài khoản của bạn không có quyền thực hiện thao tác này.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.";
                case NOT_FOUND:
                    return "Không tìm thấy nhu cầu. Có thể nhu cầu đã bị xóa.";
                case FAILED_PRECONDITION:
                case ABORTED:
                    return "Dữ liệu vừa thay đổi ở máy khác. Vui lòng thử lại.";
                default:
                    break;
            }
        }
        return "Có lỗi xảy ra. Vui lòng thử lại.";
    }
}
