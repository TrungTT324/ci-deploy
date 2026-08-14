# Data model của Script

## Tổng quan

Phần Script có hai lớp dữ liệu riêng:

1. **Script record**: metadata do Script Library lưu và trả về.
2. **Script content**: JSON array chứa các bước được `ScriptTool` thực thi tuần tự.

File mẫu: [script-example.json](examples/script-example.json).

## Script record

Một script đọc từ `GET /api/qa/scripts` có dạng:

```json
{
  "id": 12,
  "name": "Calendar smoke test",
  "content": "[{\"name\":\"open_app\",\"input\":{\"query\":\"Calendar\"}}]",
  "runCount": 4,
  "lastRun": "2026-08-13T10:30:00Z",
  "lastUpdate": "2026-08-13T09:15:00Z"
}
```

| Field | Kiểu | Bắt buộc khi lưu | Mô tả |
|---|---|---:|---|
| `id` | Number | Không | ID do database tạo. Dùng để sửa, xóa hoặc chạy script đã lưu. |
| `name` | String | Có | Tên hiển thị trong Script Library. Không được rỗng. |
| `content` | String | Có | Chuỗi chứa một JSON array hợp lệ. |
| `runCount` | Number | Không | Tổng số lần script đã lưu được chạy. |
| `lastRun` | ISO date/string | Không | Thời điểm chạy gần nhất. Có thể rỗng nếu chưa chạy. |
| `lastUpdate` | ISO date/string | Không | Thời điểm cập nhật gần nhất. |

Khi tạo hoặc cập nhật, Portal chỉ gửi các field người dùng quản lý:

```json
{
  "name": "Calendar smoke test",
  "content": "[{\"name\":\"open_app\",\"input\":{\"query\":\"Calendar\"}}]"
}
```

Các field `id`, `runCount`, `lastRun` và `lastUpdate` do backend quản lý.

## Script content

`content` sau khi parse phải là một JSON array. Mỗi phần tử là một `ActionModel`; chỉ field `name` bắt buộc.

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

Các step chạy theo đúng thứ tự trong array. Với đúng một step, API chờ `doIt()` và trả action cùng `output`. Với nhiều step, API trả ngay trạng thái đã bắt đầu và runner tiếp tục trong coroutine nền; HTTP success không có nghĩa toàn bộ script đã hoàn tất.

## Đọc danh sách step từ API

`GET /api/qa/scripts` trả một array script record. Vì `content` là String, client phải parse thêm một lần:

```javascript
const scripts = await fetch('/api/qa/scripts').then(response => response.json());
const script = scripts.find(item => item.id === 12);
const steps = script ? JSON.parse(script.content) : [];
```

`GET /api/qa/steps` trả các loại step đăng ký trong `ActionRegistry`. API chưa trả current step đang chạy. Hướng dẫn đầy đủ: [Cách lấy danh sách step hiện tại](12-lay-danh-sach-step-hien-tai.md).

Danh sách runtime hiện có 9 core step: `open_app`, `tap`, `home`, `back`, `recents`, `wait`, `clear_recents`, `capture`, `record`.

## Action model

Chi tiết cấu trúc, Gson mapping, `doIt()` và JSON Schema nằm tại [Data model của Action](09-action-data-model.md).

### open_app

```json
{"name": "open_app", "input": {"query": "Calendar"}}
```

| Field | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `name` | String | Có | Giá trị `open_app`. |
| `input.query` | String | Handler cần | Tên hiển thị hoặc package của ứng dụng. |

Runner chờ 2.500 ms sau khi gửi lệnh mở app.

### tap

```json
{"name": "tap", "input": {"x": 102, "y": 399}, "output": null}
```

| Field | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `name` | String | Có | Giá trị `tap`. |
| `input.x` | Number | Handler cần | Tọa độ pixel ngang. |
| `input.y` | Number | Handler cần | Tọa độ pixel dọc. |

Runner chờ 1.500 ms sau khi dispatch gesture. `QaAccessibilityService` phải đang active.

### home, back, recents

```json
{"name": "home"}
```

Ba step không cần `input`, gửi Android global action tương ứng qua `QaAccessibilityService`, đặt `output` thành Boolean và chờ 500 ms.

### wait

```json
{"name": "wait", "input": {"duration": 1000}}
```

`input.duration` là số millisecond, mặc định `1000`. Giá trị âm được giới hạn về `0`; `output` là `true` sau khi chờ hoàn tất.

### clear_recents

```json
{"name": "clear_recents"}
```

Không cần `input`. Action mở màn hình Recents và click nút Clear/Close all qua Accessibility. Output:

```json
{
  "success": true,
  "method": "accessibility",
  "message": "Requested removal of all tasks exposed by Android Recents."
}
```

`success` chỉ xác nhận thao tác trên nút Recents đã được chấp nhận. Nó không đồng nghĩa process nền đã bị force-stop. Android không cho app release thông thường force-stop app khác, kể cả khi người dùng đã cấp toàn bộ runtime permission và Accessibility.

### capture

```json
{"name": "capture"}
```

Action không có field bổ sung. `QaAutomationService` và Media Projection phải đang active. Runner chờ callback capture và lưu file hoàn tất. Khi thành công, `output` là URL tương đối `/qa/reports/QA_Screenshot_<timestamp>.png`; khi capture/lưu report thất bại, `output` là null.

### record

```json
{"name": "record", "input": {"duration": 5000}}
```

| Field | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `name` | String | Có | Giá trị `record`. |
| `input.duration` | Number | Không | Millisecond; mặc định `5000`. |

Action chỉ bắt đầu đếm duration sau khi recorder báo start thành công. Khi dừng và lưu report thành công, `output` là URL `/qa/reports/QA_Recording_<timestamp>.mp4`; nếu start/stop/lưu thất bại, `output` là null.

`QaAutomationService` và Media Projection phải đang active. Runner chờ thêm 2.000 ms sau khi dừng để hoàn tất và lưu video.

## Payload chạy script

### Chạy nội dung trực tiếp

Gửi JSON array làm HTTP body:

```bash
curl -X POST http://<device-ip>:8086/api/qa/runscript \
  -H 'Content-Type: application/json' \
  --data-binary @examples/script-example.json
```

### Chạy script đã lưu

Gửi ID của script record:

```json
{"id": 12}
```

## Validation và giá trị mặc định

- Root phải là JSON array khi chạy trực tiếp.
- Chỉ `name` bắt buộc; `name` được trim và chuyển về chữ thường khi chọn handler.
- `id` là Int được runtime sinh tự động, tăng dần và không trùng trong process.
- `displayName` mặc định bằng `name`.
- `category` có hai giá trị `core` và `other`.
- `id` runtime luôn là Int không null. `description`, `input`, `output` có thể null.
- `input` và `output` là dynamic JSON data.
- Action không được hỗ trợ sẽ được ghi warning và bỏ qua.
- `open_app.query` mặc định là chuỗi rỗng.
- `tap.x` và `tap.y` mặc định là `-1`, vì vậy step thiếu tọa độ sẽ bị bỏ qua.
- `record.duration` mặc định là 5.000 ms.
- `wait.duration` mặc định là 1.000 ms.
- Runner hiện chưa hỗ trợ JSON action cho `swipe`, `input_text` hoặc assertion. Các action `home`, `back`, `recents` đã được hỗ trợ.

## Quy ước đề xuất

- Đặt `name` mô tả mục tiêu kiểm thử, ví dụ `Calendar - create event smoke test`.
- Dùng package name thay cho tên app nếu cần kết quả mở app ổn định giữa nhiều ngôn ngữ.
- Ghi tọa độ theo đúng độ phân giải và orientation của thiết bị mục tiêu.
- Giữ video dài ít nhất vài giây để `MediaRecorder` có đủ frame hợp lệ.
- Không đặt secret, token hoặc dữ liệu nhạy cảm trong script vì Portal chạy qua HTTP nội bộ.
