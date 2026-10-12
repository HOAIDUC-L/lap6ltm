# NetSend - Ứng Dụng Client-Server Nhắn Tin Mạng LAN (Java Swing & TCP Socket)

> **Bài tập môn:** Lập trình mạng (Network Programming)  
> **Ngôn ngữ:** Java 17+ (Tương thích Java 17, 21+)  
> **Giao thức:** TCP Socket (`ServerSocket`, `Socket`) đa luồng  
> **Giao diện:** Java Swing thuần (Không dùng thư viện bên ngoài, không Spring Boot, không CSDL)  
> **Hệ điều hành:** Chạy mượt mà trên cả Linux (Ubuntu) và Windows  

---

## 1. Giới thiệu tổng quan

**NetSend** là ứng dụng mô phỏng tiện ích gửi nhận tin nhắn trong mạng nội bộ (tương tự dịch vụ Messenger/Net Send kinh điển của Windows) với giao diện đồ họa hiện đại bằng Java Swing và cơ chế truyền tin TCP Socket mạnh mẽ.

### Các tính năng chính:
- **Phía Server (Quản trị mạng):**
  - **Thanh thống kê tổng quan:** Hiển thị realtime số Client online, tổng số Client từng kết nối, số Client bị kick, trạng thái Server, địa chỉ IP LAN thực tế và Cổng lắng nghe (mặc định 8888).
  - **Bảng quản lý Client (`JTable`):**
    - Đầy đủ 9 cột: STT, ID Client, Tên Client, Địa chỉ IP, Tên nhóm, Thời gian kết nối, Thời gian ngắt kết nối, Thời lượng online (cập nhật theo từng giây), Trạng thái (ONLINE - xanh lá, OFFLINE - xám).
    - Hỗ trợ **tìm kiếm tức thời** theo Tên, ID, IP hoặc Nhóm.
    - Hỗ trợ **bộ lọc trạng thái** (Tất cả, ONLINE, OFFLINE).
    - Hỗ trợ **sắp xếp cột** linh hoạt khi click tiêu đề cột.
    - Không trùng lặp dòng khi Client mất kết nối bất thường.
  - **Chức năng quản trị:**
    - **Kick Client:** Ngắt kết nối một Client bất kỳ với hộp thoại xác nhận. Client bị kick nhận thông báo và bị ngắt kết nối an toàn, không ảnh hưởng đến Server hay các Client khác.
    - **Xem chi tiết:** Xem toàn bộ thông số của Client (ID, Tên, IP, Nhóm, Số lần kết nối, Số lần bị kick, Thời lượng online,...).
    - **Xóa lịch sử:** Xóa bản ghi Client đã chọn (chỉ cho phép xóa Client OFFLINE).
    - **Làm mới:** Đồng bộ lại giao diện bảng.
  - **Nhật ký hoạt động (Server Activity Log):**
    - Ghi nhận chi tiết mọi sự kiện kèm mốc thời gian chuẩn `[dd/MM/yyyy HH:mm:ss]`: khởi động, dừng, Client kết nối, ngắt kết nối, mất kết nối bất thường, kick Client, tin nhắn chuyển tiếp thành công, lỗi không tìm thấy IP/nhóm,...
    - Tự động cuộn xuống dòng mới nhất, hỗ trợ nút xóa nhật ký, không làm treo giao diện Swing EDT.
  - **Lưu trữ dữ liệu:** Tự động lưu và phục hồi lịch sử Client ra file CSV (`data/clients_history.csv`). Khi mở lại Server, toàn bộ lịch sử cũ được nạp lại an toàn.

- **Phía Client (Người dùng):**
  - **Kết nối linh hoạt:** Nhập IP Server, Cổng, Tên Client, Tên Nhóm. Hiển thị trạng thái kết nối và IP/ID được cấp.
  - **Danh sách Client online (LAN):** Cập nhật realtime danh sách các Client đang trực tuyến. Click chọn vào dòng của một Client sẽ tự động điền IP của Client đó vào ô người nhận tin.
  - **Gửi tin nhắn đa dạng:**
    - Gửi theo **Địa chỉ IP** (tin nhắn riêng).
    - Gửi theo **Tên nhóm** (chỉ các máy cùng nhóm nhận được).
    - Gửi **Toàn mạng** (`*` - Broadcast cho tất cả Client đang online).
  - **Hỗ trợ cú pháp lệnh Net Send:**
    - Người dùng có thể gõ trực tiếp lệnh vào ô tin nhắn:
      - `net send 192.168.1.10 Xin chao ban`
      - `net send NhomIT Hop luc 10 gio`
      - `net send * Thong bao bao tri he thong`
    - Chương trình tự động nhận diện và gửi tin tương ứng.
  - **Phân biệt hiển thị:** Màu sắc trực quan phân biệt tin nhắn gửi đi, tin nhắn nhận được và thông báo hệ thống/lỗi; hỗ trợ tiếng Việt có dấu Unicode UTF-8 trọn vẹn.
  - **Xử lý khi bị Kick:** Hiển thị popup thông báo, khóa khung chat, không tự động kết nối lại liên tục (người dùng phải chủ động bấm Kết nối lại nếu muốn).

---

## 2. Kiến trúc mã nguồn

Tất cả các file mã nguồn được đặt trong thư mục `src/`, tuân theo cấu trúc chuẩn, dễ hiểu và dễ thuyết minh:

```
NetSend/
├── src/
│   ├── Server.java            // Core Server TCP Socket, routing tin nhắn, quản lý ClientHandler
│   ├── Client.java            // Core Client TCP Socket, luồng nhận tin ngầm, callback GUI
│   ├── ServerGUI.java         // Giao diện quản trị Server (Thống kê, JTable, Kick, Log)
│   ├── ClientGUI.java         // Giao diện Client (Kết nối, danh sách online, chat, net send)
│   ├── ClientHandler.java     // Luồng xử lý từng socket Client tại Server
│   ├── MessageProtocol.java   // Định nghĩa giao thức, đóng khung newline '\n', mã hóa Base64 UTF-8
│   ├── ClientInfo.java        // Model dữ liệu Client: ID, Tên, IP, Nhóm, Thời gian, Thống kê
│   ├── HistoryManager.java    // Đọc/ghi lịch sử kết nối ra file CSV (data/clients_history.csv)
│   ├── UITheme.java           // Tiện ích màu sắc, font chữ và renderer cho Swing
│   └── TestRunner.java        // Bộ kiểm thử tự động 100% tính năng hệ thống
├── data/
│   └── clients_history.csv    // Tệp dữ liệu CSV lưu lịch sử Client
└── README.md                  // Hướng dẫn chi tiết
```

### Chức năng chi tiết từng file:
1. **`Server.java`:** Chạy `ServerSocket`, tiếp nhận kết nối mới và đưa vào thread pool, điều phối định tuyến tin nhắn, kick Client, broadcast danh sách online, ghi log sự kiện. Có hàm `main` để khởi chạy nhanh `ServerGUI`.
2. **`Client.java`:** Tạo `Socket` kết nối tới Server, lắng nghe phản hồi và tin nhắn từ Server trên luồng nền (background thread), cung cấp các phương thức `sendMessage`, `disconnect`. Có hàm `main` để khởi chạy nhanh `ClientGUI`.
3. **`ServerGUI.java`:** Giao diện đồ họa của Server gồm thẻ thống kê, bộ lọc tìm kiếm, bảng `JTable` với màu sắc trạng thái, nút Kick, nút Xem chi tiết, nút Xóa lịch sử và khu vực nhật ký tự cuộn.
4. **`ClientGUI.java`:** Giao diện đồ họa của Client gồm khu vực kết nối, bảng danh sách Client online, khung hiển thị tin nhắn màu sắc và thanh nhập tin (hỗ trợ cả chọn giao diện và gõ cú pháp `net send`).
5. **`ClientHandler.java`:** Lớp `Runnable` chạy trên Server đại diện cho 1 Client, nhận dữ liệu qua `BufferedReader`, phân tích gói tin và gửi phản hồi qua `PrintWriter`.
6. **`MessageProtocol.java`:** Đóng gói và giải mã các thông điệp theo chuẩn đóng khung dòng (`\n`). Sử dụng Base64 cho nội dung tin nhắn và tiếng Việt UTF-8 nhằm tránh lỗi khi tin nhắn chứa dấu cách, dấu xuống dòng hoặc ký tự phân cách.
7. **`ClientInfo.java`:** Lưu trữ toàn bộ thông tin định danh và số liệu của một Client (ID, Tên, IP, Nhóm, thời gian kết nối/ngắt, thời lượng online, số lần kết nối, số lần bị kick).
8. **`HistoryManager.java`:** Đảm bảo tính bền vững của dữ liệu, tự động lưu và đọc dữ liệu từ `data/clients_history.csv`.
9. **`UITheme.java`:** Thiết kế giao diện phẳng, đẹp mắt, các renderer màu sắc cho JTable (badge ONLINE xanh lá, OFFLINE xám).
10. **`TestRunner.java`:** Kịch bản kiểm thử tự động không cần giao diện, kiểm tra 20 tiêu chí cốt lõi của bài toán.

---

## 3. Hướng dẫn chạy trên IntelliJ IDEA

### Bước 1: Mở Project trong IntelliJ IDEA
1. Mở **IntelliJ IDEA**.
2. Chọn **File -> Open...** và duyệt đến thư mục `lap6` (hoặc thư mục chứa mã nguồn NetSend).
3. IntelliJ IDEA sẽ tự động nhận diện project. Đảm bảo cấu hình Project SDK là **Java 17** hoặc **Java 21** (vào *File -> Project Structure -> Project -> SDK*).
4. Nếu thư mục `src` chưa được đánh dấu là mã nguồn: Click chuột phải vào thư mục `src` -> **Mark Directory as -> Sources Root**.

### Bước 2: Chạy Server
1. Trong cửa sổ Project bên trái, duyệt vào thư mục `src/`.
2. Nhấp chuột phải vào file **`ServerGUI.java`** (hoặc `Server.java`).
3. Chọn **Run 'ServerGUI.main()'**.
4. Cửa sổ Server sẽ hiện lên. Nhấn nút **"▶ Khởi động Server"** để bắt đầu lắng nghe tại cổng `8888`.

### Bước 3: Chạy nhiều Client đồng thời trên IntelliJ IDEA
Để kiểm tra chat giữa 2 hoặc nhiều Client trên cùng một máy trong IntelliJ IDEA:
1. Nhấp chuột phải vào file **`ClientGUI.java`** (hoặc `Client.java`) -> Chọn **Run 'ClientGUI.main()'**.
2. Để IntelliJ cho phép mở thêm cửa sổ Client thứ hai, thứ ba mà không tắt cửa sổ cũ:
   - Trên thanh menu của IntelliJ, chọn **Run -> Edit Configurations...**
   - Ở cột bên trái, chọn cấu hình **`ClientGUI`** vừa chạy.
   - Nhìn sang góc phải của hộp thoại, nhấp vào dòng chữ xanh **"Modify options"** (hoặc với các bản IntelliJ cũ, tìm mục *Single instance only*).
   - Tích chọn mục **"Allow multiple instances"** (hoặc **"Allow parallel run"**).
   - Nhấn **Apply** rồi nhấn **OK**.
3. Bây giờ bạn có thể nhấn nút **Run** (mũi tên xanh ▶) trên thanh công cụ bao nhiêu lần tùy thích! Mỗi lần nhấn sẽ mở ra một cửa sổ Client độc lập mới:
   - Cửa sổ Client 1: Tên `Duc-PC`, Nhóm `IT` -> Bấm **Kết nối**.
   - Cửa sổ Client 2: Tên `Phong-Laptop`, Nhóm `IT` -> Bấm **Kết nối**.
   - Cửa sổ Client 3: Tên `Hoa-KeToan`, Nhóm `KeToan` -> Bấm **Kết nối**.

---

## 4. Hướng dẫn biên dịch và chạy bằng dòng lệnh (Terminal / CMD)

### Trên Linux (Ubuntu / Debian):
Mở Terminal tại thư mục project:
```bash
# 1. Biên dịch toàn bộ mã nguồn
javac -encoding UTF-8 src/*.java

# 2. Khởi chạy Server GUI
java -cp src ServerGUI

# 3. Mở một Terminal khác để khởi chạy Client thứ nhất
java -cp src ClientGUI

# 4. Mở thêm Terminal khác để khởi chạy Client thứ hai
java -cp src ClientGUI
```

### Trên Windows (Command Prompt hoặc PowerShell):
Mở CMD hoặc PowerShell tại thư mục project:
```cmd
# 1. Biên dịch toàn bộ mã nguồn
javac -encoding UTF-8 src\*.java

# 2. Khởi chạy Server GUI
java -cp src ServerGUI

# 3. Mở CMD khác để khởi chạy Client 1
java -cp src ClientGUI

# 4. Mở CMD khác để khởi chạy Client 2
java -cp src ClientGUI
```

---

## 5. Hướng dẫn kết nối trong mạng LAN thực tế

Khi chạy giữa hai hoặc nhiều máy tính khác nhau kết nối cùng mạng Wi-Fi hoặc mạng dây LAN:

### 1. Kiểm tra IP LAN máy Server:
- **Ngay trên giao diện Server:** Xem thẻ **"IP LAN MÁY SERVER"** ở thanh thống kê phía trên (chương trình tự động dò tìm IP LAN thực tế dạng `192.168.x.x` hoặc `10.x.x.x`).
- **Hoặc bằng lệnh terminal:**
  - Trên Linux: Chạy lệnh `hostname -I` hoặc `ip a`.
  - Trên Windows: Chạy lệnh `ipconfig` trong CMD, xem dòng `IPv4 Address`.

### 2. Cấu hình Firewall (Tường lửa):
- Đảm bảo máy Server cho phép nhận kết nối đến cổng `8888`:
  - Trên Linux: `sudo ufw allow 8888/tcp`
  - Trên Windows: Cho phép Java qua Windows Defender Firewall hoặc mở inbound port 8888.

### 3. Kết nối từ máy Client:
- Mở `ClientGUI` trên máy Client.
- Nhập ô **IP Server** là IP của máy Server (ví dụ: `192.168.1.15`).
- Nhập **Cổng**: `8888`.
- Nhập **Tên Client** và **Nhóm**.
- Nhấn **"Kết nối"**.

---

## 6. Hướng dẫn sử dụng & Quy tắc định tuyến tin nhắn

### Cú pháp lệnh `net send`:
Người dùng có thể gõ trực tiếp cú pháp lệnh vào ô nhập tin nhắn ở Client:
- **Gửi riêng theo IP:**
  ```
  net send 192.168.1.10 Xin chào bạn!
  ```
- **Gửi theo Nhóm:**
  ```
  net send IT Họp nhóm kỹ thuật lúc 10h sáng.
  ```
- **Gửi Toàn mạng:**
  ```
  net send * Thông báo: Server sẽ bảo trì trong 15 phút nữa.
  ```

### Quy tắc định tuyến:
1. **Gửi theo IP:** Server tìm kiếm tất cả các Client đang online có địa chỉ IP trùng khớp (ngoại trừ người gửi) để chuyển tiếp.
   - *Lưu ý khi test trên cùng một máy (Localhost):* Khi nhiều Client cùng chạy trên 1 máy, tất cả đều có IP là `127.0.0.1`. Do đó, lệnh gửi tới `127.0.0.1` sẽ chuyển tiếp đến tất cả các Client khác trên máy đó. Trên Server, mỗi Client vẫn có **ID Client duy nhất** (`CLI-001`, `CLI-002`,...) để quản lý, theo dõi thời gian và thực hiện Kick chính xác từng cửa sổ.
2. **Gửi theo Nhóm:** Server tìm kiếm các Client có tên nhóm trùng khớp (không phân biệt hoa thường) ngoại trừ người gửi. Nếu không có ai trong nhóm đang online, Server sẽ phản hồi thông báo lỗi cho người gửi.
3. **Gửi Toàn mạng (`*`):** Chuyển tiếp tin nhắn đến toàn bộ Client đang online (ngoại trừ người gửi).

---

## 7. Chạy kiểm thử tự động (Integration Tests)

Dự án tích hợp sẵn lớp `TestRunner` mô phỏng đầy đủ 20 ca kiểm thử tự động (khởi động/dừng server, nhiều client kết nối đồng thời, gửi tin IP, gửi tin nhóm, gửi tin toàn mạng, phân tích cú pháp net send, kick client, kiểm tra không ảnh hưởng client khác, lưu file CSV):

```bash
java -cp src TestRunner
```

**Kết quả kiểm thử thực tế:**
```
==================================================
BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG HỆ THỐNG NETSEND
==================================================
  [PASS] Test 1: Server khởi động
  [PASS] Test 2.1: Cả 3 Client kết nối trong 5 giây
  [PASS] Test 2.2: Client 1 kết nối thành công
  [PASS] Test 2.3: Client 2 kết nối thành công
  [PASS] Test 2.4: Client 3 kết nối thành công
  [PASS] Test 3: Số lượng online trên Server là 3
  [PASS] Test 4: Gửi tin nhắn theo IP thành công
  [PASS] Test 5: Gửi tin nhắn theo Nhóm IT tới Client 2
  [PASS] Test 6.1: Phân tích net send theo IP
  [PASS] Test 6.2: Phân tích net send theo Group
  [PASS] Test 6.3: Phân tích net send toàn mạng *
  [PASS] Test 7.1: Server gọi kickClient thành công
  [PASS] Test 7.2: Client 3 nhận được thông báo KICK
  [PASS] Test 7.3: Client 3 bị ngắt kết nối
  [PASS] Test 7.4: Client 1 & 2 vẫn đang online bình thường
  [PASS] Test 7.5: Số online giảm xuống còn 2
  [PASS] Test 8: Client 1 chủ động ngắt kết nối
  [PASS] Test 9: Server dừng an toàn
  [PASS] Test 10.1: File lịch sử CSV tồn tại
  [PASS] Test 10.2: Nạp lại lịch sử từ CSV thành công (3 bản ghi)
==================================================
KẾT QUẢ KIỂM THỬ: 20/20 BÀI KIỂM TRA ĐẠT (100.0%)
==================================================
TẤT CẢ CÁC BÀI KIỂM THỬ ĐÃ VƯỢT QUA XUẤT SẮC!
```

---

## 8. Tác giả & Giấy phép
- Ứng dụng phục vụ bài tập lớn / đồ án môn học Lập trình mạng.
- Mã nguồn sạch, chú thích tiếng Việt rõ ràng, dễ bảo vệ trước giảng viên.
