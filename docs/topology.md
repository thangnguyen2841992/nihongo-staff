# Topology tổng quan

Mở **Giám sát hệ thống → Topology tổng quan** trong giao diện Staff, hoặc **Vận hành → Topology giám sát** trong giao diện Admin. Đường dẫn: `/staff/monitoring/topology`.

## Cách sử dụng

- Nút menu trên thanh đầu trang đóng/mở sidebar chính ở cả desktop và mobile, đồng bộ cho Staff, Admin và User. Khi thu gọn trên desktop, vùng nội dung mở rộng theo; trạng thái này được giữ khi chuyển trang trong cùng layout và khi đổi qua lại kích thước màn hình. Chọn liên kết trên desktop không tự thu gọn menu. Mobile vẫn mở dưới dạng menu phủ và đóng khi chuyển trang/click ngoài. Trạng thái không lưu qua lần tải lại trang. Nền giao diện và dòng thông báo User được căn theo vùng nội dung khi sidebar thu gọn.
- Nút **Ẩn cây Type / Hiện cây Type** trong thanh công cụ bản đồ điều khiển riêng cây target, giữ nguyên bộ lọc và các vị trí đã kéo. Map tự căn lại theo vùng hiển thị mới.
- Cây menu bên cạnh map hiển thị các Type, số lượng và danh sách target có thể mở rộng/thu gọn. Chọn Type để lọc, chọn target trong cây để định vị và mở chi tiết trên map.
- Mỗi target là một icon tròn với tên ở dưới: máy chủ Linux/Unix, Windows, MySQL, Kafka, Port Check hoặc Web Check. Type chưa nhận diện dùng icon chung để hỗ trợ mở rộng.
- Xanh là UP, đỏ là DOWN, xám là UNKNOWN. Thẻ thống kê đếm toàn bộ danh sách; bộ lọc chỉ thay đổi các node đang hiển thị.
- Tìm theo tên, địa chỉ, cổng hoặc loại; kết hợp bộ lọc Type/trạng thái mà không tạo thêm request.
- Kéo nền để di chuyển bản đồ, kéo icon đến bất kỳ tọa độ nào, kể cả sang trái/lên trên vùng bố cục ban đầu; con lăn hoặc nút +/− để thu phóng. **Vừa màn hình** căn toàn bộ kết quả, kể cả target đã kéo xa; **Xếp lại** khôi phục bố cục tự động.
- Chuột phải trên nền hoặc icon trong map → **Sắp xếp tự động** để xóa vị trí kéo, xếp lại và căn cụm target đang hiển thị vào giữa map. Giữ bộ lọc Type/trạng thái hiện tại. Menu mở sát con trỏ và nằm trong vùng map; đóng bằng Escape hoặc click ra ngoài. Có thể mở bằng Shift+F10 khi focus bản đồ.
- Menu chuột phải, nút đầu trang và nút trên map trống đều có **Tạo target** để mở popup ngay trên Topology. Popup dùng chung form Máy chủ/MySQL/Kafka/Port/Web với trang đăng ký; đổi Type hoặc OS chỉ đổi trạng thái trong popup, không thay URL. Đóng bằng nút ×, Escape hoặc click ngoài.
- Khi đăng ký lỗi, popup giữ dữ liệu để sửa và thử lại. Sau thành công, popup đóng, map tải lại danh sách, xóa bộ lọc đang che target mới và chọn target vừa tạo để mở chi tiết. Vị trí các target đã kéo trước đó được giữ. Nếu tải lại lỗi, thông báo tạo thành công vẫn được giữ cùng lỗi tải trạng thái.
- Bố cục tự xếp icon thành nhiều hàng theo kích thước màn hình. Vị trí kéo được giữ qua các lần làm mới trong phiên mở trang; chưa lưu vào database hoặc qua lần tải lại trang.
- Bấm node, hoặc dùng Tab và Enter/Space, để mở chi tiết. Có liên kết tới trang hiệu năng và cấu hình cảnh báo đúng target. Khi focus nền bản đồ, phím mũi tên di chuyển góc nhìn.

## Dữ liệu và quyền

Dùng `GET /api/staff/vps` hiện có; route và API chỉ dành cho ADMIN/STAFF. Không thêm thư viện graph, API mới, migration hoặc thông tin đăng nhập vào bản đồ. Icon dùng bộ Bootstrap Icons có sẵn.

Tự cập nhật mỗi 30 giây khi tab hiển thị, cập nhật lại khi quay lại tab. Gộp request đang chạy, hủy request và timer khi rời trang. Khi tải lỗi, giữ bản đồ cũ cùng thông báo chưa tải được trạng thái và thời gian cập nhật gần nhất. Trạng thái và lần đo thành công lấy từ backend; topology không tự kết nối hoặc probe target.

Cây menu đảm nhiệm phân nhóm Type; map hiển thị các icon tự do. Chưa hiển thị kết nối mạng hoặc quan hệ phụ thuộc giữa các dịch vụ. Muốn hiển thị quan hệ thật cần bổ sung nguồn dữ liệu liên kết hoặc cấu hình dependency.

## Kiểm chứng

Frontend: 306 kiểm thử đạt, kiểm tra kiểu dữ liệu và build production thành công. Bộ kiểm thử Chromium/Edge đầy đủ: 63/64 đạt ở lượt đầu, một ca âm thanh STAFF gặp lỗi giao thức do đóng phiên trình duyệt; chạy lại riêng cả hai ca âm thanh STAFF/USER đều đạt. Sáu ca đóng/mở sidebar Staff/Admin/User trên desktop/mobile và mốc 769px đều đạt; hai ca topology desktop/mobile gồm mở/đóng nhánh cây, định vị target, hình tròn/tên bên dưới, kéo sang tọa độ âm, fit lại toàn bộ map, giữ vị trí sau khi làm mới và menu chuột phải. Xác minh tọa độ hiển thị của một target và cả cụm icon nằm giữa map sau tự sắp xếp; menu không tràn ở góc map, đóng bằng Escape/click ngoài. Popup được kiểm tra đổi OS/Type không thay URL, đóng Escape/click ngoài, xử lý lỗi 409 và tạo Port target để cập nhật map mà không rời Topology. Đây là kiểm thử với API giả lập, chưa phải xác minh toàn bộ target trên môi trường thật.

Kiểm thử frontend bao gồm: sáu loại icon, bộ lọc kết hợp, trạng thái và liên kết chi tiết, giữ dữ liệu khi lỗi, dừng cập nhật tab ẩn, hủy yêu cầu khi rời trang, bố cục và Type cũ/chưa biết. Kiểm thử trình duyệt dùng API giả lập trên desktop 1440px và mobile 390px, xác minh kéo thả, thu phóng, mở chi tiết, không tràn chiều ngang.

Log mới trong thư mục `tmp` ở gốc workspace: `topology-popup-unit.log`, `topology-popup-build.log`, `topology-popup-browser.log`, `topology-popup-browser-all.log`, `sidebar-unit.log`, `sidebar-build.log`, `sidebar-browser-all.log`, `member-sidebar-unit.log`, `member-sidebar-build.log`, `member-sidebar-browser-all.log`, `member-sidebar-audio-recheck.log`. Ảnh giao diện: `topology-1440.png`, `topology-390.png`, `topology-create-popup-1440.png`, `topology-create-popup-390.png`.

Build FE và nạp bản giao diện mới để sử dụng. Topology dùng API VPS hiện có nên không cần thay schema. Bản tối ưu Port/Web Check đi kèm cần nạp staff-service mới để có endpoint phân trang; migration của lần thêm Port/Web Check vẫn cần áp dụng nếu môi trường chưa có bảng cấu hình.
