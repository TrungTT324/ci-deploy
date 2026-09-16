# CI-Deploy Logcat WebSocket Server

Đây là một WebSocket Server được viết bằng Python giúp nhận log từ ứng dụng Android (CI-Deploy) ở chế độ **CLIENT** và phát (broadcast) tới các trình duyệt web đang kết nối (ví dụ: công cụ logcat-reader) để hiển thị log theo thời gian thực.

Nó đồng thời cung cấp một Admin Dashboard giúp theo dõi trạng thái và ngắt/quản lý kết nối của các client đang kết nối thời gian thực.

## 🚀 Cách cài đặt và khởi chạy

### 1. Chuẩn bị môi trường (Chỉ cần chạy lần đầu)

Server yêu cầu Python 3. Chạy lệnh sau để tạo môi trường ảo và cài đặt thư viện cần thiết:

```bash
cd logcat-server
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

### 2. Khởi chạy WebSocket Server

Mặc định, server sẽ lắng nghe trên tất cả các địa chỉ IP (`0.0.0.0`) và cổng `8082`. Chạy lệnh dưới đây để khởi động server:

```bash
.venv/bin/python server.py
```

Hoặc sử dụng nhanh script:

```bash
./run.sh
```

## 🛠️ Quản lý & Xem Log trực tiếp (Admin Dashboard)

Chúng tôi đã tích hợp **FastAPI**, giúp bạn có thể truy cập trực tiếp Dashboard quản trị từ trình duyệt web.

1. Truy cập địa chỉ **[http://localhost:8082](http://localhost:8082)** (hoặc `http://<LAN_IP>:8082` hiển thị trên console của server khi chạy).
2. Trang web sẽ tự động nhận diện và kết nối WebSocket thích hợp với địa chỉ tương ứng.
3. Bạn sẽ thấy:
   - **Thống kê chi tiết**: Tổng số kết nối, số lượng thiết bị Android đang gửi log (Producers), số lượng trình duyệt đang xem log (Web Viewers) và các Console Admin khác.
   - **Bảng quản trị client**: Hiển thị ID (IP:Port), loại client, thời gian kết nối và mẫu thiết bị (Android Model), kèm chức năng Ping hoặc Kick (Ngắt kết nối).
   - **Xem trực tiếp Logcat (Live Logcat Stream)**: Khu vực hiển thị log thô cuộn trực tiếp từ thiết bị Android của bạn gửi lên, hỗ trợ:
     - Lọc logcat theo từ khóa (substring search).
     - Lọc logcat theo mức độ ưu tiên log (Verbose, Debug, Info, Warning, Error) qua các chip màu sắc.
     - Bật/tắt chế độ tự động cuộn (Auto-scroll) và xoá log hiển thị nhanh chóng.

## 📱 Cấu hình trên Ứng dụng Android (CI-Deploy)

1. Mở ứng dụng **CI-Deploy** trên điện thoại.
2. Truy cập vào phần cài đặt **Log Stream** hoặc **Logcat** (qua nút Logcat trên giao diện chính).
3. Đổi chế độ (Stream Mode) từ **SERVER** sang **CLIENT**.
4. Bấm **Start Stream** để bắt đầu gửi log từ điện thoại lên máy tính (ứng dụng sẽ tự tìm kiếm và kết nối tới server trong mạng Wi-Fi cục bộ của bạn).

## 💻 Xem Log trên Trình duyệt (Logcat Reader)

1. Mở file [index.html](file:///Users/trungtruong/xsofts_prj/web/github/ci-deploy/logcat-reader/index.html) bằng trình duyệt web.
2. Nhập URL WebSocket trỏ tới máy tính của bạn, ví dụ: `ws://localhost:8082`.
3. Bấm **Connect** để kết nối và xem log trực tiếp.
