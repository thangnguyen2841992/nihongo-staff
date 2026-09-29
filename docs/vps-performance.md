# Thu thập perf theo VPS, metric và object

Luồng: đăng ký VPS → đọc Node Exporter → lưu VPS, metric assignment và object trong một transaction → scheduler đọc lịch trong DB → đọc Node Exporter → lưu perf → API/UI đọc DB.

## Sử dụng

- Restart staff-service sau khi cập nhật. Cấu hình hiện tại dùng `spring.jpa.hibernate.ddl-auto=update`; Hibernate bổ sung bảng/cột mới. Không chạy ứng dụng test với DB thật.
- Khi service sẵn sàng, khởi tạo 7 metric mặc định nếu mã metric chưa tồn tại. Không ghi đè cấu hình metric đã có. Tự gán metric mặc định cho VPS cũ còn thiếu assignment; object của VPS cũ được khám phá trong lần thu thập thành công đầu tiên.
- VPS đăng ký mới được kiểm tra Node Exporter trước khi lưu. VPS, assignment và object cùng transaction. Đồng bộ target Prometheus qua SSH vẫn là bước sau commit; lỗi SSH không ngăn collector DB đọc Node Exporter trực tiếp.
- Mở **Giám sát → Hiệu năng VPS**, hoặc **Xem danh sách VPS → Xem perf**. Chọn VPS, metric, object và thời gian 1h/6h/24h/7 ngày.
- Phần **Cấu hình thu thập** cho phép bật/tắt và đổi lịch mặc định của metric, timeout hoặc lịch riêng trên VPS. `scheduleSeconds=null` trên assignment nghĩa là kế thừa lịch metric.
- CPU và network cần 2 lần thu thập; lần đầu chỉ lưu baseline. Không ghi 0 thay cho dữ liệu thiếu. Counter reset sẽ cập nhật baseline và bỏ mẫu chênh lệch không hợp lệ.

## Dữ liệu

Danh mục nằm ở `monitor_metric`; assignment/lịch/trạng thái tác vụ ở `monitor_vps_metric`; object ở `monitor_object`; giá trị lịch sử ở `monitor_perf_value`; baseline counter ở `monitor_perf_baseline`.

- CPU_USAGE: một object cho mỗi nhãn cpu; % = 100 × (1 − Δidle/Δtotal). Không cộng lại guest/guest_nice đã nằm trong user/nice.
- DISK_USAGE: một object cho mỗi cặp device + mountpoint, loại các filesystem tmpfs/devtmpfs/overlay/squashfs; dùng avail/size.
- NETWORK_RECEIVE/TRANSMIT: từng device, bỏ lo; bytes/s = Δcounter/Δthời gian giữa hai mẫu của metric.
- MEMORY_USAGE, LOAD_1M, UPTIME: metric toàn VPS, `object_id=null`.

Mọi mẫu perf và timestamp của scheduler dùng UTC. API trả epoch seconds; UI đổi sang múi giờ trình duyệt. Object không còn xuất hiện sau một lần đọc thành công được đánh dấu OFFLINE và giữ lịch sử. Lỗi mạng không đánh dấu toàn bộ object biến mất.

Collector hiện hỗ trợ `collector_type=NODE_EXPORTER` và 7 mã trên. Metric khác trong DB cần bổ sung collector tương ứng; lỗi được lưu ở assignment và hiện trên UI. Không tự ghi đè các metric đã có để tránh thay đổi cấu hình ngoài ý muốn.

History được lọc theo VPS + metric + object tại DB, trung bình theo bucket để giới hạn khoảng 1000 điểm/chuỗi; khoảng bị gián đoạn được thêm điểm null. Latest luôn trả thời gian mẫu và cờ stale; dữ liệu cũ không bị trình bày như mẫu mới.

## Scheduler

4 worker, tối đa 4 tác vụ đang chờ/chạy; HTTP không nằm trong transaction ghi dữ liệu. Mỗi assignment được claim dưới khóa DB với lease/token. Tác vụ cũ không được ghi sau khi token đã thay đổi. Cấu hình mới áp dụng cho lần thu thập tiếp theo; baseline được lưu để giữ chuỗi counter sau restart.

Khi lỗi, assignment giữ nguyên mẫu cũ và thử lại sau ít nhất 60 giây, tăng dần tối đa 15 phút (không rút ngắn chu kỳ đã cấu hình). Thu thập thành công reset số lần lỗi. Chỉ ghi cảnh báo khi nội dung lỗi thay đổi. Thông báo kết nối bao gồm IP/port và phân biệt lỗi kết nối, DNS, timeout và HTTP.

Các metric dùng chung object (ví dụ network receive/transmit cùng `eth0`) khóa VPS và đọc object bằng khóa cập nhật trong transaction READ_COMMITTED, tránh tạo trùng object khi thu thập đồng thời. Có kiểm thử hai worker tạo cùng object; kiểm thử này dùng H2, không thay thế kiểm tra thực tế trên MySQL.

Các property tùy chọn dưới `monitoring.collection`:

```yaml
enabled: true
tick-ms: 1000
retention-days: 0
```

`retention-days=0` giữ toàn bộ perf. Nếu cấu hình số ngày >0, cleanup mỗi giờ xóa tối đa 10000 mẫu cũ/lần. Với nhiều VPS cần chọn retention và theo dõi kích thước bảng/index. Không có thao tác xóa lịch sử mặc định.

## API (ADMIN/STAFF)

- GET `/api/staff/vps-metrics`: danh mục từ DB.
- GET `/api/staff/vps/{id}/metric-configs`: assignment và cấu hình hiệu lực.
- PUT `/api/staff/vps-metrics/{metricId}/config`: `{scheduleSeconds, timeoutMs, enabled}`.
- PUT `/api/staff/vps/{id}/metrics/{code}/config`: `{scheduleSeconds, enabled}`.
- GET `/api/staff/vps/{id}/performance?metric=CPU_USAGE`: giá trị mới nhất của mọi object.
- GET cùng URL với `hours=24&objectKey=<object_id>`: lịch sử riêng; object toàn VPS dùng `objectKey=vps`.

Chu kỳ hợp lệ 5–86400 giây; timeout 1000–30000 ms. Lấy history tối đa 168 giờ/lần. API đọc không tạo object hay thu thập thêm mẫu.

## Kiểm thử

Backend test dùng H2 riêng ở chế độ MySQL, fixture Node Exporter và mock HTTP/SSH; không truy cập hay thay đổi DB/VPS thật. Test xác minh đăng ký, gán metric, baseline, rate, reset counter, đọc history, giữ mẫu khi lỗi, khóa tác vụ và schedule. Native query cần được smoke-test thêm khi triển khai trên phiên bản MySQL của môi trường.

Parser đọc định dạng text Node Exporter, hỗ trợ CRLF và nhãn escaped theo [Prometheus text format](https://prometheus.io/docs/instrumenting/exposition_formats/).
