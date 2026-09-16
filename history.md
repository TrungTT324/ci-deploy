# Lịch sử Yêu cầu (Prompt History) - CI-Deploy Project

Dưới đây là danh sách các yêu cầu (prompts) bạn đã nhập trong phiên làm việc này, được sắp xếp theo trình tự thời gian:

1. **Khởi tạo dự án Android CI-Deploy (Flutter ban đầu)**
   > Tạo app android name CI-Deploy,package name: hdisoft.app.cideploy
   > - Màn hình chính là 1 webview, hiển thị url: http://172.16.100.26:8080
   > - app icon với màu nền là màu blue nhạt,bo tròn, có dòng chũ CI lớn và deploy nhỏ nằm tụt xuống 1/2 so với CI,màu chữ trắng, chữ CI và deploy với tỉ lệ khoảng 7/3.
   > - build apk name: CI-Deploy_debug.apk và copy vào folder :
   > /opt/homebrew/var/www/apps/ci-deploy
   > - cập nhật file: /opt/homebrew/var/www/index.html thêm vào app CI-Deploy

2. **Yêu cầu tiếp tục hoàn thành**
   > chưa làm xong

3. **Tạo GitHub Repository**
   > sử dụng github CLI cài sẳn, tạo repo pulbick cho dự án này

4. **Commit & Push lên GitHub**
   > commit lên luôn

5. **Thiết lập GitHub Action CI/CD**
   > thêm github action build debug, và tao page release chứa apk

6. **Tích hợp cơ chế cập nhật OTA tự động (Auto-Update)**
   > Tạo file ci-deploy-version.json trong folder: /opt/homebrew/var/www/apps/ci-deploy
   > -Nội dung mô tả các file .apk
   > - app name,icon, version, buildno(vd:202609070315 format yyyymmddhhmm)
   > - buildNote: Mô tả ngắn gọn bản apk(vd: fix bug #222 hay thêm tính năng...)
   > - url: url dẫn tới apk để download
   > - app mỗi lần start sẽ gọi http://172.16.100.26:8080/apps/ci-deploy/ci-deploy-version.json, dựa trên buildNo, nếu buildNo mới hơn hiện tại hiển thị 1 dialog New Upgrade, nội dung là buildNote, 2 button Yes,No. Nhấn Yes sẽ download apk từ url
   > - tạo 1 màn hình hiển thị trạng thái download, có nút cancel sẻ huỷ download, khi download xong sẽ cho phép nhấn nút install để cài file apk mới.

7. **Build & Copy APK bản cập nhật**
   > - build apk name: CI-Deploy_debug.apk và copy vào folder :
   > /opt/homebrew/var/www/apps/ci-deploy
   > - cập nhật file: /opt/homebrew/var/www/index.html phần liên quan CI-Deploy

8. **Chuyển đổi hoàn toàn dự án sang Android Native Kotlin (Loại bỏ Flutter)**
   > - chuyển toàn bộ tính năng app hiện tại sang native android kotlin
   > - build apk name: CI-Deploy_debug.apk và copy vào folder :
   > /opt/homebrew/var/www/apps/ci-deploy
   > - cập nhật file: /opt/homebrew/var/www/index.html phần liên quan CI-Deploy
   > - sau đó xoá các file liên quan flutter, clean up.

9. **Tạo tài liệu ghi nhớ tính năng dự án**
   > tạo các file cần thiết để nhớ được tính năng và yêu cầu của app, lần sau mở lại không phải mô tả nhiều, cập nhật file readme.md

10. **Làm rõ về tệp cấu hình ngữ cảnh cho AI**
    > ý là các file .md thiết lập cho gemini hiểu đc context của dự án

11. **Sửa lỗi tải xuống (Download) của WebView**
    > fix lỗi webview không download file được

12. **Sửa lỗi lặp vô hạn Dialog cập nhật sau khi cài bản mới**
    > fix lỗi sau khi cài bản mới vẫn hiển thị dialog yêu cầu cập nhật

13. **Phát triển tính năng dò tìm máy chủ tự động (Host Discovery)**
    > thêm tính năng discovery host
    > - Khi app khởi động lần đầu sẽ tìm host trong local LAN bằng địa chỉ IP trên cổng 8080, giới hạn scan ở octet cuối.
    > - Khi tìm được host sẽ reload lại webview và lưu vào local
    > - Lần kế tiếp khởi động nếu host được lưu local có work hay không
    > nếu work thì ko cần scan nữa, ko work thì scan lại để cập nhật lại host.
    > - Việc kiểm tra host dựa trên request file json cho chính xác, không dựa vào trạng thái của webview.

14. **Sửa lỗi không tìm thấy host trong mạng LAN (No Host Found)**
    > fix lỗi no host found on port 8080

15. **Thêm cấu hình WebView urlPath động**
    > Thêm field urlPath = "ci-deploy"
    > Thay đổi url thành [host:8080]/urlPath

16. **Tự động kiểm tra phiên bản mới khi ứng dụng Resume**
    > sửa lại phần kiểm tra file json, sẽ được gọi môĩ khi app resume

17. **Lưu lịch sử yêu cầu**
    > tạo file history.md ghi lại các prompt nãy giờ đã nhập

18. **Sửa đổi hằng số urlPath cục bộ**
    > fix lại urlPath là constant không phải từ json

19. **Tạo nhánh dev trên GitHub**
    > tạo nhánh dev github và push code

20. **Chuyển sang nhánh dev và sửa lỗi WebView không load được trang**
    > chuyển sang làm việc trên nhánh dev, fix lỗi webview không load được

21. **Thêm tùy chọn stream logcat bằng socket TCP hoặc UDP**
    > hiện tại app sử dụng websocket để stream locat, thêm 1 tuỳ chọn có thể stream bằng socket , TCP hoặc UDP, Tạo class thực chức năng này trong module app

22. **Tạo script build.sh và tự động cập nhật ci-deploy-version.json**
    > tạo script build.sh, build debug apk, rename CI-deploy_debug.apk, copy vào folder: /opt/homebrew/var/www/ci-deploy , sau đo cập nhật lại file : /opt/homebrew/var/www/ci-deploy/ci-deploy-version.json các giá trị thích hợp.

23. **Tạo Android Emulator Rooted**
    > tạo 1 emulator rooted với AVD mới nhất hiện có

24. **Tích hợp cài đặt tự động (silent root install) trên thiết bị rooted**
    > check lại app, khi chạy trên device rooted, bảo đảm install apk được diễn ra tự động

25. **Sửa lỗi các phím cứng (back, home) trên emulator không hoạt động**
    > các button back,home,... trên emulator root mới tạo không work

26. **Tạo tài liệu hướng dẫn tạo và cấu hình máy ảo rooted**
    > tạo file device_rooted.md ghi lại các hướng dẫn tạo ra rooted device để hoạt động chính xác, thêm các lỗi nãy giờ không nhấn được các button back,home để có hướng dẫn đầy đủ.

27. **Định dạng hiển thị buildNo trên ActionBar**
    > thêm action bar text hiển thị buildNo cho có ý nghĩa, parse và format lại ngày giờ, bỏ năm và tháng

28. **Tích hợp nút Info, theo dõi ngày cài đặt và chế độ kiểm tra version định kỳ**
    > thêm button info(icon) ở bên phải, nhấn vào sẽ hiển thị dialog, nội dung chi tiết: Version hiện tại, ngày cài đặt version,thêm tuỳ chọn chế độ kiểm tra version: default như hiện tại,interval 10s,30s,120 seconds phát triển tính năng interval kiểm tra version này

29. **Tự động hóa hoàn toàn quy trình tải và cài đặt cập nhật trên thiết bị Rooted**
    > khi thiết bị rooted (sử dụng kiểm tra Build.TAGS và lệnh "which su" để vượt qua giới hạn file check của SELinux trên Android 7.0+), sau khi có version mới sẽ không hiển thị dialog confirm, quá trình cài đặt diễn ra tự động, sử dụng toast để thông báo, sau khi cài đặt thành công sẽ tự động mở lại app.

30. **Hủy kiểm tra phiên bản trong quá trình tải xuống và cài đặt**
    > và trong lúc download và install chưa thành công, cancel việc kiểm tra version

31. **Đảm bảo chỉ có duy nhất một tiến trình tải xuống hoạt động**
    > download bị loop quá nhiều lần bảo đảm rằng khi download chỉ 1 được hoạt động

32. **Bổ sung tài liệu sửa lỗi Permission denied su khi gọi root từ app**
    > java.io.IOException: Cannot run program "su": error=13, Permission denied

33. **Ngừng hoàn toàn việc kiểm tra phiên bản mới khi quá trình nâng cấp bắt đầu**
    > ngưng checkUpdate khi có bất kỳ lỗi nào xảy ra, hay khi chưa cài đặt thành công

34. **Chỉ hiển thị thông báo Toast duy nhất khi tải xuống thành công**
    > chỉ show toast 1 lần lúc download thành công

35. **Tối ưu hóa quy trình cài đặt Root thông qua luồng byte Standard Input**
    > fix lại phần tự cài đặt khi rooted device, hiện tại không cài được (truyền tệp qua stdin của su shell để bỏ qua giới hạn đọc phân vùng riêng tư của SELinux)

36. **Kích hoạt quyền ghi phân vùng hệ thống và phân quyền 06755 cho su trên emulator**
    > fix lại phần tự cài đặt khi rooted device, hiện tại không cài được (thiết lập chmod 06755 cho su sau khi tắt dm-verity)

37. **Kiểm tra thiết bị kết nối có root không**
    > kiểm tra device đang kết nối có root ko

38. **Tìm hiểu khả năng root thiết bị Masstel Tab10Pro**
    > có thể root device này được không

39. **Tạo file keystore và release APK cho module app**
    > tạo file keystore với pass dùng chung: Tt35!2233 , các thiết lập khác mặc định theo tên dự án, và tạo release apk cho module app.

40. **Phân tích UI/UX và đề xuất cải tiến**
    > review lại tính năng, phân tích sâu xem vè ui,ux có điểm nào cần chinh sửa lại cho đẹp và hợp lý. tạo file improvement để ghi lại kết quả phân tích.

41. **Cải tiến UI/UX và Build Release APK**
    > hãy cải tiến lại theo những gì đã phân tích và build apk release khi hoàn tất.

42. **Khởi động lại thiết bị Android kết nối USB**
    > reboot lại thiết bị android đang kết nối usb với pc

43. **Tự động kích hoạt Wi-Fi và đợi có IP trước khi quét Host**
    > cập nhật lại flow start app, đâu tiên sẽ kiêm tra wifi có bật chưa, tự bật và đợi cho đến khi bật và có địa chỉ ip, sau đó mới tiến hành scan host.

44. **Cập nhật menu bên trái (Navigation Drawer) có thể cuộn**
    > cập nhật lại menu bên trái có thể scroll

45. **Tính năng kết nối Bluetooth giữa 2 thiết bị và chat kiểm thử**
    > tiếp tục tạo class phục vụ kết nối 2 thiết bị bằng bluetooth, tạo 1 screen để test, có đầy đủ tính năng, yêu cầu bật bluetooth nếu chưa bật, discovery và pair với nhau, gửi nhận text message trong module app

46. **Sửa lỗi crash NullPointerException ở chế độ Landscape**
    > app chạy crash

47. **Hiển thị Panel báo lỗi và nút Retry dưới Toolbar thay vì Dialog**
    > khi host not found, hãy hiển thị 1 panel thông bào dứoi action bar, có nut retry, bỏ hiển thị dialog

48. **Ẩn Dialog tiến trình quét Host và chạy thử nghiệm Debug trên thiết bị**
    > lúc hiển thị scanning cũng không cần đialog, chạy lại trên thiết bị sau khi sửa xong, ko cân build release

49. **Bổ sung kiểm tra địa chỉ IP trước khi thực hiện quét Host**
    > check phần scan host, bảo đảm là thiết bị có địa chỉ ip rôì mơí scan

50. **Hiển thị ProgressBar quét Host trực tiếp trong Panel thông báo**
    > lúc scan nên hiển thị progress tron panel luôn

51. **Hiển thị URL kết nối thành công trên Panel và giữ trạng thái cố định kèm nút Retry**
    > ý là panel luôn hiện thị trạng thái của host và retry nếu cần, thanhf công thì hiển thị url của host

52. **Hiển thị IP thiết bị cục bộ trên Panel thông báo trong lúc quét**
    > lúc scanning hiển thị thêm ip của thiết bị

53. **Tự động quét lại tối đa 3 lần với khoảng cách 5 giây khi quét lỗi**
    > khi thất bại tự động retry 3 lần, với khoảng cách thời gian 5giây

54. **Ẩn nút Retry khi kết nối Host thành công**
    > khi thành công thì ẩn retry đi

55. **Thiết lập Panel thông báo hiển thị theo mặc định trên layout XML**
    > sao có lúc màn hình hiển thị blank page và không có panel thông báo gì cả, bảo đảm panel luôn hiển thị tuỳ vào trạng thái của host.

56. **Tối ưu hóa tốc độ và khả năng quét LAN bằng TCP socket pre-check trên cổng 8080**
    > scan host chạy trên device android ko tìm thâý host (do cơ chế Semaphore(20) và timeout 5s cũ bị giới hạn luồng khi quét trên thiết bị mới)

57. **Tách riêng màn hình trò chuyện Bluetooth Chat Screen**
    > bluethooth khi 2 thiết bị kết nối với nhau sẽ qua 1 màn hình khác để gửi data,message giữa 2 device với nhau

58. **Khắc phục lỗi ẩn Panel thông báo kết nối khi khôi phục cache IP**
    > lại không thấy pannel (do trạng thái Idle của service khi có cache IP sẽ ẩn panel, hiện tại đã bổ sung cập nhật trạng thái kết nối trực tiếp trong observer của viewModel)

59. **Luôn giữ Panel thông báo hiển thị trên màn hình ở mọi trạng thái và biên dịch bản Release**
    > cập nhật luôn hiển thị pannel và build lên thiết bị, ko build debug nữa

60. **Phân tách rõ hai chế độ Host và Client cho Bluetooth**
    > bluetooth sẽ có chế độ host và client, khi client kết nối thành công, cả 2 sẽ tự chuyển qua màn hính message

61. **Hoàn thiện luồng Bluetooth Host/Client trong module app**
    > bổ sung trạng thái mode rõ ràng, giao diện chọn Host/Client, Host chờ kết nối, Client quét/chọn thiết bị, tự mở màn hình chat và hiển thị vai trò cùng thiết bị kết nối

62. **Tạo tài liệu kỹ thuật và hướng dẫn sử dụng Bluetooth Host/Client**
    > tạo file docs/bluethooth.md mô tả kiến trúc, RFCOMM/SPP, state, flow Host/Client, quyền Android, cách sử dụng và xử lý sự cố

63. **Ổn định flow App Update và giữ OTA debug artifact**
    > chuẩn hóa debug signing cùng project key, xác minh size/SHA/package/signature, download có file tạm và cancellation an toàn, sửa kết quả root install, đồng nhất metadata deploy và tạo docs/appupdate.md

64. **Chỉ dùng debug APK trong build.sh cho OTA**
    > build.sh chỉ chạy assembleDebug, publish CI-Deploy_debug.apk và tạo version JSON từ đúng artifact debug

65. **Thêm security handshake độc lập cho Bluetooth CI-Deploy**
    > bổ sung BluetoothSecurityLayer với challenge-response xác minh app/role, reject thiết bị không hợp lệ hoặc timeout, chặn message trước authenticate và cập nhật tài liệu bluethooth

66. **Bổ sung event log cho Bluetooth Test**
    > thêm UI log có timestamp và ghi nhận action, trạng thái connector, discovery, authentication và lỗi để hỗ trợ chẩn đoán kết nối

67. **Sửa proof Bluetooth security và thêm tùy chọn bật/tắt**
    > đồng nhất thứ tự nonce khi tạo SHA-256 proof, thêm switch Security handshake trên Bluetooth Test để debug kết nối không qua xác thực

68. **Chuẩn hóa proof theo thứ tự nonce deterministic**
    > sửa lỗi local/remote bị đảo giữa hai peer bằng cách sort hai nonce trước khi hash

69. **Lưu peer Bluetooth và bổ sung P2P mode**
    > lưu address/name sau authentication, thêm nút P2P tự chọn Host/Client deterministic và tự reconnect peer đã biết

70. **P2P tự kết nối thiết bị đã pair**
    > P2P lấy danh sách paired devices, ưu tiên peer đã lưu và tự chọn vai trò/kết nối không cần chọn thủ công

71. **P2P bỏ qua peer lỗi và thử peer kế tiếp**
    > thêm retry tuần tự cho connect/auth/disconnect failure, chống retry trùng khi reject kéo theo disconnect

72. **Ghi log chi tiết từng lần thử P2P**
    > sửa double-trigger làm bỏ qua peer, thêm chỉ số TRY i/n, thông tin thiết bị, role và lý do SKIP

73. **Lưu và tự khởi động Bluetooth mode**
    > lưu mode Host/Client/P2P vào local storage và tự restore mode cuối cùng khi mở Bluetooth Test

74. **Host chỉ listen, không tự discovery**
    > bỏ yêu cầu discoverable và Bluetooth discovery khi start Host; Host chỉ mở RFCOMM server chờ Client

75. **Thêm scan thủ công cho Host**
    > bổ sung nút Scan devices (Host), hiển thị danh sách thiết bị nhưng giữ Host listen-only và không cho connect từ danh sách

76. **Client tự scan khi khởi động mode**
    > tự bắt đầu discovery khi chọn hoặc restore Client mode để hiển thị danh sách peer ngay lập tức

77. **Tương thích proof security giữa các phiên bản APK**
    > chấp nhận tạm thời các thứ tự nonce legacy và bổ sung chi tiết nonce mismatch vào log authentication

78. **Bổ sung fingerprint proof trong log auth**
    > ghi prefix received/calculated proof để phân biệt lỗi nonce với lỗi khác secret hoặc khác phiên bản APK

79. **Bật Simple Auth cho Bluetooth Debug**
    > thêm handshake hai chiều bằng message CI_DEPLOY, giữ nguyên method challenge-response cũ để dùng sau

80. **Lưu lịch sử kết nối Bluetooth gần đây**
    > lưu name/address/mode/thời gian auth thành công, tối đa 20 bản ghi và deduplicate theo address

81. **Ưu tiên Recent trong danh sách Bluetooth Test**
    > sắp xếp paired devices theo thời gian recent trước, các thiết bị paired khác hiển thị phía sau

82. **Tách group Recent Connections trên UI**
    > thêm RecyclerView riêng cho recent devices và loại thiết bị recent khỏi group paired để tránh trùng

83. **Đưa Bluetooth Host vào foreground service**
    > Host service giữ RFCOMM listener ở trạng thái sẵn sàng khi Activity background, thêm Stop Host để user dừng chủ động

84. **Host tự listen lại sau khi Client disconnect**
    > security layer của Host service tự khởi động lại RFCOMM listener sau disconnect, nhưng không restart khi user Stop Host

85. **Luôn hiển thị group Recent Connections**
    > thêm empty state `No recent connections` khi chưa có recent hoặc peer không còn paired

86. **Khôi phục danh sách device khi back từ Chat**
    > reload Recent/Paired trong onResume và giữ card danh sách luôn hiển thị khi connector tạm thời ở NONE/IDLE

87. **Ổn định reconnect Host sau phiên Chat đầu tiên**
    > delay Host listener restart để Chat đóng và BluetoothTest nhận lại callback, tránh connection lần 2 rơi vào listener cũ

88. **Chia sẻ listener giữa BluetoothTest và Chat**
    > hỗ trợ observer listeners trên security layer, giữ Test listener điều phối và Chat listener chỉ quan sát message/state để reconnect lần 2 mở Chat đúng

89. **Thêm gửi file trong Bluetooth Chat**
    > thêm nút chọn file, truyền metadata/chunk Base64 qua security layer và lưu file nhận vào cache

90. **Chụp ảnh camera và preview ảnh nhận**
    > thêm nút Camera, gửi ảnh JPEG qua file protocol và hiển thị ảnh nhận trực tiếp trong Chat

91. **Ổn định mở Camera trong Bluetooth Chat**
    > kiểm tra runtime CAMERA permission, kiểm tra camera activity và bắt exception khi mở camera để tránh crash

92. **Cải thiện image item trong Chat**
    > đưa image preview vào message container cuộn chung, giữ tỉ lệ và giới hạn kích thước hiển thị

93. **Chuyển Bluetooth Chat sang RecyclerView messages**
    > thêm adapter item text/image, bubble cho message và image item riêng trong danh sách chat

94. **Lưu message history theo cặp device**
    > lưu text/image path theo peer Bluetooth address, giới hạn 200 message và restore khi mở Chat

95. **Thêm Bluetooth Settings screen**
    > gom mode Host/Client/P2P, Security và retry interval vào màn hình Settings mở từ action bar, lưu local storage

96. **Loại bỏ nút mode/security khỏi Bluetooth Test**
    > dọn UI màn hình Test, chuyển toàn bộ thiết lập sang Bluetooth Settings; giữ lại scan và Stop Host

97. **Tinh gọn Bluetooth Test action bar và scan**
    > chuyển Enable Bluetooth lên icon action bar, dùng một nút scan chung và ẩn Stop Host ngoài Host mode

98. **Thu gọn hàng Scan và giữ vị trí scroll**
    > đưa Scan vào hàng control, loại nút scan lớn trong card và khôi phục scrollY sau khi bắt đầu discovery

99. **Ẩn Stop Host ngoài Host mode**
    > Stop Host chỉ hiển thị khi mode hiện tại là Host, ẩn hoàn toàn ở Client/P2P

100. **Fix crash Client khi nhận ảnh/file**
    > chuyển update RecyclerView/ImageView về main thread và bắt lỗi Base64 chunk không hợp lệ

101. **Icon actions và background file transfer**
    > thay File/Camera bằng icon, thêm Gallery chọn ảnh/video và chạy send file trên background executor

102. **Tách hàng action media và gallery permission flow**
    > đưa File/Camera/Gallery xuống hàng riêng dưới message input; dùng document picker cấp URI permission đúng theo file được chọn

103. **Cải thiện Chat bubble và Host auto reply**
    > bubble gửi/nhận căn trái/phải có thời gian hiển thị, Host tự reply ngẫu nhiên khi nhận message từ Client

104. **Tinh chỉnh spacing và kích thước image Chat**
    > thêm margin giữa message, padding bubble hợp lý và giới hạn image/bubble ở 70% chiều rộng thiết bị

105. **Fix Bluetooth service lifecycle trên Android 11**
    > promote foreground service trước khi khởi tạo Bluetooth listener để tránh service bị crash/killed trên Android 11

106. **Restart Bluetooth theo Settings mới**
    > sau khi Save Settings, dừng session/service hiện tại và tự khởi động lại mode/security mới ngay trên Bluetooth Test

107. **Bổ sung kiểm tra quyền Bluetooth Android 11**
    > yêu cầu ACCESS_FINE_LOCATION và Location Services bật trước discovery, mở Location Settings khi bị tắt

108. **Đồng nhất permission Android 11+**
    > Android 11 dùng Fine Location; Android 12+ chỉ yêu cầu Scan/Connect, bỏ Advertise không cần thiết cho passive Host

109. **Tách Bluetooth core thành library module**
    > thêm `:libs:bluetooth`, export connector/security/data stores độc lập và để app UI dùng lại qua dependency

110. **Fix Bluetooth crash Android 8**
    > bảo vệ lời gọi LocationManager.isLocationEnabled bằng API 28 guard; Android 8 chỉ kiểm tra Fine Location permission

111. **Cải tiến giao diện màn hình Bluetooth Chat chuyên nghiệp**
    > Cập nhật lại UI màn hình BluetoothChatActivity cho chuyên nghiệp, thiết kế bong bóng chat (chat bubbles) bo tròn góc không đối xứng, phân biệt màu sắc gửi/nhận rõ ràng, thêm chế độ chuyển đổi Tab Console Logs để theo dõi log debug bằng icon trên Toolbar, tái cấu trúc bảng nhập liệu pill-style cùng các icon đính kèm file/camera/gallery và kiểm soát luồng hoạt động ổn định.
112. **Sửa lỗi chọn và gửi hình ảnh/video từ Gallery qua Bluetooth**
    > Thay đổi phương thức chọn tệp của nút Gallery thành Intent.ACTION_GET_CONTENT kết hợp với filter MIME type của cả ảnh và video. Khắc phục lỗi phân giải đường dẫn gốc của Uri dạng content:// bằng cách sao chép luồng byte vào thư mục bộ nhớ tạm (cache) của ứng dụng trước khi gửi đi, thêm tính năng trích xuất thumbnail động bất đồng bộ cho video, cấu hình FileProvider chia sẻ tệp an toàn và hỗ trợ click mở trực tiếp tệp đa phương tiện bằng ứng dụng hệ thống mặc định.

113. **Tối ưu hóa luồng truyền tải và loại bỏ hiện tượng treo UI**
    > Thay thế hoàn toàn cơ chế chuyển đổi ByteArray thành List<Byte> đóng gói (boxing) vốn gây nghẽn rác và treo CPU/UI khi gửi tệp dung lượng lớn. Sử dụng cơ chế truyền tải stream trực tiếp kết hợp luồng ghi đệm ByteArray thô 600-byte và tái sử dụng bộ ExecutorService nền cho việc trích xuất Thumbnail video trong Adapter, giúp giao diện hoạt động mượt mà.

114. **Khắc phục lỗi treo luồng UI và sửa lỗi gửi ảnh chụp Camera**
    > Loại bỏ hành động ghi log console đối với các tin nhắn chunk FILE|CHUNK| trong callback onMessageSent nhằm tránh đẩy hàng ngàn tác vụ vẽ màn hình đồng thời gây đơ giao diện chính. Sửa lỗi ContentResolver không đọc được URI dạng file:// của ảnh chụp từ Camera bằng cách mở luồng nhập srcFile.inputStream() trực tiếp thay vì qua content provider.

115. **Reload WebView khi MainActivity ở trạng thái Resume**
    > Cập nhật lại phương thức onResume của MainActivity để tự động reload lại WebView khi ứng dụng được kích hoạt lại (Resume). Nếu WebView chưa hiển thị URL nào thì tiến hành load URL máy chủ từ đầu, ngược lại gọi phương thức webView.reload() để tải lại nội dung mới nhất.

116. **Tạo mới module ứng dụng QA Automation (appQa)**
    > Tạo mới module ứng dụng `appQa` tích hợp:
    > - Lớp `QaAccessibilityService` để tự động giả lập cử chỉ nhấn (clicks), vuốt (swipes) và nhập liệu văn bản (text injection) vào các ứng dụng đang hiển thị trên màn hình.
    > - Lớp `QaAutomationService` chạy dưới dạng Foreground Service (với quyền `mediaProjection`) hiển thị bảng điều khiển nổi (Floating Control Overlay) hỗ trợ chụp màn hình nhanh (`ScreenCaptureHelper`), quay phim màn hình (`ScreenRecordHelper`) xuất dữ liệu trực tiếp vào Gallery hệ thống thông qua `MediaSaveHelper`.
    > - Nút chạy kịch bản tự động (Demo Automation Script) tự động ẩn overlay, giả lập click trung tâm màn hình, nhập văn bản mẫu, nhấn nút submit, chụp màn hình kết quả và hiển thị lại overlay.
    > - Màn hình `MainActivity` cung cấp bảng giám sát và kích hoạt trực tiếp các quyền Accessibility, Overlay và Notification.

117. **Tạo tài liệu hướng dẫn kỹ thuật cho module appQa**
    > Tạo tệp tin `README.md` bên trong thư mục `appQa` mô tả chi tiết các tính năng chính, kiến trúc kỹ thuật của hệ thống trợ năng/quay chụp màn hình, danh sách quyền cần xin và hướng dẫn quy trình build/chạy thử nghiệm.

118. **Tích hợp module webserver và xây dựng trang báo cáo từ xa**
    > Tích hợp thư viện `:libs:webserver` vào module `appQa`:
    > - Bổ sung các API REST `/api/qa/files` để liệt kê tệp báo cáo cục bộ và đường dẫn streaming `/qa/reports/{filename}` trong `SimpleHttpServer.kt`.
    > - Cập nhật `MediaSaveHelper` và `QaAutomationService` để tự động lưu thêm bản sao của ảnh chụp và video quay màn hình vào thư mục riêng của app (`context.getExternalFilesDir("reports")`).
    > - Thiết kế trang web xem báo cáo `/reports` (`reports.html` đặt trong Assets) hiển thị lưới dạng Dark Mode giúp máy tính kết nối từ xa xem trước ảnh, xem trực tiếp video và tải file.
    > - Cập nhật `MainActivity` tự động dò tìm IP cục bộ và hiển thị đường dẫn truy cập Server đầy đủ khi dịch vụ được kích hoạt.

119. **Bổ sung thẻ trạng thái Web Server riêng biệt trên màn hình chính**
    > Tách biệt thông tin máy chủ báo cáo ra một thẻ riêng biệt (Card 4) trên giao diện `MainActivity` và `activity_main.xml`. Thẻ này hiển thị trạng thái hoạt động trực tuyến/ngoại tuyến (ONLINE/OFFLINE) cùng địa chỉ IP chi tiết để truy cập từ trình duyệt máy tính.

120. **Tích hợp các nút điều khiển Start/Stop cho Web Server**
    > Bổ sung các nút bấm điều khiển độc lập cho Web Server (Start Server / Stop Server) trong Card 4. Cập nhật `MainActivity` quản lý khởi chạy/dừng dịch vụ `HttpWebServerService` thủ công và tự động quét trạng thái hoạt động thực tế của tiến trình service thông qua `ActivityManager`.

121. **Tự động kích hoạt Web Server khi ứng dụng khởi chạy**
    > Cấu hình `MainActivity` tự động gọi lệnh khởi động `HttpWebServerService` ngay trong phương thức `onCreate()` khi bắt đầu mở ứng dụng. Trạng thái hoạt động trực tuyến (ONLINE) và địa chỉ IP truy cập tương ứng được hiển thị ngay lập tức để người dùng có thể kết nối ngay mà không cần thao tác thủ công.

122. **Nổi bật đường dẫn URL truy cập Web Server trên giao diện chính**
    > Thêm một `TextView` chuyên biệt `url_webserver` trong Card 4 hiển thị trực tiếp chuỗi URL (Ví dụ: `URL: http://192.168.1.50:8085/reports` khi ONLINE hoặc `URL: http://<device_ip>:8085/reports` khi OFFLINE). Dòng chữ URL được tô màu nhấn (Green/Secondary) và định dạng đậm để người dùng dễ nhìn thấy và sử dụng.

123. **Sửa lỗi phân giải IP của thiết bị (Device Wi-Fi IP)**
    > Thay thế phương thức lấy IP thô bằng việc sử dụng lớp tiện ích chuẩn của dự án `NetworkUtils.getLocalIpAddress(this)` từ `:libs:core`. Giúp ứng dụng lọc chính xác địa chỉ IP của card mạng Wi-Fi thực tế (`wlan0`/`eth0`) thay vì các địa chỉ nội bộ/bridge của emulator/VPN, đảm bảo người dùng có thể truy cập Web Server bình thường.

124. **Khai báo HttpWebServerService trong manifest và tối ưu hóa việc kiểm tra trạng thái chạy**
    > - Bổ sung khai báo dịch vụ `hdisoft.app.webserver.HttpWebServerService` vào file manifest `appQa/src/main/AndroidManifest.xml` (thiếu khai báo này khiến hệ thống Android từ chối chạy service).
    > - Thêm thuộc tính static `isRunning` và companion object vào `HttpWebServerService` để theo dõi chính xác vòng đời hoạt động của Server, thay thế việc sử dụng API hệ thống `ActivityManager.getRunningServices` đã bị hạn chế trên Android 10+.

125. **Chuyển đổi Web Server chạy ngầm dạng Foreground Service**
    > Nâng cấp `HttpWebServerService` thành dịch vụ nổi bật (Foreground Service) trên Android 8.0+ để cho phép máy chủ web chạy dưới nền vô thời hạn mà không bị hệ điều hành tắt:
    > - Đăng ký quyền `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` có kiểu dữ liệu là `dataSync`.
    > - Triển khai tạo Notification Channel và hiển thị thông báo trạng thái hoạt động trực tiếp khi khởi tạo service.
    > - Cập nhật cách thức kích hoạt server trong `MainActivity` và `QaAutomationService` thông qua `ContextCompat.startForegroundService()`.

126. **Hỗ trợ ghi đè trang chủ index.html của Web Server từ phía App tích hợp**
    > - Thiết lập cơ chế kiểm tra sự tồn tại của tệp tài nguyên `app_index.html` trong `SimpleHttpServer.kt` thông qua AssetManager. Nếu app tích hợp khai báo file này, server sẽ tự động serve nó tại đường dẫn gốc `/` thay cho tệp `index.html` mặc định.
    > - Tạo mới file giao diện trang chủ `app_index.html` trong assets của module `appQa` chứa cấu trúc thanh điều hướng bên trái (left bar) và các liên kết nhanh để xem reports, logcat.

127. **Tái cấu trúc trang chủ index.html thành ứng dụng đơn trang (SPA)**
    > Cập nhật lại giao diện `app_index.html` của module `appQa`:
    > - Bổ sung một container `<iframe>` để nạp các trang con (`/reports`, `/logcat`) vào vùng nội dung chính bên phải.
    > - Viết thêm kịch bản JavaScript bắt sự kiện click trên thanh điều hướng bên trái (Left Bar) để thay đổi giá trị `src` của iframe và tiêu đề động tương ứng, giúp chuyển đổi nội dung mượt mà mà không tải lại toàn bộ trang (SPA).

128. **Tích hợp cổng nhập kịch bản (Script Console) và API ghi log**
    > - Bổ sung menu "Script Console" trên thanh điều hướng bên trái của `app_index.html` cùng giao diện soạn thảo văn bản (`textarea`) nhập kịch bản điều khiển ở vùng nội dung chính.
    > - Viết xử lý nút "Send Script" thực hiện POST gói tin JSON chứa nội dung kịch bản lên endpoint `/api/qa/script`.
    > - Đăng ký endpoint `/api/qa/script` trong `SimpleHttpServer.kt` để tiếp nhận nội dung kịch bản và in trực tiếp ra log hệ thống của thiết bị Android (`android.util.Log.i`) cũng như server log.

129. **Loại bỏ tính năng xem Logcat khỏi Web Server và giao diện appQa**
    > Loại bỏ hoàn toàn tính năng Logcat theo yêu cầu:
    > - Xóa bỏ các endpoint `/logcat`, `/logcat.html`, và `/api/logcat/config` bên trong tệp định tuyến `SimpleHttpServer.kt`.
    > - Xóa bỏ nút menu "Logcat Console" và logic chuyển hướng view logcat tương ứng trong file `app_index.html` của module `appQa`.

130. **Tự động cấp toàn bộ quyền nếu thiết bị đã Root (Auto-grant rooted permissions)**
    > Thiết lập cơ chế tự động phát hiện quyền Root (`su`) và thực thi shell commands để tự động cấp toàn bộ các quyền cần thiết trên `MainActivity`:
    > - Lọc và kiểm tra sự tồn tại của tệp thực thi `su` trong các thư mục hệ thống.
    > - Nếu thiết bị là rooted (Emulator hoặc điện thoại đã root), ứng dụng tự động mở phiên kết nối `su` để ghi cấu hình `secure settings` kích hoạt Trợ năng (Accessibility Service) mà không làm mất các dịch vụ khác đã bật, cấp quyền vẽ đè màn hình (`appops SYSTEM_ALERT_WINDOW allow`), và whitelist thông báo (`pm grant POST_NOTIFICATIONS`).

131. **Sửa lỗi lặp lại SecurityException trong logcat liên quan tới WifiManager**
    > - Phát hiện và phân tích lỗi `SecurityException` xuất hiện liên tục mỗi 1 giây trong logcat do hàm `WifiManager.connectionInfo` yêu cầu quyền truy cập Location trên Android 10+ (API 29+).
    > - Tối ưu hóa hàm `getLocalIpAddress(context)` trong `NetworkUtils.kt`: Đưa tiến trình quét card mạng Network Interfaces (không yêu quyền, an toàn và tối ưu) lên chạy trước tiên. Chỉ chạy fallback WifiManager khi phương thức trước trả về null, đồng thời bắt các ngoại lệ bảo mật một cách lặng lẽ (silent catch) để triệt tiêu hoàn toàn log rác.

132. **Tách biệt logic cập nhật ứng dụng thành Kotlin Extension**
    > Tách các thành phần liên quan đến logic nâng cấp ứng dụng (appupdate) ra khỏi `MainActivity.kt` của module `app`:
    > - Tạo mới tệp extension `MainActivityUpdate.kt` để quản lý các hàm tiện ích Trình cập nhật.
    > - Chuyển phương thức đăng ký Observer (`setupAppUpdateObservers()`) và hiển thị thông tin phiên bản (`showAppUpdateInfoDialog()`) sang tệp extension mới.
    > - Thay đổi mức độ hiển thị (visibility) các trường liên quan trong `MainActivity.kt` (như `viewModel`, `discoveryService`, `updateDownloadFlow`) từ `private` thành `internal` để cho phép tệp extension liên kết và thao tác trực tiếp.

133. **Tạo đối tượng AppTool quản lý các hàm điều khiển ứng dụng**
    > - Khởi tạo tệp tin `AppTool.kt` chứa đối tượng `AppTool` trong module `:libs:core` (thay vì module application `:appQa` để tránh lỗi chiều phụ thuộc) giúp cho cả module webserver và module app cùng tái sử dụng.
    > - Phát triển phương thức đầu tiên `openApp(context, query)` hỗ trợ kích hoạt khởi chạy ứng dụng dựa trên: tên gói cụ thể (package name), nhãn hiển thị ứng dụng (app name) khớp tuyệt đối, hoặc tìm kiếm chứa chuỗi (substring search) không phân biệt chữ hoa thường trên toàn bộ danh mục launcher được cài đặt trên máy.

134. **Tích hợp giao diện và API kích hoạt ứng dụng từ xa (Open App Action)**
    > - Tạo menu điều hướng "Test Console" bên thanh trái của tệp `app_index.html`.
    > - Thiết kế giao diện nhập liệu app name/package name cùng nút "Open App" và cơ chế phản hồi trạng thái hoạt động.
    > - Thêm route handler `POST /api/qa/openapp` bên trong `SimpleHttpServer.kt` tiếp nhận tham số query và gọi `AppTool.openApp(context, query)`.
    > - Cấu hình thêm phụ thuộc `:libs:core` trong tệp `libs/webserver/build.gradle.kts` để nạp class `AppTool` thành công.

135. **Sửa lỗi không thể khởi động lại Web Server khi đã Stop (CoroutineScope lifecycle bug)**
    > - Phát hiện lỗi logic trong `SimpleHttpServer.kt` khi người dùng nhấn nút Stop Server: hàm `stop()` gọi `scope.cancel()` để đóng vòng lặp. Điều này đưa `CoroutineScope` vào trạng thái hủy bỏ vĩnh viễn (terminal state), khiến cho mọi lệnh gọi `start()` sau đó (sử dụng `scope.launch`) bị từ chối thực thi âm thầm.
    > - Khắc phục bằng cách giới thiệu biến toàn cục `serverJob` để theo dõi tiến trình chạy socket chấp nhận kết nối. Khi gọi `stop()`, ứng dụng chỉ hủy bỏ `serverJob` tương ứng thay vì hủy toàn bộ `scope`, cho phép khởi động lại server bình thường.

136. **Tự động đóng màn hình chính MainActivity khi kích hoạt dịch vụ chạy nền**
    > - Cập nhật vòng đăng ký kết quả Media Projection `mediaProjectionLauncher` trong `MainActivity.kt` của module `appQa`.
    > - Ngay sau khi bắt đầu dịch vụ chạy nền nổi bật (`startForegroundService`) và kích hoạt thành công view overlay, ứng dụng thực thi lệnh gọi `finish()` để giải phóng và tắt hoàn toàn giao diện màn hình chính (`MainActivity`), giúp người dùng chuyển đổi thẳng ra màn hình làm việc/home của thiết bị.

137. **Thiết lập mặc định chia sẻ toàn màn hình (Entire Screen) khi yêu cầu quyền chụp/quay**
    > - Tối ưu hóa bộ lọc khởi chạy quyền MediaProjection của hệ thống trên Android 14+ (API 34+):
    > - Sử dụng đối tượng cấu hình `MediaProjectionConfig.createConfigForDefaultDisplay()` truyền vào hàm `createScreenCaptureIntent(config)` của MediaProjectionManager. Việc này giúp tự động chọn trước và chỉ cho phép tùy chọn chia sẻ toàn bộ màn hình ("Entire Screen") thay vì buộc người dùng phải chọn một ứng dụng đơn lẻ ("Single App").

138. **Cấp quyền Package Visibility cho phép truy vấn ứng dụng cài đặt trên Android 11+**
    > - Khắc phục lỗi không tìm thấy ứng dụng (ví dụ như "Calendar") khi gọi API hoặc nhập lệnh từ xa thông qua `AppTool.openApp()`. Nguyên nhân do từ Android 11 (API 30+), hệ thống áp dụng cơ chế Package Visibility giới hạn khả năng truy vấn thông tin các gói cài đặt khác.
    > - Cấu hình thêm thẻ `<queries>` khai báo intent-filter launcher MAIN/LAUNCHER cùng với quyền `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` vào tệp manifest `appQa/src/main/AndroidManifest.xml`, giúp khôi phục đầy đủ quyền truy vấn và tìm kiếm danh sách launcher để khởi chạy chính xác.

139. **Xây dựng ScriptTool và nâng cấp Test Console chạy kịch bản tự động hóa từ xa qua JSON**
    > - Tạo mới lớp `ScriptTool.kt` trong module `appQa` để phân tích và thực thi tuần tự các bước trong kịch bản tự động hóa (Mở app, Click tọa độ X/Y, Chụp màn hình, Quay màn hình) bằng Coroutines.
    > - Định nghĩa interface `ScriptExecutor` trong `SimpleHttpServer` và đăng ký route `POST /api/qa/runscript`. Khi start service `QaAutomationService`, đăng ký instance để điều phối lệnh.
    > - Cập nhật giao diện `app_index.html`: Thêm ô nhập tọa độ X, Y và bộ chọn radio button (None, Capture Screen, Record Screen). Nhấn nút "Start" sẽ sinh một kịch bản JSON tổng hợp từ các input và gửi POST lên server chạy tuần tự.

140. **Tích hợp Database và Xây dựng UI CRUD cho Script tại Script Console**
    > - Cấu hình nâng cấp phiên bản SQLite Database trong `DatabaseHelper.kt` lên `2` để tạo mới bảng `scripts` lưu trữ kịch bản tự động hóa (Tên script, nội dung JSON, số lần chạy `runCount`, thời gian chạy cuối `lastRun`, thời gian cập nhật `lastUpdate`).
    > - Triển khai các API endpoint trong `SimpleHttpServer.kt` cho kịch bản (`GET /api/qa/scripts`, `POST /api/qa/scripts`, `PUT /api/qa/scripts`, `DELETE /api/qa/scripts`). Đồng thời hỗ trợ gọi chạy kịch bản đã lưu qua `POST /api/qa/runscript` bằng cách gửi kèm JSON có chứa `id` script. Kịch bản chạy sẽ tự động tăng `runCount` và ghi nhận timestamp `lastRun`.
    > - Nâng cấp giao diện `app_index.html` tại trang Script Console thành dashboard quản lý kịch bản: Thêm form điền tên/nội dung JSON kịch bản (với các nút Save, Clear/New, Run Instantly) và bảng danh sách thư viện hiển thị chi tiết số lần chạy, thời điểm chạy cuối, thời điểm cập nhật cuối kèm các nút chức năng (Run, Edit, Delete).

141. **Tái cấu trúc thư mục Web, tích hợp Vue.js (CDN), tách trang Script List & Detail**
    > - Phân tách giao diện tĩnh ra cấu trúc thư mục chuyên nghiệp trong `assets`: `css/app.css` (lưu stylesheet chung), `js/app.js` (chứa toàn bộ logic JavaScript), cùng các thư mục trống cấu trúc `img` và `fonts` để chuẩn hóa phân phối tĩnh.
    > - Đăng ký định tuyến phục vụ tài nguyên `/fonts/*` trong `SimpleHttpServer.kt`.
    > - Tích hợp framework Vue 3 (Browser Global Build từ CDN) giúp rút gọn toàn bộ mã JavaScript điều khiển DOM thủ công, chuyển sang mô hình phản xạ (Reactivity) với `v-model` và `v-if/v-show`.
    > - Chia trang Script thành hai chế độ riêng biệt: Trang Danh sách kịch bản (List) hiển thị thư viện bảng tổng hợp thông số; trang Chi tiết kịch bản (Detail) chứa biểu mẫu lưu/sửa kịch bản và khung thông số meta nâng cao (ID, Run Count, Last Run, Last Update).

142. **Tích hợp nút Switch Bật/Tắt Web Server trực tiếp trên giao diện chính của CI-Deploy**
    > - Bổ sung một widget `SwitchCompat` (`switchWebserver`) vào phần Footer của thanh menu bên (Navigation Drawer) trong tệp layout `app/src/main/res/layout/activity_main.xml`.
    > - Lắng nghe thay đổi trạng thái của Switch trong `MainActivity.kt` để khởi động dịch vụ web server (`startAndBindWebServer()`) hoặc tắt dịch vụ (`unbindWebServerIfBound()`, `stopService()`) một cách an toàn và trực tiếp.
    > - Cập nhật hàm mở rộng `updateWebConsoleInfo()` trong `MainActivityWebServer.kt` để tự động đồng bộ hóa trạng thái gạt bật/tắt của nút Switch này dựa trên trạng thái hoạt động thực tế (`HttpWebServerService.isRunning`), đồng thời chuyển đổi màu sắc mô tả liên kết URL bảng điều khiển (màu xanh lá khi ONLINE, màu đỏ khi OFFLINE).
























