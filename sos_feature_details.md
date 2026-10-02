# Chi tiết chức năng: Hệ thống Khẩn cấp (SOS Button)

## 1. Giới thiệu chung
Hệ thống Khẩn cấp (SOS Button) là một tính năng an toàn thiết yếu cần có cho ứng dụng SeniorTribe. Trong trường hợp người cao tuổi bị té ngã, mệt mỏi đột ngột hoặc gặp sự cố khẩn cấp, họ chỉ cần một thao tác đơn giản để gọi trợ giúp ngay lập tức. Tính năng này đóng vai trò như một chiếc "phao cứu sinh" kỹ thuật số.

## 2. Thiết kế Giao diện (UI/UX)
- **Vị trí:** Luôn nổi (Floating Action Button) hoặc chiếm một vị trí cố định, kích thước lớn ở ngay trung tâm hoặc góc dưới màn hình chính của tài khoản Người cao tuổi.
- **Màu sắc & Nhận diện:** Dùng màu đỏ đặc trưng, chữ "SOS" hoặc "CẤP CỨU" to, rõ ràng, độ tương phản cao để dễ nhìn thấy đối với người mắt kém.
- **Thao tác chống chạm nhầm:** Cần thiết kế thao tác **Nhấn giữ 3 giây** hoặc **Vuốt (Swipe to SOS)** để kích hoạt, tránh tình trạng vô tình chạm phải gây báo động giả.

## 3. Luồng hoạt động (Workflow)
1. **Kích hoạt:** Người cao tuổi thực hiện thao tác kích hoạt nút SOS.
2. **Thu thập dữ liệu:** Ứng dụng ngay lập tức lấy tọa độ GPS hiện tại của thiết bị (sử dụng Fused Location Provider đã có trong dự án).
3. **Phát cảnh báo đồng thời:**
   - **Gửi Push Notification:** Phát âm báo động và hiển thị thông báo khẩn cấp đến thiết bị của **Người thân** (Caregiver) đã được liên kết trong hệ thống.
   - **Gửi SMS tự động:** Gửi tin nhắn SMS tự động (chứa tọa độ GPS và link Google Maps) đến số điện thoại khẩn cấp đã cài đặt trước.
   - **Cảnh báo Cộng đồng (Tuỳ chọn):** Gửi thông báo ẩn danh tới các **Tình nguyện viên** có độ uy tín cao đang ở bán kính rất gần (dưới 500m) để họ có thể tới ứng cứu nhanh nhất.
4. **Hỗ trợ Liên lạc:** Tự động hiển thị màn hình gọi điện thoại nhanh để gọi trực tiếp cho người thân hoặc trung tâm cấp cứu (115).

## 4. Yêu cầu Kỹ thuật (Technical Requirements)
- **Permissions (Quyền Android):** Cần xin quyền `ACCESS_FINE_LOCATION` (lấy vị trí chính xác), `SEND_SMS` (gửi tin nhắn tự động), và `CALL_PHONE` (thực hiện cuộc gọi).
- **Backend & Realtime:** Sử dụng Firebase Cloud Messaging (FCM) với độ ưu tiên cao (High Priority) để đảm bảo thông báo đẩy đến ngay lập tức mà không bị hệ điều hành tắt ngầm.
- **Location Services:** Gọi Google Play Services API để lấy tọa độ tức thì thay vì cập nhật định kỳ để tiết kiệm pin bình thường.

## 5. Giá trị mang lại cho Đồ án
- Giải quyết trực tiếp "nỗi đau" (pain point) cốt lõi của người cao tuổi sống một mình.
- Thể hiện được khả năng ứng dụng Location API và Realtime Notification của nhóm phát triển.
- Giúp đồ án có thêm tính thực tế, nhân văn và có điểm nhấn mạnh mẽ khi báo cáo trước hội đồng.
