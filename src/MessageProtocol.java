import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giao thức truyền tin NetSend qua TCP Socket.
 * Đóng khung thông điệp bằng ký tự xuống dòng ('\n').
 * Các trường dữ liệu phức tạp hoặc tiếng Việt được mã hóa Base64 chuỗi UTF-8.
 */
public class MessageProtocol {
    // Các tiền tố loại thông điệp
    public static final String CMD_REGISTER = "REGISTER";
    public static final String CMD_REGISTER_ACK = "REGISTER_ACK";
    public static final String CMD_SYNC_ONLINE_LIST = "SYNC_ONLINE_LIST";
    public static final String CMD_SEND_MSG = "SEND_MSG";
    public static final String CMD_RECV_MSG = "RECV_MSG";
    public static final String CMD_MSG_STATUS = "MSG_STATUS";
    public static final String CMD_KICK = "KICK";
    public static final String CMD_DISCONNECT = "DISCONNECT";
    public static final String CMD_SERVER_SHUTDOWN = "SERVER_SHUTDOWN";

    // Phân loại đối tượng đích
    public static final String TARGET_TYPE_ALL = "ALL";
    public static final String TARGET_TYPE_GROUP = "GROUP";
    public static final String TARGET_TYPE_IP = "IP";

    // Regex phân tích lệnh "net send {IP | group | *} message"
    private static final Pattern NET_SEND_PATTERN = Pattern.compile("(?i)^\\s*net\\s+send\\s+(\\S+)\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern IPV4_PATTERN = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    public static String encodeBase64(String text) {
        if (text == null) return "";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return "";
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return base64;
        }
    }

    /**
     * Tạo thông điệp đăng ký Client
     */
    public static String buildRegister(String clientName, String group) {
        return CMD_REGISTER + "|" + encodeBase64(clientName) + "|" + encodeBase64(group);
    }

    /**
     * Tạo thông điệp xác nhận đăng ký từ Server
     */
    public static String buildRegisterAck(String clientId, String clientIp) {
        return CMD_REGISTER_ACK + "|" + clientId + "|" + clientIp;
    }

    /**
     * Tạo thông điệp đồng bộ danh sách Client online
     */
    public static String buildSyncOnlineList(List<String[]> clients) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            String[] c = clients.get(i);
            sb.append(c[0]).append(";")
              .append(c[1]).append(";")
              .append(c[2]).append(";")
              .append(c[3]);
            if (i < clients.size() - 1) {
                sb.append("\n");
            }
        }
        return CMD_SYNC_ONLINE_LIST + "|" + encodeBase64(sb.toString());
    }

    /**
     * Giải mã danh sách Client online từ thông điệp đồng bộ
     */
    public static List<String[]> parseSyncOnlineList(String base64Data) {
        List<String[]> list = new ArrayList<>();
        String decoded = decodeBase64(base64Data);
        if (decoded.trim().isEmpty()) {
            return list;
        }
        String[] lines = decoded.split("\n");
        for (String line : lines) {
            String[] parts = line.split(";", 4);
            if (parts.length == 4) {
                list.add(parts);
            }
        }
        return list;
    }

    /**
     * Tạo thông điệp gửi tin nhắn từ Client -> Server
     */
    public static String buildSendMessage(String targetType, String target, String content) {
        return CMD_SEND_MSG + "|" + targetType + "|" + target + "|" + encodeBase64(content);
    }

    /**
     * Tạo thông điệp chuyển tiếp tin nhắn từ Server -> Client nhận
     */
    public static String buildReceiveMessage(String senderId, String senderName, String senderIp,
                                             String targetType, String target, String content) {
        return CMD_RECV_MSG + "|" + senderId + "|" + encodeBase64(senderName) + "|" + senderIp
                + "|" + targetType + "|" + target + "|" + encodeBase64(content);
    }

    /**
     * Tạo thông điệp phản hồi kết quả gửi
     */
    public static String buildMessageStatus(boolean success, String note) {
        return CMD_MSG_STATUS + "|" + (success ? "SUCCESS" : "ERROR") + "|" + encodeBase64(note);
    }

    /**
     * Tạo lệnh kick từ Server
     */
    public static String buildKick(String reason) {
        return CMD_KICK + "|" + encodeBase64(reason);
    }

    /**
     * Cấu trúc chứa thông tin lệnh sau khi phân tích
     */
    public static class ParsedCommand {
        public final boolean isNetSendCommand;
        public final String targetType; // ALL, IP, GROUP
        public final String target;
        public final String message;

        public ParsedCommand(boolean isNetSendCommand, String targetType, String target, String message) {
            this.isNetSendCommand = isNetSendCommand;
            this.targetType = targetType;
            this.target = target;
            this.message = message;
        }
    }

    /**
     * Phân tích văn bản nhập vào xem có phải lệnh "net send {IP | group | *} message" hay không.
     * Nếu không phải cú pháp lệnh, trả về đối tượng có isNetSendCommand = false.
     */
    public static ParsedCommand parseNetSendCommand(String text) {
        if (text == null) {
            return new ParsedCommand(false, null, null, null);
        }
        Matcher matcher = NET_SEND_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            String rawTarget = matcher.group(1).trim();
            String rawMessage = matcher.group(2).trim();

            String targetType;
            String target = rawTarget;
            if ("*".equals(rawTarget)) {
                targetType = TARGET_TYPE_ALL;
            } else if (IPV4_PATTERN.matcher(rawTarget).matches()) {
                targetType = TARGET_TYPE_IP;
            } else {
                targetType = TARGET_TYPE_GROUP;
            }
            return new ParsedCommand(true, targetType, target, rawMessage);
        }
        return new ParsedCommand(false, null, null, text);
    }

    /**
     * Kiểm tra một chuỗi có phải địa chỉ IPv4 hợp lệ hay không.
     */
    public static boolean isIpv4Address(String str) {
        if (str == null) return false;
        return IPV4_PATTERN.matcher(str.trim()).matches();
    }
}
