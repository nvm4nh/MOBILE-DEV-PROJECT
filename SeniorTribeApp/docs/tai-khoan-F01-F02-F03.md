# Tài khoản, phân quyền, hồ sơ — F01–F03

## Phạm vi bản cập nhật

Chặng này ghép đăng nhập Google vào bản email đã chạy trên `SeniorTribeApp`, dựa trên khung nhóm `edea3c3` và checkpoint đăng ký `b2b409d`. Đây chưa phải bản hoàn thành toàn bộ F01–F03.

| Thành phần | Vai trò |
|---|---|
| `activities.LoginActivity` | Form email, nút Google, bộ chọn tài khoản và xử lý hủy/đổi Activity |
| `auth.LoginViewModel` | Trạng thái email/Google, chặn gửi trùng, bỏ callback đã hết hiệu lực |
| `auth.AuthRepository` | Xác thực Firebase, hồ sơ và membership; không ghi role/org từ client |
| `auth.GoogleCredentialSession` | Credential Manager dùng chung và dọn trạng thái provider lúc đăng xuất |
| `SeniorTribeApplication` | Cung cấp repository/session; dọn quyền RAM khi đổi tài khoản và dọn provider khi đăng xuất từ module khác |
| `activities.SessionGateActivity` | Nạp hồ sơ và kiểm tra membership trước khi mở nghiệp vụ |
| `models.User` | API model chung, đọc được dữ liệu hồ sơ cũ và giữ các trường nhóm |

Tên lớp dùng tiếng Anh theo chức năng như mẫu các module khác. Bộ cập nhật giữ các lớp xử lý Auth trong package `auth` đang chạy từ chặng trước; không di chuyển package của module khác. Activity ở `activities`, model người dùng dùng `models.User`.

## Dữ liệu và quyền

- Hồ sơ mới: `uid`, `displayName`, `email`, `area`, `createdAt`, `updatedAt`; các thời điểm dùng server timestamp.
- Hồ sơ đã có chỉ được đọc trong `ensureProfile`, không serialize `User` rồi ghi đè các trường nghiệp vụ.
- Reader đọc cả `district` và `area`, đồng thời tương thích `createdAt` dạng Timestamp hoặc milliseconds.
- Membership: `memberships/{uid}/organizations/{organizationId}`, đọc từ server; yêu cầu `active=true` và danh sách role hợp lệ.
- Mã role đang kiểm tra: `RECORDER`, `REVIEWER`, `REPRESENTATIVE`. `Reporter`, `Coordinator`, `Assignee` trong Constants của khung nhóm còn cần mapping chính thức.
- Google Authentication chỉ xác minh tài khoản; quyền tổ chức phải được cấp qua dữ liệu quản trị đáng tin cậy và được Rules bảo vệ.
- Bộ này không triển khai hoặc thay thế `firestore.rules` chung. Chưa chốt bước vào Main cùng nhóm, chưa kiểm thử vai trò đủ quyền trên các module nghiệp vụ.

## Cấu hình và phiên

- Google Services plugin đọc Web client từ file `app/google-services.json` mới của từng project Firebase; không hard-code client ID vào Java.
- Thêm Credentials/Play Services Auth 1.3.0 và Google ID 1.1.1; các cấu hình nền của nhóm giữ nguyên.
- Credential picker dùng context của Activity, có cancellation và bỏ callback cũ khi Activity bị hủy.
- Token không lưu vào field của ViewModel, Bundle hay DB. Mật khẩu không cắt khoảng trắng, không lưu vào instance state.
- Đăng xuất Firebase không phụ thuộc vào thành công của cleanup provider; lần Google tiếp theo là thao tác chọn tài khoản rõ ràng của người dùng.
- Cổng quyền trên app không thay thế Rules; quyền dữ liệu nghiệp vụ vẫn cần kiểm tra phía Firebase.

## Kiểm tra bản cập nhật

Đã kiểm tra ngày 17/10/2026 trên cây nguồn khung nhóm `edea3c3` ghép bộ email và các file Google trong bản bàn giao này.

| Kiểm tra | Kết quả |
|---|---|
| Gradle `:app:assembleDebug` | Đạt; APK hợp lệ, package `com.seniortribe.app`, minSdk 24 và targetSdk 34 |
| Gradle `:app:testDebugUnitTest` | 20 test, 0 failures, 0 errors, 0 skipped |
| Java/API Android của màn Login Google | Biên dịch được cùng toàn bộ app; có các phương thức xử lý Google trong class đã build |
| Java/XML/package/tham chiếu resource/trạng thái form | Đạt kiểm tra tĩnh |
| Đối chiếu nguồn bàn giao với cây đã build | Các file app trong bộ bàn giao trùng nội dung cây build |
| Chọn tài khoản và xác thực Google thực tế | Chưa chạy; cần cấu hình mới và thiết bị của anh |

Unit test hiện có kiểm tra đầu vào và điều kiện membership; không phải kiểm thử giao diện hoặc máy chủ Google. Bản build tại môi trường chuẩn bị dùng cấu hình Firebase của checkpoint `b2b409d` để biên dịch; file OAuth mới trên máy anh chưa được đọc/kiểm chứng ở đây. Bộ bàn giao không kèm cấu hình Firebase hay APK/debug keystore của môi trường kiểm tra.

Google UI và xác thực thực tế cần kiểm thử bằng cấu hình mới trên máy anh theo bảng GG01–GG10 trong `HUONG_DAN.md`.

## Chặng kế tiếp

F03, schema/Rules chung, dữ liệu tổ chức/vai trò, kiểm thử luồng tích hợp, dọn project cũ và cập nhật Git/PR bằng thao tác của anh.
