\# F02 · Theo dõi quyền trong phiên đang mở



Ngày chuẩn bị: 18/10/2026. Nhánh áp dụng: feat/auth-profile.



\## Hành vi



\- Main chờ xác nhận membership từ máy chủ trước khi hiện nội dung nghiệp vụ.

\- Khi không còn membership hoạt động với vai trò hợp lệ, xóa quyền trong phiên và mở lại SessionGate.

\- Khi tập tổ chức/vai trò hoạt động thay đổi nhưng vẫn còn quyền, đi qua SessionGate để xác thực lại và tải lại module với quyền mới.

\- Dữ liệu cache hoặc pending writes không được dùng để cấp hay thu hồi quyền. App chuyển sang kiểm tra; quá thời gian chờ xác nhận máy chủ thì kết thúc quyền trong phiên để kiểm tra lại.

\- Không tự đăng xuất Firebase Auth khi mất quyền tổ chức. Hồ sơ cá nhân vẫn dùng qua màn chờ.

\- Callback cũ, timeout đã hủy và kết quả tải cache từ phiên trước không được khôi phục quyền cũ.

\- Khi trở lại Main sau khi ra nền, xác nhận lại dữ liệu máy chủ. Chỉ khôi phục trạng thái màn hình nếu UID khớp phiên hiện tại.



\## Kết quả tại môi trường chuẩn bị



| Kiểm tra | Kết quả |

|---|---|

| :app:assembleDebug | Đạt |

| :app:testDebugUnitTest | 70 test; 0 thất bại; 0 lỗi; 0 bỏ qua |

| Bộ test mới LiveAccessMonitorTest | 34 test chuyển trạng thái, dữ liệu cache, đổi vai trò, đổi UID, listener và timeout cũ |

| Bộ test trước đó | 20 AuthPolicyTest và 16 ProfilePolicyTest |

| MembershipRepository | Build qua; thêm kiểm tra UID/thế hệ request và đọc server khi tải quyền/danh sách đại diện |



Các test mới chạy với nguồn snapshot và đồng hồ giả lập, dùng lớp LiveAccessMonitor thật. Chưa thay thế kiểm thử listener Firebase thật, vòng đời Activity hoặc thao tác điều hướng trên máy ảo.



\## Kiểm thử giao diện còn chờ



| Tình huống | Kỳ vọng | Trạng thái |

|---|---|---|

| Đang ở Main, tắt membership hoạt động cuối cùng | Tự về màn chờ quyền | Chờ xác nhận |

| Đổi vai trò nhưng vẫn còn quyền hợp lệ | Kiểm tra lại phiên; module tải quyền mới | Chờ xác nhận |

| Đưa app ra nền, thu hồi quyền, quay lại | Hiện màn chờ quyền | Chờ xác nhận |

| Mất xác nhận máy chủ | Hiện kiểm tra/lỗi kết nối; không coi cache rỗng là đã thu hồi | Chờ xác nhận |

| Đổi tài khoản | Không khôi phục màn hình/quyền của UID trước | Chờ xác nhận |

| Xoay màn hình khi quyền không đổi | Giữ tab và nội dung nháp theo vòng đời hiện có | Chờ xác nhận |
