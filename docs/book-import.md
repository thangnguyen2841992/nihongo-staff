# Nhập sách từ PDF và audio

Mở `/staff/imports/books` với tài khoản STAFF hoặc ADMIN. Nút **Nhập sách từ PDF** nằm trên trang quản lý sách.

## Luồng nhập nhanh

1. Chọn PDF, trình độ, loại sách và các file nghe. Tên sách lấy từ tên file; tên có N1–N5 có thể gợi ý trình độ. **Tự tạo nội dung bằng Gemini** được bật mặc định.
2. Máy chủ đọc ảnh từng nhóm tối đa 6 trang và gửi tới Gemini. Gemini tạo bản nháp bài đọc, ngữ pháp, ví dụ Nhật–Việt, nhóm câu hỏi và các lựa chọn. Các phần tiếp nối được gộp theo tên bài/tiêu đề ngữ pháp; tiến độ được lưu sau mỗi nhóm trang.
3. Xem bản nháp cạnh trang gốc. Danh sách **Chỉ hiện bài cần bổ sung** đánh dấu nội dung chưa đủ, ví dụ thiếu dịch hoặc câu chưa có đáp án. Chỉ mở **Chỉnh nội dung** khi cần sửa.
4. Đối chiếu nội dung và đáp án với sách, đánh dấu xác nhận rồi **Duyệt và nhập sách**. Nội dung được kiểm tra trên máy chủ, duyệt rồi nhập trong giao dịch; yêu cầu nhập lại phiên đã hoàn tất trả cùng sách.

Gemini chỉ điền đáp án khi có trang nguồn và chữ đáp án làm bằng chứng. Nếu gặp phụ lục sau các trang bài tập, nó có thể cập nhật đáp án cho câu đã đọc. Đáp án chưa tìm được được để trống và chặn duyệt. Bằng chứng do AI đọc vẫn cần người biên tập đối chiếu; tính năng không bảo đảm chép đủ hoặc đúng toàn bộ tài liệu.

Audio tự ghép khi số CD/track trong nguồn khớp duy nhất với số ở đầu tên file, ví dụ `02 Track 02.m4a` hoặc `CD_02.mp3`. Không ghép theo vị trí tải lên. File trùng số hoặc tên không nhận diện được giữ để người dùng chọn trong phần chỉnh nội dung. Gemini không nghe hoặc phiên âm file audio trong luồng này.

Đúng bản TRY! N3 có SHA-256 trong danh mục được tự điền từ nội dung đã biên tập: 21 bài, 113 ngữ pháp, 198 câu ôn tập. Không cần gọi AI lại. Các bài Check/やってみよう trong từng mục vẫn xem từ trang nguồn; chưa nằm trong 198 câu tương tác.

## Cấu hình Gemini và tiếp tục xử lý

Trong **Chỉnh nội dung → Bài tập**, chọn **Trang câu hỏi** (số trang PDF), tùy chọn **Trang đáp án**, rồi bấm **Nhờ AI hỗ trợ** ở câu cần bổ sung. Gemini nhận nội dung câu đang sửa, ảnh trang câu hỏi và trang kế tiếp, cùng trang đáp án nếu được chọn. Đề xuất gồm câu/lựa chọn đã đối chiếu, đáp án và giải thích tiếng Việt. Kết quả phân biệt **Đáp án từ sách** (kèm trang và bằng chứng), **AI tự giải — cần kiểm tra**, và **Chưa đủ dữ liệu**. Câu nghe thiếu audio/phiên âm không được đoán đáp án. Người dùng bấm **Áp dụng đề xuất vào câu này** rồi lưu bản nháp; API chỉ đề xuất, không ghi hoặc tự duyệt sách. Nếu câu đã thay đổi sau đề xuất, phải nhờ AI kiểm tra lại. Khởi động lại staff để nhận endpoint mới.

- Staff đọc `./.local/gemini.properties` hoặc `../.local/gemini.properties` cùng cấu hình local hiện có. Key dùng theo thứ tự `book-import.ai.api-key`, `gemini.local-api-key`, `gemini.access-key`, `GEMINI_API_KEY`. Key không gửi tới trình duyệt hoặc in vào log.
- Model dùng `book-import.ai.model`, `gemini.model`, `GEMINI_MODEL`, rồi mặc định `gemini-3.1-flash-lite`, tương ứng cấu hình hiện tại của phần tra cứu AI. Endpoint mặc định `https://generativelanguage.googleapis.com/v1beta`.
- Chế độ tự động gửi ảnh trang sách, chữ trích xuất và ngữ cảnh câu hỏi tới Google. Có thể bỏ chọn chế độ này để trích xuất PDF/Windows OCR và biên tập tại máy chủ.
- Lỗi 401/403, 404, 429 hoặc kết nối đưa phiên về **Tạm dừng AI**, giữ các phần đã tạo. **Tiếp tục tự động** chạy từ nhóm trang chưa lưu; không làm lại các nhóm đã hoàn tất. Phiên chưa xử lý hết không được duyệt như một bản tự động hoàn chỉnh.
- **Chuyển sang chỉnh thủ công** giữ bản nháp hiện có, cho người biên tập bổ sung phần còn thiếu và tự chịu trách nhiệm kiểm tra mức độ đầy đủ trước khi duyệt.
- Không tự ghi đè một bản nháp đã có chỉnh sửa bằng việc tạo lại toàn bộ nội dung. Lưu/duyệt kiểm tra phiên bản nháp để phát hiện thay đổi đồng thời.

## Kích hoạt và lưu trữ

- Khởi động lại `nihongo-staff` sau cập nhật; frontend Vite tự nhận thay đổi. Các API/controller và cấu hình key mới cần tiến trình Java mới.
- `ddl-auto: update` thêm hai bảng `book_import_session`, `book_import_page`, các trường chế độ/tiến độ AI và audio vào lesson/exercise. Môi trường quản lý schema bằng migration cần thêm tương ứng trước khi chạy.
- `book-import.storage-directory` mặc định `./.local/book-imports` tính từ thư mục chạy staff. Nên cấu hình đường dẫn tuyệt đối, ổ đĩa bền vững và sao lưu cùng database. Không xóa file của sách đã nhập vì audio vẫn dùng chúng.
- PDF tối đa 100 MB, 500 trang. Audio tối đa 40 MB/file, tổng 300 MB, 128 file/sách; multipart 100 MB/file, 320 MB/request. Proxy phía trước cần cho phép dung lượng/thời gian tải tương ứng.
- Một worker, hàng chờ 16 phiên. Các trạng thái QUEUED/PROCESSING/ANALYZING được khôi phục khi staff khởi động lại. Trang đã render và nhóm AI đã lưu được tái sử dụng.
- Chế độ thủ công dùng Windows OCR ngôn ngữ `ja` cho scan. Khi không có bộ OCR, vẫn xem được trang nguồn và nhập chữ thủ công. Chế độ Gemini đọc ảnh nên không cần Windows OCR.
- Bản nháp, ảnh và audio nháp chỉ cho STAFF/ADMIN. Audio của bài đã nhập kiểm tra quyền truy cập trình độ của học viên, hỗ trợ HTTP Range để tua.

## Kiểm chứng

`BookImportIntegrationTest` dùng H2 riêng và Gemini giả lập, kiểm tra tiếp tục sau lỗi, gộp phần tiếp nối, đáp án thiếu bằng chứng, cập nhật từ phụ lục, ghép audio, bản TRY! N3 đã chuẩn bị, chuyển thủ công, quyền truy cập, rollback và nhập lại.

`BookImportGeminiLiveTest` chỉ chạy khi bật `book-import.live-test=true`; dùng một trang TRY! N3 đã cung cấp, không tạo sách trong database thật. Kiểm tra thực tế đã nhận được bài đọc, ngữ pháp và ví dụ dịch Việt với model mặc định mới. Không chạy bài này trong CI thông thường.

`e2e/book-import.spec.ts` kiểm tra luồng thao tác với API giả lập, gồm bản nháp tự điền, chỉnh một đáp án còn thiếu và duyệt/nhập bằng một nút. Typecheck, kiểm thử frontend và build được chạy riêng. Cần khởi động staff mới để xác minh qua gateway trên ứng dụng đang chạy.
