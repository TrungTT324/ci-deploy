# Xử lý sự cố và giới hạn

## Accessibility vẫn hiển thị DISABLED

- Quay lại Android Settings và bật đúng service `appQa`.
- Tắt/bật lại service sau khi cài APK mới.
- Kiểm tra app có bị hệ thống giới hạn accessibility vì cài từ nguồn ngoài hay không.

## Không khởi động được QA Service

- Cấp Overlay trước khi nhấn Start Service.
- Chấp thuận hộp thoại Screen Capture.
- Trên Android 13+, cấp Notification.
- Kiểm tra notification foreground có xuất hiện.
- Dừng phiên cũ rồi xin lại Media Projection nếu phiên đã bị Android thu hồi.

## Tap sai vị trí hoặc không phản hồi

- Xác nhận Accessibility chính đang active.
- Kiểm tra tọa độ theo độ phân giải hiện tại.
- Tránh đổi orientation trong lúc chạy script.
- Tăng thời gian chờ bằng cách tách luồng hoặc mở rộng runner; delay hiện đang cố định.
- Một số màn hình bảo mật hoặc ứng dụng hệ thống có thể chặn gesture.

## Input text không hoạt động

- Đảm bảo ô nhập liệu đang focus và expose Accessibility node.
- Thử tap vào field trước khi gọi input.
- Một số custom view, canvas hoặc field bảo mật không hỗ trợ `ACTION_SET_TEXT`.
- JSON Script Runner hiện chưa có action `input_text`; chỉ API Kotlin của Accessibility service có chức năng này.

## Chụp ảnh timeout hoặc ảnh đen

- Kiểm tra Media Projection còn active.
- Nội dung có `FLAG_SECURE` sẽ không thể chụp bình thường.
- Thử dừng rồi start lại QA Service.
- Không chạy capture và record chồng lấp trên cùng phiên.

## Video không được lưu

- Tránh dừng video ngay sau khi bắt đầu.
- Kiểm tra dung lượng trống.
- Xem log lỗi `MediaRecorder.stop()`.
- Xác nhận file đã xuất hiện trong cả `/reports` và `Movies/QAApp`.

## Không mở được Web Portal

- Dùng port **8086**, không dùng 8085.
- Điện thoại và máy tính phải cùng mạng và không bị client isolation.
- Kiểm tra trạng thái Web Server là `ONLINE`.
- Thử URL `http://<device-ip>:8086/reports`.
- Nếu khung Portal trắng nhưng `/reports` vẫn chạy, kiểm tra Internet/CDN vì Portal dùng Vue từ `unpkg.com`.

## Script API trả success nhưng không có hành động

Với script nhiều step, API chỉ xác nhận runner đã bắt đầu. Với đúng một step, API chờ thực thi và trả action cùng `output`. Nếu không có hành động như mong đợi, kiểm tra từng dependency:

- `open_app`: app/package phải tồn tại.
- `tap`: Accessibility service chính phải active.
- `home`/`back`/`recents`: Accessibility service chính phải active; `output: false` nghĩa global action không được chấp nhận.
- `wait`: không cần service phụ; kiểm tra `input.duration` là number millisecond hợp lệ.
- `clear_recents`: Accessibility service phải active và giao diện Recents phải expose nút `Clear all`, `Close all`, `Dismiss all`, `Xóa/Xoá tất cả` hoặc `Đóng tất cả`. Nếu output báo không tìm thấy nút, OEM đang dùng nhãn/layout khác. Action không force-stop process nền.
- `capture`: QA Automation Service phải active; thành công phải trả URL `/qa/reports/*.png`. Nếu không có preview, mở URL output trực tiếp và kiểm tra file trong `/reports`.
- `record`: QA Automation Service phải active.
- Core action phải là `open_app`, `tap`, `home`, `back`, `recents`, `wait`, `clear_recents`, `capture` hoặc `record`. Có thể kiểm tra danh sách runtime bằng `GET /api/qa/steps`.
- Xem `WebserverLogger` để biết step nào bị bỏ qua hoặc lỗi.

## Giới hạn bảo mật

Webserver dùng HTTP và được thiết kế cho LAN. Không expose port ra Internet. Vì Portal có thể mở app và chạy thao tác trên thiết bị, chỉ kết nối thiết bị vào mạng kiểm thử đáng tin cậy và dừng webserver khi không sử dụng.
