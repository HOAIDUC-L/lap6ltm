import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lớp điều khiển chính của Server TCP Socket.
 * Quản lý vòng đời ServerSocket, luồng kết nối các Client,
 * định tuyến tin nhắn, kick Client, ghi log và lưu trữ lịch sử.
 */
public class Server {
    public static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public interface ServerListener {
        void onServerStatusChanged(boolean running, String ip, int port);
        void onClientListChanged();
        void onLog(String logEntry);
        void onStatsUpdated(int onlineCount, int totalConnectedCount, int kickedCount);
    }

    private ServerSocket serverSocket;
    private final int port;
    private volatile boolean running = false;
    private Thread acceptThread;
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    // Quản lý các Client đang kết nối: Key là clientId
    private final ConcurrentHashMap<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    // Quản lý toàn bộ lịch sử các Client (cả online và offline): Key là clientId
    private final ConcurrentHashMap<String, ClientInfo> allClientsMap = new ConcurrentHashMap<>();

    private final HistoryManager historyManager;
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private int totalKickedCount = 0;

    public Server(int port) {
        this.port = port;
        this.historyManager = new HistoryManager();
        loadExistingHistory();
    }

    public void addListener(ServerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServerListener listener) {
        listeners.remove(listener);
    }

    private void loadExistingHistory() {
        List<ClientInfo> history = historyManager.loadHistory();
        int maxId = 0;
        for (ClientInfo client : history) {
            allClientsMap.put(client.getId(), client);
            totalKickedCount += client.getKickCount();

            // Tìm số ID lớn nhất để không trùng ID khi cấp mới
            if (client.getId().startsWith("CLI-")) {
                try {
                    int num = Integer.parseInt(client.getId().substring(4));
                    if (num > maxId) maxId = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        idCounter.set(maxId + 1);
    }

    /**
     * Bắt đầu Server lắng nghe tại cổng đã định
     */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server đang chạy!");
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;

            String serverIp = getLocalLanIp();
            log("Server khởi động tại IP " + serverIp + ", cổng " + port);

            notifyServerStatus(true, serverIp, port);
            notifyStatsUpdated();

            acceptThread = new Thread(this::listenForClients, "Server-Accept-Thread");
            acceptThread.start();
        } catch (IOException e) {
            running = false;
            log("Lỗi khởi động Server tại cổng " + port + ": " + e.getMessage());
            notifyServerStatus(false, getLocalLanIp(), port);
            throw e;
        }
    }

    /**
     * Dừng Server an toàn, thông báo tới tất cả Client và dọn dẹp tài nguyên
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;

        log("Đang dừng Server...");

        // Thông báo ngắt kết nối an toàn cho tất cả Client đang online
        for (ClientHandler handler : activeClients.values()) {
            try {
                handler.sendMessage(MessageProtocol.CMD_SERVER_SHUTDOWN);
                handler.closeConnection(false);
            } catch (Exception ignored) {}
        }
        activeClients.clear();

        // Đánh dấu tất cả Client sang OFFLINE
        for (ClientInfo c : allClientsMap.values()) {
            c.markDisconnected();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        // Lưu lịch sử
        historyManager.saveHistory(allClientsMap.values());

        log("Server đã dừng hoàn toàn.");
        notifyServerStatus(false, getLocalLanIp(), port);
        notifyStatsUpdated();
        notifyClientListChanged();
    }

    private void listenForClients() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clientThreadPool.submit(handler);
            } catch (SocketException e) {
                // Thường do ServerSocket.close() khi dừng Server
                break;
            } catch (IOException e) {
                if (running) {
                    log("Lỗi tiếp nhận kết nối mới: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Đăng ký một Client sau khi bắt tay giao thức
     */
    public synchronized ClientInfo registerClient(ClientHandler handler, String clientName, String group, String clientIp) {
        // Kiểm tra xem Client (cùng IP và tên) đã từng có trong lịch sử hay chưa để tái sử dụng hoặc tạo mới
        ClientInfo clientInfo = null;
        for (ClientInfo existing : allClientsMap.values()) {
            if (existing.getName().equalsIgnoreCase(clientName) && existing.getIp().equals(clientIp) && !existing.isOnline()) {
                clientInfo = existing;
                clientInfo.markReconnected(LocalDateTime.now(), clientName, group, clientIp);
                break;
            }
        }

        if (clientInfo == null) {
            String newId = String.format("CLI-%03d", idCounter.getAndIncrement());
            clientInfo = new ClientInfo(newId, clientName, clientIp, group, LocalDateTime.now());
            allClientsMap.put(newId, clientInfo);
        }

        activeClients.put(clientInfo.getId(), handler);

        log("Client " + clientInfo.getName() + " (" + clientInfo.getIp() + ") thuộc nhóm [" + clientInfo.getGroup() + "] đã kết nối [ID: " + clientInfo.getId() + "]");

        historyManager.saveHistory(allClientsMap.values());
        notifyStatsUpdated();
        notifyClientListChanged();
        syncOnlineListToAll();

        return clientInfo;
    }

    /**
     * Xử lý khi Client ngắt kết nối (bình thường hoặc bất thường)
     */
    public synchronized void onClientDisconnected(ClientHandler handler, boolean abnormal) {
        ClientInfo info = handler.getClientInfo();
        if (info != null) {
            activeClients.remove(info.getId());
            info.markDisconnected();

            if (abnormal) {
                log("Client " + info.getName() + " (" + info.getIp() + ") mất kết nối bất thường");
            } else {
                log("Client " + info.getName() + " (" + info.getIp() + ") đã ngắt kết nối");
            }

            historyManager.saveHistory(allClientsMap.values());
            notifyStatsUpdated();
            notifyClientListChanged();
            syncOnlineListToAll();
        }
    }

    /**
     * Server Kick một Client được chỉ định
     */
    public synchronized boolean kickClient(String clientId) {
        ClientHandler handler = activeClients.get(clientId);
        ClientInfo info = allClientsMap.get(clientId);

        if (info == null) {
            return false;
        }

        if (handler != null) {
            totalKickedCount++;
            info.markKicked();
            log("Client " + info.getName() + " (" + info.getIp() + ") đã bị kick khỏi Server");
            handler.kick("Bạn đã bị kick khỏi Server");
            activeClients.remove(clientId);

            historyManager.saveHistory(allClientsMap.values());
            notifyStatsUpdated();
            notifyClientListChanged();
            syncOnlineListToAll();
            return true;
        }
        return false;
    }

    /**
     * Xóa một bản ghi khỏi danh sách lịch sử (chỉ khi Client đang OFFLINE)
     */
    public synchronized boolean removeHistoryRecord(String clientId) {
        ClientInfo info = allClientsMap.get(clientId);
        if (info != null && !info.isOnline()) {
            allClientsMap.remove(clientId);
            historyManager.saveHistory(allClientsMap.values());
            notifyStatsUpdated();
            notifyClientListChanged();
            return true;
        }
        return false;
    }

    /**
     * Xử lý định tuyến tin nhắn từ người gửi đến người nhận phù hợp
     */
    public void routeMessage(ClientHandler senderHandler, String targetType, String target, String content) {
        ClientInfo sender = senderHandler.getClientInfo();
        if (sender == null) return;

        List<ClientHandler> recipients = new ArrayList<>();
        boolean foundTarget = false;
        String errorMessage = null;

        if (MessageProtocol.TARGET_TYPE_ALL.equalsIgnoreCase(targetType) || "*".equals(target)) {
            // Gửi toàn mạng tới tất cả Client online ngoại trừ người gửi
            for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
                if (!entry.getKey().equals(sender.getId())) {
                    recipients.add(entry.getValue());
                }
            }
            if (recipients.isEmpty()) {
                errorMessage = "Không có Client nào khác đang online trên toàn mạng!";
            } else {
                foundTarget = true;
            }
        } else if (MessageProtocol.TARGET_TYPE_GROUP.equalsIgnoreCase(targetType)) {
            // Gửi theo nhóm: các Client cùng nhóm đích ngoại trừ người gửi
            for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
                if (!entry.getKey().equals(sender.getId())) {
                    ClientInfo info = entry.getValue().getClientInfo();
                    if (info != null && info.getGroup().equalsIgnoreCase(target)) {
                        recipients.add(entry.getValue());
                    }
                }
            }
            if (recipients.isEmpty()) {
                errorMessage = "Nhóm [" + target + "] hiện không có Client nào khác online!";
                log("Nhóm [" + target + "] không có Client online (yêu cầu từ Client " + sender.getName() + ")");
            } else {
                foundTarget = true;
            }
        } else if (MessageProtocol.TARGET_TYPE_IP.equalsIgnoreCase(targetType)) {
            // Gửi theo IP: các Client có IP trùng khớp ngoại trừ người gửi
            for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
                if (!entry.getKey().equals(sender.getId())) {
                    ClientInfo info = entry.getValue().getClientInfo();
                    if (info != null && info.getIp().equals(target)) {
                        recipients.add(entry.getValue());
                    }
                }
            }
            if (recipients.isEmpty()) {
                errorMessage = "Không tìm thấy Client nào có địa chỉ IP [" + target + "] đang online!";
                log("Không tìm thấy IP người nhận: " + target + " (yêu cầu từ Client " + sender.getName() + ")");
            } else {
                foundTarget = true;
            }
        } else {
            errorMessage = "Loại đối tượng nhận không hợp lệ: " + targetType;
        }

        // Chuyển tiếp tin nhắn tới danh sách nhận
        if (foundTarget) {
            String forwardMsg = MessageProtocol.buildReceiveMessage(
                    sender.getId(), sender.getName(), sender.getIp(),
                    targetType, target, content
            );
            for (ClientHandler rc : recipients) {
                rc.sendMessage(forwardMsg);
            }

            // Ghi nhật ký tin nhắn gửi thành công
            String targetDesc = MessageProtocol.TARGET_TYPE_ALL.equalsIgnoreCase(targetType) ? "toàn mạng (*)"
                    : (MessageProtocol.TARGET_TYPE_GROUP.equalsIgnoreCase(targetType) ? "nhóm [" + target + "]" : "IP " + target);
            log("Tin nhắn từ " + sender.getName() + " (" + sender.getIp() + ") gửi tới " + targetDesc + " thành công (" + recipients.size() + " người nhận)");

            // Báo thành công cho người gửi
            senderHandler.sendMessage(MessageProtocol.buildMessageStatus(true, "Tin nhắn đã được gửi tới " + targetDesc + " (" + recipients.size() + " người nhận)."));
        } else {
            // Báo lỗi cho người gửi
            senderHandler.sendMessage(MessageProtocol.buildMessageStatus(false, errorMessage != null ? errorMessage : "Không thể gửi tin nhắn!"));
        }
    }

    /**
     * Gửi danh sách Client online tới toàn bộ Client đang kết nối
     */
    private void syncOnlineListToAll() {
        List<String[]> onlineList = new ArrayList<>();
        for (ClientHandler handler : activeClients.values()) {
            ClientInfo info = handler.getClientInfo();
            if (info != null && info.isOnline()) {
                onlineList.add(new String[]{info.getId(), info.getName(), info.getIp(), info.getGroup()});
            }
        }

        String syncMsg = MessageProtocol.buildSyncOnlineList(onlineList);
        for (ClientHandler handler : activeClients.values()) {
            handler.sendMessage(syncMsg);
        }
    }

    /**
     * Ghi nhật ký với thời gian chính xác [dd/MM/yyyy HH:mm:ss]
     */
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);
        String formattedLog = "[" + timestamp + "] " + message;
        System.out.println(formattedLog);
        for (ServerListener l : listeners) {
            try {
                l.onLog(formattedLog);
            } catch (Exception ignored) {}
        }
    }

    private void notifyServerStatus(boolean isRunning, String ip, int currentPort) {
        for (ServerListener l : listeners) {
            try {
                l.onServerStatusChanged(isRunning, ip, currentPort);
            } catch (Exception ignored) {}
        }
    }

    private void notifyClientListChanged() {
        for (ServerListener l : listeners) {
            try {
                l.onClientListChanged();
            } catch (Exception ignored) {}
        }
    }

    private void notifyStatsUpdated() {
        int onlineCount = activeClients.size();
        int totalEver = allClientsMap.size();
        for (ServerListener l : listeners) {
            try {
                l.onStatsUpdated(onlineCount, totalEver, totalKickedCount);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Lấy địa chỉ IP LAN thực tế của máy Server (bỏ qua loopback và virtual)
     */
    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public Collection<ClientInfo> getAllClients() {
        return allClientsMap.values();
    }

    public int getOnlineCount() {
        return activeClients.size();
    }

    public int getTotalClientsCount() {
        return allClientsMap.size();
    }

    public int getTotalKickedCount() {
        return totalKickedCount;
    }

    /**
     * Điểm chạy khi thực thi java Server
     */
    public static void main(String[] args) {
        ServerGUI.main(args);
    }
}
