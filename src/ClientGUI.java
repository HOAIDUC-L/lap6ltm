import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Giao diện đồ họa cho Client NetSend bằng Java Swing.
 * Cho phép kết nối tới Server, xem danh sách Client online,
 * gửi tin nhắn theo IP, Nhóm, Toàn mạng và hỗ trợ cú pháp lệnh "net send".
 */
public class ClientGUI extends JFrame implements Client.ClientListener {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Client client;

    // Connection components
    private JTextField txtServerIp;
    private JTextField txtServerPort;
    private JTextField txtClientName;
    private JTextField txtGroup;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JLabel lblStatusBadge;
    private JLabel lblClientInfo;

    // Online client list components
    private JTable onlineTable;
    private DefaultTableModel onlineTableModel;

    // Chat area components
    private JTextPane txtChatHistory;
    private JComboBox<String> cbTargetType;
    private JTextField txtTarget;
    private JTextField txtMessageInput;
    private JButton btnSend;

    public ClientGUI() {
        super("NetSend Client - Ứng dụng gửi nhận tin mạng LAN");
        this.client = new Client();
        this.client.addListener(this);

        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(UITheme.COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Phía trên: Khung kết nối Server
        mainPanel.add(createConnectionPanel(), BorderLayout.NORTH);

        // 2. Khu vực chính: Trái là Danh sách Online, Phải là Khung Chat
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.32);
        splitPane.setDividerSize(6);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        splitPane.setLeftComponent(createOnlineListPanel());
        splitPane.setRightComponent(createChatPanel());

        mainPanel.add(splitPane, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // Sự kiện khi đóng cửa sổ -> Ngắt kết nối an toàn
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (client.isConnected()) {
                    client.disconnect();
                }
            }
        });
    }

    /**
     * Tạo bảng thiết lập kết nối (IP, Cổng, Tên, Nhóm, Nút Kết nối/Ngắt)
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        formPanel.setOpaque(false);

        formPanel.add(new JLabel("IP Server:"));
        txtServerIp = new JTextField("127.0.0.1", 9);
        txtServerIp.setFont(UITheme.FONT_REGULAR);
        formPanel.add(txtServerIp);

        formPanel.add(new JLabel("Cổng:"));
        txtServerPort = new JTextField("8888", 5);
        txtServerPort.setFont(UITheme.FONT_REGULAR);
        formPanel.add(txtServerPort);

        formPanel.add(new JLabel("Tên máy/Client:"));
        String defaultName = "Client-" + (int)(Math.random() * 900 + 100);
        txtClientName = new JTextField(defaultName, 9);
        txtClientName.setFont(UITheme.FONT_REGULAR);
        formPanel.add(txtClientName);

        formPanel.add(new JLabel("Nhóm:"));
        txtGroup = new JTextField("Chung", 7);
        txtGroup.setFont(UITheme.FONT_REGULAR);
        formPanel.add(txtGroup);

        btnConnect = UITheme.createButton("Kết nối", UITheme.COLOR_SUCCESS, Color.WHITE);
        btnConnect.addActionListener(e -> connectToServer());
        formPanel.add(btnConnect);

        btnDisconnect = UITheme.createButton("Ngắt kết nối", UITheme.COLOR_DANGER, Color.WHITE);
        btnDisconnect.setEnabled(false);
        btnDisconnect.addActionListener(e -> disconnectFromServer());
        formPanel.add(btnDisconnect);

        panel.add(formPanel, BorderLayout.WEST);

        // Trạng thái kết nối phía bên phải
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        statusPanel.setOpaque(false);

        lblStatusBadge = new JLabel("● Chưa kết nối");
        lblStatusBadge.setFont(UITheme.FONT_BOLD);
        lblStatusBadge.setForeground(Color.GRAY);
        statusPanel.add(lblStatusBadge);

        lblClientInfo = new JLabel("");
        lblClientInfo.setFont(UITheme.FONT_SMALL);
        lblClientInfo.setForeground(UITheme.COLOR_TEXT_MUTED);
        statusPanel.add(lblClientInfo);

        panel.add(statusPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Tạo panel hiển thị Danh sách Client online
     */
    private JPanel createOnlineListPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel lblTitle = new JLabel("👥 Client đang Online (LAN)");
        lblTitle.setFont(UITheme.FONT_HEADER);
        panel.add(lblTitle, BorderLayout.NORTH);

        String[] headers = {"Tên Client", "IP", "Nhóm", "Trạng thái"};
        onlineTableModel = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        onlineTable = new JTable(onlineTableModel);
        onlineTable.setFont(UITheme.FONT_REGULAR);
        onlineTable.setRowHeight(24);
        onlineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineTable.getTableHeader().setFont(UITheme.FONT_SMALL);
        onlineTable.getColumnModel().getColumn(3).setCellRenderer(new UITheme.StatusCellRenderer());

        // Nhấn vào Client để tự động chọn làm đối tượng gửi tin nhắn
        onlineTable.getSelectionModel().addListSelectionListener(e -> {
            int row = onlineTable.getSelectedRow();
            if (row != -1) {
                String ip = (String) onlineTableModel.getValueAt(row, 1);
                cbTargetType.setSelectedItem("IP");
                txtTarget.setText(ip);
                txtMessageInput.requestFocusInWindow();
            }
        });

        JScrollPane scrollPane = new JScrollPane(onlineTable);
        scrollPane.setBorder(new LineBorder(UITheme.COLOR_BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel lblHint = new JLabel("<html><i>Mẹo: Nhấn vào 1 Client để tự động điền IP gửi tin.</i></html>");
        lblHint.setFont(UITheme.FONT_SMALL);
        lblHint.setForeground(UITheme.COLOR_TEXT_MUTED);
        panel.add(lblHint, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Tạo panel Khung chat và Nhập tin nhắn / Lệnh net send
     */
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel lblTitle = new JLabel("💬 Lịch sử tin nhắn & Nhận thông báo");
        lblTitle.setFont(UITheme.FONT_HEADER);
        panel.add(lblTitle, BorderLayout.NORTH);

        // Khung hiển thị lịch sử tin nhắn
        txtChatHistory = new JTextPane();
        txtChatHistory.setEditable(false);
        txtChatHistory.setFont(UITheme.FONT_REGULAR);
        txtChatHistory.setBackground(new Color(252, 252, 252));

        JScrollPane scrollHistory = new JScrollPane(txtChatHistory);
        scrollHistory.setBorder(new LineBorder(UITheme.COLOR_BORDER, 1));
        panel.add(scrollHistory, BorderLayout.CENTER);

        // Khung soạn và gửi tin nhắn
        JPanel sendContainer = new JPanel(new BorderLayout(6, 6));
        sendContainer.setOpaque(false);

        // Thanh chọn đối tượng gửi
        JPanel targetBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        targetBar.setOpaque(false);

        targetBar.add(new JLabel("Gửi tới:"));

        cbTargetType = new JComboBox<>(new String[]{"Toàn mạng (*)", "Nhóm của tôi", "Nhóm khác", "IP"});
        cbTargetType.setFont(UITheme.FONT_REGULAR);
        cbTargetType.addActionListener(e -> onTargetTypeChanged());
        targetBar.add(cbTargetType);

        txtTarget = new JTextField("*", 14);
        txtTarget.setEnabled(false);
        txtTarget.setFont(UITheme.FONT_REGULAR);
        targetBar.add(txtTarget);

        JLabel lblCommandHint = new JLabel("<html><font color='#78909c'>(Hỗ trợ lệnh: <b>net send {IP|group|*} nội dung</b>)</font></html>");
        lblCommandHint.setFont(UITheme.FONT_SMALL);
        targetBar.add(lblCommandHint);

        sendContainer.add(targetBar, BorderLayout.NORTH);

        // Thanh nhập tin và nút Gửi
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(false);

        txtMessageInput = new JTextField();
        txtMessageInput.setFont(UITheme.FONT_REGULAR);
        txtMessageInput.setEnabled(false);
        txtMessageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });
        inputBar.add(txtMessageInput, BorderLayout.CENTER);

        btnSend = UITheme.createButton("Gửi", UITheme.COLOR_PRIMARY, Color.WHITE);
        btnSend.setEnabled(false);
        btnSend.addActionListener(e -> sendMessage());
        inputBar.add(btnSend, BorderLayout.EAST);

        sendContainer.add(inputBar, BorderLayout.CENTER);
        panel.add(sendContainer, BorderLayout.SOUTH);

        return panel;
    }

    private void onTargetTypeChanged() {
        String selected = (String) cbTargetType.getSelectedItem();
        if ("Toàn mạng (*)".equals(selected)) {
            txtTarget.setText("*");
            txtTarget.setEnabled(false);
        } else if ("Nhóm của tôi".equals(selected)) {
            txtTarget.setText(txtGroup.getText().trim());
            txtTarget.setEnabled(false);
        } else if ("Nhóm khác".equals(selected)) {
            txtTarget.setText("");
            txtTarget.setEnabled(true);
            txtTarget.requestFocusInWindow();
        } else if ("IP".equals(selected)) {
            if ("*".equals(txtTarget.getText().trim()) || txtTarget.getText().trim().equals(txtGroup.getText().trim())) {
                txtTarget.setText("");
            }
            txtTarget.setEnabled(true);
            txtTarget.requestFocusInWindow();
        }
    }

    private void connectToServer() {
        String host = txtServerIp.getText().trim();
        String portStr = txtServerPort.getText().trim();
        String name = txtClientName.getText().trim();
        String group = txtGroup.getText().trim();

        if (host.isEmpty() || portStr.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ IP, Cổng và Tên Client!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            lblStatusBadge.setText("● Đang kết nối...");
            lblStatusBadge.setForeground(UITheme.COLOR_WARNING);
            btnConnect.setEnabled(false);

            client.connect(host, port, name, group);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng phải là số nguyên hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnectFromServer() {
        client.disconnect();
    }

    private void sendMessage() {
        String input = txtMessageInput.getText().trim();
        if (input.isEmpty()) return;

        if (!client.isConnected()) {
            appendStyledMessage("Lỗi: Bạn chưa kết nối tới Server!", UITheme.COLOR_DANGER, false);
            return;
        }

        // 1. Kiểm tra xem người dùng có nhập cú pháp lệnh "net send {target} {message}" hay không
        MessageProtocol.ParsedCommand parsed = MessageProtocol.parseNetSendCommand(input);
        if (parsed.isNetSendCommand) {
            boolean ok = client.sendMessage(parsed.targetType, parsed.target, parsed.message);
            if (ok) {
                String targetDesc = MessageProtocol.TARGET_TYPE_ALL.equals(parsed.targetType) ? "toàn mạng (*)"
                        : (MessageProtocol.TARGET_TYPE_GROUP.equals(parsed.targetType) ? "nhóm [" + parsed.target + "]" : "IP " + parsed.target);
                appendStyledMessage("[" + LocalTime.now().format(TIME_FORMATTER) + "] [Bạn -> " + targetDesc + "]: " + parsed.message, UITheme.COLOR_PRIMARY, true);
                txtMessageInput.setText("");
            }
            return;
        }

        // 2. Nếu là tin nhắn thông thường, lấy target từ giao diện
        String selectedType = (String) cbTargetType.getSelectedItem();
        String targetType;
        String target;

        if ("Toàn mạng (*)".equals(selectedType)) {
            targetType = MessageProtocol.TARGET_TYPE_ALL;
            target = "*";
        } else if ("Nhóm của tôi".equals(selectedType)) {
            targetType = MessageProtocol.TARGET_TYPE_GROUP;
            target = txtGroup.getText().trim();
        } else if ("Nhóm khác".equals(selectedType)) {
            targetType = MessageProtocol.TARGET_TYPE_GROUP;
            target = txtTarget.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên nhóm nhận tin!", "Thiếu nhóm nhận", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else { // "IP"
            targetType = MessageProtocol.TARGET_TYPE_IP;
            target = txtTarget.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP nhận tin!", "Thiếu IP nhận", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        boolean ok = client.sendMessage(targetType, target, input);
        if (ok) {
            String targetDesc = MessageProtocol.TARGET_TYPE_ALL.equals(targetType) ? "toàn mạng (*)"
                    : (MessageProtocol.TARGET_TYPE_GROUP.equals(targetType) ? "nhóm [" + target + "]" : "IP " + target);
            appendStyledMessage("[" + LocalTime.now().format(TIME_FORMATTER) + "] [Bạn -> " + targetDesc + "]: " + input, UITheme.COLOR_PRIMARY, true);
            txtMessageInput.setText("");
        }
    }

    /**
     * Thêm tin nhắn có định dạng và màu sắc vào JTextPane
     */
    private void appendStyledMessage(String message, Color color, boolean isBold) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = txtChatHistory.getStyledDocument();
                Style style = txtChatHistory.addStyle("CustomStyle", null);
                StyleConstants.setForeground(style, color);
                StyleConstants.setBold(style, isBold);
                doc.insertString(doc.getLength(), message + "\n", style);
                txtChatHistory.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // --- Triển khai Client.ClientListener ---

    @Override
    public void onConnected(String clientId, String assignedIp) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText("● Đã kết nối");
            lblStatusBadge.setForeground(UITheme.COLOR_SUCCESS);
            lblClientInfo.setText("[ID: " + clientId + " | IP: " + assignedIp + "]");

            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            txtServerIp.setEditable(false);
            txtServerPort.setEditable(false);
            txtClientName.setEditable(false);
            txtGroup.setEditable(false);

            txtMessageInput.setEnabled(true);
            btnSend.setEnabled(true);
            txtMessageInput.requestFocusInWindow();

            appendStyledMessage("[" + LocalTime.now().format(TIME_FORMATTER) + "] Kết nối thành công tới Server! ID của bạn: " + clientId + ", IP xác định: " + assignedIp, UITheme.COLOR_SUCCESS, true);
        });
    }

    @Override
    public void onConnectionFailed(String error) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText("● Kết nối thất bại");
            lblStatusBadge.setForeground(UITheme.COLOR_DANGER);
            lblClientInfo.setText("");
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            txtServerIp.setEditable(true);
            txtServerPort.setEditable(true);
            txtClientName.setEditable(true);
            txtGroup.setEditable(true);

            JOptionPane.showMessageDialog(this, error, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText("● Đã ngắt kết nối");
            lblStatusBadge.setForeground(Color.GRAY);
            lblClientInfo.setText("");

            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            txtServerIp.setEditable(true);
            txtServerPort.setEditable(true);
            txtClientName.setEditable(true);
            txtGroup.setEditable(true);

            txtMessageInput.setEnabled(false);
            btnSend.setEnabled(false);
            onlineTableModel.setRowCount(0);

            appendStyledMessage("[" + LocalTime.now().format(TIME_FORMATTER) + "] " + reason, Color.GRAY, false);
        });
    }

    @Override
    public void onKicked(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText("● BỊ KICK KHỎI SERVER");
            lblStatusBadge.setForeground(UITheme.COLOR_DANGER);
            lblClientInfo.setText("");

            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            txtServerIp.setEditable(true);
            txtServerPort.setEditable(true);
            txtClientName.setEditable(true);
            txtGroup.setEditable(true);

            txtMessageInput.setEnabled(false);
            btnSend.setEnabled(false);
            onlineTableModel.setRowCount(0);

            appendStyledMessage("[" + LocalTime.now().format(TIME_FORMATTER) + "] BẠN ĐÃ BỊ KICK KHỎI SERVER! (" + reason + ")", UITheme.COLOR_DANGER, true);

            JOptionPane.showMessageDialog(this,
                    "Bạn đã bị quản trị viên KICK khỏi Server!\nLý do: " + reason + "\n\n(Bạn sẽ không tự động kết nối lại. Hãy nhấn 'Kết nối' nếu muốn tham gia lại).",
                    "Bị Kick khỏi Server",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    @Override
    public void onMessageReceived(String senderId, String senderName, String senderIp,
                                  String targetType, String target, String content) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(TIME_FORMATTER);
            String scope = MessageProtocol.TARGET_TYPE_ALL.equalsIgnoreCase(targetType) ? "[Toàn mạng]"
                    : (MessageProtocol.TARGET_TYPE_GROUP.equalsIgnoreCase(targetType) ? "[Nhóm " + target + "]" : "[Riêng]");

            // Định dạng: [21:30:15] [192.168.1.10] Duc-PC: Xin chao moi nguoi!
            String formatted = String.format("[%s] [%s] %s %s: %s", time, senderIp, senderName, scope, content);
            appendStyledMessage(formatted, new Color(21, 101, 192), false);
        });
    }

    @Override
    public void onMessageStatus(boolean success, String statusNote) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(TIME_FORMATTER);
            if (!success) {
                appendStyledMessage("[" + time + "] [Lỗi gửi tin]: " + statusNote, UITheme.COLOR_DANGER, true);
            }
        });
    }

    @Override
    public void onOnlineListUpdated(List<String[]> onlineClients) {
        SwingUtilities.invokeLater(() -> {
            onlineTableModel.setRowCount(0);
            for (String[] c : onlineClients) {
                onlineTableModel.addRow(new Object[]{c[1], c[2], c[3], "ONLINE"});
            }
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}
