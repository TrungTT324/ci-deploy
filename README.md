# CI-Deploy Android App

Ứng dụng Android Native (Kotlin) tích hợp WebView hiển thị hệ thống CI-Deploy và hỗ trợ cập nhật OTA tự động thông qua file cấu hình trên web server.

---

## 📌 Thông tin tổng quan (App Metadata)
- **Tên ứng dụng (App Name):** CI-Deploy
- **Package Name:** `hdisoft.app.cideploy`
- **Ngôn ngữ:** Native Android Kotlin (không sử dụng Flutter/React Native)
- **Giao diện chính:** `WebView` hiển thị URL: `http://172.16.100.26:8080`
- **Min SDK:** 21 (Android 5.0)
- **Target / Compile SDK:** 34 (Android 14)

---

## 🎨 Thông số biểu tượng (App Icon Specification)
Biểu tượng ứng dụng được thiết kế tối giản, hiện đại và lưu tại `app/src/main/res/mipmap-*/ic_launcher.png`:
- **Màu nền (Background):** Màu xanh lam nhạt (Light Blue - `#90CAF9`)
- **Hình dáng (Shape):** Bo tròn góc (Rounded Rectangle, góc bo ~22% kích thước tệp).
- **Nội dung chữ (Text Content):**
  - Chữ **CI** lớn (màu trắng).
  - Chữ **deploy** nhỏ hơn (màu trắng).
  - Tỷ lệ kích thước chữ **CI / deploy** là khoảng **7/3**.
  - Bố cục: Chữ `deploy` được xếp bên phải và dịch xuống thấp hơn **1/2** so với chiều cao chữ `CI` để tạo độ cân đối.

---

## 🔄 Cơ chế tự động cập nhật OTA (Auto-Update Mechanism)

### 1. File cấu hình trên Server (`ci-deploy-version.json`)
Tệp JSON này được lưu trữ tại `/opt/homebrew/var/www/apps/ci-deploy/ci-deploy-version.json` để quản lý các phiên bản phát hành:
```json
{
  "appName": "CI-Deploy",
  "icon": "http://172.16.100.26:8080/apps/ci-deploy/icon.png",
  "version": "1.0.1",
  "buildNo": 202607090330,
  "buildNote": "Bổ sung tính năng tự động cập nhật OTA và giao diện tải xuống.",
  "url": "http://172.16.100.26:8080/apps/ci-deploy/CI-Deploy_debug.apk"
}
```
- **buildNo:** Mã build có định dạng `yyyymmddhhmm` (Ví dụ: `202607090330` ứng với ngày 09/07/2026 lúc 03:30).

### 2. Luồng xử lý trong Ứng dụng
1. **Kiểm tra khi khởi động:** Ứng dụng gửi yêu cầu HTTP GET đến `http://172.16.100.26:8080/apps/ci-deploy/ci-deploy-version.json`.
2. **So sánh phiên bản:** So sánh `buildNo` nhận được với mã build hiện tại của ứng dụng (`currentBuildNo` trong `MainActivity.kt`).
3. **Hộp thoại nâng cấp (Upgrade Dialog):** Nếu mã build trên server lớn hơn, hiển thị Dialog **"New Upgrade"** chứa nội dung `buildNote` và hai nút **Yes / No**.
4. **Tiến trình tải xuống (Download UI):** Nhấn **Yes** sẽ hiển thị một Dialog kèm thanh tiến trình trực quan (ProgressBar), hiển thị dung lượng tải xuống (MB đã tải / tổng dung lượng MB) và phần trăm (%).
   - Nút **Cancel** cho phép người dùng hủy bỏ và đóng kết nối mạng ngay lập tức.
5. **Cài đặt (Installation):** Khi tải xong, hiển thị nút **Install** để kích hoạt trình cài đặt hệ thống Android (sử dụng `FileProvider` để mở tệp APK an toàn từ thư mục cache).

---

## 🛠️ Hướng dẫn xây dựng và triển khai cục bộ (Local Build & Deploy)

### Yêu cầu hệ thống
- Java JDK 17
- Android SDK (cấu hình đường dẫn trong `local.properties` bằng biến `sdk.dir`)

### Các lệnh build chính
1. **Gán quyền thực thi cho Gradle wrapper (nếu cần):**
   ```bash
   chmod +x gradlew
   ```
2. **Dọn dẹp và build bản debug APK:**
   ```bash
   ./gradlew clean assembleDebug
   ```
3. **Triển khai tệp APK lên web server cục bộ:**
   - Sao chép tệp APK đã build sang thư mục đích và đổi tên thành `CI-Deploy_debug.apk`:
     ```bash
     cp app/build/outputs/apk/debug/app-debug.apk /opt/homebrew/var/www/apps/ci-deploy/CI-Deploy_debug.apk
     ```
   - Cập nhật liên kết trong trang index của web server tại `/opt/homebrew/var/www/index.html`.

---

## 🚀 CI/CD Pipeline (GitHub Actions)
Quy trình tự động hóa được thiết lập tại `.github/workflows/build-release.yml`:
- **Kích hoạt (Trigger):** Tự động chạy khi có hành động `push` lên nhánh `main` hoặc khi đẩy một tag phiên bản (`v*`).
- **Quy trình:**
  1. Khởi tạo môi trường Ubuntu, tải mã nguồn và thiết lập Java JDK 17.
  2. Build APK bằng lệnh `./gradlew assembleDebug`.
  3. Đổi tên tệp build thành `CI-Deploy_debug.apk`.
  4. Tạo một bản phát hành mới trên GitHub (GitHub Release) với tag duy nhất (dạng `vYYYY.MM.DD-commitSHA`) và tải tệp APK lên trang phát hành làm tài nguyên chính.
