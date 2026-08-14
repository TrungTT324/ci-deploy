# Chụp ảnh, quay màn hình và báo cáo

## Media Projection

`QaAutomationService` nhận phiên Media Projection sau khi người dùng chấp thuận hộp thoại hệ thống. Cùng phiên này được dùng cho chụp ảnh và quay video.

Nếu Android thu hồi phiên, callback `onStop` sẽ dừng service. Cần mở lại app và cấp quyền Screen Capture để tạo phiên mới.

## Chụp ảnh màn hình

`ScreenCaptureHelper` tạo `VirtualDisplay` và đọc frame RGBA qua `ImageReader`.

Quy trình:

1. Ẩn floating control.
2. Chờ ngắn để UI cập nhật.
3. Đọc frame mới nhất.
4. Loại bỏ row padding để ảnh đúng kích thước màn hình.
5. Lưu PNG và hiển thị lại floating control.

Thao tác có timeout 2,5 giây nếu không nhận được frame.

Khi chạy core action `capture`, runner chờ file được lưu vào reports rồi trả URL tương đối trong `output`:

```json
"output": "/qa/reports/QA_Screenshot_1760000000000.png"
```

URL dùng cùng origin với Web Portal, có thể mở trực tiếp hoặc hiển thị bằng thẻ `<img>`.

## Quay màn hình

`ScreenRecordHelper` dùng `MediaRecorder` với cấu hình:

- Container: MP4.
- Video codec: H.264.
- Kích thước: độ phân giải vật lý của màn hình.
- Frame rate: 30 fps.
- Bitrate: 3 Mbps.
- Audio: không được ghi.

Video ban đầu được ghi vào file tạm trong cache. Khi dừng, app sao chép file sang vùng reports và Gallery, sau đó xóa file tạm.

## Hai nơi lưu kết quả

Mỗi media thành công được lưu ở hai nơi:

### Vùng reports của ứng dụng

```text
Android/data/hdisoft.app.qa/files/reports/
```

Web server đọc vùng này để hiển thị và tải file. Android hoặc file manager có thể hạn chế truy cập trực tiếp thư mục app-specific.

### Gallery công khai

- Ảnh: `Pictures/QAApp`
- Video: `Movies/QAApp`

Tên file có timestamp:

```text
QA_Screenshot_<timestamp>.png
QA_Recording_<timestamp>.mp4
```

## Trang Test Reports

Mở `http://<device-ip>:8086/reports` hoặc chọn **Test Reports** trong Web Portal. Trang cho phép lọc, preview ảnh/video và tải file về máy tính.

## Lưu ý

- Floating control chỉ được ẩn lúc bắt đầu capture/record; trong lúc quay, overlay có thể xuất hiện trở lại trong video.
- Bản reports và bản Gallery là hai bản sao riêng.
- Gỡ ứng dụng có thể xóa vùng app-specific reports, nhưng media đã xuất vào Gallery thường vẫn còn.
- Video rất ngắn có thể lỗi khi `MediaRecorder.stop()` do chưa có đủ frame hợp lệ.
