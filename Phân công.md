# 👥 Kế Hoạch & Phân Công Nhiệm Vụ — SeniorTribe

---

## 1. PHÂN CÔNG CHÍNH

| STT | Thành viên | MSSV | Chức năng phụ trách | Phần báo cáo | Nhánh GitHub | Hạn chức năng chính | Hạn báo cáo |
|:---:|:---|:---:|:---|:---|:---:|:---:|:---:|
| 1 | **Ông Tố Quyên** *(Leader)* | `31231024412` | **Chat, lịch, kết quả, lịch sử:** F11–F13, F15 | Chương 5. Cấu trúc ứng dụng & Chat/Lịch (Mục 5.1 & 5.3). Quản lý tiến độ tổng thể, phân công nhiệm vụ, slide và điều phối demo 5p. | `feat/chat-schedule`<br>`main` | | Ngày 16 |
| 2 | **Khiếu Hoàng Nam Anh** | `31241022203` | **Nhu cầu, duyệt, tìm lọc, tiếp nhận, hủy/trả lại:** F06–F10, F14 | Sơ đồ luồng công việc (Workflow Diagram - Mục 2.1), Chương 6. Quy trình thực thi, Phụ lục và định dạng PDF. | `feat/requests`<br>`main` | | 14/10 |
| 3 | **Lê Trọng Hiếu** | `31231025382` | **Ghi nhận, duyệt, cơ sở dữ liệu:** F06–F09 | Sơ đồ phân rã chức năng (BFD - Mục 2.2), Chương 4. Cơ sở dữ liệu và truy vấn Firestore. | `feat/requests` | | 14/10 |
| | | | **VẼ FIGMA [xong MVP]** | [UI Figma Giao diện chức năng](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1) | | | |
| 4 | **Nguyễn Văn Trường** | `31221025476` | **Tài khoản, phân quyền, hồ sơ, bảng tin, offline:** F01–F05, F16 | Chương 1. Tổng quan, Chương 3. Giao diện (dọc/ngang), Chương 5.2 Kết nối DB & 5.3 Offline SQLite. | `feat/auth-profile`<br>`feat/community-offline` | 14/10 | Ngày 16 |

> 📌 **Ghi chú quan trọng:**  
> - **Ngày 11–12:** Sửa lỗi và kiểm tra xoay màn hình.  
> - **Mục 5.3:** Xây dựng nội dung các trang.  
> - **SQLite:** Là phần khuyến khích hoàn thiện.

---

## 2. TIMELINE PHÁT TRIỂN

| Giai đoạn | Chi tiết công việc phân hệ | Kết quả cần đạt |
|:---:|:---|:---|
| **Ngày 1–2** | • Dựng màn hình chat và lịch.<br>• Tạo project chạy được, cấu hình Firebase Auth và đưa khung lên main.<br>• Dựng bảng tin. Tạo Issues và quy tắc nhánh.<br>• Chốt schema, model và cấu hình Firestore với nhóm. | Chốt tên collection/field và model trước cuối Ngày 2. Có một project chung. |
| **Ngày 3–5** | • Gửi/nhận tin nhắn qua Firestore.<br>• Làm đăng ký, đăng nhập email/Google và hồ sơ.<br>• Làm bảng tin và đăng bài qua Firestore.<br>• Làm tạo nhu cầu, tạo hộ và danh sách yêu cầu. | Mỗi phần đọc/ghi được dữ liệu thật. |
| **Ngày 6–7** | • Giới hạn chat đúng người gửi và đại diện được giao.<br>• Làm phân quyền, quên mật khẩu và đăng xuất.<br>• Làm sửa/ẩn bài, đọc bài đã xem khi offline.<br>• Làm lọc, nhận yêu cầu bằng transaction và hủy có lý do. | Mỗi người Push code và mở PR. Quyên tích hợp bản tuần 1. |
| **Ngày 8–10** | • Làm tổ chức đề xuất lịch, người gửi đồng ý. Hoàn thành và lịch sử.<br>• Kiểm thử tài khoản/hồ sơ khi ghép vào luồng chung.<br>• Hoàn thiện offline. Thử SQLite nếu kịp.<br>• Hoàn thiện trạng thái, Rules và kiểm tra không nhận trùng. | Chạy đủ luồng tạo nhu cầu, tổ chức nhận, chat, hẹn lịch và hoàn thành. |
| **Ngày 11–12** | • Sửa lỗi chat/lịch. Giữ nội dung khi xoay màn hình.<br>• Sửa lỗi tài khoản, trạng thái tải và lỗi đăng nhập.<br>• Kiểm tra giao diện ngang/dọc, offline và lỗi tải bài.<br>• Kiểm tra form, quyền dữ liệu và lỗi mất mạng khi nhận yêu cầu. | Chốt chức năng cuối Ngày 12. Từ đây tập trung sửa lỗi. |
| **Ngày 13–16** | • Viết Chương 6 và phần chat/lịch. Ghép PDF.<br>• Viết Chương 1, cấu trúc app và tài khoản.<br>• Viết Chương 3, bài đăng/offline. Tích hợp bản tuần 2 vào Ngày 14.<br>• Viết Chương 2, 4, Firebase và nhu cầu hỗ trợ. | Mỗi người nộp phần viết và kết quả kiểm thử. PDF đầy đủ cuối Ngày 16. |
| **Ngày 17–24** | • Kiểm tra đạo văn và chỉnh PDF theo kết quả.<br>• Kiểm tra bản build và luồng đăng nhập.<br>• Sửa giao diện, offline và chuẩn bị dữ liệu bài đăng.<br>• Kiểm tra Rules và chuẩn bị dữ liệu nhu cầu demo. | App ổn định. Báo cáo giải thích được hình và chức năng. |
| **25/10 – 26/10/2026** | • Làm slide, điều phối demo 5 phút.<br>• Xuất APK và kiểm tra cài trên thiết bị.<br>• Merge và gắn tag bản cuối.<br>• Chuẩn bị tài khoản/dữ liệu demo và giải thích Firestore. | Mỗi người luyện trả lời phần mình. Chốt APK, PDF, slide và GitHub. |

---

## 3. MỐC BÀN GIAO — CẬP NHẬT BẰNG KẾT QUẢ THỰC TẾ

| Mốc | Sản phẩm phải có | Người chốt | Điều kiện hoàn thành | Link minh chứng | Trạng thái |
|:---:|:---|:---|:---|:---:|:---:|
| **Mốc 1** | Project Android chung và schema/model thống nhất. | Nam Anh: project.<br>Hiếu: dữ liệu. | Project chạy được. Các thành viên dùng cùng model. | [Link commit/PR] | Chưa cập nhật |
| **Mốc 2** | Bản tuần 1 và code của cả 4 người. | Quyên (Leader) & Nhóm | PR đã được kiểm tra. main build được. | [Link commit/PR] | Chưa cập nhật |
| **Mốc 3** | Bản đầy đủ chức năng chính. | Quyên (Leader) & Nhóm | Demo được toàn bộ luồng hỗ trợ, chat và bài đăng. | [Link commit/PR] | Chưa cập nhật |
| **Mốc 4** | Bản tuần 2 và code của cả 4 người. | Quyên (Leader) & Nhóm | PR đã được kiểm tra. Bản tích hợp chạy được. | [Link commit/PR] | Chưa cập nhật |
| **Mốc 5** | PDF hoàn chỉnh và kết quả kiểm tra đạo văn. | Cả nhóm | Theo mẫu, dưới 20% theo yêu cầu thầy. Ghi đúng mức hoàn thành. | [Link PDF/Turnitin] | Chưa cập nhật |
| **Mốc 6** | APK, PDF, slide và phiên bản cuối trên GitHub. | Cả nhóm | Cài app, chạy demo 5 phút. Có branch/merge làm minh chứng. | [Link Release/Tag] | Chưa cập nhật |

> 💡 **Ghi chú mốc:** Điền link commit/PR và trạng thái thật vào bảng. Ngày 7 và 14 là các mốc kiểm tra nội bộ. Nếu thầy kiểm tra vào ngày khác, toàn đội phải chủ động push code trước ngày kiểm tra.

---

## 4. GITHUB — MỘT PROJECT CHUNG, MỖI NGƯỜI LÀM TRÊN NHÁNH CỦA MÌNH

| Bước | Thao tác | Người làm | Nội dung cụ thể | Kết quả | Khi nào |
|:---:|:---|:---|:---|:---|:---|
| **1** | Chuẩn bị project chung | Nam Anh | Đưa khung Android chạy được vào main. Có README hướng dẫn chạy. | Một project để cả nhóm dùng. | Ngày 1–2 |
| **2** | Clone, Pull rồi tạo nhánh | Từng thành viên | Lấy main mới nhất và tạo nhánh ghi ở bảng phân công. | Mỗi nhánh có đủ project. | Lần đầu làm việc |
| **3** | Tạo Issue và làm code | Người phụ trách chức năng | Issue ghi một người phụ trách, hạn và tiêu chí hoàn thành. Sửa phần đã giao. | Có đầu việc rõ ràng. | Trước mỗi đầu việc |
| **4** | Commit rồi Push | Người viết code | Commit lưu thay đổi. Push đưa code lên GitHub. Ví dụ: `feat: thêm gửi tin nhắn`. | Code và lịch sử thay đổi có trên GitHub. | Sau mỗi phần chạy được. Mỗi tuần phải có code. |
| **5** | Mở PR và kiểm tra | Tác giả mở PR, người khác kiểm tra | PR ghi chức năng đã làm và cách kiểm thử. Chỉ ghép khi build và luồng liên quan chạy được. | Có minh chứng review và đề nghị ghép code. | Khi phần việc sẵn sàng tích hợp |
| **6** | Merge rồi Pull bản mới | Quyên (Leader) merge, từng người Pull | Merge đưa code vào main. Nhánh cho đầu việc mới lấy từ main mới nhất. | main chứa bản app đã tích hợp. | Sau mỗi PR được kiểm tra |

### 📌 Tóm tắt quy trình phối hợp 6 bước:
1. **Nam Anh** chuẩn bị project chung chạy được, đưa vào `main`.
2. Chủ repo cấp quyền cho các thành viên. Mỗi người tải toàn bộ project về máy bằng thao tác `Clone`.
3. Mỗi người tạo nhánh từ `main`, rồi code phần mình phụ trách. Nhánh vẫn chứa toàn bộ project, nhưng người đó sửa phần việc của mình.
4. `Commit` rồi `Push` để đưa thay đổi lên GitHub.
5. Mở `Pull Request` vào `main`. Thành viên khác kiểm tra; **Quyên (Leader)** phụ trách gộp/tích hợp lại.
6. Sau khi ghép, mọi người `Pull` bản mới về máy trước khi làm phần tiếp theo.