# Khởi động staff-service trên máy local

Các backend service cùng đọc file `microservice/.local/shared.properties`. Đặt cùng file này ở đúng vị trí trên cả hai máy; không đưa file thật lên Git. `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` và `MONITORING_PROMETHEUS_SSH_PASSWORD` là những khóa staff-service sử dụng. File `config/nihongo-staff.properties.example` chỉ là ví dụ tên khóa và không được nạp khi chạy.

Trong IntelliJ, đặt Working directory là `microservice` hoặc `microservice/nihongo-staff`, rồi chạy `StaffApplication`. Xóa các biến cùng tên từng nhập trong Run Configuration để chúng không ghi đè file chung. Sau khi sửa file, khởi động lại service.

Nếu chưa có mật khẩu SSH, service vẫn khởi động và collector Node Exporter vẫn chạy; yêu cầu đồng bộ Prometheus sẽ báo thiếu cấu hình. JWT secret phải khớp với các service xác thực khác để token hợp lệ.