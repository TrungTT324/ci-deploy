# Agent build android app và triển khai local

- Khi start, agent sẽ đọc 1 file json có nội dung là 1 list các dự án android hiện có trên pc cần deploy. Gồm folder source code, git branch chính, folder destination cần copy .apk file.

## Hiện trạng (`deploy.py`)

Hiện tại `deploy.py` mới hỗ trợ deploy cho **1 dự án duy nhất** (chưa đọc file json cấu hình như mô tả ở trên), cụ thể các bước thực hiện:

1. Chạy `./gradlew assembleDebug` tại project root (thư mục cha của `agent-deploy`) để build APK debug.
2. Đọc output của Gradle để lấy build number qua log dạng `CI-DEPLOY BUILD_NO GENERATED: <số>`. Nếu không tìm thấy, fallback dùng timestamp hiện tại (`yyyyMMddHHmm`).
3. Tạo thư mục đích nếu chưa tồn tại: `/opt/homebrew/var/www/ci-deploy` (thư mục nginx phục vụ static file trên local).
4. Copy file `app/build/outputs/apk/debug/app-debug.apk` vào thư mục đích với tên `CI-Deploy_debug.apk`.
5. Ghi/cập nhật file `ci-deploy-version.json` chứa `buildNo`, `version`, `buildNote`, `url` để các thiết bị test check update.

### Chạy script

```bash
python3 deploy.py
```

Yêu cầu:
- Máy đã cài Android SDK/Gradle wrapper hoạt động được (`./gradlew`).
- Nginx (hoặc web server tương đương) đang serve thư mục `/opt/homebrew/var/www/ci-deploy` tại địa chỉ khai báo trong `url` của `ci-deploy-version.json`.

## Web UI (FastAPI)

`main.py` chạy 1 web server FastAPI để điều khiển agent từ trình duyệt: danh sách project đọc từ `projects.json`, mỗi project có nút **Start build** và hiển thị status build hiện tại (idle / building / success / failed), build number, thời gian cập nhật và log build gần nhất.

`projects.json` hiện là **file demo**, dữ liệu (path, branch...) chưa chắc chính xác, sẽ chỉnh lại sau. Cấu trúc:

```json
{
  "projects": [
    { "name": "ci-deploy", "sourceDir": "...", "branch": "main", "destDir": "...", "framework": "native" }
  ]
}
```

### Field `framework`

Quyết định cách build + đường dẫn apk output cho từng project:

| framework      | build command                        | apk output (tính từ `sourceDir`)                  |
|----------------|---------------------------------------|-----------------------------------------------------|
| `native`       | `./gradlew assembleDebug`             | `app/build/outputs/apk/debug/app-debug.apk`          |
| `reactnative`  | `./gradlew assembleDebug` (trong `android/`) | `android/app/build/outputs/apk/debug/app-debug.apk` |
| `flutter`      | `flutter build apk --debug`           | `build/app/outputs/flutter-apk/app-debug.apk`        |
| `custom`       | command tùy ý, khai báo qua `buildCommand` | khai báo qua `apkPath` (chạy dưới shell)         |

Với `framework: "custom"`, mỗi item **bắt buộc** khai báo thêm:
- `buildCommand`: lệnh shell để build (ví dụ `"make build-debug"`).
- `apkPath`: đường dẫn apk output, tính từ `sourceDir`.

### Chạy web UI

```bash
./run.sh
```

Lần đầu chạy sẽ tự tạo `.venv` và cài `requirements.txt`, các lần sau chỉ start server (reload mode). Mở trình duyệt tại http://127.0.0.1:8000

Cơ chế:
- `GET /api/projects` — mỗi lần gọi sẽ đọc lại `projects.json`, chạy `git pull origin <branch>` cho từng `sourceDir` rồi trả về danh sách project kèm thông tin commit mới nhất (hash, tác giả, ngày, message) và status build (frontend poll mỗi 8s). Nếu `git pull`/`git log` lỗi (path không tồn tại, không phải git repo, không có remote...) thì hiển thị lỗi thay vì crash.
- `POST /api/projects/{name}/build` — trigger build chạy nền (thread riêng) cho đúng `sourceDir`/`destDir` của project đó, dùng chung logic build/copy APK/ghi version json như `deploy.py`. Endpoint này **không** tự `git pull` trước khi build.
- Status build lưu **in-memory**, mất khi restart server (chưa persist).

⚠️ Lưu ý: vì `GET /api/projects` chạy `git pull` thật trên `sourceDir` mỗi lần poll, cần cấu hình đúng path repo thật trong `projects.json` trước khi chạy — tránh trỏ nhầm vào repo đang có thay đổi chưa commit vì `git pull` có thể yêu cầu merge.

## Roadmap

- [ ] `git pull` trước khi build luôn (hiện 2 việc này tách rời).
- [ ] Cache/throttle `git pull` thay vì chạy mỗi lần poll để đỡ tốn network.
- [ ] Checkout đúng `branch` khai báo trong `projects.json` trước khi build.
- [ ] Cho phép cấu hình custom `buildNote`/`version`/`url` theo từng project thay vì giá trị mặc định.
- [ ] Persist build status (hiện đang mất khi restart server).
- [ ] Xác thực dữ liệu thật cho `projects.json` (hiện là data demo).
