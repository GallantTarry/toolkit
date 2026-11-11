package shaoxia.modules;

import shaoxia.MainLauncher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class BackgroundPanel extends JPanel {
    // ✨ 一级缓存：原图缓存
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    // ✨ 二级缓存：全局静态共享的缩放后成品缓存
    // 因为是 static 的，所以不管你 new 多少个 CheckerPanel 或 CloudSharePanel，它们都会共用这唯一一张处理好的图！
    private static BufferedImage GLOBAL_SCALED_IMG = null;
    private static String GLOBAL_LAST_PATH = "";
    private static int GLOBAL_LAST_W = -1;
    private static int GLOBAL_LAST_H = -1;
    // ✨ 新增：缓存背景暗度，避免在 paintComponent 中高频读取注册表
    private static int GLOBAL_DARKNESS = 120;

    private String currentPath;
    private BufferedImage originalImg;

    public BackgroundPanel(String defaultPath) {
        setOpaque(false);
        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
        String savedPath = prefs.get("bg_image_path", defaultPath);
        loadImage(savedPath);
    }

    public void loadImage(String path) {
        this.currentPath = path;
        // ✨ 新增：每次加载或切换背景时同步更新一次暗度缓存
        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
        GLOBAL_DARKNESS = prefs.getInt("bg_darkness", 120);

        // ✨ 如果检测到用户在设置里更换了新背景图，则强制清空全局的成品缓存
        if (!path.equals(GLOBAL_LAST_PATH)) {
            GLOBAL_SCALED_IMG = null;
        }

        // 1. 优先从全局缓存中读取原图
        if (IMAGE_CACHE.containsKey(path)) {
            originalImg = IMAGE_CACHE.get(path);
            repaint();
            return;
        }

        try {
            URL imgUrl = getClass().getClassLoader().getResource(path);
            if (imgUrl != null) {
                originalImg = ImageIO.read(imgUrl);
            } else {
                File localFile = new File(path);
                if (localFile.exists() && !localFile.isDirectory()) {
                    originalImg = ImageIO.read(localFile);
                } else {
                    System.err.println("找不到图片资源: " + path);
                    originalImg = null;
                }
            }

            // 2. 加载成功后存入原图缓存
            if (originalImg != null) {
                IMAGE_CACHE.put(path, originalImg);
            }
        } catch (Exception e) {
            System.err.println("背景图片加载或解码异常: " + path);
            e.printStackTrace();
            originalImg = null;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int panelW = getWidth();
        int panelH = getHeight();

        // 防御性拦截：宽高无效时不作处理，避免初始化瞬间报错
        if (panelW <= 0 || panelH <= 0) {
            return;
        }

        if (originalImg != null) {
            Graphics2D g2 = (Graphics2D) g.create();

            // ✨ 终极优化：如果全局缓存为空，或者窗口被用户拖拽改变了大小，才进行重绘
            if (GLOBAL_SCALED_IMG == null || panelW != GLOBAL_LAST_W || panelH != GLOBAL_LAST_H || !currentPath.equals(GLOBAL_LAST_PATH)) {
                int imgW = originalImg.getWidth(this);
                int imgH = originalImg.getHeight(this);

                if (imgW > 0 && imgH > 0) {
                    double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);

                    // 1. 创建共享画布
                    BufferedImage newScaledImg = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D cg2 = newScaledImg.createGraphics();

                    // 2. 开启高质量抗锯齿插值，榨干一次 CPU 性能
                    cg2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    cg2.drawImage(originalImg, 0, 0, drawW, drawH, null);
                    cg2.dispose();

                    // 3. 覆盖到全局静态变量上，造福所有其他继承了这个类的子面板！
                    GLOBAL_SCALED_IMG = newScaledImg;
                    GLOBAL_LAST_W = panelW;
                    GLOBAL_LAST_H = panelH;
                    GLOBAL_LAST_PATH = currentPath;
                }
            }

            // ✨ 高速渲染：直接把全局共享的成品图贴上去，性能开销趋近于 0 毫秒！
            if (GLOBAL_SCALED_IMG != null) {
                int x = (panelW - GLOBAL_SCALED_IMG.getWidth()) / 2;
                int y = (panelH - GLOBAL_SCALED_IMG.getHeight()) / 2;
                g2.drawImage(GLOBAL_SCALED_IMG, x, y, this);
            }

            // ✨ 修改后：直接使用内存缓存的暗度值，0 I/O 开销
            g2.setColor(new Color(0, 0, 0, GLOBAL_DARKNESS));
            g2.fillRect(0, 0, panelW, panelH);

            g2.dispose();
        } else {
            g.setColor(new Color(35, 35, 35));
            g.fillRect(0, 0, panelW, panelH);
        }
    }
}