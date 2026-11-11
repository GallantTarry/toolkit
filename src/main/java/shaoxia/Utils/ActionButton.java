package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 按钮工具类
 * 完美复刻 RenamerPanel 返回按钮样式：胶囊形状 + 黑雾玻璃色 + 悬停明暗反馈 + 水波纹扩散动画 + 绝对视觉居中对齐（DPI 自适应）
 * （接入全局 60 FPS FrameTicker 统一时钟驱动，彻底杜绝单组件 Timer 争用）
 */
public class ActionButton extends JButton {

    // 默认使用 RenamerPanel 中的返回按钮底色
    public static final Color DEFAULT_GLASS_COLOR = new Color(30, 35, 40, 120);

    private Color bgColor;

    // 水波纹交互动效
    private int mouseX, mouseY;
    private float rippleRadius = 0;
    private float rippleAlpha = 0f;
    private final FrameTicker.FrameListener rippleFrameListener;
    private boolean isPressedState = false;
    private boolean isHover = false;

    public ActionButton(String text) {
        this(text, DEFAULT_GLASS_COLOR);
    }

    public ActionButton(String text, Color bgColor) {
        super(text);
        this.bgColor = bgColor;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(Color.WHITE);
        setFont(FontUtils.getChineseFont(Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        rippleFrameListener = delta -> {
            float targetRadius = (float) Math.max(getWidth(), getHeight()) * 1.5f;
            rippleRadius += (targetRadius - rippleRadius) * 0.18f + 1.2f;

            if (!isPressedState || rippleRadius > targetRadius * 0.6f) {
                rippleAlpha -= 0.035f;
            }

            if (rippleAlpha <= 0f) {
                rippleAlpha = 0f;
                repaint();
                return false; // 结束并自动从全局 FrameTicker 注销
            }
            repaint();
            return true;
        };

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isPressedState = true;
                    mouseX = e.getX();
                    mouseY = e.getY();
                    rippleRadius = 6;
                    rippleAlpha = 0.35f;
                    FrameTicker.register(rippleFrameListener);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressedState = false;
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                isHover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHover = false;
                isPressedState = false;
                repaint();
            }
        });
    }

    public void updateColor(Color newColor) {
        this.bgColor = newColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        Color drawColor = bgColor;

        // 悬停/点击时的透明度明暗变化
        if (isPressedState) {
            int newAlpha = Math.min(255, bgColor.getAlpha() + 40);
            drawColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), newAlpha);
        } else if (isHover) {
            int newAlpha = Math.max(0, bgColor.getAlpha() - 40);
            drawColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), newAlpha);
        }

        // 保持原汁原味的胶囊形状 (两端全圆)
        Shape capsule = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, h - 1, h - 1);
        g2.setColor(drawColor);
        g2.fill(capsule);

        // 绘制水波纹动画（严格裁切在胶囊内）
        if (rippleAlpha > 0 && rippleRadius > 0) {
            Shape oldClip = g2.getClip();
            g2.clip(capsule);

            int r = (int) rippleRadius;
            int currentAlpha = Math.max(0, Math.min(255, (int) (rippleAlpha * 255)));

            RadialGradientPaint rgp = new RadialGradientPaint(
                    new Point(mouseX, mouseY),
                    Math.max(1, r),
                    new float[]{0.0f, 0.7f, 1.0f},
                    new Color[]{
                            new Color(255, 255, 255, currentAlpha),
                            new Color(255, 255, 255, (int) (currentAlpha * 0.55)),
                            new Color(255, 255, 255, 0)
                    }
            );
            g2.setPaint(rgp);
            g2.fillOval(mouseX - r, mouseY - r, r * 2, r * 2);

            g2.setClip(oldClip);
        }

        // 高光白霜描边
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, h - 3, h - 3);

        // 绘制图标与文字（基于用户空间视觉包围盒 Visual Bounds 进行绝对水平与垂直居中，DPI 自适应）
        Icon icon = getIcon();
        String text = getText();

        if (icon != null && (text == null || text.isEmpty())) {
            int ix = (w - icon.getIconWidth()) / 2;
            int iy = (h - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, ix, iy);
        } else if (icon != null) {
            int gap = getIconTextGap();
            Font font = getFont();
            g2.setFont(font);
            FontRenderContext frc = g2.getFontRenderContext();
            GlyphVector gv = font.createGlyphVector(frc, text);
            Rectangle2D vb = gv.getVisualBounds();

            float totalW = (float) (icon.getIconWidth() + gap + vb.getWidth());
            float startX = (float) ((w - totalW) / 2.0);
            int iy = (h - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, Math.round(startX), iy);

            float tx = (float) (startX + icon.getIconWidth() + gap - vb.getX());
            float ty = (float) ((h - vb.getHeight()) / 2.0 - vb.getY());
            g2.setColor(isEnabled() ? getForeground() : new Color(255, 255, 255, 100));
            g2.drawString(text, tx, ty);
        } else if (text != null && !text.isEmpty()) {
            Font font = getFont();
            Color textColor = isEnabled() ? getForeground() : new Color(255, 255, 255, 100);
            FontUtils.drawCenteredText(g2, text, font, textColor, w, h);
        }

        g2.dispose();
    }
}
