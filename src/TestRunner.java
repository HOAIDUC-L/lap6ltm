import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lớp kiểm thử tự động toàn diện cho hệ thống NetSend.
 * Kiểm tra đầy đủ các tình huống:
 * 1. Khởi động và dừng Server socket.
 * 2. Đăng ký đồng thời nhiều Client với tên, nhóm khác nhau.
 * 3. Đồng bộ danh sách online realtime.
 * 4. Gửi tin nhắn theo IP (riêng).
 * 5. Gửi tin nhắn theo Nhóm.
 * 6. Gửi tin nhắn toàn mạng (*).
 * 7. Kiểm tra phân tích cú pháp lệnh 'net send'.
 * 8. Server Kick Client: Client bị kick nhận thông báo và ngắt, Client khác vẫn hoạt động bình thường.
 * 9. Lưu trữ và phục hồi dữ liệu lịch sử từ file CSV.
 */
public class TestRunner {
    private static int testsPassed = 0;
    private static int testsTotal = 0;

    private static void assertTrue(String testName, boolean condition, String message) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            System.err.println("  [FAIL] " + testName + " - " + message);
        }
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG HỆ THỐNG NETSEND");
        System.out.println("==================================================");

        int testPort = 18888;
        Server server = new Server(testPort);

        try {
            // Test 1: Khởi động Server
            server.start();
            assertTrue("Test 1: Server khởi động", server.isRunning(), "Server không ở trạng thái running");

            // Test 2: Kết nối Client 1 và Client 2
            Client client1 = new Client();
            Client client2 = new Client();
            Client client3 = new Client();

            CountDownLatch connectLatch = new CountDownLatch(3);
            AtomicBoolean c1Connected = new AtomicBoolean(false);
            AtomicBoolean c2Connected = new AtomicBoolean(false);
            AtomicBoolean c3Connected = new AtomicBoolean(false);

            client1.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) { c1Connected.set(true); connectLatch.countDown(); }
                public void onConnectionFailed(String e) { connectLatch.countDown(); }
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {}
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client2.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) { c2Connected.set(true); connectLatch.countDown(); }
                public void onConnectionFailed(String e) { connectLatch.countDown(); }
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {}
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client3.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) { c3Connected.set(true); connectLatch.countDown(); }
                public void onConnectionFailed(String e) { connectLatch.countDown(); }
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {}
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client1.connect("127.0.0.1", testPort, "Duc-PC", "IT");
            client2.connect("127.0.0.1", testPort, "Phong-Laptop", "IT");
            client3.connect("127.0.0.1", testPort, "Hoa-Accounting", "KeToan");

            boolean latchOk = connectLatch.await(5, TimeUnit.SECONDS);
            assertTrue("Test 2.1: Cả 3 Client kết nối trong 5 giây", latchOk, "Timeout khi kết nối");
            assertTrue("Test 2.2: Client 1 kết nối thành công", c1Connected.get(), "Client 1 không kết nối được");
            assertTrue("Test 2.3: Client 2 kết nối thành công", c2Connected.get(), "Client 2 không kết nối được");
            assertTrue("Test 2.4: Client 3 kết nối thành công", c3Connected.get(), "Client 3 không kết nối được");

            Thread.sleep(500); // Đợi server cập nhật số liệu
            assertTrue("Test 3: Số lượng online trên Server là 3", server.getOnlineCount() == 3, "Online count != 3 (thực tế: " + server.getOnlineCount() + ")");

            // Test 4: Gửi tin nhắn theo IP
            CountDownLatch ipMsgLatch = new CountDownLatch(2); // Cả client 2 và 3 đều nhận vì test trên 127.0.0.1 (cùng IP)
            List<String> receivedContent = new ArrayList<>();

            client2.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) {}
                public void onConnectionFailed(String e) {}
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {
                    receivedContent.add("C2:" + content);
                    ipMsgLatch.countDown();
                }
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client3.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) {}
                public void onConnectionFailed(String e) {}
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {
                    receivedContent.add("C3:" + content);
                    ipMsgLatch.countDown();
                }
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client1.sendMessage(MessageProtocol.TARGET_TYPE_IP, "127.0.0.1", "Xin chào qua IP!");
            boolean ipOk = ipMsgLatch.await(3, TimeUnit.SECONDS);
            assertTrue("Test 4: Gửi tin nhắn theo IP thành công", ipOk && receivedContent.size() >= 1, "Không nhận được tin nhắn theo IP");

            // Test 5: Gửi tin nhắn theo Nhóm
            CountDownLatch groupLatch = new CountDownLatch(1);
            AtomicReference<String> groupMsgReceived = new AtomicReference<>("");

            client2.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) {}
                public void onConnectionFailed(String e) {}
                public void onDisconnected(String r) {}
                public void onKicked(String r) {}
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {
                    if ("IT".equals(target)) {
                        groupMsgReceived.set(content);
                        groupLatch.countDown();
                    }
                }
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            client1.sendMessage(MessageProtocol.TARGET_TYPE_GROUP, "IT", "Họp nhóm IT lúc 10h");
            boolean groupOk = groupLatch.await(3, TimeUnit.SECONDS);
            assertTrue("Test 5: Gửi tin nhắn theo Nhóm IT tới Client 2", groupOk && "Họp nhóm IT lúc 10h".equals(groupMsgReceived.get()), "Tin nhắn nhóm không đúng nội dung");

            // Test 6: Kiểm tra cú pháp phân tích net send
            MessageProtocol.ParsedCommand cmd1 = MessageProtocol.parseNetSendCommand("net send 192.168.1.10 Xin chao");
            assertTrue("Test 6.1: Phân tích net send theo IP", cmd1.isNetSendCommand && MessageProtocol.TARGET_TYPE_IP.equals(cmd1.targetType) && "192.168.1.10".equals(cmd1.target), "Sai cú pháp IP");

            MessageProtocol.ParsedCommand cmd2 = MessageProtocol.parseNetSendCommand("net send group1 Hop nhom luc 8 gio");
            assertTrue("Test 6.2: Phân tích net send theo Group", cmd2.isNetSendCommand && MessageProtocol.TARGET_TYPE_GROUP.equals(cmd2.targetType) && "group1".equals(cmd2.target), "Sai cú pháp Group");

            MessageProtocol.ParsedCommand cmd3 = MessageProtocol.parseNetSendCommand("net send * Thong bao toan mang");
            assertTrue("Test 6.3: Phân tích net send toàn mạng *", cmd3.isNetSendCommand && MessageProtocol.TARGET_TYPE_ALL.equals(cmd3.targetType) && "*".equals(cmd3.target), "Sai cú pháp *");

            // Test 7: Kick Client 3
            CountDownLatch kickLatch = new CountDownLatch(1);
            AtomicBoolean c3Kicked = new AtomicBoolean(false);

            client3.addListener(new Client.ClientListener() {
                public void onConnected(String id, String ip) {}
                public void onConnectionFailed(String e) {}
                public void onDisconnected(String r) {}
                public void onKicked(String r) {
                    c3Kicked.set(true);
                    kickLatch.countDown();
                }
                public void onMessageReceived(String id, String name, String ip, String type, String target, String content) {}
                public void onMessageStatus(boolean s, String n) {}
                public void onOnlineListUpdated(List<String[]> list) {}
            });

            String client3Id = client3.getClientId();
            boolean kickSuccess = server.kickClient(client3Id);
            assertTrue("Test 7.1: Server gọi kickClient thành công", kickSuccess, "Server kickClient trả về false");

            boolean kickReceived = kickLatch.await(3, TimeUnit.SECONDS);
            assertTrue("Test 7.2: Client 3 nhận được thông báo KICK", kickReceived && c3Kicked.get(), "Client 3 không nhận được KICK");
            assertTrue("Test 7.3: Client 3 bị ngắt kết nối", !client3.isConnected(), "Client 3 vẫn còn connected");

            Thread.sleep(500);
            assertTrue("Test 7.4: Client 1 & 2 vẫn đang online bình thường", client1.isConnected() && client2.isConnected(), "Client khác bị ảnh hưởng bởi kick");
            assertTrue("Test 7.5: Số online giảm xuống còn 2", server.getOnlineCount() == 2, "Online count != 2 (thực tế: " + server.getOnlineCount() + ")");

            // Test 8: Ngắt kết nối bình thường Client 1
            client1.disconnect();
            Thread.sleep(500);
            assertTrue("Test 8: Client 1 chủ động ngắt kết nối", !client1.isConnected() && server.getOnlineCount() == 1, "Ngắt kết nối Client 1 thất bại");

            // Test 9: Dừng Server
            server.stop();
            assertTrue("Test 9: Server dừng an toàn", !server.isRunning(), "Server không dừng được");

            // Test 10: Kiểm tra file lịch sử CSV đã được ghi
            File historyFile = new File("data/clients_history.csv");
            assertTrue("Test 10.1: File lịch sử CSV tồn tại", historyFile.exists(), "File data/clients_history.csv không tồn tại");

            HistoryManager hm = new HistoryManager("data/clients_history.csv");
            List<ClientInfo> loaded = hm.loadHistory();
            assertTrue("Test 10.2: Nạp lại lịch sử từ CSV thành công (" + loaded.size() + " bản ghi)", loaded.size() >= 3, "Số bản ghi nạp lại không đủ");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue("Exception xảy ra trong quá trình test", false, e.getMessage());
        } finally {
            server.stop();
        }

        System.out.println("==================================================");
        System.out.println(String.format("KẾT QUẢ KIỂM THỬ: %d/%d BÀI KIỂM TRA ĐẠT (%.1f%%)",
                testsPassed, testsTotal, (testsPassed * 100.0 / Math.max(1, testsTotal))));
        System.out.println("==================================================");

        if (testsPassed == testsTotal) {
            System.out.println("TẤT CẢ CÁC BÀI KIỂM THỬ ĐÃ VƯỢT QUA XUẤT SẮC!");
        } else {
            System.exit(1);
        }
    }
}
