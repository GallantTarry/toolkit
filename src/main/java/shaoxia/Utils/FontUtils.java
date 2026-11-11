package shaoxia.Utils;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * 全局统一字体管理调度工具
 * 规范应用内中文字体、英文字体、游戏像素字体、艺术加载字体的统一定义与派生
 */
public class FontUtils {

    private static Font baseChineseFont;
    private static Font basePixelFont;
    private static Font baseEnglishFont;
    private static Font baseGodArtFont;

    private static String chineseFamily = "Noto Sans SC Medium";
    private static String pixelFamily = "Zpix";
    private static String englishFamily = "Inter";
    private static String godArtFamily = "Lavishly Yours";

    static {
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("sun.awt.disablegrab", "true");
        System.setProperty("java.awt.im.style", "on-the-spot");
        loadAllFonts();
    }

    /**
     * 加载并向 JVM 图形环境注册全部核心 TTF 字体
     */
    private static void loadAllFonts() {
        baseChineseFont = loadAndRegister("/fonts/思源黑体简体中文.ttf", "思源黑体", new Font("Microsoft YaHei", Font.PLAIN, 14));
        if (baseChineseFont != null) {
            chineseFamily = baseChineseFont.getFamily();
        }

        basePixelFont = loadAndRegister("/fonts/zpix.ttf", "zpix", new Font("Monospaced", Font.PLAIN, 14));
        if (basePixelFont != null) {
            pixelFamily = basePixelFont.getFamily();
        }

        baseEnglishFont = loadAndRegister("/fonts/Inter.ttf", "Inter", new Font("Arial", Font.PLAIN, 14));
        if (baseEnglishFont != null) {
            englishFamily = baseEnglishFont.getFamily();
        }

        baseGodArtFont = loadAndRegister("/fonts/上帝艺术字体.ttf", "上帝艺术字体", new Font("Georgia", Font.PLAIN, 14));
        if (baseGodArtFont != null) {
            godArtFamily = baseGodArtFont.getFamily();
        }
    }

    private static Font loadAndRegister(String resourcePath, String desc, Font fallback) {
        try (InputStream is = FontUtils.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[FontUtils] 未在类路径找到字体: " + resourcePath + "，使用备用字体");
                return fallback;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);
            return font;
        } catch (Exception e) {
            System.err.println("[FontUtils] 注册字体失败: " + resourcePath + " (" + e.getMessage() + ")");
            return fallback;
        }
    }

    /**
     * 初始化 Swing/FlatLaf 全局默认 UI 字体为思源黑体简体中文
     */
    public static void initGlobalFont() {
        Font defaultChinese = getChineseFont(Font.PLAIN, 14f);
        UIManager.put("defaultFont", defaultChinese);

        FontUIResource fontRes = new FontUIResource(defaultChinese);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource || (key instanceof String && ((String) key).endsWith(".font"))) {
                UIManager.put(key, fontRes);
            }
        }
    }

    // --- 中文字体 (思源黑体简体中文.ttf) ---
    public static Font getChineseFont(int style, float size) {
        if (baseChineseFont != null) {
            // 采用思源黑体 Medium 原生中黑字形，结合加粗派生与字号优化；
            // 笔画粗实饱满、边缘锐利分明，彻底解决在深色背景下字体过细发虚、灰淡模糊的问题。
            int effectiveStyle = Font.BOLD;
            if ((style & Font.ITALIC) != 0) {
                effectiveStyle |= Font.ITALIC;
            }
            float effectiveSize = (style == Font.BOLD) ? (size + 1.0f) : (size + 0.5f);
            return baseChineseFont.deriveFont(effectiveStyle, effectiveSize);
        }
        return new Font("Microsoft YaHei", style, (int) size);
    }

    public static Font getChineseFont(int style, int size) {
        return getChineseFont(style, (float) size);
    }

    // --- 拯救公猪游戏全套字体 (zpix.ttf) ---
    public static Font getPixelFont(int style, float size) {
        if (basePixelFont != null) {
            return basePixelFont.deriveFont(style, size);
        }
        return new Font("Monospaced", style, (int) size);
    }

    public static Font getPixelFont(int style, int size) {
        return getPixelFont(style, (float) size);
    }

    // --- 英文字体 (Inter.ttf) ---
    public static Font getEnglishFont(int style, float size) {
        if (baseEnglishFont != null) {
            return baseEnglishFont.deriveFont(style, size);
        }
        return new Font("Arial", style, (int) size);
    }

    public static Font getEnglishFont(int style, int size) {
        return getEnglishFont(style, (float) size);
    }

    // --- 艺术加载字体 (上帝艺术字体.ttf) ---
    public static Font getGodArtFont(int style, float size) {
        if (baseGodArtFont != null) {
            return baseGodArtFont.deriveFont(style, size);
        }
        return new Font("Georgia", style, (int) size);
    }

    public static Font getGodArtFont(int style, int size) {
        return getGodArtFont(style, (float) size);
    }

    // --- 字体家族名称（供 HTML CSS 渲染） ---
    public static String getChineseFamily() {
        return chineseFamily;
    }

    public static String getPixelFamily() {
        return pixelFamily;
    }

    public static String getEnglishFamily() {
        return englishFamily;
    }

    public static String getGodArtFamily() {
        return godArtFamily;
    }

    /**
     * 在指定宽度和高度区域内，根据字形实际视觉包围盒 (Visual Bounds，用户空间独立于 DPI) 绘制绝对水平与垂直居中的单行文字
     */
    public static void drawCenteredText(Graphics2D g2, String text, Font font, Color color, int width, int height) {
        drawCenteredText(g2, text, font, color, 0, 0, width, height);
    }

    /**
     * 在指定矩形区域内，根据字形实际视觉包围盒 (Visual Bounds，用户空间独立于 DPI) 绘制绝对水平与垂直居中的单行文字
     */
    public static void drawCenteredText(Graphics2D g2, String text, Font font, Color color, int x, int y, int width, int height) {
        if (text == null || text.isEmpty()) return;
        g2.setFont(font);
        g2.setColor(color);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontRenderContext frc = g2.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Rectangle2D vb = gv.getVisualBounds();
        float tx = (float) (x + (width - vb.getWidth()) / 2.0 - vb.getX());
        float ty = (float) (y + (height - vb.getHeight()) / 2.0 - vb.getY());
        g2.drawString(text, tx, ty);
    }
}
