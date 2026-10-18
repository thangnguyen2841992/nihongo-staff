# Giám sát Kafka qua Admin API

Mở **Giám sát hệ thống → Đăng ký Kafka**. Nhập tên target, danh sách `bootstrap servers` theo dạng `hostname:port`, chế độ bảo mật và tài khoản nếu dùng SASL. Bấm **Kiểm tra kết nối** trước khi đăng ký. Máy chạy `staff-service` phải kết nối được tới các broker mà Kafka trả về trong metadata, không chỉ broker bootstrap.

Hỗ trợ `PLAINTEXT`, `SSL` và `SASL_SSL`; cơ chế SASL gồm `PLAIN`, `SCRAM-SHA-256` và `SCRAM-SHA-512`. Với `SSL`/`SASL_SSL`, JVM chạy staff-service phải tin cậy CA của broker và hostname trong metadata phải khớp chứng chỉ. Nên dùng principal giám sát riêng với quyền đọc cluster/topic metadata, liệt kê consumer group, đọc group offsets và đọc latest offsets. Nếu principal không có quyền đọc group, metric cấp cluster vẫn được lưu còn số group/consumer lag sẽ không có dữ liệu.

Mật khẩu SASL dùng chung cơ chế AES-GCM và biến `MONITORING_ENCRYPTION_KEY` với target MySQL. Giữ nguyên khóa khi nhiều instance dùng chung database. API và danh sách target không trả lại username hoặc mật khẩu. Màn đăng ký cho phép kiểm tra rồi thay tài khoản SASL mà không làm mất metric và lịch sử.

Các metric mặc định:

| Metric | Ý nghĩa | Đơn vị |
| --- | --- | --- |
| `KAFKA_BROKER_COUNT` | Số broker trong cluster | broker |
| `KAFKA_TOPIC_COUNT` | Số topic | topic |
| `KAFKA_PARTITION_COUNT` | Tổng số partition | partition |
| `KAFKA_OFFLINE_PARTITIONS` | Partition không có leader | partition |
| `KAFKA_UNDER_REPLICATED_PARTITIONS` | Partition có ISR ít hơn replica | partition |
| `KAFKA_CONSUMER_GROUP_COUNT` | Tổng số consumer group | group |
| `KAFKA_CONSUMER_LAG` | Tổng lag theo từng consumer group | message |

Collector giới hạn đọc lag ở 500 group mỗi target trong một chu kỳ để giữ thời gian thu thập có giới hạn. Metadata của cùng target được dùng lại tối đa 3 giây, nhờ đó các metric đến hạn gần nhau không mở một kết nối Admin riêng cho từng metric. Timeout mặc định là 10 giây và có thể đổi trong cấu hình lịch thu thập.

Target Kafka dùng chung màn hiệu năng, lịch sử, WebSocket, lịch thu thập và rule event. Consumer lag là metric theo object; mỗi consumer group là một object riêng. Khi group biến mất sau một lần đọc thành công, object được đánh dấu offline theo cơ chế monitoring hiện có.

Production dùng `ddl-auto=validate`, vì vậy cần áp dụng [migration Kafka](sql/monitor-kafka-target.sql) trước khi triển khai staff-service. Khi service khởi động, `MetricCatalog` tạo các định nghĩa metric còn thiếu và gán đúng bộ metric Kafka cho target. Chưa kiểm thử với cluster Kafka thật vì workspace chưa có thông tin kết nối target.
