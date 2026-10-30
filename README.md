# SeniorTribe — Ứng dụng Android kết nối và hỗ trợ người cao tuổi neo đơn

[![Platform](https://img.shields.io/badge/Platform-Android%20Native-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java%20%7C%20XML-ED8B00.svg?style=flat&logo=openjdk&logoColor=white)]()
[![Backend](https://img.shields.io/badge/Backend-Firebase%20Firestore-FFCA28.svg?style=flat&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Local DB](https://img.shields.io/badge/Local%20DB-SQLite-003B57.svg?style=flat&logo=sqlite&logoColor=white)]()
[![Design](https://img.shields.io/badge/Design-Figma-F24E1E.svg?style=flat&logo=figma&logoColor=white)](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1)

SeniorTribe là ứng dụng điều phối hỗ trợ giữa **đầu mối cộng đồng** (ghi nhận nhu cầu của người cao tuổi), **điều phối viên** (rà soát trước khi công khai) và **đại diện tổ chức xã hội** (tiếp nhận, hẹn lịch, thực hiện và báo kết quả). Người cao tuổi là người thụ hưởng, có quyền đồng ý hoặc từ chối và không cần có tài khoản.

| | |
|:---|:---|
| **Học phần** | Phát triển ứng dụng Mobile |
| **Đơn vị** | Trường Công nghệ và Thiết kế — Đại học Kinh tế TP. Hồ Chí Minh (UEH), Khóa K50 |
| **Giảng viên hướng dẫn** | TS. Đặng Ngọc Hoàng Thành |
| **Thiết kế giao diện** | [Figma SeniorTribe](https://www.figma.com/design/joOcxsdJHhw9Dm8O4XIGUi/Untitled?node-id=0-1&t=JtOY4b3zqACJ78iL-1) |

---

## 1. Thành viên và phân công

Mỗi thành viên phụ trách một nhóm chức năng trọn vẹn, từ giao diện, ViewModel, Repository đến Security Rules của phần đó. Cột **Minh chứng** dẫn tới các pull request đã merge vào `main`; tác giả commit trong từng PR là người phụ trách tương ứng.

### 1.1. Phần mã nguồn

| Thành viên | MSSV | Chức năng phụ trách | Nhánh | Minh chứng (PR đã merge) |
|:---|:---:|:---|:---|:---|
| **Ông Tố Quyên** *(Trưởng nhóm)* | 31231024412 | **Bảng tin và offline:** bảng tin cộng đồng (F04), đăng / sửa / ẩn bài của tổ chức (F05), giao diện chung và lưu bài đã tải bằng SQLite (F16)<br>**Mở rộng:** nhập giọng nói (E04), trợ lý gợi ý (E05) | `feat/bang-tin-f04-f05-f16-v2`<br>`feat/voice-input-ai-assistant-v2` | [#8](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/8), [#14](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/14), [#17](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/17) |
| **Khiếu Hoàng Nam Anh** | 31241022203 | **Khung dự án và phối hợp hỗ trợ:** khung Android chung; chat phối hợp (F11), thống nhất lịch (F12), báo và đối soát kết quả (F13), lịch và lịch sử hỗ trợ (F15); siết Security Rules và bộ kiểm thử rules<br>**Mở rộng:** nhắc lịch và thông báo (E01), đổi lịch (E02), chat nâng cao: ảnh / tệp, gọi thoại / video (E06) | `feat/chat-schedule`<br>`feat/coordination-figma`<br>`feat/notifications-reschedule-chat`<br>`fix/bang-tin-va-rules` | [#7](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/7), [#9](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/9), [#15](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/15), [#22](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/22) |
| **Lê Trọng Hiếu** | 31231025382 | **Nghiệp vụ nhu cầu:** ghi nhận (F06), rà soát (F07), nhu cầu của đơn vị (F08), tìm và lọc (F09), tiếp nhận bằng transaction (F10), hủy / trả lại (F14); Security Rules bản đầu, dữ liệu demo<br>**Mở rộng:** đánh giá và xử lý phản ánh (E03), sự kiện cộng đồng (E07) | `feat/requests`<br>`ix/f14-tra-lai-xoa-lich`<br>`feat/feedback-groups-events` | [#6](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/6), [#12](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/12), [#18](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/18) |
| **Nguyễn Văn Trường** | 31221025476 | **Tài khoản và phân quyền:** đăng ký, đăng nhập email / Google, quên mật khẩu (F01), phân quyền theo tổ chức (F02), hồ sơ tài khoản và đổi email có xác minh (F03)<br>**Mở rộng:** tìm kiếm chung, cỡ chữ và dark mode (E08), màn hình khẩn cấp SOS (E09) | `feat/auth-profile`<br>`feat/search-ui-sos-v2` | [#11](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/11), [#20](https://github.com/nvm4nh/MOBILE-DEV-PROJECT/pull/20) |

### 1.2. Phần báo cáo và thuyết trình

Báo cáo theo mẫu `Mau Do An - Mobile v2.docx`. Ở Chương 5 và Chương 7, mỗi người viết phần chức năng mình làm.

| Thành viên | Phần báo cáo | Thuyết trình, kiểm thử |
|:---|:---|:---|
| **Ông Tố Quyên** | Chương 5: cấu trúc ứng dụng (5.1), bảng tin và SQLite offline; Chương 7: E04, E05 | Slide, điều phối demo 5 phút |
| **Khiếu Hoàng Nam Anh** | Mục 2.1 Workflow Diagram; Chương 5: chat, lịch, kết quả; Chương 6 Quy trình thực thi; Chương 7: E01, E02, E06 | Demo kỹ thuật, kiểm thử rules |
| **Lê Trọng Hiếu** | Mục 2.2 BFD; Chương 4 Cơ sở dữ liệu và truy vấn; Chương 5: nhu cầu; Chương 7: E03, E07; Phụ lục cài đặt | Chuẩn bị dữ liệu demo |
| **Nguyễn Văn Trường** | Chương 1 Tổng quan; Chương 3 Thiết kế giao diện; Chương 5: tài khoản, phân quyền; Chương 7: E08, E09 | Kiểm tra Turnitin (< 20%), kiểm thử xoay màn hình |

### 1.3. Quy trình làm việc trên GitHub

1. Mỗi đầu việc làm trên một nhánh riêng tạo từ `main` mới nhất.
2. Commit bằng email đã xác minh trên GitHub để đóng góp được tính đúng người.
3. Mở pull request vào `main`, ghi chức năng đã làm và cách kiểm thử; một thành viên khác review.
4. Chỉ merge khi `assembleDebug` build được và bộ kiểm thử rules chạy qua.
5. Sau khi merge, mọi người pull `main` trước khi làm đầu việc tiếp theo.

Kế hoạch và timeline chi tiết: [Phân công.md](Phân%20công.md).

---

## 2. Chức năng

**Chức năng chính (F01–F16):** chi tiết trong [Phạm vi chức năng.md](Phạm%20vi%20chức%20năng.md).

| Nhóm | Mã |
|:---|:---|
| Tài khoản, phân quyền, hồ sơ | F01–F03 |
| Bảng tin, đăng bài | F04, F05 |
| Ghi nhận, rà soát, tìm lọc, tiếp nhận, hủy / trả lại nhu cầu | F06–F10, F14 |
| Chat, thống nhất lịch, đối soát kết quả, lịch sử | F11–F13, F15 |
| Giao diện, xoay màn hình, offline | F16 |

**Chức năng mở rộng đã làm:** E01 nhắc lịch và thông báo · E02 đổi lịch · E03 đánh giá và phản ánh · E04 nhập giọng nói · E05 trợ lý gợi ý · E06 gửi ảnh / tệp, gọi thoại / video · E07 sự kiện cộng đồng · E08 tìm kiếm chung, cỡ chữ, dark mode · E09 SOS.

### Luồng trạng thái nhu cầu

```
REVIEW → OPEN → ACCEPTED → SCHEDULED → DELIVERED → COMPLETED
 rà soát  chờ     đã tiếp     đã chốt      chờ đối      hoàn
          nhận    nhận        lịch         soát         thành
```

* Điều phối viên có thể yêu cầu bổ sung, nhu cầu ở lại `REVIEW` cho đầu mối sửa.
* Tổ chức không thực hiện được thì trả lại, nhu cầu về `OPEN`, bắt buộc ghi lý do.
* Đầu mối có thể hủy (`CANCELLED`) khi nhu cầu chưa hoàn thành, bắt buộc ghi lý do.

---

## 3. Kỹ thuật chính

* **Firebase Authentication:** email / mật khẩu và Google Sign-In.
* **Cloud Firestore:** snapshot listener cho cập nhật realtime; mọi bước đổi trạng thái nhu cầu chạy trong transaction, nên hai tổ chức bấm tiếp nhận cùng lúc chỉ một bên thành công.
* **Quyền riêng tư:** nhu cầu tách làm hai document. `needs_summary` chia sẻ trong mạng lưới; `needs_private` (họ tên, liên hệ, địa chỉ) chỉ đơn vị ghi nhận đọc được, đại diện được giao chỉ đọc được sau khi lịch đã chốt (`SCHEDULED`, `DELIVERED`).
* **Security Rules:** kiểm tra lại vai trò, trạng thái và lần tiếp nhận ở phía máy chủ; có bộ kiểm thử trên Firebase Emulator.
* **Responsive:** bố cục dọc (`layout/`) và ngang (`layout-land/`); ViewModel giữ dữ liệu khi xoay màn hình.
* **Offline:** SQLite lưu bài đăng đã tải để đọc khi mất mạng; không lưu thông tin riêng của người cao tuổi hay nội dung chat xuống thiết bị.
* **Kiến trúc:** MVVM kết hợp Repository.

---

## 4. Cài đặt và chạy

### Yêu cầu

* Android Studio, **JDK 17** cho Gradle (Gradle 8.2 không chạy với JDK 21 trở lên).
* Android SDK API 34; ứng dụng chạy từ Android 7.0 (API 24).
* Node.js 20+ cho dữ liệu demo và kiểm thử rules; Java 21+ để chạy Firebase Emulator.

### Chạy ứng dụng

1. Clone repository:
   ```bash
   git clone https://github.com/nvm4nh/MOBILE-DEV-PROJECT.git
   ```
2. Mở thư mục `SeniorTribeApp` bằng Android Studio.
3. Vào `Settings → Build, Execution, Deployment → Build Tools → Gradle`, chọn **Gradle JDK** là JDK 17.
4. **Sync Project with Gradle Files.** Tệp `app/google-services.json` đã có sẵn.
5. Chạy trên emulator (API 30+) hoặc máy thật đã bật USB Debugging bằng **Run 'app'**.

Nếu gặp lỗi `Unsupported class file major version`, Gradle đang dùng JDK quá mới; chọn lại JDK 17 ở bước 3.

### Dữ liệu demo

```bash
cd SeniorTribeApp/firebase/seed
cp demo-accounts.example.json demo-accounts.json   # điền email 4 tài khoản demo
npm install
node seed-demo.js ./serviceAccount.json ./demo-accounts.json          # lần đầu
node seed-demo.js ./serviceAccount.json ./demo-accounts.json --reset  # làm mới trước buổi demo
```

`serviceAccount.json` là khóa quản trị Firebase, **không commit** lên GitHub.

### Kiểm thử Security Rules

```bash
cd SeniorTribeApp/firebase/tests
npm install
npm test
```

---

## 5. Cấu trúc thư mục

```
SeniorTribeApp/
├── app/src/main/java/com/seniortribe/app/
│   ├── activities/, fragments/, dialogs/   Giao diện
│   ├── viewmodels/                          Trạng thái màn hình
│   ├── data/                                Repository (Firestore, SQLite)
│   ├── models/, utils/                      Đối tượng dữ liệu, tiện ích
│   └── notifications/, calls/, sos/, ...    Chức năng mở rộng
├── firebase/
│   ├── firestore.rules                      Security Rules
│   ├── seed/                                Dữ liệu demo
│   └── tests/                               Kiểm thử rules
└── docs/                                    Tài liệu từng nhóm chức năng
```

---

## 6. Tài liệu

| Tài liệu | Nội dung |
|:---|:---|
| [Tổng quan.md](Tổng%20quan.md) | Bài toán, đối tượng sử dụng, cơ sở thực tiễn |
| [Phạm vi chức năng.md](Phạm%20vi%20chức%20năng.md) | F01–F16, E01–E09, trạng thái, quy tắc nghiệp vụ |
| [Phân công.md](Phân%20công.md) | Kế hoạch, timeline, mốc bàn giao |
| [KE_HOACH_BAO_CAO.md](KE_HOACH_BAO_CAO.md) | Đề cương 7 chương báo cáo |
| [docs/tai-khoan-F01-F02-F03.md](SeniorTribeApp/docs/tai-khoan-F01-F02-F03.md) | Tài khoản, phân quyền, hồ sơ |
| [docs/nhu-cau-F06-F10-F14.md](SeniorTribeApp/docs/nhu-cau-F06-F10-F14.md) | Nghiệp vụ nhu cầu, schema, ca kiểm thử |
| [docs/phoi-hop-F11-F15.md](SeniorTribeApp/docs/phoi-hop-F11-F15.md) | Chat, lịch, kết quả, lịch sử |
| [docs/mo-rong-E01-E02-E06.md](SeniorTribeApp/docs/mo-rong-E01-E02-E06.md) | Thông báo, đổi lịch, chat nâng cao |
| [docs/mo-rong-E08-E09.md](SeniorTribeApp/docs/mo-rong-E08-E09.md) | Tìm kiếm, giao diện nâng cao, SOS |
