import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xử lý kết nối của một Client riêng biệt trên một luồng tại Server.
 * Chịu trách nhiệm nhận diện gói tin, giải mã và điều phối tới Server.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private BufferedReader reader;
    private PrintWriter writer;
    private ClientInfo clientInfo;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean registered = false;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        boolean abnormal = true;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (!registered) {
                    if (line.startsWith(MessageProtocol.CMD_REGISTER + "|")) {
                        handleRegister(line);
                    } else {
                        server.log("Lỗi giao thức: Client chưa đăng ký nhưng đã gửi: " + line);
                        break;
                    }
                } else {
                    if (line.equals(MessageProtocol.CMD_DISCONNECT)) {
                        abnormal = false;
                        break;
                    } else if (line.startsWith(MessageProtocol.CMD_SEND_MSG + "|")) {
                        handleSendMessage(line);
                    } else {
                        server.log("Lỗi giao thức: Nhận thông điệp không rõ từ " + (clientInfo != null ? clientInfo.getName() : "Unknown") + ": " + line);
                    }
                }
            }
        } catch (SocketException e) {
            abnormal = !closed.get();
        } catch (IOException e) {
            abnormal = !closed.get();
            server.log("Lỗi I/O với Client: " + e.getMessage());
        } finally {
            closeConnection(abnormal);
        }
    }

    private void handleRegister(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length >= 3) {
            String clientName = MessageProtocol.decodeBase64(parts[1]);
            String group = MessageProtocol.decodeBase64(parts[2]);

            // Xác định IP Client từ Socket
            String clientIp = socket.getInetAddress().getHostAddress();
            if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
                clientIp = "127.0.0.1";
            }

            this.clientInfo = server.registerClient(this, clientName, group, clientIp);
            this.registered = true;

            // Gửi ACK về cho Client kèm ID và IP xác định
            sendMessage(MessageProtocol.buildRegisterAck(clientInfo.getId(), clientIp));
        }
    }

    private void handleSendMessage(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length >= 4) {
            String targetType = parts[1];
            String target = parts[2];
            String content = MessageProtocol.decodeBase64(parts[3]);
            server.routeMessage(this, targetType, target, content);
        }
    }

    /**
     * Gửi chuỗi thô qua socket cho Client (Thread-safe).
     */
    public synchronized boolean sendMessage(String message) {
        if (closed.get() || writer == null) {
            return false;
        }
        try {
            writer.println(message);
            return !writer.checkError();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Server chủ động Kick Client này.
     */
    public void kick(String reason) {
        if (closed.get()) return;
        try {
            sendMessage(MessageProtocol.buildKick(reason));
        } catch (Exception ignored) {
        }
        if (clientInfo != null) {
            clientInfo.markKicked();
        }
        closeConnection(false);
    }

    /**
     * Đóng kết nối an toàn, đảm bảo dọn dẹp tài nguyên một lần duy nhất.
     */
    public void closeConnection(boolean abnormal) {
        if (closed.compareAndSet(false, true)) {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {}
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {}

            server.onClientDisconnected(this, abnormal);
        }
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public Socket getSocket() {
        return socket;
    }
}
