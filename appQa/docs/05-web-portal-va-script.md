# Web Portal, API và kịch bản JSON

## Truy cập Portal

Web server mặc định của `appQa` chạy tại:

```text
http://<device-ip>:8086/
```

Portal có năm khu vực:

- Home Dashboard
- Script Console
- Action List
- Test Console
- Test Reports

Giao diện hiện tải Vue 3 và Google Font từ CDN, vì vậy một số phần có thể không hiển thị nếu thiết bị/máy tính không có Internet dù web server nội bộ vẫn truy cập được.

## Script Console

Script Console quản lý thư viện kịch bản trong database của webserver:

- Tạo script mới.
- Sửa tên và nội dung JSON.
- Xóa script.
- Chạy script đã lưu.
- Chạy trực tiếp JSON mà không lưu.
- Theo dõi số lần chạy, lần chạy cuối và lần cập nhật cuối.

## Cấu trúc script

Xem mô tả field đầy đủ tại [Data model của Script](08-script-data-model.md) và file [JSON mẫu](examples/script-example.json).

Script là một JSON array. Các step luôn chạy tuần tự; script nhiều step chạy trong coroutine nền, còn một step chạy đồng bộ để trả `output`:

```json
[
  {"name": "open_app", "category": "core", "input": {"query": "Calendar"}},
  {"name": "tap", "category": "core", "input": {"x": 102, "y": 399}},
  {"name": "home", "category": "core"},
  {"name": "back", "category": "core"},
  {"name": "recents", "category": "core"},
  {"name": "wait", "category": "core", "input": {"duration": 1000}},
  {"name": "clear_recents", "category": "core"},
  {"name": "capture", "category": "core"},
  {"name": "record", "category": "core", "input": {"duration": 5000}}
]
```

## Các action hiện được thực thi

### open_app

```json
{"name": "open_app", "category": "core", "input": {"query": "Calendar"}}
```

Mở ứng dụng theo tên hiển thị hoặc package. Runner chờ 2,5 giây sau thao tác.

### tap

```json
{"name": "tap", "category": "core", "input": {"x": 102, "y": 399}}
```

Chạm tọa độ tuyệt đối bằng `QaAccessibilityService`, sau đó chờ 1,5 giây.

### home, back, recents

```json
{"name": "home", "category": "core"}
```

Gửi phím điều hướng hệ thống tương ứng qua Accessibility service và trả Boolean trong `output`.

### wait

```json
{"name": "wait", "category": "core", "input": {"duration": 1000}}
```

Tạm dừng script theo số millisecond trong `input.duration`. Mặc định là 1.000 ms và trả `true` sau khi chờ xong.

### clear_recents

```json
{"name": "clear_recents", "category": "core"}
```

Action dùng Accessibility mở Recents, tìm nút Clear/Close all (có hỗ trợ nhãn tiếng Việt) và click. Output gồm `success`, `method: "accessibility"` và `message`. Action chỉ xóa recent tasks; app Android thường không thể force-stop process của app khác.

### capture

```json
{"name": "capture", "category": "core"}
```

Yêu cầu QA Automation Service chụp ảnh và chờ callback lưu file hoàn tất.

Action chỉ hoàn tất sau khi ảnh được ghi vào reports. `output` là URL ảnh dạng `/qa/reports/QA_Screenshot_<timestamp>.png`; Action List hiển thị ảnh ngay dưới JSON result và cho phép mở ảnh trong tab mới.

### record

```json
{"name": "record", "category": "core", "input": {"duration": 5000}}
```

Quay trong số millisecond được chỉ định. Mặc định là 5000 ms. Action chờ callback start, dừng và lưu file hoàn tất; `output` thành công là URL `/qa/reports/QA_Recording_<timestamp>.mp4`.

> `ScriptTool` hiện thực thi 9 core action lấy từ `ActionRegistry`. Các hàm swipe và input text tồn tại ở Accessibility service nhưng chưa được đăng ký thành JSON action.

## API được Portal sử dụng

- `GET /api/qa/scripts`: lấy danh sách script.
- `GET /api/qa/steps`: lấy động danh sách core step từ `ActionRegistry`.
- `POST /api/qa/scripts`: tạo script với `name` và `content`.
- `PUT /api/qa/scripts?id=<id>`: cập nhật script.
- `DELETE /api/qa/scripts?id=<id>`: xóa script.
- `POST /api/qa/runscript`: chạy theo `id` hoặc gửi trực tiếp JSON array.
- `POST /api/qa/openapp`: mở app với body `{"query":"..."}`.
- `GET /api/qa/files`: danh sách media reports dùng bởi trang reports.

### Lấy danh sách step hiện tại

`GET /api/qa/scripts` trả script record, trong đó `content` là chuỗi JSON chứa danh sách step. Cần chọn script theo `id` rồi gọi `JSON.parse(script.content)`.

```javascript
const scripts = await fetch('/api/qa/scripts').then(response => response.json());
const script = scripts.find(item => item.id === 12);
const steps = script ? JSON.parse(script.content) : [];
```

Lấy các loại step có thể thêm vào script:

```javascript
const definitions = await fetch('/api/qa/steps').then(response => response.json());
```

Mỗi definition có `name`, `displayName`, `description`, `category`, `className`, input mặc định, kiểu `output` và object `example` dùng để thêm/chạy thử action. Action List không giữ một danh sách hard-code riêng trong `app.js`.

Xem ví dụ `curl`, `jq`, cách lấy step trong editor và giới hạn realtime tại [Cách lấy danh sách step hiện tại](12-lay-danh-sach-step-hien-tai.md).

Ví dụ chạy trực tiếp từ máy tính:

```bash
curl -X POST http://<device-ip>:8086/api/qa/runscript \
  -H 'Content-Type: application/json' \
  -d '[{"name":"open_app","category":"core","input":{"query":"Calendar"}},{"name":"capture","category":"core"}]'
```

## Hành vi thực thi

Với một action, API chờ thực thi xong và trả model cùng `output`; Action List dùng luồng này để hiển thị kết quả. Với nhiều action, API trả thông báo đã bắt đầu và script tiếp tục trong coroutine nền. Chi tiết được ghi qua `WebserverLogger`.

Webserver hiện phục vụ trên HTTP cleartext, trong mạng nội bộ, không thể hiện cơ chế xác thực trong module `appQa`. Chỉ nên chạy trên mạng tin cậy.
