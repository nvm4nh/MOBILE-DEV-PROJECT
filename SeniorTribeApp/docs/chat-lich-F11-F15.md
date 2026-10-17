# Chat, lịch, kết quả, lịch sử · F11–F13, F15 (nhánh `feat/chat-schedule`)

Phần phối hợp sau khi tổ chức tiếp nhận nhu cầu (F10): trao đổi qua chat, thống nhất lịch với người cao tuổi, báo đã hỗ trợ, đối soát và xem lại lịch sử.

## 1. Đã làm gì

| Mã | Màn hình | File chính |
|---|---|---|
| F11 | Chat realtime theo từng lần tiếp nhận; danh sách cuộc trò chuyện | `activities/ChatActivity`, `fragments/ChatListFragment`, `layout/include_chat_thread.xml` |
| F12 | Thẻ phối hợp trên màn hình chat: tổ chức **Đề xuất lịch** → đầu mối **Xác nhận** (ghi cách người cao tuổi đồng ý) hoặc **Từ chối** (ghi lý do) | `dialogs/ProposeScheduleDialog`, `dialogs/TextInputDialog`, `layout/include_coordination_card.xml` |
| F13 | Tổ chức **Báo đã hỗ trợ** → đầu mối **Xác nhận hoàn thành** hoặc **Chưa thống nhất** (giữ `DELIVERED`, ghi nội dung cần đối soát) | như F12 |
| F15 | Tab Lịch hẹn: *Sắp tới* / *Đã thực hiện*; màn hình **Lịch sử hỗ trợ** (dòng thời gian mọi bước) | `fragments/ScheduleFragment`, `activities/NeedHistoryActivity`, `adapters/NeedEventAdapter` |

Phần dùng chung:

- `data/CoordinationRepository`: mọi thao tác Firestore của F11–F13, F15. Mỗi bước đổi trạng thái là một transaction (đọc trên máy chủ → kiểm tra → ghi) và ghi thêm một sự kiện lịch sử.
- `viewmodels/ChatViewModel`: giữ tin nhắn, lịch và trạng thái khi xoay màn hình (T04). `layout-land/activity_chat.xml`: thẻ phối hợp bên trái, chat bên phải.
- `viewmodels/MyCoordinationViewModel`: gộp nhu cầu đơn vị ghi nhận và nhu cầu đơn vị phụ trách cho danh sách chat và tab Lịch hẹn, sắp xếp ở máy nên không cần composite index.
- `firebase/firestore.rules`: thay hàm `coordinationStep()` đề xuất bằng 4 bước cụ thể, thêm rules cho `messages` và `schedules`.
- `NeedDetailActivity`: nút chat chỉ hiện cho 2 người trong luồng; thêm menu **Lịch sử hỗ trợ**; ghi chú lịch đã chốt / kết quả.
- `NeedRepository.returnNeed`: khi trả lại, xóa lịch đã chốt khỏi nhu cầu (lịch cũ hết hiệu lực).

## 2. Dữ liệu Firestore (bổ sung cho Chương 4)

| Collection / document | Ai ghi | Nội dung chính |
|---|---|---|
| `needs_assignments/{assignmentId}/messages/{id}` | 2 người trong luồng | `senderUid`, `senderName`, `text`, `at` |
| `needs_assignments/{assignmentId}/schedules/{id}` | Tổ chức đề xuất, đầu mối quyết định | `startAt`, `place`, `note`, `status: PROPOSED / CONFIRMED / DECLINED / WITHDRAWN`, `proposedByUid/Name`, `consentMethod`, `declineReason`, `decidedByUid/Name`, `decidedAt`, `createdAt` |
| `needs_summary/{needId}` (field mới) | App | Lịch: `scheduleId`, `scheduledAt`, `scheduledPlace`, `scheduledByUid`; báo hỗ trợ: `deliveryReport`, `deliveredByUid/Name`, `deliveredAt`; đối soát: `reconcileNote`, `reconcileAt`; hoàn thành: `completionNote`, `completedByUid`, `completedAt` |
| `needs_summary/{needId}/events` (loại mới) | App | `SCHEDULE_PROPOSED`, `SCHEDULE_DECLINED`, `SCHEDULED`, `DELIVERED`, `RECONCILE_PENDING`, `COMPLETED` |

Chat và lịch nằm **dưới lần tiếp nhận**, không dưới nhu cầu: khi trả lại và tổ chức khác nhận, luồng mới bắt đầu trống, người phụ trách cũ không đọc được (R08).

## 3. Ai được làm gì

| Thao tác | Điều kiện (app kiểm tra, rules kiểm tra lại) |
|---|---|
| Đọc / gửi tin nhắn | Chỉ đầu mối đã ghi nhận (`recorderUid`) và người được giao (`assigneeUid`). Gửi khi lần tiếp nhận còn `ACTIVE` và nhu cầu ở `ACCEPTED` / `SCHEDULED` / `DELIVERED`; sau đó chỉ đọc |
| Đề xuất lịch | `REPRESENTATIVE` của tổ chức phụ trách hoặc người được giao; nhu cầu `ACCEPTED`; thời gian trong tương lai. Đề xuất đang chờ cũ tự chuyển `WITHDRAWN` |
| Xác nhận lịch | `RECORDER` của đơn vị ghi nhận; bắt buộc ghi cách người cao tuổi đồng ý. Lịch `CONFIRMED` và nhu cầu `ACCEPTED → SCHEDULED` trong cùng transaction |
| Từ chối lịch | `RECORDER` của đơn vị ghi nhận; bắt buộc lý do; nhu cầu vẫn `ACCEPTED` |
| Báo đã hỗ trợ | Thành viên tổ chức phụ trách; `SCHEDULED → DELIVERED`; mô tả ít nhất 5 ký tự |
| Chưa thống nhất | `RECORDER`; giữ `DELIVERED`, ghi `reconcileNote` |
| Xác nhận hoàn thành | `RECORDER`; `DELIVERED → COMPLETED`; lần tiếp nhận chuyển `CLOSED`, chat chỉ đọc |
| Xem thông tin riêng | Người được giao chỉ đọc được khi `SCHEDULED` / `DELIVERED` (rules có sẵn từ F10) |

## 4. Kiểm thử

- [ ] Đầu mối và người được giao thấy cuộc trò chuyện trong tab Chat; thành viên khác của tổ chức không thấy nút chat.
- [ ] Gửi tin ở máy A → máy B nhận realtime; xoay ngang khi đang gõ → nội dung còn nguyên.
- [ ] Tổ chức đề xuất lịch ở quá khứ → báo lỗi. Đề xuất hợp lệ → đầu mối thấy nút Xác nhận / Từ chối.
- [ ] Đầu mối từ chối → tổ chức thấy lý do, đề xuất lịch khác.
- [ ] Đầu mối xác nhận → nhu cầu `SCHEDULED`, người phụ trách thấy thông tin riêng trên chi tiết nhu cầu; lịch hiện ở tab Lịch hẹn → Sắp tới.
- [ ] Trả lại khi đang `SCHEDULED` → lịch biến mất khỏi nhu cầu; chat cũ chỉ đọc; tổ chức mới nhận → chat mới trống.
- [ ] Báo đã hỗ trợ → đầu mối bấm Chưa thống nhất → ghi chú hiện ở thẻ phối hợp → bấm Xác nhận hoàn thành → chat chỉ đọc.
- [ ] Tắt mạng rồi bấm Đề xuất / Xác nhận / Báo hỗ trợ → báo cần mạng, trạng thái không đổi.
- [ ] Lịch sử hỗ trợ hiện đủ các bước theo thứ tự thời gian.

## 5. Ghi chú

- Rules mới chưa được chạy thử trên Firebase Emulator; cần **Publish** `firebase/firestore.rules` lên Console rồi chạy các ca kiểm thử ở trên.
- Đổi lịch sau khi đã chốt (E02) chưa làm: muốn đổi thì tổ chức trả lại hoặc trao đổi qua chat.
- Danh sách chat chỉ hiện luồng của lần tiếp nhận hiện tại; luồng của lần tiếp nhận đã trả lại không còn lối vào trong MVP.
