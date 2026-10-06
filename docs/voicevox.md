# Phát âm câu ví dụ TRY! N3 bằng VOICEVOX

`staff-service` lấy văn bản câu ví dụ từ database, bỏ phần furigana, gọi VOICEVOX Engine qua `POST /audio_query` và `POST /synthesis`, rồi lưu WAV theo nội dung câu vào `${user.home}/.nihongo/voicevox-cache`. Bản ghi mới hoặc câu đã chỉnh sửa sẽ tự tạo audio mới. Chỉ người có quyền xem ngữ pháp tương ứng mới tải được audio.

1. Cài và chạy [VOICEVOX](https://voicevox.hiroshiba.jp/) trên **cùng máy chạy staff-service**. Engine mặc định nghe ở `http://127.0.0.1:50021`. Kiểm tra bằng `curl http://127.0.0.1:50021/speakers` trong terminal của máy đó.
2. Nếu Engine dùng cổng hoặc máy khác, đặt `VOICEVOX_URL` trước khi khởi động staff-service. Chỉ trỏ đến Engine tin cậy; không công khai cổng Engine ra Internet.
3. Có thể đặt `VOICEVOX_CACHE_DIR` để chọn thư mục WAV. Thư mục mặc định nằm trong home của tài khoản chạy service và cần quyền ghi.
4. Mở sách TRY! N3, bấm **Nghe phát âm** tại câu ví dụ. Lần đầu tạo âm thanh có thể chậm; các lần sau dùng file đã lưu. Nếu Engine chưa chạy, giao diện dùng giọng tiếng Nhật của trình duyệt.

Giọng dùng là **VOICEVOX: ずんだもん** (style thường, ID 3). Credit được hiển thị khi phát audio VOICEVOX. Trước khi dùng audio đã tạo ngoài ứng dụng, kiểm tra [điều khoản VOICEVOX](https://voicevox.hiroshiba.jp/term/) và [điều khoản giọng ずんだもん](https://voicevox.hiroshiba.jp/product/zundamon/).
