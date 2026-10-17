# F03 · Ghi nhận kiểm thử ngày 17/10/2026

## Phạm vi và cách ghi nhận

Chặng này bổ sung hồ sơ cá nhân, đổi mật khẩu và màn hình đọc tổ chức/vai trò trong SeniorTribeApp, nhánh feat/auth-profile.

Kết quả thao tác được ghi nhận từ ảnh máy ảo và xác nhận của thành viên phụ trách. Những bước chỉ được xác nhận bằng lời chưa có ảnh minh chứng riêng. Chưa kết luận toàn bộ F01–F03 hoặc luồng tích hợp nhóm hoàn tất.

## Build và unit test tại môi trường chuẩn bị

| Kiểm tra | Kết quả |
|---|---|
| Nền tích hợp | Main 8e8c78c + Auth email/Google + F03 và các bản sửa phản hồi lỗi |
| :app:assembleDebug | Đạt sau bản sửa cuối |
| :app:testDebugUnitTest | 36 test; 0 thất bại, 0 lỗi, 0 bỏ qua |
| Phạm vi unit test | 20 test Auth/membership hiện có và 16 test ProfilePolicy |
| Cấu hình | JDK 17; Gradle 8.2; AGP 8.2.0; compile/target 34; minSdk 24; Firebase BoM 32.8.0 |

Unit test kiểm tra logic đầu vào, vai trò và dữ liệu xác minh; không thay thế thao tác UI hoặc kiểm thử Firestore Rules đang triển khai. Thành viên đã Run ứng dụng trên máy ảo để thực hiện các bước dưới đây.

## Kết quả thao tác trên máy ảo

| Mã | Nội dung | Kết quả |
|---|---|---|
| HS01 | Tài khoản chờ quyền mở Hồ sơ của tôi | Đạt: hiển thị email và UID của tài khoản |
| HS02–HS03 | Sửa tên/khu vực có dấu, lưu, Back rồi mở lại | Đạt qua giao diện: nội dung đã lưu còn đúng dấu |
| HS04a | Họ tên trống hoặc một ký tự | Đạt: báo lỗi tại ô họ tên |
| HS04b | Khu vực vượt 120 ký tự | Đạt: báo lỗi tại ô khu vực |
| HS05 | Bản nháp chưa lưu, xoay ngang/dọc, Back rồi mở lại | Đạt: giữ bản nháp khi xoay; rời màn rồi mở lại hiện dữ liệu đã lưu |
| HS06 | Lưu khi máy ảo mất mạng, sau đó bật mạng và bấm lưu lại | Đạt: hiện lỗi kết nối, giữ nội dung; thử lại khi có mạng lưu được |
| HS07 | Tài khoản chỉ đăng nhập Google xem phần Bảo mật | Đạt: hiện hướng dẫn quản lý mật khẩu qua Google |
| MK01 | Nhập sai mật khẩu hiện tại khi đổi | Đạt sau sửa: chặn đổi và hiển thị lỗi rõ; mật khẩu đang dùng vẫn có hiệu lực |
| MK02 | Mật khẩu mới dưới 8 ký tự; xác nhận không khớp; trùng mật khẩu hiện tại | Đạt theo xác nhận: hiển thị lỗi tương ứng |
| MK03 | Đổi mật khẩu hợp lệ, đăng xuất, thử mật khẩu cũ và mới | Đạt: mật khẩu cũ bị từ chối; mật khẩu mới đăng nhập được |
| MK04 | Nhập đủ ba ô, xoay ngang/dọc trước khi gửi, rồi Hủy | Đạt theo xác nhận: giữ nội dung ba ô; đóng được hộp thoại |
| TC01 | Tài khoản chưa có membership | Đạt phần giao diện: báo chờ cấp quyền; vẫn dùng được hồ sơ cá nhân |

## Lỗi đã sửa trong chặng

- Giữ bản nháp hồ sơ: render lấy tên/khu vực hiện tại trong ViewModel, tránh bản chụp State cũ ghi đè sau khi tạo lại view.
- Phản hồi đổi mật khẩu: ẩn bàn phím khi gửi, dùng màu lỗi, cuộn tới thông báo và hiện lỗi sai mật khẩu hiện tại tại đúng ô. Thành viên đã thử lại và xác nhận thấy lỗi.
- Khi đổi thất bại do mật khẩu hiện tại sai, xóa ô hiện tại và giữ hai ô mật khẩu mới trong RAM để sửa lại. Xóa toàn bộ khi thành công, phiên thay đổi hoặc hộp thoại kết thúc.
- Nhãn chung đổi thành Đăng xuất và ← Quay lại. Back trong hộp thoại đóng hộp thoại; Back từ hồ sơ đang chờ quyền trở về màn chờ. Đăng xuất kết thúc phiên và mở màn đăng nhập.

## Hiện tượng nhập tiếng Việt

Gõ bằng bàn phím laptop dùng UniKey/EVKey bị mất chữ; dán văn bản vẫn hiển thị, lưu và mở lại đúng dấu. Chưa kết luận đã sửa tương thích bộ gõ máy tính với máy ảo.

## Các mục còn cần kiểm tra

| Nhóm | Công việc còn lại |
|---|---|
| TC02 | Nhiều tổ chức, nhiều vai trò chuẩn với dữ liệu quản trị cấp sẵn |
| TC03 | Trạng thái verified: đúng, sai, thiếu hoặc sai kiểu dữ liệu |
| TC04 | Thiếu hồ sơ tổ chức hoặc lỗi đọc; lỗi tổ chức không cản hồ sơ riêng |
| TC05 | Membership ngừng hoạt động; xác minh không tự mở quyền nghiệp vụ |
| PH01 | Đổi tài khoản và kiểm tra không còn hồ sơ/tổ chức của tài khoản trước |
| PH02 | Tài khoản đã có quyền dùng tab Cá nhân cùng nhu cầu, chat và lịch |
| Google | Hủy bộ chọn, chọn lại tài khoản và tình huống lỗi mạng riêng của Google |
| Firestore | Đối chiếu trực tiếp các trường bất biến; Rules chặn sửa chéo UID và sửa quyền |
| Dữ liệu cũ | Hồ sơ legacy với trường thừa hoặc kiểu thời gian cũ theo Rules chung |

Chỉ dùng dữ liệu tổ chức/quyền do người phụ trách CSDL/Rules cấp. Các mục chưa thử giữ nguyên trạng thái chưa xác nhận.