# Kiến trúc kỹ thuật

## Các thành phần chính

### MainActivity

Điểm vào của ứng dụng. Thành phần này:

- Hiển thị và polling trạng thái quyền/service mỗi giây.
- Mở Android Settings để cấp Accessibility và Overlay.
- Xin quyền Media Projection.
- Start/stop QA Automation Service và Web Server.
- Đặt port webserver mặc định thành 8086.
- Thử tự cấp một số quyền khi phát hiện thiết bị root.

### QaAutomationService

Foreground service quản lý vòng đời Media Projection, floating overlay, capture/record và kết nối callback chạy script cho webserver.

### QaAccessibilityService

Kế thừa `BaseAccessibilityGestureService` để thực thi gesture và quản lý lifecycle kết nối. Service bổ sung input text, tìm nút Clear all và global action; `ScriptTool` truy cập instance runtime này để chạy action.

### ScriptTool

Parse JSON array bằng Gson thành `ActionModel` và gọi `doIt()` theo thứ tự. Một step chạy đồng bộ để Web Action List nhận `output`; script nhiều step chạy trong coroutine `Dispatchers.Default`. Gesture được chuyển về main looper.

### ActionModel và ActionRegistry

- `ActionModel` định nghĩa contract runtime, ID tăng dần, Gson serialization và method `doIt()`.
- `CoreActions.kt` chứa implementation của từng core step.
- `ActionRegistry` là nguồn dữ liệu duy nhất cho factory, category và definition của 9 core step.
- Khi thêm core step, implement concrete `ActionModel` rồi gọi `registerCore(...)`; Gson và Web Action List cùng nhận mapping mới.

### ScreenCaptureHelper

Đóng gói `VirtualDisplay`, `ImageReader` và chuyển raw buffer thành Bitmap.

### ScreenRecordHelper

Đóng gói `MediaRecorder`, surface và `VirtualDisplay` cho video H.264.

### MediaSaveHelper

Lưu media vào app-specific reports và MediaStore/Gallery.

### Webserver package

Module phụ thuộc artifact `hdisoft.app:webserver:1.0.0`. Assets HTML/CSS/JS của Portal nằm trong `appQa/src/main/assets`. `QaWebRequestHandler` phục vụ `GET /api/qa/steps` từ registry ngay khi MainActivity khởi tạo web server. `SimpleHttpServer.scriptExecutor` được gắn với `ScriptTool` khi QA Automation Service khởi động.

## Luồng lệnh từ Web Portal

```text
Browser
  -> HTTP API :8086
  -> SimpleHttpServer
  -> QaWebRequestHandler (GET /api/qa/steps)
  -> ScriptExecutor callback
  -> ScriptTool
  -> AppTool / QaAccessibilityService / QaAutomationService
  -> Android app hoặc Media Projection
  -> reports + Gallery
```

## Vòng đời quan trọng

- Web server có thể chạy độc lập với QA Automation Service.
- `GET /api/qa/steps` hoạt động khi web server online, không phụ thuộc Media Projection.
- `wait` không cần Accessibility hoặc Media Projection.
- `clear_recents` cần Accessibility và chỉ xóa task được giao diện Recents expose; không force-stop process.
- `open_app` có thể hoạt động khi server online.
- `tap`, `home`, `back`, `recents` cần Accessibility service chính active.
- `capture` và `record` cần QA Automation Service cùng Media Projection active.
- Khi QA Automation Service được start, callback ScriptExecutor được đăng ký cho webserver.

## Cấu hình build

- Namespace/application ID: `hdisoft.app.qa`
- minSdk: 24, vì action gesture dùng `AccessibilityService.dispatchGesture()`.
- targetSdk/compileSdk: 34
- Java/Kotlin target: 17
- Version: 1.0.0
- `BUILD_NO`: sinh theo thời gian build với định dạng `yyyyMMddHHmm`

## Điểm cần chú ý khi bảo trì

- Port nguồn sự thật được set trong `MainActivity`; tránh hard-code port khác trong toast hoặc tài liệu.
- Core step mới cần concrete `ActionModel` và một entry `ActionRegistry.registerCore(...)`; không thêm nhánh dispatch vào `ScriptTool`.
- Action List tải definition từ `/api/qa/steps`; không khai báo lại action trong `assets/js/app.js`.
- Nếu đổi contract của definition API, cập nhật client đọc API và các tài liệu liên quan.
- Các thao tác media phải giải phóng `VirtualDisplay`, `ImageReader` và `MediaRecorder` trong cả luồng thành công lẫn lỗi.
