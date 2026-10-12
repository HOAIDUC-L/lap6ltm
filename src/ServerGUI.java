import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Giao diện quản trị Server NetSend bằng Java Swing.
 * Bao gồm thanh thống kê, bảng quản lý Client đa tính năng và nhật ký tự cuộn.
 */
public class ServerGUI extends JFrame implements Server.ServerListener {
    private Server server;
    private int currentPort = 8888;

    // Stat card labels
    private JLabel lblOnlineCount;
    private JLabel lblTotalConnected;
    private JLabel lblKickedCount;
    private JLabel lblServerStatus;
    private JLabel lblServerIp;
    private JLabel lblServerPort;

    // Server controls
    private JTextField txtPort;
    private JButton btnStartServer;
    private JButton btnStopServer;

    // Table & filter
    private JTable clientTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField txtSearch;
    private JComboBox<String> cbStatusFilter;

    // Action buttons
    private JButton btnKick;
    private JButton btnRefresh;
    private JButton btnDeleteHistory;
    private JButton btnViewDetails;

    // Log area
    private JTextArea txtLog;
    private JButton btnClearLog;

    // Timer cập nhật thời lượng online mỗi giây
    private Timer durationUpdateTimer;

    // Tiêu đề các cột trong bảng
    private static final String[] COLUMN_NAMES = {
            "STT", "ID Client", "Tên Client", "Địa chỉ IP", "Tên nhóm",
            "Thời gian kết nối", "Thời gian ngắt kết nối", "Thời lượng online", "Trạng thái"
    };

    public ServerGUI() {
        super("NetSend Server - Quản trị và giám sát mạng LAN");
        initUI();
        initServer();
        startDurationTimer();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(950, 600));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(UITheme.COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Phía trên: Thống kê tổng quan & Điều khiển Server
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        topPanel.add(createStatsPanel(), BorderLayout.NORTH);
        topPanel.add(createServerControlPanel(), BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 2. Khu vực trung tâm: Bảng Client và Nhật ký hoạt động
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.62);
        splitPane.setDividerSize(6);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        splitPane.setTopComponent(createClientManagementPanel());
        splitPane.setBottomComponent(createLogPanel());

        mainPanel.add(splitPane, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    /**
     * Tạo thanh thẻ thống kê tổng quan (Online, Tổng kết nối, Đã kick, Trạng thái, IP, Cổng)
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 6, 8, 8));
        panel.setOpaque(false);

        lblOnlineCount = new JLabel("0", SwingConstants.CENTER);
        lblTotalConnected = new JLabel("0", SwingConstants.CENTER);
        lblKickedCount = new JLabel("0", SwingConstants.CENTER);
        lblServerStatus = new JLabel("ĐÃ DỪNG", SwingConstants.CENTER);
        lblServerIp = new JLabel(Server.getLocalLanIp(), SwingConstants.CENTER);
        lblServerPort = new JLabel(String.valueOf(currentPort), SwingConstants.CENTER);

        lblServerStatus.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblServerIp.setFont(new Font("SansSerif", Font.BOLD, 14));
        lblServerPort.setFont(new Font("SansSerif", Font.BOLD, 16));

        panel.add(UITheme.createStatCard("CLIENT ONLINE", lblOnlineCount, UITheme.COLOR_SUCCESS));
        panel.add(UITheme.createStatCard("TỔNG TỪNG KẾT NỐI", lblTotalConnected, UITheme.COLOR_PRIMARY));
        panel.add(UITheme.createStatCard("ĐÃ BỊ KICK", lblKickedCount, UITheme.COLOR_DANGER));
        panel.add(UITheme.createStatCard("TRẠNG THÁI SERVER", lblServerStatus, Color.GRAY));
        panel.add(UITheme.createStatCard("IP LAN MÁY SERVER", lblServerIp, UITheme.COLOR_TEXT_DARK));
        panel.add(UITheme.createStatCard("CỔNG LẮNG NGHE", lblServerPort, UITheme.COLOR_TEXT_DARK));

        return panel;
    }

    /**
     * Tạo thanh điều khiển Server (Port, Nút Khởi động, Nút Dừng)
     */
    private JPanel createServerControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);

        JLabel lblPortTitle = new JLabel("Cổng dịch vụ:");
        lblPortTitle.setFont(UITheme.FONT_BOLD);
        leftPanel.add(lblPortTitle);

        txtPort = new JTextField(String.valueOf(currentPort), 6);
        txtPort.setFont(UITheme.FONT_REGULAR);
        leftPanel.add(txtPort);

        btnStartServer = UITheme.createButton("▶ Khởi động Server", UITheme.COLOR_SUCCESS, Color.WHITE);
        btnStartServer.addActionListener(e -> startServer());
        leftPanel.add(btnStartServer);

        btnStopServer = UITheme.createButton("⏹ Dừng Server", UITheme.COLOR_DANGER, Color.WHITE);
        btnStopServer.setEnabled(false);
        btnStopServer.addActionListener(e -> stopServer());
        leftPanel.add(btnStopServer);

        panel.add(leftPanel, BorderLayout.WEST);

        JLabel lblHint = new JLabel("Mặc định cổng 8888. Đảm bảo Firewall mở cổng để máy khác trong LAN kết nối.");
        lblHint.setFont(UITheme.FONT_SMALL);
        lblHint.setForeground(UITheme.COLOR_TEXT_MUTED);
        panel.add(lblHint, BorderLayout.EAST);

        return panel;
    }

    /**
     * Tạo khu vực Bảng quản lý Client kèm tìm kiếm, lọc và các nút chức năng
     */
    private JPanel createClientManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        // Thanh công cụ tìm kiếm và lọc phía trên bảng
        JPanel filterBar = new JPanel(new BorderLayout(8, 0));
        filterBar.setOpaque(false);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchPanel.setOpaque(false);

        JLabel lblSearch = new JLabel("\uD83D\uDD0D Tìm kiếm:");
        lblSearch.setFont(UITheme.FONT_BOLD);
        searchPanel.add(lblSearch);

        txtSearch = new JTextField(16);
        txtSearch.setToolTipText("Tìm theo Tên, ID, IP hoặc Nhóm");
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        searchPanel.add(txtSearch);

        JLabel lblFilter = new JLabel("Lọc trạng thái:");
        lblFilter.setFont(UITheme.FONT_BOLD);
        searchPanel.add(lblFilter);

        cbStatusFilter = new JComboBox<>(new String[]{"Tất cả", "ONLINE", "OFFLINE"});
        cbStatusFilter.setFont(UITheme.FONT_REGULAR);
        cbStatusFilter.addActionListener(e -> applyFilters());
        searchPanel.add(cbStatusFilter);

        filterBar.add(searchPanel, BorderLayout.WEST);

        // Nút chức năng phía bên phải
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionPanel.setOpaque(false);

        btnKick = UITheme.createButton("⛔ Kick Client", UITheme.COLOR_DANGER, Color.WHITE);
        btnKick.addActionListener(e -> kickSelectedClient());
        actionPanel.add(btnKick);

        btnViewDetails = UITheme.createButton("ℹ Xem chi tiết", UITheme.COLOR_PRIMARY, Color.WHITE);
        btnViewDetails.addActionListener(e -> viewClientDetails());
        actionPanel.add(btnViewDetails);

        btnDeleteHistory = UITheme.createButton("🗑 Xóa lịch sử", new Color(120, 144, 156), Color.WHITE);
        btnDeleteHistory.addActionListener(e -> deleteSelectedHistory());
        actionPanel.add(btnDeleteHistory);

        btnRefresh = UITheme.createButton("🔄 Làm mới", new Color(74, 98, 110), Color.WHITE);
        btnRefresh.addActionListener(e -> refreshTableData());
        actionPanel.add(btnRefresh);

        filterBar.add(actionPanel, BorderLayout.EAST);
        panel.add(filterBar, BorderLayout.NORTH);

        // Tạo JTable với Non-editable DefaultTableModel
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        clientTable = new JTable(tableModel);
        clientTable.setFont(UITheme.FONT_REGULAR);
        clientTable.setRowHeight(26);
        clientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientTable.getTableHeader().setFont(UITheme.FONT_HEADER);
        clientTable.getTableHeader().setBackground(new Color(236, 239, 241));
        clientTable.setGridColor(new Color(224, 224, 224));

        // Gán sorter và renderers
        rowSorter = new TableRowSorter<>(tableModel);
        clientTable.setRowSorter(rowSorter);

        // Canh chỉnh các cột
        clientTable.getColumnModel().getColumn(0).setPreferredWidth(45);  // STT
        clientTable.getColumnModel().getColumn(0).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // ID
        clientTable.getColumnModel().getColumn(1).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Tên
        clientTable.getColumnModel().getColumn(2).setCellRenderer(new UITheme.StripeRowRenderer());

        clientTable.getColumnModel().getColumn(3).setPreferredWidth(110); // IP
        clientTable.getColumnModel().getColumn(3).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(4).setPreferredWidth(90);  // Nhóm
        clientTable.getColumnModel().getColumn(4).setCellRenderer(new UITheme.StripeRowRenderer());

        clientTable.getColumnModel().getColumn(5).setPreferredWidth(140); // Thời gian kết nối
        clientTable.getColumnModel().getColumn(5).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(6).setPreferredWidth(140); // Thời gian ngắt kết nối
        clientTable.getColumnModel().getColumn(6).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(7).setPreferredWidth(110); // Thời lượng
        clientTable.getColumnModel().getColumn(7).setCellRenderer(new UITheme.CenterCellRenderer());

        clientTable.getColumnModel().getColumn(8).setPreferredWidth(90);  // Trạng thái
        clientTable.getColumnModel().getColumn(8).setCellRenderer(new UITheme.StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(clientTable);
        scrollPane.setBorder(new LineBorder(UITheme.COLOR_BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tạo khu vực Nhật ký hoạt động (Activity Log)
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(UITheme.COLOR_CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblLogTitle = new JLabel("📜 Nhật ký hoạt động hệ thống");
        lblLogTitle.setFont(UITheme.FONT_HEADER);
        headerPanel.add(lblLogTitle, BorderLayout.WEST);

        btnClearLog = UITheme.createButton("Xóa nhật ký", new Color(144, 164, 174), Color.WHITE);
        btnClearLog.setFont(UITheme.FONT_SMALL);
        btnClearLog.addActionListener(e -> txtLog.setText(""));
        headerPanel.add(btnClearLog, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtLog.setBackground(new Color(250, 250, 250));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(new LineBorder(UITheme.COLOR_BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void initServer() {
        server = new Server(currentPort);
        server.addListener(this);
        refreshTableData();
    }

    private void startDurationTimer() {
        durationUpdateTimer = new Timer(1000, e -> updateOnlineDurations());
        durationUpdateTimer.start();
    }

    private void updateOnlineDurations() {
        if (server == null) return;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String status = (String) tableModel.getValueAt(i, 8);
            if ("ONLINE".equalsIgnoreCase(status)) {
                String id = (String) tableModel.getValueAt(i, 1);
                for (ClientInfo c : server.getAllClients()) {
                    if (c.getId().equals(id)) {
                        tableModel.setValueAt(c.getFormattedDuration(), i, 7);
                        break;
                    }
                }
            }
        }
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1024 || port > 65535) {
                JOptionPane.showMessageDialog(this, "Cổng phải nằm trong khoảng từ 1024 đến 65535!", "Lỗi cổng", JOptionPane.ERROR_MESSAGE);
                return;
            }
            this.currentPort = port;

            if (server != null && server.isRunning()) {
                JOptionPane.showMessageDialog(this, "Server đang chạy!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (server == null || server.getPort() != port) {
                if (server != null) server.removeListener(this);
                server = new Server(port);
                server.addListener(this);
            }

            server.start();

            btnStartServer.setEnabled(false);
            btnStopServer.setEnabled(true);
            txtPort.setEditable(false);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể khởi động Server: " + e.getMessage() + "\n(Có thể cổng đã được sử dụng bởi ứng dụng khác)", "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (server != null && server.isRunning()) {
            int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn dừng Server không?\nTất cả Client đang kết nối sẽ bị ngắt kết nối.", "Xác nhận dừng Server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                server.stop();
                btnStartServer.setEnabled(true);
                btnStopServer.setEnabled(false);
                txtPort.setEditable(true);
            }
        }
    }

    private void kickSelectedClient() {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một Client trong bảng để Kick!", "Chưa chọn Client", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = clientTable.convertRowIndexToModel(selectedRow);
        String clientId = (String) tableModel.getValueAt(modelRow, 1);
        String clientName = (String) tableModel.getValueAt(modelRow, 2);
        String clientIp = (String) tableModel.getValueAt(modelRow, 3);
        String status = (String) tableModel.getValueAt(modelRow, 8);

        if (!"ONLINE".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Client " + clientName + " (" + clientId + ") hiện đang OFFLINE!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn KICK Client [" + clientName + "] (IP: " + clientIp + ", ID: " + clientId + ") khỏi Server?",
                "Xác nhận Kick Client",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean kicked = server.kickClient(clientId);
            if (!kicked) {
                JOptionPane.showMessageDialog(this, "Không thể kick Client. Có thể Client vừa ngắt kết nối.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedHistory() {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bản ghi trong bảng để xóa lịch sử!", "Chưa chọn bản ghi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = clientTable.convertRowIndexToModel(selectedRow);
        String clientId = (String) tableModel.getValueAt(modelRow, 1);
        String clientName = (String) tableModel.getValueAt(modelRow, 2);
        String status = (String) tableModel.getValueAt(modelRow, 8);

        if ("ONLINE".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa bản ghi của Client đang ONLINE! Hãy kick hoặc chờ Client ngắt kết nối trước.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa bản ghi lịch sử của Client [" + clientName + "] (" + clientId + ")?",
                "Xác nhận xóa lịch sử",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            server.removeHistoryRecord(clientId);
        }
    }

    private void viewClientDetails() {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một Client trong bảng để xem chi tiết!", "Chưa chọn Client", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = clientTable.convertRowIndexToModel(selectedRow);
        String clientId = (String) tableModel.getValueAt(modelRow, 1);

        ClientInfo target = null;
        for (ClientInfo c : server.getAllClients()) {
            if (c.getId().equals(clientId)) {
                target = c;
                break;
            }
        }

        if (target == null) return;

        JPanel detailPanel = new JPanel(new GridLayout(10, 2, 8, 8));
        detailPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        detailPanel.add(new JLabel("ID Client:"));
        detailPanel.add(new JLabel(target.getId()));

        detailPanel.add(new JLabel("Tên Client:"));
        detailPanel.add(new JLabel(target.getName()));

        detailPanel.add(new JLabel("Địa chỉ IP:"));
        detailPanel.add(new JLabel(target.getIp()));

        detailPanel.add(new JLabel("Nhóm:"));
        detailPanel.add(new JLabel(target.getGroup()));

        detailPanel.add(new JLabel("Trạng thái hiện tại:"));
        JLabel lblStatus = new JLabel(target.getStatus());
        lblStatus.setForeground(target.isOnline() ? UITheme.COLOR_SUCCESS : Color.GRAY);
        lblStatus.setFont(UITheme.FONT_BOLD);
        detailPanel.add(lblStatus);

        detailPanel.add(new JLabel("Thời gian kết nối gần nhất:"));
        detailPanel.add(new JLabel(target.getFormattedConnectedTime()));

        detailPanel.add(new JLabel("Thời gian ngắt kết nối:"));
        detailPanel.add(new JLabel(target.getFormattedDisconnectedTime()));

        detailPanel.add(new JLabel("Thời lượng hoạt động:"));
        detailPanel.add(new JLabel(target.getFormattedDuration()));

        detailPanel.add(new JLabel("Số lần đã kết nối:"));
        detailPanel.add(new JLabel(String.valueOf(target.getConnectionCount())));

        detailPanel.add(new JLabel("Số lần bị Kick:"));
        detailPanel.add(new JLabel(String.valueOf(target.getKickCount())));

        JOptionPane.showMessageDialog(this, detailPanel, "Chi tiết Client: " + target.getName(), JOptionPane.PLAIN_MESSAGE);
    }

    private void applyFilters() {
        String query = txtSearch.getText().trim();
        String statusFilter = (String) cbStatusFilter.getSelectedItem();

        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (!query.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + PatternQuote(query), 1, 2, 3, 4));
        }

        if (statusFilter != null && !"Tất cả".equals(statusFilter)) {
            filters.add(RowFilter.regexFilter("^" + statusFilter + "$", 8));
        }

        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    private void refreshTableData() {
        if (server == null) return;

        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            int stt = 1;
            for (ClientInfo c : server.getAllClients()) {
                tableModel.addRow(new Object[]{
                        stt++,
                        c.getId(),
                        c.getName(),
                        c.getIp(),
                        c.getGroup(),
                        c.getFormattedConnectedTime(),
                        c.getFormattedDisconnectedTime(),
                        c.getFormattedDuration(),
                        c.getStatus()
                });
            }
            applyFilters();
        });
    }

    // --- Triển khai Server.ServerListener ---

    @Override
    public void onServerStatusChanged(boolean running, String ip, int port) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblServerStatus.setText("ĐANG CHẠY");
                lblServerStatus.setForeground(UITheme.COLOR_SUCCESS);
                lblServerIp.setText(ip);
                lblServerPort.setText(String.valueOf(port));
                btnStartServer.setEnabled(false);
                btnStopServer.setEnabled(true);
                txtPort.setEditable(false);
            } else {
                lblServerStatus.setText("ĐÃ DỪNG");
                lblServerStatus.setForeground(UITheme.COLOR_DANGER);
                btnStartServer.setEnabled(true);
                btnStopServer.setEnabled(false);
                txtPort.setEditable(true);
            }
        });
    }

    @Override
    public void onClientListChanged() {
        refreshTableData();
    }

    @Override
    public void onLog(String logEntry) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(logEntry + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    @Override
    public void onStatsUpdated(int onlineCount, int totalConnectedCount, int kickedCount) {
        SwingUtilities.invokeLater(() -> {
            lblOnlineCount.setText(String.valueOf(onlineCount));
            lblTotalConnected.setText(String.valueOf(totalConnectedCount));
            lblKickedCount.setText(String.valueOf(kickedCount));
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
