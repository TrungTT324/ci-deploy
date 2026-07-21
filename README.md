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

---

## 🖱️ Module AppMouse (Bluetooth HID Mouse)

`appMouse` là một ứng dụng Android **độc lập** (module Gradle riêng, APK riêng — không phải feature của app CI-Deploy) biến điện thoại thành một **con chuột Bluetooth** để điều khiển máy tính/laptop/TV, tương tự các app "phone as mouse" ngoài Play Store.

### Thông tin module
- **Package Name:** `hdisoft.app.mouse`
- **Thư mục mã nguồn:** `appMouse/`
- **Min SDK:** 21 (Android 5.0) — app cài được trên mọi thiết bị, nhưng tính năng chuột Bluetooth (`android.bluetooth.BluetoothHidDevice`) chỉ hoạt động thật từ **Android 9 (API 28)** trở lên; máy cũ hơn vẫn mở app được, nút Start/Discoverable chỉ bị disable kèm thông báo "not supported".
- **Thư viện dùng chung:**
  - `libs/bluetooth` — lớp kết nối `BluetoothHidMouseConnector`.
  - `libs/appupdate` + `libs/core` — tự kiểm tra & cài bản cập nhật OTA (xem mục "Tự động cập nhật (OTA)" bên dưới).

### Cơ chế hoạt động — vì sao thiết bị khác nhận ra đây là "chuột thật"
Khác với tính năng Bluetooth chat/host sẵn có trong app CI-Deploy (dùng socket RFCOMM/SPP để 2 máy chạy cùng app này nói chuyện với nhau), `appMouse` đăng ký trực tiếp vào **Bluetooth HID Device Profile** của hệ điều hành:
1. Gọi `BluetoothAdapter.getProfileProxy(..., BluetoothProfile.HID_DEVICE)` để lấy proxy `BluetoothHidDevice`.
2. Đăng ký ứng dụng (`registerApp`) với một **HID report descriptor** chuẩn của chuột (3 nút bấm + di chuyển tương đối X/Y + con lăn), cùng `SUBCLASS1_MOUSE` trong SDP settings.
3. Khi máy tính/thiết bị khác quét & ghép đôi Bluetooth, nó thấy điện thoại này y hệt một con chuột Bluetooth thật (không cần cài app gì ở phía máy tính) — vì mọi hệ điều hành đều hỗ trợ sẵn driver HID chuẩn.
4. Ngay khi ghép đôi (bond) xong, app **chủ động gọi `hidDevice.connect(device)`** (lắng nghe `ACTION_BOND_STATE_CHANGED`) thay vì chỉ chờ host tự kết nối — cần thiết vì một số host (điển hình là **macOS**) chỉ bond ở tầng liên kết Bluetooth chứ không tự mở kênh HID Device Profile; thiếu bước chủ động này thì host báo "Connected" nhưng chuột không phản ứng gì cả.
5. Sau khi kết nối, mọi thao tác chạm trên điện thoại được gửi đi dưới dạng **HID input report** (`sendReport`) tới máy chủ.

Lớp `BluetoothHidMouseConnector` (dùng chung qua `libs/bluetooth`, package `hdisoft.app.cideploy.features.bluetooth.data`) đóng gói toàn bộ logic đăng ký/hủy đăng ký HID profile, chủ động connect khi bond xong, và gửi report.

### Giao diện & cách sử dụng
1. Mở app **AppMouse** trên điện thoại (có Bluetooth; cần Android 9+ để tính năng chuột hoạt động).
2. Nhấn nút **Start**: app tự xin các quyền Bluetooth cần thiết (`BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, và `BLUETOOTH_SCAN` trên Android 12+), bật Bluetooth nếu đang tắt, đăng ký làm thiết bị HID chuột, rồi **tự động bật Discoverable ngay sau đó** — không cần bấm thêm nút Discoverable (nút này chỉ dùng để bật lại thủ công nếu cửa sổ hiển thị hết hạn giữa chừng).
3. Trên máy tính/TV: vào phần cài đặt Bluetooth, quét và ghép đôi với thiết bị có tên **trùng với tên Bluetooth của chính điện thoại** (ví dụ "Galaxy A55", không phải tên app) — không cần phần mềm hay driver bổ sung. Tên này cũng hiện ngay trên app khi đã discoverable ("Discoverable as \"...\"").
4. Sau khi ghép đôi, trạng thái trên app chuyển thành "Connected to …" và có thể điều khiển:
   - **Vùng touchpad (giữa màn hình):** kéo 1 ngón để di chuyển con trỏ; chạm nhanh 1 ngón = click chuột trái; chạm nhanh 2 ngón = click chuột phải; kéo 2 ngón theo chiều dọc = cuộn trang.
   - **Nút Left / Right** ở dưới cùng: click trái/phải tường minh.
   - **Nút ▲ / ▼:** cuộn lên/xuống.
5. Nhấn **Stop** để hủy đăng ký HID (ngắt kết nối chuột ảo) khi không dùng nữa.

**Lưu ý riêng cho macOS:** nếu Mac quét Bluetooth mà không thấy điện thoại xuất hiện dù các thiết bị khác vẫn thấy được, thử mở **System Settings → Bluetooth** (không chỉ icon trên menu bar) và chờ quét khoảng 15–30 giây, hoặc dùng **Bluetooth Setup Assistant** (Spotlight) — macOS đôi khi bỏ sót thiết bị HID lạ trong danh sách quét nhanh.

### 🔄 Tự động cập nhật (OTA)
`appMouse` tích hợp module dùng chung `libs/appupdate` (xem `libs/appupdate/README.md` nếu muốn tích hợp module này vào app khác):
- Khi mở app, `AppUpdateController` tự kiểm tra bản mới qua URL JSON cấu hình sẵn (`appmouse-version.json`), so sánh `buildNo` với `BuildConfig.BUILD_NO` hiện tại.
- Có bản mới hơn → hiện dialog "New update available" → tải về (dialog progress bar, có thể Cancel) → xác minh sha256/kích thước/chữ ký (`AppUpdateInstaller.verifyApk`, `libs/appupdate`) → mở màn hình cài đặt hệ thống.
- **Publish bản mới:** chạy `./build_local.sh` ở thư mục gốc repo — tự build debug APK, publish với tên file có timestamp (luôn chỉ giữ đúng 1 file APK trên server), và cập nhật cả `appmouse-version.json` (cho OTA check) lẫn `app.json` (cho trang portal `ci-deploy/index.html`).
