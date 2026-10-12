import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Hỗ trợ giao diện Swing hiện đại, tinh gọn và nhất quán.
 * Cung cấp màu sắc, font chữ và các renderer hiển thị đẹp mắt.
 */
public class UITheme {
    // Bảng màu chính
    public static final Color COLOR_PRIMARY = new Color(25, 118, 210);      // Xanh dương đậm
    public static final Color COLOR_PRIMARY_HOVER = new Color(30, 136, 229);
    public static final Color COLOR_SUCCESS = new Color(46, 125, 50);       // Xanh lá
    public static final Color COLOR_SUCCESS_BG = new Color(232, 245, 233);
    public static final Color COLOR_DANGER = new Color(198, 40, 40);        // Đỏ
    public static final Color COLOR_DANGER_BG = new Color(255, 235, 238);
    public static final Color COLOR_WARNING = new Color(239, 108, 0);       // Cam
    public static final Color COLOR_BG = new Color(245, 247, 250);          // Nền xám sáng nhẹ
    public static final Color COLOR_CARD_BG = Color.WHITE;
    public static final Color COLOR_BORDER = new Color(207, 216, 220);      // Viền xám nhạt
    public static final Color COLOR_TEXT_DARK = new Color(38, 50, 56);      // Chữ đen xám
    public static final Color COLOR_TEXT_MUTED = new Color(120, 144, 156);  // Chữ mờ

    // Phông chữ chuẩn
    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 16);
    public static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FONT_REGULAR = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font FONT_STAT_NUMBER = new Font("SansSerif", Font.BOLD, 22);

    /**
     * Tạo nút bấm giao diện phẳng với màu sắc tùy chỉnh
     */
    public static JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(new LineBorder(bg.darker(), 1, true), new EmptyBorder(6, 14, 6, 14)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Tạo thẻ thống kê (Stat Card) hiển thị trên thanh tổng quan
     */
    public static JPanel createStatCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(COLOR_CARD_BG);
        card.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_SMALL);
        titleLabel.setForeground(COLOR_TEXT_MUTED);

        valueLabel.setFont(FONT_STAT_NUMBER);
        valueLabel.setForeground(accentColor);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Tùy chỉnh renderer cho JTable hiển thị trạng thái ONLINE / OFFLINE đẹp mắt
     */
    public static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);

            String status = value != null ? value.toString() : "";
            if ("ONLINE".equalsIgnoreCase(status)) {
                setForeground(COLOR_SUCCESS);
                setFont(FONT_BOLD);
                if (!isSelected) {
                    setBackground(COLOR_SUCCESS_BG);
                }
            } else {
                setForeground(new Color(117, 117, 117));
                setFont(FONT_REGULAR);
                if (!isSelected) {
                    setBackground(new Color(245, 245, 245));
                }
            }
            return c;
        }
    }

    /**
     * Renderer căn giữa nội dung ô bảng
     */
    public static class CenterCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 252, 255));
            }
            return this;
        }
    }

    /**
     * Renderer xen kẽ màu nền dòng cho bảng
     */
    public static class StripeRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 252, 255));
            }
            return this;
        }
    }
}
