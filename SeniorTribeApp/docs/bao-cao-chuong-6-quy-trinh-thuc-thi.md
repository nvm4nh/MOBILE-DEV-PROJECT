# CHƯƠNG 6. QUY TRÌNH THỰC THI (bản nháp)

> **Người thực hiện:** Khiếu Hoàng Nam Anh (`31241022203`) · nhánh `feat/requests`
> **Trạng thái:** Mục 6.2, 6.3 và phần trả lại / hủy đã khớp với code trên `main`. Mục 6.1, 6.4, 6.5 viết theo phạm vi chốt và Security Rules. Cần đối chiếu lại khi nhánh `feat/auth-profile` (Trường) và `feat/chat-schedule` (Quyên) được merge, rồi mới chụp hình.
> Các vị trí `[Hình 6.x]` là chỗ chèn ảnh chụp màn hình khi ghép vào file Word. Theo yêu cầu của thầy, ảnh phải có chú thích và đánh số theo chương.

Chương này mô tả cách các tác nhân dùng SeniorTribe để đưa một nhu cầu hỗ trợ đi từ lúc được ghi nhận đến khi hoàn thành. Toàn bộ vòng đời trạng thái đã trình bày ở Mục 2.1 (Hình 2.1, Hình 2.2). Mỗi quy trình dưới đây gồm: mục tiêu, tác nhân, các bước thao tác trên app, dữ liệu Firestore được đọc/ghi và các tình huống ngoại lệ.

---

## 6.1. Quy trình đăng ký và đăng nhập tài khoản

**Mục tiêu:** Xác thực người dùng và xác định quyền tổ chức trước khi cho phép thao tác nghiệp vụ.
**Tác nhân:** Mọi người dùng (đầu mối, điều phối viên, đại diện tổ chức).

**Các bước:**
1. Mở app, màn hình khởi động (`SplashActivity`) kiểm tra phiên đăng nhập qua `FirebaseAuth.getCurrentUser()`. Nếu đã đăng nhập thì vào thẳng màn hình chính, nếu chưa thì chuyển sang màn hình Đăng nhập. `[Hình 6.1: Màn hình khởi động]`
2. Người dùng mới chọn **Đăng ký**, nhập họ tên, email, mật khẩu. App gọi `createUserWithEmailAndPassword()` và tạo hồ sơ `users/{uid}`. `[Hình 6.2: Màn hình đăng ký]`
3. Người dùng đã có tài khoản đăng nhập bằng email/mật khẩu (`signInWithEmailAndPassword()`) hoặc Google Sign-In kết hợp Firebase Authentication (T03). `[Hình 6.3: Màn hình đăng nhập]`
4. Quên mật khẩu: nhập email, app gửi thư đặt lại qua `sendPasswordResetEmail()`.
5. Sau khi đăng nhập, `MembershipRepository` đọc `memberships/{uid}/organizations/{orgId}` để biết tài khoản thuộc tổ chức nào và có quyền `RECORDER`, `REVIEWER` hay `REPRESENTATIVE`.
6. Nếu chưa được cấp quyền, tab Nhu cầu hiển thị trạng thái *"Tài khoản chưa được cấp quyền"*, và người dùng không tạo, duyệt hay tiếp nhận được nhu cầu. `[Hình 6.4: Trạng thái chưa được cấp quyền]`

**Điểm cần lưu ý:** Đăng nhập thành công không tự cấp quyền tổ chức. Quyền do quản trị cấp sẵn trong cơ sở dữ liệu, và giao diện không có ô chọn vai trò (R01).

---

## 6.2. Quy trình ghi nhận và rà soát nhu cầu

**Mục tiêu:** Đưa một nhu cầu thực tế của người cao tuổi vào hệ thống, có kiểm tra nội bộ trước khi chia sẻ ra mạng lưới.
**Tác nhân:** Đầu mối cộng đồng (`RECORDER`), điều phối viên (`REVIEWER`) cùng đơn vị.

**Các bước của đầu mối (F06):**
1. Tại tab Nhu cầu, chọn **Ghi nhận nhu cầu**. Form gồm 3 phần: `[Hình 6.5: Form ghi nhận – màn hình dọc]`
   - **Tóm tắt** (sẽ chia sẻ trong mạng lưới): tiêu đề, chủ đề, nội dung, khu vực ở mức phường, thời gian mong muốn, nguồn ghi nhận.
   - **Thông tin riêng** (chỉ đơn vị ghi nhận xem): họ tên người cao tuổi, liên hệ, địa chỉ.
   - **Sự đồng ý:** xác nhận đã hỏi ý kiến người cao tuổi và cách hỏi.
2. Khi bấm Gửi, `PrivacyGuard` kiểm tra phần tóm tắt. Nếu có chuỗi từ 9 chữ số (số điện thoại) hoặc có tên người cao tuổi, form báo lỗi tại ô tương ứng và không gửi.
3. Hệ thống chạy một transaction: đọc `counters/needs` để sinh mã `NC-xxxx` không trùng, rồi ghi đồng thời `needs_summary/{id}` (trạng thái `REVIEW`), `needs_private/{id}` và một sự kiện lịch sử.
4. Nếu mất mạng, đầu mối có thể **Lưu nháp**. `NeedDraftStore` chỉ lưu phần tóm tắt, không lưu thông tin riêng xuống máy (T05).
5. Khi xoay ngang, form chuyển sang bố cục 2 cột (`layout-land`) và giữ nguyên dữ liệu đang nhập nhờ `NeedFormViewModel` (T04). `[Hình 6.6: Form ghi nhận – màn hình ngang]`

**Các bước của điều phối viên (F07):**
6. Mở nhu cầu ở trạng thái Chờ rà soát, màn hình `ReviewNeedActivity` hiển thị 4 tiêu chí cần tick: tóm tắt không chứa tên/địa chỉ/số điện thoại; có nguồn ghi nhận rõ ràng; đã ghi nhận sự đồng ý và cách hỏi ý kiến; khu vực chỉ ở mức phường. `[Hình 6.7: Màn hình rà soát]`
7. Nút **Duyệt** chỉ bật khi đủ 4 tiêu chí. Duyệt chuyển nhu cầu sang `OPEN`.
8. Nếu thiếu thông tin, chọn **Yêu cầu bổ sung** và bắt buộc ghi chú. Nhu cầu vẫn ở `REVIEW`, gắn cờ `needsRevision`.
9. Đầu mối thấy ghi chú bổ sung, sửa và gửi lại (F08). Nếu sửa một nhu cầu đã `OPEN`, nhu cầu tự quay về `REVIEW` và rời khỏi danh sách tìm kiếm cho tới khi được duyệt lại.

**Ngoại lệ:**
- Điều phối viên cũng là người ghi nhận nhu cầu đó: hệ thống báo *"Bạn không thể tự duyệt nhu cầu do chính mình ghi nhận"*.
- Điều phối viên của đơn vị khác: không thấy nhu cầu và cũng bị Security Rules chặn ghi.

---

## 6.3. Quy trình tiếp nhận hỗ trợ

**Mục tiêu:** Một tổ chức nhận phụ trách một nhu cầu, đảm bảo không có hai tổ chức cùng nhận (R04).
**Tác nhân:** Đại diện tổ chức hỗ trợ (`REPRESENTATIVE`).

**Các bước (F09, F10):**
1. Vào tab Nhu cầu → **Tìm để tiếp nhận**. Danh sách chỉ gồm các nhu cầu `OPEN`, cập nhật realtime qua snapshot listener. `[Hình 6.8: Danh sách tìm để tiếp nhận]`
2. Lọc theo khu vực, chủ đề, thời gian; gõ từ khóa có dấu hoặc không dấu (`VnText` chuẩn hóa tiếng Việt). Danh sách hiển thị đủ 4 trạng thái: đang tải, trống, lỗi, chưa có quyền (`StateView`).
3. Mở chi tiết nhu cầu: chỉ thấy phần tóm tắt, thông tin riêng bị che. `[Hình 6.9: Chi tiết nhu cầu – thông tin riêng bị che]`
4. Bấm **Tiếp nhận**, hộp thoại `AcceptNeedDialog` cho chọn đại diện phụ trách trong tổ chức. `[Hình 6.10: Hộp thoại chọn người phụ trách]`
5. Hệ thống chạy transaction:
   - Đọc lại `needs_summary/{id}` trên máy chủ.
   - Nếu trạng thái không còn `OPEN` hoặc đã có `currentAssignmentId`, hủy giao dịch và báo lỗi.
   - Nếu hợp lệ: tạo bản ghi lần tiếp nhận `needs_assignments/{assignmentId}` (trạng thái `ACTIVE`), cập nhật nhu cầu sang `ACCEPTED` với tổ chức, người phụ trách và `currentAssignmentId`, và ghi sự kiện lịch sử.
6. Nhu cầu chuyển vào danh sách của tổ chức. Mã `currentAssignmentId` được truyền sang màn hình chat để mở luồng trao đổi riêng cho lần tiếp nhận này (R08).

**Ngoại lệ:**
- **Hai tổ chức bấm cùng lúc:** chỉ một transaction thành công. Máy còn lại nhận thông báo *"Nhu cầu này vừa được tổ chức khác tiếp nhận hoặc không còn chờ tiếp nhận"*. `[Hình 6.11: Thông báo nhận trùng]`
- **Mất mạng:** app chặn thao tác và báo cần kết nối để máy chủ xác nhận, không báo thành công giả.
- **Sau khi tiếp nhận:** người phụ trách vẫn chưa đọc được họ tên, địa chỉ, liên hệ của người cao tuổi cho tới khi lịch được chốt (R03).

---

## 6.4. Quy trình phối hợp qua chat và thống nhất lịch

**Mục tiêu:** Đầu mối và người phụ trách trao đổi phương án, chốt lịch hỗ trợ có sự đồng ý của người cao tuổi.
**Tác nhân:** Đầu mối ghi nhận, đại diện được giao phụ trách; người cao tuổi tham gia gián tiếp qua đầu mối.

**Các bước (F11, F12):**
1. Từ chi tiết nhu cầu `ACCEPTED`, cả hai bên mở **Chat**. Phòng chat gắn với `currentAssignmentId`; thành viên khác của tổ chức chưa được vào chat trong MVP. `[Hình 6.12: Màn hình chat – dọc]`
2. Tin nhắn văn bản được gửi và nhận realtime qua Firestore. Khi xoay ngang, nội dung đang gõ không bị mất. `[Hình 6.13: Màn hình chat – ngang]`
3. Đại diện tổ chức **đề xuất lịch** (ngày, giờ, người thực hiện).
4. Đầu mối trao đổi với người cao tuổi. Nếu cụ đồng ý, đầu mối xác nhận lịch, nhu cầu chuyển sang `SCHEDULED`. Nếu không đồng ý, hai bên tiếp tục trao đổi để đề xuất lịch khác. `[Hình 6.14: Đề xuất và xác nhận lịch]`
5. Khi lịch đã chốt, người phụ trách được đọc thông tin riêng cần thiết để đến hỗ trợ (Rules chỉ cho phép khi trạng thái là `SCHEDULED` hoặc `DELIVERED`).

**Trả lại nhu cầu (F14):** Ở bước `ACCEPTED` hoặc `SCHEDULED`, nếu tổ chức không thực hiện được, đại diện chọn **Trả lại**, bắt buộc chọn lý do (không sắp xếp được người, lịch không phù hợp, vượt khả năng, hoặc lý do khác kèm ghi chú). Nhu cầu quay về `OPEN` để tổ chức khác nhận, `returnCount` tăng 1. Lần tiếp nhận cũ chuyển `RETURNED` nhưng vẫn lưu lại (R07), luồng chat cũ chỉ còn đọc, và lịch cũ hết hiệu lực. `[Hình 6.15: Bảng chọn lý do trả lại]`

---

## 6.5. Quy trình báo hỗ trợ và đối soát kết quả

**Mục tiêu:** Ghi nhận việc hỗ trợ đã thực hiện và xác nhận kết quả với người cao tuổi trước khi đóng nhu cầu.
**Tác nhân:** Tổ chức phụ trách, đầu mối ghi nhận.

**Các bước (F13, F15):**
1. Sau buổi hỗ trợ, thành viên tổ chức phụ trách bấm **Báo đã hỗ trợ**, nhu cầu chuyển sang `DELIVERED`. `[Hình 6.16: Báo đã hỗ trợ]`
2. Đầu mối liên hệ người cao tuổi để đối soát kết quả.
3. Nếu thống nhất, đầu mối **Xác nhận hoàn thành**, nhu cầu chuyển sang `COMPLETED`, chat chuyển sang chỉ đọc. `[Hình 6.17: Xác nhận hoàn thành]`
4. Nếu chưa thống nhất, nhu cầu giữ ở `DELIVERED` và đầu mối ghi nội dung cần đối soát qua chat.
5. Lịch và lịch sử hỗ trợ (F15) cho phép xem lại lịch đã thống nhất, người phụ trách, các lần tiếp nhận (kể cả lần đã trả lại) và kết quả, theo quyền của từng người. `[Hình 6.18: Lịch sử hỗ trợ]`

**Hủy nhu cầu (F14):** Ở bất kỳ trạng thái nào trước `COMPLETED`, đầu mối có thể **Hủy** khi người cao tuổi không còn nhu cầu, không đồng ý tiếp tục, hoặc đã được gia đình/đơn vị khác hỗ trợ. Lý do là bắt buộc; chọn "Lý do khác" thì phải ghi chú. Nhu cầu chuyển sang `CANCELLED`, lần tiếp nhận đang chạy (nếu có) chuyển `CLOSED`. Tổ chức hỗ trợ không có quyền hủy, chỉ được trả lại, để nhu cầu chưa giải quyết không bị tự đóng. `[Hình 6.19: Bảng chọn lý do hủy]`

**Lưu ý (R09):** `COMPLETED` chỉ đóng một nhu cầu cụ thể. Người cao tuổi có thể phát sinh nhu cầu mới, và đầu mối ghi nhận lại từ Quy trình 6.2.

---

## Ghi chú cho nhóm (xóa khi ghép vào báo cáo)

- **6.1:** Trên `main` hiện tại, nút Google Sign-In trong `LoginActivity` đang vào thẳng `MainActivity` ở chế độ demo, chưa xác thực thật. Cần bản của Trường (`feat/auth-profile`) trước khi chụp Hình 6.3.
- **6.4, 6.5:** `ChatActivity` và `ScheduleFragment` trên `main` vẫn còn dữ liệu mẫu. Các bước trên viết theo phạm vi F11–F13 và hàm `coordinationStep()` trong `firestore.rules`. Khi Quyên merge `feat/chat-schedule`, cần sửa tên nút, field Firestore cho khớp rồi mới chụp hình.
- Ảnh chụp nên dùng dữ liệu tạo bằng `firebase/seed/seed-demo.js` để không lộ thông tin thật.
