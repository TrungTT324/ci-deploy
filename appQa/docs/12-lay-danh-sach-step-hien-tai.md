# Cách lấy danh sách step hiện tại

## Phân biệt hai loại danh sách

Trong appQa, “list step hiện tại” có thể mang hai nghĩa:

1. **Các step đã cấu hình trong một script**: nằm trong field `content` của script record.
2. **Các loại step mà runner hỗ trợ**: lấy động từ `ActionRegistry`.

Backend có `GET /api/qa/steps` để trả danh sách definition; vẫn chưa expose step đang chạy theo thời gian thực.

Endpoint được đăng ký bởi `QaWebRequestHandler` khi MainActivity cấu hình web server, nên việc đọc definition không cần Media Projection hay QA Automation Service đang chạy.

## Lấy step của một script đã lưu

Gọi API lấy Script Library:

```http
GET http://<device-ip>:8086/api/qa/scripts
```

```bash
curl -fsS http://<device-ip>:8086/api/qa/scripts
```

Response là array các script record. Field `content` là một **JSON string**, không phải array trực tiếp:

```json
[
  {
    "id": 12,
    "name": "Calendar smoke test",
    "content": "[{\"name\":\"open_app\",\"input\":{\"query\":\"Calendar\"}},{\"name\":\"tap\",\"input\":{\"x\":102,\"y\":399}}]",
    "runCount": 4,
    "lastRun": "2026-08-13T10:30:00Z",
    "lastUpdate": "2026-08-13T09:15:00Z"
  }
]
```

Chọn script theo `id`, sau đó parse `content` để nhận `List<ActionModel>`:

```javascript
const scripts = await fetch('/api/qa/scripts').then(response => response.json());
const script = scripts.find(item => item.id === 12);
const steps = script ? JSON.parse(script.content) : [];

console.log(steps);
```

Kết quả:

```json
[
  {"name": "open_app", "input": {"query": "Calendar"}},
  {"name": "tap", "input": {"x": 102, "y": 399}}
]
```

Với `jq`:

```bash
curl -fsS http://<device-ip>:8086/api/qa/scripts \
  | jq --argjson scriptId 12 \
    '.[] | select(.id == $scriptId) | .content | fromjson'
```

Lấy step của tất cả script:

```bash
curl -fsS http://<device-ip>:8086/api/qa/scripts \
  | jq 'map({id, name, steps: (.content | fromjson)})'
```

## Lấy step đang mở trong Script Console

Web Portal giữ JSON của editor trong `editingScript.content`. Danh sách step đang chỉnh sửa được lấy bằng:

```javascript
const currentSteps = JSON.parse(editingScript.content || '[]');
```

Trước khi dùng, Portal gọi `parseAndValidateActions(content)` để xác nhận:

- Root là JSON array.
- Mỗi phần tử là object.
- Mỗi step có `name` không rỗng.

Nếu script chưa được lưu thì danh sách này chỉ tồn tại trong trạng thái của trang web và chưa xuất hiện trong `GET /api/qa/scripts`.

## Lấy danh sách loại step runner hỗ trợ

```bash
curl -fsS http://<device-ip>:8086/api/qa/steps | jq
```

```javascript
const stepDefinitions = await fetch('/api/qa/steps')
  .then(response => response.json());
```

Một item trả về có dạng:

```json
{
  "name": "wait",
  "displayName": "Wait",
  "description": "Pause script execution for a configured duration.",
  "category": "core",
  "className": "WaitAction",
  "input": {"duration": 1000},
  "output": "Boolean",
  "example": {
    "name": "wait",
    "displayName": "Wait",
    "description": "Pause script execution for a configured duration.",
    "category": "core",
    "input": {"duration": 1000},
    "output": null
  }
}
```

Definition không có `id`; ID Int tăng dần chỉ được sinh khi `example` được parse thành action runtime.

Riêng definition `capture` trả `"output": "Image URL"`. Sau khi chạy một capture thành công, action runtime chứa URL ảnh có thể xem từ Web Portal:

```json
{
  "name": "capture",
  "output": "/qa/reports/QA_Screenshot_1760000000000.png"
}
```

Definition `clear_recents` trả `"output": "Clear-recents result"`; runtime output có `method: "accessibility"`. Action chỉ xóa task trong Recents và không force-stop process nền.

Definition `record` trả `"output": "Video URL"`. Khi thành công, runtime output là `/qa/reports/QA_Recording_<timestamp>.mp4`; Web Action List hiển thị video player và link mở file.

Runner hiện map 9 tên action:

| STT | `name` | Input |
|---:|---|---|
| 1 | `open_app` | `{"query":"Calendar"}` |
| 2 | `tap` | `{"x":102,"y":399}` |
| 3 | `home` | `null` |
| 4 | `back` | `null` |
| 5 | `recents` | `null` |
| 6 | `wait` | `{"duration":1000}` |
| 7 | `clear_recents` | `null` |
| 8 | `capture` | `null` |
| 9 | `record` | `{"duration":5000}` |

Source of truth hiện tại:

- Registry và factory: `model/ActionRegistry.kt`.
- Concrete execution: `model/CoreActions.kt`.
- Web tải động từ `GET /api/qa/steps` vào `actionDefinitions`.
- Mô tả đầy đủ: [Action Definitions](11-action-definitions.md).

Khi thêm core step mới bằng `ActionRegistry.registerCore(...)`, Gson dispatch và Web Action List cùng nhận definition đó; không sửa `app.js`.

Ví dụ đăng ký:

```kotlin
registerCore(
    "wait", "Wait", "Pause script execution.",
    "WaitAction", mapOf("duration" to 1000), ::WaitAction
)
```

## Có lấy được step đang chạy không?

Chưa. Hành vi hiện tại:

- Script có đúng một step: `/api/qa/runscript` chờ `doIt()` hoàn tất và trả action runtime cùng `output`.
- Script có nhiều step: API trả trạng thái bắt đầu, sau đó runner thực thi trong coroutine nền.
- Chưa có `runId`, current step index, execution history hoặc endpoint progress.

Vì vậy `GET /api/qa/scripts` chỉ trả **cấu hình step đã lưu**, không phản ánh step nào đang được thực thi tại thời điểm gọi.
