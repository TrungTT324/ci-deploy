# App Update Feature

> ⚠️ **Phần kiến trúc code/flow trong file này đã lỗi thời** (viết trước khi verify/permission/install/dialog UI được gộp vào `:libs:appupdate`). Xem [appupdate-flow.md](appupdate-flow.md) cho flow và vị trí file hiện tại. Phần JSON metadata, versioning, build/deploy bên dưới vẫn còn đúng.

## Mục đích

App Update của CI-Deploy kiểm tra metadata phiên bản trên CI server, tải APK debug đã ký bằng project key, xác minh file rồi cài đặt bằng root hoặc trình cài đặt Android.

Artifact OTA vẫn là **debug APK** để phục vụ môi trường test. Debug APK được ký bằng cùng `ci-deploy.jks` với release, vì Android chỉ cho phép update khi certificate của APK mới khớp APK đang cài.

## Các file liên quan

### Domain và data

- [UpdateInfo.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/UpdateInfo.kt): metadata từ version JSON.
- [DownloadState.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/DownloadState.kt): state của download.
- [InstallState.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/domain/model/InstallState.kt): state xác minh/cài đặt.
- [UpdateRemoteDataSource.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/data/datasource/UpdateRemoteDataSource.kt): HTTP check và download.
- [UpdateRepositoryImpl.kt](../libs/appupdate/src/main/java/hdisoft/app/appupdate/data/repository/UpdateRepositoryImpl.kt): repository implementation.
- `libs/appupdate/.../domain/usecase/`: các use case check và download.

### App orchestration

- [MainViewModel.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/MainViewModel.kt): điều phối job check/download/verify/install và periodic check.
- [MainActivity.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/MainActivity.kt): dialog, progress UI, manual install và root install.
- [DeviceUtils.kt](../libs/core/src/main/java/hdisoft/app/core/utils/DeviceUtils.kt): kiểm tra root (`isDeviceRooted()`) — phần xác minh APK/cài đặt đã chuyển sang `AppUpdateInstaller` (`:libs:appupdate`), xem `appupdate-flow.md`.
- [UpgradeReceiver.kt](../app/src/main/java/hdisoft/app/cideploy/features/main/presentation/UpgradeReceiver.kt): mở lại ứng dụng sau `MY_PACKAGE_REPLACED`.

### Build/deploy

- [build.sh](../build.sh): build và publish APK debug cùng version JSON.
- [agent-deploy/main.py](../agent-deploy/main.py): build/copy APK từ web deploy agent.
- [agent-deploy/projects.json](../agent-deploy/projects.json): cấu hình artifact và metadata của `ci-deploy`.
- [app/build.gradle.kts](../app/build.gradle.kts): `BUILD_NO`, `versionCode` và signing config.

## Flow tổng quát

```text
Host discovery thành công
        |
        v
GET /ci-deploy/ci-deploy-version.json
        |
        v
remote buildNo > current BUILD_NO ?
       / \
     no   yes
     |      |
   done   root usable ?
            /       \
          yes       no
           |         |
   auto download   Upgrade dialog
           |         |
           +---- download ----+
                    |
                    v
          size + SHA-256 + APK package
          + installed signature check
                    |
             valid APK ?
              /       \
            no        yes
            |          |
          error   root install / manual Install
```

Một thời điểm chỉ được chạy một check job và một download job. `MainViewModel` giữ các job trong `viewModelScope`; khi Activity recreate, state flow được phát lại nhưng `beginVerification()` ngăn việc verify/install trùng.

## Metadata JSON

Server phải trả JSON có dạng:

```json
{
  "appName": "CI-Deploy",
  "version": "1.0.0",
  "buildNo": 202607181118,
  "buildNote": "Fix Bluetooth and OTA flow",
  "url": "http://192.168.1.135:8080/ci-deploy/CI-Deploy_debug.apk",
  "sha256": "...64 hex characters...",
  "sizeBytes": 12345678,
  "packageName": "hdisoft.app.cideploy"
}
```

Các trường bắt buộc cho OTA:

- `buildNo > 0`: dùng để so với `BuildConfig.BUILD_NO`.
- `url`: URL tải APK.
- `sha256`: SHA-256 của đúng file APK được trỏ bởi `url`.
- `sizeBytes`: kích thước byte chính xác của APK.
- `packageName`: phải là `hdisoft.app.cideploy`.

Nếu thiếu metadata hash/size, update bị từ chối thay vì cài file không xác minh.

## Download

`UpdateRemoteDataSource` dùng `HttpURLConnection` với timeout kết nối 10 giây và timeout đọc 15 giây.

Download được ghi vào file tạm:

```text
CI-Deploy_upgrade.apk.part
```

Chỉ sau khi:

1. Đọc hết response.
2. Đóng/flush output stream.
3. Kiểm tra `Content-Length` nếu server cung cấp.
4. Đổi tên atomic từ `.part` sang `CI-Deploy_upgrade.apk`.

thì mới phát `DownloadState.Success`.

Khi hủy:

- Coroutine/job bị cancel.
- Connection và stream được đóng.
- Worker download cũ vẫn được giữ tới khi kết thúc cleanup.
- File `.part` bị xóa.
- Không cho phép tạo download mới khi job cũ chưa kết thúc.

## State

### DownloadState

```text
Idle -> Downloading -> Success
                    \-> Error
                    \-> Cancelled
```

### InstallState

```text
Idle -> Verifying -> Ready       (manual install)
                  \-> Installing -> Success
                                \-> Error
```

`InstallState.Error` luôn chứa build number và lý do. Với thiết bị root, lỗi root install mở tùy chọn manual install; với thiết bị không root, lỗi hiển thị trong dialog download.

## Xác minh APK

`AppUpdateInstaller.verifyApk()` (`:libs:appupdate`) kiểm tra theo thứ tự:

1. File tồn tại và không rỗng.
2. Kích thước bằng `sizeBytes`.
3. SHA-256 bằng metadata.
4. File parse được như APK.
5. Package name đúng `hdisoft.app.cideploy`.
6. Certificate của APK trùng certificate ứng dụng đang cài.

Bước certificate đặc biệt quan trọng trong môi trường debug. `assembleDebug` phải dùng signing config `release` trong [app/build.gradle.kts](../app/build.gradle.kts), nếu không Android sẽ báo `signatures do not match previously installed version`.

## Cài đặt root

`installApkRootSilent()` thực hiện hai phase:

1. Dùng `su -c "cat > /data/local/tmp/...apk"` để copy APK.
2. Chạy `cmd package install -r` và fallback `pm install -r`.

Kết quả chỉ được xem là thành công khi:

- Process kết thúc trong timeout.
- Exit code của lệnh installer là `0`.
- Output có dòng `Success`.

Không dùng exit code của lệnh cleanup (`rm`) làm kết quả cài đặt.

Root detection chỉ trả true khi `su -c id` thực sự trả `uid=0`; không dựa riêng vào file `su` hoặc build tags.

## Cài đặt manual

Trên Android 8 trở lên, nếu ứng dụng chưa có quyền `REQUEST_INSTALL_PACKAGES`, app mở màn hình Settings để người dùng bật quyền nguồn không xác định. Sau đó người dùng nhấn Install lại.

APK được mở bằng `FileProvider` với authority:

```text
hdisoft.app.cideploy.fileprovider
```

## Debug artifact và deploy

`build.sh` tạo và publish artifact debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Nhưng version JSON OTA trỏ tới:

```text
CI-Deploy_debug.apk
```

Script này không build hoặc publish release APK; release có thể được build riêng bằng Gradle khi cần.

Publish dùng file tạm rồi `mv` sang tên chính, giúp client không đọc phải file đang copy dở. JSON được ghi sau APK.

`agent-deploy/projects.json` phải giữ các giá trị đồng nhất:

```json
{
  "apkPath": "app/build/outputs/apk/debug/app-debug.apk",
  "artifactFileName": "CI-Deploy_debug.apk",
  "url": "/ci-deploy/CI-Deploy_debug.apk",
  "packageName": "hdisoft.app.cideploy"
}
```

Không đổi hoa/thường giữa tên file, URL và JSON.

## Versioning

- `BUILD_NO`: timestamp `yyyyMMddHHmm`, dùng cho UI và update check.
- `versionCode`: số nguyên tăng theo phút, dùng cho Android package manager.
- `versionName`: phiên bản hiển thị cho người dùng.

`BUILD_NO` và `versionCode` có mục đích khác nhau; không dùng `versionCode = 1` cố định cho các OTA build mới.

## Xử lý lỗi thường gặp

### Không thấy update

- Kiểm tra host hiện tại và HTTP status của JSON.
- Kiểm tra `remote buildNo` lớn hơn `BuildConfig.BUILD_NO`.
- Kiểm tra `url`, `sha256`, `sizeBytes`, `packageName` không rỗng.
- Kiểm tra periodic interval không bị tắt.

### `APK SHA-256 does not match`

Hash JSON không được tính trên đúng artifact hoặc file đã bị thay sau khi JSON publish. Tính lại hash từ `CI-Deploy_debug.apk`, rồi ghi JSON sau cùng.

### `APK signature does not match`

APK server được ký bằng key khác APK đang cài. Với flow debug hiện tại, debug phải dùng `ci-deploy.jks`; không dùng Android default debug keystore.

### Root install báo fail

Đọc log tag `CI_DEPLOY_ROOT`, đặc biệt output `cmd package install`/`pm install`. Kiểm tra package name, signature, versionCode và quyền `su`.

### Download bị lặp

Không gọi `startDownload()` lần nữa khi `downloadJob` còn active. Chờ worker cũ cleanup xong sau cancel; không tự đặt job về null từ UI.

## Kiểm thử đề xuất

- JSON hợp lệ và JSON thiếu từng trường bắt buộc.
- HTTP 404/500, timeout và response bị truncate.
- Cancel khi đang download và lập tức nhấn Yes lần nữa.
- Hash sai, size sai, package sai, certificate sai.
- Root `pm install` thành công và thất bại.
- Manual install khi chưa cấp unknown-source permission.
- Activity recreate trong lúc download, verify và install.
- Hai lần update liên tiếp với `versionCode` tăng.
- Kiểm tra server chỉ publish JSON sau khi APK hoàn tất.
