# Khởi động staff-service trên máy local

MySQL và JWT cần cấu hình mật khẩu/secret tương ứng (qua biến môi trường hoặc
cấu hình local). `MONITORING_PROMETHEUS_SSH_PASSWORD` dùng để đồng bộ target
qua SSH. Nếu chưa có mật khẩu SSH, service vẫn khởi động và collector Node
Exporter vẫn chạy; yêu cầu đồng bộ Prometheus sẽ báo cấu hình thiếu. Dữ liệu
đăng ký VPS đã commit vẫn được giữ và không nên đăng ký lại.

Chạy trong thư mục `staff`:

```powershell
New-Item -ItemType Directory -Force .local
Copy-Item config/nihongo-staff.properties.example .local/nihongo-staff.properties
```

Chỉ copy lần đầu; không ghi đè file đã điền. Mở file vừa tạo và điền mật khẩu
MySQL, mật khẩu SSH của Prometheus và JWT secret đang dùng chung với các service
khác. Trong file `.properties`, dấu backslash trong giá trị phải viết thành `\\`.
Không cần đặt mật khẩu trong `application.yml` hay gửi vào chat.

`application.yml` tự đọc file này khi working directory là `staff` hoặc là thư
mục gốc `mícroservices`. Nếu chạy IntelliJ, đặt Working directory về thư mục
`staff`, rồi chạy lại `StaffApplication`. Cũng có thể khai báo ba biến qua
Environment variables trong Run Configuration thay cho file local.

File `.local/nihongo-staff.properties` được Git bỏ qua. File mẫu không chứa
credentials và không tự được nạp. Các giá trị trống trong mẫu cần được điền trước
khi chạy. JWT secret phải khớp với hệ thống hiện tại để token hợp lệ.
