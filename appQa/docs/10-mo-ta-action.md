# Mô tả Action

Mỗi phần tử trong script được parse thành một `ActionModel`. `id` được sinh tự động tăng dần; chỉ `name` cần có trong JSON đầu vào. Action built-in thuộc category `core`; action mở rộng thuộc `other`.

## tap

Chạm vào tọa độ pixel tuyệt đối bằng Accessibility gesture.

```json
{
  "name": "tap",
  "category": "core",
  "input": {
    "x": 102,
    "y": 399
  },
  "output": null
}
```

- `input.x`: tọa độ ngang, mặc định `-1` nếu thiếu.
- `input.y`: tọa độ dọc, mặc định `-1` nếu thiếu.
- Cần `QaAccessibilityService` active.
- `output`: boolean cho biết gesture đã được dispatch hay chưa.
- Runner chờ 1.500 ms trước action tiếp theo.

## open_app

Mở app theo tên hiển thị hoặc package name.

```json
{
  "name": "open_app",
  "displayName": "Open Calendar",
  "category": "core",
  "input": {
    "query": "Calendar"
  }
}
```

- `input.query`: tên app hoặc package.
- `output`: boolean từ `AppTool.openApp`.
- Runner chờ 2.500 ms để app render.

## capture

Chụp màn hình và lưu ảnh vào reports cùng Gallery.

```json
{
  "name": "capture",
  "category": "core"
}
```

- `input` có thể null.
- Cần QA Automation Service và Media Projection active.
- Runner chờ callback capture và việc lưu file report hoàn tất.
- `output` là URL tương đối `/qa/reports/QA_Screenshot_<timestamp>.png` khi thành công, hoặc null khi thất bại.
- Action List tự render preview từ URL và có thể mở ảnh trong tab mới.

## wait

Tạm dừng script mà không thao tác lên thiết bị.

```json
{"name": "wait", "category": "core", "input": {"duration": 1000}}
```

- `input.duration`: thời gian chờ theo millisecond, mặc định `1000`.
- Giá trị âm được giới hạn về `0`.
- `output` là `true` sau khi chờ hoàn tất.

## home, back, recents

Điều khiển ba phím điều hướng hệ thống Android bằng Accessibility global action.

```json
[
  {"name": "home", "category": "core"},
  {"name": "back", "category": "core"},
  {"name": "recents", "category": "core"}
]
```

- `input` có thể null.
- Cần `QaAccessibilityService` active.
- `output`: boolean cho biết Android có chấp nhận global action hay không.
- Runner chờ 500 ms trước action tiếp theo.
- Ba action có thể chạy thử riêng từ Action List trên Web Portal.

## clear_recents

Xóa các task mà màn hình Recents của thiết bị cho phép xóa.

```json
{"name": "clear_recents", "category": "core", "input": null}
```

- Dùng Accessibility mở Recents, cuộn tìm và click nút Clear/Close/Dismiss all.
- `output` là object gồm `success`, `method: "accessibility"` và `message`.
- Không force-stop process nền. Android không cấp quyền này cho app release thông thường, kể cả khi đã cấp đầy đủ runtime permission và Accessibility.
- Runner chờ thêm 500 ms sau thao tác.

## record

Quay màn hình trong khoảng thời gian chỉ định.

```json
{
  "name": "record",
  "category": "core",
  "input": {
    "duration": 5000
  }
}
```

- `input.duration`: millisecond, mặc định `5000`.
- Cần QA Automation Service và Media Projection active.
- Action chờ callback start trước khi đếm duration và chờ lưu file khi stop.
- `output` là URL `/qa/reports/QA_Recording_<timestamp>.mp4` khi thành công, hoặc null khi thất bại.
- Runner chờ thêm 2.000 ms để hoàn tất file.

## Script hoàn chỉnh

```json
[
  {
    "name": "open_app",
    "displayName": "Open Calendar",
    "description": "Mở ứng dụng Calendar",
    "category": "core",
    "input": {"query": "Calendar"},
    "output": null
  },
  {
    "name": "tap",
    "category": "core",
    "input": {"x": 102, "y": 399}
  },
  {
    "name": "wait",
    "category": "core",
    "input": {"duration": 1000}
  },
  {
    "name": "capture",
    "category": "core"
  }
]
```

File chạy trực tiếp: [script-example.json](examples/script-example.json).

## Luồng thực thi

```text
JSON object
  -> Gson ActionModelJsonAdapter
  -> concrete ActionModel
  -> sinh id Int tăng dần
  -> validate name
  -> xác định category core/other
  -> action.doIt(context)
  -> cập nhật dynamic output
```

Với một action, API chờ `doIt()` hoàn tất và trả model runtime cùng `output`. Script có nhiều action tiếp tục chạy trong coroutine nền và API trả response ngay khi bắt đầu.

Danh sách definition dùng trên Web Portal được lấy từ `GET /api/qa/steps`. Hiện có 9 core action; `wait` và `clear_recents` đều được tự động đưa lên Action List từ `ActionRegistry`.
