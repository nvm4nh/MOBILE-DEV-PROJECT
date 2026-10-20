# Tài khoản, phân quyền, hồ sơ · F01–F03

## Chặng F03 · 17/10/2026

Tiếp nối bản email + Google đã được chạy trên máy thành viên phụ trách. Tài khoản Google đã được xác nhận bằng provider Google và UID khớp giữa ứng dụng và Firebase Authentication. Chưa coi toàn bộ bảng kiểm thử Google, F02 hoặc luồng nhóm đã đạt.

### Màn hình và trách nhiệm

| File | Công việc |
|---|---|
| `fragments/ProfileFragment` + `layout/fragment_profile.xml` | Đọc và sửa hồ sơ thật, email/UID chỉ đọc, đăng xuất, phần tổ chức/vai trò |
| `viewmodels/AccountProfileViewModel` | Bản nháp trong RAM, trạng thái tải/lưu/lỗi, tách lỗi tổ chức khỏi lỗi hồ sơ |
| `dialogs/ChangePasswordDialog` + `viewmodels/ChangePasswordViewModel` | Xác thực lại trước khi đổi mật khẩu; giữ form khi xoay, xóa chuỗi nhạy cảm khi kết thúc |
| `auth/AuthRepository` | Đọc từ server, transaction cập nhật ba trường hồ sơ, gọi Firebase Auth cho đổi mật khẩu |
| `auth/ProfileInput`, `auth/ProfileErrors` | Kiểm tra đầu vào và thông báo lỗi |
| `models/AccountOrganization` | Projection chỉ đọc cho nhiều tổ chức, nhiều vai trò và trạng thái xác minh |
| `models/ProfileUpdateResult` | Phân biệt lưu Firestore thành công với lỗi đồng bộ tên hiển thị Firebase Auth |
| `models/User` | Giữ API chung; đọc riêng `area` nếu có, tương thích dữ liệu chỉ có `district` |
| `activities/SessionGateActivity` | Mở cùng ProfileFragment bằng back stack; quay lại màn chờ bằng Back |

Các lớp mới theo package nhóm (`viewmodels`, `dialogs`, `fragments`, `models`); lớp Auth đang có vẫn giữ trong `auth`. Không tạo project hoặc Activity hồ sơ thứ hai.

### Hợp đồng dữ liệu đang dùng

| Đường dẫn | Đọc | Ghi từ F03 |
|---|---|---|
| `users/{uid}` | Chỉ hồ sơ đúng UID đang đăng nhập | `displayName`, `area`, `updatedAt` bằng server timestamp |
| `memberships/{uid}/organizations/{orgId}` | `orgName`, `active`, `roles` của chính tài khoản | Không |
| `organizations/{orgId}` | `name`, `area`, `verified`; `address`, `phone` nếu quản trị có cấp | Không |
| Firebase Auth của tài khoản hiện tại | Provider mật khẩu / Google | Tên hiển thị sau khi lưu hồ sơ; mật khẩu sau khi reauthenticate thành công |

Không serialize toàn bộ `User` để lưu. Không thay đổi `uid`, `email`, `createdAt`, `district`, `role`, `orgId`, `orgName`, `phone`. `area` là trường hồ sơ đã có ở chặng trước; không thêm trường dữ liệu Firestore mới. Nếu hồ sơ cũ có đồng thời `area` và `district`, F03 sửa `area`, giữ `district` cho dữ liệu nghiệp vụ cũ.

Transaction yêu cầu kết nối máy chủ, không để lại một lệnh sửa hồ sơ offline rồi tự báo thành công. Nếu Firestore đã commit nhưng đồng bộ tên trong Firebase Auth lỗi, UI nói hồ sơ đã lưu và hướng dẫn lưu lại để thử đồng bộ, không báo sai là Firestore thất bại.

Mật khẩu không trim; tối thiểu 8 ký tự, xác nhận khớp, khác mật khẩu hiện tại. Chỉ provider `password` được vào form này. Người dùng chỉ dùng Google quản lý mật khẩu trên Google, không tự được gắn provider mới bởi F03.

### Phân quyền và xác minh

- Chỉ ba mã chuẩn: `RECORDER`, `REVIEWER`, `REPRESENTATIVE`. Không tự quy đổi role cũ hoặc hiển thị role lạ như quyền hợp lệ.
- Hiển thị mọi tổ chức của tài khoản, sắp theo mã tổ chức. Membership ngừng hoạt động vẫn được mô tả rõ là không hoạt động.
- Dấu xác minh chỉ đọc từ `organizations/{orgId}.verified` là Boolean. `"true"`, dữ liệu thiếu hoặc không đọc được không được coi là đã xác minh.
- Dấu xác minh không tự cấp quyền, không kích hoạt membership đã tắt. Không có upload hồ sơ xác minh hay tự duyệt tổ chức.
- Dữ liệu tổ chức thiếu/lỗi không làm mất form hồ sơ cá nhân.
- Giao diện không thay thế Firestore Rules. Server vẫn phải chặn ghi chéo UID và mọi ghi membership/organization từ app.

### Ghép với main mới

Đã đối chiếu `main` commit `8e8c78c`: schema membership và `organizations` của nhóm phù hợp. `firebase/firestore.rules` trên nhánh này đã có phần owner-only `users`, đọc membership của chính mình và đọc hồ sơ tổ chức khi đăng nhập.

Bộ thay đổi F03 không chứa Manifest, Gradle, JSON cấu hình, Rules hoặc file nhu cầu/chat/lịch. Giữ cấu hình Google mới trên máy, không chép lại JSON cũ từ main. Không thay Rules chung bằng file Rules của project thử nghiệm cũ.

Giới hạn tích hợp còn phải kiểm thử:

- Shared `MembershipRepository` của module nhu cầu hiện chọn tổ chức đầu tiên theo orgId. F03 chỉ hiển thị đầy đủ, chưa thêm luồng chuyển tổ chức làm việc.
- Các module dùng tên trong dữ liệu đã tạo hoặc cache riêng sẽ không tự đổi tên hồi tố sau khi sửa hồ sơ. Khi ghép nhóm cần kiểm tra tải lại dữ liệu tên và quyền của phiên.
- Rules `validProfile().hasOnly(...)` của main vẫn chỉ nhận hồ sơ sáu trường. Hồ sơ legacy có thêm trường/dạng thời gian cũ có thể đọc được nhưng bị từ chối sửa. Không tự migrate hoặc nới Rules khi chưa thống nhất với người phụ trách CSDL/Rules.
- Chưa kiểm thử thực tế tài khoản được cấp role, nhiều tổ chức hoặc luồng nhu cầu → chat → lịch trên máy người dùng.
- Việc dọn project đăng ký thử nghiệm cũ, ghi nhận commit và xử lý PR do người phụ trách thao tác riêng trên `feat/auth-profile`.

### Nguồn API

- [Firebase Authentication · Manage Users](https://firebase.google.com/docs/auth/android/manage-users): `reauthenticate`, `updatePassword`, đọc provider và cập nhật tên.
- [Cloud Firestore · Add data](https://firebase.google.com/docs/firestore/manage-data/add-data): cập nhật trường cụ thể và server timestamp.
- [Cloud Firestore · Transactions](https://firebase.google.com/docs/firestore/manage-data/transactions): đọc/ghi transaction yêu cầu kết nối, không tự xếp hàng như một lần ghi offline thông thường.

Kết quả build, các phép thử thực tế và các mục còn chờ nằm trong [F03_KIEM_THU.md](auth/F03_KIEM_THU.md). Kết quả unit test không thay thế kiểm thử UI, Firebase Rules và tích hợp nhóm.
