package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassDropPanel;
import shaoxia.Utils.GlassPanel;
import shaoxia.Utils.GlassTextField;

// 🛡️ 安全门禁拦截包
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;

// --- ZXing 二维码库相关导入 ---
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极速云传面板 (默认手动开启 + 自定义账户密码/留空免密 + 睡觉/亮灯编程双状态点阵互动修复版)
 */
public class CloudSharePanel extends BackgroundPanel {
    private MainLauncher parent;
    private static CloudSharePanel currentInstance;

    // ================= 静态全局状态 (保障后台持久运行) =================
    private static HttpServer localServer = null;
    private static HttpServer wanForwardServer = null;
    private static volatile Process sshProcess = null;
    private static final int SHARE_PORT = 6506;
    private static final int WAN_FORWARD_PORT = 6507;
    private static final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static List<File> sharedFiles = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<File, Long> folderSizeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile boolean isLocalReady = false;
    private static volatile boolean isWanEnabled = false;
    private static volatile boolean isSshChecking = false;
    private static volatile boolean isSshReady = false;
    private static volatile long sshGeneration = 0;
    private static volatile String currentWanUrl = "WAN穿透未启动";
    private static boolean hookRegistered = false;

    // 动态门禁凭据（默认为空，即无密码门禁）
    private static volatile String authUser = "";
    private static volatile String authPass = "";

    // ================= 状态 1：熄灯趴桌酣睡点阵矩阵 (64列 x 26行) =================
    private static final String[] SLEEP_MATRIX = {
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,OOOOOOOOOOOOOO,,",
            ".....OOOO|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,OMMMMMMMMMMMMO,,",
            "....ODDDDO,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,OMEEEEEEEEEEEO,,",
            "...ODDDDDDO,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,OMEEEEEEEEEEEO,,",
            "...PDOOBBOO,,,,,,,,,,,,,,,OOOOOOOOOO,,,,,,,,,,,,OMEEEEEEEEEEEO,,",
            "..PDO....|,,,,,,,,,,,,,,OOLLLLLLLLLLOO,,,,,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,,,OLLLLLLLLLLLLHHO,,,,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,,OLLLLLLHHHHHHHHHHO,,,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,OLLLLHHHOOOOOOOOGHHO,,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,OLLLHHOWWWwWWwWOOGHHO,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,OLLHHOWWwFFFFwFFOOGHO,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,,OLLHHOWFFFFFFFFFfOGHO,,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,,,,,,,,,,OOLLHHOWFOOOFFOOOfOGGHO,,,,,OMEEEEEEEEEEEO,,",
            "..DO.....|,OOOOOO,,,OLLLHHHOFFFFFFFFffOGGHHO,,,,OMMMMMMMMMMMNO,,",
            "..DO.....|ORccccRO,OLLLLHHHOOFFFFFFFOOGGHHHO,,,,OOOOOOOOOOOOOO,,",
            ".ODDO....|ORRRRRrOOOLLLLHHHHHOOOOOOFFFOGGHHHO,,,,,,,,,OSSO,,,,,,",
            "ODDDDO...|ORRRRRrO,OGGLLLHHHHHHHHHFFFFOGGGHHO,,,,,,,,,OSSO,,,,,,",
            "OOOOOOddddORRRRRrOOOOGGGGGGGGGGGGffOOOGGGOOOddddddddddOSSOdddddd",
            "ddddddddddOrrrrrrOssssOOOOOOOOOOOOOOOOOOssssOOOOOOOOOOOSSOdddddd",
            "ddddddddddOrrrrrrOssssssssddddddddddddddddOKkKkKkKkKkKOSSOdddddd",
            "dddddddddddOOOOOOssssddddddddddddddddddddOKkKkKkKkKkKOSSSSSSSSOd",
            "ddddddddddddddddddddddddddddddddddddddddOKKKKKKKKKKKOOOOOOOOOOOd",
            "ddddddddddddddddddddddddddddddddddddddddOOOOOOOOOOOOdddddddddddd"
    };

    // ================= 状态 2：拖入文件惊醒 + 亮灯认真编程点阵矩阵 (64列 x 26行) =================
    // 注：屏幕代码采用独立数字字符 '1'(青) '2'(绿) '3'(黄) '4'(粉)，避免与 'P'、'G' 标签冲突
    private static final String[] AWAKE_MATRIX = {
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,,,OOOOOOOOOO,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
            ".........|,,,,,,,,,,,,,OOLLLLLLLLLLOO,,,,,,,,,,,OOOOOOOOOOOOOO,,",
            ".....OOOO|,,,,,,,,,,,,OLLLLHOOOOOOOOHO,,,,,,,,,,OMMMMMMMMMMMMO,,",
            "....ODDDDO,,,,,,,,,,,OLLLHOWWWwWWwWWwOO,,,,,,,,,OMIIIIIIIIIIIO,,",
            "...ODDDDDDO,,,,,,,,,,OLLHOWWwFFFFFFwWWwO,,,,,,,,OMI111II33IIIO,,",
            "...PDOBBBOO,,,,,,,,,,OLLHOWWFFFFFFFFFwwO,,,,,,,,OMIII22222IIIO,,",
            "..PDOBBBB|,,,,,,,,,,,OLLHOWFFFOFFFOFFFwO,,,,,,,,OMII44III3IIIO,,",
            "..DO.BBBB|,,,,,,,,,,,OLLHOWFFFOFFFOFFFFO,,,,,,,,OMI11111IIIIIO,,",
            "..DO.....|,,,,,,,,,,,OOLLHOFFFFFFFFFFFFO,,,,,,,,OMII222II33IIO,,",
            "..DO.....|,,,,~,,,,,,OOHLHOOFFFFFFuuFFOO,,,,,,,,OMIII44444IIIO,,",
            "..DO.....|,,,~,,,,,,,OLLHHHHOOOOOOOOOO,,,,,,,,,,OMI111III222IO,,",
            "..DO.....|,,,,~,,,,,OLLLLHHHHHHHHHHHHHO,,,,,,,,,OMIII3333IIIIO,,",
            "..DO.....|,,,~,,,,,OLLLLLHHHHHHLHHLHHHHO,,,,,,,,OMI1111II44IIO,,",
            "..DO.....|,,,,,,,,,OLLLLHHHHHHHLHHLHHHHHO,,,,,,,OMIIIIIIIIIIIO,,",
            "..DO.....|,OOOOOO,,OLLLLHHHHHHHGGGGHHHHHHO,,,,,,OMMMMMMMMMMMAO,,",
            "..DO.....|ORccccRO,OLLLLHHHHHHGOOOOGHHHHHHO,,,,,OOOOOOOOOOOOOO,,",
            ".ODDO....|ORRRRRrOOOLLLLHHHHHGO,,,,OGHHHHHHO,,,,,,,,,,OSSO,,,,,,",
            "ODDDDO...|ORRRRRrO,OGGLLHHHHHGO,,,,OGGHHHHFFO,,,,,,,,,OSSO,,,,,,",
            "OOOOOOddddORRRRRrOOOOGGGGGGGGGddddddOOGGGGFFFOddddddddOSSOdddddd",
            "ddddddddddOrrrrrrOssssOOOOOOOOddddddddOOFFFOOOOOOOOOOOOSSOdddddd",
            "ddddddddddOrrrrrrOssssssssddddddddddddddddOKFFkkFFKkKkOSSOdddddd",
            "dddddddddddOOOOOOssssddddddddddddddddddddOKkKkKkKkKkKOSSSSSSSSOd",
            "ddddddddddddddddddddddddddddddddddddddddOKKKKKKKKKKKOOOOOOOOOOOd",
            "ddddddddddddddddddddddddddddddddddddddddOOOOOOOOOOOOdddddddddddd"
    };

    // UI 组件
    private JLabel fileStatusLabel;
    private GlassTextField lanUrlField;
    private GlassTextField wanUrlField;
    private GlassTextField userField;
    private GlassTextField passField;
    private JButton lanQrBtn;
    private JButton wanQrBtn;
    private CloudEngineIndicator engineIndicator;
    private GlassDropPanel dropArea;
    private ActionButton toggleLanBtn;
    private ActionButton toggleWanBtn;

    // 动画与拖拽唤醒状态
    private Timer animTimer;
    private int animTick = 0;
    private volatile boolean isDragHover = false;

    public CloudSharePanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        currentInstance = this;
        this.parent = parent;
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        // 读取上次保存的自定义账户与密码（默认为空字符串）
        Preferences prefs = Preferences.userNodeForPackage(CloudSharePanel.class);
        authUser = prefs.get("cloud_auth_user", "");
        authPass = prefs.get("cloud_auth_pass", "");

        if (!hookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (sshProcess != null && sshProcess.isAlive()) {
                    sshProcess.destroyForcibly();
                }
                if (localServer != null) {
                    localServer.stop(0);
                }
                if (wanForwardServer != null) {
                    wanForwardServer.stop(0);
                }
                serverExecutor.shutdownNow();
            }));
            hookRegistered = true;
        }

        initTopBar();
        initCoreWorkspace();

        // 像素帧动画计时器（控制睡觉冒 ZZZ 气泡 & 醒来敲代码手指/屏幕代码滚动/咖啡热气）
        animTimer = new Timer(240, e -> {
            animTick = (animTick + 1) % 12;
            if (dropArea != null && dropArea.isShowing()) {
                dropArea.repaint();
            }
        });
        animTimer.start();

        // 默认关闭两种服务，由用户分别开启 LAN 直连或 WAN 穿透
        syncUIState();
    }

    /**
     * 静默获取用户目录作为手机上传落盘路径，避免安装在 C:\Program Files 下无写入权限
     */
    private static File getUploadDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            File downloadsDir = new File(userHome, "Downloads");
            File baseDir = (downloadsDir.exists() && downloadsDir.isDirectory()) ? downloadsDir : new File(userHome);
            File uploadDir = new File(baseDir, "CloudShareUploads");
            if (uploadDir.exists() || uploadDir.mkdirs()) {
                return uploadDir;
            }
        }
        File fallbackDir = new File(System.getProperty("java.io.tmpdir"), "CloudShareUploads");
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs();
        }
        return fallbackDir;
    }

    /**
     * 判断当前小人是否处于“醒来亮灯认真编程”状态：
     * 只要用户正在拖入文件 (isDragHover) 或 已经挂载了文件 (!sharedFiles.isEmpty())，立刻醒来亮灯编程！
     */
    private boolean isCoderAwake() {
        return isDragHover || !sharedFiles.isEmpty();
    }

    private void syncUIState() {
        lanUrlField.setText(isLocalReady
                ? "http://" + getLocalIPv4() + ":" + SHARE_PORT + "/"
                : "LAN直连未启动");
        wanUrlField.setText(isWanEnabled ? currentWanUrl : "WAN穿透未启动");
        toggleLanBtn.setText(isLocalReady ? "关闭 LAN 直连" : "启动 LAN 直连");
        toggleWanBtn.setText(isWanEnabled ? "关闭 WAN 穿透" : "启动 WAN 穿透");
        updateFileStatus();
        engineIndicator.repaint();
        if (dropArea != null) dropArea.repaint();
    }

    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));
        topBar.setPreferredSize(new Dimension(0, 56));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());

        toggleLanBtn = new ActionButton("启动 LAN 直连");
        toggleLanBtn.setPreferredSize(new Dimension(130, 36));
        toggleLanBtn.setMinimumSize(new Dimension(130, 36));
        toggleLanBtn.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
        toggleLanBtn.addActionListener(e -> {
            if (isLocalReady) stopLanSharing();
            else startLanSharing();
        });

        toggleWanBtn = new ActionButton("启动 WAN 穿透");
        toggleWanBtn.setPreferredSize(new Dimension(130, 36));
        toggleWanBtn.setMinimumSize(new Dimension(130, 36));
        toggleWanBtn.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
        toggleWanBtn.addActionListener(e -> {
            if (isWanEnabled) stopWanSharing();
            else startWanSharing();
        });

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);
        leftTop.add(backBtn);
        leftTop.add(Box.createHorizontalStrut(10));
        leftTop.add(toggleLanBtn);
        leftTop.add(Box.createHorizontalStrut(10));
        leftTop.add(toggleWanBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        engineIndicator = new CloudEngineIndicator();
        engineIndicator.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isWanEnabled) {
                    redeploySshTunnel();
                }
            }
        });
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.setOpaque(false);
        rightTop.add(engineIndicator);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
    }

    private void initCoreWorkspace() {
        // 使用外层 BorderLayout 将中部表单与底部大按钮彻底分离，杜绝 GridBagLayout 空间挤压裁切
        JPanel workspaceWrapper = new JPanel(new BorderLayout(0, 10));
        workspaceWrapper.setOpaque(false);
        workspaceWrapper.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // --- 0. 文件拖入热挂载区 (直接在此绘制睡觉/亮灯编程点阵场景) ---
        dropArea = new GlassDropPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintInteractiveCoderMatrix(g, getWidth(), getHeight());
            }
        };
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(6, 12, 5, 12));
        dropArea.setMinimumSize(new Dimension(200, 95));
        dropArea.setPreferredSize(new Dimension(200, 126));

        fileStatusLabel = new JLabel("拖入任意文件/文件夹唤醒挂载 (双击清空)", SwingConstants.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                String text = getText();
                if (text != null && !text.isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    FontMetrics fm = g2.getFontMetrics(getFont());
                    int tw = fm.stringWidth(text);
                    int th = fm.getHeight();
                    int padX = 12;
                    int padY = 2;
                    int boxW = Math.min(getWidth() - 8, tw + padX * 2);
                    int boxH = th + padY * 2;
                    int boxX = (getWidth() - boxW) / 2;
                    int boxY = (getHeight() - boxH) / 2;

                    g2.setColor(new Color(10, 13, 20, 185));
                    g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
                    g2.setColor(isCoderAwake() ? new Color(255, 210, 100, 90) : new Color(255, 255, 255, 35));
                    g2.drawRoundRect(boxX, boxY, boxW - 1, boxH - 1, 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        fileStatusLabel.setForeground(new Color(220, 228, 238));
        fileStatusLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
        fileStatusLabel.setPreferredSize(new Dimension(200, 22));
        dropArea.add(fileStatusLabel, BorderLayout.SOUTH);

        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    sharedFiles.clear();
                    folderSizeCache.clear();
                    isDragHover = false;
                    updateFileStatus();
                    dropArea.repaint();
                }
            }
        });

        dropArea.setDropTarget(new DropTarget(dropArea, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                isDragHover = true;
                SwingUtilities.invokeLater(() -> {
                    dropArea.setDragActive(true);
                    fileStatusLabel.setText("已唤醒！松开鼠标即可挂载文件并开始传输...");
                    fileStatusLabel.setForeground(new Color(255, 225, 120));
                    dropArea.repaint();
                });
            }

            @Override
            public void dragOver(java.awt.dnd.DropTargetDragEvent dtde) {
                // ✨ 保持空实现，避免高频调用阻塞 OLE 队列
            }

            @Override
            public void dragExit(java.awt.dnd.DropTargetEvent dte) {
                isDragHover = false;
                SwingUtilities.invokeLater(() -> {
                    dropArea.setDragActive(false);
                    updateFileStatus();
                    dropArea.repaint();
                });
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                isDragHover = false;
                SwingUtilities.invokeLater(() -> dropArea.setDragActive(false));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        dtde.dropComplete(true);

                        if (files != null && !files.isEmpty()) {
                            Thread.ofVirtual().name("CloudShare-DropWorker").start(() -> {
                                for (File f : files) {
                                    if (!sharedFiles.contains(f)) sharedFiles.add(f);
                                }
                                SwingUtilities.invokeLater(CloudSharePanel.this::updateFileStatus);
                            });
                        }
                    } else {
                        dtde.dropComplete(false);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try { dtde.dropComplete(false); } catch (Exception ignored) {}
                }
            }
        }));

        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 10, 0);
        centerPanel.add(dropArea, gbc);

        // --- 1. LAN 直连栏 ---
        GlassPanel lanPanel = new GlassPanel(new BorderLayout(10, 0));
        lanPanel.setBorder(new EmptyBorder(7, 15, 7, 15));
        lanPanel.setMinimumSize(new Dimension(200, 46));
        lanPanel.setPreferredSize(new Dimension(200, 48));

        JLabel lanLabel = new JLabel("LAN 直连:");
        lanLabel.setForeground(new Color(180, 180, 180));
        lanLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 15));
        lanLabel.setPreferredSize(new Dimension(90, 30));

        lanUrlField = new GlassTextField();
        lanUrlField.setEditable(false);
        lanUrlField.setText("LAN直连未启动");
        lanUrlField.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
        lanUrlField.setForeground(Color.WHITE);
        lanUrlField.setPreferredSize(new Dimension(150, 32));
        lanUrlField.setMinimumSize(new Dimension(100, 32));
        bindClickCopyAndJump(lanUrlField);

        lanQrBtn = createRoundedRectButton("扫码");
        lanQrBtn.addActionListener(e -> showQRCode(lanUrlField.getText()));

        lanPanel.add(lanLabel, BorderLayout.WEST);
        lanPanel.add(lanUrlField, BorderLayout.CENTER);
        lanPanel.add(lanQrBtn, BorderLayout.EAST);

        gbc.gridy = 1;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 10, 0);
        centerPanel.add(lanPanel, gbc);

        // --- 2. WAN 穿透栏 ---
        GlassPanel wanPanel = new GlassPanel(new BorderLayout(10, 0));
        wanPanel.setBorder(new EmptyBorder(7, 15, 7, 15));
        wanPanel.setMinimumSize(new Dimension(200, 46));
        wanPanel.setPreferredSize(new Dimension(200, 48));

        JLabel wanLabel = new JLabel("WAN 穿透:");
        wanLabel.setForeground(new Color(180, 180, 180));
        wanLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 15));
        wanLabel.setPreferredSize(new Dimension(90, 30));

        wanUrlField = new GlassTextField();
        wanUrlField.setEditable(false);
        wanUrlField.setText("WAN穿透未启动");
        wanUrlField.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
        wanUrlField.setForeground(Color.WHITE);
        wanUrlField.setPreferredSize(new Dimension(150, 32));
        wanUrlField.setMinimumSize(new Dimension(100, 32));
        bindClickCopyAndJump(wanUrlField);

        wanQrBtn = createRoundedRectButton("扫码");
        wanQrBtn.addActionListener(e -> showQRCode(wanUrlField.getText()));

        wanPanel.add(wanLabel, BorderLayout.WEST);
        wanPanel.add(wanUrlField, BorderLayout.CENTER);
        wanPanel.add(wanQrBtn, BorderLayout.EAST);

        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 10, 0);
        centerPanel.add(wanPanel, gbc);

        // --- 3. 自定义账户与密码栏（留空则免密） ---
        GlassPanel authPanel = new GlassPanel(new BorderLayout(10, 0));
        authPanel.setBorder(new EmptyBorder(7, 15, 7, 15));
        authPanel.setMinimumSize(new Dimension(200, 46));
        authPanel.setPreferredSize(new Dimension(200, 48));

        JLabel authLabel = new JLabel("访问门禁:");
        authLabel.setForeground(new Color(180, 180, 180));
        authLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 15));
        authLabel.setPreferredSize(new Dimension(90, 30));

        JPanel authFieldsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        authFieldsPanel.setOpaque(false);

        userField = createPlaceholderField("账户 (留空免密)", authUser);
        passField = createPlaceholderField("密码 (留空免密)", authPass);

        DocumentListener authDocListener = new DocumentListener() {
            private void syncAuth() {
                authUser = userField.getText().trim();
                authPass = passField.getText().trim();
                Preferences p = Preferences.userNodeForPackage(CloudSharePanel.class);
                p.put("cloud_auth_user", authUser);
                p.put("cloud_auth_pass", authPass);
            }
            @Override public void insertUpdate(DocumentEvent e) { syncAuth(); }
            @Override public void removeUpdate(DocumentEvent e) { syncAuth(); }
            @Override public void changedUpdate(DocumentEvent e) { syncAuth(); }
        };
        userField.getDocument().addDocumentListener(authDocListener);
        passField.getDocument().addDocumentListener(authDocListener);

        authFieldsPanel.add(userField);
        authFieldsPanel.add(passField);

        authPanel.add(authLabel, BorderLayout.WEST);
        authPanel.add(authFieldsPanel, BorderLayout.CENTER);

        gbc.gridy = 3;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        centerPanel.add(authPanel, gbc);

        workspaceWrapper.add(centerPanel, BorderLayout.CENTER);
        add(workspaceWrapper, BorderLayout.CENTER);
    }

    /**
     * 核心点阵矩阵绘制：支持【趴桌睡觉冒 ZZZ】与【拖入文件惊醒亮灯认真编程】无缝切换
     */
    private void paintInteractiveCoderMatrix(Graphics g, int w, int h) {
        if (w <= 20 || h <= 20) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int inset = 3;
        Shape clipShape = new RoundRectangle2D.Float(inset, inset, w - inset * 2, h - inset * 2, 18, 18);
        g2.clip(clipShape);

        boolean awake = isCoderAwake();
        String[] activeMatrix = awake ? AWAKE_MATRIX : SLEEP_MATRIX;

        int matrixRows = activeMatrix.length;       // 26
        int matrixCols = activeMatrix[0].length();  // 64

        // 计算正方形点阵格尺寸，铺满整个拖拽监听区域
        int cell = Math.max(3, (h - inset * 2) / matrixRows);
        int dotSize = Math.max(2, cell - 1);

        int totalCols = (w - inset * 2 + cell - 1) / cell;
        int colOffset = (totalCols - matrixCols) / 2;
        int startX = (w - totalCols * cell) / 2;
        int startY = (h - matrixRows * cell) / 2;

        // 1. 底层背景色
        g2.setColor(awake ? new Color(18, 23, 34, 225) : new Color(10, 13, 20, 225));
        g2.fillRect(inset, inset, w - inset * 2, h - inset * 2);

        // 2. 遍历渲染点阵矩阵
        for (int r = 0; r < matrixRows; r++) {
            String rowStr = activeMatrix[r];
            for (int c = 0; c < totalCols; c++) {
                int srcCol = c - colOffset;
                char ch;
                if (srcCol < 0) {
                    ch = (r >= 20) ? 'd' : '.';
                } else if (srcCol >= rowStr.length()) {
                    ch = (r >= 20) ? 'd' : ',';
                } else {
                    ch = rowStr.charAt(srcCol);
                }

                // 跳过咖啡热气占位符，由动态帧专门绘制
                if (ch == '~') {
                    ch = ',';
                }

                // 醒来敲代码状态：让屏幕上的彩色代码行随 animTick 动态滚动变化
                if (awake && (ch == '1' || ch == '2' || ch == '3' || ch == '4' || ch == 'I')) {
                    ch = getAnimatedCodePixel(r, srcCol, ch);
                }

                Color baseColor = getScenePixelColor(ch, awake);
                if (baseColor != null) {
                    // 如果处于亮灯编程状态，计算台灯暖光束与显示器荧光对周围点阵的照亮加成
                    if (awake && srcCol >= 0 && srcCol < matrixCols) {
                        baseColor = applyLampAndScreenGlow(baseColor, r, srcCol);
                    }
                    g2.setColor(baseColor);
                    int px = startX + c * cell;
                    int py = startY + r * cell;
                    g2.fillRoundRect(px, py, dotSize, dotSize, 2, 2);
                }
            }
        }

        // 3. 叠加动态帧元素
        if (!awake) {
            // === 睡觉状态：头顶右上角冒出三个动态漂浮的 Z Z Z 像素气泡 ===
            int phase = animTick % 4;
            int z1Dy = (phase == 1 || phase == 2) ? -1 : 0;
            int z2Dy = (phase == 2 || phase == 3) ? -1 : 0;
            int z3Dy = (phase == 3 || phase == 0) ? -1 : 0;

            drawPixelSprite(g2, Z_SMALL, startX, startY, cell, dotSize, colOffset + 39, 9 + z1Dy, new Color(195, 210, 230));
            drawPixelSprite(g2, Z_MEDIUM, startX, startY, cell, dotSize, colOffset + 43, 5 + z2Dy, new Color(220, 232, 248));
            drawPixelSprite(g2, Z_LARGE, startX, startY, cell, dotSize, colOffset + 48, 0 + z3Dy, new Color(245, 250, 255));
        } else {
            // === 醒来编程状态：1) 咖啡杯冒热气  2) 双手在键盘上飞速交替敲击 ===
            drawAnimatedCoffeeSteam(g2, startX, startY, cell, dotSize, colOffset);
            drawAnimatedTypingHands(g2, startX, startY, cell, dotSize, colOffset);
        }

        g2.dispose();
    }

    private static final String[] Z_SMALL = {
            "ZZZ",
            "..Z",
            ".Z.",
            "ZZZ"
    };

    private static final String[] Z_MEDIUM = {
            "ZZZZ",
            "..Z.",
            ".Z..",
            "Z...",
            "ZZZZ"
    };

    private static final String[] Z_LARGE = {
            "ZZZZZ",
            "...Z.",
            "..Z..",
            ".Z...",
            "ZZZZZ"
    };

    private void drawPixelSprite(Graphics2D g2, String[] sprite, int startX, int startY, int cell, int dotSize,
                                 int baseCol, int baseRow, Color color) {
        g2.setColor(new Color(12, 15, 22));
        for (int r = 0; r < sprite.length; r++) {
            String row = sprite[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == 'Z') {
                    g2.fillRoundRect(startX + (baseCol + c) * cell, startY + (baseRow + r + 1) * cell, dotSize, dotSize, 2, 2);
                }
            }
        }
        g2.setColor(color);
        for (int r = 0; r < sprite.length; r++) {
            String row = sprite[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == 'Z') {
                    g2.fillRoundRect(startX + (baseCol + c) * cell, startY + (baseRow + r) * cell, dotSize, dotSize, 2, 2);
                }
            }
        }
    }

    /**
     * 醒来状态：绘制红咖啡杯上方袅袅升起的动态像素热气
     */
    private void drawAnimatedCoffeeSteam(Graphics2D g2, int startX, int startY, int cell, int dotSize, int colOffset) {
        int shift = (animTick % 2 == 0) ? 0 : 1;
        int[][] steamCoords = {
                {13 + shift, 14},
                {15 - shift, 13},
                {13 + shift, 11},
                {14 - shift, 10}
        };
        for (int i = 0; i < steamCoords.length; i++) {
            int alpha = 210 - i * 35;
            g2.setColor(new Color(245, 248, 255, Math.max(80, alpha)));
            int px = startX + (colOffset + steamCoords[i][0]) * cell;
            int py = startY + steamCoords[i][1] * cell;
            g2.fillRoundRect(px, py, dotSize, dotSize, 2, 2);
        }
    }

    /**
     * 醒来状态：绘制双手在机械键盘上快速交替敲击的动态帧与按键火花
     */
    private void drawAnimatedTypingHands(Graphics2D g2, int startX, int startY, int cell, int dotSize, int colOffset) {
        boolean leftDown = (animTick % 2 == 0);
        Color skin = new Color(235, 180, 140);
        Color spark = new Color(120, 245, 255);

        // 左手与右手交替上下敲击键盘 (列 39..45, 行 20..22)
        int leftHandRow = leftDown ? 22 : 21;
        int rightHandRow = leftDown ? 21 : 22;

        g2.setColor(skin);
        for (int dc = 0; dc < 2; dc++) {
            g2.fillRoundRect(startX + (colOffset + 39 + dc) * cell, startY + leftHandRow * cell, dotSize, dotSize, 2, 2);
            g2.fillRoundRect(startX + (colOffset + 43 + dc) * cell, startY + rightHandRow * cell, dotSize, dotSize, 2, 2);
        }

        // 敲击瞬间亮起青色键盘微光像素点
        g2.setColor(spark);
        int sparkCol = leftDown ? (colOffset + 40) : (colOffset + 44);
        g2.fillRoundRect(startX + sparkCol * cell, startY + 23 * cell, dotSize, dotSize, 2, 2);
    }

    /**
     * 醒来状态：让显示器上的代码行呈现实时打字/滚动效果
     */
    private char getAnimatedCodePixel(int row, int col, char original) {
        if (row < 6 || row > 14 || col < 50 || col > 59) return original;
        int scrollRow = 6 + ((row - 6 + animTick / 2) % 9);
        char scrolled = AWAKE_MATRIX[scrollRow].charAt(col);
        // 模拟光标闪烁
        if (row == 13 && col == 53 + (animTick % 5)) {
            return '1';
        }
        if (scrolled == '1' || scrolled == '2' || scrolled == '3' || scrolled == '4' || scrolled == 'I') {
            return scrolled;
        }
        return original;
    }

    /**
     * 醒来状态：台灯暖黄色光锥与显示器蓝光对周围像素点的照亮混色
     */
    private Color applyLampAndScreenGlow(Color base, int r, int c) {
        // 1. 左侧台灯暖黄光锥范围：灯头在 (r=7, c=7)，向下呈扇形扩散照亮桌面、咖啡杯与人物左侧
        if (r >= 7 && r <= 24) {
            double dy = r - 6.0;
            double centerCol = 8.5 + dy * 0.65;
            double halfSpread = 2.5 + dy * 0.85;
            double dist = Math.abs(c - centerCol);
            if (dist <= halfSpread) {
                double intensity = (1.0 - dist / halfSpread) * (1.0 - (dy / 22.0) * 0.45);
                intensity = Math.max(0.0, Math.min(1.0, intensity));
                int nr = Math.min(255, (int) (base.getRed() + 75 * intensity));
                int ng = Math.min(255, (int) (base.getGreen() + 52 * intensity));
                int nb = Math.min(255, (int) (base.getBlue() + 18 * intensity));
                base = new Color(nr, ng, nb);
            }
        }

        // 2. 右侧显示器屏幕荧光范围：照亮人物右侧面庞与键盘区域
        if (r >= 5 && r <= 21 && c >= 31 && c <= 48) {
            double distC = 48.0 - c;
            double intensity = Math.max(0.0, (1.0 - distC / 18.0) * 0.35);
            int nr = Math.min(255, (int) (base.getRed() + 20 * intensity));
            int ng = Math.min(255, (int) (base.getGreen() + 55 * intensity));
            int nb = Math.min(255, (int) (base.getBlue() + 85 * intensity));
            base = new Color(nr, ng, nb);
        }
        return base;
    }

    /**
     * 点阵矩阵调色板映射 (所有 case 标签均唯一，无任何冲突)
     */
    private Color getScenePixelColor(char ch, boolean awake) {
        switch (ch) {
            case '.': return awake ? new Color(28, 34, 48) : new Color(17, 21, 30);      // 左侧墙面
            case '|': return new Color(13, 16, 23);                                      // 墙角分界线
            case ',': return awake ? new Color(40, 49, 68) : new Color(26, 32, 44);      // 主背景墙面
            case 'd': return awake ? new Color(68, 79, 98) : new Color(45, 53, 68);      // 桌面主色
            case 's': return awake ? new Color(46, 55, 70) : new Color(31, 37, 48);      // 桌面投影
            case 'O': return new Color(12, 14, 20);                                      // 深黑轮廓线
            case 'D': return awake ? new Color(90, 102, 120) : new Color(55, 63, 76);    // 台灯金属灯臂/灯罩
            case 'P': return new Color(210, 155, 60);                                    // 台灯关节黄铜螺母
            case 'B': return awake ? new Color(255, 238, 120) : new Color(75, 82, 94);   // 台灯灯泡（亮起暖黄 / 熄灭暗灰）
            case 'L': return awake ? new Color(162, 175, 192) : new Color(132, 144, 160);// 连帽衫高光
            case 'H': return awake ? new Color(124, 136, 154) : new Color(98, 108, 124); // 连帽衫主色
            case 'G': return awake ? new Color(86, 96, 112) : new Color(68, 76, 90);     // 连帽衫暗部
            case 'W': return new Color(62, 37, 22);                                      // 头发深棕
            case 'w': return new Color(98, 60, 36);                                      // 头发亮棕
            case 'F': return new Color(216, 162, 122);                                   // 肤色主色
            case 'f': return new Color(175, 122, 86);                                    // 肤色阴影
            case 'u': return new Color(145, 88, 70);                                     // 专注嘴部线条
            case 'R': return new Color(185, 55, 55);                                     // 红咖啡杯高光
            case 'r': return new Color(138, 38, 38);                                     // 红咖啡杯主色
            case 'c': return new Color(48, 22, 18);                                      // 咖啡液面深棕
            case 'K': return new Color(30, 35, 45);                                      // 机械键盘外框
            case 'k': return awake ? new Color(75, 88, 110) : new Color(52, 60, 74);     // 机械键盘键帽
            case 'M': return new Color(54, 63, 78);                                      // 显示器高光外框
            case 'E': return new Color(20, 24, 32);                                      // 显示器熄屏黑
            case 'I': return new Color(18, 34, 58);                                      // 显示器亮屏深蓝 IDE 底色
            case '1': return new Color(80, 228, 255);                                    // 屏幕代码：青色关键字
            case '2': return new Color(95, 242, 145);                                    // 屏幕代码：绿色字符串
            case '3': return new Color(255, 218, 95);                                    // 屏幕代码：黄色函数名
            case '4': return new Color(255, 120, 198);                                   // 屏幕代码：粉色变量
            case 'N': return new Color(235, 135, 35);                                    // 显示器待机橙色指示灯
            case 'A': return new Color(46, 235, 125);                                    // 显示器运行绿色指示灯
            case 'S': return new Color(22, 26, 35);                                      // 显示器支架与底座
            default:  return null;
        }
    }

    /**
     * 创建带有轻量级占位提示文字的毛玻璃输入框
     */
    private GlassTextField createPlaceholderField(String placeholder, String initialText) {
        GlassTextField field = new GlassTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(145, 150, 160));
                    g2.setFont(FontUtils.getChineseFont(Font.PLAIN, 13));
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(placeholder, 12, y);
                    g2.dispose();
                }
            }
        };
        field.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
        field.setForeground(Color.WHITE);
        field.setPreferredSize(new Dimension(80, 32));
        field.setMinimumSize(new Dimension(60, 32));
        field.setText(initialText != null ? initialText : "");
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.repaint(); }
            @Override public void focusLost(FocusEvent e) { field.repaint(); }
        });
        return field;
    }

    private JButton createRoundedRectButton(String text) {
        JButton btn = new JButton(text) {
            private final Color bgColor = new Color(30, 35, 40, 120);

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                boolean isPressed = getModel().isPressed();
                boolean isHover = getModel().isRollover();
                Color drawColor = bgColor;

                if (isPressed) {
                    int newAlpha = Math.min(255, bgColor.getAlpha() + 40);
                    drawColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), newAlpha);
                } else if (isHover) {
                    int newAlpha = Math.max(0, bgColor.getAlpha() - 40);
                    drawColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), newAlpha);
                }

                int w = getWidth();
                int h = getHeight();
                int arc = 15;
                g2.setColor(drawColor);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                String t = getText();
                if (t != null && !t.isEmpty()) {
                    Font font = getFont();
                    Color textColor = isEnabled() ? getForeground() : new Color(255, 255, 255, 100);
                    FontUtils.drawCenteredText(g2, t, font, textColor, w, h);
                }

                g2.dispose();
            }
        };
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(80, 32));
        btn.setMinimumSize(new Dimension(80, 32));
        return btn;
    }

    private String getLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static long calculateFolderSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        return folderSizeCache.computeIfAbsent(file, CloudSharePanel::computeDirSizeRecursive);
    }

    private static long computeDirSizeRecursive(File dir) {
        long size = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                size += child.isFile() ? child.length() : computeDirSizeRecursive(child);
            }
        }
        return size;
    }

    private void updateFileStatus() {
        if (sharedFiles.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                fileStatusLabel.setText("拖入任意文件/文件夹唤醒挂载 (双击清空)");
                fileStatusLabel.setForeground(new Color(220, 228, 238));
                if (dropArea != null) dropArea.repaint();
            });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            fileStatusLabel.setText("正在后台统计挂载项目体积...");
            fileStatusLabel.setForeground(Color.WHITE);
        });
        Thread.ofVirtual().name("CloudShare-SizeCalc").start(() -> {
            long totalSize = 0;
            for (File f : sharedFiles) {
                totalSize += calculateFolderSize(f);
            }
            String stateSuffix = (isLocalReady || isWanEnabled) ? " - 传输服务运行中" : " - 编码就绪(待启动)";
            final String text = "已挂载 " + sharedFiles.size() + " 个项目 (共 " + formatSize(totalSize) + ")" + stateSuffix;
            SwingUtilities.invokeLater(() -> {
                fileStatusLabel.setText(text);
                fileStatusLabel.setForeground(new Color(125, 249, 175));
                if (dropArea != null) dropArea.repaint();
            });
        });
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private void bindClickCopyAndJump(JTextField field) {
        field.setCursor(new Cursor(Cursor.HAND_CURSOR));
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    String url = field.getText().trim();
                    if (url.startsWith("http")) {
                        StringSelection selection = new StringSelection(url);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
                    }
                }
            }
        });
    }

    private void showQRCode(String url) {
        if (url == null || url.trim().isEmpty() || !url.startsWith("http")) {
            JOptionPane.showMessageDialog(this,
                    "链接尚未就绪，请先启动对应的 LAN 直连或 WAN 穿透！",
                    "未就绪",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 250, 250, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            BufferedImage rawImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    rawImage.setRGB(x, y, matrix.get(x, y) ? 0xFF111111 : 0xFFFFFFFF);
                }
            }

            int arc = 25;
            BufferedImage roundedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = roundedImage.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setClip(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
            g2.drawImage(rawImage, 0, 0, null);

            g2.setClip(null);
            g2.setColor(new Color(200, 200, 200, 80));
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g2.dispose();

            JOptionPane.showMessageDialog(this,
                    new JLabel(new ImageIcon(roundedImage)),
                    "扫码访问",
                    JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "二维码生成失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================= 核心网络逻辑 (支持动态门禁/留空免密) =================

    private HttpServer createShareServer(InetSocketAddress bindAddress) throws Exception {
        HttpServer server = HttpServer.create(bindAddress, 0);
        try {
            HttpContext rootContext = server.createContext("/", new IndexHandler(sharedFiles));
            HttpContext downContext = server.createContext("/download", new FileTransportHandler(sharedFiles, true));
            HttpContext viewContext = server.createContext("/view", new FileTransportHandler(sharedFiles, false));
            HttpContext uploadContext = server.createContext("/upload", new UploadHandler());
            server.createContext("/favicon.ico", new FaviconHandler());

            BasicAuthenticator auth = new BasicAuthenticator("ToolKit CloudShare") {
                @Override
                public Result authenticate(HttpExchange t) {
                    String u = authUser != null ? authUser.trim() : "";
                    String p = authPass != null ? authPass.trim() : "";
                    if (u.isEmpty() && p.isEmpty()) {
                        return new Success(new HttpPrincipal("guest", "ToolKit CloudShare"));
                    }
                    return super.authenticate(t);
                }

                @Override
                public boolean checkCredentials(String user, String pwd) {
                    String expectedUser = authUser != null ? authUser.trim() : "";
                    String expectedPass = authPass != null ? authPass.trim() : "";
                    String actualUser = user != null ? user : "";
                    String actualPass = pwd != null ? pwd : "";
                    return actualUser.equals(expectedUser) && actualPass.equals(expectedPass);
                }
            };

            rootContext.setAuthenticator(auth);
            downContext.setAuthenticator(auth);
            viewContext.setAuthenticator(auth);
            uploadContext.setAuthenticator(auth);

            server.setExecutor(serverExecutor);
            server.start();
            return server;
        } catch (Exception ex) {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
            throw ex;
        }
    }

    private void startLanSharing() {
        try {
            if (localServer == null) {
                localServer = createShareServer(new InetSocketAddress(SHARE_PORT));
            }
            isLocalReady = true;
            lanUrlField.setText("http://" + getLocalIPv4() + ":" + SHARE_PORT + "/");
            toggleLanBtn.setText("关闭 LAN 直连");
            updateFileStatus();
            engineIndicator.repaint();
        } catch (Exception ex) {
            localServer = null;
            isLocalReady = false;
            lanUrlField.setText("LAN 启动失败：6506 端口可能被占用");
            toggleLanBtn.setText("启动 LAN 直连");
            updateFileStatus();
        }
    }

    private void stopLanSharing() {
        if (localServer != null) {
            localServer.stop(0);
            localServer = null;
        }
        isLocalReady = false;
        lanUrlField.setText("LAN直连已关闭");
        toggleLanBtn.setText("启动 LAN 直连");
        updateFileStatus();
        engineIndicator.repaint();
    }

    private void startWanSharing() {
        try {
            if (wanForwardServer == null) {
                InetAddress loopback = InetAddress.getByName("127.0.0.1");
                wanForwardServer = createShareServer(new InetSocketAddress(loopback, WAN_FORWARD_PORT));
            }
            isWanEnabled = true;
            currentWanUrl = "正在向公网节点请求 SSH 映射...";
            wanUrlField.setText(currentWanUrl);
            toggleWanBtn.setText("关闭 WAN 穿透");
            updateFileStatus();
            startSshTunnel();
        } catch (Exception ex) {
            isWanEnabled = false;
            if (wanForwardServer != null) {
                wanForwardServer.stop(0);
                wanForwardServer = null;
            }
            currentWanUrl = "[本机转发服务启动失败] " + ex.getMessage();
            wanUrlField.setText(currentWanUrl);
            toggleWanBtn.setText("启动 WAN 穿透");
            updateFileStatus();
            engineIndicator.repaint();
        }
    }

    private void stopWanSharing() {
        isWanEnabled = false;
        killSshProcess();
        if (wanForwardServer != null) {
            wanForwardServer.stop(0);
            wanForwardServer = null;
        }
        currentWanUrl = "WAN穿透已关闭";
        wanUrlField.setText(currentWanUrl);
        toggleWanBtn.setText("启动 WAN 穿透");
        updateFileStatus();
        engineIndicator.repaint();
    }

    private void killSshProcess() {
        synchronized (CloudSharePanel.class) {
            sshGeneration++;
            Process process = sshProcess;
            sshProcess = null;
            isSshChecking = false;
            isSshReady = false;
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void redeploySshTunnel() {
        if (!isWanEnabled || wanForwardServer == null) return;
        killSshProcess();
        startSshTunnel();
    }

    private File resolveSshEngineFile() {
        String userDir = System.getProperty("user.dir");
        File[] candidates = new File[]{
                new File(userDir, "app/engines/openssh/ssh.exe"),
                new File(userDir, "engines/openssh/ssh.exe"),
                new File(new File(userDir).getParentFile(), "app/engines/openssh/ssh.exe"),
                new File(new File(userDir).getParentFile(), "engines/openssh/ssh.exe")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return candidates[0];
    }

    private void setWanStatus(long generation, String status, boolean checking, boolean ready) {
        synchronized (CloudSharePanel.class) {
            if (generation != sshGeneration || !isWanEnabled) return;
            currentWanUrl = status;
            isSshChecking = checking;
            isSshReady = ready;
        }
        SwingUtilities.invokeLater(() -> {
            if (generation != sshGeneration || !isWanEnabled) return;
            if (wanUrlField != null) wanUrlField.setText(currentWanUrl);
            if (engineIndicator != null) engineIndicator.repaint();
            updateFileStatus();
        });
    }

    private void startSshTunnel() {
        if (!isWanEnabled || wanForwardServer == null) return;

        final long generation;
        synchronized (CloudSharePanel.class) {
            generation = ++sshGeneration;
            isSshChecking = true;
            isSshReady = false;
        }
        currentWanUrl = "正在向公网节点请求 SSH 映射...";
        wanUrlField.setText(currentWanUrl);
        engineIndicator.repaint();

        Thread.ofVirtual().name("CloudShare-SshTunnel").start(() -> {
            Process process = null;
            boolean urlFound = false;
            try {
                File sshFile = resolveSshEngineFile();
                if (!sshFile.exists()) {
                    throw new FileNotFoundException("未找到内置 SSH 引擎: " + sshFile.getAbsolutePath());
                }

                ProcessBuilder builder = new ProcessBuilder(
                        sshFile.getAbsolutePath(),
                        "-o", "StrictHostKeyChecking=no",
                        "-o", "BatchMode=yes",
                        "-R", "80:127.0.0.1:" + WAN_FORWARD_PORT,
                        "nokey@localhost.run"
                );
                builder.redirectErrorStream(true);
                process = builder.start();

                synchronized (CloudSharePanel.class) {
                    if (generation != sshGeneration || !isWanEnabled) {
                        process.destroyForcibly();
                        return;
                    }
                    sshProcess = process;
                }

                process.getOutputStream().close();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    Pattern urlPattern = Pattern.compile("(https?://[a-zA-Z0-9-]+\\.lhr\\.life)");
                    while ((line = reader.readLine()) != null) {
                        if (generation != sshGeneration || !isWanEnabled) {
                            process.destroyForcibly();
                            return;
                        }
                        if (!urlFound) {
                            Matcher matcher = urlPattern.matcher(line);
                            if (matcher.find()) {
                                String publicUrl = matcher.group(1) + "/";
                                urlFound = true;
                                setWanStatus(generation, publicUrl, false, true);
                            }
                        }
                    }
                }

                if (generation == sshGeneration && isWanEnabled) {
                    String endStatus = urlFound ? "[隧道已断开]" : "[未获取到公网地址]";
                    setWanStatus(generation, endStatus, false, false);
                }
            } catch (Exception e) {
                setWanStatus(generation, "[调度异常] " + e.getMessage(), false, false);
            } finally {
                if (process != null) {
                    synchronized (CloudSharePanel.class) {
                        if (sshProcess == process) sshProcess = null;
                    }
                }
            }
        });
    }

    class CloudEngineIndicator extends JComponent {
        private Timer pulseTimer;
        private double pulseAngle = 0;

        public CloudEngineIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            pulseTimer = new Timer(50, e -> {
                if (isSshChecking || isSshReady) {
                    pulseAngle += 0.1;
                    repaint();
                }
            });
            pulseTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            Color cloudColor;
            Color glowColor;

            if (isSshReady) {
                cloudColor = new Color(52, 152, 219);
                glowColor = new Color(52, 152, 219, 80);
            } else if (isSshChecking) {
                cloudColor = new Color(243, 156, 18);
                glowColor = new Color(243, 156, 18, 60);
            } else {
                cloudColor = new Color(110, 115, 120);
                glowColor = new Color(110, 115, 120, 30);
            }

            if (isSshReady || isSshChecking) {
                int pulseRadius = 13 + (int)(Math.sin(pulseAngle) * 3);
                g2.setColor(new Color(cloudColor.getRed(), cloudColor.getGreen(), cloudColor.getBlue(), 30));
                g2.fillOval(cx - pulseRadius, cy - pulseRadius, pulseRadius * 2, pulseRadius * 2);
            }

            g2.setColor(glowColor);
            g2.fillOval(cx - 15, cy - 15, 30, 30);

            g2.setColor(cloudColor);
            int x = cx - 11;
            int y = cy - 5;
            g2.fillRoundRect(x, y + 4, 22, 8, 8, 8);
            g2.fillOval(x + 2, y - 3, 12, 12);
            g2.fillOval(x + 11, y, 9, 9);

            g2.dispose();
        }
    }

    static class IndexHandler implements HttpHandler {
        private final List<File> files;

        public IndexHandler(List<File> files) {
            this.files = files;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long totalBytes = 0;
            for (File f : files) {
                totalBytes += calculateFolderSize(f);
            }
            String totalSizeStr = formatSize(totalBytes);

            StringBuilder html = new StringBuilder(16384);
            html.append("<!DOCTYPE html><html lang='zh-CN'><head><meta charset='UTF-8'>")
                    .append("<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover'>")
                    .append("<title>ToolKit · 极速云传</title>")
                    .append("<link rel='shortcut icon' href='/favicon.ico' type='image/x-icon'>")
                    .append("<style>")
                    .append(":root { --glass-bg: rgba(18, 24, 38, 0.62); --glass-hover: rgba(28, 38, 58, 0.78); --glass-border: rgba(255, 255, 255, 0.12); --glass-highlight: rgba(56, 189, 248, 0.45); --accent: #38bdf8; --accent-glow: rgba(56, 189, 248, 0.35); --emerald: #34d399; --text-main: #f1f5f9; --text-sub: #94a3b8; }")
                    .append("* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }")
                    .append("html { width: 100%; max-width: 100vw; overflow-x: hidden; }")
                    .append("body { margin: 0; width: 100%; max-width: 100vw; min-height: 100vh; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', sans-serif; background: #050811; color: var(--text-main); overflow-x: hidden; position: relative; }")

                    // 深空行星弧光与背景画布
                    .append("#starCanvas { position: fixed; inset: 0; width: 100%; height: 100%; z-index: 0; pointer-events: none; }")
                    .append(".planet-horizon { position: fixed; top: -380px; left: 50%; transform: translateX(-50%); width: 1300px; max-width: 100vw; height: 520px; border-radius: 50%; background: radial-gradient(ellipse at center, rgba(56, 189, 248, 0.22) 0%, rgba(30, 64, 175, 0.12) 45%, transparent 72%); filter: blur(35px); z-index: 0; pointer-events: none; }")
                    .append(".planet-rim { position: fixed; bottom: -480px; left: 50%; transform: translateX(-50%); width: 100vw; height: 600px; border-radius: 50%; background: radial-gradient(circle at 50% 0%, rgba(14, 165, 233, 0.14), rgba(2, 6, 23, 0.95) 65%); border-top: 1px solid rgba(125, 211, 252, 0.22); box-shadow: 0 -15px 60px rgba(56, 189, 248, 0.15); z-index: 0; pointer-events: none; }")

                    // 开场启动特效遮罩层 (Boot Overlay)
                    .append("#bootOverlay { position: fixed; inset: 0; z-index: 9999; background: radial-gradient(circle at center, #0b1326 0%, #03060c 100%); display: flex; flex-direction: column; align-items: center; justify-content: center; transition: opacity 0.65s cubic-bezier(0.16, 1, 0.3, 1), transform 0.65s cubic-bezier(0.16, 1, 0.3, 1); }")
                    .append("#bootOverlay.boot-hidden { opacity: 0; transform: scale(1.06); pointer-events: none; }")
                    .append(".boot-core { position: relative; width: 96px; height: 96px; display: flex; align-items: center; justify-content: center; margin-bottom: 24px; }")
                    .append(".orbit-ring { position: absolute; inset: 0; border-radius: 50%; border: 1.5px solid transparent; border-top-color: #38bdf8; border-right-color: rgba(56, 189, 248, 0.3); animation: spin 1.4s linear infinite; box-shadow: 0 0 25px rgba(56, 189, 248, 0.25); }")
                    .append(".orbit-ring-inner { position: absolute; inset: 12px; border-radius: 50%; border: 1.5px dashed rgba(125, 211, 252, 0.55); animation: spinReverse 2.2s linear infinite; }")
                    .append(".core-cloud { font-size: 34px; filter: drop-shadow(0 0 12px rgba(56, 189, 248, 0.8)); animation: pulseCore 1.5s ease-in-out infinite; }")
                    .append(".boot-title { font-size: 1.35rem; font-weight: 700; letter-spacing: 6px; color: #fff; text-shadow: 0 0 18px rgba(56, 189, 248, 0.6); margin-bottom: 8px; }")
                    .append(".boot-sub { font-size: 0.78rem; letter-spacing: 3px; color: #7dd3fc; opacity: 0.85; font-family: 'Consolas', monospace; margin-bottom: 20px; }")
                    .append(".boot-bar-wrap { width: 180px; height: 3px; background: rgba(255,255,255,0.1); border-radius: 4px; overflow: hidden; }")
                    .append(".boot-bar { width: 0%; height: 100%; background: linear-gradient(90deg, #38bdf8, #818cf8, #38bdf8); box-shadow: 0 0 10px #38bdf8; animation: loadBar 0.85s cubic-bezier(0.22, 1, 0.36, 1) forwards; }")

                    // 主容器与入场动画
                    .append(".container { position: relative; z-index: 1; width: 100%; max-width: 820px; margin: 0 auto; padding: 28px 18px 60px; box-sizing: border-box; opacity: 0; transform: translateY(22px); transition: opacity 0.7s ease, transform 0.7s cubic-bezier(0.16, 1, 0.3, 1); }")
                    .append("body.ready .container { opacity: 1; transform: translateY(0); }")

                    // 顶部状态导航栏（复刻桌面端顶栏与脉冲云指示器）
                    .append(".top-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; background: var(--glass-bg); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid var(--glass-border); border-radius: 16px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.12); margin-bottom: 22px; width: 100%; min-width: 0; box-sizing: border-box; }")
                    .append(".brand-left { display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1; }")
                    .append(".engine-dot-wrap { position: relative; width: 36px; height: 36px; border-radius: 50%; background: rgba(56, 189, 248, 0.12); display: flex; align-items: center; justify-content: center; border: 1px solid rgba(56, 189, 248, 0.35); flex-shrink: 0; }")
                    .append(".engine-dot-wrap::after { content: ''; position: absolute; inset: -4px; border-radius: 50%; border: 1px solid rgba(56, 189, 248, 0.45); animation: radarPulse 2s infinite; }")
                    .append(".brand-title { font-weight: 700; font-size: 1.05rem; letter-spacing: 1px; color: #fff; }")
                    .append(".brand-status { font-size: 0.76rem; color: #7dd3fc; display: flex; align-items: center; gap: 6px; margin-top: 2px; }")
                    .append(".status-pill { padding: 6px 14px; border-radius: 999px; background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12); font-size: 0.8rem; color: #cbd5e1; font-weight: 600; white-space: nowrap; flex-shrink: 0; margin-left: 8px; }")

                    // 毛玻璃拖拽上传舱（复刻桌面端 GlassDropPanel）
                    .append(".upload-dock { position: relative; background: var(--glass-bg); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1.5px dashed rgba(125, 211, 252, 0.32); border-radius: 18px; padding: 26px 20px; text-align: center; cursor: pointer; transition: all 0.28s cubic-bezier(0.16, 1, 0.3, 1); box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.08); margin-bottom: 24px; overflow: hidden; width: 100%; min-width: 0; box-sizing: border-box; }")
                    .append(".upload-dock:hover, .upload-dock.drag-active { background: rgba(24, 36, 58, 0.78); border-color: var(--accent); transform: translateY(-2px); box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45), 0 0 25px rgba(56, 189, 248, 0.18); }")
                    .append(".upload-icon { font-size: 2rem; margin-bottom: 8px; display: inline-block; transition: transform 0.3s ease; filter: drop-shadow(0 4px 12px rgba(56, 189, 248, 0.4)); }")
                    .append(".upload-dock:hover .upload-icon { transform: translateY(-4px) scale(1.06); }")
                    .append(".upload-title { font-size: 1.05rem; font-weight: 600; color: #fff; margin: 0 0 4px; letter-spacing: 0.5px; word-break: break-all; overflow-wrap: anywhere; }")
                    .append(".upload-hint { font-size: 0.82rem; color: var(--text-sub); margin: 0; word-break: break-all; overflow-wrap: anywhere; }")
                    .append(".progress-wrap { display: none; margin-top: 14px; background: rgba(15, 23, 42, 0.75); border-radius: 999px; height: 8px; overflow: hidden; border: 1px solid rgba(255,255,255,0.1); }")
                    .append(".progress-bar { width: 0%; height: 100%; background: linear-gradient(90deg, #38bdf8, #34d399); border-radius: 999px; transition: width 0.15s ease; box-shadow: 0 0 12px rgba(56, 189, 248, 0.6); }")

                    // 文件列表与毛玻璃卡片
                    .append(".section-head { display: flex; align-items: center; justify-content: space-between; margin: 0 4px 12px; font-size: 0.85rem; color: var(--text-sub); font-weight: 600; letter-spacing: 1px; width: 100%; min-width: 0; flex-wrap: wrap; gap: 6px; }")
                    .append(".file-list { display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; width: 100%; min-width: 0; }")
                    .append(".file-card { background: var(--glass-bg); backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); border-radius: 15px; padding: 15px 20px; display: flex; align-items: center; border: 1px solid var(--glass-border); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.32), inset 0 1px 0 rgba(255, 255, 255, 0.08); transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1); opacity: 0; transform: translateY(16px); width: 100%; min-width: 0; box-sizing: border-box; }")
                    .append("body.ready .file-card { animation: cardFadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) forwards; }")
                    .append(".file-card:hover { background: var(--glass-hover); border-color: var(--glass-highlight); transform: translateY(-3px); box-shadow: 0 14px 32px rgba(0, 0, 0, 0.48), 0 0 20px rgba(56, 189, 248, 0.12); }")
                    .append(".icon-box { width: 46px; height: 46px; border-radius: 12px; background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.1); display: flex; align-items: center; justify-content: center; font-size: 23px; margin-right: 16px; flex-shrink: 0; box-shadow: inset 0 1px 0 rgba(255,255,255,0.12); }")
                    .append(".info { flex: 1 1 auto; min-width: 0; overflow: hidden; }")
                    .append(".name { font-weight: 600; font-size: 1rem; margin: 0 0 5px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #f8fafc; letter-spacing: 0.2px; min-width: 0; word-break: break-all; overflow-wrap: anywhere; cursor: pointer; }")
                    .append(".name.expanded { display: block; -webkit-line-clamp: unset; white-space: normal; }")
                    .append(".meta { font-size: 0.8rem; color: var(--text-sub); margin: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; min-width: 0; }")
                    .append(".badge { display: inline-block; padding: 1px 7px; border-radius: 5px; background: rgba(56, 189, 248, 0.12); color: #7dd3fc; border: 1px solid rgba(56, 189, 248, 0.22); font-size: 0.72rem; font-weight: 600; flex-shrink: 0; }")
                    .append(".actions { display: flex; gap: 8px; margin-left: 14px; flex-shrink: 0; }")
                    .append(".btn { padding: 8px 15px; border-radius: 10px; font-size: 0.85rem; font-weight: 600; text-decoration: none; transition: all 0.2s ease; display: inline-flex; align-items: center; gap: 5px; cursor: pointer; white-space: nowrap; }")
                    .append(".btn-view { background: rgba(255, 255, 255, 0.07); color: #e2e8f0; border: 1px solid rgba(255, 255, 255, 0.15); }")
                    .append(".btn-view:hover { background: rgba(255, 255, 255, 0.15); border-color: rgba(255, 255, 255, 0.3); color: #fff; }")
                    .append(".btn-down { background: linear-gradient(135deg, rgba(56, 189, 248, 0.28), rgba(37, 99, 235, 0.45)); color: #fff; border: 1px solid rgba(56, 189, 248, 0.45); box-shadow: 0 4px 12px rgba(2, 132, 199, 0.25); }")
                    .append(".btn-down:hover { background: linear-gradient(135deg, rgba(56, 189, 248, 0.48), rgba(37, 99, 235, 0.68)); box-shadow: 0 6px 16px rgba(56, 189, 248, 0.35); transform: translateY(-1px); }")

                    // 空状态（呼应桌面端小人酣睡待命意境）
                    .append(".empty-state { text-align: center; padding: 48px 24px; color: var(--text-sub); border: 1px solid var(--glass-border); border-radius: 18px; background: var(--glass-bg); backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); box-shadow: 0 8px 30px rgba(0,0,0,0.3); }")
                    .append(".empty-icon { font-size: 2.6rem; margin-bottom: 10px; display: inline-block; animation: floatZzz 3s ease-in-out infinite; }")

                    // 关键帧动画与移动端适配
                    .append("@keyframes spin { to { transform: rotate(360deg); } }")
                    .append("@keyframes spinReverse { to { transform: rotate(-360deg); } }")
                    .append("@keyframes pulseCore { 0%, 100% { transform: scale(0.95); opacity: 0.85; } 50% { transform: scale(1.08); opacity: 1; } }")
                    .append("@keyframes loadBar { 0% { width: 0%; } 100% { width: 100%; } }")
                    .append("@keyframes radarPulse { 0% { transform: scale(0.95); opacity: 0.8; } 100% { transform: scale(1.45); opacity: 0; } }")
                    .append("@keyframes cardFadeUp { to { opacity: 1; transform: translateY(0); } }")
                    .append("@keyframes floatZzz { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }")
                    .append("@media (max-width: 600px) { ")
                    .append("  .container { padding: 16px 12px 50px; } ")
                    .append("  .top-bar { padding: 10px 12px; flex-wrap: wrap; gap: 8px; } ")
                    .append("  .brand-left { width: 100%; } ")
                    .append("  .brand-title { font-size: 0.98rem; } ")
                    .append("  .brand-status { font-size: 0.72rem; white-space: normal; word-break: break-all; } ")
                    .append("  .status-pill { margin-left: auto; font-size: 0.72rem; padding: 4px 10px; } ")
                    .append("  .file-card { padding: 12px 14px; } ")
                    .append("  .icon-box { width: 38px; height: 38px; font-size: 20px; margin-right: 10px; } ")
                    .append("  .actions { gap: 6px; margin-left: 10px; } ")
                    .append("  .btn { padding: 6px 10px; font-size: 0.78rem; border-radius: 8px; } ")
                    .append("  .name { font-size: 0.92rem; white-space: normal; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.38; word-break: break-all; overflow-wrap: anywhere; } ")
                    .append("  .section-head { flex-direction: column; align-items: flex-start; gap: 4px; font-size: 0.78rem; } ")
                    .append("  .planet-rim { width: 100vw; min-width: 100vw; } ")
                    .append("  .planet-horizon { width: 100vw; } ")
                    .append("}")
                    .append("</style></head><body>")

                    // 1. 深空星尘画布与行星弧光
                    .append("<canvas id='starCanvas'></canvas>")
                    .append("<div class='planet-horizon'></div>")
                    .append("<div class='planet-rim'></div>")

                    // 2. 启动特效层
                    .append("<div id='bootOverlay'>")
                    .append("  <div class='boot-core'><div class='orbit-ring'></div><div class='orbit-ring-inner'></div><div class='core-cloud'>☁️</div></div>")
                    .append("  <div class='boot-title'>TOOLKIT 云传终端</div>")
                    .append("  <div class='boot-sub'>ESTABLISHING QUANTUM STREAM...</div>")
                    .append("  <div class='boot-bar-wrap'><div class='boot-bar'></div></div>")
                    .append("</div>")

                    // 3. 主界面内容
                    .append("<div class='container'>")
                    .append("  <div class='top-bar'>")
                    .append("    <div class='brand-left'>")
                    .append("      <div class='engine-dot-wrap'>☁️</div>")
                    .append("      <div style='min-width:0; flex:1;'>")
                    .append("        <div class='brand-title'>ToolKit · 极速云传</div>")
                    .append("        <div class='brand-status'><span style='color:#34d399;'>●</span> 安全加密链路已映射 · 实时双向互传</div>")
                    .append("      </div>")
                    .append("    </div>")
                    .append("    <div class='status-pill'>已挂载 ").append(files.size()).append(" 项 · ").append(totalSizeStr).append("</div>")
                    .append("  </div>")

                    // 上传交互舱
                    .append("  <input type='file' id='fileUploader' multiple style='display:none;' onchange='handleFiles(this.files)'>")
                    .append("  <div id='uploadDock' class='upload-dock' onclick='document.getElementById(\"fileUploader\").click()'>")
                    .append("    <div class='upload-icon'>🚀</div>")
                    .append("    <p id='uploadTitle' class='upload-title'>点击选择手机文件，或直接将文件拖入此处上传至电脑</p>")
                    .append("    <p id='uploadHint' class='upload-hint'>支持多文件并发回传 · 自动落盘至电脑 Downloads/CloudShareUploads 目录</p>")
                    .append("    <div id='progressWrap' class='progress-wrap'><div id='progressBar' class='progress-bar'></div></div>")
                    .append("  </div>")

                    .append("  <div class='section-head'><span>SHARED REPOSITORY · 云端挂载列表</span><span>双击桌面端可清空</span></div>")
                    .append("  <div class='file-list'>");

            if (files.isEmpty()) {
                html.append("<div class='empty-state'>")
                        .append("  <div class='empty-icon'>💤</div>")
                        .append("  <div style='font-size:1.02rem; color:#e2e8f0; font-weight:600; margin-bottom:6px;'>主机小人正在趴桌酣睡，暂未挂载文件</div>")
                        .append("  <div style='font-size:0.84rem;'>请在电脑端拖入文件唤醒挂载，或直接点击上方区域从手机上传文件</div>")
                        .append("</div>");
            } else {
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    String rawName = f.getName();
                    String safeName = escapeHtml(rawName);
                    boolean isDir = f.isDirectory();
                    String ext = getExtension(rawName).toLowerCase();

                    String icon = isDir ? "📁" : "📄";
                    String typeTag = isDir ? "目录" : (ext.isEmpty() ? "文件" : ext.toUpperCase());
                    if (!isDir) {
                        if (ext.matches("(jpg|jpeg|png|gif|webp|bmp|svg)")) icon = "🖼️";
                        else if (ext.matches("(zip|rar|7z|tar|gz)")) icon = "📦";
                        else if (ext.matches("(mp4|mkv|avi|mov|webm)")) icon = "🎬";
                        else if (ext.matches("(mp3|wav|flac|aac|ogg)")) icon = "🎵";
                        else if (ext.matches("(java|py|json|xml|html|css|js|ts|c|cpp|sql|sh)")) icon = "🧑‍💻";
                        else if (ext.matches("(pdf|doc|docx|xls|xlsx|ppt|pptx)")) icon = "📑";
                    }

                    String sizeInfo = isDir ? formatSize(calculateFolderSize(f)) : formatSize(f.length());
                    boolean canPreview = !isDir && ext.matches("(jpg|jpeg|png|gif|webp|bmp|txt|pdf)");
                    double delaySec = Math.min(0.6, i * 0.055);

                    html.append("<div class='file-card' style='animation-delay:").append(String.format(java.util.Locale.US, "%.2fs", delaySec)).append(";'>")
                            .append("  <div class='icon-box'>").append(icon).append("</div>")
                            .append("  <div class='info'>")
                            .append("    <p class='name' title='").append(safeName).append("' onclick='this.classList.toggle(\"expanded\")'>").append(safeName).append("</p>")
                            .append("    <p class='meta'><span class='badge'>").append(escapeHtml(typeTag)).append("</span><span>").append(sizeInfo).append("</span></p>")
                            .append("  </div>")
                            .append("  <div class='actions'>");

                    if (canPreview) {
                        html.append("<a href='/view?id=").append(i).append("' class='btn btn-view' target='_blank'>预览</a>");
                    }
                    html.append("<a href='/download?id=").append(i).append("' class='btn btn-down'>⬇ 下载</a>")
                            .append("  </div></div>");
                }
            }

            html.append("  </div></div>")
                    .append("<script>")
                    // 1. 启动开场控制（首次访问完整展示星轨启动动画，上传后刷新则快速切入）
                    .append("(function(){")
                    .append("  const overlay = document.getElementById('bootOverlay');")
                    .append("  const seen = sessionStorage.getItem('tk_cloud_booted');")
                    .append("  const delay = seen ? 180 : 880;")
                    .append("  if (seen) overlay.style.transitionDuration = '0.3s';")
                    .append("  setTimeout(() => {")
                    .append("    overlay.classList.add('boot-hidden');")
                    .append("    document.body.classList.add('ready');")
                    .append("    sessionStorage.setItem('tk_cloud_booted', '1');")
                    .append("  }, delay);")
                    .append("})();")

                    // 2. 深空动态星尘背景渲染
                    .append("(function(){")
                    .append("  const cvs = document.getElementById('starCanvas');")
                    .append("  const ctx = cvs.getContext('2d');")
                    .append("  let w, h, stars = [];")
                    .append("  function resize(){ w = cvs.width = window.innerWidth; h = cvs.height = window.innerHeight; }")
                    .append("  window.addEventListener('resize', resize); resize();")
                    .append("  for(let i=0;i<75;i++){ stars.push({x:Math.random()*w, y:Math.random()*h, r:Math.random()*1.5+0.4, a:Math.random(), s:Math.random()*0.015+0.004, vy:Math.random()*0.18+0.04}); }")
                    .append("  function draw(){")
                    .append("    ctx.clearRect(0,0,w,h);")
                    .append("    for(const st of stars){")
                    .append("      st.a += st.s; st.y -= st.vy; if(st.y < 0) st.y = h;")
                    .append("      const alpha = (Math.sin(st.a)*0.4 + 0.5) * 0.75;")
                    .append("      ctx.fillStyle = `rgba(186, 230, 253, ${alpha})`;")
                    .append("      ctx.beginPath(); ctx.arc(st.x, st.y, st.r, 0, Math.PI*2); ctx.fill();")
                    .append("    }")
                    .append("    requestAnimationFrame(draw);")
                    .append("  }")
                    .append("  draw();")
                    .append("})();")

                    // 3. 拖拽高亮与带实时百分比进度条的文件上传
                    .append("const dock = document.getElementById('uploadDock');")
                    .append("['dragenter','dragover'].forEach(ev => dock.addEventListener(ev, e => { e.preventDefault(); dock.classList.add('drag-active'); }));")
                    .append("['dragleave','drop'].forEach(ev => dock.addEventListener(ev, e => { e.preventDefault(); dock.classList.remove('drag-active'); }));")
                    .append("dock.addEventListener('drop', e => { if(e.dataTransfer.files.length) handleFiles(e.dataTransfer.files); });")

                    .append("function uploadSingleFile(file, index, total) {")
                    .append("  return new Promise((resolve, reject) => {")
                    .append("    const xhr = new XMLHttpRequest();")
                    .append("    const title = document.getElementById('uploadTitle');")
                    .append("    const hint = document.getElementById('uploadHint');")
                    .append("    const bar = document.getElementById('progressBar');")
                    .append("    xhr.open('POST', '/upload', true);")
                    .append("    xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));")
                    .append("    xhr.upload.onprogress = (e) => {")
                    .append("      if (e.lengthComputable) {")
                    .append("        const pct = Math.round((e.loaded / e.total) * 100);")
                    .append("        bar.style.width = pct + '%';")
                    .append("        title.innerText = `正在跃迁传输 (${index}/${total})：${file.name}`;")
                    .append("        hint.innerText = `当前进度：${pct}% · 请勿关闭页面`;")
                    .append("      }")
                    .append("    };")
                    .append("    xhr.onload = () => (xhr.status === 200 ? resolve() : reject(xhr.responseText));")
                    .append("    xhr.onerror = () => reject('Network Error');")
                    .append("    xhr.send(file);")
                    .append("  });")
                    .append("}")

                    .append("async function handleFiles(files) {")
                    .append("  if (!files || files.length === 0) return;")
                    .append("  const title = document.getElementById('uploadTitle');")
                    .append("  const hint = document.getElementById('uploadHint');")
                    .append("  const wrap = document.getElementById('progressWrap');")
                    .append("  const bar = document.getElementById('progressBar');")
                    .append("  wrap.style.display = 'block';")
                    .append("  try {")
                    .append("    for (let i = 0; i < files.length; i++) {")
                    .append("      bar.style.width = '0%';")
                    .append("      await uploadSingleFile(files[i], i + 1, files.length);")
                    .append("    }")
                    .append("    title.innerText = '✅ 全部文件已成功同步至电脑端！';")
                    .append("    hint.innerText = '正在刷新云端挂载列表...';")
                    .append("    setTimeout(() => location.reload(), 650);")
                    .append("  } catch (err) {")
                    .append("    title.innerText = '❌ 上传中断：' + err;")
                    .append("    hint.innerText = '请检查网络连接后点击重试';")
                    .append("  }")
                    .append("}")
                    .append("</script></body></html>");

            byte[] response = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private String getExtension(String fileName) {
            int idx = fileName.lastIndexOf('.');
            return (idx > 0) ? fileName.substring(idx + 1) : "";
        }

        private String escapeHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }

    static class FileTransportHandler implements HttpHandler {
        private final List<File> files;
        private final boolean isDownload;

        public FileTransportHandler(List<File> files, boolean isDownload) {
            this.files = files;
            this.isDownload = isDownload;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int id = -1;
            if (query != null && query.startsWith("id=")) {
                try { id = Integer.parseInt(query.substring(3)); } catch (Exception ignored) {}
            }

            if (id >= 0 && id < files.size()) {
                File file = files.get(id);
                String encodeName = URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20");

                if (file.isDirectory()) {
                    if (!isDownload) {
                        exchange.sendResponseHeaders(403, -1);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename*=UTF-8''" + encodeName + ".zip");
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, 0);

                    try (OutputStream os = exchange.getResponseBody();
                         java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os)) {
                        zipDirectory(file, file.getName(), zos);
                    }
                    return;
                }

                if (isDownload) {
                    exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename*=UTF-8''" + encodeName);
                } else {
                    exchange.getResponseHeaders().add("Content-Disposition", "inline; filename*=UTF-8''" + encodeName);
                    String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
                    exchange.getResponseHeaders().add("Content-Type", getMimeType(ext));
                }
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fs = new FileInputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = fs.read(buffer)) >= 0) { os.write(buffer, 0, count); }
                }
            } else {
                String error = "404 - 资源已断开连接";
                byte[] errBytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(errBytes); }
            }
        }

        private void zipDirectory(File fileToZip, String fileName, java.util.zip.ZipOutputStream zos) throws IOException {
            if (fileToZip.isHidden()) return;
            if (fileToZip.isDirectory()) {
                if (fileName.endsWith("/")) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(fileName));
                } else {
                    zos.putNextEntry(new java.util.zip.ZipEntry(fileName + "/"));
                }
                zos.closeEntry();
                File[] children = fileToZip.listFiles();
                if (children != null) {
                    for (File childFile : children) {
                        zipDirectory(childFile, fileName + "/" + childFile.getName(), zos);
                    }
                }
                return;
            }
            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                byte[] bytes = new byte[16384];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }
            }
        }

        private String getMimeType(String ext) {
            switch (ext) {
                case "jpg": case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "webp": return "image/webp";
                case "pdf": return "application/pdf";
                case "txt": return "text/plain; charset=utf-8";
                default: return "application/octet-stream";
            }
        }
    }

    static class FaviconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = CloudSharePanel.class.getResourceAsStream("/imgs/logo_app.ico")) {
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = is.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "image/x-icon");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    static class UploadHandler implements HttpHandler {
        private static final long MAX_FILE_SIZE = 50L * 1024 * 1024 * 1024L;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileNameStr = exchange.getRequestHeaders().getFirst("X-File-Name");
                if (fileNameStr == null) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String decodedName = java.net.URLDecoder.decode(fileNameStr, "UTF-8");

                String safeFileName = new File(decodedName).getName();

                if (safeFileName.isEmpty() || safeFileName.equals("..") || safeFileName.equals(".")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                File uploadDir = getUploadDirectory();
                File targetFile = new File(uploadDir, safeFileName);

                long totalBytesRead = 0;
                try (InputStream is = exchange.getRequestBody();
                     FileOutputStream fos = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = is.read(buffer)) >= 0) {
                        totalBytesRead += count;
                        if (totalBytesRead > MAX_FILE_SIZE) {
                            fos.close();
                            targetFile.delete();
                            String error = "Error: File exceeds maximum allowed size of 50GB!";
                            byte[] errBytes = error.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(413, errBytes.length);
                            try (OutputStream os = exchange.getResponseBody()) { os.write(errBytes); }
                            return;
                        }
                        fos.write(buffer, 0, count);
                    }
                }

                folderSizeCache.remove(targetFile);
                if (!sharedFiles.contains(targetFile)) {
                    sharedFiles.add(targetFile);
                }

                String response = "Upload Success";
                byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }

                SwingUtilities.invokeLater(() -> {
                    if (currentInstance != null) {
                        currentInstance.updateFileStatus();
                    }
                });
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}