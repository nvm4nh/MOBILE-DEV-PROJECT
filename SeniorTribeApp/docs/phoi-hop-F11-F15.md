# Phối hợp sau tiếp nhận (F11, F12, F13, F15)

Tài liệu cho nhánh `feat/coordination-figma`: màn hình theo Figma *SeniorTribe — Giao diện MVP*
(trang "04 Phối hợp hỗ trợ (F11–F13, F15)"), dữ liệu Firestore, Security Rules và cách kiểm thử.

## 1. Màn hình

| Mã | Màn hình (Figma) | Lớp | Ghi chú |
|:--|:--|:--|:--|
| F11 | Chat · NC-xxxx | `ChatActivity`, `ChatAdapter`, `ChatViewModel` | Dòng chia ngày "Hôm nay / Hôm qua / Thứ 7, 19/10"; tin hệ thống "Đề xuất lịch: …", "Đã chốt lịch: …", "Đầu mối đề nghị đổi giờ: …" (bấm để mở F12); tin chưa được máy chủ nhận hiện "Đang gửi…"; gửi lỗi thì nội dung được trả lại ô nhập. |
| F12 | Thống nhất lịch + đồng ý | `ScheduleAgreementActivity`, `CoordinationViewModel` | Đề xuất của tổ chức: Ngày, Khung giờ, Hình thức. Ghi nhận đồng ý (đầu mối): ô tick "Cụ đồng ý với lịch hẹn này" + Cách hỏi ý kiến. Nút "Đổi giờ khác" / "Chốt lịch". Bố cục ngang 2 cột. |
| F13 | Tiến độ & đối soát kết quả | `NeedProgressActivity`, `CoordinationViewModel` | 5 bước: Đã tiếp nhận → Đã hẹn lịch → Tổ chức báo đã hỗ trợ → Đầu mối đối soát → Hoàn thành. Tổ chức báo kết quả; đầu mối "Chưa khớp" / "Hoàn thành". Bố cục ngang 2 cột. |
| F15 | Lịch | `ScheduleFragment`, `ScheduleAdapter` | Tab "Sắp tới" / "Lịch sử", nhóm theo ngày, ô giờ bên trái; "Gần đây" dưới tab Sắp tới. Bấm lịch mở F13. |

Tin hệ thống trong chat **không** được ghi vào `messages`: chúng được dựng từ
`needs_assignments/{id}/schedules`, nên người dùng không thể giả một tin "Đã chốt lịch".

## 2. Dữ liệu mới

`needs_assignments/{assignmentId}/schedules/{scheduleId}`

| Field | Kiểu | Ý nghĩa |
|:--|:--|:--|
| `startAt`, `endAt` | timestamp | Khung giờ hẹn; `endAt` sau `startAt`, tối đa 12 tiếng |
| `format` | string (2–120) | Hình thức, ví dụ "Đến nhà · 2 đoàn viên" |
| `place` | string | Khu vực chung, lấy từ `area` của nhu cầu (địa chỉ chính xác chỉ mở sau khi chốt, R03) |
| `elderConsent` | bool | Chỉ có khi `CONFIRMED`, phải là `true` |
| `consentMethod` | string | Cách đầu mối hỏi ý kiến cụ |

`needs_summary/{needId}` khi chốt lịch thêm `scheduledEndAt`, `scheduledFormat` (chép đúng từ lịch đã chốt).

> Đề xuất cũ tạo trước bản này không có `endAt`/`format` nên không chốt được; tổ chức chỉ cần
> bấm "Đề xuất giờ khác" để gửi lại.

## 3. Kiểm thử Security Rules

```bash
cd SeniorTribeApp/firebase/tests
npm install
npm test        # chạy Firebase Emulator (cần Java 21+), 37 ca
```

Các ca chính: chỉ đầu mối ghi nhận và người được giao vào được chat (R08); không giả người gửi,
không tự đặt thời gian, không chèn field; lần tiếp nhận đã trả lại / hoàn thành thì chỉ đọc;
đề xuất thiếu khung giờ / hình thức, giờ kết thúc trước giờ bắt đầu, khung giờ > 12 tiếng đều bị
từ chối; chốt lịch bắt buộc `elderConsent == true` và khớp khung giờ / hình thức; đại diện được giao
chỉ đọc được `needs_private` sau khi chốt (R03); báo hỗ trợ / đối soát đúng vai trò.

## 4. Kiểm thử trên Firebase thật (project `seniortribe`)

Emulator chạy đúng file `firestore.rules`, nhưng trước buổi demo vẫn cần thử trên project thật:

1. Firebase Console → Firestore → Rules: dán `firebase/firestore.rules` → **Publish**.
2. Dùng 2 máy (hoặc 1 máy + 1 emulator): tài khoản đầu mối ghi nhận và tài khoản đại diện tổ chức.
3. Chạy lần lượt và đánh dấu kết quả:

| # | Thao tác | Kỳ vọng |
|:-:|:--|:--|
| 1 | Hai bên nhắn tin | Tin hiện realtime, có dòng "Hôm nay" |
| 2 | Tổ chức mở "Lịch hẹn" → chọn Ngày, Khung giờ, Hình thức → Gửi đề xuất | Chat hiện "Đề xuất lịch: …" ở cả hai máy |
| 3 | Đầu mối mở đề xuất, chưa tick "Cụ đồng ý" | Nút "Chốt lịch" mờ |
| 4 | Tick + ghi cách hỏi ý kiến → Chốt lịch | Trạng thái "Đã hẹn lịch"; chat hiện "Đã chốt lịch: …"; đại diện được giao xem được thông tin riêng |
| 5 | Đầu mối bấm "Đổi giờ khác" (ở một đề xuất khác) kèm lý do | Tổ chức thấy lý do, đề xuất lại được |
| 6 | Tổ chức mở "Tiến độ" → ghi kết quả → Báo đã hỗ trợ | Bước 3 xong, bước 4 "Đang chờ đầu mối hỏi lại cụ" |
| 7 | Đầu mối ghi nội dung → "Chưa khớp" | Vẫn "Chờ đối soát", hiện "Chưa khớp: …" |
| 8 | Đầu mối "Hoàn thành" | Nhu cầu "Hoàn thành", chat chỉ còn đọc |
| 9 | Tắt mạng rồi gửi tin; bật mạng | Tin hiện "Đang gửi…", sau đó có giờ gửi |
| 10 | Trả lại nhu cầu (F14) rồi gửi tin ở luồng cũ | Ô nhập ẩn; nếu gửi kịp trước khi màn hình cập nhật thì bị từ chối và nội dung trở lại ô nhập |
| 11 | Xoay ngang ở F12 khi đang gõ cách hỏi ý kiến | Bố cục 2 cột, nội dung và ô tick giữ nguyên |
| 12 | Tab "Lịch" | Lịch nhóm theo ngày, ô giờ bên trái; tab "Lịch sử" có lần đã hoàn thành |
