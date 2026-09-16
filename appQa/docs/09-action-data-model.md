# ActionModel

## Contract

`ActionModel` là model dùng chung cho mọi action trong script:

```kotlin
abstract class ActionModel(
    val id: Int,
    val name: String,
    val displayName: String = name,
    val description: String? = null,
    val category: ActionCategory,
    val input: Any? = null,
    var output: Any? = null
) {
    abstract suspend fun doIt(context: Context): Any?
}
```

JSON tương ứng:

```json
{
  "id": 1,
  "name": "tap",
  "displayName": "tap",
  "description": null,
  "category": "core",
  "input": {
    "x": 102,
    "y": 399
  },
  "output": null
}
```

## Fields

| Field | Kiểu | Bắt buộc | Default | Mô tả |
|---|---|---:|---|---|
| `id` | Int | Runtime luôn có | Sinh tự động | ID bắt đầu từ 1, tăng dần và không trùng trong process. |
| `name` | String | **Có** | Không có | Tên action mà runner dùng để chọn handler. |
| `displayName` | String | Không | Bằng `name` | Tên thân thiện dùng trên giao diện. |
| `description` | String/null | Không | `null` | Nội dung mô tả action. |
| `category` | `core`/`other` | Không | Theo `name` | Nhóm action. Built-in action thuộc `core`; action mở rộng thuộc `other`. |
| `input` | Dynamic/null | Không | `null` | Dữ liệu đầu vào tùy action: object, array, string, number, boolean hoặc null. |
| `output` | Dynamic/null | Không | `null` | Kết quả tùy action; runner có thể cập nhật sau khi xử lý. |

## Validation

Chỉ `name` được dùng để xác định model hợp lệ:

```text
valid = name.trim().isNotEmpty()
```

- JSON đầu vào có thể không có `id`; `ActionModel.fromJson` luôn sinh một ID mới không null.
- ID trong JSON đầu vào bị bỏ qua để client không thể tạo ID trùng.
- `displayName` có thể thiếu; khi đó nhận giá trị của `name`.
- `description`, `input`, `output` có thể thiếu hoặc null.
- `category` chỉ có `core` hoặc `other`.
- Không validate shape của `input`/`output` ở cấp `ActionModel` vì đây là dữ liệu dynamic.
- Handler của từng action tự đọc và kiểm tra các field cần thiết trong `input`.

Model tối thiểu hợp lệ:

```json
{"name": "capture"}
```

Model không hợp lệ:

```json
{"name": "   "}
```

## Dynamic input và output

`input` và `output` dùng `Any?` trong Kotlin. Gson map JSON động thành:

- `Map<String, Any?>`
- `List<Any?>`
- `String`
- `Number`
- `Boolean`
- `null`

Các action hiện tại dùng object làm `input`. Ví dụ `tap`:

```json
{
  "id": 2,
  "name": "tap",
  "category": "core",
  "input": {
    "x": 102,
    "y": 399
  }
}
```

Nếu runner dispatch tap thành công, `output` runtime có thể là boolean:

```json
{
  "id": 3,
  "name": "tap",
  "category": "core",
  "input": {"x": 102, "y": 399},
  "output": true
}
```

Với một action, API trả lại model runtime cùng `output` sau khi `doIt()` hoàn tất. Với nhiều action, runner thực thi nền và kết quả từng step được ghi qua logger.

## Method doIt()

Mỗi concrete action chịu trách nhiệm thực thi chính nó:

```kotlin
abstract suspend fun doIt(context: Context): Any?
```

| Concrete class | `name` | Logic trong `doIt()` |
|---|---|---|
| `OpenAppAction` | `open_app` | Mở ứng dụng, cập nhật output, delay. |
| `TapAction` | `tap` | Dispatch Accessibility gesture, cập nhật output, delay. |
| `WaitAction` | `wait` | Chờ theo duration rồi trả `true`. |
| `ClearRecentsAction` | `clear_recents` | Mở Recents và click nút clear-all bằng Accessibility. |
| `HomeAction` | `home` | Gửi global action Home và cập nhật output. |
| `BackAction` | `back` | Gửi global action Back và cập nhật output. |
| `RecentsAction` | `recents` | Gửi global action Recents và cập nhật output. |
| `CaptureAction` | `capture` | Chờ capture/lưu report và trả URL ảnh. |
| `RecordAction` | `record` | Chờ start, record theo duration, stop/lưu và trả URL video. |
| `OtherAction` | tên mở rộng | Trả `false` cho action chưa có implementation. |

`ScriptTool` chỉ điều phối:

```kotlin
val actions = ActionModel.listFromJson(jsonScript)
actions.forEach { action -> action.doIt(context) }
```

## Gson mapping

`ActionModelJsonAdapter` chọn concrete class theo `name` thông qua `ActionRegistry`. Registry cũng cung cấp cùng definition cho `GET /api/qa/steps`, vì vậy Gson dispatch và Web Action List không có hai danh sách riêng. API mapping:

```kotlin
val action = ActionModel.fromJson(jsonString)
val json = action.toJson()

val actions = ActionModel.listFromJson(jsonArrayString)
val jsonArray = ActionModel.listToJson(actions)
```

Khi serialize, JSON luôn chứa `id`, `name`, `displayName`, `description`, `category`, `input`, `output`. Khi deserialize, `id` trong JSON bị bỏ qua và instance nhận ID mới.

## Input theo action hiện tại

| `name` | `category` | `input` | `output` runtime |
|---|---|---|---|
| `open_app` | `core` | `{"query":"Calendar"}` | Boolean từ lệnh mở app. |
| `tap` | `core` | `{"x":102,"y":399}` | Boolean từ lúc dispatch gesture. |
| `home` | `core` | `null` | Boolean từ `GLOBAL_ACTION_HOME`. |
| `back` | `core` | `null` | Boolean từ `GLOBAL_ACTION_BACK`. |
| `recents` | `core` | `null` | Boolean từ `GLOBAL_ACTION_RECENTS`. |
| `wait` | `core` | `{"duration":1000}` | `true` sau khi chờ xong. |
| `clear_recents` | `core` | `null` | Object success/method/message. |
| `capture` | `core` | `null` | URL `/qa/reports/*.png`, hoặc null khi thất bại. |
| `record` | `core` | `{"duration":5000}` | URL `/qa/reports/*.mp4`, hoặc null khi thất bại. |

## Ví dụ đầy đủ

```json
{
  "id": 4,
  "name": "tap",
  "displayName": "Tap Search button",
  "description": "Tap nút Search trên màn hình Calendar",
  "category": "core",
  "input": {
    "x": 102,
    "y": 399
  },
  "output": null
}
```

## Tương thích format cũ

Runner vẫn đọc format cũ trong giai đoạn chuyển đổi:

```json
{"action": "tap", "x": 102, "y": 399}
```

Format cũ được ánh xạ như sau:

- `action` → `name`
- Toàn bộ object cũ → `input`
- `displayName` → mặc định bằng `name`
- ID mới → được sinh tự động
- `category` → tự suy ra theo `name`

## Sinh ID

`ActionModel` dùng `AtomicInteger` dùng chung trong process:

```text
Action đầu tiên  -> id = 1
Action tiếp theo -> id = 2
Action tiếp theo -> id = 3
```

`getAndIncrement()` bảo đảm hai action được tạo đồng thời không nhận cùng ID. Bộ đếm không reset giữa các script trong cùng process; nó chỉ reset khi process ứng dụng khởi động lại.

## Category

```kotlin
enum class ActionCategory(val wireName: String) {
    CORE("core"),
    OTHER("other")
}
```

- `core`: action tích hợp sẵn gồm `open_app`, `tap`, `home`, `back`, `recents`, `wait`, `clear_recents`, `capture`, `record`.
- `other`: action mở rộng hoặc action chưa thuộc danh sách core.
- Nếu JSON truyền category hợp lệ, runner sử dụng giá trị đó.
- Nếu thiếu/sai category, runner suy ra từ `name`.

Script mới nên luôn dùng contract `name` + `input`.

## File liên quan

- Kotlin model: `appQa/src/main/java/hdisoft/app/qa/model/ActionModel.kt`
- Registry/factory: `appQa/src/main/java/hdisoft/app/qa/model/ActionRegistry.kt`
- Runner: `appQa/src/main/java/hdisoft/app/qa/ScriptTool.kt`
- Concrete implementations: `appQa/src/main/java/hdisoft/app/qa/model/CoreActions.kt`
- JSON Schema: [script-actions.schema.json](examples/script-actions.schema.json)
- Script mẫu: [script-example.json](examples/script-example.json)
- Hướng dẫn action: [Mô tả Action](10-mo-ta-action.md)
- Definition và source mapping: [Action Definitions](11-action-definitions.md)
