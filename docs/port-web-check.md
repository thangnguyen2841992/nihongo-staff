# Port Check và Web Check

Trong **Giám sát hệ thống → Đăng ký target → Chọn Type**, chọn **Port Check** hoặc **Web Check**. Cả hai dùng lịch thu thập, biểu đồ, WebSocket và rule cảnh báo hiện có. API chỉ dành cho ADMIN/STAFF; USER bị từ chối trước khi thực hiện kiểm tra.

## Port Check

Nhập tên, hostname/IP và cổng 1–65535. Kiểm tra thiết lập kết nối TCP từ máy chạy staff-service; không kiểm tra giao thức ứng dụng, không hỗ trợ UDP và không cần exporter.

| Metric | Ý nghĩa |
| --- | --- |
| `PORT_AVAILABLE` | 1 khi kết nối TCP được, 0 khi không được |
| `PORT_RESPONSE_MS` | Thời gian thực hiện lượt kiểm tra, kể cả lượt thất bại, đơn vị ms |

## Web Check

Nhập tên, URL HTTP/HTTPS và khoảng mã phản hồi mong đợi, mặc định 200–399. Hỗ trợ cổng tùy chỉnh và đường dẫn/query riêng; mỗi URL là một target, nên có thể kiểm tra nhiều health endpoint cùng host/cổng.

- Gửi GET và kiểm tra chứng chỉ/hostname HTTPS bằng cơ chế tin cậy mặc định của JVM. Không bỏ qua lỗi TLS.
- Không đi theo redirect. Ví dụ 302 được ghi là 302; nếu khoảng mong đợi chứa 302 thì kết quả UP, không kiểm tra URL đích.
- Đọc đến phản hồi headers rồi đóng body; không render JavaScript, kiểm tra nội dung hoặc tải hết trang. Thời gian phản hồi phản ánh lúc nhận headers, gồm DNS/kết nối/TLS cần thiết.
- Không gửi cookie/JWT của ứng dụng, không cấu hình authorization header và không sử dụng proxy hệ thống. URL không nhận userinfo hoặc fragment.

| Metric | Ý nghĩa |
| --- | --- |
| `WEB_AVAILABLE` | 1 khi có HTTP status trong khoảng mong đợi, 0 trong trường hợp khác |
| `WEB_RESPONSE_MS` | Thời gian kiểm tra, kể cả lượt thất bại, đơn vị ms |
| `WEB_STATUS_CODE` | HTTP status nhận được; 0 khi không nhận được phản hồi HTTP |

## Trạng thái, lịch và cảnh báo

- Mặc định thu thập mỗi 30 giây, timeout 5 giây. Chỉnh lịch theo target tại màn hình lịch thu thập; timeout theo cấu hình metric hiện có.
- Nút **Kiểm tra ngay** thực hiện probe, không ghi lịch sử monitoring. Có thể đăng ký mà chưa probe hoặc khi probe đang DOWN. Target mới bắt đầu UNKNOWN, scheduler sẽ ghi kết quả.
- Kết quả DOWN là mẫu đo hợp lệ: vẫn lưu khả dụng bằng 0 và thời gian kiểm tra, đưa trạng thái về DOWN cùng lý do. Khi hoạt động lại, trạng thái trở về UP.
- Để cảnh báo mất kết nối, tạo rule cho `PORT_AVAILABLE`/`WEB_AVAILABLE`, điều kiện **LT 1**, chọn số mẫu liên tiếp theo nhu cầu. Rule phát ALERT/RECOVERY theo cơ chế hiện có; không tự tạo rule cho target mới.
- Các metric cùng target/timeout dùng chung probe trong tối đa 3 giây để tránh gọi lặp. Probe lỗi cấu hình nội bộ dùng cơ chế lỗi/backoff, khác với kết quả mới xác nhận dịch vụ DOWN.
- Danh sách đăng ký tải 20 target mỗi trang theo Type, tự làm mới mỗi 30 giây khi tab đang hiển thị. Giữ nguyên hàng hiện có trong lúc cập nhật, gộp các yêu cầu trùng và hủy yêu cầu khi rời màn hình. Chuyển Type vẫn giữ bản nháp riêng.
- DNS hết chỗ hoặc yêu cầu hết hạn khi đang đợi trong queue là lỗi bộ kiểm tra, không ghi mẫu khả dụng bằng 0. Probe tức thời trả 503; scheduler dùng lỗi/backoff. Lookup đã hết hạn được gỡ khỏi queue. TCP chia thời gian còn lại cho các địa chỉ dự phòng.

DNS chạy trong pool riêng giới hạn hai luồng, queue 16, caller đợi trong budget kiểm tra. TCP dùng địa chỉ đã phân giải/kiểm tra. Chặn địa chỉ unspecified, link-local, multicast và địa chỉ metadata `100.100.100.200`. HTTP còn được JDK phân giải khi kết nối; kiểm tra DNS trước request không thay thế giới hạn egress ở hạ tầng nếu cho phép hostname/DNS không tin cậy. Mạng nội bộ và loopback vẫn được phép để giám sát dịch vụ nội bộ.

## API

- `GET /api/staff/check-targets`: danh sách cấu hình Port/Web.
- `GET /api/staff/check-targets/page?type=PORT_CHECK&page=0&size=20`: trang theo Type, trả `{content,number,hasNext}`. `type` chỉ nhận PORT_CHECK/WEB_CHECK; `page >= 0`, `size` từ 1 đến 100. Dùng Slice, không chạy COUNT; một scalar join query cho mỗi trang. Đọc cấu hình collector cũng chỉ dùng một join query.
- `POST /api/staff/check-targets/probe`: kiểm tra tức thời, trả `healthy`, `responseMs`, `statusCode`, `message`. Mất kết nối là kết quả DOWN; dữ liệu yêu cầu sai trả 400.
- `POST /api/staff/check-targets`: đăng ký; tên hoặc địa chỉ trùng trả 409. Không kết nối mạng trong giao dịch đăng ký.

Port:

```json
{"name":"SSH production","type":"PORT_CHECK","host":"server.example.com","port":22}
```

Web:

```json
{"name":"Website health","type":"WEB_CHECK","url":"https://example.com/health","statusMin":200,"statusMax":299}
```

## Triển khai

Áp dụng [monitor-check-target.sql](sql/monitor-check-target.sql) trước khi chạy bản staff-service mới ở môi trường dùng schema validation. Migration mở rộng enum loại target và thêm bảng cấu hình, không thay lịch sử metric. Bản phát triển dùng `ddl-auto=update` có thể tự tạo schema khi khởi động.

Build/khởi động lại staff-service và triển khai FE `dist` mới (dev server dùng source mới). Trong đợt này chưa áp dụng migration hoặc khởi động lại dịch vụ đang chạy trên database thật.

## Kiểm chứng

- Staff-service và thư viện phụ thuộc: 179 kiểm thử đạt, 1 Gemini live bỏ qua theo cấu hình; đóng gói thành công. FE: 294 kiểm thử đạt, kiểm tra kiểu/build production thành công; 56/56 ca Chromium/Edge đạt.
- TCP/HTTP local thật: cổng mở/đóng, HTTP 503 và khoảng status tùy chọn, redirect không được theo, timeout headers/DNS, đóng stream body mà không chờ tải hết.
- Database H2: đăng ký các URL khác path trên cùng host/cổng; chống trùng; gán đúng 2 metric Port và 3 metric Web; lưu 0 khi DOWN, ALERT rồi RECOVERY; phân biệt mẫu DOWN mới với dữ liệu stale khi collector lỗi nội bộ.
- Quyền API: USER không được list/probe/register, ADMIN/STAFF được truy cập.
- FE: đăng ký được target đang DOWN, bảo toàn draft khi đổi Type, bỏ phản hồi probe cũ, lỗi quyền; desktop/mobile có đường dẫn sang biểu đồ đúng.
- Log kiểm chứng: `tmp/check-feature-be-final.log`, `tmp/check-feature-fe-unit.log`, `tmp/check-feature-fe-final-build.log`, `tmp/check-feature-browser-all.log` ở thư mục gốc workspace.

Test trình duyệt dùng API giả lập; kiểm tra mạng thật ở backend dùng localhost và cổng tạm. Chưa xác minh endpoint Internet/production của người dùng hoặc migration trên database production.

Đợt tối ưu tiếp theo: staff và thư viện phụ thuộc đạt 184 kiểm thử, 1 bỏ qua theo cấu hình. Kiểm thử H2 xác nhận trang 20/21 Port target dùng đúng một truy vấn, không hydrate entity và không lẫn Web target; kiểm thử DNS queue/capacity và collector xác nhận không tạo mẫu DOWN giả. Tham khảo thêm [màn hình topology](topology.md).
