package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;

/**
 * 全局通用的高质感毛玻璃拖拽/监听面板 (内嵌高性能流光引擎)
 * 采用全局统一黑雾色底色。支持外部触发流光拖拽特效，开销极低。
 */
public class GlassDropPanel extends JPanel {

    // 预缓存静态颜色与画笔，避免在 60ms 动画重绘中高频 new 对象引发 GC 抖动
    private static final Color ACTIVE_BG_COLOR = new Color(25, 30, 35, 180);
    private static final Color BORDER_IDLE_COLOR = new Color(255, 255, 255, 60);
    private static final Color DASH_IDLE_COLOR = new Color(255, 255, 255, 120);
    private static final Color TRACK_ACTIVE_COLOR = new Color(255, 255, 255, 40);

    private static final Stroke BORDER_STROKE = new BasicStroke(1.5f);
    private static final Stroke IDLE_DASH_STROKE = new BasicStroke(
            1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{12.0f, 8.0f}, 0.0f
    );

    private static final float[] GRADIENT_FRACTIONS = new float[]{0.0f, 0.45f, 0.5f, 0.55f, 1.0f};
    private static final Color[] GRADIENT_COLORS = new Color[]{
            new Color(255, 255, 255, 0),
            new Color(255, 255, 255, 0),
            new Color(255, 255, 255, 255), // 颗粒绝对高亮纯白中心
            new Color(255, 255, 255, 0),
            new Color(255, 255, 255, 0)
    };

    // 动画状态机与参数
    private boolean isDragActive = false;
    private final Timer animTimer;
    private float angle = 0;
    private float dashPhase = 0;

    public GlassDropPanel() {
        this(new BorderLayout());
    }

    public GlassDropPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        // 默认设置好内边距，防止内部的文字或组件贴边
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ✨ 性能优化：内置 60ms 降频渲染引擎 (约 16 FPS)，大幅降低拖拽时的 CPU 开销
        animTimer = new Timer(60, e -> {
            angle += 0.15f;      // 增大步长，抵消降频带来的迟滞感
            dashPhase += 4.5f;
            repaint();           // 仅重绘当前轻量级面板，不影响全局布局
        });
        animTimer.setCoalesce(true); // 合并积压的 Timer 事件，防止 EDT 繁忙时堆积重绘请求
    }

    /**
     * ✨ 全局调用接口：控制流光引擎的启停
     * 外部模块在 dragEnter 时传入 true，在 dragExit 和 drop 时传入 false
     */
    public void setDragActive(boolean active) {
        if (this.isDragActive == active) return;
        this.isDragActive = active;

        if (active) {
            if (!animTimer.isRunning()) animTimer.start();
        } else {
            if (animTimer.isRunning()) animTimer.stop();
        }
        repaint();
    }

    @Override
    public void removeNotify() {
        // 组件从容器移除或切换页面时，确保停止动画定时器，杜绝后台空转重绘
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        isDragActive = false;
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // ✨ 标准顺序：先调用 super.paintComponent(g)
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 30;

        if (!isDragActive) {
            // ==========================================
            // 状态 1：平时静默状态 (0 性能开销)
            // ==========================================
            g2.setColor(ActionButton.DEFAULT_GLASS_COLOR);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setStroke(BORDER_STROKE);
            g2.setColor(BORDER_IDLE_COLOR);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setStroke(IDLE_DASH_STROKE);
            g2.setColor(DASH_IDLE_COLOR);
            g2.drawRoundRect(8, 8, w - 17, h - 17, arc - 8, arc - 8);

        } else {
            // ==========================================
            // 状态 2：拖拽激活状态 (高性能流光特效)
            // ==========================================
            g2.setColor(ACTIVE_BG_COLOR); // 拖拽时底色稍微加深，突出流光
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            // 画一圈隐约可见的纯白底圈轨道
            g2.setColor(TRACK_ACTIVE_COLOR);
            g2.setStroke(BORDER_STROKE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            int cx = w / 2;
            int cy = h / 2;
            int radius = Math.max(w, h);

            float startX = cx + (float) Math.cos(angle) * radius;
            float startY = cy + (float) Math.sin(angle) * radius;
            float endX = cx + (float) Math.cos(angle + Math.PI) * radius;
            float endY = cy + (float) Math.sin(angle + Math.PI) * radius;

            LinearGradientPaint lgp = new LinearGradientPaint(
                    startX, startY, endX, endY,
                    GRADIENT_FRACTIONS,
                    GRADIENT_COLORS
            );

            g2.setPaint(lgp);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 12f}, dashPhase));

            // 流光收缩 2 个像素，完美嵌入边缘
            g2.drawRoundRect(2, 2, w - 5, h - 5, arc, arc);
        }

        g2.dispose();
    }
}