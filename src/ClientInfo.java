import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Đại diện cho thông tin một Client trong hệ thống NetSend.
 * Quản lý định danh, địa chỉ mạng, trạng thái hoạt động và lịch sử kết nối.
 */
public class ClientInfo {
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final String id;
    private String name;
    private String ip;
    private String group;
    private LocalDateTime connectedTime;
    private LocalDateTime disconnectedTime;
    private String status; // "ONLINE" hoặc "OFFLINE"
    private int connectionCount;
    private int kickCount;
    private long totalOnlineSeconds; // Tổng thời lượng đã online (tính bằng giây)

    public ClientInfo(String id, String name, String ip, String group, LocalDateTime connectedTime) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.group = group != null && !group.trim().isEmpty() ? group.trim() : "Chung";
        this.connectedTime = connectedTime != null ? connectedTime : LocalDateTime.now();
        this.disconnectedTime = null;
        this.status = "ONLINE";
        this.connectionCount = 1;
        this.kickCount = 0;
        this.totalOnlineSeconds = 0;
    }

    public ClientInfo(String id, String name, String ip, String group,
                      LocalDateTime connectedTime, LocalDateTime disconnectedTime,
                      String status, int connectionCount, int kickCount, long totalOnlineSeconds) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.group = group != null && !group.trim().isEmpty() ? group.trim() : "Chung";
        this.connectedTime = connectedTime;
        this.disconnectedTime = disconnectedTime;
        this.status = status;
        this.connectionCount = connectionCount;
        this.kickCount = kickCount;
        this.totalOnlineSeconds = totalOnlineSeconds;
    }

    public synchronized void markReconnected(LocalDateTime newConnectTime, String newName, String newGroup, String newIp) {
        this.connectedTime = newConnectTime != null ? newConnectTime : LocalDateTime.now();
        this.disconnectedTime = null;
        this.status = "ONLINE";
        this.connectionCount++;
        if (newName != null && !newName.trim().isEmpty()) {
            this.name = newName.trim();
        }
        if (newGroup != null && !newGroup.trim().isEmpty()) {
            this.group = newGroup.trim();
        }
        if (newIp != null && !newIp.trim().isEmpty()) {
            this.ip = newIp.trim();
        }
    }

    public synchronized void markDisconnected() {
        if ("ONLINE".equals(this.status)) {
            LocalDateTime now = LocalDateTime.now();
            this.disconnectedTime = now;
            this.status = "OFFLINE";
            if (this.connectedTime != null) {
                long sessionSeconds = Duration.between(this.connectedTime, now).getSeconds();
                if (sessionSeconds > 0) {
                    this.totalOnlineSeconds += sessionSeconds;
                }
            }
        }
    }

    public synchronized void markKicked() {
        this.kickCount++;
        markDisconnected();
    }

    public synchronized long getCurrentOnlineSeconds() {
        if ("ONLINE".equals(this.status) && this.connectedTime != null) {
            long currentSession = Duration.between(this.connectedTime, LocalDateTime.now()).getSeconds();
            return totalOnlineSeconds + Math.max(0, currentSession);
        }
        return totalOnlineSeconds;
    }

    public synchronized String getFormattedDuration() {
        long seconds = getCurrentOnlineSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public synchronized String getFormattedConnectedTime() {
        return connectedTime != null ? connectedTime.format(FORMATTER) : "-";
    }

    public synchronized String getFormattedDisconnectedTime() {
        return disconnectedTime != null ? disconnectedTime.format(FORMATTER) : "-";
    }

    public String getId() {
        return id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public synchronized String getIp() {
        return ip;
    }

    public synchronized void setIp(String ip) {
        this.ip = ip;
    }

    public synchronized String getGroup() {
        return group;
    }

    public synchronized void setGroup(String group) {
        this.group = group;
    }

    public synchronized LocalDateTime getConnectedTime() {
        return connectedTime;
    }

    public synchronized LocalDateTime getDisconnectedTime() {
        return disconnectedTime;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized boolean isOnline() {
        return "ONLINE".equalsIgnoreCase(status);
    }

    public synchronized int getConnectionCount() {
        return connectionCount;
    }

    public synchronized int getKickCount() {
        return kickCount;
    }

    public synchronized long getTotalOnlineSeconds() {
        return totalOnlineSeconds;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s [%s]", name, ip, group, status);
    }
}
