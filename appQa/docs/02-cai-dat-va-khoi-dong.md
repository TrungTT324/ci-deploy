# Cài đặt và khởi động

## Build APK

Chạy từ thư mục gốc repository:

```bash
./gradlew :appQa:assembleDebug
```

APK debug được tạo trong:

```text
appQa/build/outputs/apk/debug/
```

## Cài APK bằng ADB

```bash
adb install -r appQa/build/outputs/apk/debug/appQa-debug.apk
```

Tên package là `hdisoft.app.qa`.

## Các quyền cần thiết

### Accessibility

Từ màn hình chính, chọn **Enable Accessibility**, tìm `appQa` trong Android Settings rồi bật service. Đây là điều kiện để tap, swipe, nhập text và điều hướng hệ thống.

### Draw over other apps

Chọn **Grant Overlay Permission** và bật quyền vẽ trên ứng dụng khác. Nếu chưa có quyền này, QA Automation Service sẽ không khởi động.

### Screen Capture

Nhấn **Start Service**. Android hiển thị hộp thoại Media Projection; chọn cho phép để app có thể chụp và quay màn hình. Quyền này gắn với phiên service hiện tại và có thể phải cấp lại sau khi service dừng.

### Notification

Android 13 trở lên yêu cầu `POST_NOTIFICATIONS`. App hỏi quyền khi mở để foreground service hoạt động ổn định và hiển thị trạng thái.

### Storage

Trên Android 10 trở lên, ảnh/video được lưu bằng MediaStore và không cần quyền ghi bộ nhớ kiểu cũ. `WRITE_EXTERNAL_STORAGE` chỉ được khai báo đến API 28 để tương thích Android cũ.

## Trạng thái trên màn hình chính

Màn hình chính cập nhật mỗi giây các trạng thái:

- Accessibility: `ENABLED` hoặc `DISABLED`.
- Overlay: `GRANTED` hoặc `DENIED`.
- QA Service: `RUNNING` hoặc `STOPPED`.
- Web Server: `ONLINE` hoặc `OFFLINE`.

Web server tự khởi động khi mở MainActivity. Có thể bật/tắt riêng bằng các nút Web Server. Port mặc định hiện tại của `appQa` là **8086** để tránh trùng với ứng dụng CI-Deploy dùng port 8085.

## Kết nối từ máy tính

1. Điện thoại và máy tính phải ở cùng mạng LAN/Wi-Fi.
2. Đọc URL được hiển thị trên MainActivity.
3. Mở `http://<device-ip>:8086/` trên trình duyệt.
4. Nếu không kết nối được, kiểm tra firewall, guest Wi-Fi/client isolation và trạng thái `ONLINE`.

## Dừng ứng dụng an toàn

Nhấn nút Close trên floating control hoặc **Stop Service** trên MainActivity. Khi service bị hủy, app dừng ghi hình nếu còn chạy, giải phóng MediaProjection, gỡ overlay và xóa instance runtime.

