# SmsPing OTG (bản Android - SIM7600G-H qua USB-OTG)

## Cách build APK từ điện thoại (giống pipeline com.pingsim)
1. Tạo repo mới trên GitHub (hoặc dùng lại repo cũ), upload toàn bộ thư mục này lên (dùng giao diện web GitHub: **Add file > Upload files**, kéo thả cả cây thư mục).
2. Vào tab **Actions** của repo → workflow "Build APK" sẽ tự chạy (hoặc bấm **Run workflow** để chạy tay).
3. Đợi build xong (vài phút) → vào job vừa chạy → mục **Artifacts** → tải file `SmsPingOtg-debug-apk`.
4. Giải nén ra được `app-debug.apk` → cài vào điện thoại (nhớ bật "Cài từ nguồn không xác định").

## Cách dùng
1. Cắm cáp OTG → cắm modem SIM7600G-H vào điện thoại.
2. Mở app → bấm **Quét** để tìm modem → chọn trong danh sách → bấm **Connect** → Android sẽ hỏi cấp quyền USB, chọn **OK**.
3. App sẽ tự kiểm tra IMEI modem (danh sách được phép dùng, giống bản laptop) rồi mới cho phép PING.
4. Các chức năng còn lại y hệt bản laptop: PING SMS, tự động báo khi SDT online lại, AT Command thủ công, Decode, Check Hardware, Check TK SIM.

## Vì sao không dùng PWABuilder
Android Chrome hỗ trợ WebUSB nhưng **không hỗ trợ Web Serial API** (do bản thân Android không cung cấp API serial chuẩn cho trình duyệt). Muốn giao tiếp AT command qua PWA sẽ phải tự viết toàn bộ giao thức USB CDC-ACM bằng JavaScript — phức tạp và dễ lỗi hơn nhiều so với dùng thư viện `usb-serial-for-android` có sẵn trong app native này.

## File quan trọng
- `MainActivity.kt` — toàn bộ logic nghiệp vụ (port từ `frmMain.cs` bản laptop).
- `UsbAtManager.kt` — quản lý kết nối/gửi/nhận qua USB-OTG.
- `PduCodec.kt` — mã hóa/giải mã PDU + bảng tra mã lỗi (logic giống hệt bản laptop).
- `device_filter.xml` — nhận diện modem SIM7600 theo Vendor ID khi cắm OTG.
