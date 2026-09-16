# Tài liệu appQa

Thư mục này mô tả các tính năng đang có trong module Android `appQa`.

Script Runner hiện có 9 core step được đăng ký trong `ActionRegistry`: `open_app`, `tap`, `home`, `back`, `recents`, `wait`, `clear_recents`, `capture`, `record`. Web Action List lấy danh sách này từ `GET /api/qa/steps`.

## Mục lục

1. [Tổng quan sản phẩm](01-tong-quan.md)
2. [Cài đặt và khởi động](02-cai-dat-va-khoi-dong.md)
3. [Accessibility và điều khiển thiết bị](03-accessibility-automation.md)
4. [Chụp ảnh, quay màn hình và báo cáo](04-media-va-bao-cao.md)
5. [Web Portal, API và kịch bản JSON](05-web-portal-va-script.md)
6. [Kiến trúc kỹ thuật](06-kien-truc.md)
7. [Xử lý sự cố và giới hạn](07-xu-ly-su-co.md)
8. [Data model của Script](08-script-data-model.md)
9. [Data model của Action](09-action-data-model.md)
10. [Mô tả và cách sử dụng Action](10-mo-ta-action.md)
11. [Action Definitions và source mapping](11-action-definitions.md)
12. [Cách lấy danh sách step hiện tại](12-lay-danh-sach-step-hien-tai.md)

JSON mẫu: [examples/script-example.json](examples/script-example.json).
JSON Schema: [examples/script-actions.schema.json](examples/script-actions.schema.json).

## Xem tài liệu trên trình duyệt

Có thể double-click `index.html` để xem trực tiếp bằng trình duyệt, không cần chạy server. Trang chứa một bản fallback của toàn bộ nội dung Markdown để hoạt động với URL `file://`.

Khi đang chỉnh sửa tài liệu và muốn trang luôn đọc phiên bản `.md` mới nhất, chạy một web server tĩnh.

Sau khi sửa Markdown, đồng bộ bản fallback dùng cho `file://`:

```bash
node appQa/docs/sync-index.mjs
```

Từ thư mục `appQa/docs`, chạy một web server tĩnh:

```bash
python3 -m http.server 8090
```

Sau đó mở `http://localhost:8090/`. File `index.html` cung cấp thanh điều hướng, tìm kiếm và render các file Markdown mà không cần thư viện bên ngoài.

## Phạm vi và nguồn tham chiếu

Tài liệu được đối chiếu với mã nguồn trong `appQa/src/main`, `appQa/build.gradle.kts` và giao diện web trong `appQa/src/main/assets`. Giá trị port hiện tại trong mã nguồn là **8086**.
