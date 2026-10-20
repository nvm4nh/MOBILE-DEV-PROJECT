# F01–F03 · Điều chỉnh giao diện theo feedback

Bản sửa dựa trên `feat/auth-profile` tại `699239bb1d91b227c0ae1576ac3c65a6ff976a43`, kiểm tra ngày 20/10/2026.

## Thay đổi

| Màn hình | Kết quả trong mã |
| --- | --- |
| Đăng nhập | Thêm biểu tượng ứng dụng đang dùng ở Splash/Manifest; đưa “Quên mật khẩu?” ngay dưới ô mật khẩu; nút viền trắng, icon Google màu và nhãn “Tiếp tục với Google”. |
| Đăng ký | Thêm thanh trên cùng với nút quay lại; thêm “hoặc” và nút Google có thao tác xác thực thật. |
| Quên mật khẩu | Thêm thanh trên cùng với nút quay lại. Giữ thông báo tiếp nhận yêu cầu đặt lại mật khẩu. |
| Chờ cấp quyền | Hiện icon khóa ở trạng thái chờ quyền. Không dùng icon này cho lỗi mạng hay lúc đang kiểm tra. |
| Tài khoản | Tiêu đề “Tài khoản”; đầu trang có avatar mặc định, tên và email lấy từ hồ sơ; hai dòng họ tên/khu vực có bút chì mở hộp sửa riêng. |

Figma từ chối truy cập trong phiên kiểm tra này. Màu, font và icon lấy từ tài nguyên hiện có của app. Chưa xác nhận kích thước, khoảng cách và logo trùng chính xác với Figma. Avatar mặc định không bổ sung tính năng tải ảnh lên.

## Cách nối chức năng

- `GoogleSignInLauncher` tách bộ chọn Credential Manager hiện có ra khỏi `LoginActivity`, dùng chung cho đăng nhập và đăng ký. `LoginViewModel` tiếp tục xử lý Firebase, lỗi/hủy bộ chọn và yêu cầu đang chạy. Khi xoay màn hình, bộ chọn cũ được hủy; nếu đã sang bước Firebase thì chờ kết quả đang chạy.
- Google và email đều đi qua `SessionGateActivity`; luồng cấp quyền, membership và theo dõi phiên vẫn được dùng như trước.
- `EditProfileDialog` dùng `AccountProfileViewModel` của màn Tài khoản. Bản nháp và dòng đang sửa được giữ trong RAM qua lần tạo lại view. Hủy sẽ bỏ bản nháp; lỗi lưu giữ hộp thoại để thử lại; lưu thành công cập nhật tên/email ở phần đầu trang.
- `AuthRepository.updateOwnProfile` nhận dòng cần sửa và dùng transaction để chỉ cập nhật `displayName` **hoặc** `area`, kèm `updatedAt`. Giá trị của dòng còn lại lấy từ bản đọc trong transaction. Tên trong Firebase Auth được đồng bộ từ hồ sơ sau khi lưu.
- Email, UID, vai trò và tổ chức chỉ được xem. Không thêm schema, Firebase Rules, thư viện hoặc dịch vụ tính phí.
- Các trang có nội dung dài dùng cuộn; thanh quay lại của Đăng ký/Quên mật khẩu nằm ngoài vùng cuộn. Hộp sửa dùng chiều cao theo nội dung và điều chỉnh khi bàn phím mở.

## Kết quả kiểm tra tự động

| Kiểm tra ngày 20/10/2026 | Kết quả |
| --- | --- |
| `:app:assembleDebug`, build sạch | Đạt; tạo được APK debug từ mã cuối cùng. |
| `:app:testDebugUnitTest` | 70 test đạt, 0 lỗi, 0 bỏ qua: AuthPolicy 20, ProfilePolicy 16, LiveAccessMonitor 34. Đây là test chính sách và theo dõi phiên, không phải kiểm thử giao diện hay Firebase thật. |
| `:app:lintDebug` | Chưa đạt toàn repo: 3 lỗi và 149 cảnh báo. Checkout gốc `699239b` cũng có đúng 3 lỗi tương ứng, kèm 148 cảnh báo. Bản sửa không thêm lỗi mức Error/Fatal. |
| Bản vá | Áp dụng được trên checkout sạch `699239b`; nội dung đủ 20 file khớp với bản chuẩn bị gói. |

Ba lỗi lint cũ: `FullBackupContent` trong `res/xml/backup_rules.xml` và `res/xml/data_extraction_rules.xml`; `UseAppTint` trong `res/layout/activity_splash.xml`. Các file đó giữ nguyên. Lệnh gộp có lint trả mã lỗi vì ba chẩn đoán này, dù các tác vụ assemble và unit test đã đạt.

## Kiểm thử cần thực hiện trên máy thật hoặc máy ảo

Các mục dưới đây là hướng dẫn kiểm thử, chưa phải kết quả chạy thiết bị của bản sửa.

| Tình huống | Kết quả cần đạt |
| --- | --- |
| Đăng nhập, chiều dọc/ngang | Thấy logo; link Quên mật khẩu nằm dưới ô mật khẩu; hai nút đăng nhập không bị che, cuộn được. |
| Đăng ký / Quên mật khẩu, chạm mũi tên | Quay lại đúng màn trước; Đăng ký đang gửi yêu cầu sẽ báo chờ. |
| Google từ Đăng ký, tài khoản mới hoặc đã có | Mở bộ chọn; xác thực thành công đi qua cổng kiểm tra tài khoản/quyền. Không tạo tài khoản email song song. |
| Hủy Google, chọn lại; Google lỗi rồi chuyển sang email | Không treo trạng thái chờ; thao tác lại được; lỗi Google cũ không che thông báo của email. |
| Xoay lúc bộ chọn Google mở / Firebase đang xử lý | Bộ chọn cũ bị hủy hoặc chờ đúng tác vụ Firebase; không tự mở thêm một yêu cầu. |
| Tài khoản chưa có membership hoạt động | Thấy icon khóa và thông tin chờ cấp quyền; sao chép UID và mở Tài khoản được. |
| Tài khoản: sửa tên / khu vực, lưu, mở lại | Chỉ dòng đã chọn thay đổi; tiêu đề và avatar/tên/email hiển thị đúng; tên đầu trang cập nhật sau lưu. |
| Hủy hộp sửa bằng nút Hủy hoặc Back | Không ghi Firestore; mở lại thấy giá trị đã lưu. |
| Xoay khi đang nhập tên/khu vực hoặc đang lưu | Giữ đúng dòng và bản nháp trong cùng phiên; không gửi lại yêu cầu lưu. |
| Tên quá ngắn, khu vực quá dài; mất mạng khi lưu | Hiện lỗi trong hộp sửa; không đóng hộp hay báo đã lưu. Khôi phục mạng rồi thử lại được. |
| Google-only / email-password, đăng xuất và đổi quyền | Giữ đúng cách quản lý mật khẩu và luồng thu hồi quyền hiện có. |

## Tích hợp nhánh

`main` tại thời điểm kiểm tra là `c3d84448410a95bd56f1cb482db55e0f2d7a5294`. Ghép thử hai nhánh bằng `git merge-tree` không xung đột. Đây là kiểm tra tích hợp mã, không thay thế kiểm thử ứng dụng.

PR #5 trước đây đã đóng và chưa merge. Sau khi áp dụng, chạy kiểm thử thiết bị và push bản sửa lên `feat/auth-profile`, mở PR mới vào `main` với mô tả bao quát F01–F03. Cần kiểm tra lại diff và khả năng merge khi `main` có commit mới.
