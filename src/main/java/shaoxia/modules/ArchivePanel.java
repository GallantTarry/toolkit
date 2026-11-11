package shaoxia.modules;

import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassComboBox;
import shaoxia.Utils.GlassDropPanel;
import shaoxia.Utils.GlassTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 解压缩助手面板
 * 窗口尺寸 1:1 对齐 ScoringPanel + 右上角同尺寸怀表密码设置 + 56x27 精致微缩比例像素点阵 + 直调 app/tools/UnRAR.exe
 */
public class ArchivePanel extends BackgroundPanel {

    private final MainLauncher parent;

    // 顶部栏左侧按钮与右上角怀表
    private JPanel leftTop;
    private ActionButton backBtn;
    private PocketWatchIndicator watchIndicator;

    // 解压缩密码（为空字符串表示未设置密码）
    private volatile String archivePassword = "";

    // 解压缩助手相关组件与状态
    private JLabel dl;
    private GlassDropPanel dropArea;
    private boolean isExtractMode = true;
    private volatile boolean isProcessing = false;
    private volatile int currentProgress = 0;

    // 拖拽左右半区状态：0-未拖拽(默认静止)，1-鼠标在左侧(左半边闪烁)，2-鼠标在右侧(右半边闪烁)
    private volatile int dragHighlightSide = 0;

    // 点阵像素闪烁控制参数（默认完全静止，仅在拖拽悬停或播放音乐时驱动）
    private volatile boolean isPlaying = false;
    private javazoom.jl.player.Player player;
    private javax.swing.Timer animTimer;
    private int animTick = 0;

    // 缓存解析出的 UnRAR.exe 可执行文件
    private File cachedUnRarExe = null;

    // 超时空之轮史诗短句（处理进度时显示）
    private static final String[] ARCHIVE_PHRASES = {
            "Crossing the winds of time on Zenan Bridge,",
            "Crono's lightning cleaves the crimson scales,",
            "Unleashing the dual tech against the Dragon,",
            "The pendulum of eternity swings."
    };

    // 保留供 MainLauncher 反射兼容的 4 个占位字段（不在界面显示）
    private boolean isMonitoring = false;
    private GlassTextField searchField = new GlassTextField();
    private GlassComboBox<String> categoryCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(new String[]{"默认"}));
    private JTextArea detailArea = new JTextArea();

    // =========================================================================
    // 56x27 精致微缩比例像素点阵（严格 56 列 × 27 行）：
    // 左侧：小巧高马尾少女马尔举弩 + 刺猬红发勇者克罗诺跨步挥刀
    // 中央：冰雷双人技光束与魔龙烈焰吐息在桥面半空激撞
    // 右侧：锯齿巨翼展开、张口露出上下利齿咆哮的赤红魔龙
    // 底部：泽南四孔古石桥与湍急河水
    // =========================================================================
    private static final String[] CHRONO_BATTLE_MATRIX = {
            "..+.........*............+..............*..........+....",
            "......*...........+.............*..........VVVVVV.......",
            "....................*....................VVvvvvvvVV..*..",
            "....*...................+...............VvvvvVvvvvvVV...",
            "..............*........................VvvvvVvvvvvvvV...",
            ".........+.........................A.AAVvvvVvvvvVvvvvV..",
            "...............R...........*......AAMMMMVvvVvvvvVvvvvvV.",
            ".............R.RR.R....+.........MMMEEMMmVvVvvvvVvvVvvV.",
            "............rRRRRRR.......LL...FFDDDDMMMMVVvv..Vvv..Vvv.",
            "....YY.......rWWWWR.....llLWFFOOOFF..mMMM.V....V....V...",
            "...YYyYY......KKKK..D.llllLWWFF.DDDDMMMMm...............",
            "..yy.WWY.....YYYYY.DDlll..LL........mPMMMm.......*......",
            ".....KKK....WSSSSWADll..............PPMMMMMm............",
            ".....JJWA....SSSS..ll....+.......ADDPPMMMMMm.......A....",
            "....WJJDD....BBBB...................PPMMMMMMm.....MA....",
            ".....WW.....BB..BB...................PPMMMMMMm...MMm....",
            ".....J.J...WW....WW..................MMMM..MMMM.mMM.....",
            "....bb.bb.bbb....bbb...............ADMMM..ADMMMMMM......",
            "..HHHHH...HHHHH...HHHHH...HHHHH...HHHHH...HHHHH...HHHH..",
            ".HNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNH.",
            ".CCCCCCC====CCCCCCCC====CCCCCCCC====CCCCCCCC====CCCCCCC.",
            ".CCCCCCccccccCCCCCCccccccCCCCCCccccccCCCCCCccccccCCCCCC.",
            "..CCCCccXXXXccCCCCccXXXXccCCCCccXXXXccCCCCccXXXXccCCCC..",
            "..CCCcc~O~~O~ccCCcc~O~~O~ccCCcc~O~~O~ccCCcc~O~~O~ccCCC..",
            "..CCcc~~*==*~~cccc~~*==*~~cccc~~*==*~~cccc~~*==*~~ccCC..",
            "~~~O~~~~~==~~~~~O~~~~==~~~~O~~~~==~~~~O~~~~~==~~~~~O~~~~",
            "~~~~~~*~~~~~~~~~~~*~~~~~~~~~~~*~~~~~~~~~~~*~~~~~~~~~~*~~"
    };

    public ArchivePanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);

        initTopBar();
        add(createArchiveCard(), BorderLayout.CENTER);

        // 窗口缩放自适应重绘
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustFontSize();
            }
        });
    }

    /**
     * 初始化顶部导航栏（左侧返回按钮 + 右上角与 ScoringPanel 同尺寸的怀表密码按钮）
     */
    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));
        topBar.setPreferredSize(new Dimension(0, 56));

        leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);

        backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic();
            parent.showMenu();
        });
        leftTop.add(backBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        // 右上角怀表图标（尺寸与位置对标 ScoringPanel 的 SheepIndicator）
        watchIndicator = new PocketWatchIndicator();

        watchIndicator.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPasswordDialog();
            }
        });
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.setOpaque(false);
        rightTop.add(watchIndicator);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
    }

    /**
     * 弹出怀表密码设置对话框（供解压与压缩共用）
     */
    private void showPasswordDialog() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel tipLabel = new JLabel("请输入解压/压缩密码（留空则清除密码）：");
        tipLabel.setFont(FontUtils.getChineseFont(Font.PLAIN, 13));

        JTextField pwdField = new JTextField(archivePassword, 18);
        pwdField.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));

        panel.add(tipLabel, BorderLayout.NORTH);
        panel.add(pwdField, BorderLayout.CENTER);

        Object[] options = {"保存设置", "清空密码", "取消"};
        int result = JOptionPane.showOptionDialog(
                this,
                panel,
                "时空怀表 · 密码设置",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        if (result == JOptionPane.YES_OPTION) {
            archivePassword = pwdField.getText() == null ? "" : pwdField.getText().trim();
        } else if (result == JOptionPane.NO_OPTION) {
            archivePassword = "";
        } else {
            return;
        }

        boolean hasPwd = !archivePassword.isEmpty();
        watchIndicator.setPasswordActive(hasPwd);



    }

    private JPanel createArchiveCard() {
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(40, 80, 80, 80));

        dropArea = new GlassDropPanel();
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dropArea.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 点击切换全图闪烁并播放 resources/music/燃起来吧!.mp3
        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isProcessing) {
                    toggleMusic();
                }
            }
        });

        dl = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (isProcessing) {
                    paintProcessingView(g);
                    return;
                }
                paintChronoDotMatrix(g);
            }
        };
        dropArea.add(dl, BorderLayout.CENTER);

        setArchiveDropTarget(dropArea);
        centerPanel.add(dropArea, BorderLayout.CENTER);

        return centerPanel;
    }

    /**
     * 响应式刷新处理视图与点阵画布（重构为 Direct Java2D 渲染，彻底消除 HTML 动态解析造成的字体震颤与闪烁）
     */
    private void adjustFontSize() {
        if (dl != null) {
            if (!dl.getText().isEmpty()) {
                dl.setText("");
            }
            dl.repaint();
        }
    }

    /**
     * 高性能无抖动处理中进度与文字渲染器 (原生像素级精准绘制，排版稳健、字号适度、彻底根除抖动)
     */
    private void paintProcessingView(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = dl.getWidth();
        int h = dl.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int centerY = h / 2;

        // 1. 顶部操作状态标题（小巧精致，14px 思源黑体，清晰不刺眼）
        String actionTitle = isExtractMode ? "正在批量解压归档文件..." : "正在批量归档压缩文件...";
        Font titleFont = FontUtils.getChineseFont(Font.BOLD, 14);
        Color titleColor = isExtractMode ? new Color(125, 211, 252, 230) : new Color(253, 164, 175, 230);
        g2.setFont(titleFont);
        FontMetrics fmTitle = g2.getFontMetrics();
        int titleX = (w - fmTitle.stringWidth(actionTitle)) / 2;
        int titleY = centerY - 38;
        g2.setColor(titleColor);
        g2.drawString(actionTitle, titleX, titleY);

        // 2. 居中数字进度百分比（现代稳健数字 Inter 24px，恒定居中永不跳动）
        String pctText = currentProgress + "%";
        Font pctFont = FontUtils.getEnglishFont(Font.BOLD, 24);
        g2.setFont(pctFont);
        FontMetrics fmPct = g2.getFontMetrics();
        int pctX = (w - fmPct.stringWidth(pctText)) / 2;
        int pctY = centerY - 4;
        g2.setColor(Color.WHITE);
        g2.drawString(pctText, pctX, pctY);

        // 3. 纤薄现代渐变微型进度条（宽 220px，高 4px）
        int barW = Math.min(220, w - 80);
        int barH = 4;
        int barX = (w - barW) / 2;
        int barY = centerY + 12;
        int fillW = (int) (barW * (Math.max(0, Math.min(100, currentProgress)) / 100.0));

        // 进度条背景槽
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRoundRect(barX, barY, barW, barH, barH, barH);

        // 进度条高亮渐变填充
        if (fillW > 0) {
            GradientPaint barGrad = new GradientPaint(
                    barX, barY,
                    isExtractMode ? new Color(56, 189, 248) : new Color(244, 63, 94),
                    barX + barW, barY,
                    isExtractMode ? new Color(129, 140, 248) : new Color(251, 146, 60)
            );
            g2.setPaint(barGrad);
            g2.fillRoundRect(barX, barY, fillW, barH, barH, barH);
        }

        // 4. 底部微缩史诗短句（紧凑精致，字号降为 12px 斜体，排版典雅低调，不再狂抖遮挡界面）
        int stage = Math.min(3, Math.max(0, currentProgress / 25));
        String phrase = ARCHIVE_PHRASES[stage];
        Font phraseFont = FontUtils.getEnglishFont(Font.ITALIC, 12);
        g2.setFont(phraseFont);
        FontMetrics fmPhrase = g2.getFontMetrics();
        int phraseX = (w - fmPhrase.stringWidth(phrase)) / 2;
        int phraseY = centerY + 36;
        g2.setColor(new Color(226, 232, 240, 150));
        g2.drawString(phrase, phraseX, phraseY);

        g2.dispose();
    }

    /**
     * 点阵调色盘映射（默认静止，拖左仅左闪，拖右仅右闪，播放音乐全闪，无任何范围光晕）
     */
    private Color getDotColor(char ch, int row, int col, int midCol) {
        boolean isLeft = (col < midCol);
        boolean active = isPlaying || (dragHighlightSide == 1 && isLeft) || (dragHighlightSide == 2 && !isLeft);

        boolean shimmer = active && ((row + col + animTick) % 3 == 0);
        boolean waveShift = active && ((col + animTick) % 4 == 0);
        boolean beamShift = active && ((col - animTick * 2) % 4 == 0);

        switch (ch) {
            case '*':
                return shimmer ? new Color(255, 255, 255) : new Color(210, 235, 255, 200);
            case '+':
                return shimmer ? new Color(224, 242, 254) : new Color(125, 211, 252, 195);
            case 'R':
                return shimmer ? new Color(252, 129, 129) : new Color(220, 38, 38);
            case 'r':
                return new Color(185, 28, 28);
            case 'W':
                return shimmer ? new Color(255, 255, 255) : new Color(241, 245, 249);
            case 'K':
                return new Color(254, 215, 170);
            case 'Y':
                return shimmer ? new Color(254, 249, 195) : new Color(250, 204, 21);
            case 'y':
                return new Color(217, 119, 6);
            case 'J':
                return shimmer ? new Color(251, 146, 60) : new Color(249, 115, 22);
            case 'S':
                return shimmer ? new Color(45, 212, 191) : new Color(13, 148, 136);
            case 's':
                return new Color(15, 118, 110);
            case 'B':
                return shimmer ? new Color(96, 165, 250) : new Color(37, 99, 235);
            case 'b':
                return new Color(120, 53, 15);
            case 'A':
                return shimmer ? new Color(254, 240, 138) : new Color(245, 158, 11);
            case 'D':
                return shimmer ? new Color(255, 255, 255) : new Color(224, 242, 254);
            case 'L':
                return shimmer ? new Color(255, 255, 255) : new Color(254, 240, 138);
            case 'l':
                return beamShift
                        ? new Color(125, 211, 252, 235)
                        : new Color(254, 240, 138, 120);
            case 'M':
                return shimmer ? new Color(248, 113, 113) : new Color(220, 38, 38);
            case 'm':
                return new Color(153, 27, 27);
            case 'P':
                return shimmer ? new Color(254, 240, 138) : new Color(251, 191, 36);
            case 'E':
                return shimmer ? new Color(255, 255, 255) : new Color(253, 224, 71);
            case 'V':
                return shimmer ? new Color(244, 63, 94) : new Color(190, 18, 60);
            case 'v':
                return new Color(136, 19, 55);
            case 'F':
                return shimmer ? new Color(253, 224, 71) : new Color(239, 68, 68);
            case 'H':
                return new Color(226, 232, 240);
            case 'N':
                return new Color(148, 163, 184);
            case 'C':
                return new Color(100, 116, 139);
            case 'c':
                return new Color(71, 85, 105);
            case 'X':
                return new Color(30, 41, 59);
            case '=':
                return (shimmer || waveShift) ? new Color(165, 243, 252, 245) : new Color(56, 189, 248, 195);
            case 'O':
                return shimmer ? new Color(254, 240, 138, 235) : new Color(56, 189, 248, 185);
            case '~':
                return waveShift ? new Color(14, 116, 144, 235) : new Color(12, 74, 110, 205);
            default:
                return null;
        }
    }

    /**
     * 核心点阵渲染器：绘制 56x27 像素点阵
     */
    private void paintChronoDotMatrix(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = dl.getWidth();
        int h = dl.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = CHRONO_BATTLE_MATRIX.length;
        int cols = CHRONO_BATTLE_MATRIX[0].length();
        int midCol = cols / 2;

        int cellSize = Math.max(3, Math.min(w / (cols + 6), (h - 28) / (rows + 2)));
        int gap = cellSize >= 5 ? 1 : 0;
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH - (cellSize * 2)) / 2;

        for (int r = 0; r < rows; r++) {
            String line = CHRONO_BATTLE_MATRIX[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;

                Color color = getDotColor(ch, r, c, midCol);
                if (color != null) {
                    int px = startX + c * cellSize;
                    int py = startY + r * cellSize;
                    g2.setColor(color);
                    g2.fillRoundRect(px, py, dotSize, dotSize, arc, arc);
                }
            }
        }

        // 拖入文件停滞时：左右上方同时显示解压与压缩提示
        if (dragHighlightSide == 1 || dragHighlightSide == 2) {
            paintLeftRightDragHints(g2, w, Math.max(16, startY - 8));
        }

        // 底部微缩点阵铭文
        int fontSize = Math.max(11, (int) (cellSize * 1.8));
        g2.setFont(FontUtils.getEnglishFont(Font.BOLD, fontSize));
        g2.setColor(new Color(226, 232, 240, (isPlaying || dragHighlightSide != 0) ? 210 : 140));
        String caption = "C H R O N O   T R I G G E R";
        FontMetrics fm = g2.getFontMetrics();
        int textX = (w - fm.stringWidth(caption)) / 2;
        int textY = startY + totalH + Math.max(16, cellSize * 3);
        g2.drawString(caption, textX, textY);

        g2.dispose();
    }

    /**
     * 拖入停滞时，左边和右边同时绘制提示文字
     */
    private void paintLeftRightDragHints(Graphics2D g2, int w, int hintY) {
        int fontSize = Math.max(12, Math.min(14, w / 50));
        Font font = FontUtils.getChineseFont(Font.BOLD, fontSize);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        String leftHint = "◀ 左侧释放：批量解压";
        String rightHint = "右侧释放：批量压缩 ▶";

        int leftX = (w / 4) - fm.stringWidth(leftHint) / 2;
        int rightX = (w * 3 / 4) - fm.stringWidth(rightHint) / 2;

        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawString(leftHint, leftX + 1, hintY + 1);
        g2.setColor(dragHighlightSide == 1 ? new Color(125, 211, 252, 250) : new Color(226, 232, 240, 115));
        g2.drawString(leftHint, leftX, hintY);

        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawString(rightHint, rightX + 1, hintY + 1);
        g2.setColor(dragHighlightSide == 2 ? new Color(253, 164, 175, 250) : new Color(226, 232, 240, 115));
        g2.drawString(rightHint, rightX, hintY);
    }

    /**
     * 统一管理闪烁定时器
     */
    private void updateAnimTimerState() {
        boolean shouldAnimate = (isPlaying || dragHighlightSide != 0) && !isProcessing;
        if (shouldAnimate) {
            if (animTimer == null) {
                animTimer = new javax.swing.Timer(250, e -> {
                    if ((isPlaying || dragHighlightSide != 0) && !isProcessing) {
                        animTick++;
                        if (dl != null) dl.repaint();
                    }
                });
                animTimer.setCoalesce(true);
            }
            if (!animTimer.isRunning()) {
                animTimer.start();
            }
        } else {
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
        }
        if (dl != null) {
            dl.repaint();
        }
    }

    /**
     * 点击触发全图一起闪烁并播放 resources/music/燃起来吧!.mp3
     */
    private void toggleMusic() {
        if (isPlaying) {
            stopMusic();
        } else {
            isPlaying = true;
            updateAnimTimerState();
            Thread.ofVirtual().name("Archive-MusicPlayer").start(() -> {
                try {
                    InputStream is = openMusicStream();
                    if (is != null) {
                        player = new javazoom.jl.player.Player(is);
                        player.play();

                        if (isPlaying) {
                            SwingUtilities.invokeLater(this::stopMusic);
                        }
                    } else {
                        System.out.println("提示：未找到 resources/music/燃起来吧!.mp3，保持全图闪烁视觉模式");
                    }
                } catch (Exception e) {
                    System.out.println("音乐播放中断或异常: " + e.getMessage());
                    SwingUtilities.invokeLater(this::stopMusic);
                }
            });
        }
    }

    /**
     * 多路径兼容加载 resources/music/燃起来吧!.mp3
     */
    private InputStream openMusicStream() {
        String[] filePaths = {
                "app/resources/music/燃起来吧!.mp3",
                "app/music/燃起来吧!.mp3",
                "resources/music/燃起来吧!.mp3",
                "src/main/resources/music/燃起来吧!.mp3",
                "music/燃起来吧!.mp3"
        };
        for (String path : filePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                try {
                    return new BufferedInputStream(new FileInputStream(f));
                } catch (FileNotFoundException ignored) {
                }
            }
        }

        String[] resourcePaths = {
                "/music/燃起来吧!.mp3",
                "/resources/music/燃起来吧!.mp3",
                "music/燃起来吧!.mp3",
                "resources/music/燃起来吧!.mp3"
        };
        for (String res : resourcePaths) {
            InputStream is = getClass().getResourceAsStream(res);
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream(res);
            }
            if (is != null) {
                return new BufferedInputStream(is);
            }
        }
        return null;
    }

    public void stopMusic() {
        isPlaying = false;
        if (player != null) {
            try {
                player.close();
            } catch (Exception ignored) {
            }
            player = null;
        }
        updateAnimTimerState();
    }

    @Override
    public void removeNotify() {
        stopMusic();
        super.removeNotify();
    }

    /**
     * 设置拖拽目标监听：拖到左边仅左边闪烁，拖到右边仅右边闪烁，离开恢复静止
     */
    private void setArchiveDropTarget(GlassDropPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                if (isProcessing) return;
                updateDragSide(dtde.getLocation(), p);
                SwingUtilities.invokeLater(() -> p.setDragActive(true));
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                if (isProcessing) return;
                updateDragSide(dtde.getLocation(), p);
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                dragHighlightSide = 0;
                SwingUtilities.invokeLater(() -> {
                    p.setDragActive(false);
                    updateAnimTimerState();
                });
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                Point dropPt = dtde.getLocation();
                int w = Math.max(1, p.getWidth());
                final boolean dropOnLeft = (dropPt.x < w / 2);

                dragHighlightSide = 0;
                SwingUtilities.invokeLater(() -> {
                    p.setDragActive(false);
                    updateAnimTimerState();
                });

                try {
                    if (isProcessing) {
                        dtde.rejectDrop();
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object transferData = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dtde.dropComplete(true);

                    if (transferData instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferData;
                        if (files != null && !files.isEmpty()) {
                            isExtractMode = dropOnLeft;
                            processArchiveDroppedFiles(files);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void updateDragSide(Point pt, GlassDropPanel p) {
        int w = Math.max(1, p.getWidth());
        int newSide = (pt.x < w / 2) ? 1 : 2;
        if (newSide != dragHighlightSide) {
            dragHighlightSide = newSide;
            isExtractMode = (newSide == 1);
            SwingUtilities.invokeLater(this::updateAnimTimerState);
        }
    }

    // =========================================================================
    // 直接调用物理路径 ToolKit\app\tools\UnRAR.exe 执行批量解压与批量压缩
    // =========================================================================

    private synchronized File resolveUnRarExecutable() {
        if (cachedUnRarExe != null && cachedUnRarExe.exists()) {
            return cachedUnRarExe;
        }

        String[] localCandidates = {
                "app/tools/UnRAR.exe",
                "tools/UnRAR.exe",
                "resources/tools/UnRAR.exe",
                "src/main/resources/tools/UnRAR.exe"
        };
        for (String path : localCandidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                cachedUnRarExe = f;
                return cachedUnRarExe;
            }
        }

        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null && !appPath.isEmpty()) {
                File exeDir = new File(appPath).getParentFile();
                if (exeDir != null) {
                    File f1 = new File(exeDir, "app/tools/UnRAR.exe");
                    if (f1.exists() && f1.isFile()) {
                        cachedUnRarExe = f1;
                        return cachedUnRarExe;
                    }
                    File f2 = new File(exeDir, "tools/UnRAR.exe");
                    if (f2.exists() && f2.isFile()) {
                        cachedUnRarExe = f2;
                        return cachedUnRarExe;
                    }
                }
            }

            CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File codeDir = new File(codeSource.getLocation().toURI());
                if (codeDir.isFile()) {
                    codeDir = codeDir.getParentFile();
                }
                if (codeDir != null) {
                    File[] dirCandidates = {
                            new File(codeDir, "tools/UnRAR.exe"),
                            new File(codeDir, "app/tools/UnRAR.exe"),
                            codeDir.getParentFile() != null ? new File(codeDir.getParentFile(), "app/tools/UnRAR.exe") : null,
                            codeDir.getParentFile() != null ? new File(codeDir.getParentFile(), "tools/UnRAR.exe") : null
                    };
                    for (File candidate : dirCandidates) {
                        if (candidate != null && candidate.exists() && candidate.isFile()) {
                            cachedUnRarExe = candidate;
                            return cachedUnRarExe;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void processArchiveDroppedFiles(List<File> files) {
        isProcessing = true;
        currentProgress = 0;
        updateAnimTimerState();
        adjustFontSize();

        Thread.ofVirtual().name("Archive-BatchWorker").start(() -> {
            try {
                if (isExtractMode) {
                    executeBatchExtraction(files);
                } else {
                    executeBatchCompression(files);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    isProcessing = false;
                    currentProgress = 0;
                    updateAnimTimerState();
                    adjustFontSize();
                    JOptionPane.showMessageDialog(ArchivePanel.this, ex.getMessage(), "操作终止", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void executeBatchExtraction(List<File> files) throws Exception {
        File unrarExe = resolveUnRarExecutable();
        int totalFiles = Math.max(1, files.size());

        // 第一步：解压前预检所有压缩包是否加密，若未设置密码则立即提前报错，不建空文件夹、不卡死
        for (File file : files) {
            if (!file.exists() || file.isDirectory()) continue;
            boolean isZip = isZipArchive(file);
            if (isZip) {
                if (isZipEncrypted(file) && (archivePassword == null || archivePassword.isEmpty())) {
                    throw new IllegalStateException("检测到加密压缩包：【" + file.getName() + "】\n请先点击右上角「时空怀表」设置密码后再解压！");
                }
            } else if (unrarExe != null && unrarExe.exists()) {
                int checkCode = checkRarPasswordStatus(unrarExe, file);
                if (checkCode == 1 && (archivePassword == null || archivePassword.isEmpty())) {
                    throw new IllegalStateException("检测到加密压缩包：【" + file.getName() + "】\n请先点击右上角「时空怀表」设置密码后再解压！");
                } else if (checkCode == 2) {
                    throw new IllegalStateException("解压失败：【" + file.getName() + "】\n当前设置的怀表密码不正确，请核对后重试！");
                }
            }
        }

        // 第二步：正式执行批量解压
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            if (!file.exists() || file.isDirectory()) continue;

            File parentDir = file.getParentFile();
            if (parentDir == null) parentDir = new File(".");

            String baseName = file.getName();
            int dotIdx = baseName.lastIndexOf('.');
            String folderName = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName + "_解压";
            File targetDir = new File(parentDir, folderName);
            boolean dirCreatedByUs = !targetDir.exists() && targetDir.mkdirs();

            int baseProgress = (i * 100) / totalFiles;
            int nextProgress = ((i + 1) * 100) / totalFiles;
            updateProgress(Math.max(1, baseProgress));

            try {
                if (isZipArchive(file)) {
                    if (isZipEncrypted(file)) {
                        extractEncryptedZip(file, targetDir, archivePassword, baseProgress, nextProgress);
                    } else {
                        extractSingleZipFallback(file, targetDir, baseProgress, nextProgress);
                    }
                } else {
                    if (unrarExe == null || !unrarExe.exists()) {
                        throw new IllegalStateException("未找到 UnRAR.exe 组件，无法解压 RAR 格式文件：" + file.getName());
                    }
                    runUnRarExtractCommand(unrarExe, file, targetDir, baseProgress, nextProgress);
                }
            } catch (Exception ex) {
                // 解压失败时清理刚创建的空目录
                if (dirCreatedByUs) {
                    File[] children = targetDir.listFiles();
                    if (children == null || children.length == 0) {
                        targetDir.delete();
                    }
                }
                throw ex;
            }

            updateProgress(Math.min(99, nextProgress));
        }

        SwingUtilities.invokeLater(() -> {
            currentProgress = 100;
            adjustFontSize();
            JOptionPane.showMessageDialog(ArchivePanel.this,
                    "批量解压完成！文件已解压至各对应同名目录。",
                    "系统通知", JOptionPane.INFORMATION_MESSAGE);
            isProcessing = false;
            updateAnimTimerState();
            adjustFontSize();
        });
    }

    /**
     * 预检 RAR 压缩包密码状态：0-正常可解，1-需要密码但未设置，2-密码错误
     */
    private int checkRarPasswordStatus(File unrarExe, File archiveFile) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(unrarExe.getAbsolutePath());
            cmd.add("l");
            cmd.add("-y");
            if (archivePassword != null && !archivePassword.isEmpty()) {
                cmd.add("-p" + archivePassword);
            } else {
                cmd.add("-p-");
            }
            cmd.add(archiveFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 关键：立刻关闭子进程输入流，彻底杜绝 UnRAR 等待控制台输入密码导致程序卡死
            process.getOutputStream().close();

            boolean hasEncryptedStar = false;
            boolean hasPwdErrorMsg = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*")) {
                        hasEncryptedStar = true;
                    }
                    String lower = trimmed.toLowerCase();
                    if (lower.contains("password") || lower.contains("encrypted") || lower.contains("密码") || lower.contains("加密")) {
                        hasPwdErrorMsg = true;
                    }
                }
            }
            int exitCode = process.waitFor();
            if (archivePassword == null || archivePassword.isEmpty()) {
                if (hasEncryptedStar || hasPwdErrorMsg || exitCode == 11) {
                    return 1;
                }
            } else if (exitCode == 11 || (exitCode != 0 && hasPwdErrorMsg)) {
                return 2;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void runUnRarExtractCommand(File unrarExe, File archiveFile, File targetDir, int startPct, int endPct) throws Exception {
        String targetPath = targetDir.getAbsolutePath();
        if (!targetPath.endsWith(File.separator)) {
            targetPath += File.separator;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(unrarExe.getAbsolutePath());
        cmd.add("x");
        cmd.add("-y");
        cmd.add("-o+");
        if (archivePassword != null && !archivePassword.isEmpty()) {
            cmd.add("-p" + archivePassword);
        } else {
            cmd.add("-p-");
        }
        cmd.add(archiveFile.getAbsolutePath());
        cmd.add(targetPath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        // 关键：立刻关闭标准输入流，防止任何情况下 UnRAR 卡死等待输入
        process.getOutputStream().close();

        Pattern pctPattern = Pattern.compile("(\\d{1,3})%");
        boolean pwdErrorDetected = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("password") || lower.contains("encrypted") || lower.contains("密码") || lower.contains("加密")) {
                    pwdErrorDetected = true;
                }
                Matcher m = pctPattern.matcher(line);
                if (m.find()) {
                    int subPct = Integer.parseInt(m.group(1));
                    int mapped = startPct + (subPct * (endPct - startPct)) / 100;
                    updateProgress(Math.min(99, mapped));
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 11 || (exitCode != 0 && pwdErrorDetected)) {
            if (archivePassword == null || archivePassword.isEmpty()) {
                throw new IllegalStateException("检测到加密压缩包：【" + archiveFile.getName() + "】\n请先点击右上角「时空怀表」设置解压密码！");
            } else {
                throw new IllegalStateException("解压失败：【" + archiveFile.getName() + "】\n解压密码错误，请点击右上角怀表重新核对密码！");
            }
        }
        if (exitCode != 0) {
            throw new IllegalStateException("解压失败：【" + archiveFile.getName() + "】文件可能已损坏或格式不受支持（错误码 " + exitCode + "）。");
        }
    }

    private void executeBatchCompression(List<File> files) throws Exception {
        File parentDir = files.get(0).getParentFile();
        if (parentDir == null) parentDir = new File(".");

        final File finalArchiveFile = resolveAvailableArchiveFile(parentDir, ".zip");
        int totalFiles = 0;
        for (File f : files) {
            totalFiles += countTotalFiles(f);
        }
        if (totalFiles == 0) totalFiles = 1;
        int[] processedCount = new int[]{0};

        boolean usePassword = (archivePassword != null && !archivePassword.isEmpty());

        if (usePassword) {
            // 设置了密码：使用内置 PKWARE 标准 ZipCrypto 加密压缩引擎写入受密码保护的 ZIP
            try (ZipCryptoArchiveWriter zcw = new ZipCryptoArchiveWriter(
                    new BufferedOutputStream(new FileOutputStream(finalArchiveFile)), archivePassword)) {
                for (File file : files) {
                    addFileOrDirToEncryptedZip(zcw, file, file.getName(), processedCount, totalFiles);
                }
            }
        } else {
            // 未设置密码：使用标准 ZipOutputStream 压缩
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(finalArchiveFile)), StandardCharsets.UTF_8)) {
                for (File file : files) {
                    addFileOrDirToZip(zos, file, file.getName(), processedCount, totalFiles);
                }
            }
        }

        final boolean encrypted = usePassword;
        SwingUtilities.invokeLater(() -> {
            currentProgress = 100;
            adjustFontSize();
            JOptionPane.showMessageDialog(ArchivePanel.this,
                    "批量压缩完成！" + (encrypted ? "（已启用怀表密码加密）" : "") + "\n归档文件已生成：" + finalArchiveFile.getName(),
                    "系统通知", JOptionPane.INFORMATION_MESSAGE);
            isProcessing = false;
            updateAnimTimerState();
            adjustFontSize();
        });
    }

    // =========================================================================
    // ZIP 格式识别、加密预检及原生 ZipCrypto 加解密实现
    // =========================================================================

    private boolean isZipArchive(File f) {
        if (f.getName().toLowerCase().endsWith(".zip")) return true;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            if (raf.length() >= 4) {
                int sig = raf.readInt();
                return sig == 0x504B0304 || sig == 0x504B0506;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 快速检测 ZIP 压缩包是否包含加密条目（检查 Local File Header 的 Bit 0 标志位）
     */
    private boolean isZipEncrypted(File f) {
        try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(f)) {
            if (zf.isValidZipFile()) {
                return zf.isEncrypted();
            }
        } catch (Exception ignored) {
        }
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            if (raf.length() >= 30) {
                byte[] header = new byte[30];
                raf.readFully(header);
                ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                if (bb.getInt(0) == 0x04034b50) {
                    int flags = bb.getShort(6) & 0xFFFF;
                    return (flags & 1) != 0;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void addFileOrDirToEncryptedZip(ZipCryptoArchiveWriter zcw, File file, String entryPath, int[] processedCount, int totalFiles) throws IOException {
        if (file.isDirectory()) {
            String dirPath = entryPath.endsWith("/") ? entryPath : entryPath + "/";
            zcw.writeDirectoryEntry(dirPath);
            processedCount[0]++;
            updateProgress(Math.min(99, (int) ((processedCount[0] * 100.0) / totalFiles)));

            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileOrDirToEncryptedZip(zcw, child, dirPath + child.getName(), processedCount, totalFiles);
                }
            }
        } else {
            zcw.writeFileEntry(entryPath, file);
            processedCount[0]++;
            updateProgress(Math.min(99, (int) ((processedCount[0] * 100.0) / totalFiles)));
        }
    }

    /**
     * 解压加密的 ZIP 文件（流式高性能解压，原生支持 WinZip AES-256 与标准 ZipCrypto 双算法，带严格防 Zip Slip 校验与 UnRAR 兜底）
     */
    private void extractEncryptedZip(File archiveFile, File targetDir, String password, int startPct, int endPct) throws Exception {
        char[] pwdChars = (password != null && !password.isEmpty()) ? password.toCharArray() : null;

        // 优先使用高兼容性、纯流式、原生支持 AES-256 / ZipCrypto 的 Zip4j 引擎，杜绝全量加载到堆内存导致 OOM
        try {
            boolean success = false;
            // 字符集自动重试：优先 UTF-8，遇文件名乱码或异常时自动降级尝试 GBK
            Charset[] tryCharsets = new Charset[]{StandardCharsets.UTF_8, Charset.forName("GBK")};
            Exception lastEx = null;

            for (Charset cs : tryCharsets) {
                try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(archiveFile, pwdChars)) {
                    zf.setCharset(cs);
                    if (!zf.isValidZipFile()) {
                        continue;
                    }
                    List<net.lingala.zip4j.model.FileHeader> headers = zf.getFileHeaders();
                    if (headers == null || headers.isEmpty()) {
                        success = true;
                        break;
                    }

                    Path targetPath = targetDir.toPath().toAbsolutePath().normalize();
                    String targetCanonical = targetDir.getCanonicalPath();
                    if (!targetCanonical.endsWith(File.separator)) {
                        targetCanonical += File.separator;
                    }

                    // 1. 严格 Zip Slip 预检，彻底防范目录穿透攻击
                    for (net.lingala.zip4j.model.FileHeader header : headers) {
                        String entryName = header.getFileName();
                        File outFile = new File(targetDir, entryName);
                        Path outPath = outFile.toPath().toAbsolutePath().normalize();
                        String outCanonical = outFile.getCanonicalPath();

                        if (!outPath.startsWith(targetPath) || (!outCanonical.startsWith(targetCanonical) && !outCanonical.equals(targetDir.getCanonicalPath()))) {
                            throw new SecurityException("检测到恶意跨目录压缩包 (Zip Slip Vulnerability)！拒绝解压: " + entryName);
                        }
                    }

                    // 2. 流式逐个解压并精确驱动总进度条
                    int total = headers.size();
                    for (int i = 0; i < total; i++) {
                        net.lingala.zip4j.model.FileHeader header = headers.get(i);
                        zf.extractFile(header, targetDir.getAbsolutePath());

                        int mapped = startPct + (int) (((i + 1) * 1.0 / total) * (endPct - startPct));
                        updateProgress(Math.min(99, mapped));
                    }
                    success = true;
                    break;
                } catch (net.lingala.zip4j.exception.ZipException ze) {
                    lastEx = ze;
                    if (ze.getType() == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD
                            || ze.getType() == net.lingala.zip4j.exception.ZipException.Type.CHECKSUM_MISMATCH) {
                        throw new IllegalStateException("解压失败：【" + archiveFile.getName() + "】\n解压密码错误，请点击右上角怀表重新核对密码！", ze);
                    }
                } catch (SecurityException se) {
                    throw se; // 安全漏洞拒绝异常直接抛出
                } catch (Exception ex) {
                    lastEx = ex;
                    String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                    if (msg.contains("wrong password") || msg.contains("password is not correct") || msg.contains("checksum failed")) {
                        throw new IllegalStateException("解压失败：【" + archiveFile.getName() + "】\n解压密码错误，请点击右上角怀表重新核对密码！", ex);
                    }
                }
            }

            if (success) {
                return;
            }
            if (lastEx != null) {
                throw lastEx;
            }
        } catch (IllegalStateException | SecurityException knownEx) {
            throw knownEx;
        } catch (Exception zip4jEx) {
            // 双重保险：如果遭遇非标或特殊 ZIP 结构，且本地已放置 app/tools/UnRAR.exe，则尝试调度 UnRAR 兜底解压
            File unrarExe = resolveUnRarExecutable();
            if (unrarExe != null && unrarExe.exists()) {
                try {
                    runUnRarExtractCommand(unrarExe, archiveFile, targetDir, startPct, endPct);
                    return;
                } catch (Exception unrarEx) {
                    throw unrarEx;
                }
            }
            throw zip4jEx;
        }
    }

    /**
     * 标准 PKWARE ZipCrypto 流密码引擎
     */
    private static class ZipCryptoEngine {
        private static final int[] CRC_TABLE = new int[256];
        static {
            for (int i = 0; i < 256; i++) {
                int r = i;
                for (int j = 0; j < 8; j++) {
                    r = ((r & 1) == 1) ? ((r >>> 1) ^ 0xEDB88320) : (r >>> 1);
                }
                CRC_TABLE[i] = r;
            }
        }

        private int key0 = 0x12345678;
        private int key1 = 0x23456789;
        private int key2 = 0x34567890;

        public ZipCryptoEngine(String password) {
            for (byte b : password.getBytes(StandardCharsets.UTF_8)) {
                updateKeys(b);
            }
        }

        public void updateKeys(byte b) {
            key0 = (key0 >>> 8) ^ CRC_TABLE[(key0 ^ b) & 0xFF];
            key1 = (key1 + (key0 & 0xFF)) * 134775813 + 1;
            key2 = (key2 >>> 8) ^ CRC_TABLE[(key2 ^ (key1 >>> 24)) & 0xFF];
        }

        public byte decryptByte() {
            int temp = (key2 & 0xFFFF) | 2;
            return (byte) (((temp * (temp ^ 1)) >>> 8) & 0xFF);
        }

        public byte encryptByte(byte plain) {
            byte cipher = (byte) (plain ^ decryptByte());
            updateKeys(plain);
            return cipher;
        }
    }

    /**
     * 原生支持密码加密的 ZIP 归档写入器
     */
    private static class ZipCryptoArchiveWriter implements Closeable {
        private final OutputStream out;
        private final String password;
        private final SecureRandom random = new SecureRandom();
        private final List<CentralDirRecord> cdRecords = new ArrayList<>();
        private int currentOffset = 0;

        private static class CentralDirRecord {
            byte[] nameBytes;
            int flags;
            int method;
            int crc32;
            int compSize;
            int uncompSize;
            int localHeaderOffset;
        }

        public ZipCryptoArchiveWriter(OutputStream out, String password) {
            this.out = out;
            this.password = password;
        }

        public void writeDirectoryEntry(String dirPath) throws IOException {
            byte[] nameBytes = dirPath.getBytes(StandardCharsets.UTF_8);
            int localOffset = currentOffset;

            ByteBuffer lfh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
            lfh.putInt(0x04034b50);
            lfh.putShort((short) 20);
            lfh.putShort((short) 0x0800); // UTF-8
            lfh.putShort((short) 0);
            lfh.putInt(0x54210000);
            lfh.putInt(0);
            lfh.putInt(0);
            lfh.putInt(0);
            lfh.putShort((short) nameBytes.length);
            lfh.putShort((short) 0);

            out.write(lfh.array());
            out.write(nameBytes);
            currentOffset += 30 + nameBytes.length;

            CentralDirRecord rec = new CentralDirRecord();
            rec.nameBytes = nameBytes;
            rec.flags = 0x0800;
            rec.method = 0;
            rec.localHeaderOffset = localOffset;
            cdRecords.add(rec);
        }

        public void writeFileEntry(String entryPath, File file) throws IOException {
            byte[] plainBytes = new byte[(int) file.length()];
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                dis.readFully(plainBytes);
            }

            CRC32 crc = new CRC32();
            crc.update(plainBytes);
            int crcVal = (int) crc.getValue();

            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            deflater.setInput(plainBytes);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(plainBytes.length);
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                baos.write(buf, 0, n);
            }
            deflater.end();
            byte[] deflated = baos.toByteArray();

            ZipCryptoEngine cipher = new ZipCryptoEngine(password);
            byte[] header12 = new byte[12];
            random.nextBytes(header12);
            header12[11] = (byte) ((crcVal >>> 24) & 0xFF);
            for (int i = 0; i < 12; i++) {
                header12[i] = cipher.encryptByte(header12[i]);
            }

            byte[] encryptedBody = new byte[deflated.length];
            for (int i = 0; i < deflated.length; i++) {
                encryptedBody[i] = cipher.encryptByte(deflated[i]);
            }

            byte[] nameBytes = entryPath.getBytes(StandardCharsets.UTF_8);
            int compSize = 12 + encryptedBody.length;
            int localOffset = currentOffset;

            ByteBuffer lfh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
            lfh.putInt(0x04034b50);
            lfh.putShort((short) 20);
            lfh.putShort((short) 0x0801); // Bit 0 (Encrypted) | Bit 11 (UTF-8)
            lfh.putShort((short) 8);      // DEFLATE
            lfh.putInt(0x54210000);
            lfh.putInt(crcVal);
            lfh.putInt(compSize);
            lfh.putInt(plainBytes.length);
            lfh.putShort((short) nameBytes.length);
            lfh.putShort((short) 0);

            out.write(lfh.array());
            out.write(nameBytes);
            out.write(header12);
            out.write(encryptedBody);
            currentOffset += 30 + nameBytes.length + compSize;

            CentralDirRecord rec = new CentralDirRecord();
            rec.nameBytes = nameBytes;
            rec.flags = 0x0801;
            rec.method = 8;
            rec.crc32 = crcVal;
            rec.compSize = compSize;
            rec.uncompSize = plainBytes.length;
            rec.localHeaderOffset = localOffset;
            cdRecords.add(rec);
        }

        @Override
        public void close() throws IOException {
            int cdStart = currentOffset;
            int cdSize = 0;
            for (CentralDirRecord r : cdRecords) {
                ByteBuffer cdh = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
                cdh.putInt(0x02014b50);
                cdh.putShort((short) 20);
                cdh.putShort((short) 20);
                cdh.putShort((short) r.flags);
                cdh.putShort((short) r.method);
                cdh.putInt(0x54210000);
                cdh.putInt(r.crc32);
                cdh.putInt(r.compSize);
                cdh.putInt(r.uncompSize);
                cdh.putShort((short) r.nameBytes.length);
                cdh.putShort((short) 0);
                cdh.putShort((short) 0);
                cdh.putShort((short) 0);
                cdh.putShort((short) 0);
                cdh.putInt(0);
                cdh.putInt(r.localHeaderOffset);

                out.write(cdh.array());
                out.write(r.nameBytes);
                cdSize += 46 + r.nameBytes.length;
            }

            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            eocd.putInt(0x06054b50);
            eocd.putShort((short) 0);
            eocd.putShort((short) 0);
            eocd.putShort((short) cdRecords.size());
            eocd.putShort((short) cdRecords.size());
            eocd.putInt(cdSize);
            eocd.putInt(cdStart);
            eocd.putShort((short) 0);

            out.write(eocd.array());
            out.flush();
            out.close();
        }
    }

    private void extractSingleZipFallback(File archiveFile, File targetDir, int startPct, int endPct) throws Exception {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(archiveFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                zipFile = new ZipFile(archiveFile, Charset.forName("GBK"));
            } catch (Exception ex) {
                zipFile = new ZipFile(archiveFile);
            }
        }

        try {
            int totalEntries = Math.max(1, zipFile.size());
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[16384];
            String targetCanonical = targetDir.getCanonicalPath();
            if (!targetCanonical.endsWith(File.separator)) {
                targetCanonical += File.separator;
            }
            Path targetDirPath = targetDir.toPath().toAbsolutePath().normalize();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outFile = new File(targetDir, entry.getName());
                Path outPath = outFile.toPath().toAbsolutePath().normalize();
                String outCanonical = outFile.getCanonicalPath();

                if (!outPath.startsWith(targetDirPath) || (!outCanonical.startsWith(targetCanonical) && !outCanonical.equals(targetDir.getCanonicalPath()))) {
                    throw new SecurityException("检测到恶意跨目录压缩包 (Zip Slip Vulnerability)！拒绝解压: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (InputStream is = zipFile.getInputStream(entry);
                         OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                }

                count++;
                int mapped = startPct + (int) ((count * 1.0 / totalEntries) * (endPct - startPct));
                updateProgress(Math.min(99, mapped));
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private void addFileOrDirToZip(ZipOutputStream zos, File file, String entryPath, int[] processedCount, int totalFiles) throws IOException {
        if (file.isDirectory()) {
            String dirPath = entryPath.endsWith("/") ? entryPath : entryPath + "/";
            zos.putNextEntry(new ZipEntry(dirPath));
            zos.closeEntry();
            processedCount[0]++;
            int percent = Math.min(99, (int) ((processedCount[0] * 100.0) / totalFiles));
            updateProgress(percent);

            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileOrDirToZip(zos, child, dirPath + child.getName(), processedCount, totalFiles);
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryPath));
            byte[] buffer = new byte[16384];
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                int len;
                while ((len = is.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
            processedCount[0]++;
            int percent = Math.min(99, (int) ((processedCount[0] * 100.0) / totalFiles));
            updateProgress(percent);
        }
    }

    private int countTotalFiles(File file) {
        if (file.isFile()) return 1;
        int count = 1;
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                count += countTotalFiles(c);
            }
        }
        return count;
    }

    private File resolveAvailableArchiveFile(File parentDir, String ext) {
        File mainFile = new File(parentDir, "ToolKit归档文件" + ext);
        if (!mainFile.exists()) {
            return mainFile;
        }

        File firstAttachment = new File(parentDir, "ToolKit归档文件_附件" + ext);
        if (!firstAttachment.exists()) {
            return firstAttachment;
        }

        int index = 1;
        while (true) {
            File candidate = new File(parentDir, "ToolKit归档文件_附件" + index + ext);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }

    private void updateProgress(int percent) {
        if (percent > currentProgress) {
            currentProgress = percent;
            SwingUtilities.invokeLater(() -> {
                if (dl != null) dl.repaint();
            });
        }
    }

    /**
     * 原生自绘的“时空怀表”图标指示器组件（尺寸、缩放、偏移 1:1 对齐 ScoringPanel.SheepIndicator）
     */
    static class PocketWatchIndicator extends JComponent {

        private final double SCALE = 0.8;
        private final int OFFSET_X = 10;
        private final int OFFSET_Y = 2;
        private boolean passwordActive = false;

        public PocketWatchIndicator() {
            setPreferredSize(new Dimension(65, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void setPasswordActive(boolean active) {
            this.passwordActive = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(12 + OFFSET_X, OFFSET_Y);
            g2.scale(SCALE, SCALE);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 1. 怀表右侧垂落的复古挂链
            Path2D chain = new Path2D.Float();
            chain.moveTo(25, 4);
            chain.curveTo(35, 3, 44, 12, 42, 24);
            chain.curveTo(41, 31, 44, 36, 46, 39);
            g2.setColor(new Color(212, 175, 55));
            g2.draw(chain);

            // 2. 顶部表环 (Bow)
            g2.setColor(new Color(234, 179, 8));
            g2.draw(new Arc2D.Float(19, 1, 12, 9, 0, 360, Arc2D.OPEN));

            // 3. 表冠底座与旋钮 (Crown)
            g2.setColor(new Color(245, 158, 11));
            g2.fillRoundRect(21, 7, 8, 5, 2, 2);
            g2.setColor(Color.BLACK);
            g2.drawRoundRect(21, 7, 8, 5, 2, 2);

            // 4. 怀表黄金外表的壳体 (Outer Case: 中心 25, 26，直径 30)
            Ellipse2D outerCase = new Ellipse2D.Float(10, 11, 30, 30);
            g2.setColor(new Color(245, 185, 35));
            g2.fill(outerCase);
            g2.setColor(Color.BLACK);
            g2.draw(outerCase);

            // 5. 怀表内圈金属倒角环
            Ellipse2D innerRim = new Ellipse2D.Float(12.5f, 13.5f, 25, 25);
            g2.setColor(new Color(217, 140, 16));
            g2.fill(innerRim);
            g2.setColor(Color.BLACK);
            g2.draw(innerRim);

            // 6. 复古象牙白表盘 (Dial Face)
            Ellipse2D dial = new Ellipse2D.Float(14, 15, 22, 22);
            g2.setColor(passwordActive ? new Color(235, 248, 255) : new Color(255, 250, 235));
            g2.fill(dial);
            g2.setColor(Color.BLACK);
            g2.draw(dial);

            // 7. 表盘高光弧线
            g2.setColor(new Color(255, 255, 255, 160));
            g2.draw(new Arc2D.Float(16, 17, 18, 18, 110, 65, Arc2D.OPEN));

            // 8. 12 个精细刻度点
            g2.setColor(new Color(45, 55, 72));
            int cx = 25;
            int cy = 26;
            for (int i = 0; i < 12; i++) {
                double angle = Math.toRadians(i * 30 - 90);
                double rOuter = 9.0;
                double rInner = (i % 3 == 0) ? 6.8 : 7.8;
                int x1 = (int) Math.round(cx + Math.cos(angle) * rInner);
                int y1 = (int) Math.round(cy + Math.sin(angle) * rInner);
                int x2 = (int) Math.round(cx + Math.cos(angle) * rOuter);
                int y2 = (int) Math.round(cy + Math.sin(angle) * rOuter);
                g2.drawLine(x1, y1, x2, y2);
            }

            // 9. 时针与分针（经典 10点10分 夹角）
            g2.setColor(new Color(30, 41, 59));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx, cy, cx - 4, cy - 3); // 时针
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx, cy, cx + 5, cy - 5); // 分针

            // 10. 表芯宝石轴（设置密码后点亮为冰蓝色宝石）
            g2.setColor(passwordActive ? new Color(56, 189, 248) : new Color(220, 38, 38));
            g2.fillOval(cx - 2, cy - 2, 4, 4);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawOval(cx - 2, cy - 2, 4, 4);

            g2.dispose();
        }
    }
}