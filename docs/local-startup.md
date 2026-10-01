# Khởi động staff-service trên máy local

Các backend service cùng đọc file `.local/shared.properties` ở thư mục workspace. Không đưa file thật lên Git. Staff-service dùng `SPRING_DATASOURCE_PASSWORD` và `JWT_SECRET`; không cần mật khẩu SSH để ghi target Prometheus local.

Để đăng ký target MySQL qua JDBC, staff-service còn cần `MONITORING_ENCRYPTION_KEY` trong cùng file local. Giữ nguyên khóa này trên mọi máy chạy với cùng DB; xem [hướng dẫn giám sát MySQL](mysql-monitoring.md).

Trong IntelliJ, đặt Working directory là thư mục workspace hoặc `staff`, rồi chạy `StaffApplication`. Xóa các biến cùng tên từng nhập trong Run Configuration để chúng không ghi đè file chung. Sau khi sửa file, khởi động lại service.

Prometheus local đọc `targets/node_targets.json` (Linux) và `targets/windows_targets.json` (Windows) qua hai job `node` và `windows_vps` dùng `file_sd_configs`; staff-service ghi cả hai file vào thư mục Prometheus đang chạy. Cấu hình đang dùng nằm tại `C:/Users/thang/Downloads/prometheus-3.15.0.windows-amd64/prometheus.yml`. Sau khi thêm job mới, cần khởi động lại Prometheus một lần vì tiến trình hiện tại chưa bật `--web.enable-lifecycle`. Các lần cập nhật file target về sau được `file_sd_configs` tự đọc lại sau 5 giây. Có thể đổi vị trí bằng `PROMETHEUS_TARGETS_FILE` và `PROMETHEUS_WINDOWS_TARGETS_FILE`, nhưng file Prometheus cấu hình phải trỏ đến cùng đường dẫn. JWT secret phải khớp với các service xác thực khác để token hợp lệ.

Để đăng ký laptop Windows chạy cùng máy với staff-service, chọn **Windows Exporter**, nhập `127.0.0.1` và cổng `9182`. Nếu exporter ở máy khác, nhập IP mà **máy chạy staff-service** truy cập được. Sau khi đăng ký, cần khởi động lại staff-service để chạy code mới; trường `monitor_vps.exporter_type` được Hibernate thêm vào DB với `ddl-auto=update`. Bản ghi Linux cũ chưa có giá trị ở trường này vẫn được xử lý là Node Exporter.
