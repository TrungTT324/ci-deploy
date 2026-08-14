# Action Definitions

## Phạm vi đối chiếu

Danh sách này được map trực tiếp với source code hiện tại trong:

- `ActionRegistry.kt`: nguồn dữ liệu duy nhất cho definition và factory của core action.
- `ActionModel.kt`: model, Gson adapter, ID runtime và category.
- `CoreActions.kt`: concrete action và method `doIt()`.
- `ScriptTool.kt`: parse danh sách và điều phối thực thi.
- `QaAccessibilityService.kt`: gesture, nhập text và navigation.
- `BaseAccessibilityGestureService.kt`: gesture/navigation dùng chung bởi service chính.
- `QaAutomationService.kt`: screenshot, record và demo AUTO.
- `QaWebRequestHandler.kt`: endpoint `GET /api/qa/steps`.
- `assets/js/app.js`: tải definition động và chạy action từ Web Portal.

## Action đã được JSON runner hỗ trợ

Hiện có **9 action executable**. Tất cả thuộc category `core` và được đăng ký tập trung trong `ActionRegistry`.

| `name` | `displayName` đề xuất | `input` | `output` | Source mapping |
|---|---|---|---|---|
| `open_app` | Open App | `{"query":"Calendar"}` | Boolean | `OpenAppAction.doIt()` → `AppTool.openApp()` |
| `tap` | Tap | `{"x":102,"y":399}` | Boolean | `TapAction.doIt()` → `QaAccessibilityService.clickAt()` |
| `home` | Home | `null` | Boolean | `HomeAction.doIt()` → `performNavigation(GLOBAL_ACTION_HOME)` |
| `back` | Back | `null` | Boolean | `BackAction.doIt()` → `performNavigation(GLOBAL_ACTION_BACK)` |
| `recents` | Recents | `null` | Boolean | `RecentsAction.doIt()` → `performNavigation(GLOBAL_ACTION_RECENTS)` |
| `wait` | Wait | `{"duration":1000}` | Boolean | `WaitAction.doIt()` → `delay(duration)` |
| `clear_recents` | Clear Recent Apps | `null` | Clear-recents result | `ClearRecentsAction.doIt()` → `ClearRecentsTool.clear()` |
| `capture` | Capture Screen | `null` | Image URL hoặc null | `CaptureAction.doIt()` → `takeScreenShotExternal()` |
| `record` | Record Screen | `{"duration":5000}` | Video URL hoặc null | `RecordAction.doIt()` → start/stop record |

ID không được define cố định trong danh sách. Mỗi lần JSON được parse, `ActionModel` sinh `id: Int` mới bằng bộ đếm tăng dần, không trùng trong process.

Tất cả action gesture chạy trên Android API 24+; đây cũng là `minSdk` của module. App build release không cần debug hoặc root. Các action Accessibility cần service được bật; `capture`/`record` cần phiên Media Projection đã được người dùng chấp thuận.

Response của `GET /api/qa/steps` không chứa runtime `id`. Mỗi item chứa metadata, `className`, input mặc định, chuỗi kiểu `output` và object `example`. Khi chạy `example`, Gson mới sinh ID runtime.

## Definition: open_app

```json
{
  "name": "open_app",
  "displayName": "Open App",
  "description": "Open an installed Android application by label or package name.",
  "category": "core",
  "input": {
    "query": "Calendar"
  },
  "output": null
}
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> OpenAppAction
  -> doIt(context)
  -> AppTool.openApp(context, query)
```

- `AppTool` đến từ module `libs:core`.
- `query` rỗng: action bị bỏ qua và ghi warning.
- Sau khi mở app, runner chờ 2.500 ms.
- Web Portal cũng có endpoint `/api/qa/openapp` để mở app ngoài Script Runner.

## Definition: tap

```json
{
  "name": "tap",
  "displayName": "Tap",
  "description": "Tap an absolute screen coordinate through Accessibility.",
  "category": "core",
  "input": {
    "x": 102,
    "y": 399
  },
  "output": null
}
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> TapAction
  -> doIt(context)
  -> QaAccessibilityService.instance
  -> clickAt(x, y)
```

- `x`, `y` được đọc từ dynamic `input`.
- Thiếu tọa độ: nhận mặc định `-1` và action bị bỏ qua.
- Cần Accessibility service chính active.
- Runner chờ 1.500 ms.
- `QaAccessibilityService.clickAt()` là alias tương thích trên `BaseAccessibilityGestureService.tap()`.

## Definitions: home, back, recents

```json
[
  {"name": "home", "category": "core", "input": null, "output": null},
  {"name": "back", "category": "core", "input": null, "output": null},
  {"name": "recents", "category": "core", "input": null, "output": null}
]
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> HomeAction / BackAction / RecentsAction
  -> doIt(context)
  -> QaAccessibilityService.instance
  -> performNavigation(GLOBAL_ACTION_HOME / BACK / RECENTS)
```

- Không cần `input`.
- Cần Accessibility service chính active.
- `output` là `true` khi Android chấp nhận global action, ngược lại là `false`.
- Runner chờ 500 ms trước action tiếp theo.
- Có thể chọn, chỉnh JSON và chạy riêng từng action từ Action List trên Web Portal.

## Definition: wait

```json
{"name":"wait","displayName":"Wait","category":"core","input":{"duration":1000},"output":null}
```

`WaitAction.doIt()` chờ số millisecond trong `input.duration`, sau đó đặt `output=true`. Definition được trả tự động bởi `GET /api/qa/steps`.

## Definition: clear_recents

```json
{"name":"clear_recents","displayName":"Clear Recent Apps","category":"core","input":null,"output":null}
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> ClearRecentsAction
  -> doIt(context)
  -> ClearRecentsTool.clear(context)
  -> Recents / Accessibility click Clear all
```

- Mở Recents, cuộn hai hướng và click nhãn Clear/Close/Dismiss all hoặc nhãn tiếng Việt tương ứng.
- Output là object `success`, `method`, `message`.
- Action chỉ xóa task trong Recents, không force-stop process nền. `stop_all` không còn được advertise vì hành vi đó cần root, shell, app đặc quyền hoặc device-owner policy.

### Migration từ stop_all

Payload cũ `{"name":"stop_all"}` không còn là core action executable và không xuất hiện trong `GET /api/qa/steps`. Đổi payload thành `{"name":"clear_recents"}` nếu mục tiêu là xóa các card/task trên màn hình Recents. Không có payload thay thế nào có thể force-stop mọi app trong điều kiện app release thông thường, không root.

## Definition: capture

```json
{
  "name": "capture",
  "displayName": "Capture Screen",
  "description": "Capture the current screen and save a PNG report.",
  "category": "core",
  "input": null,
  "output": null
}
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> CaptureAction
  -> doIt(context)
  -> QaAutomationService.takeScreenShotExternal()
  -> takeScreenShot()
  -> ScreenCaptureHelper.captureScreen()
  -> MediaSaveHelper
```

- Cần QA Automation Service và Media Projection active.
- Ảnh được lưu trong app reports và `Pictures/QAApp`.
- `takeScreenShotExternal()` suspend cho đến khi callback capture và lưu reports hoàn tất.
- `output` thành công là `/qa/reports/QA_Screenshot_<timestamp>.png`; thất bại là null.
- Web Action List dùng URL cùng origin này để hiển thị preview và link mở ảnh.

## Definition: record

```json
{
  "name": "record",
  "displayName": "Record Screen",
  "description": "Record the screen for a configured duration and save an MP4 report.",
  "category": "core",
  "input": {
    "duration": 5000
  },
  "output": null
}
```

### Mapping source

```text
Gson ActionModelJsonAdapter
  -> RecordAction
  -> doIt(context)
  -> QaAutomationService.startScreenRecordingExternal()
  -> delay(duration)
  -> QaAutomationService.stopScreenRecordingExternal()
  -> ScreenRecordHelper / MediaSaveHelper
```

- `duration` tính bằng millisecond, mặc định 5.000.
- Cần QA Automation Service và Media Projection active.
- Video được lưu trong app reports và `Movies/QAApp`.
- Action chờ callback start trước khi đếm duration, rồi chờ stop và lưu report hoàn tất.
- `output` thành công là `/qa/reports/QA_Recording_<timestamp>.mp4`; thất bại là null.
- Runner chờ thêm 2.000 ms sau khi stop.

## Capability đã có trong source nhưng chưa map vào JSON runner

Các capability sau **đã có method thực thi**, nhưng chưa có concrete `ActionModel` và entry tương ứng trong `ActionRegistry`. Do đó chưa được xem là action executable từ JSON.

| Action name đề xuất | Category | Input đề xuất | Source đã có | Trạng thái |
|---|---|---|---|---|
| `swipe` | `core` | `startX`, `startY`, `endX`, `endY`, `duration` | `QaAccessibilityService.swipe()`; `BaseAccessibilityGestureService.swipe()` | Chưa map |
| `input_text` | `core` | `text` | `QaAccessibilityService.inputText()` | Chưa map |
| `auto_sequence` | `other` | Cấu hình hiện đang hard-code | `QaAutomationService.executeAutoSequence()` | Chỉ có nút AUTO overlay |
| `start_record` | `other` | `null` | `startScreenRecordingExternal()` | Chưa map riêng |
| `stop_record` | `other` | `null` | `stopScreenRecordingExternal()` | Chưa map riêng |

## Vì sao capability chưa phải action?

Một method chỉ được coi là JSON action khi có đủ các phần:

1. Có `name` chính thức.
2. Có definition cho `input`/`output`.
3. Có concrete `ActionModel` triển khai `doIt()` và factory được đăng ký trong `ActionRegistry`.
4. Có validation tại handler.
5. Có JSON Schema và ví dụ.
6. Definition được trả qua `GET /api/qa/steps` để Web Portal tự hiển thị.

Hiện `swipe`, `input_text` và AUTO mới đáp ứng phần method thực thi, chưa có đầy đủ mapping trên.

## Danh sách payload example dạng JSON

Đây là nội dung `example` dùng để chạy action, vì vậy `output` ban đầu là null và chưa có runtime `id`. Response đầy đủ của `GET /api/qa/steps` còn có `className` và kiểu output; phần lớn là `"Boolean"`, `capture` là `"Image URL"`, `record` là `"Video URL"`, còn `clear_recents` là `"Clear-recents result"`. Xem [Cách lấy danh sách step hiện tại](12-lay-danh-sach-step-hien-tai.md).

```json
[
  {
    "name": "open_app",
    "displayName": "Open App",
    "description": "Open an installed Android application by label or package name.",
    "category": "core",
    "input": {"query": "Calendar"},
    "output": null
  },
  {
    "name": "tap",
    "displayName": "Tap",
    "description": "Tap an absolute screen coordinate through Accessibility.",
    "category": "core",
    "input": {"x": 102, "y": 399},
    "output": null
  },
  {
    "name": "home",
    "displayName": "Home",
    "description": "Press the Android Home system navigation button.",
    "category": "core",
    "input": null,
    "output": null
  },
  {
    "name": "back",
    "displayName": "Back",
    "description": "Press the Android Back system navigation button.",
    "category": "core",
    "input": null,
    "output": null
  },
  {
    "name": "recents",
    "displayName": "Recents",
    "description": "Open the Android recent apps overview.",
    "category": "core",
    "input": null,
    "output": null
  },
  {
    "name": "wait",
    "displayName": "Wait",
    "description": "Pause script execution for a configured duration.",
    "category": "core",
    "input": {"duration": 1000},
    "output": null
  },
  {
    "name": "clear_recents",
    "displayName": "Clear Recent Apps",
    "description": "Remove all app tasks exposed by the device Recents screen through Accessibility.",
    "category": "core",
    "input": null,
    "output": null
  },
  {
    "name": "capture",
    "displayName": "Capture Screen",
    "description": "Capture the current display and save a PNG to reports and Gallery.",
    "category": "core",
    "input": null,
    "output": null
  },
  {
    "name": "record",
    "displayName": "Record Screen",
    "description": "Record the screen for a configured duration and save an MP4 report.",
    "category": "core",
    "input": {"duration": 5000},
    "output": null
  }
]
```

## Tổng kết

- Action đã map và chạy được từ JSON: **9**.
- Capability có sẵn nhưng chưa map: **5**.
- Category đang dùng: `core`, `other`.
- Action category `other` đã chạy được từ JSON: **chưa có**.
