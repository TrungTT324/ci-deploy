# Accessibility và điều khiển thiết bị

## Service đang sử dụng

`QaAccessibilityService` là service duy nhất mà `ScriptTool` và floating control gọi. Service kế thừa `BaseAccessibilityGestureService` để dùng chung gesture dispatch và lifecycle, đồng thời giữ instance trong thời gian được Android kết nối.

## Các thao tác hỗ trợ

### Tap

`clickAt(x, y)` tạo gesture tại tọa độ pixel tuyệt đối. Module có `minSdk 24` vì API `dispatchGesture()` chỉ tồn tại từ Android 7.0; Accessibility service phải đang active.

### Swipe

`swipe(startX, startY, endX, endY, durationMs)` tạo đường vuốt giữa hai điểm. Thời lượng mặc định là 300 ms.

### Input text

`inputText(text)` thử đặt text vào node đang focus trước. Nếu không tìm thấy, service duyệt cây UI để tìm node editable. Ứng dụng đích có thể từ chối thao tác nếu field không expose Accessibility action.

### Điều hướng hệ thống

Service hỗ trợ các global action:

- Back
- Home
- Recents

Floating control hiện cung cấp Back và Home. Script runner và Action List cung cấp thêm Recents.

## Floating Control

Các nút theo chức năng:

- Drag: kéo thanh công cụ đến vị trí khác.
- Camera: chụp ảnh màn hình.
- Record: bắt đầu hoặc dừng quay video.
- AUTO: chạy chuỗi demo tap, nhập text, tap và capture.
- Back/Home: gửi global action.
- Close: dừng QA Automation Service.

Overlay dùng tọa độ tuyệt đối. Khi thiết bị đổi độ phân giải, density, navigation mode hoặc orientation, tọa độ trong script có thể không còn chính xác.

## Điều kiện chạy ổn định

- Giữ màn hình mở và thiết bị đã unlock.
- Đợi ứng dụng đích render xong trước khi gửi gesture.
- Dùng tọa độ theo đúng độ phân giải thiết bị đang kiểm thử.
- Kiểm tra trạng thái Accessibility sau khi app được cập nhật/cài lại vì Android có thể tắt service.
