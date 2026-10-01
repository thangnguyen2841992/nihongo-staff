# Giám sát MySQL qua JDBC

Mở **Monitoring Server → Đăng ký MySQL**. Nhập tên target, host, cổng (mặc định 3306), tài khoản giám sát, mật khẩu và chế độ TLS. Bấm **Kiểm tra kết nối** rồi **Đăng ký giám sát**. Máy chạy `staff-service` phải kết nối được đến cổng MySQL. Tài khoản chỉ cần quyền đăng nhập và đọc `SHOW GLOBAL STATUS`; câu lệnh này không yêu cầu quyền bổ sung theo [tài liệu MySQL](https://dev.mysql.com/doc/refman/8.4/en/show-status.html). Không dùng tài khoản quản trị.

Kết nối mặc định `VERIFY_IDENTITY`: xác thực chứng chỉ và hostname của server. Nếu server dùng CA riêng, cấu hình trust store cho JVM chạy staff-service. Có thể chọn `DISABLED` cho mạng nội bộ tin cậy; khi đó dữ liệu kết nối không được mã hóa. Xem [chế độ TLS của Connector/J](https://dev.mysql.com/doc/connectors/en/connector-j-connp-props-security.html).

`staff-service` cần `MONITORING_ENCRYPTION_KEY`: khóa AES 32 byte mã hóa Base64. File local `.local/shared.properties` của workspace đã có khóa này và được ignore khỏi Git. Khi chạy trên máy khác với **cùng DB chứa target**, sao chép đúng khóa sang file local tương ứng; đổi hoặc mất khóa sẽ làm các mật khẩu target đã lưu không giải mã được. Không đưa khóa/mật khẩu lên Git. Mật khẩu target chỉ được nhận ở API đăng ký và lưu dưới dạng AES-GCM trong `monitor_mysql_target`.

Các metric mặc định được tạo riêng, không gán cho VPS Linux/Windows:

| Metric | Ý nghĩa | Đơn vị |
| --- | --- | --- |
| `MYSQL_UPTIME` | Thời gian MySQL chạy | giây |
| `MYSQL_THREADS_CONNECTED` | Kết nối đang mở | kết nối |
| `MYSQL_THREADS_RUNNING` | Luồng đang chạy | luồng |
| `MYSQL_QUERIES_RATE` | Truy vấn xử lý mỗi giây | truy vấn/giây |
| `MYSQL_SLOW_QUERIES_RATE` | Truy vấn chậm mỗi giây | truy vấn/giây |
| `MYSQL_CONNECTIONS_RATE` | Lượt kết nối mỗi giây | kết nối/giây |
| `MYSQL_BYTES_RECEIVED_RATE` | Dữ liệu nhận mỗi giây | byte/giây |
| `MYSQL_BYTES_SENT_RATE` | Dữ liệu gửi mỗi giây | byte/giây |

Các chỉ số được đọc từ `SHOW GLOBAL STATUS` theo lịch trong `monitor_vps_metric`. Metric có hậu tố `_RATE` tính chênh lệch counter giữa hai lần thu thập; mẫu đầu tiên chỉ thiết lập mốc, chưa có giá trị tốc độ. Khi counter giảm (ví dụ MySQL khởi động lại), hệ thống bỏ mẫu chênh lệch đó và thiết lập mốc mới. Các target MySQL dùng chung màn hiệu năng, lịch sử, WebSocket và rule event với hệ thống giám sát hiện tại; event chỉ phát khi mẫu theo lịch thỏa rule. Xem [định nghĩa status variable](https://dev.mysql.com/doc/refman/8.4/en/server-status-variables.html).

Chạy phiên bản mới của `staff-service` để khởi tạo metric và bảng mới. Cấu hình local hiện dùng `ddl-auto=update`; môi trường dùng migration riêng có thể áp dụng [script tạo bảng](sql/monitor-mysql-target.sql) trước khi chạy. Chưa kiểm thử với một MySQL target thật vì chưa có thông tin kết nối target.
