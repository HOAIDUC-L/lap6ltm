import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xử lý kết nối TCP Socket phía Client.
 * Quản lý gửi nhận tin nhắn ngầm, bắt tay giao thức và callback sự kiện lên GUI.
 */
public class Client {
    public interface ClientListener {
        void onConnected(String clientId, String clientIp);
        void onConnectionFailed(String error);
        void onDisconnected(String reason);
        void onKicked(String reason);
        void onMessageReceived(String senderId, String senderName, String senderIp,
                               String targetType, String target, String content);
        void onMessageStatus(boolean success, String statusNote);
        void onOnlineListUpdated(List<String[]> onlineClients);
    }

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenThread;

    private String clientId;
    private String clientIp;
    private String clientName;
    private String group;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean wasKicked = false;
    private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(ClientListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ClientListener listener) {
        listeners.remove(listener);
    }

    /**
     * Bắt đầu kết nối tới Server
     */
    public void connect(String host, int port, String clientName, String group) {
        if (connected.get()) {
            return;
        }
        this.clientName = clientName;
        this.group = group;
        this.wasKicked = false;

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                connected.set(true);

                // Gửi thông điệp đăng ký ban đầu
                writer.println(MessageProtocol.buildRegister(clientName, group));

                // Bắt đầu luồng lắng nghe tin nhắn từ Server
                listenThread = new Thread(this::listenForServerMessages, "Client-Listen-Thread");
                listenThread.setDaemon(true);
                listenThread.start();

            } catch (Exception e) {
                connected.set(false);
                notifyConnectionFailed("Không thể kết nối tới Server (" + host + ":" + port + "): " + e.getMessage());
            }
        }, "Client-Connect-Thread").start();
    }

    private void listenForServerMessages() {
        try {
            String line;
            while (connected.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith(MessageProtocol.CMD_REGISTER_ACK + "|")) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 3) {
                        this.clientId = parts[1];
                        this.clientIp = parts[2];
                        notifyConnected(clientId, clientIp);
                    }
                } else if (line.startsWith(MessageProtocol.CMD_SYNC_ONLINE_LIST + "|")) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length >= 2) {
                        List<String[]> list = MessageProtocol.parseSyncOnlineList(parts[1]);
                        notifyOnlineListUpdated(list);
                    }
                } else if (line.startsWith(MessageProtocol.CMD_RECV_MSG + "|")) {
                    String[] parts = line.split("\\|", 7);
                    if (parts.length >= 7) {
                        String senderId = parts[1];
                        String senderName = MessageProtocol.decodeBase64(parts[2]);
                        String senderIp = parts[3];
                        String targetType = parts[4];
                        String target = parts[5];
                        String content = MessageProtocol.decodeBase64(parts[6]);
                        notifyMessageReceived(senderId, senderName, senderIp, targetType, target, content);
                    }
                } else if (line.startsWith(MessageProtocol.CMD_MSG_STATUS + "|")) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 3) {
                        boolean success = "SUCCESS".equalsIgnoreCase(parts[1]);
                        String note = MessageProtocol.decodeBase64(parts[2]);
                        notifyMessageStatus(success, note);
                    }
                } else if (line.startsWith(MessageProtocol.CMD_KICK + "|")) {
                    String[] parts = line.split("\\|", 2);
                    String reason = parts.length >= 2 ? MessageProtocol.decodeBase64(parts[1]) : "Bạn đã bị kick khỏi Server";
                    wasKicked = true;
                    notifyKicked(reason);
                    disconnectInternal(false);
                    break;
                } else if (line.equals(MessageProtocol.CMD_SERVER_SHUTDOWN)) {
                    notifyDisconnected("Server đã dừng hoạt động!");
                    disconnectInternal(false);
                    break;
                }
            }
        } catch (SocketException e) {
            if (connected.get() && !wasKicked) {
                notifyDisconnected("Mất kết nối tới Server!");
            }
        } catch (IOException e) {
            if (connected.get() && !wasKicked) {
                notifyDisconnected("Lỗi kết nối: " + e.getMessage());
            }
        } finally {
            disconnectInternal(false);
        }
    }

    /**
     * Gửi tin nhắn tới đối tượng cụ thể (ALL, GROUP, IP)
     */
    public synchronized boolean sendMessage(String targetType, String target, String content) {
        if (!connected.get() || writer == null || content == null || content.trim().isEmpty()) {
            return false;
        }
        try {
            String msg = MessageProtocol.buildSendMessage(targetType, target, content);
            writer.println(msg);
            return !writer.checkError();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Chủ động ngắt kết nối
     */
    public void disconnect() {
        if (connected.get()) {
            try {
                if (writer != null) {
                    writer.println(MessageProtocol.CMD_DISCONNECT);
                }
            } catch (Exception ignored) {}
            disconnectInternal(true);
            notifyDisconnected("Bạn đã chủ động ngắt kết nối");
        }
    }

    private void disconnectInternal(boolean notify) {
        if (connected.compareAndSet(true, false)) {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {}
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void notifyConnected(String id, String ip) {
        for (ClientListener l : listeners) {
            try {
                l.onConnected(id, ip);
            } catch (Exception ignored) {}
        }
    }

    private void notifyConnectionFailed(String error) {
        for (ClientListener l : listeners) {
            try {
                l.onConnectionFailed(error);
            } catch (Exception ignored) {}
        }
    }

    private void notifyDisconnected(String reason) {
        for (ClientListener l : listeners) {
            try {
                l.onDisconnected(reason);
            } catch (Exception ignored) {}
        }
    }

    private void notifyKicked(String reason) {
        for (ClientListener l : listeners) {
            try {
                l.onKicked(reason);
            } catch (Exception ignored) {}
        }
    }

    private void notifyMessageReceived(String senderId, String senderName, String senderIp,
                                       String targetType, String target, String content) {
        for (ClientListener l : listeners) {
            try {
                l.onMessageReceived(senderId, senderName, senderIp, targetType, target, content);
            } catch (Exception ignored) {}
        }
    }

    private void notifyMessageStatus(boolean success, String statusNote) {
        for (ClientListener l : listeners) {
            try {
                l.onMessageStatus(success, statusNote);
            } catch (Exception ignored) {}
        }
    }

    private void notifyOnlineListUpdated(List<String[]> onlineClients) {
        for (ClientListener l : listeners) {
            try {
                l.onOnlineListUpdated(onlineClients);
            } catch (Exception ignored) {}
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean wasKicked() {
        return wasKicked;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getClientName() {
        return clientName;
    }

    public String getGroup() {
        return group;
    }

    /**
     * Điểm chạy khi thực thi java Client
     */
    public static void main(String[] args) {
        ClientGUI.main(args);
    }
}
