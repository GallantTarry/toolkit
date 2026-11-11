package shaoxia;

import com.formdev.flatlaf.FlatDarkLaf;
import org.json.JSONArray;
import org.json.JSONObject;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.WindowsTimerLock;
import shaoxia.Utils.FrameTicker;
import shaoxia.Utils.RippleButton;
import shaoxia.modules.*;
import shaoxia.modules.savepig.SavePigPanel;

import shaoxia.ui.components.BlueCat;
import shaoxia.ui.components.ModernTrayMenu;
import shaoxia.ui.components.SettingsDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * 主页面板块 MainLauncher
 * （全模块永驻内存不销毁 + 12模块动态九宫格拖拽定制 + 全量业务状态跨重启持久化 + 4px 纤薄透明玻璃圆弧外边框 + 3D 翻转）
 */
public class MainLauncher extends JFrame {

    static {
        // 激活 Windows 1ms 硬件时钟精度锁（消除 15.6ms 抖动，锁死 60 FPS 16.6ms 帧间隔）
        WindowsTimerLock.lock1ms();

        // 彻底根除 Windows 分层透明窗口与中文输入法（IME）底层 Direct3D 硬件加速互锁崩溃 Bug
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        // 阻断 AWT 原生鼠标抓取与输入法选词候选框冲突
        System.setProperty("sun.awt.disablegrab", "true");
        // 强制使用原位组字渲染（on-the-spot），杜绝创建宿主外独立组字悬浮窗导致的窗口层级撕裂与崩溃
        System.setProperty("java.awt.im.style", "on-the-spot");
    }

    // =========================================================================
    // 全局模块注册表（无论未来开发多少个新模块，只需在此登记即可参与主页拖拽替换）
    // =========================================================================
    public static class ModuleMeta {
        public final String id;
        public final String title;
        public final String iconPath;

        public ModuleMeta(String id, String title, String iconPath) {
            this.id = id;
            this.title = title;
            this.iconPath = iconPath;
        }
    }

    public static final ModuleMeta[] ALL_MODULES = {
            new ModuleMeta("RENAMER", "重命名助手", "/icons/renamer.png"),
            new ModuleMeta("CHECKER", "数据校对", "/icons/checker.png"),
            new ModuleMeta("FINDER", "查找助手", "/icons/finder.png"),
            new ModuleMeta("SCORING", "评分助手", "/icons/scoring.png"),
            new ModuleMeta("REPLACER", "内容替换", "/icons/replacer.png"),
            new ModuleMeta("TREASURE", "解压缩助手", "/icons/archive.png"),
            new ModuleMeta("REGION", "地区助手", "/icons/region.png"),
            new ModuleMeta("CONVERTER", "转换助手", "/icons/converter.png"),
            new ModuleMeta("CLOUD", "传输助手", "/icons/cloud.png"),
            new ModuleMeta("CLIPBOARD", "粘贴板助手", "/icons/clipboard.png"),
            new ModuleMeta("SECRET_CHAT", "大声密谋", "/icons/secret.png"),
            new ModuleMeta("COMPARISON", "公告比对", "/icons/comparison.png"),
            new ModuleMeta("CUSTOM_PLUGIN", "编程助手", "/icons/custommoduleplugin.png")
    };

    public static final String DEFAULT_HOME_MODULES =
            "RENAMER,CHECKER,FINDER,SCORING,REPLACER,TREASURE,REGION,CONVERTER,CLOUD";

    public static ModuleMeta getModuleMeta(String id) {
        if (id == null) return null;
        for (ModuleMeta m : ALL_MODULES) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    public static final int MIN_WIN_WIDTH = 640;
    public static final int MIN_WIN_HEIGHT = 520;

    // 纤薄透明玻璃外边框厚度（4px 薄层包裹）与同心圆角直径
    public static final int GLASS_BORDER_SIZE = 4;
    private static final int INNER_ARC = 25;
    private static final int OUTER_ARC = INNER_ARC + GLASS_BORDER_SIZE * 2;
    private static final int RESIZE_HIT_ZONE = 10;

    private static Image createHighQualityScaledImage(Image src, int w, int h) {
        if (src == null || w <= 0 || h <= 0) return src;
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return resized;
    }

    private BlueCat blueCat;
    private SavePigPanel gamePanel;
    private CardLayout cardLayout;
    private JPanel glassFramePanel;
    private JPanel mainContainer;
    private JPanel mainMenuPanel;
    private JPanel homeGridArea;
    private JLabel homeTitleLabel;
    private CloseButton closeButton;
    private static RandomAccessFile raf;
    private static FileChannel channel;
    private static FileLock lock;

    // 恒定底层壁纸缓存（翻转时固定不动，杜绝壁纸反转与灰色背景）
    private Image sharedBackdropImage;
    private Method paintChildrenMethod;

    // 防止关闭动画重复触发，并保护正常窗口坐标不被划走动画偏移覆盖
    private volatile boolean isAnimatingClose = false;
    private Rectangle lastNormalBounds;

    // 3D 翻转卡片转场动画核心状态
    private boolean isFlipping = false;
    private double flipProgress = 0.0;
    private boolean flipForward = true;
    private BufferedImage flipFromImg = null;
    private BufferedImage flipToImg = null;
    private FrameTicker.FrameListener flipListener = null;
    private String currentCard = "MENU";

    // 全板块单例缓存池，任何板块一旦创建永不销毁
    private final Map<String, JPanel> moduleCache = new HashMap<>();

    // 全局跨重启工作区状态文件与内存镜像
    private final File stateFile;
    private JSONObject workspaceState = new JSONObject();

    public static final Font MAIN_FONT = FontUtils.getChineseFont(Font.PLAIN, 16);
    public static final Font BOLD_FONT = FontUtils.getChineseFont(Font.BOLD, 18);

    public MainLauncher() {
        setTitle("ToolKit");

        setUndecorated(true);
        // 启用窗口级逐像素透明，让外圈 4px 薄玻璃呈现真实半透明质感并彻底消除圆角锯齿
        setBackground(new Color(0, 0, 0, 0));
        setMinimumSize(new Dimension(MIN_WIN_WIDTH, MIN_WIN_HEIGHT));

        try {
            paintChildrenMethod = JComponent.class.getDeclaredMethod("paintChildren", Graphics.class);
            paintChildrenMethod.setAccessible(true);
        } catch (Exception ignored) {
        }

        // 初始化本地状态持久化目录 (~/.toolkit/workspace_state.json)
        File dataDir = new File(System.getProperty("user.home"), ".toolkit");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.stateFile = new File(dataDir, "workspace_state.json");
        loadWorkspaceStateFromFile();

        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
        int savedWidth = prefs.getInt("window_width", workspaceState.optInt("window_width", MIN_WIN_WIDTH));
        int savedHeight = prefs.getInt("window_height", workspaceState.optInt("window_height", MIN_WIN_HEIGHT));
        int winWidth = Math.max(MIN_WIN_WIDTH, savedWidth);
        int winHeight = Math.max(MIN_WIN_HEIGHT, savedHeight);
        setSize(winWidth, winHeight);

        int winX = prefs.getInt("window_x", workspaceState.optInt("window_x", -1));
        int winY = prefs.getInt("window_y", workspaceState.optInt("window_y", -1));
        if (winX != -1 && winY != -1) {
            setLocation(winX, winY);
        } else {
            setLocationRelativeTo(null);
        }
        lastNormalBounds = getBounds();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Image trayIconImage = null;
        try {
            java.net.URL iconUrl = getClass().getResource("/imgs/app.png");
            if (iconUrl != null) {
                BufferedImage masterImg = javax.imageio.ImageIO.read(iconUrl);
                int[] targetSizes = {16, 24, 32, 48, 64, 128, 256};
                java.util.List<Image> iconList = new java.util.ArrayList<>();
                for (int sz : targetSizes) {
                    if (sz == masterImg.getWidth() && sz == masterImg.getHeight()) {
                        iconList.add(masterImg);
                    } else {
                        iconList.add(createHighQualityScaledImage(masterImg, sz, sz));
                    }
                }
                setIconImages(iconList);
            }

            java.net.URL trayUrl = getClass().getResource("/imgs/tray.png");
            if (trayUrl != null) {
                trayIconImage = javax.imageio.ImageIO.read(trayUrl);
            } else if (iconUrl != null) {
                trayIconImage = javax.imageio.ImageIO.read(iconUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        initSystemTray(trayIconImage);

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveWorkspaceStateToFileSync));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleCloseRequest();
            }
        });

        // 外层 4px 纤薄透明玻璃圆弧外边框容器
        glassFramePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth();
                int h = getHeight();
                if (w > GLASS_BORDER_SIZE * 2 && h > GLASS_BORDER_SIZE * 2) {
                    double inX = GLASS_BORDER_SIZE;
                    double inY = GLASS_BORDER_SIZE;
                    double inW = w - GLASS_BORDER_SIZE * 2.0;
                    double inH = h - GLASS_BORDER_SIZE * 2.0;

                    Shape outerShape = new RoundRectangle2D.Double(0.6, 0.6, w - 1.2, h - 1.2, OUTER_ARC, OUTER_ARC);
                    Shape innerCutout = new RoundRectangle2D.Double(inX + 0.4, inY + 0.4, inW - 0.8, inH - 0.8, INNER_ARC, INNER_ARC);

                    Area glassRing = new Area(outerShape);
                    glassRing.subtract(new Area(innerCutout));

                    LinearGradientPaint glassBasePaint = new LinearGradientPaint(
                            0, 0, w, h,
                            new float[]{0.0f, 0.4f, 0.75f, 1.0f},
                            new Color[]{
                                    new Color(240, 248, 255, 78),
                                    new Color(175, 205, 238, 38),
                                    new Color(135, 165, 205, 32),
                                    new Color(220, 240, 255, 68)
                            }
                    );
                    g2.setPaint(glassBasePaint);
                    g2.fill(glassRing);

                    Shape oldClip = g2.getClip();
                    g2.clip(glassRing);
                    g2.setColor(new Color(10, 14, 22, 36));
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.draw(new RoundRectangle2D.Double(
                            inX - 0.8, inY - 0.8, inW + 1.6, inH + 1.6,
                            INNER_ARC + 1.6, INNER_ARC + 1.6
                    ));
                    g2.setClip(oldClip);

                    LinearGradientPaint outerRimPaint = new LinearGradientPaint(
                            0, 0, w, h,
                            new float[]{0.0f, 0.45f, 1.0f},
                            new Color[]{
                                    new Color(255, 255, 255, 190),
                                    new Color(200, 230, 255, 65),
                                    new Color(235, 248, 255, 140)
                            }
                    );
                    g2.setPaint(outerRimPaint);
                    g2.setStroke(new BasicStroke(1.1f));
                    g2.draw(outerShape);
                }

                g2.dispose();
            }
        };
        glassFramePanel.setOpaque(false);
        glassFramePanel.setBorder(BorderFactory.createEmptyBorder(
                GLASS_BORDER_SIZE, GLASS_BORDER_SIZE, GLASS_BORDER_SIZE, GLASS_BORDER_SIZE
        ));
        add(glassFramePanel);

        cardLayout = new CardLayout();

        mainContainer = new JPanel(cardLayout) {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

                int w = getWidth();
                int h = getHeight();
                Shape clipShape = new RoundRectangle2D.Double(0, 0, w, h, INNER_ARC, INNER_ARC);

                Graphics2D gClip = (Graphics2D) g2.create();
                gClip.clip(clipShape);

                Image bgImg = getOrLoadBackdropImage();
                if (bgImg != null) {
                    gClip.drawImage(bgImg, 0, 0, w, h, this);
                } else {
                    gClip.setColor(new Color(12, 16, 26));
                    gClip.fillRect(0, 0, w, h);
                }

                if (isFlipping && flipFromImg != null && flipToImg != null) {
                    render3DFlip(gClip, flipFromImg, flipToImg, w, h, flipProgress, flipForward);
                } else {
                    super.paint(gClip);
                }
                gClip.dispose();

                LinearGradientPaint innerRimPaint = new LinearGradientPaint(
                        0, 0, w, h,
                        new float[]{0.0f, 0.5f, 1.0f},
                        new Color[]{
                                new Color(255, 255, 255, 115),
                                new Color(190, 220, 255, 40),
                                new Color(255, 255, 255, 85)
                        }
                );
                g2.setPaint(innerRimPaint);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1.0, h - 1.0, INNER_ARC, INNER_ARC));

                g2.dispose();
            }
        };
        mainContainer.setOpaque(false);
        glassFramePanel.add(mainContainer, BorderLayout.CENTER);

        mainContainer.setFocusable(true);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                setCursor(Cursor.getDefaultCursor());
            }
        });

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            try {
                if (!(event instanceof MouseEvent) || isAnimatingClose || !isShowing()) return;
                MouseEvent me = (MouseEvent) event;
                int id = me.getID();
                if (id == MouseEvent.MOUSE_EXITED || id == MouseEvent.MOUSE_MOVED) {
                    Point screenPt = me.getLocationOnScreen();
                    Rectangle winBounds = getBounds();
                    boolean outsideWindow = !winBounds.contains(screenPt);
                    boolean insideSafeZone = screenPt.x >= winBounds.x + RESIZE_HIT_ZONE
                            && screenPt.x <= winBounds.x + winBounds.width - RESIZE_HIT_ZONE
                            && screenPt.y >= winBounds.y + RESIZE_HIT_ZONE
                            && screenPt.y <= winBounds.y + winBounds.height - RESIZE_HIT_ZONE;

                    if (outsideWindow) {
                        if (getCursor().getType() != Cursor.DEFAULT_CURSOR) {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    } else if (insideSafeZone && me.getComponent() != mainContainer && me.getComponent() != glassFramePanel) {
                        int curType = getCursor().getType();
                        if (curType >= Cursor.SW_RESIZE_CURSOR && curType <= Cursor.E_RESIZE_CURSOR) {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        // 加载主菜单
        mainMenuPanel = createMainMenu();
        mainContainer.add(mainMenuPanel, "MENU");
        cardLayout.show(mainContainer, "MENU");

        // 后台预热粘贴板与解压缩模块
        SwingUtilities.invokeLater(() -> {
            getOrCreateModule("TREASURE");
            getOrCreateModule("CLIPBOARD");
        });

        blueCat = new BlueCat(this);
        getLayeredPane().add(blueCat, JLayeredPane.POPUP_LAYER);
        blueCat.setCatVisible(prefs.getBoolean("cat_enabled", true));

        // 修复按钮悬停局部重绘 Clip 截断小猫图层与玻璃外框的问题
        RepaintManager.setCurrentManager(new RepaintManager() {
            @Override
            public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
                if (blueCat != null && blueCat.isVisible()
                        && mainMenuPanel != null && c != mainContainer && c != glassFramePanel && c != blueCat
                        && SwingUtilities.isDescendingFrom(c, mainMenuPanel)) {
                    super.addDirtyRegion(glassFramePanel, 0, 0, glassFramePanel.getWidth(), glassFramePanel.getHeight());
                    super.addDirtyRegion(blueCat, 0, 0, blueCat.getWidth(), blueCat.getHeight());
                    return;
                }
                super.addDirtyRegion(c, x, y, w, h);
            }
        });

        closeButton = new CloseButton();
        closeButton.setBounds(winWidth - 45 - GLASS_BORDER_SIZE, 15 + GLASS_BORDER_SIZE, 30, 30);
        closeButton.addActionListener(e -> handleCloseRequest());
        getLayeredPane().add(closeButton, Integer.valueOf(JLayeredPane.POPUP_LAYER + 1));

        // 阻断 3D 翻转卡片期间的用户鼠标穿透与误触
        Component gp = getGlassPane();
        if (gp instanceof JComponent) {
            JComponent glass = (JComponent) gp;
            glass.addMouseListener(new MouseAdapter() {});
            glass.addMouseMotionListener(new MouseAdapter() {});
        }

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (closeButton != null) {
                    closeButton.setBounds(getWidth() - 45 - GLASS_BORDER_SIZE, 15 + GLASS_BORDER_SIZE, 30, 30);
                }
                // 先强制校验子容器布局，再通知小猫重新计算 9 宫格落点
                if (glassFramePanel != null) {
                    glassFramePanel.validate();
                }
                if (blueCat != null) {
                    blueCat.updateBounds();
                }
                if (!isAnimatingClose && isShowing()) {
                    lastNormalBounds = getBounds();
                }
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                if (!isAnimatingClose && isShowing()) {
                    lastNormalBounds = getBounds();
                }
            }
        });

        MouseAdapter dragAndResizeListener = new MouseAdapter() {
            private final int BORDER_THICKNESS = RESIZE_HIT_ZONE;
            private int cursorType = Cursor.DEFAULT_CURSOR;
            private Point startPos = null;
            private Rectangle startBounds = null;

            private int calcCursorType(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), MainLauncher.this);
                int w = getWidth();
                int h = getHeight();
                boolean left = p.x < BORDER_THICKNESS;
                boolean right = p.x > w - BORDER_THICKNESS;
                boolean top = p.y < BORDER_THICKNESS;
                boolean bottom = p.y > h - BORDER_THICKNESS;

                if (top && left) return Cursor.NW_RESIZE_CURSOR;
                if (top && right) return Cursor.NE_RESIZE_CURSOR;
                if (bottom && left) return Cursor.SW_RESIZE_CURSOR;
                if (bottom && right) return Cursor.SE_RESIZE_CURSOR;
                if (top) return Cursor.N_RESIZE_CURSOR;
                if (bottom) return Cursor.S_RESIZE_CURSOR;
                if (left) return Cursor.W_RESIZE_CURSOR;
                if (right) return Cursor.E_RESIZE_CURSOR;
                return Cursor.DEFAULT_CURSOR;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (isAnimatingClose) return;
                cursorType = calcCursorType(e);
                setCursor(Cursor.getPredefinedCursor(cursorType));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (isAnimatingClose || startPos != null) return;
                cursorType = Cursor.DEFAULT_CURSOR;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isAnimatingClose) return;
                cursorType = calcCursorType(e);
                setCursor(Cursor.getPredefinedCursor(cursorType));
                startPos = e.getLocationOnScreen();
                startBounds = getBounds();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isAnimatingClose) return;
                startPos = null;
                startBounds = null;
                cursorType = calcCursorType(e);
                setCursor(Cursor.getPredefinedCursor(cursorType));
                lastNormalBounds = getBounds();
                persistWindowBounds(lastNormalBounds);
                saveWorkspaceStateAsync();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isAnimatingClose || startPos == null || startBounds == null) return;
                Point p = e.getLocationOnScreen();
                int dx = p.x - startPos.x;
                int dy = p.y - startPos.y;

                if (cursorType == Cursor.DEFAULT_CURSOR) {
                    setLocation(startBounds.x + dx, startBounds.y + dy);
                } else {
                    Rectangle bounds = new Rectangle(startBounds);

                    if (cursorType == Cursor.E_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR) {
                        bounds.width += dx;
                    }
                    if (cursorType == Cursor.S_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR) {
                        bounds.height += dy;
                    }
                    if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR) {
                        bounds.width -= dx;
                        bounds.x += dx;
                    }
                    if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR) {
                        bounds.height -= dy;
                        bounds.y += dy;
                    }

                    if (bounds.width < MIN_WIN_WIDTH) {
                        bounds.width = MIN_WIN_WIDTH;
                        if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR) {
                            bounds.x = startBounds.x + startBounds.width - MIN_WIN_WIDTH;
                        }
                    }
                    if (bounds.height < MIN_WIN_HEIGHT) {
                        bounds.height = MIN_WIN_HEIGHT;
                        if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR) {
                            bounds.y = startBounds.y + startBounds.height - MIN_WIN_HEIGHT;
                        }
                    }

                    setBounds(bounds);
                }
                lastNormalBounds = getBounds();
            }
        };

        glassFramePanel.addMouseListener(dragAndResizeListener);
        glassFramePanel.addMouseMotionListener(dragAndResizeListener);
        mainContainer.addMouseListener(dragAndResizeListener);
        mainContainer.addMouseMotionListener(dragAndResizeListener);
    }

    // =========================================================================
    // 主页九宫格板块配置读取、保存与原地动态刷新
    // =========================================================================

    public List<String> getHomeModuleIds() {
        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
        String savedCsv = prefs.get("home_modules", "");

        if (savedCsv == null || savedCsv.trim().isEmpty()) {
            JSONArray jsonArr = workspaceState.optJSONArray("home_modules");
            if (jsonArr != null && jsonArr.length() == 9) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < jsonArr.length(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(jsonArr.optString(i, ""));
                }
                savedCsv = sb.toString();
            } else {
                savedCsv = DEFAULT_HOME_MODULES;
            }
        }

        Set<String> validSet = new LinkedHashSet<>();
        for (String raw : savedCsv.split(",")) {
            String id = raw.trim();
            if (getModuleMeta(id) != null) {
                validSet.add(id);
            }
            if (validSet.size() == 9) break;
        }

        if (validSet.size() < 9) {
            for (ModuleMeta meta : ALL_MODULES) {
                validSet.add(meta.id);
                if (validSet.size() == 9) break;
            }
        }

        return new ArrayList<>(validSet);
    }

    public void updateHomeModules(List<String> newModuleIds) {
        if (newModuleIds == null || newModuleIds.size() != 9) return;

        String csv = String.join(",", newModuleIds);
        try {
            Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
            prefs.put("home_modules", csv);
            prefs.flush();
        } catch (Exception ignored) {
        }

        JSONArray arr = new JSONArray();
        for (String id : newModuleIds) {
            arr.put(id);
        }
        workspaceState.put("home_modules", arr);

        refreshHomeGrid();
        saveWorkspaceStateAsync();
    }

    /**
     * 原地刷新主菜单九宫格按钮，并在布局完成后主动通知小猫更新 9 宫格坐标
     */
    public void refreshHomeGrid() {
        if (homeGridArea == null || homeTitleLabel == null) return;

        homeGridArea.removeAll();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(10, 0, 20, 0);
        gbc.weighty = 0;
        homeGridArea.add(homeTitleLabel, gbc);

        Color glassColor = new Color(30, 35, 40, 120);
        List<String> homeIds = getHomeModuleIds();

        gbc.gridwidth = 1;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        for (int i = 0; i < 9; i++) {
            String modId = homeIds.get(i);
            ModuleMeta meta = getModuleMeta(modId);
            if (meta == null) {
                meta = ALL_MODULES[i % ALL_MODULES.length];
            }
            JButton btn = createModuleButton(meta.id, glassColor, meta.iconPath);
            gbc.gridy = (i / 3) + 1;
            gbc.gridx = i % 3;
            homeGridArea.add(btn, gbc);
        }

        homeGridArea.revalidate();
        homeGridArea.repaint();

        // 【关键补充】：九宫格按钮重新生成后，立即通知小猫刷新按钮缓存与坐标网格
        if (blueCat != null) {
            SwingUtilities.invokeLater(() -> blueCat.updateBounds());
        }

        if (glassFramePanel != null) {
            glassFramePanel.repaint();
        }
    }

    /**
     * 供设置弹窗保存时立即刷新小猫显隐状态与网格坐标
     */
    public void reloadCatSettings() {
        updateCatAndCloseButton("MENU".equals(currentCard));
        if (blueCat != null && blueCat.isVisible()) {
            blueCat.updateBounds();
        }
    }

    public void resetSharedBackdropCache() {
        sharedBackdropImage = null;
        repaint();
    }

    private Image getOrLoadBackdropImage() {
        if (sharedBackdropImage != null) {
            return sharedBackdropImage;
        }

        if (mainMenuPanel != null) {
            Class<?> clazz = mainMenuPanel.getClass();
            while (clazz != null && clazz != JPanel.class && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Image.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(mainMenuPanel);
                            if (val instanceof Image) {
                                sharedBackdropImage = (Image) val;
                                return sharedBackdropImage;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }

        String[] resPaths = {"/bg_imgs/月球与地球.png", "bg_imgs/月球与地球.png", "/resources/bg_imgs/月球与地球.png"};
        for (String p : resPaths) {
            java.net.URL url = getClass().getResource(p);
            if (url == null) {
                url = getClass().getClassLoader().getResource(p);
            }
            if (url != null) {
                sharedBackdropImage = new ImageIcon(url).getImage();
                return sharedBackdropImage;
            }
        }

        String[] filePaths = {"bg_imgs/月球与地球.png", "resources/bg_imgs/月球与地球.png", "src/main/resources/bg_imgs/月球与地球.png"};
        for (String fp : filePaths) {
            File f = new File(fp);
            if (f.exists() && f.isFile()) {
                sharedBackdropImage = new ImageIcon(f.getAbsolutePath()).getImage();
                return sharedBackdropImage;
            }
        }

        return null;
    }

    private void persistWindowBounds(Rectangle bounds) {
        if (bounds == null) return;
        int w = Math.max(MIN_WIN_WIDTH, bounds.width);
        int h = Math.max(MIN_WIN_HEIGHT, bounds.height);
        try {
            Preferences p = Preferences.userNodeForPackage(MainLauncher.class);
            p.putInt("window_x", bounds.x);
            p.putInt("window_y", bounds.y);
            p.putInt("window_width", w);
            p.putInt("window_height", h);
            p.flush();
        } catch (Exception ignored) {
        }
        workspaceState.put("window_x", bounds.x);
        workspaceState.put("window_y", bounds.y);
        workspaceState.put("window_width", w);
        workspaceState.put("window_height", h);
    }

    private void handleCloseRequest() {
        if (isAnimatingClose) return;
        Preferences localPrefs = Preferences.userNodeForPackage(MainLauncher.class);
        boolean shouldExit = (localPrefs.getInt("close_action", 0) == 1);
        animateClose(shouldExit);
    }

    public void exitApplication() {
        if (isVisible()) {
            animateClose(true);
        } else {
            saveWorkspaceStateToFileSync();
            System.exit(0);
        }
    }

    private void animateClose(boolean exitApp) {
        if (isAnimatingClose) return;
        isAnimatingClose = true;

        final Rectangle originBounds = getBounds();
        lastNormalBounds = new Rectangle(originBounds);
        persistWindowBounds(lastNormalBounds);
        saveWorkspaceStateAsync();

        final long durationNs = 220_000_000L;
        final long startTime = System.nanoTime();
        final int slideDistanceX = 110;

        FrameTicker.register(delta -> {
            long elapsed = System.nanoTime() - startTime;
            double progress = Math.min(1.0, (double) elapsed / durationNs);
            double ease = 1.0 - Math.pow(1.0 - progress, 3);

            int newX = originBounds.x + (int) Math.round(slideDistanceX * ease);
            float newOpacity = (float) Math.max(0.0, 1.0 - ease);

            setLocation(newX, originBounds.y);
            try {
                setOpacity(newOpacity);
            } catch (Exception ignored) {
            }

            if (progress >= 1.0) {
                setVisible(false);
                setLocation(originBounds.x, originBounds.y);
                try {
                    setOpacity(1.0f);
                } catch (Exception ignored) {
                }
                isAnimatingClose = false;

                if (exitApp) {
                    saveWorkspaceStateToFileSync();
                    System.exit(0);
                }
                return false; // 动画完成，自动从全局 FrameTicker 注销
            }
            return true;
        });
    }

    private void initSystemTray(Image iconImage) {
        if (!SystemTray.isSupported()) {
            System.err.println("当前系统不支持系统托盘功能，回退为常规关闭模式。");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        if (iconImage == null) {
            iconImage = new ImageIcon(new byte[0]).getImage();
        }

        SystemTray tray = SystemTray.getSystemTray();
        ModernTrayMenu modernMenu = new ModernTrayMenu(this, this);

        TrayIcon trayIcon = new TrayIcon(iconImage, "ToolKit");
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    Point mousePos = MouseInfo.getPointerInfo().getLocation();
                    int x = mousePos.x - modernMenu.getWidth() / 2;
                    int y = mousePos.y - modernMenu.getHeight() - 5;

                    Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                    if (x + modernMenu.getWidth() > screenBounds.x + screenBounds.width) {
                        x = screenBounds.x + screenBounds.width - modernMenu.getWidth() - 5;
                    }
                    if (x < screenBounds.x) x = screenBounds.x + 5;
                    if (y + modernMenu.getHeight() > screenBounds.y + screenBounds.height) {
                        y = screenBounds.y + screenBounds.height - modernMenu.getHeight() - 5;
                    }
                    if (y < screenBounds.y) {
                        y = mousePos.y + 20;
                    }

                    modernMenu.showMenu(x, y);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    modernMenu.setVisible(false);
                    try {
                        setOpacity(1.0f);
                    } catch (Exception ignored) {
                    }
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                    toFront();
                }
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("系统托盘图标添加失败！");
        }
    }

    public void switchPanel(String cardName) {
        if ("MENU".equals(cardName)) {
            showMenu();
        } else {
            openModule(cardName);
        }
    }

    private JPanel getOrCreateModule(String moduleName) {
        JPanel panel = moduleCache.get(moduleName);
        if (panel != null) {
            return panel;
        }

        switch (moduleName) {
            case "RENAMER": panel = new RenamerPanel(this); break;
            case "CHECKER": panel = new CheckerPanel(this); break;
            case "FINDER": panel = new FinderPanel(this); break;
            case "SCORING": panel = new ScoringPanel(this); break;
            case "REPLACER": panel = new ReplacerPanel(this); break;
            case "TREASURE": panel = new ArchivePanel(this); break;
            case "REGION": panel = new RegionFetcherPanel(this); break;
            case "CONVERTER": panel = new ConverterPanel(this); break;
            case "CLOUD": panel = new CloudSharePanel(this); break;
            case "CLIPBOARD": panel = new ClipboardPanel(this); break;
            case "SECRET_CHAT": panel = new SecretChatPanel(this); break;
            case "COMPARISON": panel = new ComparisonPanel(this); break;
            case "CUSTOM_PLUGIN": panel = new CustomModulePluginPanel(this); break;
        }

        if (panel != null) {
            restoreModuleState(moduleName, panel);
            moduleCache.put(moduleName, panel);
            mainContainer.add(panel, moduleName);
        }
        return panel;
    }

    public void openModule(String moduleName) {
        JPanel panel = getOrCreateModule(moduleName);
        if (panel != null) {
            performFlipTransition(moduleName, true);
        }
    }

    public void showMenu() {
        if (isFlipping) return;
        Timer bloomTimer = new Timer(55, evt -> {
            ((Timer) evt.getSource()).stop();
            performFlipTransition("MENU", false);
        });
        bloomTimer.setRepeats(false);
        bloomTimer.start();
        saveWorkspaceStateAsync();
    }

    // =========================================================================
    // 3D 悬浮卡片转场渲染引擎
    // =========================================================================

    private double[] getDeviceScaleFactors() {
        GraphicsConfiguration gc = mainContainer != null ? mainContainer.getGraphicsConfiguration() : null;
        if (gc == null) {
            gc = getGraphicsConfiguration();
        }
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        if (gc != null) {
            AffineTransform tx = gc.getDefaultTransform();
            double sx = tx.getScaleX();
            double sy = tx.getScaleY();
            if (sx > 0 && sy > 0) {
                return new double[]{sx, sy};
            }
        }
        return new double[]{1.0, 1.0};
    }

    private BufferedImage captureContainer(String cardName) {
        int w = mainContainer.getWidth();
        int h = mainContainer.getHeight();
        if (w <= 0 || h <= 0) return null;

        double[] scales = getDeviceScaleFactors();
        double scaleX = scales[0];
        double scaleY = scales[1];

        int physW = Math.max(1, (int) Math.round(w * scaleX));
        int physH = Math.max(1, (int) Math.round(h * scaleY));

        BufferedImage img = new BufferedImage(physW, physH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.scale(scaleX, scaleY);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        for (Component c : mainContainer.getComponents()) {
            if (c.isVisible()) {
                if (c instanceof BackgroundPanel && paintChildrenMethod != null) {
                    try {
                        paintChildrenMethod.invoke(c, g2);
                    } catch (Exception ex) {
                        c.paint(g2);
                    }
                } else {
                    c.paint(g2);
                }
            }
        }

        if ("MENU".equals(cardName) && closeButton != null && closeButton.isVisible()) {
            Graphics2D gBtn = (Graphics2D) g2.create();
            gBtn.translate(closeButton.getX() - GLASS_BORDER_SIZE, closeButton.getY() - GLASS_BORDER_SIZE);
            closeButton.paint(gBtn);
            gBtn.dispose();
        }

        g2.dispose();
        return img;
    }

    public void performFlipTransition(String targetCard, boolean isForward) {
        if (isFlipping) return;
        if (targetCard.equals(currentCard)) {
            updateCatAndCloseButton("MENU".equals(targetCard));
            return;
        }

        int w = mainContainer.getWidth();
        int h = mainContainer.getHeight();
        if (w <= 0 || h <= 0) {
            cardLayout.show(mainContainer, targetCard);
            currentCard = targetCard;
            updateCatAndCloseButton("MENU".equals(targetCard));
            return;
        }

        flipFromImg = captureContainer(currentCard);
        updateCatAndCloseButton(false);

        cardLayout.show(mainContainer, targetCard);
        mainContainer.doLayout();
        mainContainer.validate();

        flipToImg = captureContainer(targetCard);

        if (flipFromImg == null || flipToImg == null) {
            currentCard = targetCard;
            updateCatAndCloseButton("MENU".equals(targetCard));
            mainContainer.repaint();
            return;
        }

        Component gp = getGlassPane();
        if (gp != null) gp.setVisible(true);

        isFlipping = true;
        flipForward = isForward;
        flipProgress = 0.0;
        currentCard = targetCard;

        final long startTime = System.currentTimeMillis();
        final int duration = 360;

        if (flipListener != null) {
            FrameTicker.unregister(flipListener);
            flipListener = null;
        }

        flipListener = delta -> {
            long elapsed = System.currentTimeMillis() - startTime;
            double raw = (double) elapsed / duration;
            if (raw >= 1.0) {
                isFlipping = false;
                if (flipFromImg != null) {
                    flipFromImg.flush();
                    flipFromImg = null;
                }
                if (flipToImg != null) {
                    flipToImg.flush();
                    flipToImg = null;
                }
                if (gp != null) gp.setVisible(false);
                updateCatAndCloseButton("MENU".equals(currentCard));
                mainContainer.repaint();
                flipListener = null;
                return false; // 动画完成，自动从全局 FrameTicker 注销
            }
            flipProgress = (1.0 - Math.cos(raw * Math.PI)) / 2.0;
            mainContainer.repaint();
            return true;
        };
        FrameTicker.register(flipListener);
    }

    private void render3DFlip(Graphics2D g2, BufferedImage from, BufferedImage to, int w, int h, double progress, boolean forward) {
        double totalAngle = progress * Math.PI;
        if (!forward) {
            totalAngle = -totalAngle;
        }

        boolean showingFront = Math.abs(totalAngle) < Math.PI / 2.0;
        BufferedImage currentFace = showingFront ? from : to;
        double faceAngle = showingFront ? totalAngle : (totalAngle > 0 ? totalAngle - Math.PI : totalAngle + Math.PI);

        int srcW = currentFace.getWidth();
        int srcH = currentFace.getHeight();
        if (srcW <= 0 || srcH <= 0 || w <= 0 || h <= 0) return;

        double cosA = Math.cos(faceAngle);
        double sinA = Math.sin(faceAngle);

        float faceAlpha = (float) Math.max(0.0, Math.min(1.0, Math.pow(Math.abs(cosA), 0.42)));
        if (faceAlpha <= 0.01f) return;

        double scaleX = (double) srcW / w;
        double scaleY = (double) srcH / h;

        Graphics2D gPhys = (Graphics2D) g2.create();
        gPhys.scale(1.0 / scaleX, 1.0 / scaleY);
        gPhys.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gPhys.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gPhys.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gPhys.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        gPhys.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, faceAlpha));

        int slices = Math.max(96, Math.min(160, srcW / 8));
        double focalLength = srcW * 1.65;

        int[] destX = new int[slices + 1];
        double[] destYTop = new double[slices + 1];
        double[] destH = new double[slices + 1];

        for (int k = 0; k <= slices; k++) {
            double xMid = k * ((double) srcW / slices) - srcW / 2.0;
            double x3d = xMid * cosA;
            double z3d = xMid * sinA;
            double scale = focalLength / (focalLength + z3d);

            destX[k] = (int) Math.round(srcW / 2.0 + x3d * scale);
            destYTop[k] = (srcH / 2.0) - (srcH / 2.0) * scale;
            destH[k] = srcH * scale;
        }

        for (int i = 0; i < slices; i++) {
            int dx1 = destX[i];
            int dx2 = destX[i + 1];
            if (dx1 == dx2) continue;

            int drawDx1 = Math.min(dx1, dx2);
            int drawDx2 = Math.max(dx1, dx2);

            int sx1 = (int) Math.round(i * ((double) srcW / slices));
            int sx2 = (int) Math.round((i + 1) * ((double) srcW / slices));
            if (sx1 == sx2) continue;

            int dy1 = (int) Math.round((destYTop[i] + destYTop[i + 1]) / 2.0);
            int dy2 = (int) Math.round(dy1 + (destH[i] + destH[i + 1]) / 2.0);

            gPhys.drawImage(currentFace, drawDx1, dy1, drawDx2, dy2, sx1, 0, sx2, srcH, null);
        }

        gPhys.dispose();
    }

    private void updateCatAndCloseButton(boolean isMenu) {
        if (blueCat != null) {
            Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
            boolean isCatEnabled = prefs.getBoolean("cat_enabled", true);
            blueCat.setCatVisible(isMenu && isCatEnabled);
        }
        if (closeButton != null) {
            closeButton.setVisible(isMenu);
        }
    }

    // =========================================================================
    // 全局跨重启状态持久化引擎
    // =========================================================================

    private void loadWorkspaceStateFromFile() {
        if (!stateFile.exists()) return;
        try {
            String json = Files.readString(stateFile.toPath(), StandardCharsets.UTF_8);
            workspaceState = new JSONObject(json);
        } catch (Exception e) {
            System.err.println("读取全局工作区状态失败: " + e.getMessage());
            workspaceState = new JSONObject();
        }
    }

    public void saveWorkspaceStateAsync() {
        Thread.ofVirtual().name("Workspace-State-Saver").start(this::saveWorkspaceStateToFileSync);
    }

    public synchronized void saveWorkspaceStateToFileSync() {
        try {
            Rectangle boundsToSave = (!isAnimatingClose && getWidth() >= MIN_WIN_WIDTH && getHeight() >= MIN_WIN_HEIGHT)
                    ? getBounds()
                    : lastNormalBounds;
            if (boundsToSave != null) {
                persistWindowBounds(boundsToSave);
            }

            JSONArray homeArr = new JSONArray();
            for (String id : getHomeModuleIds()) {
                homeArr.put(id);
            }
            workspaceState.put("home_modules", homeArr);

            for (Map.Entry<String, JPanel> entry : moduleCache.entrySet()) {
                captureModuleState(entry.getKey(), entry.getValue());
            }

            Files.writeString(stateFile.toPath(), workspaceState.toString(2), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("保存全局工作区状态异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void captureModuleState(String name, JPanel panel) {
        try {
            JSONObject obj = new JSONObject();
            switch (name) {
                case "RENAMER": {
                    obj.put("currentMode", getField(panel, "currentMode"));
                    obj.put("isFlipped", getField(panel, "isFlipped"));
                    obj.put("biaoIdx", ((JComboBox<?>) getField(panel, "biaoCombo")).getSelectedIndex());
                    obj.put("baoIdx", ((JComboBox<?>) getField(panel, "baoCombo")).getSelectedIndex());
                    obj.put("typeIdx", ((JComboBox<?>) getField(panel, "typeCombo")).getSelectedIndex());
                    obj.put("newCompanyInput", ((JTextField) getField(panel, "newCompanyInput")).getText());

                    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getField(panel, "companyModel");
                    JSONArray companies = new JSONArray();
                    for (int i = 0; i < model.getSize(); i++) {
                        companies.put(model.getElementAt(i));
                    }
                    obj.put("companies", companies);
                    obj.put("selectedCompanyIdx", ((JComboBox<?>) getField(panel, "companyCombo")).getSelectedIndex());

                    List<File[]> undoBatch = (List<File[]>) getField(panel, "lastRenamedBatch");
                    JSONArray undoArr = new JSONArray();
                    if (undoBatch != null) {
                        for (File[] pair : undoBatch) {
                            if (pair != null && pair.length == 2) {
                                JSONObject pairObj = new JSONObject();
                                pairObj.put("old", pair[0].getAbsolutePath());
                                pairObj.put("new", pair[1].getAbsolutePath());
                                undoArr.put(pairObj);
                            }
                        }
                    }
                    obj.put("undoBatch", undoArr);
                    break;
                }
                case "CHECKER": {
                    obj.put("fuzzyLevel", getField(panel, "fuzzyLevel"));
                    break;
                }
                case "FINDER": {
                    obj.put("pathText", ((JTextField) getField(panel, "pathDisplayField")).getText());
                    obj.put("rulesText", ((JTextArea) getField(panel, "rulesTextArea")).getText());
                    obj.put("statusText", ((JLabel) getField(panel, "statusLabel")).getText());
                    obj.put("chkWord", ((JCheckBox) getField(panel, "chkWord")).isSelected());
                    obj.put("chkExcel", ((JCheckBox) getField(panel, "chkExcel")).isSelected());
                    obj.put("chkPdf", ((JCheckBox) getField(panel, "chkPdf")).isSelected());
                    obj.put("chkOther", ((JCheckBox) getField(panel, "chkOther")).isSelected());
                    obj.put("chkFolderOnly", ((JCheckBox) getField(panel, "chkFolderOnly")).isSelected());

                    List<File> allFiles = (List<File>) getField(panel, "allFiles");
                    JSONArray fileArr = new JSONArray();
                    if (allFiles != null && allFiles.size() <= 5000) {
                        for (File f : allFiles) {
                            fileArr.put(f.getAbsolutePath());
                        }
                    }
                    obj.put("allFiles", fileArr);
                    break;
                }
                case "REPLACER": {
                    JTextField[] inputs = (JTextField[]) getField(panel, "inputs");
                    JSONArray inArr = new JSONArray();
                    if (inputs != null) {
                        for (JTextField tf : inputs) {
                            inArr.put(tf != null ? tf.getText() : "");
                        }
                    }
                    obj.put("inputs", inArr);

                    JTextField customKeyInput = (JTextField) getField(panel, "customKeyInput");
                    obj.put("customKey", customKeyInput != null ? customKeyInput.getText() : "");

                    JTextField[] customKeyInputs = (JTextField[]) getField(panel, "customKeyInputs");
                    JSONArray customKeyArr = new JSONArray();
                    if (customKeyInputs != null) {
                        for (JTextField tf : customKeyInputs) {
                            customKeyArr.put(tf != null ? tf.getText() : "");
                        }
                    }
                    obj.put("customKeyInputs", customKeyArr);

                    obj.put("isAllCustomMode", getField(panel, "isAllCustomMode"));
                    obj.put("isBlackMode", getField(panel, "isBlackModeEnabled"));
                    obj.put("isRename", getField(panel, "isRenameEnabled"));
                    obj.put("logText", ((JTextArea) getField(panel, "logArea")).getText());
                    break;
                }
                case "REGION": {
                    obj.put("address", ((JTextField) getField(panel, "addressInput")).getText());
                    obj.put("regionText", ((JTextPane) getField(panel, "regionArea")).getText());
                    obj.put("cultureText", ((JTextPane) getField(panel, "cultureArea")).getText());
                    break;
                }
                case "CLOUD": {
                    List<File> shared = (List<File>) getField(panel, "sharedFiles");
                    JSONArray sharedArr = new JSONArray();
                    if (shared != null) {
                        for (File f : shared) {
                            sharedArr.put(f.getAbsolutePath());
                        }
                    }
                    obj.put("sharedFiles", sharedArr);
                    break;
                }
                case "TREASURE":
                case "CLIPBOARD": {
                    obj.put("isMonitoring", getField(panel, "isMonitoring"));
                    obj.put("searchText", ((JTextField) getField(panel, "searchField")).getText());
                    obj.put("categoryIdx", ((JComboBox<?>) getField(panel, "categoryCombo")).getSelectedIndex());
                    obj.put("detailText", ((JTextArea) getField(panel, "detailArea")).getText());
                    break;
                }
                default:
                    return;
            }
            workspaceState.put(name, obj);
        } catch (Exception e) {
            System.err.println("捕获模块 [" + name + "] 状态失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreModuleState(String name, JPanel panel) {
        if (!workspaceState.has(name)) return;
        JSONObject obj = workspaceState.optJSONObject(name);
        if (obj == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                switch (name) {
                    case "RENAMER": {
                        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getField(panel, "companyModel");
                        JSONArray companies = obj.optJSONArray("companies");
                        if (companies != null && companies.length() > 0) {
                            model.removeAllElements();
                            for (int i = 0; i < companies.length(); i++) {
                                model.addElement(companies.getString(i));
                            }
                        }
                        JComboBox<?> companyCombo = (JComboBox<?>) getField(panel, "companyCombo");
                        int selCompIdx = obj.optInt("selectedCompanyIdx", 0);
                        if (selCompIdx >= 0 && selCompIdx < model.getSize()) {
                            companyCombo.setSelectedIndex(selCompIdx);
                        }

                        ((JComboBox<?>) getField(panel, "biaoCombo")).setSelectedIndex(obj.optInt("biaoIdx", 0));
                        ((JComboBox<?>) getField(panel, "baoCombo")).setSelectedIndex(obj.optInt("baoIdx", 0));
                        ((JComboBox<?>) getField(panel, "typeCombo")).setSelectedIndex(obj.optInt("typeIdx", 0));
                        ((JTextField) getField(panel, "newCompanyInput")).setText(obj.optString("newCompanyInput", ""));

                        List<File[]> undoBatch = (List<File[]>) getField(panel, "lastRenamedBatch");
                        JSONArray undoArr = obj.optJSONArray("undoBatch");
                        if (undoBatch != null && undoArr != null) {
                            undoBatch.clear();
                            for (int i = 0; i < undoArr.length(); i++) {
                                JSONObject pairObj = undoArr.getJSONObject(i);
                                undoBatch.add(new File[]{new File(pairObj.getString("old")), new File(pairObj.getString("new"))});
                            }
                        }

                        setField(panel, "currentMode", obj.optInt("currentMode", 0));
                        invokeMethod(panel, "switchUIByMode");

                        boolean wantFlipped = obj.optBoolean("isFlipped", false);
                        boolean curFlipped = (boolean) getField(panel, "isFlipped");
                        if (wantFlipped != curFlipped) {
                            setField(panel, "isFlipped", wantFlipped);
                            JPanel mainContent = (JPanel) getField(panel, "mainContentPanel");
                            JPanel leftP = (JPanel) getField(panel, "leftPanel");
                            JPanel rightP = (JPanel) getField(panel, "rightPanel");
                            mainContent.removeAll();
                            if (wantFlipped) {
                                mainContent.add(rightP);
                                mainContent.add(leftP);
                            } else {
                                mainContent.add(leftP);
                                mainContent.add(rightP);
                            }
                            mainContent.revalidate();
                            mainContent.repaint();
                        }
                        break;
                    }
                    case "CHECKER": {
                        setField(panel, "fuzzyLevel", obj.optInt("fuzzyLevel", 0));
                        panel.repaint();
                        break;
                    }
                    case "FINDER": {
                        ((JTextField) getField(panel, "pathDisplayField")).setText(obj.optString("pathText", " 未选择任何文件夹..."));
                        ((JTextArea) getField(panel, "rulesTextArea")).setText(obj.optString("rulesText", "评审报告\n专家签到表\n应答文件上传情况统计"));

                        List<File> allFiles = (List<File>) getField(panel, "allFiles");
                        JSONArray fileArr = obj.optJSONArray("allFiles");
                        if (allFiles != null && fileArr != null) {
                            allFiles.clear();
                            for (int i = 0; i < fileArr.length(); i++) {
                                File f = new File(fileArr.getString(i));
                                if (f.exists()) allFiles.add(f);
                            }
                        }

                        ((JCheckBox) getField(panel, "chkWord")).setSelected(obj.optBoolean("chkWord", true));
                        ((JCheckBox) getField(panel, "chkExcel")).setSelected(obj.optBoolean("chkExcel", true));
                        ((JCheckBox) getField(panel, "chkPdf")).setSelected(obj.optBoolean("chkPdf", true));
                        ((JCheckBox) getField(panel, "chkOther")).setSelected(obj.optBoolean("chkOther", true));
                        ((JCheckBox) getField(panel, "chkFolderOnly")).setSelected(obj.optBoolean("chkFolderOnly", false));

                        invokeMethod(panel, "filterFiles");
                        ((JLabel) getField(panel, "statusLabel")).setText(obj.optString("statusText", " 准备就绪"));
                        break;
                    }
                    case "REPLACER": {
                        JTextField[] inputs = (JTextField[]) getField(panel, "inputs");
                        JSONArray inArr = obj.optJSONArray("inputs");
                        if (inputs != null && inArr != null) {
                            for (int i = 0; i < Math.min(inputs.length, inArr.length()); i++) {
                                if (inputs[i] != null) {
                                    inputs[i].setText(inArr.optString(i, ""));
                                }
                            }
                        }

                        JTextField customKeyInput = (JTextField) getField(panel, "customKeyInput");
                        if (customKeyInput != null) {
                            customKeyInput.setText(obj.optString("customKey", ""));
                        }

                        JTextField[] customKeyInputs = (JTextField[]) getField(panel, "customKeyInputs");
                        JSONArray customKeyArr = obj.optJSONArray("customKeyInputs");
                        if (customKeyInputs != null && customKeyArr != null) {
                            for (int i = 0; i < Math.min(customKeyInputs.length, customKeyArr.length()); i++) {
                                if (customKeyInputs[i] != null) {
                                    customKeyInputs[i].setText(customKeyArr.optString(i, ""));
                                }
                            }
                        }

                        boolean isAllCustom = obj.optBoolean("isAllCustomMode", false);
                        setField(panel, "isAllCustomMode", isAllCustom);
                        invokeMethod(panel, "updateRowsLayoutMode");

                        setField(panel, "isBlackModeEnabled", obj.optBoolean("isBlackMode", true));
                        setField(panel, "isRenameEnabled", obj.optBoolean("isRename", false));

                        String savedLog = obj.optString("logText", "");
                        if (!savedLog.isEmpty()) {
                            ((JTextArea) getField(panel, "logArea")).setText(savedLog);
                        }
                        panel.revalidate();
                        panel.repaint();
                        break;
                    }
                    case "REGION": {
                        String savedAddr = obj.optString("address", "");
                        String savedRegion = obj.optString("regionText", "等待输入...");
                        String savedCulture = obj.optString("cultureText", "文化与美誉将在此显示...");

                        ((JTextField) getField(panel, "addressInput")).setText(savedAddr);
                        Timer debounce = (Timer) getField(panel, "debounceTimer");
                        if (debounce != null) debounce.stop();

                        ((JTextPane) getField(panel, "regionArea")).setText(savedRegion);
                        ((JTextPane) getField(panel, "cultureArea")).setText(savedCulture);
                        break;
                    }
                    case "CLOUD": {
                        List<File> shared = (List<File>) getField(panel, "sharedFiles");
                        JSONArray sharedArr = obj.optJSONArray("sharedFiles");
                        if (shared != null && sharedArr != null) {
                            for (int i = 0; i < sharedArr.length(); i++) {
                                File f = new File(sharedArr.getString(i));
                                if (f.exists() && !shared.contains(f)) {
                                    shared.add(f);
                                }
                            }
                            invokeMethod(panel, "updateFileStatus");
                        }
                        break;
                    }
                    case "TREASURE":
                    case "CLIPBOARD": {
                        setField(panel, "isMonitoring", obj.optBoolean("isMonitoring", true));
                        JComboBox<?> catCombo = (JComboBox<?>) getField(panel, "categoryCombo");
                        int catIdx = obj.optInt("categoryIdx", 0);
                        if (catIdx >= 0 && catIdx < catCombo.getItemCount()) {
                            catCombo.setSelectedIndex(catIdx);
                        }
                        ((JTextField) getField(panel, "searchField")).setText(obj.optString("searchText", ""));
                        ((JTextArea) getField(panel, "detailArea")).setText(obj.optString("detailText", ""));
                        panel.repaint();
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("恢复模块 [" + name + "] 状态失败: " + e.getMessage());
            }
        });
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void invokeMethod(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    // =========================================================================

    class CloseButton extends JButton {
        public CloseButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean isHover = getModel().isRollover();
            boolean isPressed = getModel().isArmed();

            if (isHover) {
                g2.setColor(isPressed ? new Color(200, 30, 30, 220) : new Color(232, 17, 35, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }

            g2.setColor(isHover ? Color.WHITE : new Color(160, 165, 175));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int gap = 10;
            g2.drawLine(gap, gap, getWidth() - gap, getHeight() - gap);
            g2.drawLine(getWidth() - gap, gap, gap, getHeight() - gap);

            g2.dispose();
        }
    }

    class GearButton extends JButton {
        public GearButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(45, 45));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int radius = 11;

            boolean isHover = getModel().isRollover();

            g2.setColor(isHover ? new Color(130, 135, 145) : new Color(100, 105, 115));
            g2.fillRoundRect(cx - radius / 2, cy + radius + 7, radius, 4, 3, 3);
            g2.fillRect(cx - 2, cy + radius + 3, 4, 5);

            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(isHover ? Color.WHITE : new Color(170, 175, 185));
            g2.drawArc(cx - radius - 4, cy - radius - 4, (radius + 4) * 2, (radius + 4) * 2, -70, 180);

            g2.setColor(isHover ? new Color(52, 152, 219) : new Color(41, 128, 185));
            g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

            g2.setClip(new java.awt.geom.Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
            g2.setColor(isHover ? new Color(46, 204, 113) : new Color(39, 174, 96));
            g2.fillOval(cx - radius / 2, cy - radius / 3, radius + 5, radius - 2);
            g2.fillOval(cx + radius / 5, cy + radius / 8, radius / 2, radius + 5);
            g2.fillOval(cx - radius + 2, cy + radius / 2, radius + 5, radius / 2);

            g2.setColor(new Color(255, 255, 255, isHover ? 70 : 40));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawOval(cx - radius, cy - radius / 2, radius * 2, radius);
            g2.drawOval(cx - radius, cy - radius / 5, radius * 2, radius * 2 / 5);
            g2.drawOval(cx - radius, cy + radius / 5, radius * 2, radius);
            g2.drawOval(cx - radius / 2, cy - radius, radius, radius * 2);
            g2.drawOval(cx - radius / 5, cy - radius, radius * 2 / 5, radius * 2);
            g2.drawOval(cx + radius / 5, cy - radius, radius, radius * 2);

            g2.setClip(null);
            g2.setColor(new Color(255, 255, 255, isHover ? 130 : 80));
            g2.fillOval(cx - radius / 2, cy - radius + 2, radius, radius / 3);

            g2.setColor(isHover ? Color.WHITE : new Color(170, 175, 185));
            int axisX = (int) (Math.cos(Math.toRadians(-70)) * (radius + 4));
            int axisY = (int) (Math.sin(Math.toRadians(-70)) * (radius + 4));
            g2.fillOval(cx + axisX - 2, cy + axisY - 2, 4, 4);
            g2.fillOval(cx - axisX - 2, cy - axisY - 2, 4, 4);

            g2.dispose();
        }
    }

    private JPanel createMainMenu() {
        BackgroundPanel menuPanel = new BackgroundPanel("bg_imgs/月球与地球.png");
        menuPanel.setLayout(new BorderLayout());
        menuPanel.setFocusable(true);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topBar.setOpaque(false);

        GearButton settingsBtn = new GearButton();
        settingsBtn.addActionListener(e -> SettingsDialog.showDialog(MainLauncher.this));
        topBar.add(settingsBtn);

        homeGridArea = new JPanel(new GridBagLayout());
        homeGridArea.setOpaque(false);

        homeTitleLabel = new JLabel("ToolKit");
        homeTitleLabel.setFont(new Font("Montserrat", Font.BOLD, 56));
        homeTitleLabel.setForeground(Color.WHITE);

        refreshHomeGrid();

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner instanceof javax.swing.text.JTextComponent) {
                    return false;
                }

                if (e.getKeyCode() == KeyEvent.VK_G && menuPanel.isShowing() && (gamePanel == null || !gamePanel.isVisible())) {
                    gamePanel = new SavePigPanel();
                    gamePanel.enableInputMethods(false);
                    mainContainer.add(gamePanel, "GAME");
                    performFlipTransition("GAME", true);
                    gamePanel.requestFocusInWindow();
                    return true;
                }

                if (e.getKeyCode() == KeyEvent.VK_E && gamePanel != null && gamePanel.isVisible()) {
                    gamePanel.stopGame();
                    mainContainer.remove(gamePanel);
                    gamePanel = null;

                    SwingUtilities.invokeLater(() -> {
                        showMenu();
                        menuPanel.requestFocusInWindow();
                    });
                    return true;
                }
            }
            return false;
        });

        menuPanel.add(topBar, BorderLayout.NORTH);
        menuPanel.add(homeGridArea, BorderLayout.CENTER);

        return menuPanel;
    }

    private JButton createModuleButton(String cardName, Color themeColor, String iconPath) {
        RippleButton btn = new RippleButton("", themeColor);
        btn.setPreferredSize(new Dimension(270, 110));
        btn.setMinimumSize(new Dimension(80, 50));
        btn.setFocusable(false);

        try {
            java.net.URL imgURL = getClass().getResource(iconPath);
            if (imgURL != null) {
                ImageIcon originalIcon = new ImageIcon(imgURL);

                int initIconSize = 49;
                BufferedImage initialImg = new BufferedImage(initIconSize, initIconSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D initG2 = initialImg.createGraphics();
                initG2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                initG2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                initG2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                initG2.drawImage(originalIcon.getImage(), 0, 0, initIconSize, initIconSize, null);
                initG2.dispose();
                btn.setIcon(new ImageIcon(initialImg));
                btn.putClientProperty("lastIconSize", initIconSize);

                btn.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        int w = btn.getWidth();
                        int h = btn.getHeight();
                        if (w <= 0 || h <= 0) return;

                        int iconSize = Math.max(32, (int) (Math.min(w, h) * 0.45));

                        Integer last = (Integer) btn.getClientProperty("lastIconSize");
                        if (last != null && last == iconSize) {
                            return;
                        }
                        btn.putClientProperty("lastIconSize", iconSize);

                        BufferedImage scaledImg = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = scaledImg.createGraphics();

                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        g2.drawImage(originalIcon.getImage(), 0, 0, iconSize, iconSize, null);
                        g2.dispose();

                        btn.setIcon(new ImageIcon(scaledImg));
                    }
                });
            } else {
                System.err.println("找不到图标资源，请检查路径: " + iconPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        btn.addActionListener(e -> {
            Timer bloomTimer = new Timer(65, evt -> {
                ((Timer) evt.getSource()).stop();
                openModule(cardName);
            });
            bloomTimer.setRepeats(false);
            bloomTimer.start();
        });
        return btn;
    }

    public static void main(String[] args) {
        // 激活 Windows 1ms 硬件时钟精度锁
        WindowsTimerLock.lock1ms();

        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.awt.disablegrab", "true");
        System.setProperty("java.awt.im.style", "on-the-spot");

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[ToolKit] 未捕获异常 (" + t.getName() + "): " + e.getMessage());
            e.printStackTrace();
        });

        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            FontUtils.initGlobalFont();

            try {
                File lockFile = new File(System.getProperty("java.io.tmpdir"), "daili_assistant.lock");
                raf = new RandomAccessFile(lockFile, "rw");
                channel = raf.getChannel();
                lock = channel.tryLock();

                if (lock == null) {
                    UIManager.put("OptionPane.messageForeground", Color.WHITE);
                    UIManager.put("OptionPane.border", BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(80, 80, 80), 1),
                            BorderFactory.createEmptyBorder(15, 20, 15, 20)
                    ));

                    Color btnBg = new Color(60, 63, 65);
                    UIManager.put("Button.background", btnBg);
                    UIManager.put("Button.foreground", Color.WHITE);

                    UIManager.put("Button.default.background", btnBg);
                    UIManager.put("Button.default.foreground", Color.WHITE);
                    UIManager.put("Button.default.borderColor", new Color(80, 80, 80));
                    UIManager.put("Button.default.focusedBorderColor", new Color(100, 100, 100));
                    UIManager.put("Button.default.hoverBackground", new Color(75, 78, 81));

                    JDialog dialog = new JDialog((Frame) null, "提示", true);
                    dialog.setUndecorated(true);
                    dialog.setBackground(new Color(32, 33, 38, 255));

                    JPanel content = new JPanel();
                    content.setBackground(new Color(32, 33, 38));
                    content.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
                    content.setLayout(new BorderLayout(15, 15));

                    JLabel msg = new JLabel("  ToolKit 已在后台运行  ", SwingConstants.CENTER);
                    msg.setForeground(Color.WHITE);
                    msg.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
                    msg.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

                    JButton btn = new JButton("ok");
                    btn.setFocusPainted(false);
                    btn.addActionListener(e -> System.exit(0));

                    content.add(msg, BorderLayout.CENTER);
                    content.add(btn, BorderLayout.SOUTH);

                    dialog.add(content);
                    dialog.pack();
                    dialog.setLocationRelativeTo(null);
                    dialog.setVisible(true);

                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            JWindow splashScreen = new JWindow();
            splashScreen.setBackground(new Color(0, 0, 0, 0));

            java.net.URL gifUrl = MainLauncher.class.getClassLoader().getResource("imgs/loading.gif");

            if (gifUrl != null) {
                Image image = Toolkit.getDefaultToolkit().createImage(gifUrl);
                ImageFilter filter = new RGBImageFilter() {
                    public final int filterRGB(int x, int y, int rgb) {
                        if ((rgb | 0xFF000000) == 0xFFFFFFFF) {
                            return 0x00FFFFFF & rgb;
                        }
                        return rgb;
                    }
                };
                Image transparentGif = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(image.getSource(), filter));
                JLabel gifLabel = new JLabel(new ImageIcon(transparentGif));
                splashScreen.getContentPane().add(gifLabel);
            }

            splashScreen.pack();
            splashScreen.setLocationRelativeTo(null);
            splashScreen.setVisible(true);

            Thread.ofVirtual().name("SplashScreen-Timer").start(() -> {
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {
                }

                SwingUtilities.invokeLater(() -> {
                    MainLauncher launcher = new MainLauncher();
                    splashScreen.dispose();
                    launcher.setVisible(true);
                });
            });
        });
    }
}