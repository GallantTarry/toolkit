package shaoxia.ui.components;

import shaoxia.Utils.FontUtils;

import shaoxia.MainLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/**
 * 极其精致的现代化圆角微透托盘悬浮窗菜单 (替代传统老旧 PopupMenu)
 * 继承自无主 JDialog (Window.Type.UTILITY)，解决主窗口隐藏到托盘后 JWindow 无法获取焦点导致点击桌面不关闭的 Bug
 */
public class ModernTrayMenu extends JDialog {

    public ModernTrayMenu(Window owner, MainLauncher launcher) {
        super((Frame) null, false);
        setUndecorated(true);
        setType(Window.Type.UTILITY); // 隐藏任务栏图标，同时具备独立顶层焦点能力
        setAlwaysOnTop(true);
        setFocusableWindowState(true);
        setAutoRequestFocus(true);

        // 关键点：将底座窗口设为完全透明，使抗锯齿圆角边缘绝不漏出黑边
        setBackground(new Color(0, 0, 0, 0));

        getRootPane().setOpaque(false);
        ((JComponent) getContentPane()).setOpaque(false);

        // 主绘图面板
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制卡片底色：采用高级半透明暗色系，与 FlatLaf 完美融合
                g2.setColor(new Color(32, 33, 38, 245));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16); // 优雅的 16px 大圆角

                // 绘制极细的高光微透微边框，极富精致感
                g2.setStroke(new BasicStroke(1.0f));
                g2.setColor(new Color(255, 255, 255, 35));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setFocusable(true);

        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(3, 4, 3, 4);

        // 顶层状态小标题
        JLabel titleLabel = new JLabel("ToolKit");
        titleLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
        titleLabel.setForeground(new Color(140, 145, 155));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0;
        mainPanel.add(titleLabel, gbc);

        // 微弱的分隔线
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 255, 255, 20));
        gbc.gridy = 1;
        mainPanel.add(sep, gbc);

        // 按钮 1：显示面板
        JButton showBtn = createMenuButton("打开主菜单", new Color(255, 255, 255, 25));
        showBtn.addActionListener(e -> {
            setVisible(false);
            try {
                launcher.setOpacity(1.0f);
            } catch (Exception ignored) {
            }
            launcher.setVisible(true);
            launcher.setExtendedState(JFrame.NORMAL);
            launcher.toFront();
        });
        gbc.gridy = 2;
        mainPanel.add(showBtn, gbc);

        // 按钮 2：完全退出 (带柔和警示红底色，联动丝滑划走退出效果)
        JButton exitBtn = createMenuButton("完全的退出", new Color(231, 76, 60, 45));
        exitBtn.setForeground(new Color(241, 105, 105)); // 优雅的淡红警示文字
        exitBtn.addActionListener(e -> {
            setVisible(false);
            launcher.exitApplication();
        });
        gbc.gridy = 3;
        mainPanel.add(exitBtn, gbc);

        add(mainPanel);
        pack(); // 自动计算最佳尺寸

        // 当用户左键点击电脑屏幕其他任何地方时，菜单自动“失焦隐藏”
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}

            @Override
            public void windowLostFocus(WindowEvent e) {
                setVisible(false);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                setVisible(false);
            }
        });
    }

    /**
     * 弹出托盘菜单并强制抢占系统前台焦点，确保主窗口隐藏在托盘时点击外部区域也能正常触发失焦关闭
     */
    public void showMenu(int x, int y) {
        setLocation(x, y);
        setVisible(true);
        toFront();
        requestFocus();
        SwingUtilities.invokeLater(() -> {
            toFront();
            requestFocus();
            getContentPane().requestFocusInWindow();
        });
    }

    /** 统一流水线定制现代菜单按钮 */
    private JButton createMenuButton(String text, Color hoverBg) {
        JButton btn = new JButton(text) {
            private boolean isHover = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { isHover = true; repaint(); }
                    @Override
                    public void mouseExited(MouseEvent e) { isHover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // 悬浮状态下呈现高亮圆角切片
                if (isHover) {
                    g2.setColor(hoverBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                // 采用真实字形视觉包围盒绝对居中，彻底消除字形偏位
                FontUtils.drawCenteredText(g2, getText(), getFont(), getForeground(), getWidth(), getHeight());
                g2.dispose();
            }
        };
        btn.setFont(MainLauncher.MAIN_FONT);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(120, 36));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }
}