# appQa - QA Automation Tool (Android Native)

`appQa` là một ứng dụng hỗ trợ kiểm thử tự động (QA Automation) trên hệ điều hành Android. Ứng dụng tích hợp khả năng giả lập thao tác cử chỉ (nhấn/gõ chữ) lên các ứng dụng khác mà không cần root, hiển thị thanh điều khiển nổi (Floating Control Overlay) và hỗ trợ chụp ảnh/quay phim màn hình lưu trữ trực tiếp vào thư viện thiết bị.

---

## 📱 Các Tính Năng Chính

1. **Điều Khiển Hệ Thống Không Cần Root (Accessibility Automation)**:
   * Giả lập thao tác chạm (`clickAt`) và vuốt màn hình (`swipe`) bất kỳ dựa trên tọa độ X, Y thông qua cấu hình cử chỉ trợ năng hệ thống.
   * Định vị và tự động nhập văn bản (`inputText`) trực tiếp vào ô nhập liệu đang có tiêu điểm (focused input text fields) trên bất kỳ ứng dụng nào khác.
   * Giả lập nhanh các nút bấm vật lý của hệ thống như **Back** (Quay lại) và **Home** (Trở về màn hình chính).

2. **Bảng Điều Khiển Nổi (Interactive Floating Overlay)**:
   * Hiển thị một thanh công cụ nổi đè lên trên các ứng dụng khác (`SYSTEM_ALERT_WINDOW`) với thiết kế Dark Mode tối giản, bo góc cao cấp.
   * Hỗ trợ kéo thả (drag) linh hoạt khắp mọi nơi trên màn hình để không cản trở quá trình sử dụng.
   * Cho phép kích hoạt nhanh chụp ảnh màn hình, bắt đầu/dừng quay phim màn hình và chạy kịch bản tự động demo.

3. **Chụp và Quay Màn Hình Độc Lập (Media Projection Engine)**:
   * **Chụp Ảnh Màn Hình (`ScreenCaptureHelper`)**: Đọc luồng đệm pixel trực tiếp từ `ImageReader` (định dạng `RGBA_8888`), tự động cắt bỏ khoảng đệm thừa (row padding stride) để tạo ảnh chụp sắc nét có kích thước khớp 100% với màn hình vật lý.
   * **Quay Phim Màn Hình (`ScreenRecordHelper`)**: Sử dụng bộ giải mã `MediaRecorder` mã hóa H264 (3 Mbps, 30fps, MPEG_4) bắt hình từ Virtual Display và xuất ra tệp video `.mp4`.
   * **Lưu Trữ Scoped-Storage (`MediaSaveHelper`)**: Toàn bộ ảnh chụp và video được xuất trực tiếp vào các thư mục công khai của thiết bị (`Pictures/QAApp` và `Movies/QAApp`) qua MediaStore, hoạt động hoàn hảo trên Android 10+ đến Android 14+ mà không cần xin quyền đọc ghi bộ nhớ vật lý.

4. **Kịch Bản Tự Động Demo (Demo Automation Script)**:
   * Khi kích hoạt nút **AUTO** trên bảng điều khiển:
     1. Tự động ẩn thanh công cụ nổi tạm thời để tránh che mất nội dung màn hình.
     2. Đợi 1.5 giây, mô phỏng cử chỉ click vào vị trí trung tâm màn hình.
     3. Tự động nhập nội dung văn bản: *"CI-Deploy QA Automation Active"*.
     4. Đợi 1.5 giây, mô phỏng cử chỉ click xuống phía dưới nút trung tâm khoảng 200px (thường là nút Tìm kiếm / Submit).
     5. Đợi 1.5 giây, tự động chụp ảnh màn hình ghi lại trạng thái kết quả.
     6. Lưu hình ảnh báo cáo vào Gallery, khôi phục lại hiển thị của thanh điều khiển nổi và thông báo hoàn thành kịch bản.

5. **Tích Hợp Web Server Xem Báo Cáo Từ Xa (Web Server Integration)**:
   * Tự động khởi chạy một Web Server HTTP siêu nhẹ trên cổng `8085` ngay khi QA Service được bật.
   * Cung cấp trang web xem báo cáo trực quan `/reports` (hoặc `/qa/reports.html`) có giao diện Dark Mode hiện đại để duyệt danh sách, xem trước ảnh chụp, phát video quay màn hình và tải xuống các báo cáo về máy tính.
   * Hiển thị địa chỉ IP và URL truy cập web server trực tiếp trên giao diện chính của ứng dụng (`MainActivity`) để người dùng dễ dàng kết nối từ máy tính cùng mạng Wi-Fi.

---

## 🛠️ Kiến Trúc và Các Lớp Kỹ Thuật

* **`MainActivity`**: Màn hình trung tâm quản lý trạng thái các quyền trợ năng và điều khiển dịch vụ.
* **`QaAccessibilityService`**: Kế thừa `AccessibilityService`, nắm giữ instance tĩnh (singleton-like access) để nhận lệnh giả lập cử chỉ thông qua `dispatchGesture` trên API 24+ và truy cập nút view cây phân cấp thông qua `rootInActiveWindow`.
* **`QaAutomationService`**: Dịch vụ nền nổi bật (Foreground Service) kết nối với phiên ghi hình `MediaProjection`. Nó trực tiếp quản lý và cập nhật vòng đời của Floating View trên `WindowManager`.
* **`ScreenCaptureHelper` & `ScreenRecordHelper`**: Đóng gói logic chuyển đổi Surface VirtualDisplay kết nối giữa MediaProjection và các consumer (`ImageReader` / `MediaRecorder`).
* **`MediaSaveHelper`**: Tích hợp các câu lệnh tương tác của `ContentResolver` giúp xuất tệp đa phương tiện an toàn.
* **`HttpWebServerService` & `SimpleHttpServer`**: Dịch vụ nền chạy máy chủ web HTTP trên cổng `8085`. Server được cấu hình thêm các REST API:
  * `GET /api/qa/files`: Trả về mảng JSON chứa danh sách toàn bộ các file ảnh/video báo cáo có trong thư mục của ứng dụng kèm dung lượng và thời gian tạo.
  * `GET /qa/reports/{filename}`: Stream trực tiếp dữ liệu nhị phân của hình ảnh hoặc video được chọn về trình duyệt.
  * `GET /reports` hoặc `/qa/reports.html`: Trả về giao diện Dashboard để duyệt xem và tải báo cáo.

---

## 🔑 Danh Sách Các Quyền Cần Thiết

Ứng dụng khai báo các quyền sau trong [AndroidManifest.xml](file:///Users/trungtruong/xsofts_prj/web/github/ci-deploy/appQa/src/main/AndroidManifest.xml):

* `android.permission.SYSTEM_ALERT_WINDOW`: Cho phép ứng dụng vẽ đè lên màn hình.
* `android.permission.BIND_ACCESSIBILITY_SERVICE`: Quyền bắt buộc để đăng ký làm Trợ năng hệ thống (Accessibility Service).
* `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Cho phép dịch vụ nền hoạt động cùng quyền chụp màn hình trên Android 10+.
* `android.permission.POST_NOTIFICATIONS`: Cho phép đẩy thông báo dịch vụ chạy ngầm trên Android 13+ (API 33+).
* `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`): Tương thích ngược lưu file cho các thiết bị chạy Android 9 trở xuống.

---

## 🚀 Hướng Dẫn Cài Đặt và Sử Dụng

### Bước 1: Biên dịch ứng dụng
Sử dụng công cụ Gradle ở thư mục gốc để build APK:
```bash
./gradlew :appQa:assembleDebug
```
Tệp tin APK đầu ra nằm tại: `appQa/build/outputs/apk/debug/appQa-debug.apk`

### Bước 2: Cài đặt và Bật Quyền
1. Cài đặt tệp APK lên thiết bị của bạn.
2. Khởi chạy ứng dụng **appQa**.
3. Cấp các quyền thông qua giao diện chính của ứng dụng:
   * Nhấn **Enable Accessibility** -> Chọn ứng dụng **appQa** trong danh sách và kích hoạt Trợ năng (Accessibility).
   * Nhấn **Grant Overlay Permission** -> Cho phép **appQa** vẽ đè lên các ứng dụng khác (Draw over other apps).
4. Nhấn **Start Service** -> Ứng dụng sẽ hiển thị hộp thoại xác nhận quyền quay chụp màn hình của hệ thống. Nhấn đồng ý (**Start Now**).

### Bước 3: Sử dụng Bảng Điều Khiển Nổi
Khi dịch vụ hoạt động, một thanh công cụ màu xám đậm sẽ xuất hiện trên màn hình:
* **Grip Icon (Kéo thả)**: Chạm và di chuyển icon đầu tiên bên trái để thay đổi vị trí của thanh công cụ.
* **Camera Icon (Chụp ảnh)**: Nhấn để chụp màn hình hiện tại (không chứa thanh công cụ). Ảnh chụp được lưu tại thư mục `Pictures/QAApp` trong Gallery.
* **Red Circle Icon (Quay phim)**: Nhấn để bắt đầu ghi màn hình. Icon chuyển sang hình vuông màu trắng. Nhấn lại nút này để dừng và lưu video tại thư mục `Movies/QAApp` trong Gallery.
* **AUTO Button (Chạy kịch bản)**: Nhấn để kích hoạt chuỗi giả lập click và gõ văn bản tự động.
* **Back & Home Icons**: Nhấn để kích hoạt nhanh hành động quay lại hoặc quay về màn hình chính.
* **Close Icon (Dừng dịch vụ)**: Nhấn nút đỏ cuối cùng bên phải để tắt hoàn toàn bảng điều khiển nổi và dừng dịch vụ chạy ngầm.

### Bước 4: Truy Cập Web Server Xem Báo Cáo Từ Máy Tính
1. Đảm bảo máy tính của bạn và thiết bị chạy ứng dụng đang kết nối chung một mạng Wi-Fi cục bộ.
2. Khi QA Service ở trạng thái **RUNNING**, quan sát dòng thông báo trên màn hình ứng dụng:
   `Web server active at: http://<địa_chỉ_IP>:8085/reports`
3. Mở trình duyệt web trên máy tính và truy cập vào địa chỉ URL trên để bắt đầu theo dõi, xem trực tiếp và tải các video, hình ảnh báo cáo kiểm thử.
