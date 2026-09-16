# libs:appupdate

Module dùng chung để tự cập nhật (OTA) một app Android bằng cách tải APK trực tiếp từ một server nội bộ (không qua Play Store) — module lo **toàn bộ vòng đời**: kiểm tra bản mới, tải file, xác minh APK, xin quyền "cài ứng dụng không rõ nguồn" nếu thiếu, và cài đặt (âm thầm qua root nếu máy rooted, hoặc mở trình cài đặt hệ thống nếu không). App tích hợp chỉ còn phải tự lo phần thật sự đặc thù của mình: khai báo `FileProvider`/`file_paths.xml` trong `AndroidManifest.xml` (bắt buộc theo cơ chế Android, không thể gộp vào 1 module dùng chung) và `BuildConfig.BUILD_NO` của riêng app.

Đã được dùng thực tế trong 2 app của repo này: `app` (CI-Deploy — flow đầy đủ, có host-discovery, xem `ServiceLocator.appUpdateChecker`) và `appMouse` (flow tối giản, xem `appMouse/src/main/java/hdisoft/app/mouse/update/AppUpdateController.kt`).

**Điểm tích hợp duy nhất là `AppUpdateChecker`** (package `hdisoft.app.appupdate`). App tích hợp chỉ cần biết một `baseUrl` (thư mục server chứa file JSON) — tên file JSON tự suy ra từ tên app (`R.string.app_name` / `android:label`, ví dụ `"CI-Deploy"` → `ci-deploy-version.json`), chỉ truyền `jsonFileName` khi cần override tên khác. Các class `UpdateRepository`/`CheckUpdateUseCase`/`DownloadApkUseCase`/`UpdateRemoteDataSource`/`AppUpdateInstaller` bên dưới vẫn còn (dùng nội bộ bởi `AppUpdateChecker`, hoặc cho ai cần tự custom `UpdateRepository`), nhưng **không cần đụng tới khi tích hợp app mới**.

Muốn có luôn 1 màn hình settings/thông tin phiên bản mà không cần tự dựng UI, gọi `AppUpdateInfoDialog.show(context, versionName, buildNo, onIntervalSaved, onSourceModeSaved)` (mục 4).

## 1. Cơ chế hoạt động

1. App gọi `checkUpdate(baseUrl)` → tải 1 file JSON nhỏ (metadata bản mới nhất) từ server.
2. So sánh `buildNo` trong JSON với `BuildConfig.BUILD_NO` hiện tại của app.
3. Nếu có bản mới hơn → gọi `downloadApk(url, targetFile)` → nhận `Flow<DownloadState>` báo tiến độ tải theo thời gian thực.
4. Sau khi tải xong (`DownloadState.Success`) → gọi `installDownloadedApk(context, info, file)` → nhận `Flow<InstallState>`:
   - `Verifying` → check size/sha256/chữ ký so với `info` và app đang cài.
   - Sai → xoá file, emit `Error(message)`.
   - Đúng, máy **rooted** (mặc định `autoInstallIfRooted = DeviceUtils.isDeviceRooted()`, `:libs:core`) → `Installing` → cài âm thầm qua `su`/`pm install`, không cần thao tác gì từ người dùng → `Success`/`Error`.
   - Đúng, máy **không rooted** → emit `Ready(file)`, chờ app gọi `installApk(context, file)` (thường khi người dùng bấm nút "Cài đặt") → module tự kiểm tra & xin quyền "cài ứng dụng không rõ nguồn" nếu Android chưa cấp, rồi mở trình cài đặt hệ thống qua `FileProvider`.

## 2. Định dạng JSON metadata (bắt buộc trên server)

```json
{
  "appName": "AppMouse",
  "version": "1.0.0",
  "buildNo": 202607191234,
  "buildNote": "Mô tả ngắn về bản build này",
  "url": "http://<host>:8080/path/to/app-debug.apk",
  "sha256": "<sha256 của đúng file apk ở url trên>",
  "sizeBytes": 6147336,
  "packageName": "hdisoft.app.yourapp"
}
```

Tất cả field đều bắt buộc — thiếu `buildNo` (≤0) hoặc `url` rỗng thì `checkUpdate()` trả về `null` (coi như không có bản cập nhật). `sha256` + `sizeBytes` dùng để `AppUpdateInstaller.verifyApk` (gọi qua `installDownloadedApk`) xác minh file tải về đúng, chưa bị hỏng/can thiệp.

Xem `build_local.sh` ở thư mục gốc repo — đó là ví dụ script publish sẵn: build APK, tính sha256/size, ghi JSON này lên server local mỗi lần chạy.

## 3. Thêm vào một module app mới

### 3.1. Gradle

```kotlin
// app-cua-ban/build.gradle.kts
dependencies {
    implementation(project(":libs:appupdate")) // đã tự kéo theo :libs:core
}
```

### 3.2. AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<application
    ...
    android:usesCleartextTraffic="true"> <!-- chỉ cần nếu server JSON/APK là http, không phải https -->

    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

`res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="temp_files" path="." />
    <files-path name="app_files" path="." />
</paths>
```

### 3.3. BuildConfig.BUILD_NO

Module so sánh version bằng số nguyên tăng dần theo thời gian, không dùng `versionCode`. Thêm vào `build.gradle.kts` của app:

```kotlin
import java.text.SimpleDateFormat
import java.util.Date

fun generateBuildNo(): Long =
    SimpleDateFormat("yyyyMMddHHmm").format(Date()).toLong()

android {
    defaultConfig {
        buildConfigField("long", "BUILD_NO", "${generateBuildNo()}L")
    }
    buildFeatures { buildConfig = true }
}
```

## 4. Cách dùng tối thiểu (không cần DI/ViewModel)

Chỉ cần một `baseUrl` — không cần biết gì về `UpdateRepository`/`UseCase` bên dưới:

```kotlin
class AppUpdateController(private val activity: AppCompatActivity) {

    private val appUpdateChecker = AppUpdateChecker(activity) // jsonFileName mặc định suy từ app_name

    fun checkForUpdate() {
        activity.lifecycleScope.launch {
            val info = try { appUpdateChecker.checkUpdate(BASE_URL) } catch (e: Exception) { null }
            if (info != null && info.buildNo > BuildConfig.BUILD_NO) {
                showUpdateDialog(info)
            }
        }
    }

    private fun startDownload(info: UpdateInfo) {
        val apkFile = File(activity.cacheDir, "app_update.apk")
        activity.lifecycleScope.launch {
            appUpdateChecker.downloadApk(info.url, apkFile).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> { /* cập nhật progress bar: state.progress, state.downloadedMb, state.totalMb */ }
                    is DownloadState.Success -> verifyAndInstall(info, state.file)
                    is DownloadState.Error -> { /* show lỗi: state.message */ }
                    DownloadState.Cancelled, DownloadState.Idle -> Unit
                }
            }
        }
    }

    private fun verifyAndInstall(info: UpdateInfo, file: File) {
        activity.lifecycleScope.launch {
            // autoInstallIfRooted mặc định = DeviceUtils.isDeviceRooted() — không cần truyền gì thêm
            // để có cài âm thầm qua root miễn phí khi máy hỗ trợ.
            appUpdateChecker.installDownloadedApk(activity, info, file).collect { state ->
                when (state) {
                    is InstallState.Ready -> appUpdateChecker.installApk(activity, state.file) // mở trình cài đặt hệ thống, tự xin quyền nếu thiếu
                    is InstallState.Error -> { /* verify thất bại hoặc root-install lỗi: state.message. File đã tự bị xoá nếu verify sai. */ }
                    is InstallState.Success -> { /* cài xong qua root, không cần thao tác gì thêm */ }
                    else -> Unit // Idle / Verifying / Installing — chỉ cần cập nhật UI nếu muốn
                }
            }
        }
    }

    companion object {
        // Chỉ cần thư mục chứa JSON trên server — KHÔNG kèm tên file.
        // Tên file JSON tự suy từ app_name (vd "AppMouse" -> "appmouse-version.json").
        private const val BASE_URL = "http://<lan-ip>:8080/path/to"
    }
}
```

Gọi `AppUpdateController(this).checkForUpdate()` trong `onCreate()` của Activity chính là xong.

Muốn override tên file JSON (ví dụ server đặt tên khác quy ước `<app-name-slug>-version.json`), truyền `jsonFileName` khi khởi tạo: `AppUpdateChecker(activity, jsonFileName = "custom-name.json")`.

Muốn xem bản đầy đủ hơn (dialog xin xác nhận, dialog progress có nút Cancel) thì đọc trực tiếp `appMouse/src/main/java/hdisoft/app/mouse/update/AppUpdateController.kt` — copy gần như nguyên bản là chạy được, đã dùng đúng `AppUpdateChecker` theo mẫu trên.

Muốn có sẵn màn hình "Thông tin phiên bản" (version/build/ngày cài, lần check gần nhất, APK tải gần nhất, chọn chu kỳ check + LAN/Public host) mà không tự dựng UI, gọi thẳng từ 1 menu action:

```kotlin
AppUpdateInfoDialog.show(
    context = activity,
    versionName = BuildConfig.VERSION_NAME,
    buildNo = BuildConfig.BUILD_NO,
    onIntervalSaved = { interval -> /* ví dụ: viewModel.setCheckInterval(interval) */ },
    onSourceModeSaved = { /* ví dụ: trigger check lại ngay với mode mới */ }
)
```

`versionName`/`buildNo` phải truyền vào vì module không đọc được `BuildConfig` của app tích hợp (mỗi app có `BuildConfig` riêng trong package của nó).

## 4a. Toàn bộ UI flow "có bản cập nhật" (`AppUpdateDownloadFlow`)

Thay vì tự dựng dialog "New Upgrade" → dialog progress download → dialog Ready-to-install/manual-install như trước, gọi thẳng `AppUpdateDownloadFlow` — dialog xin quyền, cài âm thầm qua root (nếu máy rooted, không hiện dialog nào cả — giống mặc định của `installDownloadedApk`), fallback dialog khi root-install lỗi... tất cả đã có sẵn.

State (`DownloadState`/`InstallState`) vẫn nên sống ở nơi app đã lưu nó (thường là ViewModel, để sống sót qua xoay màn hình) — class này **chỉ vẽ dialog**, không tự giữ state tải/cài. Nó cần 1 adapter nhỏ implement `AppUpdateDownloadActions` để gọi ngược lại ViewModel:

```kotlin
private val updateDownloadActions = object : AppUpdateDownloadActions {
    override fun startDownload(info: UpdateInfo, url: String, targetFile: File) = viewModel.startDownload(info, url, targetFile)
    override fun cancelDownload() = viewModel.cancelDownload()
    override fun verifyDownloadedApk(file: File, autoInstallWithRoot: Boolean) = viewModel.verifyDownloadedApk(applicationContext, file, autoInstallWithRoot)
    override fun installApk(file: File) { viewModel.installApk(this@MainActivity, file) }
    override fun resetUpdateOperation() = viewModel.resetUpdateOperation()
    override fun dismissVersion(buildNo: Long) = viewModel.dismissVersion(buildNo)
    override fun clearUpdateInfo() = viewModel.clearUpdateInfo()
    override fun resumePeriodicUpdateCheck() = viewModel.resumePeriodicUpdateCheck()
}
private val updateDownloadFlow by lazy { AppUpdateDownloadFlow(this, updateDownloadActions, apkFileName = "app_update.apk") }

// khi ViewModel báo có info mới (buildNo > current) và đã resolve xong downloadUrl (LAN host, urlPath... tùy app):
updateDownloadFlow.onUpdateAvailable(info, downloadUrl)

// feed thẳng 2 Flow ViewModel đã có sẵn — collect ở đâu app đã collect, chỉ đổi hàm xử lý:
viewModel.downloadState.collectLatest { updateDownloadFlow.onDownloadStateChanged(it) }
viewModel.installState.collectLatest { updateDownloadFlow.onInstallStateChanged(it) }
```

`downloadUrl` phải là URL đã resolve đầy đủ — class không tự biết cách build URL từ `info.url` (mỗi app rewrite theo host/path riêng, xem cách `:app`'s `MainActivity` làm). Tạo **1 instance mới cho mỗi Activity instance** (class giữ tham chiếu dialog) — đừng cache qua `onCreate` khác nhau.

## 4b. LAN host discovery (`hdisoft.app.appupdate.hostdiscovery.*`)

Trước đây là module riêng (`:libs:hostdiscovery`); đã gộp thẳng vào `appupdate` (nguyên cấu trúc clean-architecture, chỉ đổi package) để gắn được với setting `UpdateSourceMode` — quét LAN vô nghĩa nếu app đang ở chế độ `PUBLIC_HOST`. Chỉ `app` (CI-Deploy) dùng tính năng này (`appMouse` không cần biết host LAN).

- `HostDiscoveryService` (`android.app.Service`, bind qua `LocalBinder`) — gọi `startDiscovery()` **bất cứ lúc nào cũng an toàn**: hàm tự kiểm tra `AppUpdateSettings.getUpdateSourceMode(context)` trước — khác `LAN_ONLY` thì lập tức emit `State.Disabled` và không quét gì cả. Khi `LAN_ONLY`: check host đã lưu trước (`GetSavedHostUseCase`/`VerifyHostUseCase`), fallback quét đồng thời `<subnet>1..254` cổng 8080 (`HostRemoteDataSource.discoverHost`, `Semaphore(80)`, thắng đầu tiên hủy phần còn lại), lưu lại host tìm được (`SaveHostUseCase`).
- `State` — `Idle` / `Disabled` (mode không phải LAN_ONLY) / `Progress(message)` / `Success(host)` / `Error(error)`.
- Đổi setting sang `LAN_ONLY` nên gọi lại `startDiscovery()` ngay (xem `AppUpdateInfoDialog`'s `onSourceModeSaved` trong `:app`'s `MainActivity`) để không phải chờ tới lần mở app kế tiếp.
- `HostRemoteDataSource(port, verifyPath)` nhận `port`/`verifyPath` thay vì hardcode — default vẫn là `8080` / `ci-deploy/ci-deploy-version.json` (khớp OTA JSON path của CI-Deploy) vì hiện chỉ CI-Deploy dùng; app khác muốn tái sử dụng discovery thì tự truyền path của mình.

## 5. Model & API tham khảo

| File | Vai trò |
|---|---|
| `AppUpdateChecker.kt` | **Điểm tích hợp khuyến nghị**, phủ toàn bộ vòng đời: `checkUpdate(baseUrl)` / `downloadApk(url, file)` / `installDownloadedApk(context, info, file, autoInstallIfRooted)` (verify + install, trả `Flow<InstallState>`) / `installApk(context, file)` (mở trình cài đặt hệ thống, tự xin quyền) / `cleanupTempApkFiles(cacheDir)`. Tự dựng `jsonUrl` từ `baseUrl` + `jsonFileName` (mặc định suy từ app label qua `defaultJsonFileName(context)`) |
| `AppUpdateInstaller.kt` | Implementation nội bộ của phần verify/permission/install — `verifyApk`, `hasInstallPermission`/`requestInstallPermission`, `installApk`, `installApkRootSilent`, `cleanupTempApkFiles`. Gọi trực tiếp chỉ khi cần kiểm soát chi tiết hơn `installDownloadedApk` cho phép |
| `hostdiscovery/presentation/HostDiscoveryService.kt` | LAN host discovery, tự gate theo `AppUpdateSettings.getUpdateSourceMode` (mục 4b) — chỉ `:app` dùng |
| `AppUpdateDownloadFlow.kt` + `AppUpdateDownloadActions.kt` | UI flow "có bản cập nhật" dùng chung (mục 4a) — dialog New Upgrade → download progress → Ready/manual-install, im lặng hoàn toàn khi máy rooted. Chỉ vẽ UI; state vẫn do app giữ (thường ở ViewModel), đưa vào qua `onDownloadStateChanged`/`onInstallStateChanged` và `AppUpdateDownloadActions` |
| `AppUpdateInfoDialog.kt` | Dialog "Thông tin phiên bản" dùng chung — UI đầy đủ trên `AppUpdateSettings`, chỉ cần `versionName`/`buildNo` từ app gọi |
| `res/values/strings.xml`, `res/values-vi/strings.xml` | Toàn bộ text của `AppUpdateInfoDialog` + `AppUpdateDownloadFlow` — mặc định (không qualifier) là tiếng Anh, `values-vi/` là bản tiếng Việt. Cả 2 gọi `context.getString(...)`, nên tự hiển thị đúng ngôn ngữ mà app tích hợp đang chạy (theo locale thiết bị hoặc locale app tự set) — không có logic chọn locale thủ công nào cả, đó là cơ chế resource resolution mặc định của Android |
| `AppUpdateSettings.kt` | Setting + lịch sử dùng chung, lưu trong SharedPreferences riêng của module (`appupdate_settings`, qua `PrefsStore` ở `:libs:core`) — app chỉ cần dựng UI, không cần tự định nghĩa key: (1) khoảng thời gian tự check định kỳ (`getCheckIntervalSeconds`/`setCheckIntervalSeconds`, options chuẩn ở `CHECK_INTERVAL_OPTIONS`); (2) nguồn host để check — `UpdateSourceMode.LAN_ONLY` (app tự tìm host qua LAN discovery, module không can thiệp) hay `PUBLIC_HOST` (bỏ qua LAN, dùng thẳng `DEFAULT_PUBLIC_BASE_URL`) qua `getUpdateSourceMode`/`setUpdateSourceMode`; (3) **lịch sử chỉ-đọc** `getLastCheckedAtMillis()`/`getLastApkFilePath()` — được `AppUpdateChecker` tự ghi mỗi lần `checkUpdate()`/`downloadApk()` thành công, app không tự set (setter là `internal`) |
| `domain/model/UpdateInfo.kt` | Metadata bản cập nhật, parse từ JSON server |
| `domain/model/DownloadState.kt` | `Idle` / `Downloading(progress, downloadedMb, totalMb)` / `Success(file)` / `Error(message)` / `Cancelled` |
| `domain/model/InstallState.kt` | `Idle` / `Verifying(buildNo)` / `Ready(buildNo, file)` (chờ gọi `installApk`) / `Installing(buildNo)` / `Success(buildNo)` / `Error(buildNo, message)` — emit bởi `installDownloadedApk` |
| `domain/repository/UpdateRepository.kt` | Interface: `checkUpdate(jsonUrl)`, `downloadApk(url, file)` |
| `domain/usecase/CheckUpdateUseCase.kt` | `suspend operator fun invoke(jsonUrl): UpdateInfo?` |
| `domain/usecase/DownloadApkUseCase.kt` | `operator fun invoke(url, file): Flow<DownloadState>` |
| `data/datasource/UpdateRemoteDataSource.kt` | HTTP thật (HttpURLConnection), nơi cần sửa nếu đổi giao thức/thêm header |

## 6. Lưu ý quan trọng đã rút ra khi tích hợp (đọc trước khi debug lại từ đầu)

- **`downloadApk()` đã `conflate()` sẵn** — vì tải qua LAN/localhost thường nhanh hơn tốc độ UI thread vẽ progress bar, nếu không conflate thì `trySend()` sẽ nghẽn và toàn bộ download **âm thầm đứng lại giữa chừng, không có lỗi nào hiện ra**. Đừng bỏ `.conflate()` khi sửa datasource.
- **`AppUpdateInstaller.verifyApk` cố tình bỏ qua** trường hợp không đọc được chữ ký của file APK chưa cài (archive-only) — đây là lỗi nền tảng có thật trên một số máy Android 9/10 (`getPackageArchiveInfo` không parse được `signingInfo` của APK chỉ ký v2, không có v1/JAR). Nếu app của bạn build debug, hãy **bật `enableV1Signing = true`** cho signingConfig debug để giảm khả năng gặp lỗi này — nhưng đừng dựa hoàn toàn vào nó, vì một số máy vẫn không đọc được dù có v1.
- **Root detection (`DeviceUtils.isDeviceRooted`) vẫn nằm ở `:libs:core`**, không chuyển vào `appupdate` — nó được dùng chung bởi các feature khác không liên quan tới OTA (`:libs:logcat`, `TestTcpActivity`). `AppUpdateChecker.installDownloadedApk` gọi nó làm default cho `autoInstallIfRooted`, nhưng bản thân việc phát hiện root không phải là "của" module này. Nó thử **2 kiểu cú pháp `su`**: `su -c "cmd"` (Magisk/SuperSU) trước, rồi `su 0 sh -c "cmd"` (AOSP/toybox — bản `su` không hiểu `-c`, dùng trên emulator Google APIs không Magisk) — `AppUpdateInstaller.installApkRootSilent` thì **chưa** được cập nhật tương tự, vẫn chỉ dùng cú pháp `-c`.
- **`android:usesCleartextTraffic="true"`** là bắt buộc nếu server JSON/APK chạy `http://` thường (không TLS) — thiếu dòng này thì `checkUpdate()`/`downloadApk()` sẽ throw `CleartextNotPermittedException` ngay từ request đầu tiên, và vì exception bị nuốt trong try/catch nên triệu chứng chỉ là "không bao giờ thấy dialog cập nhật" — không có crash, dễ tưởng nhầm là JSON sai.
- `checkUpdate()`/`downloadApk()` không tự giữ tham chiếu tới Activity/Context — an toàn gọi từ `lifecycleScope`, nhưng **code xử lý kết quả (dialog, cập nhật UI) thì bạn phải tự canh `isFinishing`/`isDestroyed`** nếu có khả năng Activity bị hủy giữa lúc đang tải.
- **`AppUpdateSettings.DEFAULT_PUBLIC_BASE_URL` hiện là placeholder** (`https://TODO-public-host.example.com/ci-deploy`) — chưa trỏ tới server thật nào. Chọn `UpdateSourceMode.PUBLIC_HOST` trước khi thay hằng số này bằng URL public thật sẽ khiến `checkUpdate()` luôn thất bại (không crash, chỉ không bao giờ thấy có bản mới) — sửa trực tiếp trong `AppUpdateSettings.kt` khi đã có host public.
