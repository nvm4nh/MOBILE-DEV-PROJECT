# 👵 SeniorTribe — Ứng Dụng Android Kết Nối & Hỗ Trợ Người Cao Tuổi

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java%20%2F%20XML-orange.svg)]()
[![Backend](https://img.shields.io/badge/Backend-Firebase%20Firestore-yellow.svg)](https://firebase.google.com)
[![Figma](https://img.shields.io/badge/UI-Figma%20Design-purple.svg)](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1)

---

## 📌 THÔNG TIN HỌC PHẦN & ĐỀ TÀI
* **Cơ sở đào tạo:** Trường Công nghệ và Thiết kế — Đại học Kinh tế TP. Hồ Chí Minh (UEH)
* **Học phần:** Phát triển Ứng dụng Mobile
* **Giảng viên hướng dẫn:** TS. Đặng Ngọc Hoàng Thành
* **Tên đề tài:** **SeniorTribe: Ứng dụng hỗ trợ và trò chuyện với người già neo đơn**  
  Là đề tài phát triển ứng dụng mạng xã hội cộng đồng, hỗ trợ các tổ chức ghi nhận nhu cầu, phối hợp thăm hỏi và theo dõi việc giúp đỡ người cao tuổi.

---

## 📂 MỤC LỤC TÀI LIỆU DỰ ÁN (Trích xuất từ [MOBILE] CHECKLIST)

Các tài liệu chi tiết được tổ chức tương ứng với các sheet trong file kế hoạch **[MOBILE] CHECKLIST.xlsx**:

1. 📋 **[Checklist & Tiến độ (Sheet: Trang tính1)](Checklist.md)** *(hoặc [Trang tính1.md](Trang%20tính1.md))*: Danh mục chi tiết các đầu việc cơ bản (M01–M10), mở rộng (E01–E15), thiết kế Figma và phần báo cáo.
2. 🎯 **[Tổng quan đề tài (Sheet: Tổng quan)](Tổng%20quan.md)**: Bài toán nghiên cứu, đối tượng sử dụng, quy trình nghiệp vụ và tính cấp thiết thực tiễn.
3. ⚙️ **[Phạm vi chức năng (Sheet: Phạm vi chức năng)](Phạm%20vi%20chức%20năng.md)**: Chi tiết 16 chức năng MVP (F01–F16), quy trình chuyển trạng thái nhu cầu, chức năng mở rộng (E01–E09), kiến trúc kỹ thuật (T01–T06) và 9 quy tắc nghiệp vụ (R01–R09).
4. 👥 **[Kế hoạch & Phân công (Sheet: Phân công)](Phân%20công.md)**: Phân công trách nhiệm 4 thành viên (Nam Anh, Hiếu, Quyên, Trường), Timeline ngày 1–26, bảng mốc bàn giao và quy trình phối hợp nhánh GitHub.

---

## 🎯 TỔNG QUAN BÀI TOÁN & GIẢI PHÁP

### 1. Bài toán nghiên cứu
Tập trung vào việc kết nối nhu cầu đã được đầu mối cộng đồng ghi nhận với tổ chức có khả năng hỗ trợ, đồng thời xác định người phụ trách và kết quả thực hiện.

### 2. Đối tượng sử dụng
* **Đầu mối cộng đồng:** Nhân viên, cộng tác viên hoặc tình nguyện viên được tổ chức phân công ghi nhận và theo dõi nhu cầu.
* **Tổ chức hỗ trợ:** Tổ chức xã hội, hội/đoàn thể hoặc nhóm thiện nguyện tiếp nhận và thực hiện hỗ trợ.
* **Người cao tuổi:** Người được hỗ trợ, có quyền đồng ý hoặc từ chối phương án; không bắt buộc có tài khoản.

### 3. Phạm vi và quy trình giải quyết
Ứng dụng tập trung vào trò chuyện, thăm hỏi và hỗ trợ sinh hoạt, theo quy trình luân chuyển khép kín:

\textbf{Ghi nhận} \longrightarrow \textbf{Rà soát} \longrightarrow \textbf{Tiếp nhận} \longrightarrow \textbf{Thống nhất lịch} \longrightarrow \textbf{Thực hiện} \longrightarrow \textbf{Ghi nhận kết quả}

SeniorTribe cung cấp bảng tin tổ chức, quản lý nhu cầu, trò chuyện phối hợp và theo dõi lịch sử. Thông tin cá nhân được giới hạn theo nhiệm vụ của từng người nhằm bảo mật và an toàn dữ liệu.

### 4. Cơ sở thực tiễn & Giá trị ứng dụng
* **Thực tiễn:**
  - **DNXH Vầng Trăng Khuyết:** Tổng hợp, xác minh và giữ liên lạc với các hoàn cảnh cần giúp còn thủ công và thiếu sót.
  - **Dự án VIE 090 của HelpAge Việt Nam:** Tổ chức quản lý trường hợp, điều phối và giám sát hỗ trợ người cao tuổi có nhu cầu mở rộng quy mô.
* **Giá trị mang lại:**
  SeniorTribe hướng đến tập trung thông tin nhu cầu, làm rõ trách nhiệm, theo dõi tiến độ và hạn chế chia sẻ dữ liệu không cần thiết.

---

## 🎨 THIẾT KẾ GIAO DIỆN
* **Figma UI/UX:** [Truy cập bản thiết kế giao diện SeniorTribe](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1)