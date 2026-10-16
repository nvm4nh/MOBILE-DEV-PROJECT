# Nhu cầu hỗ trợ · F06–F10, F14 (nhánh `feat/requests`)

Ghi nhận → rà soát → tìm/lọc → tiếp nhận → trả lại/hủy nhu cầu, theo Figma và mục 2.2.4 của báo cáo.

## 1. Đã làm gì

| Mã | Màn hình | File chính |
|---|---|---|
| F06 | Ghi nhận nhu cầu (3 phần: Tóm tắt · Thông tin riêng · Sự đồng ý), Lưu nháp, xoay ngang 2 cột | `activities/CreateNeedActivity`, `layout/activity_create_need.xml`, `layout-land/activity_create_need.xml`, `include_need_form_*.xml` |
| F07 | Rà soát: 4 mục kiểm tra, Duyệt / Yêu cầu bổ sung | `activities/ReviewNeedActivity`, `layout/activity_review_need.xml` |
| F08 | Tab Nhu cầu → "Đơn vị ghi nhận": lọc theo trạng thái, sửa khi chưa có tổ chức nhận | `fragments/NeedListFragment`, `viewmodels/NeedListViewModel` |
| F09 | Tab Nhu cầu → "Tìm để tiếp nhận": từ khóa (không phân biệt dấu), lọc khu vực / chủ đề / thời gian | như F08 |
| F10 | Chi tiết nhu cầu, che thông tin riêng, hộp thoại chọn đại diện, tiếp nhận bằng transaction | `activities/NeedDetailActivity`, `dialogs/AcceptNeedDialog` |
| F14 | Trả lại (tổ chức) / Hủy (đầu mối), bắt buộc lý do | `dialogs/NeedReasonSheet` |

Phần dùng chung:

- `data/NeedRepository`: mọi thao tác Firestore. Mỗi lần đổi trạng thái là một transaction (đọc trạng thái trên máy chủ → kiểm tra → ghi), nên khi offline thì không báo thành công giả (R04).
- `data/MembershipRepository`: đọc quyền từ `memberships/{uid}/organizations/{orgId}`, cùng schema với nhánh `feat/auth-profile`.
- `viewmodels/*`: giữ dữ liệu khi xoay màn hình (T04).
- `utils/PrivacyGuard`: chặn gửi nếu phần tóm tắt có số điện thoại hoặc tên người cao tuổi (R03).
- `utils/NeedDraftStore`: lưu nháp, **chỉ phần tóm tắt**, không lưu thông tin riêng xuống máy (T05).
- `utils/StateView` + `layout/view_list_state.xml`: các trạng thái đang tải / rỗng / lỗi / chưa có quyền (F16).
- `firebase/firestore.rules`: rules cho nhu cầu, ghép với rules users/memberships của Trường.
- `firebase/seed/`: script tạo tổ chức, quyền và nhu cầu mẫu để demo.

## 2. Chạy thử

1. Mở `SeniorTribeApp` bằng Android Studio, Sync Gradle. `app/google-services.json` là file của project Firebase `seniortribe` (lấy từ nhánh `feat/auth-profile`), và plugin `com.google.gms.google-services` đã được bật trong `app/build.gradle`.
2. Firebase Console → Firestore → Rules: dán nội dung `firebase/firestore.rules` → Publish.
3. Đăng ký 4 tài khoản trong app (email/mật khẩu), ví dụ: đầu mối, điều phối viên, 2 đại diện tổ chức khác.
4. Cấp quyền bằng script (hoặc tạo tay trên Console theo mục 3):
   ```bash
   cd SeniorTribeApp/firebase/seed
   cp demo-accounts.example.json demo-accounts.json   # sửa email cho đúng
   npm install
   node seed-demo.js ./serviceAccount.json ./demo-accounts.json
   ```
   Lấy `serviceAccount.json` ở Project settings → Service accounts → Generate new private key. **Không commit file này.**
5. Luồng demo: đầu mối Ghi nhận → điều phối viên Rà soát → Duyệt → tài khoản đại diện vào "Tìm để tiếp nhận" → Tiếp nhận → (Quyên: chat, lịch) → Trả lại hoặc Hủy.

## 3. Dữ liệu Firestore (dùng cho Chương 4)

| Collection / document | Ai ghi | Nội dung chính |
|---|---|---|
| `memberships/{uid}/organizations/{orgId}` | Quản trị (seed) | `orgName`, `roles: [RECORDER, REVIEWER, REPRESENTATIVE]`, `active` |
| `organizations/{orgId}` | Quản trị | `name`, `area`, `verified` |
| `organizations/{orgId}/members/{uid}` | Quản trị | `displayName`, `roles`, `active` (để chọn người phụ trách) |
| `needs_summary/{needId}` | App | Tóm tắt: `caseCode`, `title`, `topic`, `content`, `area`, `desiredTime`, `source`, `consentRecorded`, `consentMethod`; quy trình: `status`, `needsRevision`, `reviewNote`, `reviewerUid`; đơn vị: `recorderOrgId/Name`, `recorderUid/Name`; tiếp nhận: `assignedOrgId/Name`, `assigneeUid/Name`, `currentAssignmentId`, `returnCount`; hủy: `cancelReason`, `cancelNote`; thời gian: `createdAt`, `updatedAt`, `submittedAt`, `reviewedAt`, `acceptedAt`, `cancelledAt` |
| `needs_private/{needId}` | Đầu mối | `elderlyName`, `contact`, `address`, `recorderOrgId` (cùng id với tóm tắt) |
| `needs_assignments/{assignmentId}` | Đại diện | Mỗi lần tiếp nhận: `needId`, `orgId`, `assigneeUid`, `status: ACTIVE / RETURNED / CLOSED`, `acceptedAt`, `endReason`, `endedAt` |
| `needs_summary/{needId}/events/{eventId}` | App | Lịch sử: `type`, `fromStatus`, `toStatus`, `byUid`, `byName`, `note`, `at` |
| `counters/needs` | App | `next`: số tiếp theo cho mã `NC-0001`, tăng trong transaction nên không trùng |

Danh sách sắp xếp ở máy nên **không cần tạo composite index**.

## 4. Ai được làm gì

| Thao tác | Điều kiện (app kiểm tra, rules kiểm tra lại) |
|---|---|
| Ghi nhận | `RECORDER` của đơn vị; tạo ở `REVIEW` |
| Sửa | `RECORDER` của đơn vị ghi nhận; chỉ khi `REVIEW` hoặc `OPEN`; sửa xong luôn về `REVIEW` |
| Duyệt / Yêu cầu bổ sung | `REVIEWER` của **cùng đơn vị ghi nhận** (rà soát nội bộ, R05), không tự duyệt nhu cầu của mình |
| Xem trong "Tìm để tiếp nhận" | Nhu cầu `OPEN`, chỉ phần tóm tắt |
| Tiếp nhận | `REPRESENTATIVE`; `OPEN → ACCEPTED`; transaction chặn hai tổ chức nhận cùng lúc |
| Xem thông tin riêng | Thành viên đơn vị ghi nhận, hoặc đúng người phụ trách khi đã `SCHEDULED` / `DELIVERED` |
| Trả lại | `REPRESENTATIVE` của tổ chức đang phụ trách; khi `ACCEPTED` / `SCHEDULED`; về `OPEN`, lần tiếp nhận cũ thành `RETURNED` |
| Hủy | `RECORDER` của đơn vị ghi nhận; khi chưa `COMPLETED` / `CANCELLED`; bắt buộc lý do |

## 5. Kiểm thử (T06)

- [ ] Tài khoản chưa có membership → tab Nhu cầu báo "Tài khoản chưa được cấp quyền".
- [ ] Ghi số điện thoại / tên cụ vào Nội dung → form báo lỗi, không gửi.
- [ ] Lưu nháp → mở lại form → phần tóm tắt được khôi phục, thông tin riêng trống.
- [ ] Xoay ngang khi đang nhập → form chia 2 cột, dữ liệu còn nguyên.
- [ ] Điều phối viên chưa tick đủ 4 mục → nút Duyệt bị khóa; Yêu cầu bổ sung mà không ghi chú → báo lỗi.
- [ ] Đầu mối mở nhu cầu bị yêu cầu bổ sung → thấy ghi chú, sửa, gửi lại.
- [ ] Sửa nhu cầu đang `OPEN` → quay về `REVIEW`, biến mất khỏi "Tìm để tiếp nhận".
- [ ] Hai máy cùng bấm Tiếp nhận một nhu cầu → chỉ một máy thành công, máy kia báo đã có tổ chức nhận.
- [ ] Tắt mạng rồi bấm Tiếp nhận / Duyệt / Trả lại → báo cần mạng, trạng thái không đổi.
- [ ] Đại diện xem chi tiết nhu cầu đã nhận nhưng chưa chốt lịch → thông tin riêng vẫn bị che.
- [ ] Trả lại → nhu cầu về `OPEN`, tổ chức khác nhận lại được; `needs_assignments` còn bản `RETURNED`.
- [ ] Hủy → bắt buộc chọn lý do; "Lý do khác" bắt buộc ghi chú.

## 6. Ghi chú cho các bạn trong nhóm

- **Quyên (F11–F13, F15):** chat nên gắn theo `currentAssignmentId` (màn hình chi tiết gửi kèm `Constants.EXTRA_ASSIGNMENT_ID`), vì mỗi lần tiếp nhận có luồng chat riêng (R08). Hàm `coordinationStep()` trong rules là bản đề xuất cho các bước `ACCEPTED → SCHEDULED → DELIVERED → COMPLETED`, bạn chỉnh lại theo field thật của bạn. Khi trả lại một nhu cầu đã `SCHEDULED`, lịch cũ cần coi như hết hiệu lực.
- **Trường (F01–F05, F16):** phần này đọc đúng schema `memberships` của bạn. `SeniorTribeApplication` trên `main` đang bật persistence cache. Bản của bạn dùng `MemoryCacheSettings` thì đúng với T05 hơn (không lưu `needs_private` xuống máy), nên khi ghép code thì giữ bản của bạn.
- **Cả nhóm:** `colors.xml` và `themes.xml` đã đổi sang bộ màu Figma (`#1F6F5C`…) và bổ sung các thuộc tính màu Material 3, nên các màn hình khác cũng đổi màu theo. `NeedSummary` vẫn giữ `setCategory`, `setDistrict`, `setCreatedAt(long)` để code mẫu cũ trong `ChatListFragment` không bị lỗi.

## 7. Giới hạn của bản MVP

- Một tài khoản thuộc nhiều tổ chức thì app dùng tổ chức đầu tiên.
- Mọi tài khoản đã đăng nhập đều đọc được bản tóm tắt `OPEN`. Bản tóm tắt không có thông tin nhận diện; muốn giới hạn chặt theo mạng lưới thì cần thêm custom claims.
- Bộ lọc "Thời gian" lọc theo thời điểm ghi nhận, vì "thời gian mong muốn" là chữ tự do.
