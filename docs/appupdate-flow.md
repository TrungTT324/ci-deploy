# App Update Flow (module `app` — trạng thái hiện tại)

> Tài liệu này mô tả **luồng OTA hiện tại đang chạy trong module `app`** (CI-Deploy), sau loạt refactor gộp toàn bộ verify/permission/install/dialog vào `:libs:appupdate` để dùng chung. `docs/appupdate.md` (file cũ) mô tả kiến trúc trước refactor — phần "Các file liên quan" và "Flow tổng quát" ở đó **không còn đúng vị trí file**; phần JSON metadata, versioning, build/deploy ở đó vẫn còn đúng.

## Mục đích

`app` (CI-Deploy) tự kiểm tra bản mới trên CI server, tải APK, xác minh, rồi cài đặt — âm thầm qua root nếu máy hỗ trợ, hoặc qua trình cài đặt hệ thống với xác nhận người dùng nếu không. Toàn bộ cơ chế (check/download/verify/permission/install/dialog UI) giờ sống trong `:libs:appupdate`, dùng chung được cho app khác (xem `libs/appupdate/README.md`); `app` chỉ còn giữ phần thật sự đặc thù của nó: resolve host LAN, rewrite URL theo host, và wiring ViewModel↔UI.

## Ranh giới module

| Sống ở đâu | Thành phần | Vai trò |
|---|---|---|
| `:libs:appupdate` | `AppUpdateChecker` | Điểm tích hợp duy nhất: `checkUpdate`, `downloadApk`, `installDownloadedApk`, `installApk`, `cleanupTempApkFiles` |
| `:libs:appupdate` | `AppUpdateInstaller` | Implementation verify/permission/install (gọi nội bộ bởi `AppUpdateChecker`) |
| `:libs:appupdate` | `AppUpdateSettings` | Check-interval, `UpdateSourceMode` (LAN/Public), lịch sử last-checked/last-apk |
| `:libs:appupdate` | `AppUpdateDownloadFlow` + `AppUpdateDownloadActions` | Toàn bộ dialog UI (upgrade prompt → download progress → Ready/manual-install); **không giữ state**, chỉ vẽ UI và gọi ngược qua `Actions` |
| `:libs:appupdate` | `AppUpdateInfoDialog` | Dialog "Thông tin phiên bản" (settings cho interval + source mode) |
| `:libs:appupdate` | `hostdiscovery/*` | LAN host discovery, tự gate theo `UpdateSourceMode` |
| `:libs:core` | `DeviceUtils.isDeviceRooted()` | Root detection — **chỉ cái này** còn ở core, vì `:libs:logcat`/`TestTcpActivity` cũng dùng, không liên quan riêng OTA |
| `app` | `MainViewModel.kt` | Giữ **state thật** (`downloadState`/`installState` StateFlow, `updateInfo`/`currentHost` LiveData) — sống sót qua xoay màn hình vì nằm trong ViewModel; mọi hàm chỉ delegate xuống `AppUpdateChecker` |
| `app` | `MainActivityUpdate.kt` | Extension functions của `MainActivity`: nối observer ViewModel ↔ `AppUpdateDownloadFlow`, build `finalDownloadUrl` theo host, mở `AppUpdateInfoDialog` |
| `app` | `MainActivity.kt` | Khởi tạo `updateDownloadFlow`/`updateDownloadActions` (adapter gọi ngược ViewModel), bind `HostDiscoveryService`, gọi `checkForUpdates` ở các mốc vòng đời |
| `app` | `ServiceLocator.kt` | `appUpdateChecker` singleton (context = applicationContext), passthrough `getUpdateSourceMode()` cho ViewModel |
| `app` | `UpgradeReceiver.kt` | Nhận `ACTION_MY_PACKAGE_REPLACED`, tự mở lại app sau khi cài xong |

## Các file liên quan

- [AppUpdateChecker.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateChecker.kt)
- [AppUpdateInstaller.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateInstaller.kt)
- [AppUpdateSettings.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateSettings.kt)
- [AppUpdateDownloadFlow.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateDownloadFlow.kt) / [AppUpdateDownloadActions.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateDownloadActions.kt)
- [AppUpdateInfoDialog.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/AppUpdateInfoDialog.kt)
- [hostdiscovery/presentation/HostDiscoveryService.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/hostdiscovery/presentation/HostDiscoveryService.kt)
- [domain/model/UpdateInfo.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/UpdateInfo.kt) / [DownloadState.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/DownloadState.kt) / [InstallState.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/InstallState.kt)
- [MainViewModel.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/MainViewModel.kt)
- [MainActivity.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/MainActivity.kt) / [MainActivityUpdate.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/MainActivityUpdate.kt)
- [ServiceLocator.kt](../app/src/main/java/hdisoft/app/cideploy/di/ServiceLocator.kt)
- [UpgradeReceiver.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/UpgradeReceiver.kt)
- [DeviceUtils.kt](../libs/core/src/main/java/hdisoft/app/core/utils/DeviceUtils.kt) — `isDeviceRooted()`/`findSuPath()` (cùng chỗ với `isEmulator()`/`getDeviceId()` — không có `FileUtils.kt` riêng nữa, đã gộp vào đây)

## Flow tổng quát

### Giai đoạn 1 — Resolve host (mỗi lần app resume)

```text
App onCreate: bind HostDiscoveryService (chưa scan gì)
App onResume: luôn gọi discoveryService?.startDiscovery()
        |
        v
Chờ Wi-Fi bật + có IP (tối đa 15s)
        |
        v
Có host đã lưu (recent host) ?
       / \
     no   yes
      \    |
       \   verifyHostUseCase(savedHost)  (HTTP check thật, không phải so subnet)
        \       |
         \   host còn sống ?
          \    /   \
           \ yes    no
            \  |     |
             Success  UpdateSourceMode == LAN_ONLY ?
             (host)      /            \
                       yes            no
                        |              |
                  scan subnet     State.Error
                  (tối đa 3 lần,   ("Cannot reach <host>,
                  xem code)        LAN discovery is off")
                        |
                 tìm được host ?
                   /        \
                 yes         no (hết 3 lần)
                  |            |
              Success        Error
```

`HostDiscoveryService.startDiscovery()` **luôn thử host đã lưu trước tiên, bất kể `UpdateSourceMode`** — chỉ khi bước đó thất bại thì mới xét setting: `LAN_ONLY` mới được quét subnet, `PUBLIC_HOST` thì báo lỗi và dừng ngay (không quét, vì không có gì khác để thử). `State.Success(host)` được `MainActivity.handleDiscoveryState` chuyển thành `viewModel.setHost(host)`, việc này tự động: (a) load/refresh WebView tại `http://<host>:8080/ci-deploy` (qua `currentHost.observe`), và (b) gọi `viewModel.checkForUpdates(host)` — không có code riêng nào khác trong `MainActivity` cần gọi 2 việc này nữa.

### Giai đoạn 2 — Check version (sau khi có host)

```text
viewModel.checkForUpdates(host)
        |
        v
baseUrl = LAN_ONLY ? "http://<host>:8080/ci-deploy"
                    : AppUpdateSettings.DEFAULT_PUBLIC_BASE_URL   (còn là placeholder — xem "Ghi chú/TODO")
        |
        v
AppUpdateChecker.checkUpdate(baseUrl)  -> UpdateInfo?
        |
   buildNo > BuildConfig.BUILD_NO ?
       / \
     no   yes
     |     |
   done   MainActivityUpdate: build finalDownloadUrl (rewrite host+urlPath+filename từ info.url)
           |
           v
   AppUpdateDownloadFlow.onUpdateAvailable(info, finalDownloadUrl)
           |
   DeviceUtils.isDeviceRooted() ?
       /            \
     yes             no
      |               |
  clearUpdateInfo   showUpgradeDialog (Yes/No)
  + startDownload         |
      |                  Yes -> clearUpdateInfo + startDownload (có dialog progress)
      |                  No  -> dismissVersion + resumePeriodicUpdateCheck
      |
      v
  appUpdateChecker.downloadApk(url, file)  ->  Flow<DownloadState>
      |
  Success ?
    / \
  no   yes
  |     |
 lỗi/hủy   AppUpdateChecker.installDownloadedApk(context, info, file, autoInstallIfRooted)
 -> resetUpdateOperation      |
                       Verifying -> AppUpdateInstaller.verifyApk
                              |
                        hợp lệ ?
                         /    \
                       no     yes
                       |       |
                     Error   autoInstallIfRooted ?
                     (xoá file)   /        \
                              yes          no
                               |            |
                        Installing        Ready(file)
                        (su/pm install)    -> dialog Install/Later
                               |
                          Success/Error
```

Một thời điểm chỉ chạy một check job và một download job (`MainViewModel` tự guard bằng `checkUpdateJob`/`downloadJob`). Khi Activity recreate (xoay màn hình), `downloadState`/`installState` StateFlow **sống sót** vì nằm trong ViewModel; `AppUpdateDownloadFlow` thì **không** — nó được tạo lại (`by lazy`) mỗi khi `MainActivity` được tạo lại, nên dialog có thể tạm biến mất giữa chừng cho tới khi state tiếp theo tới, nhưng job download/install ở tầng ViewModel không bị hủy.

## Settings ảnh hưởng tới flow

- **Check interval** (`AppUpdateSettings.getCheckIntervalSeconds`) — 0 = chỉ check khi mở app/resume; >0 = `MainViewModel.restartUpdateCheckTimer()` chạy vòng lặp `delay(interval) -> checkForUpdates(host)`. Đổi qua `AppUpdateInfoDialog`, gọi `viewModel.setCheckInterval(...)`.
- **Update source** (`AppUpdateSettings.getUpdateSourceMode`) — `LAN_ONLY` (mặc định) hay `PUBLIC_HOST`. Ảnh hưởng 2 chỗ: (1) `HostDiscoveryService.startDiscovery()` chỉ xét setting này **sau khi** host đã lưu không dùng được — quyết định quét subnet tiếp (`LAN_ONLY`) hay báo lỗi dừng luôn (`PUBLIC_HOST`); (2) `MainViewModel.checkForUpdates` chọn `baseUrl` khác nhau. Đổi qua `AppUpdateInfoDialog` → `onSourceModeSaved` callback trong `MainActivityUpdate.kt` chủ động gọi lại `checkForUpdates` + `discoveryService?.startDiscovery()` ngay, không cần chờ lần mở app kế tiếp.
- **`AppUpdateSettings.DEFAULT_PUBLIC_BASE_URL`** — hiện là placeholder (`https://TODO-public-host.example.com/ci-deploy`). Chọn `PUBLIC_HOST` trước khi thay hằng số này bằng URL thật sẽ khiến `checkUpdate()` luôn thất bại âm thầm (không crash, chỉ không bao giờ thấy bản mới).

## State machine

### DownloadState
```text
Idle -> Downloading -> Success
                    \-> Error
                    \-> Cancelled
```

### InstallState
```text
Idle -> Verifying -> Ready        (chờ AppUpdateDownloadFlow gọi installApk khi người dùng bấm Install)
                  \-> Installing -> Success   (chỉ khi autoInstallIfRooted = true)
                                \-> Error
```

`InstallState.Error` không tự chứa cờ "đã rooted hay chưa" — `AppUpdateDownloadFlow.onInstallStateChanged` tự gọi lại `DeviceUtils.isDeviceRooted()` để quyết định hiển thị dialog manual-install-fallback (rooted, root-install lỗi) hay chỉ cập nhật text lỗi trong dialog download đang mở (không rooted).

Đừng nhầm với `HostDiscoveryService.State` (`Idle`/`Progress`/`Success`/`Error`) — đây là state machine **khác**, của host resolution (giai đoạn 1), không phải của download/install.

## Xác minh & cài đặt (`AppUpdateInstaller`)

Không đổi so với logic gốc, chỉ đổi vị trí file (từ `FileUtils` sang `AppUpdateInstaller`, `:libs:appupdate`):

1. `verifyApk`: file tồn tại + không rỗng → size khớp `sizeBytes` → SHA-256 khớp → parse được như APK → package name khớp → chữ ký khớp APK đang cài (bỏ qua nếu không đọc được signingInfo trên APK v2-only chưa cài, một giới hạn nền tảng có thật trên Android 9/10 — SHA-256 đã đủ đảm bảo tính toàn vẹn trong trường hợp đó).
2. `installApkRootSilent`: 2 phase qua `su` — copy APK vào `/data/local/tmp` qua stdin, rồi `cmd package install -r` (fallback `pm install -r`). Chỉ coi là thành công khi exit code = 0 **và** output có dòng `Success`; exit code của lệnh `rm` cleanup không tính.
3. `installApk` (manual): kiểm tra `canRequestPackageInstalls()` (Android 8+) — thiếu quyền thì mở `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` và dừng (người dùng phải bấm Install lại); có quyền thì mở APK qua `FileProvider` (`${applicationId}.fileprovider`).

Root detection (`DeviceUtils.isDeviceRooted()`, `:libs:core` — trước đây ở `FileUtils`, đã gộp hẳn vào `DeviceUtils` cùng `isEmulator()`/`getDeviceId()`, không còn `FileUtils.kt` riêng) chỉ trả `true` khi `su` thực sự trả `uid=0` — không dựa vào sự tồn tại của file `su`. **Chỉ cache kết quả `true` vĩnh viễn cho cả process** — kết quả `false` không bao giờ được cache, luôn check lại ở lần gọi sau. Trước đây cache cả `false`: nếu lần gọi `isDeviceRooted()` đầu tiên (thường rất sớm, ngay khi mở app) rơi vào lúc root daemon chưa sẵn sàng hoặc app quản lý root (Magisk...) chưa kịp hiện/được trả lời prompt "Grant root access?", kết quả `false` đó bị khoá cứng cho tới khi process bị kill — khiến toàn bộ flow tự động rơi vào nhánh xác nhận thủ công dù máy **thực sự đã root**.

**`isDeviceRooted()` cũng thử 2 kiểu cú pháp `su`**, không chỉ 1: `su -c "<cmd>"` (Magisk/SuperSU) trước, rồi fallback `su 0 sh -c "<cmd>"` (AOSP/toybox). Verify trực tiếp trên 1 emulator Google APIs thật (image không Magisk): `su` ở đó là bản toybox, `usage: su WHO COMMAND...`, **không hiểu `-c`** — `su -c id` báo lỗi `invalid uid/gid '-c'` dù `adb root`/`adb shell id` xác nhận máy có `uid=0` thật. Đây là nguyên nhân cụ thể, đã verify (không phải giả thuyết) cho triệu chứng "máy rooted nhưng UI vẫn không hiện `(rooted)`/vẫn đòi xác nhận cài đặt". **Lưu ý: `AppUpdateInstaller.installApkRootSilent` chưa được sửa tương tự** — vẫn chỉ dùng cú pháp `-c`, nên detection có thể đúng (`true`) trong khi bước install thật sự vẫn có thể fail trên máy dùng toybox `su`, rơi xuống dialog manual-install-fallback.

`MainActivity` hiển thị trạng thái root ở **title** của actionbar ("CI-Deploy (rooted)"), tách riêng khỏi subtitle (chỉ còn "Build: ..."). `updateRootStatusTitle()` tự check lại mỗi lần `onCreate`/`onResume` (không dùng `by lazy` cache 1 lần như trước) — chạy `isDeviceRooted()` trên `Dispatchers.IO` rồi cập nhật title trên main thread, vì đây là lệnh `exec` chặn luồng (tối đa 5s) và giờ có thể chạy lại nhiều lần trong 1 phiên, không chỉ 1 lần lúc khởi động.

## Cam kết: hoàn toàn im lặng khi máy rooted

Khi `DeviceUtils.isDeviceRooted()` trả `true` (không bị cache sai như trên), **không có bước nào trong toàn bộ flow — resolve host, check version, download, verify, install — hiển thị dialog chờ người dùng xác nhận**:

- `AppUpdateDownloadFlow.onUpdateAvailable`: bỏ qua `showUpgradeDialog`, gọi thẳng `startDownload`.
- `onDownloadStateChanged` (rooted): không dựng dialog progress, chỉ log + tự gọi `verifyDownloadedApk(..., autoInstallWithRoot = true)` khi `Success`.
- `onInstallStateChanged`: `Verifying`/`Installing`/`Success` không có UI gì (nhánh `else -> Unit`); root luôn `autoInstallIfRooted = true` nên không bao giờ nhận `Ready` (trạng thái duy nhất chờ người dùng bấm Install).

**Ngoại lệ duy nhất, có chủ đích**: nếu root-install thực sự **thất bại** (`InstallState.Error` khi đang rooted, ví dụ chữ ký APK không khớp — root không bỏ qua được việc PackageManager kiểm tra chữ ký, hoặc `su` từ chối lệnh) thì `showManualInstallFallbackDialog` mới hiện ra, hỏi Install/Skip. Đây là quyết định có chủ đích (giữ lại đường lui cho người dùng khi tự động thất bại thật sự), không phải bug cần bỏ.

## Sau khi cài xong

`UpgradeReceiver` nhận `ACTION_MY_PACKAGE_REPLACED` → mở lại `MainActivity` (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`). Không có logic app-update nào chạy trong receiver ngoài việc relaunch.

## Ghi chú / TODO còn tồn đọng

- `AppUpdateSettings.DEFAULT_PUBLIC_BASE_URL` vẫn là placeholder — `PUBLIC_HOST` mode chưa dùng thật được (xem trên).
- `AppUpdateDownloadFlow` được tạo mới (`by lazy`) mỗi khi `MainActivity` recreate; nó không tự phục hồi dialog đang hiển thị dở, chỉ phản ứng đúng với state kế tiếp nhận được từ ViewModel — đây là hạn chế đã tồn tại từ trước refactor (không phải regression mới).
- `docs/appupdate.md` (file cũ) cần được coi là tài liệu tham khảo cho phần JSON metadata / versioning / build.sh / agent-deploy — phần kiến trúc code ở đó đã lỗi thời, dùng tài liệu này (`appupdate-flow.md`) làm nguồn chính xác cho phần flow/kiến trúc.
- Đã dọn `MainActivity.kt`: bỏ so sánh subnet thủ công trong `onResume()` (thay bằng việc luôn gọi `discoveryService?.startDiscovery()`, service tự verify host thật qua HTTP thay vì so chuỗi subnet), bỏ hàm `showNoHostDialog()` (dead code, không còn nơi nào gọi), và bỏ `HostDiscoveryService.State.Disabled` (gộp vào `State.Error` vì giờ nó chỉ xuất hiện sau khi đã thử host thật và thất bại — không còn là trạng thái "trung lập" nữa).
- `FileUtils.kt` (`:libs:core`) không còn tồn tại — `isDeviceRooted()`/`findSuPath()` đã gộp vào `DeviceUtils.kt` cùng module, và đồng thời được sửa để hỗ trợ cả cú pháp `su` kiểu Magisk (`-c`) lẫn kiểu AOSP/toybox (`WHO CMD...`).
- **Follow-up chưa làm**: `AppUpdateInstaller.installApkRootSilent` (`:libs:appupdate`) vẫn chỉ dùng cú pháp `su -c "<cmd>"` cho cả 2 phase (copy APK + chạy installer) — chưa được sửa để thử fallback toybox như `isDeviceRooted()`. Nghĩa là trên máy dùng toybox `su` (ví dụ emulator Google APIs không cài Magisk), `isDeviceRooted()` giờ có thể trả `true` đúng, nhưng bước cài đặt âm thầm thực tế vẫn có thể fail — rơi xuống dialog manual-install-fallback (đây là hành vi fallback có chủ đích, không phải crash).

## Kiểm thử đề xuất

- JSON hợp lệ và JSON thiếu từng trường bắt buộc.
- HTTP 404/500, timeout và response bị truncate.
- Cancel khi đang download và lập tức bấm Yes lần nữa.
- Hash sai, size sai, package sai, chữ ký sai.
- Root `pm install` thành công và thất bại (kiểm tra dialog manual-install-fallback xuất hiện đúng).
- Manual install khi chưa cấp quyền "install unknown apps".
- Activity recreate (xoay màn hình) trong lúc download, verify và install — xác nhận job không bị hủy, dialog phản ứng đúng khi quay lại foreground.
- Đổi `UpdateSourceMode` giữa `LAN_ONLY`/`PUBLIC_HOST` khi đang có bản cập nhật chờ xử lý.
- Đổi check interval về 0 rồi về >0, xác nhận timer restart đúng.
- Host đã lưu còn sống → `Success` ngay, không quét subnet (cả 2 mode).
- Host đã lưu chết + `LAN_ONLY` → rơi xuống quét subnet.
- Host đã lưu chết + `PUBLIC_HOST` → `Error` ngay, không quét, không retry.
- Không có host đã lưu (cài mới) + `PUBLIC_HOST` → `Error` ngay từ lần mở app đầu tiên.
- Resume liên tục (background/foreground nhanh) khi đã có host sống — xác nhận `startDiscovery()` không làm gì bất thường khi đang `Progress` (guard ở đầu hàm).
- Máy rooted nhưng lần gọi `isDeviceRooted()` **đầu tiên** thất bại (mô phỏng bằng cách trì hoãn prompt "Grant root" của Magisk) — xác nhận lần gọi **sau** vẫn tự phát hiện lại root thay vì kẹt cứng ở `false`.
- Máy rooted, root-install thành công toàn trình — xác nhận không có bất kỳ dialog/Toast chờ tap nào xuất hiện từ lúc có `UpdateInfo` tới khi cài xong.
