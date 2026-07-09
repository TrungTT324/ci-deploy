# Google Gemini Workspace Context: CI-Deploy

Tệp này cung cấp ngữ cảnh, cấu trúc và tiêu chuẩn phát triển của dự án **CI-Deploy** cho Gemini khi khởi chạy trong workspace này.

---

## 📱 Thông tin dự án
- **Tên ứng dụng:** CI-Deploy (Android Native Kotlin)
- **Package Name:** `hdisoft.app.cideploy`
- **Màn hình chính:** `WebView` trỏ tới `http://172.16.100.26:8080`
- **Compile SDK:** 34 | **Min SDK:** 21

---

## 🛠️ Cấu trúc và Tính năng chính

### 1. Luồng Cập nhật OTA tự động (Auto-Update)
- Ứng dụng tự động kiểm tra phiên bản mới từ máy chủ qua tệp cấu hình JSON:
  `http://<host>:8080/apps/ci-deploy/ci-deploy-version.json`
- **So sánh Build**: So khớp `buildNo` (dạng `yyyymmddhhmm`) giữa server và client. Nếu server có mã build lớn hơn, hiển thị Dialog thông báo cập nhật **"New Upgrade"**.
- **Tiến trình tải xuống (Download UI)**:
  - Khi đồng ý tải (nhấn **Yes**), hiển thị Dialog ProgressBar kèm trạng thái tải chi tiết (đã tải MB / tổng số MB và tỷ lệ phần trăm %).
  - Cho phép hủy bỏ tiến trình tải bất kỳ lúc nào bằng nút **Cancel** (hệ thống sẽ đóng coroutine kết nối mạng).
- **Cài đặt**: Hoàn thành tải xuống sẽ mở nút **Install** kích hoạt trình cài đặt mặc định thông qua Android `FileProvider`.

### 2. Tìm kiếm Host tự động (Host Discovery)
- **Khi ứng dụng khởi chạy**:
  - Đọc địa chỉ IP host đã lưu trong `SharedPreferences`.
  - Nếu đã lưu, gửi yêu cầu kiểm tra tệp `ci-deploy-version.json` từ host này. Nếu hoạt động tốt, sử dụng luôn mà không cần quét lại mạng.
  - Nếu chưa lưu hoặc host cũ không hoạt động, ứng dụng sẽ lấy subnet của mạng Wi-Fi cục bộ (ví dụ: `192.168.1.`) và tiến hành quét đồng thời 254 địa chỉ IP (`1..254`) trên cổng `8080`.
- **Thuật toán quét song song**: Sử dụng Kotlin Coroutine `Channel` để khởi chạy đồng thời các tiến trình kiểm tra. Khi tìm thấy địa chỉ IP hợp lệ đầu tiên, nó sẽ gửi vào Channel, lập tức hủy toàn bộ 253 tiến trình còn lại để tối ưu hóa hiệu năng và kết nối ngay lập tức.
- **Lưu trữ & Dự phòng**: Host tìm thấy sẽ được lưu lại. Nếu không tìm thấy host nào hoạt động, hiển thị Dialog thông báo kèm nút **Retry** để quét lại.

### 3. Cấu hình Native Android
- **MainActivity.kt:** Nằm tại `app/src/main/java/hdisoft/app/cideploy/MainActivity.kt`. Kế thừa trực tiếp lớp `Activity` chuẩn để tương thích với giao diện gốc không có tiêu đề `@android:style/Theme.Light.NoTitleBar`.
- **AndroidManifest.xml:** Định nghĩa quyền `INTERNET`, `REQUEST_INSTALL_PACKAGES` và đăng ký thành phần `FileProvider`.
- **FileProvider:** Cấu hình đường dẫn cho phép chia sẻ tệp APK tải về tại `app/src/main/res/xml/file_paths.xml`.

---

## ⚙️ Các lệnh phát triển
- **Build APK Debug:** `./gradlew assembleDebug`
- **Triển khai cục bộ:** Copy tệp APK đã build sang thư mục Nginx:
  ```bash
  cp app/build/outputs/apk/debug/app-debug.apk /opt/homebrew/var/www/apps/ci-deploy/CI-Deploy_debug.apk
  ```
- **File HTML Triển khai:** Cập nhật trang [/opt/homebrew/var/www/index.html](file:///opt/homebrew/var/www/index.html) để thêm liên kết tải về trực tiếp.

---

## 🤖 Chỉ dẫn cho AI Assistant
- Dự án này đã được **di chuyển hoàn toàn khỏi Flutter** sang Native Kotlin. Tuyệt đối không tạo lại hay sử dụng bất kỳ tệp tin hoặc lệnh nào liên quan tới Flutter (`lib/`, `pubspec.yaml`, `flutter build`, v.v.).
- Toàn bộ tính năng tương tác mạng sử dụng Java/Kotlin HTTP chuẩn kết hợp với `kotlinx.coroutines` để tối ưu hóa hiệu suất ứng dụng.
