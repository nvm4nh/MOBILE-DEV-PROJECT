# 👵 SeniorTribe — Ứng Dụng Android Kết Nối & Hỗ Trợ Người Cao Tuổi

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%2F%20Java-orange.svg)]()
[![Backend](https://img.shields.io/badge/Backend-Firebase-yellow.svg)](https://firebase.google.com)
[![AI](https://img.shields.io/badge/AI-Google%20Gemini-blue.svg)](https://ai.google.dev)

---

## 📌 THÔNG TIN ĐỒ ÁN
* **Cơ sở đào tạo:** Trường Công nghệ và Thiết kế — Đại học Kinh tế TP. Hồ Chí Minh (UEH)
* **Học phần:** Phát triển Ứng dụng Mobile
* **Giảng viên hướng dẫn:** TS. Đặng Ngọc Hoàng Thành
* **Tên đề tài:** **SeniorTribe** — Phát triển ứng dụng di động kết nối người cao tuổi với cộng đồng tình nguyện viên

---

## 🎯 MỤC TIÊU & TÍNH CẤP THIẾT
Trong bối cảnh xã hội ngày càng bận rộn và già hóa dân số, nhiều người cao tuổi phải đối mặt với sự cô đơn và thiếu người trò chuyện, bầu bạn. 

**SeniorTribe** được xây dựng nhằm giải quyết bài toán này:
* Tạo cầu nối giữa **Người cao tuổi**, **Người thân trong gia đình** và **Tình nguyện viên trẻ tuổi** trong khu vực.
* Giúp người thân dễ dàng tạo các yêu cầu hỗ trợ (tâm sự, dạo bộ, đọc sách, hướng dẫn công nghệ) thay cho cha mẹ, ông bà.
* Ứng dụng thuật toán gợi ý thông minh dựa trên vị trí địa lý, thời gian rảnh và sở thích chung.
* Tích hợp **Chatbot AI** hỗ trợ người dùng soạn nội dung yêu cầu ngắn gọn, tình cảm và dễ hiểu nhất.

---

## ✨ CÁC CHỨC NĂNG CHÍNH CỦA ỨNG DỤNG

### 1. Phân quyền & Quản lý người dùng
* **Người cao tuổi (Elderly):** Giao diện cỡ chữ to, tương phản cao, xem lịch hẹn tâm sự và gọi trợ giúp nhanh.
* **Người thân (Family/Caregiver):** Tạo và quản lý hồ sơ của người già, đăng yêu cầu hỗ trợ thay, theo dõi tình nguyện viên.
* **Tình nguyện viên (Volunteer):** Tìm kiếm và nhận các yêu cầu trò chuyện, tích lũy điểm uy tín trong cộng đồng.

### 2. Trợ lý AI Soạn Thảo (AI Assistant - Gemini API)
* Người dùng chỉ cần nhập vài từ khóa ngắn gọn hoặc mong muốn sơ sài.
* Chatbot AI tự động tạo tiêu đề ấm áp, lời nhắn rõ ràng, tối ưu câu từ cho cộng đồng dễ tiếp nhận.

### 3. Ghép Đôi Thông Minh (Smart Matching)
* **Theo khoảng cách:** Tự động tính toán bán kính gần (1km - 10km) bằng GPS.
* **Theo thời gian:** Lọc theo khung giờ rảnh trùng khớp (sáng/chiều/tối các ngày trong tuần).
* **Theo sở thích:** Kết nối theo chủ đề chung (chơi cờ, thơ ca, nghe nhạc xưa, chăm sóc cây cảnh,...).

### 4. Nhắn Tin Thời Gian Thực (Realtime Chat)
* Trao đổi thông tin trực tiếp giữa người thân/người cao tuổi và tình nguyện viên trước khi gặp gỡ.

### 5. Đánh Giá & Điểm Uy Tín (Reviews & Rating)
* Chấm điểm sao và viết lời nhận xét sau khi hoàn thành buổi hỗ trợ, đảm bảo an toàn và văn minh cho cộng đồng.

---

## 🛠 CÔNG NGHỆ & KIẾN TRÚC HỆ THỐNG
* **Hệ điều hành:** Android (Target SDK 34, Min SDK 24).
* **Ngôn ngữ:** Kotlin / Java.
* **Kiến trúc:** MVVM (Model - View - ViewModel) tách biệt giao diện và logic dữ liệu.
* **Cơ sở dữ liệu & Xác thực:**
  * **Firebase Authentication:** Quản lý đăng nhập, phân quyền Role.
  * **Cloud Firestore:** Lưu trữ dữ liệu NoSQL Realtime (Users, Profiles, Requests, Messages).
  * **Firebase Storage:** Lưu trữ ảnh đại diện, hình ảnh hoạt động.
* **Trí tuệ nhân tạo:** Google Gemini API (Generative AI SDK for Android).
* **Định vị & Bản đồ:** Google Play Services Fused Location Provider.

---

## 👥 PHÂN CÔNG NHIỆM VỤ CỤ THỂ
*(Phân công trách nhiệm rõ ràng theo tiêu chí đánh giá của giảng viên, mỗi thành viên phụ trách một khối công việc độc lập)*

| STT | Thành Viên | MSSV | Vai Trò | Nhiệm Vụ Độc Lập Phụ Trách |
|:---:|:---|:---:|:---|:---|
| 1 | **[Họ và Tên 1]** *(Trưởng nhóm)* | [MSSV] | Leader & AI Module | - Khởi tạo project, quản lý Git/GitHub.<br>- Tích hợp Google Gemini API cho Chatbot AI.<br>- Lập trình màn hình Tạo yêu cầu & Chatbot hỗ trợ.<br>- Viết Báo cáo: **Chương 1 & Chương 5**. |
| 2 | **[Họ và Tên 2]** | [MSSV] | Database & Backend Lead | - Thiết lập Firebase (Auth, Firestore, Security Rules).<br>- Thiết kế lược đồ CSDL và các hàm Repository truy vấn.<br>- Lập trình màn hình Đăng ký/Đăng nhập & Quản lý hồ sơ người thân.<br>- Viết Báo cáo: **Chương 4 & Chương 6**. |
| 3 | **[Họ và Tên 3]** | [MSSV] | UI/UX & Matching Algorithm | - Thiết kế Wireframe/UI chuẩn cho người cao tuổi (chữ to, nút lớn).<br>- Viết thuật toán Matching (tính khoảng cách GPS theo Haversine + sở thích).<br>- Lập trình màn hình Trang chủ & Màn hình Tìm kiếm/Bộ lọc gợi ý.<br>- Viết Báo cáo: **Chương 2 & Chương 3**. |
| 4 | **[Họ và Tên 4]** | [MSSV] | Realtime Chat & QA Lead | - Lập trình tính năng Nhắn tin trò chuyện thời gian thực.<br>- Lập trình màn hình Đánh giá sao & Lịch sử hoạt động.<br>- Kiểm thử ứng dụng trên Emulator/Thiết bị thật, quay video demo.<br>- Tổng hợp, chỉnh sửa định dạng Báo cáo và làm Slide. |

---

## 📂 CẤU TRÚC MÃ NGUỒN (PROJECT STRUCTURE)
```text
app/src/main/java/com/seniortribe/app/
│
├── data/                      # Tầng dữ liệu (Models, Repositories, Firebase)
│   ├── model/                 # User, ElderProfile, HelpRequest, Message, Review
│   ├── repository/            # AuthRepository, RequestRepository, ChatRepository
│   └── remote/                # GeminiHelper, FirestoreService
│
├── ui/                        # Tầng hiển thị (Activities, Fragments, Adapters, ViewModels)
│   ├── auth/                  # Đăng nhập, Đăng ký, Chọn vai trò
│   ├── home/                  # Trang chủ theo vai trò (Người già / TNV)
│   ├── request/               # Tạo yêu cầu, Popup Chatbot AI, Chi tiết yêu cầu
│   ├── matching/              # Danh sách gợi ý, Lọc khoảng cách GPS & Sở thích
│   ├── chat/                  # Danh sách phòng chat, Màn hình nhắn tin Realtime
│   └── profile/               # Hồ sơ cá nhân, Hồ sơ người cao tuổi cần bảo trợ
│
└── utils/                     # Tiện ích (LocationCalculator, DateFormatter, Constants)
