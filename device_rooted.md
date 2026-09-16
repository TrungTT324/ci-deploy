# Hướng dẫn Tạo và Cấu hình Thiết bị Giả lập Android Rooted

Tài liệu này hướng dẫn cách tạo, cấu hình tối ưu và sửa các lỗi thường gặp (như phím Back, Home, Menu không hoạt động, thiếu dung lượng đĩa) để xây dựng một thiết bị giả lập Android rooted hoạt động chính xác cho dự án **CI-Deploy**.

---

## 1. Quy trình Tạo Thiết bị Giả lập Rooted qua CLI

Để thiết bị giả lập có thể kích hoạt quyền root trực tiếp qua ADB (`adb root`), ta **bắt buộc** phải sử dụng System Image thuộc nhánh **Google APIs** (không sử dụng nhánh **Google Play Store** do tính chất bảo mật của Play Store ngăn cản việc chạy ADB dưới quyền root).

### Bước 1: Liệt kê và tải System Image phù hợp
Sử dụng `sdkmanager` kiểm tra và tải phiên bản Android phù hợp (ví dụ Android 14 - API 34 Google APIs trên máy chip Apple Silicon):
```bash
# Tải System Image Google APIs cho arm64-v8a
sdkmanager "system-images;android-34;google_apis;arm64-v8a"
```

### Bước 2: Tạo thiết bị ảo (AVD)
Sử dụng `avdmanager` để khởi tạo máy ảo:
```bash
# Tạo máy ảo mang tên Pixel_6_Rooted_API_34 mô phỏng Pixel 6
echo "no" | avdmanager create avd -n "Pixel_6_Rooted_API_34" -k "system-images;android-34;google_apis;arm64-v8a" -d "pixel_6" -f
```

---

## 2. Cấu hình Tối ưu & Khắc phục Lỗi Phím cứng (Back, Home, Menu)

Sau khi tạo bằng CLI, cấu hình mặc định của AVD thường thiếu một số thông số quan trọng dẫn tới các lỗi như:
1. **Lỗi phím Back, Home, Recents (Menu) và phím âm lượng không hoạt động.**
2. **Không đủ dung lượng đĩa cứng (Disk Space) để khởi tạo phân vùng ghi đè hệ thống (`-writable-system`).**

Để sửa các vấn đề này, hãy mở và chỉnh sửa tệp tin cấu hình máy ảo tại:
`~/.android/avd/<Tên_AVD>.avd/config.ini`

### Các thuộc tính cần cập nhật:

#### 1. Kích hoạt bàn phím ảo để ánh xạ phím cứng (Sửa lỗi liệt nút Back/Home)
Mặc định CLI có thể tạo với thuộc tính `hw.keyboard = no`. Cần sửa lại thành `yes` để trình giả lập có thể chuyển đổi click chuột trên thanh công cụ thành các mã quét phím tương ứng (Back, Home, Menu):
```ini
hw.keyboard = yes
```

#### 2. Cấu hình giao diện thiết bị (Skin) để hiển thị viền máy ảo chính xác
Thêm đường dẫn Skin cho máy ảo để các phím ảo hoạt động đồng bộ:
```ini
skin.dynamic = yes
skin.name = pixel_6
skin.path = /Users/trungtruong/Library/Android/sdk/skins/pixel_6
```

#### 3. Giảm kích thước phân vùng Userdata (Sửa lỗi thiếu bộ nhớ đĩa cứng)
Khi chạy máy ảo với cờ `-writable-system`, hệ thống sẽ sao chép toàn bộ ảnh phân vùng gốc sang thư mục máy ảo để cho phép ghi đè, yêu cầu tối thiểu khoảng 7.3 GB bộ nhớ trống. Nếu đĩa cứng máy Mac còn ít dung lượng, hãy giảm dung lượng phân vùng dữ liệu người dùng (`disk.dataPartition.size`) từ 6 GB xuống 2 GB (`2147483648`) để khởi động thành công:
```ini
disk.dataPartition.size = 2147483648
```

---

## 3. Khởi chạy và Kích hoạt Quyền Root

### Bước 1: Khởi chạy máy ảo ở chế độ lạnh (Cold Boot)
Sau khi chỉnh sửa `config.ini`, bạn phải chạy máy ảo với tham số `-no-snapshot-load` ở lần đầu tiên để nạp lại toàn bộ cấu hình mới và ghi đè snapshot cũ:
```bash
emulator -avd Pixel_6_Rooted_API_34 -writable-system -no-snapshot-load &
```

### Bước 2: Kích hoạt quyền Root qua ADB
Khi máy ảo đã khởi động hoàn tất, thực hiện các lệnh sau để chuyển ADB sang quyền root và mount ghi đè hệ thống:
```bash
# Chuyển ADB daemon sang quyền root
adb root

# Mount ghi đè hệ thống (nếu cần chỉnh sửa file /system)
adb remount
```
*Lưu ý: Nếu chạy lệnh `adb shell whoami` trả về kết quả là `root`, thiết bị ảo của bạn đã được rooted thành công.*

---

## 4. Tự động cấp quyền Cài đặt Ứng dụng Không rõ Nguồn gốc (OTA Bypass)

Mặc định khi cài đặt tệp APK từ bên ngoài hệ thống (OTA Update), hệ điều hành Android sẽ hiển thị màn hình cài đặt "Install unknown apps" để yêu cầu người dùng cho phép thủ công.

Đối với môi trường kiểm thử (Emulator), ta có thể sử dụng quyền quản trị qua ADB để tự động cấp quyền này cho ứng dụng **CI-Deploy** ngay từ đầu, tránh việc hiển thị Settings phiền phức:
```bash
adb shell appops set hdisoft.app.cideploy REQUEST_INSTALL_PACKAGES allow
```
sau khi chạy lệnh này, ứng dụng sẽ có quyền cài đặt gói cập nhật tự động trực tiếp mà không cần bật cài đặt thủ công.

---

## 5. Khắc phục lỗi `Permission denied` khi thực thi lệnh `su` từ ứng dụng

Nếu ứng dụng ghi nhận lỗi:
`java.io.IOException: Cannot run program "su": error=13, Permission denied`

### Nguyên nhân:
Mặc định trên các thiết bị ảo Google APIs, file `/system/xbin/su` được phân quyền chỉ cho phép tài khoản `root` và `shell` (chạy qua adb) thực thi (`-rwsr-x---`). Tiến trình ứng dụng Android chạy dưới quyền của tài khoản người dùng thông thường (`others`) nên không có quyền thực thi file này và bị hệ điều hành chặn lại.

### Giải pháp:
Bạn cần cài đặt công cụ quản lý quyền Superuser (như Magisk) để cấp quyền Root cho ứng dụng thông qua giao diện hoặc chạy lệnh sau qua máy tính nếu sử dụng thiết bị ảo đã mở khóa phân vùng hệ thống (`writable-system`):

1. **Khởi chạy emulator có cờ ghi đè hệ thống:**
   ```bash
   emulator -avd Pixel_6_Rooted_API_34 -writable-system &
   ```
2. **Cấp quyền thực thi `su` cho mọi tiến trình:**
   ```bash
   adb root
   adb remount
   adb shell chmod 06755 /system/xbin/su
   ```
   *Lưu ý: Trên các máy ảo Google APIs đời mới (API 34+), nếu hệ điều hành khóa phân vùng và hiển thị lỗi `Device must be bootloader unlocked`, phương pháp tốt nhất là sử dụng một bộ công cụ root tự động máy ảo (ví dụ: [rootAVD](https://github.com/newbit13/rootAVD)) để cài đặt Magisk vào máy ảo, từ đó ứng dụng có thể gọi lệnh `su` thành công thông qua hộp thoại xin quyền Superuser của Magisk.*
