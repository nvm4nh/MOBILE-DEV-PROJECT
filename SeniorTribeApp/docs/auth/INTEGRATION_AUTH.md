# Auth & Profile: bản tích hợp email và Google

## Điểm ghép vào khung chung

| Thành phần | Thay đổi |
|---|---|
| `SeniorTribeApplication` | Khởi tạo Firebase và AuthRepository dùng chung; Firestore không lưu cache riêng tư xuống đĩa; dấu đã qua kiểm tra quyền chỉ ở RAM |
| `models.User` | Giữ API `role`, `orgId`, `orgName`, `phone`, `district`, `createdAt`; thêm cách đọc profile cũ mà không serialize đè dữ liệu |
| `activities.RegisterActivity` | Đăng ký thật, lưu hồ sơ, thử lại hồ sơ nếu lỗi; hoàn tất thì chuyển qua bước kiểm tra phiên/quyền |
| `activities.LoginActivity` | Đăng nhập email thật, không cắt khoảng trắng của mật khẩu, không có nhánh đăng nhập demo |
| `auth.GoogleCredentialSession` | Bộ chọn Google dùng Credential Manager; dọn trạng thái provider khi đăng xuất |
| `activities.ForgotPasswordActivity` | Gửi yêu cầu Firebase thật; không hiển thị thành công khi chưa gửi được vì lỗi cấu hình/kết nối |
| `activities.SessionGateActivity` | Làm mới token, xác nhận hồ sơ và đọc membership từ server; tách chờ quyền khỏi lỗi; hỗ trợ copy UID, thử lại, đăng xuất |
| `activities.SplashActivity` | Phiên Auth có sẵn đi qua SessionGate trước Main |
| `activities.MainActivity` | Thêm điều kiện vào Main và theo dõi thay đổi tài khoản; giữ các tab/fragments của nhóm |

## Tương thích dữ liệu

- Profile mới vẫn dùng schema đã chạy ở đợt 1: `uid`, `displayName`, `email`, `area`, `createdAt`, `updatedAt`.
- Hai thời điểm được ghi bằng `FieldValue.serverTimestamp()`.
- `User.fromDocument` đọc cả `area` của đợt 1 và `district` của model chung; đọc được `createdAt` kiểu Firestore Timestamp hoặc số milliseconds.
- Nếu document đã tồn tại, `ensureProfile` chỉ đọc và kiểm tra. Không gọi `set(user)` lên profile cũ; các trường `role`, `orgId`, `phone`, `district` và trường khác không bị xóa.
- Các trường tổ chức/quyền chưa có trên profile mới không được client tự điền để cấp quyền. Trường `User.role` cũ không được dùng thay membership để mở Main.
- Khi hoàn thiện F03, cần chốt trường khu vực ghi xuống DB là `area` hay `district` cùng người phụ trách schema/Rules; reader tương thích hiện tại giữ được dữ liệu đã có.

## Membership đang dùng

Đường dẫn đã dùng từ nền tảng đợt 1:

`memberships/{uid}/organizations/{organizationId}`

Một membership được coi đang hoạt động khi `active` là boolean `true` và `roles` là danh sách không rỗng gồm các mã `RECORDER`, `REVIEWER`, `REPRESENTATIVE`. Một tài khoản có thể có nhiều vai trò/nhiều document tổ chức. Dữ liệu hoạt động nhưng sai kiểu hoặc có mã vai trò lạ được báo lỗi.

Các chuỗi `Reporter`, `Coordinator`, `Assignee` còn trong `Constants` của khung nhóm cần được đối chiếu với các mã quyền canonical khi ghép các thao tác nghiệp vụ. Bộ tích hợp này không sửa hằng số của module khác hay tự chuyển các chuỗi đó thành quyền đã được cấp.

## Rules và phần còn phải phối hợp

- Bộ file không tự triển khai hay thay thế Rules đang chạy trên Firebase.
- Đăng ký chặng này giữ schema sáu trường nên dùng được với Rules hồ sơ đã kiểm thử của đợt 1.
- Cổng phiên/quyền là bước điều hướng trên ứng dụng. Quyền dữ liệu của từng nghiệp vụ vẫn phải được bảo vệ bằng Rules chung và được kiểm thử cùng module tương ứng.
- Trước PR sẵn sàng merge, đối chiếu schema/Rules chung: các trường User, dữ liệu tổ chức, mã role và quyền đọc/ghi cho các collection nghiệp vụ.
- Không đưa bản Rules riêng có catch-all deny vào thay toàn bộ Rules của nhóm; cũng không mở toàn bộ đọc/ghi hoặc cho phép client tự sửa role/org để vượt qua lỗi tích hợp.
- Chưa có màn tự tạo/xác minh tổ chức hoặc tự gán membership trong phạm vi chặng này.

## Chặng kế tiếp của F01–F03

1. Kiểm thử Google Sign-In đã ghép sau khi cấu hình provider và SHA-1 của máy/build; xem `../tai-khoan-F01-F02-F03.md`.
2. Xem/sửa họ tên, khu vực; đổi mật khẩu có xác thực lại.
3. Hiển thị tổ chức, huy hiệu xác minh và các vai trò theo tổ chức từ dữ liệu được cấp.
4. Hoàn tất Rules chung, kiểm thử tích hợp, bỏ project cũ rồi hướng dẫn anh tự push cập nhật PR.
