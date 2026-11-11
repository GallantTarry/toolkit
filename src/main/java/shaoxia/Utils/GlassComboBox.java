package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;

/**
 * 全局统一的高级毛玻璃下拉框
 * 统一规格：15px 圆角 + 6px 边距 + 自动黑雾皮肤
 */
public class GlassComboBox<E> extends JComboBox<E> {

    private final Color glassColor = new Color(30, 35, 40, 120);

    public GlassComboBox(ComboBoxModel<E> model) {
        super(model);
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        setForeground(Color.WHITE);
        setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        setFocusable(false); // 学习 RenamerPanel：默认不获焦，防止首项因默认获焦导致文字发暗

        // 统一 UI 覆盖
        setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton("▼") {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        FontUtils.drawCenteredText(g2, getText(), getFont(), getForeground(), getWidth(), getHeight());
                        g2.dispose();
                    }
                };
                btn.setContentAreaFilled(false);
                btn.setBorderPainted(false);
                btn.setFocusPainted(false);
                btn.setForeground(new Color(255, 255, 255, 180));
                return btn;
            }
            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {}
        });

        // 渲染器保持一致：当前选中展示值 (index == -1) 强制纯白与透明背景，杜绝暗色遮罩
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1) {
                    l.setOpaque(false);
                    l.setForeground(Color.WHITE);
                } else {
                    l.setOpaque(true);
                    l.setBackground(isSelected ? new Color(80, 90, 100) : new Color(40, 45, 50));
                    l.setForeground(Color.WHITE);
                }
                l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return l;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(glassColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
        g2.dispose();
    }
}
