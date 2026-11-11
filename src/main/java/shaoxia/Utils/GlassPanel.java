package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;

/**
 * 全局通用的极暗毛玻璃面板容器 (优化对比度版)
 * 专门用于包裹 JTextArea(日志/规则)、JTable(表格) 等组件，统一全局黑雾外观
 */
public class GlassPanel extends JPanel {

    public GlassPanel() {
        this(new BorderLayout());
    }

    public GlassPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 降低白光反光，加深黑底遮罩，让里面的白色文字对比度飙升
        g2.setColor(new Color(255, 255, 255, 10));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);
        g2.setColor(new Color(0, 0, 0, 110)); // 暗度拉满
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);

        // 纯净白霜描边
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);

        g2.dispose();
    }
}