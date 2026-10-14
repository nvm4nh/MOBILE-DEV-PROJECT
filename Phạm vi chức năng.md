# ⚙️ Phạm Vi Chức Năng — SeniorTribe

---

## 1. CHỨC NĂNG CƠ BẢN (MVP)

| Mã | Chức năng | Phạm vi chốt |
|:---:|:---|:---|
| **F01** | Tài khoản | Đăng ký bằng email, đăng nhập email/Google, quên mật khẩu qua email và đăng xuất. Tài khoản mới chưa có quyền tổ chức cho đến khi được cấp quyền. |
| **F02** | Phân quyền theo tổ chức | Đầu mối ghi nhận, điều phối viên rà soát và đại diện tổ chức hỗ trợ. Quyền được cấp trước theo đơn vị; người dùng không tự chọn để nâng quyền. |
| **F03** | Hồ sơ tài khoản và tổ chức | Sửa tên hiển thị và khu vực của tài khoản. Xem hồ sơ tổ chức có dữ liệu cấp sẵn. Hồ sơ người được hỗ trợ được quản lý theo phạm vi nhu cầu và quyền truy cập. |
| **F04** | Bảng tin cộng đồng | Xem danh sách và chi tiết bài đăng của tổ chức. Nội dung văn bản; không mặc định đăng thông tin riêng của người được hỗ trợ lên bảng tin. |
| **F05** | Đăng bài trên mobile | Đại diện có quyền tạo, sửa và ẩn bài của tổ chức mình. Không sửa bài của tổ chức khác. |
| **F06** | Ghi nhận nhu cầu | Đầu mối tạo nhu cầu cho người cao tuổi: mã trường hợp, nội dung, khu vực rộng, thời gian mong muốn, nguồn ghi nhận và thông tin về sự đồng ý. Gửi vào REVIEW. Dữ liệu liên hệ và bằng chứng đồng ý lưu riêng. |
| **F07** | Rà soát nhu cầu | Điều phối viên được giao kiểm tra nội dung, nguồn ghi nhận và phạm vi chia sẻ; duyệt sang OPEN hoặc yêu cầu bổ sung trong REVIEW. Chỉ nhu cầu đã duyệt mới xuất hiện cho tổ chức tiếp nhận. |
| **F08** | Nhu cầu của đơn vị | Xem và theo dõi nhu cầu thuộc đơn vị theo quyền. Đầu mối sửa khi chưa được nhận. Nếu sửa nội dung đã duyệt ở OPEN, đưa về REVIEW để rà soát lại. |
| **F09** | Tìm và lọc nhu cầu | Tổ chức tìm nhu cầu đã duyệt, đang ở OPEN theo khu vực/chủ đề. Tìm từ khóa trong danh sách đã tải. Chỉ xem bản tóm tắt trong mạng lưới được cấp quyền. |
| **F10** | Tiếp nhận hỗ trợ | Một tổ chức nhận một nhu cầu và giao một đại diện phụ trách; chuyển OPEN sang ACCEPTED. Transaction ngăn nhận trùng. Tiếp nhận chưa đồng nghĩa được xem toàn bộ hồ sơ riêng. |
| **F11** | Chat phối hợp realtime | Chat văn bản giữa đầu mối ghi nhận và đại diện phụ trách sau khi tiếp nhận. Người cao tuổi không bắt buộc có tài khoản. Thành viên khác chưa được vào chat trong MVP. |
| **F12** | Thống nhất lịch | Tổ chức đề xuất lịch; đầu mối trao đổi với người cao tuổi và ghi nhận đồng ý để chuyển sang SCHEDULED. Chia sẻ thông tin liên hệ cần thiết cho người được giao thực hiện trong phạm vi được phép. |
| **F13** | Ghi nhận và đối soát kết quả | Tổ chức báo đã hỗ trợ, chuyển sang DELIVERED. Đầu mối đối soát với người cao tuổi rồi xác nhận COMPLETED. Nếu chưa thống nhất, giữ DELIVERED và ghi nội dung cần đối soát. |
| **F14** | Hủy hoặc trả lại nhu cầu | Đầu mối hủy theo yêu cầu thực tế, kèm lý do. Tổ chức không thực hiện được thì trả về OPEN, ghi lý do và kết thúc lần tiếp nhận. Không tự đóng nhu cầu chưa được giải quyết. |
| **F15** | Lịch và lịch sử hỗ trợ | Xem lịch đã thống nhất, người phụ trách, các lần tiếp nhận và kết quả theo quyền. Hoàn thành một lần hỗ trợ không đồng nghĩa đã giải quyết mọi nhu cầu của người cao tuổi. |
| **F16** | Giao diện và offline | Chữ dễ đọc, nút rõ, xoay màn hình không mất dữ liệu form; có trạng thái tải/rỗng/lỗi. Offline ưu tiên bài đăng đã tải và nội dung được phép lưu. Không lưu bền vững hồ sơ riêng hoặc chat trên thiết bị. |

> 🔄 **Luồng trạng thái chính:**  
> REVIEW ➔ OPEN ➔ ACCEPTED ➔ SCHEDULED ➔ DELIVERED ➔ COMPLETED  
> *(Đầu mối có thể hủy khi nhu cầu chưa hoàn thành: CANCELLED)*

---

## 2. QUY TRÌNH & TRẠNG THÁI NHU CẦU HỖ TRỢ

| Trạng thái | Tên trạng thái | Người thực hiện & Mô tả quy trình |
|:---:|:---|:---|
| **REVIEW** | Chờ rà soát / bổ sung | Đầu mối gửi nhu cầu. Điều phối viên duyệt sang OPEN hoặc ghi yêu cầu bổ sung; đầu mối chỉnh sửa và gửi lại. |
| **OPEN** | Chờ tiếp nhận | Nhu cầu đã được duyệt. Đại diện tổ chức có quyền tiếp nhận. Chỉ bản tóm tắt được hiển thị trong mạng lưới. |
| **ACCEPTED** | Đã tiếp nhận | Một tổ chức và một đại diện được giao phụ trách. Hai đầu mối mở chat và trao đổi phương án, lịch. |
| **SCHEDULED** | Đã thống nhất lịch | Tổ chức đề xuất lịch. Đầu mối ghi nhận sự đồng ý của người cao tuổi trước khi chốt. |
| **DELIVERED** | Chờ đối soát kết quả | Tổ chức báo đã hỗ trợ. Đầu mối kiểm tra kết quả với người cao tuổi; chưa thống nhất thì giữ trạng thái này. |
| **COMPLETED** | Hoàn thành | Đầu mối xác nhận kết quả của nhu cầu cụ thể. Chat chuyển sang chỉ đọc. |
| **CANCELLED** | Đã hủy | Đầu mối hủy trước khi hoàn thành, kèm lý do. Chat chuyển sang chỉ đọc. |

---

## 3. CHỨC NĂNG MỞ RỘNG (Sau khi MVP ổn định)

| Mã | Nhóm mở rộng | Nội dung |
|:---:|:---|:---|
| **E01** | Nhắc lịch và thông báo | Thông báo tin nhắn, lịch, tiếp nhận và việc cần theo dõi; cho phép tùy chọn loại thông báo. |
| **E02** | Đổi lịch | Đề xuất lịch mới và bên còn lại xác nhận; giữ lịch sử thay đổi. |
| **E03** | Đánh giá và xử lý phản ánh | Đánh giá, báo cáo hành vi và quy trình xử lý phản ánh. Đối soát kết quả cơ bản đã thuộc F13, không chờ phần mở rộng. |
| **E04** | Nhập giọng nói | Chuyển lời nói thành nội dung nhu cầu, cho người nhập sửa trước khi gửi. |
| **E05** | AI hỗ trợ | Hỗ trợ viết nhu cầu, hướng dẫn thao tác, tìm kiếm và gợi ý. Chỉ dùng dữ liệu được phép; không tự duyệt hoàn cảnh, sự đồng ý hoặc quyền tổ chức. |
| **E06** | Chat nâng cao | Gửi ảnh/file, gọi thoại và video theo quyền và phạm vi chia sẻ. |
| **E07** | Nhóm và sự kiện | Hoạt động nhiều người, đăng ký/hủy tham gia và quản lý sức chứa. |
| **E08** | Tìm kiếm và giao diện nâng cao | Tìm nhiều loại nội dung, tùy chỉnh cỡ chữ và dark mode. |
| **E09** | SOS | Chỉ xem xét khi đã có đơn vị phản ứng và quy trình vận hành. Không đưa cam kết cứu hộ vào MVP. |

---

## 4. HẠNG MỤC KỸ THUẬT

| Mã | Hạng mục | Cách triển khai |
|:---:|:---|:---|
| **T01** | Java và XML | Dùng một project Android chung. Tách mã theo phần việc để các thành viên có thể lập trình và tích hợp. |
| **T02** | Firestore | Lưu tổ chức, quyền thành viên, nhu cầu, lịch sử tiếp nhận, lịch và tin nhắn. Tách document tóm tắt và document riêng; có Security Rules và indexes trong repo. |
| **T03** | API đăng nhập | Google Sign In kết hợp Firebase Authentication. Vẫn hỗ trợ email/mật khẩu. Đăng nhập không tự cấp quyền tổ chức. |
| **T04** | Responsive | Không khóa xoay; bố cục co giãn, giữ dữ liệu form khi đổi hướng màn hình. |
| **T05** | Offline và SQLite | Cho xem lại bài đăng đã tải và nội dung được phép lưu. SQLite là phần khuyến khích. Cấu hình cache để không lưu bền vững hồ sơ riêng/chat; các thao tác thay đổi trạng thái cần kết nối máy chủ. |
| **T06** | Kiểm thử quyền và dữ liệu | Kiểm tra tài khoản ngoài đơn vị, thành viên không được giao, hai tổ chức nhận đồng thời, sửa nhu cầu đã duyệt, trả rồi nhận lại, xoay và ngắt mạng. |

---

## 5. QUY TẮC NGHIỆP VỤ (Business Rules)

| Mã | Quy tắc | Nội dung |
|:---:|:---|:---|
| **R01** | Quyền theo đơn vị | Chỉ tài khoản có quyền được cấp trước mới ghi nhận, rà soát, nhận nhu cầu hoặc đăng bài nhân danh tổ chức. Không dùng lựa chọn vai trò trên giao diện để nâng quyền. |
| **R02** | Chia sẻ trong mạng lưới | Danh sách nhu cầu chỉ hiển thị trong mạng lưới được cấp quyền, không mặc định công khai trên Internet. Bảng tin tổ chức và hồ sơ hỗ trợ có phạm vi truy cập riêng. |
| **R03** | Thông tin riêng | Tách bản tóm tắt khỏi tên, địa chỉ, liên hệ và bằng chứng đồng ý. Người xem danh sách không tự động được đọc hồ sơ riêng; quyền được kiểm tra trên máy chủ. |
| **R04** | Giới hạn tiếp nhận | Mỗi nhu cầu chỉ có một tổ chức và một đại diện phụ trách tại một thời điểm. Việc nhận phải được máy chủ xác nhận, không báo nhận thành công khi offline. |
| **R05** | Rà soát và sự đồng ý | Rà soát trong app là kiểm tra nội bộ, không phải chứng nhận hoàn cảnh hay xác minh pháp lý. Thông tin do người nhập khai báo chưa tự nó chứng minh sự đồng ý hợp lệ. |
| **R06** | Thay đổi trạng thái | Kiểm tra quyền và trạng thái hiện tại trên máy chủ khi duyệt, nhận, chốt lịch, trả lại, hủy hoặc hoàn thành. Không cho người dùng sửa trực tiếp trạng thái tùy ý. |
| **R07** | Trả lại và tiếp nhận lại | Trả lại chỉ áp dụng trước khi tổ chức báo đã hỗ trợ. Lưu người trả, lý do và lần tiếp nhận cũ; nhu cầu về OPEN để tổ chức khác nhận. |
| **R08** | Chat theo lần tiếp nhận | Mỗi lần tiếp nhận có luồng chat riêng. Luồng cũ chỉ đọc khi kết thúc lần tiếp nhận; người phụ trách cũ không được xem chat mới hoặc thông tin riêng được bàn giao sau đó. |
| **R09** | Hoàn thành và nhu cầu lặp lại | COMPLETED đóng một nhu cầu cụ thể. Người cao tuổi có thể phát sinh nhu cầu khác; nhiều lần được hỗ trợ không tự động bị coi là gian lận. |