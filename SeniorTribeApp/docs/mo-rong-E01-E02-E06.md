# Chức năng mở rộng E01, E02, E06 — Nhắc lịch & thông báo, Đổi lịch, Chat nâng cao

> Phụ trách: **Khiếu Hoàng Nam Anh** (`31241022203`) · Nhánh `feat/notifications-reschedule-chat`
> Căn cứ: `Phạm vi chức năng.md` mục 3 (E01, E02, E06), quy tắc R01–R09, kỹ thuật T02–T06.

## 1. Tóm tắt

| Mã | Chức năng | Đã làm |
|:--:|:--|:--|
| E01 | Nhắc lịch và thông báo | Thông báo theo vai trò cho tin nhắn, lịch hẹn, tiếp nhận / trạng thái, việc cần theo dõi; nhắc trước giờ hẹn 1 ngày và 1/2/3 giờ; màn hình tùy chọn loại thông báo; bấm thông báo mở đúng màn hình sau khi kiểm tra quyền. |
| E02 | Đổi lịch | Một bên đề xuất giờ mới sau khi đã chốt, bên còn lại xác nhận / từ chối; bên gửi rút lại được; lịch cũ chuyển `RESCHEDULED` và giữ trong lịch sử. |
| E06 | Chat nâng cao | Gửi ảnh (thư viện, chụp) và tệp (PDF, Word, Excel, PowerPoint, văn bản) tối đa 5 MB; xem / mở / lưu; gọi thoại và gọi video giữa hai người trong luồng chat. |

Ba chức năng đều **không cần gói Blaze**: không dùng Cloud Functions, FCM hay Cloud Storage.

## 2. E02 · Đổi lịch

### 2.1. Luồng

```
SCHEDULED (lịch c1 đã chốt)
  │  Bên A gửi yêu cầu đổi lịch r1 (giờ mới + lý do)      schedules/r1: PROPOSED, kind=RESCHEDULE
  │     - A là đầu mối  → phải ghi sẵn "cụ đồng ý giờ mới" + cách hỏi
  │     - A là tổ chức  → đầu mối hỏi ý kiến cụ khi xác nhận
  ├─ Bên B đồng ý  → r1: CONFIRMED, c1: RESCHEDULED (replacedById=r1),
  │                  nhu cầu trỏ sang r1, rescheduleCount + 1   (một transaction)
  ├─ Bên B không nhận (kèm lý do) → r1: DECLINED, lịch c1 giữ nguyên
  └─ Bên A rút lại              → r1: WITHDRAWN, lịch c1 giữ nguyên
```

Nhu cầu **vẫn ở trạng thái SCHEDULED** trong suốt quá trình; quyền xem thông tin riêng (R03) không đổi.

### 2.2. Dữ liệu (`needs_assignments/{a}/schedules/{id}`)

Thêm vào đề xuất F12: `kind = "RESCHEDULE"`, `replacesScheduleId`, `proposedBySide` (`ORG` / `RECORDER`), `reason`, `elderConsent` + `consentMethod` (khi đầu mối gửi), `replacedById` + `rescheduledAt` (khi bị thay). Nhu cầu thêm `rescheduleCount`, `rescheduledAt`. Sự kiện lịch sử: `RESCHEDULE_PROPOSED`, `RESCHEDULE_DECLINED`, `RESCHEDULE_WITHDRAWN`, `RESCHEDULED`.

### 2.3. Màn hình

`RescheduleActivity` (dọc 1 cột, ngang 2 cột): lịch đang hẹn · yêu cầu đang chờ · form giờ mới · lịch sử thay đổi. Mở từ: nút **Đổi lịch** ở màn hình Tiến độ và Thống nhất lịch, menu chat, tin hệ thống "Yêu cầu đổi lịch" trong chat.

## 3. E01 · Nhắc lịch và thông báo

### 3.1. Hai nguồn, một mốc "đã xử lý"

| Khi | Cách | Độ trễ |
|:--|:--|:--|
| Ứng dụng đang chạy (kể cả nền) | `NotificationCenter`: listener realtime trên nhu cầu của đơn vị, sự kiện `events` mới, tin nhắn mới, cuộc gọi đến | Tức thì |
| Ứng dụng đã tắt | `NotificationSyncWorker` (WorkManager, ~15 phút, cần mạng) | ≤ ~15 phút |
| Nhắc lịch | `ScheduleReminderWorker` hẹn trước (không cần mạng, còn sau khi khởi động lại máy) | Đúng mốc |

Cả hai nguồn dùng chung mốc trong `NotificationPrefs` nên mỗi sự kiện chỉ báo một lần. Không dùng FCM vì cần Cloud Functions (gói Blaze).

### 3.2. Ai nhận gì (`NotificationRules`, có unit test)

| Sự kiện | Người nhận | Loại |
|:--|:--|:--|
| Ghi nhận / gửi lại nhu cầu | Điều phối viên của đơn vị ghi nhận | Việc cần theo dõi |
| Yêu cầu bổ sung / đã duyệt | Đầu mối đã ghi nhận | Việc cần theo dõi / Trạng thái |
| Tiếp nhận | Người được giao ("Bạn được giao phụ trách"), đầu mối đơn vị ghi nhận | Trạng thái |
| Đề xuất lịch | Đầu mối | Lịch hẹn |
| Từ chối đề xuất / chốt lịch | Tổ chức phụ trách | Lịch hẹn |
| Yêu cầu đổi lịch, không nhận, rút lại, đã đổi | Bên còn lại | Lịch hẹn |
| Báo đã hỗ trợ | Đầu mối (cần đối soát) | Việc cần theo dõi |
| Ghi nội dung chưa khớp / hoàn thành / hủy | Tổ chức phụ trách | Việc cần theo dõi / Trạng thái |
| Trả lại | Đầu mối | Trạng thái |
| Tin nhắn, cuộc gọi đến | Người còn lại trong luồng chat (R08) | Tin nhắn / Cuộc gọi |

Không báo việc do chính mình làm. Đang mở luồng chat nào thì không báo tin của luồng đó.

### 3.3. Riêng tư

- Thông báo đặt `VISIBILITY_PRIVATE`: màn hình khóa chỉ hiện "Có thông báo mới".
- Không chứa tên, địa chỉ, số điện thoại của người cao tuổi; chỉ mã hồ sơ, tiêu đề tóm tắt, tên người thao tác.
- Nội dung tin nhắn mặc định **ẩn** ("Mai đã gửi tin nhắn mới"); người dùng tự bật nếu muốn.
- Chỉ lưu cờ bật/tắt và mốc thời gian (SharedPreferences). Đăng xuất: gỡ thông báo, hủy nhắc lịch, xóa mốc.
- Bấm thông báo luôn đi qua `MainActivity` → `SessionGate` (nếu cần) để kiểm tra phiên và quyền tổ chức trước (R01).

### 3.4. Màn hình

Hồ sơ → **Thông báo & nhắc lịch**: bật/tắt 4 loại; nhắc trước 1 ngày; nhắc lại trước 1/2/3 giờ hoặc không; hiện nội dung tin nhắn; cảnh báo khi máy đang tắt thông báo của app (mở Cài đặt hệ thống).

## 4. E06 · Chat nâng cao

### 4.1. Ảnh / tệp

- Nhóm dùng gói Firebase miễn phí, **Cloud Storage cần gói Blaze**, nên tệp được chia mảnh ≤ 700 000 byte lưu trong Firestore:

```
needs_assignments/{a}/attachments/{attId}            ownerUid, fileName, mimeType, size, chunkCount, kind, createdAt
needs_assignments/{a}/attachments/{attId}/chunks/{i} index, data (bytes)
needs_assignments/{a}/messages/{m}                   type IMAGE/FILE, attachmentId, fileName, mimeType, size, thumb, text (chú thích)
```

- Thứ tự ghi: metadata + mảnh 0 → mảnh 1..n-1 (mỗi mảnh một request) → tin nhắn. Rules chỉ nhận tin khi mảnh cuối đã có, nên bên kia không thấy tệp dở dang.
- Ảnh được nén lại (cạnh dài ≤ 1600 px, JPEG) → **mất EXIF/GPS**; kèm ảnh nhỏ `thumb` ≤ 64 KB để hiện ngay trong chat.
- Trước khi gửi: hộp thoại xem trước + chú thích + nhắc riêng tư; nếu chưa chốt lịch, nhắc thêm không gửi địa chỉ / giấy tờ của cụ (R03).
- Xem ảnh: phóng to bằng hai ngón, `FLAG_SECURE` (không chụp màn hình). Mở tệp bằng ứng dụng khác: ghi tạm vào `cache/shared` qua `FileProvider`, xóa khi đóng chat và khi mở lại app (F16, T05). "Lưu về máy" là thao tác chủ động, có cảnh báo.
- Giới hạn: 5 MB / tệp; PDF, Word, Excel, PowerPoint, văn bản, ảnh.

### 4.2. Gọi thoại / video

- WebRTC (`io.getstream:stream-webrtc-android`, bản dựng sẵn của thư viện WebRTC mã nguồn mở). Firestore chỉ dùng để trao đổi tín hiệu:

```
needs_assignments/{a}/calls/{c}             callerUid, callerName, calleeUid, video, status, offer, answer, createdAt, answeredAt, endedAt, endedByUid, durationSec
needs_assignments/{a}/calls/{c}/candidates  fromUid, candidate, sdpMid, sdpMLineIndex, at
```

- Trạng thái: `RINGING` → `ACCEPTED` → `ENDED`; hoặc `DECLINED` (người nghe), `CANCELLED` / `MISSED` (người gọi, 45 giây không ai nghe).
- Âm thanh / hình ảnh đi thẳng giữa hai máy, không lưu lại. Nhật ký cuộc gọi hiện trong chat như tin hệ thống.
- Cuộc gọi đến: đang ở màn hình chat thì mở ngay màn hình nghe máy; app chạy nền thì có thông báo kênh "Cuộc gọi".
- Mạng: mặc định dùng STUN công khai của Google, đủ khi hai máy cùng Wi-Fi hoặc mạng thông thường. Một số mạng di động / công ty chặn kết nối trực tiếp, khi đó cần máy chủ TURN (điền trong `res/values/webrtc.xml`, không commit mật khẩu).
- Màn hình gọi tự xử lý xoay (`configChanges`) để cuộc gọi không bị ngắt; ẩn màn hình thì tạm tắt camera.

## 5. Security Rules và kiểm thử

| Tệp kiểm thử | Số ca | Nội dung chính |
|:--|:--:|:--|
| `firebase/tests/reschedule.rules.test.mjs` | 30 | Ai được gửi / xác nhận / từ chối / rút lại; đồng ý của cụ; lịch cũ phải chuyển RESCHEDULED cùng lúc; giờ trên nhu cầu khớp lịch mới; `rescheduleCount` +1; hồi quy F12 (chốt lịch phải đúng lịch nhu cầu trỏ tới). |
| `firebase/tests/media.rules.test.mjs` | 23 | Tin ảnh/tệp trỏ đúng tệp của mình, khớp dung lượng / kiểu, mảnh cuối đã có; mảnh ≤ 700 000 byte, đúng thứ tự; tệp 5 MB (8 mảnh); người ngoài luồng chat không đọc / ghi; lần tiếp nhận kết thúc thì chỉ đọc; gọi đúng người, đúng vai khi đổi trạng thái; ICE chỉ hai bên. |
| `app/src/test/.../NotificationRulesTest.java` | 11 | Ai nhận thông báo cho từng sự kiện; tin nhắn theo R08; mốc nhắc lịch. |

Chạy: `cd SeniorTribeApp/firebase/tests && npm install && npm test` (các tệp chạy tuần tự, **102/102 pass**); `./gradlew testDebugUnitTest`.

Ghi chú kỹ thuật: Security Rules giới hạn số lần `get()` mỗi request, nên các rule ghi của chat dùng `activeChatMember()` đọc lần tiếp nhận và nhu cầu đúng một lần (`let`).

## 6. Kiểm thử thủ công trên 2 máy

| # | Kịch bản | Kết quả mong đợi |
|:--:|:--|:--|
| 1 | Máy A (tổ chức) gửi yêu cầu đổi lịch; máy B (đầu mối) mở thông báo | B mở thẳng màn hình Đổi lịch, phải tick "cụ đồng ý" mới bấm được "Đồng ý đổi lịch" |
| 2 | B đồng ý; xem Tiến độ, Lịch, chat | Giờ mới ở mọi nơi; lịch sử có "Đã được thay"; chat có "Đã đổi lịch sang…" |
| 3 | A gửi yêu cầu, rồi gửi giờ khác | Yêu cầu cũ thành "Đã rút lại", chỉ còn một yêu cầu chờ |
| 4 | Tắt hẳn app trên B, A nhắn tin | B nhận thông báo trong ~15 phút; mở app thì không báo lại |
| 5 | Đặt lịch hẹn sau 2 giờ 5 phút, chọn "nhắc trước 2 giờ", tắt mạng | Thông báo nhắc vẫn đến đúng giờ |
| 6 | Đổi lịch sau khi đã hẹn nhắc | Không nhắc giờ cũ; nhắc theo giờ mới |
| 7 | Gửi ảnh chụp có GPS | Ảnh bên kia nhận không còn EXIF vị trí |
| 8 | Gửi PDF 4 MB, tắt mạng giữa chừng | Không có tin nào bên kia; bật mạng gửi lại được |
| 9 | Gọi video, xoay ngang / dọc | Cuộc gọi không ngắt; khung hình co giãn |
| 10 | Nhu cầu bị trả lại khi đang mở chat | Nút đính kèm và nút gọi ẩn; chat chỉ còn đọc |
| 11 | Đăng xuất | Thông báo đang hiện bị gỡ; không còn nhắc lịch |

## 7. Giới hạn đã biết

- Không có push từ máy chủ: khi app đã tắt, thông báo đến trễ tối đa ~15 phút (tùy chế độ tiết kiệm pin của máy). Cuộc gọi đến chỉ đổ chuông khi app đang chạy (kể cả nền).
- Gọi qua mạng di động có thể không kết nối được nếu không có TURN.
- Cuộc gọi chạy trong màn hình gọi; Android 11+ không cho dùng micro khi app ở nền lâu mà không có foreground service, nên rời màn hình gọi lâu có thể mất tiếng.
- Tệp tối đa 5 MB vì lưu trong Firestore (gói miễn phí giới hạn dung lượng và lượt đọc / ghi mỗi ngày).
