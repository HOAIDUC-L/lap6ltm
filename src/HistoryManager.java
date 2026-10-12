import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Quản lý lưu trữ và nạp lịch sử kết nối của các Client ra file CSV.
 * Dữ liệu được bảo toàn khi tắt/mở lại Server mà không cần cơ sở dữ liệu.
 */
public class HistoryManager {
    private static final String DEFAULT_FILE_PATH = "data/clients_history.csv";
    private static final String CSV_HEADER = "id,name,ip,group,connectedTime,disconnectedTime,status,connectionCount,kickCount,totalOnlineSeconds";
    private final String filePath;

    public HistoryManager() {
        this(DEFAULT_FILE_PATH);
    }

    public HistoryManager(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Nạp danh sách ClientInfo đã từng lưu trong file CSV.
     * Mọi Client nạp từ lịch sử cũ mặc định ở trạng thái OFFLINE.
     */
    public synchronized List<ClientInfo> loadHistory() {
        List<ClientInfo> list = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            return list;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("id,") || line.equalsIgnoreCase(CSV_HEADER)) {
                        continue; // Bỏ qua tiêu đề
                    }
                }

                ClientInfo client = parseCsvLine(line);
                if (client != null) {
                    list.add(client);
                }
            }
        } catch (Exception e) {
            System.err.println("[HistoryManager] Lỗi khi đọc file lịch sử: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lưu toàn bộ danh sách ClientInfo vào file CSV.
     */
    public synchronized void saveHistory(Collection<ClientInfo> clients) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            writer.println(CSV_HEADER);
            for (ClientInfo client : clients) {
                writer.println(toCsvLine(client));
            }
            writer.flush();
        } catch (Exception e) {
            System.err.println("[HistoryManager] Lỗi khi ghi file lịch sử: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String toCsvLine(ClientInfo c) {
        return String.join(",",
                escapeCsv(c.getId()),
                escapeCsv(c.getName()),
                escapeCsv(c.getIp()),
                escapeCsv(c.getGroup()),
                escapeCsv(c.getConnectedTime() != null ? c.getConnectedTime().format(ClientInfo.FORMATTER) : ""),
                escapeCsv(c.getDisconnectedTime() != null ? c.getDisconnectedTime().format(ClientInfo.FORMATTER) : ""),
                escapeCsv(c.getStatus()),
                String.valueOf(c.getConnectionCount()),
                String.valueOf(c.getKickCount()),
                String.valueOf(c.getTotalOnlineSeconds())
        );
    }

    private ClientInfo parseCsvLine(String line) {
        List<String> tokens = parseCsvTokens(line);
        if (tokens.size() < 10) {
            return null;
        }

        try {
            String id = tokens.get(0);
            String name = tokens.get(1);
            String ip = tokens.get(2);
            String group = tokens.get(3);
            LocalDateTime connectedTime = parseDateTime(tokens.get(4));
            LocalDateTime disconnectedTime = parseDateTime(tokens.get(5));
            String status = "OFFLINE"; // Luôn nạp lại trạng thái OFFLINE khi mở Server
            int connectionCount = Integer.parseInt(tokens.get(7));
            int kickCount = Integer.parseInt(tokens.get(8));
            long totalOnlineSeconds = Long.parseLong(tokens.get(9));

            return new ClientInfo(id, name, ip, group, connectedTime, disconnectedTime, status, connectionCount, kickCount, totalOnlineSeconds);
        } catch (Exception e) {
            System.err.println("[HistoryManager] Không thể parse dòng CSV: " + line + " -> " + e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(str.trim(), ClientInfo.FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private List<String> parseCsvTokens(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        sb.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    tokens.add(sb.toString().trim());
                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }
}
