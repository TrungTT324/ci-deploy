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
