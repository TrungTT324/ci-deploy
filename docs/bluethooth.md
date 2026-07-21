# Bluetooth Host/Client trong CI-Deploy

## 1. Mục đích

Tính năng Bluetooth trong module `app` dùng để kết nối trực tiếp hai thiết bị Android và gửi/nhận tin nhắn văn bản nhằm kiểm thử giao tiếp thiết bị.

Tính năng có hai vai trò:

- **Host**: mở Bluetooth server socket và chờ một Client kết nối.
- **Client**: tìm thiết bị, thực hiện ghép đôi nếu cần và chủ động kết nối tới Host.

Sau khi kết nối thành công, cả hai thiết bị tự động chuyển sang màn hình Bluetooth Chat để gửi và nhận dữ liệu.

> Tính năng sử dụng Bluetooth Classic với RFCOMM/SPP, không sử dụng Bluetooth Low Energy (BLE).

## 2. Thành phần chính

### `BluetoothConnector`

Đường dẫn:

```text
app/src/main/java/hdisoft/app/cideploy/features/bluetooth/data/BluetoothConnector.kt
```

Đây là lớp quản lý toàn bộ Bluetooth transport:

- Kiểm tra Bluetooth có được thiết bị hỗ trợ và đang bật hay không.
- Kiểm tra quyền Bluetooth theo phiên bản Android.
- Đọc danh sách thiết bị đã ghép đôi.
- Discovery các thiết bị ở gần.
- Mở server socket ở Host.
- Mở client socket và kết nối tới Host.
- Duy trì input/output stream sau khi kết nối.
- Gửi, nhận tin nhắn và phát callback trạng thái cho giao diện.
- Đóng discovery, socket và thread khi đổi mode hoặc ngắt kết nối.

### `BluetoothTestActivity`

Đường dẫn:

```text
app/src/main/java/hdisoft/app/cideploy/features/bluetooth/presentation/BluetoothTestActivity.kt
```

Màn hình chọn mode và thiết lập kết nối:

- Yêu cầu quyền Bluetooth.
- Yêu cầu người dùng bật Bluetooth bằng dialog hệ thống.
- Chọn **Host Mode** hoặc **Client Mode**.
- Hiển thị trạng thái kết nối.
- Hiển thị danh sách thiết bị paired/discovered trong Client Mode.
- Tự mở `BluetoothChatActivity` khi kết nối thành công.

### `BluetoothChatActivity`

Đường dẫn:

```text
app/src/main/java/hdisoft/app/cideploy/features/bluetooth/presentation/BluetoothChatActivity.kt
```

Màn hình giao tiếp sau khi kết nối:

- Hiển thị vai trò Host/Client và tên thiết bị đầu xa.
- Gửi tin nhắn text.
- Hiển thị log tin nhắn gửi/nhận kèm thời gian.
- Xóa log trên giao diện.
- Ngắt kết nối khi người dùng thoát hoặc chọn Disconnect.
- Tự đóng màn hình khi thiết bị đầu xa ngắt kết nối.

## 3. Kỹ thuật kết nối

### Bluetooth profile và UUID

Kết nối dùng secure RFCOMM socket với service name và UUID cố định:

```text
Service name: CIDeployBluetooth
UUID: 00001101-0000-1000-8000-00805F9B34FB
```

Đây là UUID phổ biến của Serial Port Profile (SPP). Host và Client phải dùng cùng UUID để tìm đúng service.

Host tạo socket bằng:

```kotlin
listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
```

Client tạo socket bằng:

```kotlin
device.createRfcommSocketToServiceRecord(MY_UUID)
```

### Mô hình thread

`BluetoothConnector` dùng ba thread riêng:

| Thread | Mode | Trách nhiệm |
| --- | --- | --- |
| `AcceptThread` | Host | Chặn tại `accept()` và chờ Client kết nối |
| `ConnectThread` | Client | Gọi `connect()` tới RFCOMM service của Host |
| `ConnectedThread` | Cả hai | Đọc input stream và ghi output stream sau khi kết nối |

Các thao tác thay đổi mode, quản lý socket và disconnect được đồng bộ bằng `@Synchronized` hoặc `synchronized` để hạn chế race condition.

Khi một thread/socket cũ hoàn tất muộn sau khi đã đổi mode, connector kiểm tra instance thread hiện tại trước khi cập nhật state. Cơ chế này tránh thread cũ ghi đè trạng thái của phiên kết nối mới.

### Protocol tin nhắn

Mỗi tin nhắn được gửi dưới dạng một dòng:

```text
<message>\n
```

Phía nhận dùng `bufferedReader().readLine()` để tách từng message. Vì vậy:

- Một lần nhấn Send tương ứng một message.
- Ký tự xuống dòng được dùng làm delimiter và không xuất hiện trong message nhận.
- Phiên bản hiện tại dùng charset mặc định của runtime ở cả hai đầu.
- Nếu mở rộng giao thức, nên chuyển sang charset `UTF-8` tường minh và dùng packet có header/length thay vì chỉ dùng newline.

### Chia sẻ kết nối giữa hai Activity

Connector đang hoạt động được giữ tại:

```kotlin
BluetoothConnector.sharedConnector
```

`BluetoothTestActivity` tạo connector và `BluetoothChatActivity` lấy lại cùng instance này. Nhờ đó socket không bị đóng khi chuyển màn hình.

Connector giữ `applicationContext`, không giữ trực tiếp Activity context, nhằm hạn chế memory leak.

Lưu ý: `sharedConnector` chỉ tồn tại trong process của ứng dụng. Nếu Android kill process thì kết nối và phiên chat cũng mất.

## 4. Mode và state

### Mode

| Mode | Ý nghĩa |
| --- | --- |
| `NONE` | Chưa chọn vai trò hoặc đã disconnect hoàn toàn |
| `HOST` | Thiết bị đóng vai trò server và chờ Client |
| `CLIENT` | Thiết bị chủ động tìm và kết nối tới Host |

Mode hiện tại được lưu trong `BluetoothConnector.currentMode`.

### Connection state

| State | Ý nghĩa |
| --- | --- |
| `IDLE` | Chưa có thao tác kết nối đang chạy |
| `LISTENING` | Host đang chờ tại server socket |
| `CONNECTING` | Client đang thực hiện `socket.connect()` |
| `CONNECTED` | RFCOMM socket đã kết nối và có thể gửi/nhận |
| `DISCONNECTED` | Kết nối vừa bị đóng hoặc thiết bị đầu xa đã rời phiên |

Luồng trạng thái Host:

```text
NONE/IDLE
  -> chọn Host
HOST/LISTENING
  -> Client kết nối
HOST/CONNECTED
  -> đóng socket
HOST/DISCONNECTED
```

Luồng trạng thái Client:

```text
NONE/IDLE
  -> chọn Client
CLIENT/IDLE
  -> chọn thiết bị
CLIENT/CONNECTING
  -> kết nối thành công
CLIENT/CONNECTED
  -> đóng socket
CLIENT/DISCONNECTED
```

Khi gọi `disconnect()`, connector đóng discovery và tất cả socket/thread, xóa thiết bị hiện tại, chuyển mode về `NONE`, sau đó đưa state về `IDLE`.

## 5. Security handshake (CI-Deploy identity)

`BluetoothSecurityLayer` là lớp wrapper độc lập đặt trên `BluetoothConnector`. Có thể bật/tắt lớp này mà không thay đổi transport Bluetooth. Connector chỉ báo `CONNECTED` khi RFCOMM đã mở; màn hình Chat chỉ được mở sau khi handshake thành công.

Trên màn hình **Bluetooth Test**, card **BLUETOOTH EVENT LOG** hiển thị timestamp, action người dùng, state connector, discovery, authentication và lỗi. Log tự cuộn xuống dòng mới nhất và giữ nội dung selectable để sao chép khi chẩn đoán.

Switch **Security handshake** bật mặc định. Tắt switch sẽ bỏ qua handshake, cho phép kết nối/gửi message trực tiếp để debug; khi tắt, ứng dụng không còn đảm bảo peer là CI-Deploy.

Bản Debug hiện dùng **Simple Auth**: sau khi RFCOMM connected, mỗi phía gửi đúng chuỗi `CI_DEPLOY`; chỉ khi cả hai đã gửi và nhận chuỗi này mới pass authenticate. Challenge-response nonce/proof cũ vẫn được giữ trong `BluetoothSecurityLayer` (`simpleAuthentication=false`) để dùng cho phiên security nâng cao sau này.

Peer đã authenticate được lưu address/name vào local storage. Nút **P2P** lấy danh sách Bluetooth đã pair, ưu tiên peer đã lưu rồi tự chọn peer còn lại. Hai thiết bị chọn vai trò deterministic theo tên Bluetooth (fallback từ danh sách pair): thiết bị có khóa nhỏ hơn làm Host, thiết bị còn lại làm Client. Vì vậy không cần chọn thiết bị thủ công sau khi đã pair.

Nếu một paired device không connect được, authentication bị reject hoặc disconnect trước khi xác thực, P2P đánh dấu peer đó thất bại và tự thử paired device kế tiếp; khi hết danh sách, flow dừng và ghi log.

Mỗi lần thử được ghi dạng `P2P: TRY i/n` kèm name/address, role decision, và `P2P: SKIP ... reason=...`, giúp xác nhận toàn bộ danh sách đã được duyệt.

Mode Host/Client/P2P được lưu trong local storage khi người dùng chọn. Lần mở lại Bluetooth Test, mode cuối cùng sẽ tự khởi động một lần nếu Bluetooth và permission đã sẵn sàng.

Sau authentication thành công, app lưu Recent Connections gồm `name`, `address`, `mode`, `connectedAt`; giữ tối đa 20 bản ghi và đưa bản ghi cùng address lên đầu khi kết nối lại.

Khi mở Bluetooth Test hoặc reload danh sách, các thiết bị recent vẫn đang paired được đưa lên đầu danh sách để thao tác reconnect nhanh; các thiết bị paired khác hiển thị phía sau.

UI hiển thị Recent Connections thành một group riêng, tách khỏi Paired Devices và không lặp cùng một thiết bị ở hai group.

Group Recent luôn hiển thị; khi chưa có lịch sử hoặc các peer recent không còn paired, UI hiển thị `No recent connections` thay vì ẩn group.

Khi back từ Chat về Bluetooth Test, Activity luôn reload lại Recent/Paired devices trong `onResume`; card danh sách không bị ẩn theo mode connector tạm thời.

Hai phía thực hiện challenge-response:

```text
CONNECTED
  -> HELLO(app=CI_DEPLOY, role, nonce)
  -> kiểm tra app và role đối diện
  -> AUTH(nonce, proof=SHA-256(secret | nonce-order))
  -> AUTH_OK
  -> AUTHENTICATED -> mở Chat / gửi message
```

Protocol dùng prefix `CDS1`. Nếu `app` khác `CI_DEPLOY`, role không đối nghịch, nonce/proof sai hoặc quá 10 giây không hoàn tất, layer gửi `REJECT` rồi đóng socket. Mọi dữ liệu ứng dụng nhận trước trạng thái `AUTHENTICATED` đều bị loại bỏ; `sendMessage()` cũng bị từ chối trước bước này.

Proof dùng thứ tự nonce chuẩn hóa (sắp xếp tăng dần), tương đương `SHA-256(secret | min(nonceA, nonceB) | max(nonceA, nonceB))`; nhờ vậy hai phía không phụ thuộc vào góc nhìn local/remote.

Trong giai đoạn nâng cấp, layer tạm chấp nhận hai thứ tự nonce legacy để thiết bị chưa cập nhật APK không bị fail ngay; log reject sẽ ghi 8 ký tự đầu của nonce nhận/đợi để chẩn đoán mà không lộ secret.

Lớp này ngăn kết nối nhầm sang ứng dụng khác ở mức application. Shared secret được đóng gói trong app nên không phải cơ chế chống reverse-engineering tuyệt đối; nên kết hợp pairing/RFCOMM secure socket của Android để có mã hóa và xác thực transport.

Muốn bỏ security, Activity có thể dùng trực tiếp callback và `sendMessage()` của `BluetoothConnector`; khi đó không còn kiểm tra identity CI-Deploy.

## 6. Luồng Host

1. Người dùng mở menu **Bluetooth Test**.
2. Ứng dụng kiểm tra quyền và trạng thái Bluetooth.
3. Người dùng chọn **Host Mode**.
4. Host không tự bật discovery; nút **Scan devices (Host)** cho phép scan thủ công lần đầu để xem danh sách.
5. Scan Host không đổi role và không connect thiết bị; `AcceptThread` vẫn listen chờ Client.
6. Khi Client kết nối, Host nhận `BluetoothSocket` từ `accept()`.
7. Connector đóng server socket, tạo `ConnectedThread` và chuyển state sang `CONNECTED`.
8. Security layer chạy handshake; chỉ khi `AUTHENTICATED` mới tự mở màn hình chat.

Host chỉ phục vụ một kết nối tại một thời điểm. Muốn nhận kết nối mới sau khi phiên hiện tại kết thúc, người dùng chọn lại **Host Mode**.

Host được chạy trong foreground service nên vẫn giữ trạng thái `LISTENING` khi Activity bị đóng/background. Chỉ nút **Stop Host** mới dừng service và disconnect; sau đó Host không tự listen lại.

BluetoothTest và Chat dùng chung một `BluetoothSecurityLayer`/`BluetoothConnector`. Test giữ listener điều phối phiên và Chat đăng ký observer riêng, nên sau mỗi reconnect Host vẫn mở Chat đúng như lần đầu.

Chat hỗ trợ nút **File**: chọn file qua Android document picker, gửi `FILE|START`, các chunk Base64 và `FILE|END`. Peer nhận sẽ ghép dữ liệu và lưu vào thư mục cache của app, đồng thời ghi đường dẫn vào chat log.

Nút **Camera** chụp ảnh thumbnail JPEG bằng camera intent và gửi như file ảnh. Khi nhận `.jpg/.jpeg/.png`, Chat hiển thị ảnh ở vùng preview và lưu bản gốc trong cache.

Vùng Chat dùng container cuộn chung cho text log và image item; ảnh tự co theo chiều rộng, giữ aspect ratio và giới hạn chiều cao để không phá layout.

Chat hiện dùng RecyclerView message list: text hiển thị bubble theo hướng gửi/nhận, còn ảnh hiển thị bằng image item riêng và tự co theo chiều rộng.

Các action file dùng icon File/Camera/Gallery; Gallery cho phép chọn media (ảnh/video). Việc đọc file, encode chunk và gửi chạy trên background executor để không block UI.

Message bubble được căn theo hướng gửi/nhận và hiển thị thời gian `HH:mm`. Host tự động gửi một reply ngẫu nhiên khi nhận text message từ Client để hỗ trợ kiểm thử hai chiều.

Message item có khoảng cách dọc 6dp và padding bubble 22dp/14dp. Image item và bubble được giới hạn 70% chiều rộng màn hình, ảnh giữ aspect ratio.

Ba icon media nằm trên một hàng riêng bên dưới hàng nhập message. Gallery dùng `ACTION_OPEN_DOCUMENT` với URI permission do Android cấp, nên không yêu cầu quyền storage thủ công; Camera yêu cầu runtime `CAMERA` permission trước khi mở.

Bluetooth Test không còn các nút chọn Host/Client/P2P hoặc Security; các thiết lập này được quản lý tập trung tại màn hình Settings trên action bar.

Enable Bluetooth được đưa lên action bar bằng icon. Device scan dùng một nút duy nhất cho cả Host và Client; Stop Host chỉ enable khi mode hiện tại là Host.

Nút Scan được đặt chung hàng với các control khác, kích thước gọn; khi bắt đầu scan, vị trí scroll của màn hình được giữ nguyên.

Nút **Stop Host** chỉ hiển thị trong Host mode; Client/P2P không hiển thị nút này.

BluetoothHostService được promote lên foreground trước khi khởi tạo listener, tương thích Android 11; manifest khai báo foreground service connected-device cho các Android mới hơn.

Trên Android 11 trở xuống, Bluetooth discovery yêu cầu `ACCESS_FINE_LOCATION` và Location Services phải bật. App kiểm tra cả hai; nếu Location đang tắt sẽ mở màn hình Location Settings thay vì chạy scan lỗi.

Trên Android 12 trở lên, app chỉ yêu cầu `BLUETOOTH_SCAN` và `BLUETOOTH_CONNECT`; không yêu cầu `BLUETOOTH_ADVERTISE` vì Host không bật discoverable.

Message history được lưu riêng theo Bluetooth address của peer, tối đa 200 message; khi mở lại Chat, text và image file còn tồn tại trong cache sẽ được nạp lại.

Bluetooth core đã được đóng gói thành module `:libs:bluetooth`; app khác có thể dependency module này để dùng connector, security layer và các local stores mà không cần lấy UI Bluetooth Test/Chat.

## 7. Luồng Client

1. Người dùng chọn **Client Mode**.
2. Danh sách thiết bị đã paired được hiển thị.
3. Người dùng có thể:
   - Chạm một thiết bị paired để kết nối ngay.
   - Nhấn **Scan for devices** để discovery thiết bị gần đó.
4. Nếu thiết bị chưa paired:
   - Chạm thiết bị để bắt đầu pairing.
   - Hoàn tất dialog pairing của Android.
   - Chạm lại thiết bị sau khi pairing hoàn thành.
5. Connector dừng discovery trước khi kết nối để tránh làm chậm RFCOMM.
6. `ConnectThread` gọi `socket.connect()`.
7. Khi thành công, connector tạo `ConnectedThread`, chuyển state sang `CONNECTED`; sau đó security handshake phải thành công mới mở màn hình chat.

Client cần chọn đúng thiết bị đang chạy CI-Deploy ở Host Mode. Một thiết bị chỉ bật Bluetooth hoặc discoverable nhưng không mở service `CIDeployBluetooth` sẽ không nhận được kết nối RFCOMM này.

Khi chọn hoặc tự khôi phục **Client Mode**, ứng dụng tự bắt đầu Bluetooth discovery và hiển thị danh sách thiết bị; người dùng chỉ cần chọn peer phù hợp để connect.

## 7. Gửi và nhận dữ liệu

### Gửi

`BluetoothChatActivity` gọi:

```kotlin
connector.sendMessage(message)
```

Connector:

1. Kiểm tra `ConnectedThread` có tồn tại.
2. Nối ký tự newline vào message.
3. Ghi bytes vào `OutputStream`.
4. Gọi `flush()`.
5. Phát callback `onMessageSent()`.

### Nhận

`ConnectedThread` liên tục gọi `readLine()` trên `InputStream`.

Khi có dữ liệu:

1. Connector phát `onMessageReceived(message)`.
2. Activity chuyển cập nhật về UI thread.
3. Màn chat thêm dòng `RECV` kèm timestamp.

Khi stream trả về EOF hoặc phát sinh `IOException`, connector giải phóng connection hiện tại và chuyển sang `DISCONNECTED`.

## 8. Callback từ connector

Giao diện nhận sự kiện qua `BluetoothConnector.Callback`:

| Callback | Mục đích |
| --- | --- |
| `onStateChanged` | Cập nhật status và điều hướng sang chat |
| `onDeviceFound` | Thêm thiết bị discovery vào danh sách |
| `onMessageReceived` | Hiển thị message nhận |
| `onMessageSent` | Hiển thị message đã gửi |
| `onError` | Hiển thị lỗi socket, permission hoặc discovery |
| `onDiscoveryFinished` | Khôi phục trạng thái nút Scan |

Callback từ socket thread không chạy trên main thread. Activity phải dùng `runOnUiThread` trước khi cập nhật View.

## 9. Quyền Android

Các quyền được khai báo trong `app/src/main/AndroidManifest.xml`.

### Android 12 trở lên

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

- `BLUETOOTH_SCAN`: discovery thiết bị.
- `BLUETOOTH_CONNECT`: đọc paired device, pair và mở socket.
- `BLUETOOTH_ADVERTISE`: đưa Host vào trạng thái discoverable.

Đây là runtime permissions và phải được người dùng cấp.

### Android 11 trở xuống

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Discovery Bluetooth Classic trên các phiên bản Android cũ yêu cầu quyền vị trí. Một số thiết bị còn yêu cầu bật Location trong Quick Settings để discovery trả về kết quả.

## 10. Hướng dẫn sử dụng

### Chuẩn bị

- Cài cùng phiên bản CI-Deploy lên hai thiết bị Android.
- Bật Bluetooth trên cả hai thiết bị.
- Đặt hai thiết bị trong phạm vi Bluetooth.
- Cấp đầy đủ quyền Bluetooth/Location khi ứng dụng yêu cầu.

### Trên thiết bị Host

1. Mở CI-Deploy.
2. Mở navigation drawer.
3. Chọn **Bluetooth Test**.
4. Nhấn **Host Mode**.
5. Chấp nhận yêu cầu cho phép thiết bị hiển thị với thiết bị khác.
6. Giữ màn hình chờ ở trạng thái `Host Listening`.

### Trên thiết bị Client

1. Mở màn hình **Bluetooth Test**.
2. Nhấn **Client Mode**.
3. Nếu Host đã nằm trong **Paired Devices**, chạm vào Host để kết nối.
4. Nếu chưa thấy Host, nhấn **Scan for devices**.
5. Chạm Host để pairing và xác nhận mã ghép đôi trên cả hai thiết bị.
6. Sau khi pairing hoàn tất, chạm lại Host để kết nối.

Khi kết nối thành công, cả hai màn hình tự chuyển sang Bluetooth Chat.

### Gửi message

1. Nhập nội dung vào ô cuối màn hình.
2. Nhấn **Send**.
3. Thiết bị gửi hiển thị log `SEND`.
4. Thiết bị nhận hiển thị log `RECV`.

Nút xóa chỉ xóa log đang hiển thị, không xóa kết nối hoặc dữ liệu trên thiết bị đầu xa.

## 11. Xử lý sự cố

### Không tìm thấy Host

- Kiểm tra Host đã chọn **Host Mode**.
- Chấp nhận dialog discoverable trên Host.
- Đưa hai thiết bị lại gần nhau.
- Tắt/bật Bluetooth và scan lại.
- Trên Android 11 trở xuống, kiểm tra Location đang bật.
- Kiểm tra ứng dụng có đủ quyền Bluetooth/Location.

### Pairing thành công nhưng không kết nối

- Chạm lại thiết bị trong danh sách sau khi pairing hoàn tất.
- Đảm bảo thiết bị được chọn đang chạy CI-Deploy ở Host Mode.
- Xóa pairing trong Android Settings, pair lại và thử kết nối.
- Chọn lại Host Mode để mở một server socket mới.

### Hiển thị `Connection failed`

Các nguyên nhân thường gặp:

- Host chưa ở state `LISTENING`.
- Host đã có một Client khác kết nối.
- UUID/service của hai phía không giống nhau.
- Thiết bị đầu xa ra khỏi phạm vi hoặc tắt Bluetooth.
- Pairing chưa hoàn thành.

### Chat tự đóng

Màn chat tự đóng khi input stream bị EOF hoặc socket phát sinh lỗi. Kiểm tra thiết bị đầu xa có thoát chat, tắt Bluetooth hoặc mất kết nối hay không.

## 12. Giới hạn hiện tại

- Chỉ hỗ trợ một Client trên mỗi Host.
- Chỉ gửi message text theo từng dòng.
- Không có reconnect tự động.
- Host discoverable tối đa 300 giây cho mỗi lần yêu cầu.
- Chưa có ACK, retry, checksum hoặc mã hóa ở tầng ứng dụng.
- Log chat chỉ tồn tại trên màn hình, không được lưu xuống file/database.
- Connector dùng singleton trong process, chưa được đặt trong foreground service.
- Chưa hỗ trợ BLE, truyền file hoặc dữ liệu nhị phân.

## 13. Hướng mở rộng

Nếu sử dụng Bluetooth cho dữ liệu nghiệp vụ thay vì chỉ test, nên cân nhắc:

- Tách `BluetoothConnector` thành service có lifecycle độc lập với Activity.
- Dùng `StateFlow`/`SharedFlow` thay cho callback mutable.
- Xác định charset UTF-8 tường minh.
- Thiết kế frame gồm version, message type, payload length và checksum.
- Bổ sung handshake để xác nhận đúng ứng dụng/phiên bản.
- Bổ sung ACK, timeout và reconnect có giới hạn.
- Thêm queue ghi dữ liệu để nhiều lần Send không tranh chấp `OutputStream`.
- Thêm test cho state machine và protocol parser.
- Không gửi token, mật khẩu hoặc dữ liệu nhạy cảm nếu chưa có mã hóa/xác thực ở tầng ứng dụng.

## 14. Kiểm tra thủ công đề xuất

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Từ chối runtime permission | Hiển thị thông báo permission denied, không crash |
| Bluetooth đang tắt | Hiển thị dialog hệ thống yêu cầu bật |
| Chọn Host | State chuyển sang `LISTENING` |
| Client scan | Danh sách nhận thiết bị và nút Scan được bật lại khi hoàn tất |
| Client chọn thiết bị chưa pair | Android bắt đầu pairing, chưa kết nối socket ngay |
| Client chọn thiết bị đã pair | State chuyển `CONNECTING` rồi `CONNECTED` |
| Kết nối thành công | Cả hai thiết bị mở màn hình chat và hiển thị đúng role |
| Gửi text hai chiều | Bên gửi có `SEND`, bên nhận có `RECV` đúng nội dung |
| Một bên disconnect | Bên còn lại chuyển `DISCONNECTED` và đóng chat |
| Đổi Host sang Client khi đang chờ | Server socket cũ được đóng, UI chuyển sang Client |
