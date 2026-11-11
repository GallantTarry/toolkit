package shaoxia.modules;

import javazoom.jl.player.Player;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassDropPanel;
import shaoxia.Utils.GlassPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公告比对助手面板 (Word 与 PDF 双文件内容自动比对校验)
 * 学习 ScoringPanel 点阵矩阵渲染、粒子闪烁律动与火箭队音频联动 (已去除拖放提示白字，修复字体乱码)
 */
public class ComparisonPanel extends BackgroundPanel {

    private final MainLauncher parent;

    private JPanel leftTop;
    private ActionButton backBtn;

    private File wordFile, pdfFile;
    private JLabel wordLabel, pdfLabel;
    private JTextArea logArea;

    // 音频播放与点阵微动效核心控制
    private boolean isMusicPlaying = false;
    private Thread musicThread;
    private Player player;
    private javax.swing.Timer musicAnimTimer;
    private int animTick = 0;

    public ComparisonPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);

        initTopBar();
        add(createComparisonCard(), BorderLayout.CENTER);
    }

    /**
     * 初始化顶部导航栏
     */
    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 20));
        topBar.setPreferredSize(new Dimension(0, 56));

        leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftTop.setOpaque(false);

        backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic(); // 返回主菜单时自动停止音频与计时器
            parent.showMenu();
        });
        leftTop.add(backBtn);

        topBar.add(leftTop, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);
    }

    // =========================================================================
    // 🎨 小次郎 (Kojiro - DOCX) 与 武藏 (Musashi - PDF) 36x20 高精度点阵矩阵
    // =========================================================================
    private static final String[] KOJIRO_MATRIX = {
            "..+............................*....",
            "............BBBBBBBB................",
            ".........BBBBBBBBBBBB...............",
            "........BBBBbbbbbbbbDD..............",
            ".......BBBbbbSSSSbbbbDD.............",
            "......BBBbbSSSSSSSSsbbDD............",
            "......BBbbS.wE..wE.SsbbD............",
            "......BBbbSSSSSSSSSSsbbD....*.......",
            ".......BbbSSSSsLLsSSsbbD............",
            ".......BbbbSSSSSSSSsbbbD............",
            "...*....BbbbbSSSSsbbbbD...GHhG......",
            ".........bbbbkKKkbbbb....GGGHhG.....",
            "........WWWWKkKKkKWWWW....GG........",
            ".......WWWWWWkKKkWWWWWW...GK........",
            "......WWWWWWWRRRRWWWWWWW.GKK........",
            ".....WWWWWWWWWR..rWWWWWWWWGK........",
            ".....WWWWWWWWRRRRWWWWWWWWW..........",
            ".....WWWWWWWWR..rWWWWWWWWW..........",
            "....WWWWWWWWWR..rWWWWWWWWWW.........",
            "............................+......."
    };

    private static final String[] MUSASHI_MATRIX = {
            "....*...............................",
            "..........MMMMMMMMMM................",
            ".......MMMMMMMMMMMMMMMMMM...........",
            ".....MMMMMMMMMPPMMMMMMMMMMMM........",
            "....MMMMmmmmmmPPMMMMMMMMMMMMMM......",
            "...MMMMmSSSSSSSmPMMMMMMMMMMMMMMM.....",
            "...MMMmSSSSSSSSSmPMMMMMMMMMMMMMM....",
            "...MMmSS.we..we.SSmPMMMMMMMMMMMMMM..",
            "...MMmSSSSSSSSSSSSmPMMMMMMMMMMMMMM..",
            "....MmSSSSASSSSSSsmPMMMMMMMMMMMMMM...",
            "....MmSSSsLLLLssmPMMMMMMMMMMMMMM....",
            ".....MmSSSSSSSSmPMMMMMMMMMMMMM......",
            "......mSSSSSSSSm...PMMMMMMMMM.......",
            ".......mmkKKkmm....PMMMMMMM......*..",
            ".....WWWWkKKkWWWW...PMMMMM..........",
            "....WWWWWWkKKkWWWW...PMMMM..........",
            "....WWWWWWRRRRWWWWW....MMM..........",
            "...WWWWWWWR..RWWWWWW....M...........",
            "...WWWWWWWRRRRWWWWWW................",
            "...WWWWWWWR..RWWWWWW........+......."
    };

    private Color getKojiroDotColor(char ch, int row, int col) {
        boolean shimmer = isMusicPlaying && ((row + col + animTick) % 3 == 0);
        switch (ch) {
            case '*': return shimmer ? Color.WHITE : new Color(210, 235, 255, 190);
            case '+': return shimmer ? new Color(186, 230, 253) : new Color(56, 189, 248, 170);
            case 'B': return shimmer ? new Color(191, 219, 254) : new Color(147, 197, 253);
            case 'b': return new Color(96, 125, 215);
            case 'D': return new Color(67, 56, 202);
            case 'S': return new Color(254, 226, 200);
            case 's': return new Color(240, 195, 165);
            case 'w': return Color.WHITE;
            case 'E': return shimmer ? new Color(96, 165, 250) : new Color(37, 99, 235);
            case 'L': return new Color(248, 113, 113);
            case 'R': return shimmer ? new Color(254, 202, 202) : new Color(239, 68, 68);
            case 'r': return new Color(185, 28, 28);
            case 'H': return shimmer ? new Color(254, 205, 211) : new Color(244, 63, 94);
            case 'h': return new Color(190, 18, 60);
            case 'G': return shimmer ? new Color(134, 239, 172) : new Color(34, 197, 94);
            case 'W': return shimmer ? Color.WHITE : new Color(248, 250, 252);
            case 'K': return new Color(30, 41, 59);
            case 'k': return new Color(15, 23, 42);
            default: return null;
        }
    }

    private Color getMusashiDotColor(char ch, int row, int col) {
        boolean shimmer = isMusicPlaying && ((row + col + animTick) % 3 == 0);
        switch (ch) {
            case '*': return shimmer ? Color.WHITE : new Color(255, 228, 230, 190);
            case '+': return shimmer ? new Color(254, 205, 211) : new Color(244, 114, 182, 170);
            case 'P': return shimmer ? new Color(254, 205, 211) : new Color(251, 113, 133);
            case 'M': return shimmer ? new Color(244, 114, 182) : new Color(225, 29, 118);
            case 'm': return new Color(157, 23, 77);
            case 'S': return new Color(254, 226, 200);
            case 's': return new Color(240, 195, 165);
            case 'w': return Color.WHITE;
            case 'e': return shimmer ? new Color(110, 231, 183) : new Color(16, 185, 129);
            case 'A': return shimmer ? new Color(253, 224, 71) : new Color(52, 211, 153);
            case 'L': return shimmer ? new Color(252, 165, 165) : new Color(239, 68, 68);
            case 'R': return shimmer ? new Color(254, 202, 202) : new Color(239, 68, 68);
            case 'W': return shimmer ? Color.WHITE : new Color(248, 250, 252);
            case 'K': return new Color(30, 41, 59);
            case 'k': return new Color(15, 23, 42);
            default: return null;
        }
    }

    private String truncateFileName(String name, int maxLen) {
        if (name == null) return "";
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 3) + "...";
    }

    /**
     * 绘制小次郎点阵画布 (彻底移除未加载时的大白字提示)
     */
    private void paintKojiroDotMatrix(Graphics g, JLabel label, boolean isLoaded) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = label.getWidth();
        int h = label.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = KOJIRO_MATRIX.length;
        int cols = KOJIRO_MATRIX[0].length();

        int cellSize = Math.max(3, Math.min((w - 24) / cols, (h - 24) / rows));
        int gap = cellSize >= 5 ? 1 : 0;
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        // 未加载时让角色居中偏上呈现更优美构图
        int startY = (h - totalH) / 2 - (isLoaded ? 10 : 0);

        // 柔和微蓝背景光晕
        int centerX = startX + 16 * cellSize;
        int centerY = startY + 10 * cellSize;
        int glowR = cellSize * (isMusicPlaying && (animTick % 2 == 0) ? 10 : 9);
        g2.setColor(new Color(59, 130, 246, isMusicPlaying ? 50 : (isLoaded ? 32 : 18)));
        g2.fillOval(centerX - glowR, centerY - glowR, glowR * 2, glowR * 2);

        // 逐点绘制
        for (int r = 0; r < rows; r++) {
            String line = KOJIRO_MATRIX[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;
                Color color = getKojiroDotColor(ch, r, c);
                if (color != null) {
                    int px = startX + c * cellSize;
                    int py = startY + r * cellSize;
                    g2.setColor(color);
                    g2.fillRoundRect(px, py, dotSize, dotSize, arc, arc);
                }
            }
        }

        // 仅在已加载文件时才显示文件状态提示（统一使用中文字体，彻底避免乱码）
        if (isLoaded && wordFile != null) {
            g2.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
            g2.setColor(new Color(52, 211, 153));
            FontMetrics fm = g2.getFontMetrics();
            String title = "[已加载] " + truncateFileName(wordFile.getName(), 20);
            g2.drawString(title, (w - fm.stringWidth(title)) / 2, startY + totalH + 18);
        }

        g2.dispose();
    }

    /**
     * 绘制武藏点阵画布 (彻底移除未加载时的大白字提示)
     */
    private void paintMusashiDotMatrix(Graphics g, JLabel label, boolean isLoaded) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = label.getWidth();
        int h = label.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = MUSASHI_MATRIX.length;
        int cols = MUSASHI_MATRIX[0].length();

        int cellSize = Math.max(3, Math.min((w - 24) / cols, (h - 24) / rows));
        int gap = cellSize >= 5 ? 1 : 0;
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH) / 2 - (isLoaded ? 10 : 0);

        // 柔和洋红背景光晕
        int centerX = startX + 17 * cellSize;
        int centerY = startY + 10 * cellSize;
        int glowR = cellSize * (isMusicPlaying && (animTick % 2 == 0) ? 10 : 9);
        g2.setColor(new Color(244, 63, 94, isMusicPlaying ? 50 : (isLoaded ? 32 : 18)));
        g2.fillOval(centerX - glowR, centerY - glowR, glowR * 2, glowR * 2);

        // 逐点绘制
        for (int r = 0; r < rows; r++) {
            String line = MUSASHI_MATRIX[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;
                Color color = getMusashiDotColor(ch, r, c);
                if (color != null) {
                    int px = startX + c * cellSize;
                    int py = startY + r * cellSize;
                    g2.setColor(color);
                    g2.fillRoundRect(px, py, dotSize, dotSize, arc, arc);
                }
            }
        }

        // 仅在已加载文件时才显示文件状态提示（统一使用中文字体，彻底避免乱码）
        if (isLoaded && pdfFile != null) {
            g2.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
            g2.setColor(new Color(52, 211, 153));
            FontMetrics fm = g2.getFontMetrics();
            String title = "[已加载] " + truncateFileName(pdfFile.getName(), 20);
            g2.drawString(title, (w - fm.stringWidth(title)) / 2, startY + totalH + 18);
        }

        g2.dispose();
    }

    private JPanel createComparisonCard() {
        JPanel compPanel = new JPanel(new BorderLayout());
        compPanel.setOpaque(false);
        compPanel.setBorder(new EmptyBorder(5, 20, 20, 20));

        // 顶部：拖拽区域
        JPanel dropPanel = new JPanel(new GridLayout(1, 2, 25, 0));
        dropPanel.setOpaque(false);

        GlassDropPanel wordDropPanel = new GlassDropPanel();
        wordDropPanel.setPreferredSize(new Dimension(300, 140));
        wordDropPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        wordLabel = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false; // 事件穿透到底层 GlassDropPanel
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintKojiroDotMatrix(g, this, wordFile != null);
            }
        };
        wordLabel.setForeground(Color.WHITE);
        wordDropPanel.add(wordLabel, BorderLayout.CENTER);

        wordDropPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    clearWordFile();
                } else if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    toggleMusic(); // 单击切换音乐与点阵微动效
                }
            }
        });

        GlassDropPanel pdfDropPanel = new GlassDropPanel();
        pdfDropPanel.setPreferredSize(new Dimension(300, 140));
        pdfDropPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        pdfLabel = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false; // 事件穿透到底层 GlassDropPanel
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintMusashiDotMatrix(g, this, pdfFile != null);
            }
        };
        pdfLabel.setForeground(Color.WHITE);
        pdfDropPanel.add(pdfLabel, BorderLayout.CENTER);

        pdfDropPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    clearPdfFile();
                } else if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    toggleMusic(); // 单击切换音乐与点阵微动效
                }
            }
        });

        setupComparisonDND(wordDropPanel, true, wordLabel);
        setupComparisonDND(pdfDropPanel, false, pdfLabel);

        dropPanel.add(wordDropPanel);
        dropPanel.add(pdfDropPanel);

        // 中部：日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
        logArea.setForeground(Color.WHITE);
        logArea.setOpaque(false);
        logArea.setBackground(new Color(0, 0, 0, 0));
        logArea.setLineWrap(true);
        logArea.setBorder(new EmptyBorder(10, 15, 10, 15));
        logArea.setText("为了防止世界被破坏，为了保护世界的和平。\n");

        JScrollPane scrollPane = new JScrollPane(logArea) {
            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        scrollPane.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
        scrollPane.getViewport().setUI(new javax.swing.plaf.basic.BasicViewportUI());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBackground(new Color(0, 0, 0, 0));
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

        GlassPanel logGlassWrapper = new GlassPanel(new BorderLayout());
        logGlassWrapper.add(scrollPane, BorderLayout.CENTER);

        JPanel logContainer = new JPanel(new BorderLayout());
        logContainer.setOpaque(false);
        logContainer.setBorder(new EmptyBorder(15, 0, 0, 0));
        logContainer.add(logGlassWrapper, BorderLayout.CENTER);

        // 操作按钮与返回菜单并排放在顶部
        ActionButton btnRun = new ActionButton("开始比对");
        btnRun.setPreferredSize(new Dimension(130, 36));
        btnRun.addActionListener(e -> startMatch());

        ActionButton btnClear = new ActionButton("清空记录");
        btnClear.setPreferredSize(new Dimension(130, 36));
        btnClear.addActionListener(e -> {
            wordFile = null;
            pdfFile = null;
            wordLabel.setText("");
            pdfLabel.setText("");
            logArea.setText("已清空。为了防止世界杯破坏，为了保护世界和平。\n");
            repaintDropPanels();
        });

        leftTop.add(btnRun);
        leftTop.add(btnClear);

        compPanel.add(dropPanel, BorderLayout.NORTH);
        compPanel.add(logContainer, BorderLayout.CENTER);

        return compPanel;
    }

    // =========================================================================
    // 🎵 音频播放引擎与动效联动（支持点一下播放、再点一下关闭）
    // =========================================================================
    private void toggleMusic() {
        if (isMusicPlaying) {
            stopMusic();
        } else {
            playMusic();
        }
    }

    private void playMusic() {
        isMusicPlaying = true;
        startMusicAnimTimer();
        musicThread = Thread.ofVirtual().name("Comparison-MusicPlayer").start(() -> {
            while (isMusicPlaying) {
                try {
                    InputStream is = getClass().getResourceAsStream("/music/火箭队登场台词.mp3");
                    if (is == null) {
                        is = getClass().getClassLoader().getResourceAsStream("music/火箭队登场台词.mp3");
                    }
                    if (is == null) {
                        System.err.println("找不到 resources/music/火箭队登场台词.mp3，请检查资源路径！");
                        isMusicPlaying = false;
                        SwingUtilities.invokeLater(this::stopMusic);
                        break;
                    }
                    player = new Player(is);
                    player.play();
                } catch (Exception e) {
                    if (isMusicPlaying) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        });
    }

    private void startMusicAnimTimer() {
        if (musicAnimTimer == null) {
            musicAnimTimer = new javax.swing.Timer(250, e -> {
                if (isMusicPlaying) {
                    animTick++;
                    if (wordLabel != null) wordLabel.repaint();
                    if (pdfLabel != null) pdfLabel.repaint();
                }
            });
            musicAnimTimer.setCoalesce(true);
        }
        if (!musicAnimTimer.isRunning()) {
            musicAnimTimer.start();
        }
        repaintDropPanels();
    }

    private void stopMusic() {
        isMusicPlaying = false;
        if (musicAnimTimer != null && musicAnimTimer.isRunning()) {
            musicAnimTimer.stop();
        }
        if (player != null) {
            try {
                player.close();
            } catch (Exception ignored) {}
        }
        if (musicThread != null) {
            musicThread.interrupt();
        }
        repaintDropPanels();
    }

    private void repaintDropPanels() {
        if (wordLabel != null) wordLabel.repaint();
        if (pdfLabel != null) pdfLabel.repaint();
    }

    @Override
    public void removeNotify() {
        stopMusic();
        super.removeNotify();
    }

    /**
     * 双击监听面板清空 Word 文件
     */
    private void clearWordFile() {
        if (wordFile != null) {
            String name = wordFile.getName();
            wordFile = null;
            wordLabel.setText("");
            logComparison("已清空 Word 文档: " + name);
            repaintDropPanels();
        }
    }

    /**
     * 双击监听面板清空 PDF 文件
     */
    private void clearPdfFile() {
        if (pdfFile != null) {
            String name = pdfFile.getName();
            pdfFile = null;
            pdfLabel.setText("");
            logComparison("已清空 PDF 文档: " + name);
            repaintDropPanels();
        }
    }

    private void setupComparisonDND(GlassDropPanel dropTargetArea, boolean isWord, JLabel labelToUpdate) {
        new DropTarget(dropTargetArea, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                SwingUtilities.invokeLater(() -> dropTargetArea.setDragActive(true));
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                SwingUtilities.invokeLater(() -> dropTargetArea.setDragActive(false));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                SwingUtilities.invokeLater(() -> dropTargetArea.setDragActive(false));
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object transferData = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dtde.dropComplete(true);

                    if (transferData instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferData;
                        if (files == null || files.isEmpty()) {
                            return;
                        }

                        // 智能防呆：若同时拖入多个文件且包含 Word 与 PDF，自动归位到对应面板
                        if (files.size() >= 2) {
                            File wordCandidate = null;
                            File pdfCandidate = null;
                            for (File file : files) {
                                if (file != null && file.isFile()) {
                                    String lower = file.getName().toLowerCase();
                                    if ((lower.endsWith(".docx") || lower.endsWith(".doc")) && wordCandidate == null) {
                                        wordCandidate = file;
                                    } else if (lower.endsWith(".pdf") && pdfCandidate == null) {
                                        pdfCandidate = file;
                                    }
                                }
                            }
                            if (wordCandidate != null && pdfCandidate != null) {
                                final File finalWord = wordCandidate;
                                final File finalPdf = pdfCandidate;
                                SwingUtilities.invokeLater(() -> {
                                    wordFile = finalWord;
                                    pdfFile = finalPdf;
                                    labelToUpdate.setText("");
                                    repaintDropPanels();
                                    logComparison("智能识别完成：已自动将 Word 与 PDF 文件归位到对应面板。");
                                });
                                return;
                            }
                        }

                        File f = files.get(0);
                        if (f.isDirectory()) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(ComparisonPanel.this,
                                        "暂不支持对文件夹进行操作，请拖入具体的文件！",
                                        "格式错误",
                                        JOptionPane.WARNING_MESSAGE);
                                logComparison("提示：拖入的是文件夹，已取消加载。");
                            });
                            return;
                        }

                        String name = f.getName().toLowerCase();
                        if (isWord) {
                            if (name.endsWith(".pdf")) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(ComparisonPanel.this,
                                            "文档放反了！\n当前为 Word 文档监听区域，请勿放入 PDF 文件。\n请将 PDF 文件拖入右侧面板。",
                                            "文档放置错误",
                                            JOptionPane.WARNING_MESSAGE);
                                    logComparison("提示：左侧为 Word 文档区域，请勿放入 PDF 文件。");
                                });
                                return;
                            }
                            if (!name.endsWith(".docx") && !name.endsWith(".doc")) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(ComparisonPanel.this,
                                            "文件格式错误！\n左侧区域仅支持放入 Word (.docx / .doc) 格式文档。\n当前文件：" + f.getName(),
                                            "格式错误",
                                            JOptionPane.ERROR_MESSAGE);
                                    logComparison("错误：文件格式不兼容（仅支持 Word 文档）：" + f.getName());
                                });
                                return;
                            }
                            SwingUtilities.invokeLater(() -> {
                                wordFile = f;
                                labelToUpdate.setText("");
                                repaintDropPanels();
                                logComparison("已加载 Word 文件: " + f.getName());
                            });
                        } else {
                            if (name.endsWith(".docx") || name.endsWith(".doc")) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(ComparisonPanel.this,
                                            "文档放反了！\n当前为 PDF 文档监听区域，请勿放入 Word 文件。\n请将 Word 文件拖入左侧面板。",
                                            "文档放置错误",
                                            JOptionPane.WARNING_MESSAGE);
                                    logComparison("提示：右侧为 PDF 文档区域，请勿放入 Word 文件。");
                                });
                                return;
                            }
                            if (!name.endsWith(".pdf")) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(ComparisonPanel.this,
                                            "文件格式错误！\n右侧区域仅支持放入 PDF (.pdf) 格式文档。\n当前文件：" + f.getName(),
                                            "格式错误",
                                            JOptionPane.ERROR_MESSAGE);
                                    logComparison("错误：文件格式不兼容（仅支持 PDF 文档）：" + f.getName());
                                });
                                return;
                            }
                            SwingUtilities.invokeLater(() -> {
                                pdfFile = f;
                                labelToUpdate.setText("");
                                repaintDropPanels();
                                logComparison("已加载 PDF 文件: " + f.getName());
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        dtde.dropComplete(false);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void startMatch() {
        if (wordFile == null || pdfFile == null) {
            logComparison("请先拖放 Word (.docx) 和 PDF 文件！");
            JOptionPane.showMessageDialog(ComparisonPanel.this, "请先拖放 Word (.docx) 和 PDF 文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        logComparison("正在解析并处理任务...");
        Thread.ofVirtual().name("Comparison-Worker").start(() -> {
            try {
                String w = readWord(wordFile);
                String p = readPdf(pdfFile);
                SwingUtilities.invokeLater(() -> executeCompare(w, p));
            } catch (Exception ex) {
                logComparison("错误：文件解析异常，请检查文件格式是否正确。");
            }
        });
    }

    private void executeCompare(String w, String p) {
        logComparison("--- 比对分析结果 ---");
        boolean isAllPassed = true;

        String wNo = findPattern(w, "采购编号[:：]?\\s*([^\\s]+)");
        String pNo = findPattern(p, "(?:采购项目编号|招标编号|采购编号)[:：]?\\s*([^\\s\\n]+)");

        if (wNo != null) wNo = wNo.replaceAll("[)）]+$", "").trim();
        if (pNo != null) pNo = pNo.replaceAll("[)）]+$", "").trim();

        if (!checkResult(wNo, pNo, "【项目编号】")) {
            isAllPassed = false;
        }

        String wc = w.replaceAll("\\s", "");
        String pc = p.replaceAll("\\s", "");

        String wT1 = findPattern(wc, "至(\\d{4}年\\d{1,2}月\\d{1,2}日(上午|下午)?\\d{1,2}时(?:\\d{1,2}分)?)");
        String pT1 = findPattern(pc, "(?:采购文件获取截止时间|获取时间.*?到)(\\d{4}[年-]\\d{1,2}[月-]\\d{1,2}日?\\s*(?:上午|下午)?\\d{1,2}时?\\s*(?::|分)?\\d{1,2}分?)");

        if (!checkTimeResult(wT1, pT1, "【获取截止时间】")) {
            isAllPassed = false;
        }

        String wT2 = findPattern(wc, "截止时间.*?(\\d{4}年\\d{1,2}月\\d{1,2}日(上午|下午)?\\d{1,2}时\\d{1,2}分)");
        String pT2 = findPattern(pc, "(?:开启应答文件时间|递交截止时间|首次应答文件提交的截止时间).*?(\\d{4}[年-]\\d{1,2}[月-]\\d{1,2}日?\\s*(?:上午|下午)?\\d{1,2}时?\\s*(?::|分)?\\d{1,2}分?)");

        if (!checkTimeResult(wT2, pT2, "【应答截止时间】")) {
            isAllPassed = false;
        }

        final boolean finalStatus = isAllPassed;
        SwingUtilities.invokeLater(() -> {
            if (finalStatus) {
                JOptionPane.showMessageDialog(ComparisonPanel.this, "成功！所有比对项目匹配无误。", "校对结果", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(ComparisonPanel.this, "提示：发现不匹配项，详情请查看日志记录。", "校对结果", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private boolean checkResult(String w, String p, String lab) {
        if (w == null || p == null) {
            logComparison(lab + " 提取失败 (数据缺失)");
            return false;
        }
        boolean match = w.trim().equalsIgnoreCase(p.trim());
        logComparison(lab + " -> Word:[" + w.trim() + "] 对比 PDF:[" + p.trim() + "] -> " + (match ? "匹配" : "冲突"));
        return match;
    }

    private boolean checkTimeResult(String wt, String pt, String lab) {
        if (wt == null || pt == null) {
            logComparison(lab + " 提取数据缺失");
            return false;
        }
        String nW = normalizeTime(wt);
        String nP = pt.replaceAll("[^0-9]", "");
        boolean match = nP.startsWith(nW);
        logComparison(lab + " -> Word:[" + nW + "] 对比 PDF:[" + nP + "] -> " + (match ? "匹配" : "冲突"));
        return match;
    }

    private String normalizeTime(String raw) {
        if (raw == null) return "";
        Matcher m = Pattern.compile("(\\d{4})[年-]?(\\d{1,2})[月-]?(\\d{1,2})日?\\s*(上午|下午)?\\s*(\\d{1,2})[时:]?(\\d{1,2})?分?").matcher(raw);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(5));

            if (raw.contains("下午") && hour < 12) hour += 12;
            if (raw.contains("上午") && hour == 12) hour = 0;

            int min = (m.group(6) != null) ? Integer.parseInt(m.group(6)) : 0;
            return String.format("%04d%02d%02d%02d%02d", year, month, day, hour, min);
        }
        return raw.replaceAll("[^0-9]", "");
    }

    private String readWord(File f) throws Exception {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".doc")) {
            try (FileInputStream fis = new FileInputStream(f);
                 WordExtractor extractor = new WordExtractor(fis)) {
                return extractor.getText();
            }
        }
        try (FileInputStream fis = new FileInputStream(f); XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private String readPdf(File f) throws Exception {
        try (PDDocument doc = PDDocument.load(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String findPattern(String t, String r) {
        Matcher m = Pattern.compile(r).matcher(t);
        return m.find() ? m.group(1) : null;
    }

    private void logComparison(String m) {
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(m + "\n");
            }
        });
    }
}