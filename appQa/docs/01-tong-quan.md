# Tổng quan appQa

## Mục tiêu

`appQa` là công cụ QA Automation chạy native trên Android. Ứng dụng cho phép kỹ sư kiểm thử điều khiển thao tác trên thiết bị, chụp/quay trạng thái màn hình, chạy chuỗi hành động JSON và xem báo cáo từ trình duyệt trong cùng mạng nội bộ.

Ứng dụng hỗ trợ Android API 24 trở lên, target API 34 và được viết bằng Kotlin/JVM 17. API 24 là mức tối thiểu vì action gesture dùng `AccessibilityService.dispatchGesture()`.

## Nhóm tính năng chính

### Điều khiển thiết bị bằng Accessibility

- Chạm tại tọa độ màn hình.
- Vuốt giữa hai tọa độ với thời lượng tùy chỉnh.
- Điền văn bản vào ô nhập liệu đang focus hoặc ô có thể chỉnh sửa.
- Gửi hành động hệ thống Back, Home và Recents.
- Gesture dùng chung implementation trong `BaseAccessibilityGestureService`.

### Floating Control

Khi QA Automation Service chạy, một thanh điều khiển nổi xuất hiện trên ứng dụng khác. Thanh này có thể kéo thả và cung cấp thao tác chụp ảnh, bắt đầu/dừng quay, chạy demo tự động, Back, Home và đóng service.

### Screen Capture và Screen Record

- Chụp ảnh PNG theo đúng kích thước màn hình vật lý.
- Quay MP4/H.264, 30 fps, bitrate 3 Mbps.
- Tự ẩn floating control trước khi bắt hình.
- Lưu một bản cho Web Report và một bản vào Gallery của thiết bị.

### Web Portal và Script Library

Web server tích hợp cung cấp:

- Dashboard điều khiển tại `http://<device-ip>:8086/`.
- Thư viện tạo, sửa, xóa và chạy script.
- Action List hai cột để xem 9 core step, chỉnh input, chạy thử và xem output.
- API `GET /api/qa/steps` lấy definition động từ `ActionRegistry`.
- Test Console để mở app, tap và capture/record nhanh.
- Trang xem ảnh/video báo cáo tại `/reports`.

## Luồng sử dụng tiêu chuẩn

1. Mở `appQa` và bật Accessibility.
2. Cấp quyền Draw over other apps.
3. Nhấn Start Service và xác nhận Screen Capture của Android.
4. Dùng floating control hoặc truy cập Web Portal từ máy tính cùng mạng.
5. Chạy thao tác kiểm thử.
6. Xem ảnh/video trong `/reports` hoặc trong Gallery.

## Thành phần ngoài phạm vi

`appQa` không phải framework assertion hoàn chỉnh. Script hiện tại điều phối hành động và thu thập bằng chứng; nó chưa có cú pháp kiểm tra nội dung UI, OCR, selector theo resource ID, retry có điều kiện hoặc kết quả pass/fail theo assertion.

Android không cấp cho app thường quyền force-stop ứng dụng khác. Vì vậy registry không cung cấp `stop_all`; action `clear_recents` chỉ xóa các task mà giao diện Recents expose qua Accessibility và không tuyên bố dừng process nền.
