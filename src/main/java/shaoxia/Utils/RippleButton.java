package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * 终极波纹按钮：采用现代 Fluent / macOS 中心阻尼微缩放反馈 + RadialGradient 径向水波纹
 * 保持原有 Icon 传入机制，内部采用高质量双三次与区域平均算法确保图标绝对清晰，文字绝对视觉居中
 * （接入全局 60 FPS FrameTicker 统一时钟驱动，彻底杜绝单组件 Timer 争用）
 */
public class RippleButton extends JButton {

    private int mouseX, mouseY;
    private float rippleRadius = 0;
    private float rippleAlpha = 0f;
    private final FrameTicker.FrameListener rippleFrameListener;

    private final Color themeColor;
    private boolean isHover = false;
    private boolean isPressedState = false;

    public RippleButton(String text, Color themeColor) {
        super(text);
        this.themeColor = themeColor;

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFont(FontUtils.getChineseFont(Font.BOLD, 20));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        rippleFrameListener = delta -> {
            float targetRadius = (float) Math.max(getWidth(), getHeight()) * 1.2f;
            rippleRadius += (targetRadius - rippleRadius) * 0.15f + 1.0f;

            if (!isPressedState || rippleRadius > targetRadius * 0.7f) {
                rippleAlpha -= 0.03f;
            }

            if (rippleAlpha <= 0f) {
                rippleAlpha = 0f;
                repaint();
                return false; // 动画完成，自动从全局 FrameTicker 注销
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
                    rippleRadius = 10;
                    rippleAlpha = 0.32f;
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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();
        int arc = 25;

        // 1. 绘制固定外框底色
        int alpha = isPressedState ? 210 : (isHover ? 185 : 140);
        g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), alpha));
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        // 2. 将所有内部光影、波纹、图标严格限制在圆角矩形 Clip 内部
        Shape oldClip = g2.getClip();
        Shape clipShape = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);
        g2.clip(clipShape);

        // 悬停时的顶部微弱高光质感
        if (isHover && !isPressedState) {
            g2.setColor(new Color(255, 255, 255, 12));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
        }

        // 3. 绘制水波纹动画
        if (rippleAlpha > 0 && rippleRadius > 0) {
            int r = (int) rippleRadius;
            int currentAlpha = Math.max(0, Math.min(255, (int) (rippleAlpha * 255)));

            RadialGradientPaint rgp = new RadialGradientPaint(
                    new Point(mouseX, mouseY),
                    r,
                    new float[]{0.0f, 0.7f, 1.0f},
                    new Color[]{
                            new Color(255, 255, 255, currentAlpha),
                            new Color(255, 255, 255, (int) (currentAlpha * 0.55)),
                            new Color(255, 255, 255, 0)
                    }
            );
            g2.setPaint(rgp);
            g2.fillOval(mouseX - r, mouseY - r, r * 2, r * 2);
        }

        // 4. 中心阻尼微缩放绘制图标与文字（悬停 1.07x，按压 0.90x）
        double contentScale = isPressedState ? 0.90 : (isHover ? 1.07 : 1.0);

        Icon icon = getIcon();
        if (icon != null) {
            int iw = icon.getIconWidth();
            int ih = icon.getIconHeight();

            // 根据当前缩放比例计算目标尺寸，并对齐偶数消除亚像素模糊
            int targetW = (int) Math.round(iw * contentScale);
            int targetH = (int) Math.round(ih * contentScale);
            if (targetW % 2 != 0) targetW++;
            if (targetH % 2 != 0) targetH++;

            int iconX = (w - targetW) / 2;
            int iconY = (h - targetH) / 2;

            // 如果图标来自 ImageIcon，直接走高质量 BICUBIC 缩放绘制，彻底绕开模糊的最近邻插值
            if (icon instanceof ImageIcon) {
                Image img = ((ImageIcon) icon).getImage();
                g2.drawImage(img, iconX, iconY, targetW, targetH, null);
            } else {
                // 回退绘制
                AffineTransform oldTx = g2.getTransform();
                if (contentScale != 1.0) {
                    double cx = w / 2.0;
                    double cy = h / 2.0;
                    g2.translate(cx, cy);
                    g2.scale(contentScale, contentScale);
                    g2.translate(-cx, -cy);
                }
                icon.paintIcon(this, g2, (w - iw) / 2, (h - ih) / 2);
                g2.setTransform(oldTx);
            }
        }

        String text = getText();
        if (text != null && !text.isEmpty()) {
            AffineTransform oldTx = g2.getTransform();
            if (contentScale != 1.0) {
                double cx = w / 2.0;
                double cy = h / 2.0;
                g2.translate(cx, cy);
                g2.scale(contentScale, contentScale);
                g2.translate(-cx, -cy);
            }
            // 采用真实字形视觉包围盒绝对居中，彻底消除字形偏位
            Font font = getFont();
            FontUtils.drawCenteredText(g2, text, font, new Color(0, 0, 0, 70), 1, 1, w, h);
            FontUtils.drawCenteredText(g2, text, font, Color.WHITE, 0, 0, w, h);
            g2.setTransform(oldTx);
        }

        g2.setClip(oldClip);

        // 5. 绘制反光白霜边框
        g2.setStroke(new BasicStroke(1.6f));
        int borderAlpha = isPressedState ? 150 : (isHover ? 120 : 65);
        g2.setColor(new Color(255, 255, 255, borderAlpha));
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

        g2.dispose();
    }
}
