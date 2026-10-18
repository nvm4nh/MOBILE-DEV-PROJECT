\# F02 · Kiểm thử thu hồi quyền bằng Firestore Rules



Ngày kiểm thử: 17/10/2026.



\## Phạm vi



Bổ sung kiểm tra membership đang hoạt động khi đọc thông tin riêng của nhu cầu, tham gia chat và tạo lịch. Nền đối chiếu là Rules trên feat/auth-profile và main có cùng Git blob 4137b6cd614657adb24fcb79090f7574e6b6cb59.



\## Môi trường và kết quả



| Mục | Kết quả |

|---|---|

| Môi trường | Firestore Emulator v1.19.8, Java 17, dữ liệu và tài khoản giả lập |

| Cách kiểm tra | Request REST với danh tính Firebase giả lập; thao tác quản trị chỉ dùng để tạo dữ liệu thử |

| Bản sửa | 91 phép kiểm tra; 91 đạt; 0 không đạt |

| Đối chứng | Tái hiện các thao tác lọt quyền bằng Rules cũ rồi chạy cùng tình huống với Rules mới |



| Tình huống khi membership đã tắt | Rules cũ | Rules mới |

|---|---|---|

| UID được giao đọc thông tin riêng ở trạng thái SCHEDULED | HTTP 200 | HTTP 403 |

| UID được giao đọc chat | HTTP 200 | HTTP 403 |

| UID được giao gửi chat khi lần tiếp nhận còn hiệu lực | HTTP 200 | HTTP 403 |

| UID được giao tạo lịch ở trạng thái ACCEPTED | HTTP 200 | HTTP 403 |



Đã kiểm tra thêm: thành viên còn quyền tiếp tục sử dụng các thao tác hợp lệ; thu hồi quyền của cả phía đầu mối và đại diện; membership thiếu hoặc sai kiểu; quyền ở tổ chức khác không mở quyền của công việc hiện tại; lịch sử chat chỉ đọc khi luồng đã kết thúc và thành viên còn hoạt động; chống giả senderUid; hồ sơ cá nhân vẫn sửa được khi quyền nghiệp vụ đã tắt; chặn sửa chéo UID và tự cấp membership/xác minh tổ chức.



\## Giới hạn



Kết quả trên áp dụng cho Rules được thử trong Emulator. Không thay thế kiểm thử trên Firebase đang triển khai hoặc thao tác giao diện Android. Chưa kết luận toàn bộ F01–F03 hay luồng tích hợp nhóm đã hoàn tất. Phản ứng của màn Main khi membership thay đổi trong một phiên đang mở vẫn cần kiểm thử tích hợp.

