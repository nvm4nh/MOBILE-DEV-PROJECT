# Chức năng mở rộng E08, E09 — Tìm kiếm & giao diện nâng cao, SOS

> Phụ trách theo bảng phân công: **Nguyễn Văn Trường** (`31221025476`) · Nhánh `feat/search-ui-sos`
> Căn cứ: `Phạm vi chức năng.md` mục 3 (E08, E09), quy tắc R02, R03, R08, kỹ thuật T04, T05.
> Ghi chú: bảng phân công ghi "E01, E09" cho dòng "Tìm kiếm giao diện nâng cao, SOS"; theo `Phạm vi chức năng.md`, nội dung này là **E08** (E01 là nhắc lịch, thuộc nhánh khác).

## 1. Tóm tắt

| Mã | Chức năng | Đã làm |
|:--:|:--|:--|
| E08 | Tìm kiếm và giao diện nâng cao | Tìm chung nhu cầu / bài đăng / lịch hẹn trong một ô, không cần dấu, nhiều từ, tô đậm chữ khớp, lọc theo loại, từ khóa gần đây; 4 mức cỡ chữ áp dụng toàn app; dark mode thật (bảng màu riêng) và chọn Theo máy / Sáng / Tối. |
| E09 | SOS | Màn hình Khẩn cấp: gọi 115 / 113 / 114 qua trình quay số; báo khẩn cho bên cùng phối hợp (loại sự việc, ghi chú, vị trí nếu muốn); bên kia nhận thông báo kênh "Khẩn cấp", xác nhận đã nhận, ghi đã xử lý xong. **Không cam kết cứu hộ.** |

## 2. E08 · Tìm kiếm chung

- Mở từ biểu tượng 🔍 ở Bảng tin. Lọc: Tất cả / Nhu cầu / Bài đăng / Lịch hẹn; ở "Tất cả" mỗi nhóm hiện 5 kết quả đầu, có "Xem tất cả".
- Nguồn dữ liệu (chỉ phần được phép đọc, R02, R03):
  - Nhu cầu của đơn vị (ghi nhận hoặc phụ trách) và nhu cầu `OPEN` trong mạng lưới — **chỉ bản tóm tắt** (`needs_summary`), không đọc `needs_private`.
  - Bài đăng chưa ẩn; mất mạng thì tìm trong bài đã lưu SQLite (F16) và hiện cảnh báo.
  - Lịch hẹn (`SCHEDULED`, `DELIVERED`, `COMPLETED`) của đơn vị: tìm được theo "thu 7", "19/10", hình thức, tổ chức.
- `SearchMatcher` (Java thuần, có unit test): bỏ dấu **từng ký tự** nên vị trí khớp trùng chuỗi gốc → tô đậm đúng "khám mắt" khi gõ "kham mat"; mọi từ khóa phải xuất hiện (AND); xếp hạng ưu tiên khớp ở tiêu đề, đầu từ, cả cụm liền nhau.
- Dữ liệu tải một lần rồi lọc ở máy như F09 → không cần composite index, gõ tới đâu ra tới đó.
- Từ khóa gần đây: tối đa 6, lưu trên máy; **không lưu từ khóa có ≥ 9 chữ số** (tránh giữ số điện thoại); xóa được; tự xóa khi đăng xuất.

## 3. E08 · Cỡ chữ & giao diện

- Hồ sơ → **Cỡ chữ & giao diện**: Nhỏ (0,9×) / Vừa / Lớn (1,15×) / Rất lớn (1,3×), có xem trước; Giao diện Theo máy / Sáng / Tối.
- Cỡ chữ **nhân với cỡ chữ của máy** (vẫn tôn trọng cài đặt trợ năng), giới hạn 0,8–2,0. Áp dụng cho mọi màn hình qua `TextScaleApplier` (ActivityLifecycleCallbacks) nên không phải sửa từng Activity; màn hình đang mở tự tạo lại khi quay về.
- Dark mode: `values-night/colors.xml` định nghĩa lại toàn bộ bảng màu cùng tên (nền, bề mặt, chữ, viền, nhãn trạng thái, ghi chú cảnh báo, màn hình tài khoản), độ tương phản chữ ≥ 4,5:1; thanh trạng thái tối. Trước đây `values-night/themes.xml` chỉ đổi nền cửa sổ nên chữ / thẻ vẫn sáng.

## 4. E09 · SOS

### 4.1. Phạm vi

Theo `Phạm vi chức năng.md`: *"Chỉ xem xét khi đã có đơn vị phản ứng và quy trình vận hành. Không đưa cam kết cứu hộ vào MVP."* Vì vậy SOS chỉ làm hai việc:

1. Đưa người dùng tới **số khẩn cấp quốc gia** (115 cấp cứu, 113 công an, 114 cứu hỏa – cứu nạn) bằng `ACTION_DIAL` (mở trình quay số, người dùng tự bấm gọi → không cần quyền `CALL_PHONE`, không gọi nhầm).
2. **Báo khẩn cho người còn lại trong lần phối hợp** (đầu mối đã ghi nhận ↔ người được giao) để họ liên hệ người nhà của cụ, hỗ trợ thêm. Màn hình luôn nhắc rõ "SeniorTribe không phải dịch vụ cứu hộ".

### 4.2. Dữ liệu

```
needs_assignments/{a}/sos/{alertId}
  byUid, byName, kind (MEDICAL | SAFETY | FIRE | OTHER), note (≤ 300), status (OPEN → ACK → RESOLVED),
  createdAt, calledNumber? (115 | 113 | 114), location? (GeoPoint), accuracyM?,
  ackByUid, ackByName, ackAt, resolvedByUid, resolvedByName, resolvedAt, resolveNote
```

- Gắn theo **lần tiếp nhận** nên chỉ hai người trong luồng chat đọc / ghi (R08); vị trí (nếu gửi) chỉ hai người đó thấy.
- Không sửa nội dung, không xóa (giữ để đối soát).
- Vị trí lấy **một lần** bằng `LocationManager` khi người dùng tick "Gửi kèm vị trí" (ưu tiên vị trí trong 2 phút, chờ tối đa 10 giây); không theo dõi liên tục.

### 4.3. Nhận báo khẩn

`SosWatcher` chạy khi ứng dụng đang mở: theo dõi báo khẩn `OPEN` của các lần phối hợp mình tham gia, hiện thông báo mức cao (kênh "Khẩn cấp", ẩn nội dung trên màn hình khóa), mỗi báo khẩn một lần, bỏ qua báo khẩn quá 30 phút. Bấm thông báo mở màn hình SOS của nhu cầu đó để **Tôi đã nhận** / **Đã xử lý xong** (kèm ghi chú), **Xem vị trí** trên bản đồ.

### 4.4. Lối vào

Nút đỏ **SOS** ở Bảng tin · Hồ sơ → **Khẩn cấp (SOS)** · menu ⋮ của chi tiết nhu cầu đang phối hợp (chọn sẵn nhu cầu).

## 5. Security Rules và kiểm thử

| Tệp | Số ca | Nội dung |
|:--|:--:|:--|
| `firebase/tests/sos.rules.test.mjs` | 10 | Chỉ hai người trong luồng chat gửi / đọc; không giả danh; loại / số gọi / vị trí hợp lệ; không thêm trường lạ (ví dụ địa chỉ); lần tiếp nhận kết thúc thì không gửi; bên kia xác nhận, người báo không tự xác nhận; đã xử lý thì không mở lại; không sửa nội dung, không xóa. |
| `app/src/test/.../SearchMatcherTest.java` | 7 | Bỏ dấu giữ độ dài, nhiều từ, tô đậm đúng chữ có dấu, gộp đoạn, xếp hạng, đoạn trích. |
| `app/src/test/.../TextScaleApplierTest.java` | 3 | Hệ số cỡ chữ nhân với cỡ chữ máy, giới hạn. |

Chạy: `cd SeniorTribeApp/firebase/tests && npm test` (**59/59 pass** trên nhánh này); `./gradlew testDebugUnitTest`.

## 6. Kiểm thử thủ công

| # | Kịch bản | Kết quả mong đợi |
|:--:|:--|:--|
| 1 | Gõ "kham mat" | Ra nhu cầu "Khám mắt…", chữ "Khám mắt" được tô đậm |
| 2 | Gõ "thu 7" ở nhóm Lịch hẹn | Ra các lịch vào thứ 7 |
| 3 | Tắt mạng rồi tìm bài đăng | Có cảnh báo, vẫn ra bài đã tải trước đó |
| 4 | Gõ một số điện thoại rồi bấm tìm | Không xuất hiện trong "Tìm gần đây" |
| 5 | Chọn cỡ chữ "Rất lớn", quay lại Chat, Nhu cầu | Chữ to ở mọi màn hình, không cần khởi động lại |
| 6 | Chọn "Tối" | Mọi màn hình nền tối, chữ sáng, thanh trạng thái tối |
| 7 | Xoay ngang màn hình Tìm kiếm, SOS | Giữ từ khóa, bộ lọc, ghi chú đang gõ; SOS chia 2 cột |
| 8 | Máy A (người được giao) gửi báo khẩn kèm vị trí; máy B (đầu mối) đang mở app | B nhận thông báo "Báo khẩn · NC-xxxx", bấm vào thấy vị trí, bấm "Tôi đã nhận" |
| 9 | Tài khoản khác cùng tổ chức mở SOS | Không thấy nhu cầu để báo, chỉ gọi được số khẩn cấp |
| 10 | Bấm "115 · Cấp cứu y tế" | Mở trình quay số với 115, chưa tự gọi |

## 7. Giới hạn

- Báo khẩn chỉ đến khi máy bên kia đang chạy ứng dụng (không có push từ máy chủ vì gói Firebase miễn phí). Khi ghép với nhánh E01, có thể đưa báo khẩn vào cơ chế kiểm tra định kỳ của E01.
- Không có đơn vị phản ứng / tổng đài riêng; không theo dõi vị trí liên tục.
