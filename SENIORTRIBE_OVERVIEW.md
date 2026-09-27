# SeniorTribe

## 1. Mô tả bài toán

### 1.1. Target Audience

| **Nhóm người dùng**            | **Đối tượng**                                                                                                                                                         | **Nhu cầu chính**                                                                               |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Người dùng chính**           | Người cao tuổi, đặc biệt là người sống một mình hoặc ít có cơ hội giao tiếp                                                                                           | Trò chuyện, kết nối bạn bè, tham gia cộng đồng, chia sẻ hoạt động và tìm kiếm sự hỗ trợ khi cần |
| **Người dùng chính**           | Người đồng hành / Tình nguyện viên, như sinh viên và người trẻ muốn hỗ trợ cộng đồng                                                                                  | Tìm người cần hỗ trợ, kết nối, trò chuyện và tiếp nhận các yêu cầu hỗ trợ phù hợp               |
| **Người dùng phụ / liên quan** | Người thân ở xa muốn theo dõi và hỗ trợ ông bà, cha mẹ                                                                                                                | Theo dõi hoạt động, trao đổi và nhận thông báo khi cần thiết                                    |
| **Đối tượng liên quan**        | Tổ chức xã hội / cộng đồng: Hội Người cao tuổi, Đoàn Thanh niên phường, Hội Chữ thập đỏ, CLB Người cao tuổi, CLB tình nguyện, Nhà văn hóa, tổ chức phi lợi nhuận khác | Tổ chức hoạt động cộng đồng và kết nối nguồn lực hỗ trợ người cao tuổi                          |
| **Người quản trị**             | Admin                                                                                                                                                                 | Quản lý người dùng, nội dung, yêu cầu hỗ trợ và duy trì hoạt động của hệ thống                  |

### 1.2. Problem cần Solving

#### Vấn đề 1: Người cao tuổi thiếu kết nối và giao tiếp

**Pain Points:**

* Không có người thường xuyên trò chuyện.
* Khó tìm những người có cùng sở thích hoặc hoàn cảnh.
* Ít cơ hội tham gia các hoạt động cộng đồng.
* Có nhu cầu chia sẻ nhưng không biết kết nối với ai.

#### Vấn đề 2: Người cao tuổi gặp khó khăn trong các công việc hằng ngày

Một số người cao tuổi có thể cần hỗ trợ đối với những công việc như:

* Đi chợ.
* Đi khám.
* Sửa chữa một số vật dụng.
* Đóng tiền điện, nước.
* Một số công việc sinh hoạt khác.

**Pain Points:**

* Có nhu cầu hỗ trợ nhưng không biết tìm người giúp ở đâu.
* Người thân có thể ở xa và không thể hỗ trợ trực tiếp.
* Người muốn giúp đỡ lại không biết ai đang cần hỗ trợ.

#### Vấn đề 3: Khó tìm người hỗ trợ phù hợp và đáng tin cậy

Người cao tuổi có thể không biết tìm người hỗ trợ ở đâu, trong khi sinh viên và người trẻ muốn tình nguyện lại khó biết ai đang cần hỗ trợ và hỗ trợ vấn đề gì.

**Pain Points:**

* Người cần giúp không biết tìm người hỗ trợ.
* Người muốn giúp không biết ai đang cần.
* Khó trao đổi trước khi thực hiện hỗ trợ.
* Khó theo dõi trạng thái của một yêu cầu hỗ trợ.

#### Vấn đề 4: Rào cản công nghệ đối với người cao tuổi

Một trong những vấn đề quan trọng là người cao tuổi có thể gặp khó khăn khi sử dụng các ứng dụng có giao diện phức tạp.

**Pain Points:**

* Chữ nhỏ.
* Nhiều nút và nhiều bước thao tác.
* Khó tìm chức năng cần sử dụng.
* Khó nhập nội dung bằng bàn phím.
* Khó ghi nhớ quy trình sử dụng.

#### Vấn đề 5: Người thân ở xa khó duy trì kết nối

Người thân có thể không sống cùng hoặc không thường xuyên ở gần người cao tuổi nên khó biết được hoạt động và nhu cầu của họ.

**Pain Points:**

* Không thể thường xuyên trò chuyện trực tiếp.
* Khó biết người thân đang tham gia hoạt động gì.
* Có thể bỏ lỡ những thông tin hoặc yêu cầu hỗ trợ quan trọng.

---

## 2. Xây dựng chức năng

### 2.1. Danh sách chức năng

| **Nhóm chức năng**      | **Bản demo (MVP)**                                                                        | **Tính năng mở rộng (Extension)**                      |
| ----------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| **Tài khoản**           | Đăng ký, đăng nhập, đăng xuất, quên mật khẩu, Google Login                                | Xác thực nâng cao                                      |
| **Hồ sơ**               | Avatar, thông tin cá nhân, sở thích, chỉnh sửa hồ sơ                                      | Hồ sơ chi tiết hơn                                     |
| **Giao diện dễ dùng**   | Chữ lớn, bố cục đơn giản, nút lớn và rõ ràng, Dark Mode, điều chỉnh được cỡ chữ           | Tùy chỉnh giao diện sâu hơn                            |
| **Bảng tin**            | Xem danh sách bài viết, xem nội dung theo thời gian                                       | Cá nhân hóa nội dung                                   |
| **Đăng bài**            | Đăng text + hình ảnh, chỉnh sửa/xóa bài                                                   | Video, nội dung đa phương tiện                         |
| **Tương tác**           | Like, comment, share bài viết                                                             | Reaction đa dạng, chia sẻ nâng cao                     |
| **Kết nối**             | Tìm người, xem profile, kết nối (gửi/chấp nhận kết nối)                                   | Gợi ý người phù hợp                                    |
| **Nhóm & sở thích**     | Xem nhóm theo sở thích, tham gia nhóm, bài viết trong nhóm                                | Nhóm chuyên sâu, quản trị nhóm                         |
| **Chat**                | Chat 1-1, gửi tin nhắn realtime                                                           | Gọi thoại/video, gửi file                              |
| **Hỗ trợ**              | Tạo yêu cầu hỗ trợ, xem danh sách yêu cầu hỗ trợ, xem chi tiết yêu cầu, tiếp nhận yêu cầu | Matching tự động người cần hỗ trợ – người đồng hành    |
| **Loại hỗ trợ**         | Trò chuyện, đồng hành, hỗ trợ sinh hoạt, mô tả nhu cầu khác                               | Phân loại chi tiết hơn                                 |
| **Trạng thái hỗ trợ**   | Mới tạo → Đã tiếp nhận → Đang hỗ trợ → Hoàn tất                                           | Lịch sử và đánh giá hỗ trợ                             |
| **Hoạt động cộng đồng** | Xem hoạt động và thông tin của hoạt động                                                  | Đăng ký sự kiện, nhắc lịch                             |
| **Thông báo**           | Tin nhắn, like, comment, kết nối, hỗ trợ                                                  | Thông báo cá nhân hóa                                  |
| **Tìm kiếm**            | Tìm người dùng/bài viết, tìm nhóm/hoạt động                                               | Tìm kiếm nâng cao                                      |
| **AI Assistant**        | Chatbot hướng dẫn sử dụng app, hỗ trợ viết nội dung đơn giản                              | Voice Input, Text-to-Speech, gợi ý hoạt động và hỗ trợ |
| **Admin Mobile**        | Quản lý user, bài viết, yêu cầu hỗ trợ                                                    | Báo cáo/thống kê nâng cao                              |
| **Firebase**            | Authentication + Firestore + Realtime Database + Storage                                  | Firebase Cloud Messaging, Analytics                    |
| **Offline**             | Cache một số dữ liệu đã xem                                                               | Đồng bộ dữ liệu nâng cao                               |
| **Responsive Mobile**   | Portrait + Landscape                                                                      | Tối ưu thêm cho nhiều kích thước màn hình              |

---

## 3. Link trang web tham khảo

1. [The Social Community for Anyone Over 50 - Stitch](https://www.stitch.net/)
2. [Senior Planet Community - Make friends. Talk openly](https://community.seniorplanet.org/)
3. [Senior Planet from AARP](https://seniorplanet.org/)
4. [Papa | Companion Care for Older Adults & Families](https://www.papa.com/)
5. [GetSetUp Free - GetSetUp](https://www.getsetup.io/en-US/partner/getsetup-free)
