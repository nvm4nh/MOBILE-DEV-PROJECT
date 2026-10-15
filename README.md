# 👵 SeniorTribe — Ứng Dụng Android Kết Nối & Hỗ Trợ Người Cao Tuổi

[![Platform](https://img.shields.io/badge/Platform-Android%20Native-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java%20%7C%20XML-ED8B00.svg?style=flat&logo=openjdk&logoColor=white)]()
[![Backend](https://img.shields.io/badge/Backend-Firebase%20Firestore-FFCA28.svg?style=flat&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Auth](https://img.shields.io/badge/Auth-Google%20Sign--In-4285F4.svg?style=flat&logo=google&logoColor=white)]()
[![Database](https://img.shields.io/badge/Local%20DB-SQLite%20Offline-003B57.svg?style=flat&logo=sqlite&logoColor=white)]()
[![UI/UX](https://img.shields.io/badge/Design-Figma%20UI%2FUX-F24E1E.svg?style=flat&logo=figma&logoColor=white)](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1)

---

## 📌 THÔNG TIN HỌC PHẦN & ĐỀ TÀI
* **Cơ sở đào tạo:** Trường Công nghệ và Thiết kế — Đại học Kinh tế TP. Hồ Chí Minh (UEH)
* **Chuyên ngành:** Công nghệ Phần mềm — Khóa: K50
* **Học phần:** Phát triển Ứng dụng Mobile
* **Giảng viên hướng dẫn:** TS. Đặng Ngọc Hoàng Thành
* **Tên đề tài:** **SeniorTribe — Ứng dụng hỗ trợ và trò chuyện với người già neo đơn**  
  Phát triển mạng xã hội cộng đồng hỗ trợ các tổ chức xã hội ghi nhận nhu cầu, phân công điều phối, thăm hỏi và theo dõi tiến độ giúp đỡ người cao tuổi có hoàn cảnh khó khăn.

---

## 👥 THÀNH VIÊN NHÓM & PHÂN CÔNG ĐỘC LẬP
> ⚠️ **Tuân thủ quy định giảng viên:** Phân chia trách nhiệm độc lập, rõ ràng từng cá nhân; minh chứng dựa trên nhánh (branch), commit và pull request trên GitHub.

| STT | Họ và Tên | MSSV | Vai trò | Nhánh GitHub | Phụ trách Code chính | Phụ trách Báo cáo & Thuyết trình |
|:---:|:---|:---:|:---|:---:|:---|:---|
| 1 | **Ông Tố Quyên** | `31231024412` | **Trưởng nhóm** | `feat/chat-schedule`<br>`main` | • Quản trị repository, điều phối nhánh và merge PR tuần 1, 2.<br>• Chat phối hợp realtime 1-1 (F11).<br>• Đề xuất & thống nhất lịch hẹn (F12).<br>• Đối soát kết quả & lịch sử (F13, F15). | • Chương 5: Cấu trúc ứng dụng & Chat/Lịch (Mục 5.1 & 5.3).<br>• Quản lý tiến độ tổng thể, phân công nhiệm vụ.<br>• Phụ trách slide thuyết trình & điều phối demo 5p.<br>• Kiểm thử xoay màn hình mục chat. |
| 2 | **Khiếu Hoàng Nam Anh** | `31241022203` | **Thành viên** | `feat/requests`<br>`main` | • Cấu hình khung Android chung.<br>• Transaction tiếp nhận nhu cầu (F10).<br>• Hủy & trả lại nhu cầu (F14). | • Sơ đồ luồng công việc (Workflow Diagram - Mục 2.1).<br>• Chương 6: Quy trình thực thi.<br>• Tham gia thuyết trình & demo kỹ thuật. |
| 3 | **Lê Trọng Hiếu** | `31231025382` | **Thành viên** | `feat/requests` | • Form Ghi nhận nhu cầu (F06).<br>• Rà soát & duyệt nhu cầu (F07).<br>• Tìm kiếm & lọc nhu cầu (F08, F09).<br>• Firestore Security Rules. | • Sơ đồ phân rã chức năng (BFD - Mục 2.2).<br>• Chương 4: Cơ sở dữ liệu, Sơ đồ tương tác & Truy vấn.<br>• Phụ lục hướng dẫn cài đặt & ghép PDF. |
| 4 | **Nguyễn Văn Trường** | `31221025476` | **Thành viên** | `feat/auth-profile`<br>`feat/community-offline` | • Đăng nhập Email/Google & Phân quyền (F01–F03).<br>• Bảng tin & đăng bài tổ chức (F04, F05).<br>• Offline cache lưu bài viết bằng SQLite (F16, T05).<br>• Xử lý Responsive xoay màn hình (T04). | • Chương 1: Tổng quan đề tài & công cụ (Mục 1.1–1.4).<br>• Chương 3: Thiết kế giao diện (dọc/ngang).<br>• Chương 5: Kết nối DB & SQLite Offline (Mục 5.2 & 5.3).<br>• Kiểm tra đạo văn Turnitin (< 20%). |

---

## 📂 MỤC LỤC TÀI LIỆU DỰ ÁN & BÁO CÁO

Các tài liệu chính thức của đồ án được chuẩn hóa theo mẫu của giảng viên TS. Đặng Ngọc Hoàng Thành:

1. 📘 **[Kế hoạch & Đề cương Báo cáo chi tiết (theo Mau Do An - Mobile v2.docx)](KE_HOACH_BAO_CAO.md)**: Bản chuyển hướng chi tiết 7 chương báo cáo chuẩn barem (Tổng quan, Phân tích thiết kế, Giao diện, CSDL, Cài đặt ứng dụng, Quy trình thực thi, Hướng mở rộng và Phụ lục).
2. 📋 **[Checklist Công Việc & Deadline](Checklist.md)** *(hoặc [Trang tính1.md](Trang%20tính1.md))*: Danh mục 11 tác vụ MVP (M01–M10), 15 tác vụ mở rộng (E01–E15) và tiến độ báo cáo.
3. 🎯 **[Tổng Quan Đề Tài](Tổng%20quan.md)**: Bài toán nghiên cứu, đối tượng sử dụng, quy trình nghiệp vụ và cơ sở thực tiễn (Vầng Trăng Khuyết, HelpAge).
4. ⚙️ **[Phạm Vi Chức Năng](Phạm%20vi%20chức%20năng.md)**: Chi tiết 16 chức năng MVP (F01–F16), bảng luồng 7 trạng thái, kỹ thuật (T01–T06) và 9 quy tắc nghiệp vụ (R01–R09).
5. 👥 **[Phân Công & Quy Trình GitHub](Phân%20công.md)**: Timeline ngày 1–26, 6 mốc bàn giao và quy trình chuẩn 6 bước phối hợp nhóm trên GitHub.

---

## 🎯 BÀI TOÁN NGHIÊN CỨU & QUY TRÌNH NGHIỆP VỤ

### 1. Bài toán nghiên cứu
Kết nối nhu cầu hỗ trợ đã được đầu mối cộng đồng ghi nhận với tổ chức xã hội có khả năng thực hiện, đồng thời minh bạch người phụ trách, tiến độ và kết quả thực hiện.

### 2. Đối tượng sử dụng
* **Đầu mối cộng đồng:** Nhân viên, cộng tác viên hoặc tình nguyện viên được phân công ghi nhận và theo dõi nhu cầu tại địa bàn.
* **Tổ chức hỗ trợ:** Tổ chức xã hội, hội/đoàn thể hoặc nhóm thiện nguyện tiếp nhận và trực tiếp thực hiện hỗ trợ.
* **Người cao tuổi:** Người được hỗ trợ, có quyền đồng ý hoặc từ chối phương án hỗ trợ; không bắt buộc phải có tài khoản.

### 3. Quy trình luân chuyển trạng thái (6 bước khép kín)
```
[Ghi nhận] ➔ [Rà soát] ➔ [Tiếp nhận] ➔ [Thống nhất lịch] ➔ [Thực hiện] ➔ [Ghi nhận kết quả]
 (REVIEW)      (OPEN)     (ACCEPTED)      (SCHEDULED)      (DELIVERED)      (COMPLETED)
```
* **Bảo vệ quyền riêng tư:** Thông tin cá nhân nhạy cảm (tên đầy đủ, địa chỉ nhà, số điện thoại, giấy đồng ý) được lưu trữ riêng trong collection `needs_private` và chỉ phân quyền cho đầu mối ghi nhận cùng đại diện được giao tiếp nhận sau khi đã duyệt.

---

## ⚡ KỸ THUẬT CỐT LÕI (BÁM SÁT BAREM CHẤM ĐIỂM [*])

* **Firebase Services:**
  * **Authentication:** Xác thực tài khoản bằng Email/Mật khẩu và **Google Sign-In API**.
  * **Cloud Firestore:** Lưu trữ dữ liệu NoSQL realtime, phân quyền bằng Security Rules và xử lý chống nhận trùng bằng **Firestore Transaction**.
* **Responsive Layout:**
  * Hỗ trợ xoay ngang (`Landscape`) và dọc (`Portrait`) không vỡ giao diện.
  * Giữ nguyên dữ liệu form nhập dở khi xoay thiết bị thông qua `ViewModel` và `onSaveInstanceState`.
* **Cơ chế Caching & SQLite Offline:**
  * Tích hợp bộ nhớ đệm cục bộ với `SQLiteOpenHelper`.
  * Tự động lưu cache các bài viết cộng đồng đã tải; khi ngắt kết nối mạng (`Offline`), người dùng vẫn đọc được các bài đã xem trước đó.

---

## 🛠️ HƯỚNG DẪN CÀI ĐẶT & CHẠY DỰ ÁN

### Yêu cầu môi trường
* **Android Studio:** Phiên bản Flamingo (2022.2.1) hoặc mới hơn (Giraffe/Hedgehog).
* **JDK:** Java Development Kit 17.
* **Android SDK:** API Level 34 (Android 14) hoặc tối thiểu API Level 26 (Android 8.0).

### Các bước khởi chạy
1. **Clone repository:**
   ```bash
   git clone https://github.com/nvm4nh/MOBILE-DEV-PROJECT.git
   ```
2. **Mở project:** Khởi động Android Studio và chọn thư mục `SeniorTribeApp`.
3. **Cấu hình Firebase:** Đặt file `google-services.json` được cấp vào thư mục `SeniorTribeApp/app/`.
4. **Đồng bộ dependencies:** Nhấn **Sync Project with Gradle Files**.
5. **Chạy ứng dụng:** Chọn thiết bị ảo (Emulator API 30+) hoặc cắm điện thoại Android (đã bật USB Debugging), nhấn **Run 'app'** (`Shift + F10`).

---

## 🎨 THIẾT KẾ GIAO DIỆN & LIÊN KẾT LIÊN QUAN
* **Figma Prototype:** [Truy cập bản thiết kế UI/UX SeniorTribe trên Figma](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1)
* **Báo cáo PDF:** Đang được hoàn thiện theo mẫu `Mau Do An - Mobile v2.docx` tại nhánh `main`.