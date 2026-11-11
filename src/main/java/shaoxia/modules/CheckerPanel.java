package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.awt.Font;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassDropPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;

/**
 * 数据校对助手 CheckerPanel (极致无按钮版 - 沉浸式 Travelers 交互 + 模糊比对功能)
 */
public class CheckerPanel extends BackgroundPanel {

    private JLabel dl;
    private GlassDropPanel dropArea; // 使用全局封装的高性能毛玻璃组件

    // =========================================================================
    // 星际飞船航行在宇宙深空 —— 56x27 像素点阵矩阵 (仿照 ConverterPanel 画卷风格)
    // 图例说明：
    // '.' 透明深空   '*' 璀璨恒星   '+' 暖金微星
    // 'R' 土星光环   'P' 土星向阳   'p' 土星背光
    // 'b' 水系行星大气边缘          'E' 水系行星深蓝海域   'c' 水系行星翠绿陆地
    // 'm' 静谧弯月   'n' 紫晶星云外围                     'N' 紫晶星云核心
    // '#' 飞船深色装甲骨架          '=' 飞船向阳高光白板   's' 飞船背光冷灰装甲
    // 'G' 飞船座舱高光玻璃          'g' 飞船座舱暗部玻璃   'X' 飞船机翼赤红标志
    // 'T' 飞船引擎金属喷口          'F' 引擎炽白核心焰     'f' 等离子推进亮青焰
    // '~' 散逸离子流微尘            '>' 飞船锐利导流艏尖
    // =========================================================================
    private static final String[] SPACE_MATRIX = {
            "..*..................+...............*.............+....",
            "..........PPPP................*.........................",
            "....+...PPPPPPpp.RRR......................*...mm........",
            "........PPPPPRRRRR......+...................mmm......*..",
            ".....RRRRRRRRRpppp.................*........mmm.........",
            "....RRR.pppppppp............*...............mmm.........",
            "..........pppp................................mm....+...",
            "...*.................+..................*...............",
            "........................##.........................*....",
            "............*...........#==#............................",
            ".........................#X===#..............+..........",
            ".....+............~fFTT###=======###....................",
            ".............~..~~ffFTT#========GGGG==#..........*......",
            "...*.....~..~~fffFFFTTT#====X===GGGGGG===>..............",
            ".............~..~~ffFTT#ssssssssggggss#.................",
            "..................~fFTT###sssssss###............+.......",
            "........*................#Xsss#.........................",
            "........................#ss#.......*....................",
            "........................##............................*.",
            "....+..............*.......................bbbb.........",
            ".........nn..............................bbEEEEbb.......",
            ".......nnNNnn...............+............bEEccEEb.......",
            "......nNN*NNn.........*..................bcccEEEb....+..",
            ".......nnNNn.............................bbEEEEbb.......",
            ".........nn...................*............bbbb.........",
            "...*..............+................................*....",
            "....................................+..................."
    };

    /**
     * 点阵调色盘映射（仿照 ConverterPanel，支持音乐播放时的星辰闪烁与尾焰微动效）
     */
    private Color getDotColor(char ch, int row, int col) {
        boolean shimmer = isPlaying && ((row + col + animTick) % 3 == 0);
        boolean pulse = isPlaying && ((col + animTick) % 2 == 0);
        switch (ch) {
            case '*': // 璀璨恒星
                return shimmer ? new Color(255, 255, 255) : new Color(210, 230, 255, 200);
            case '+': // 暖金微星
                return shimmer ? new Color(255, 245, 180) : new Color(254, 202, 87, 180);
            case 'R': // 土星环带（金黄光环）
                return shimmer ? new Color(255, 235, 165) : new Color(251, 191, 36, 215);
            case 'P': // 土星向阳面
                return new Color(251, 146, 60, 240);
            case 'p': // 土星背光面
                return new Color(180, 83, 9, 225);
            case 'b': // 远方水系行星大气边缘
                return shimmer ? new Color(125, 211, 252) : new Color(56, 189, 248, 195);
            case 'E': // 远方水系行星深蓝海域
                return new Color(2, 132, 199, 235);
            case 'c': // 远方水系行星翠绿大陆
                return new Color(52, 211, 153, 230);
            case 'm': // 静谧弯月
                return shimmer ? new Color(254, 249, 195) : new Color(253, 230, 138, 205);
            case 'n': // 紫晶星云外围
                return shimmer ? new Color(192, 132, 252, 190) : new Color(168, 85, 247, 135);
            case 'N': // 紫晶星云核心
                return shimmer ? new Color(244, 114, 182, 230) : new Color(217, 70, 239, 195);
            case '#': // 飞船深色结构骨架
                return new Color(51, 65, 85);
            case '=': // 飞船向阳高光钛白装甲
                return new Color(248, 250, 252);
            case 's': // 飞船背光冷灰装甲
                return new Color(148, 163, 184);
            case 'G': // 飞船座舱高光玻璃
                return shimmer ? new Color(165, 243, 252) : new Color(56, 189, 248);
            case 'g': // 飞船座舱暗部玻璃
                return new Color(14, 116, 144);
            case 'X': // 飞船机翼赤红标志
                return shimmer ? new Color(252, 165, 165) : new Color(239, 68, 68);
            case 'T': // 飞船引擎金属喷口
                return new Color(100, 116, 139);
            case 'F': // 引擎炽白高能核心焰
                return pulse ? new Color(255, 255, 255) : new Color(186, 230, 253);
            case 'f': // 小飞船等离子推进尾焰
                return pulse ? new Color(56, 189, 248) : new Color(14, 165, 233);
            case '~': // 散逸离子流微尘
                return shimmer ? new Color(125, 211, 252, 220) : new Color(56, 189, 248, 110);
            case '>': // 飞船锐利导流艏尖
                return pulse ? new Color(255, 255, 255) : new Color(224, 242, 254);
            default:
                return null;
        }
    }

    private final Color DASHBOARD_BG = new Color(45, 48, 50);
    private final Color TEXT_COLOR = new Color(230, 230, 230);

    // 音乐播放与点阵微动效控制 (仿照 ConverterPanel 实现)
    private volatile boolean isPlaying = false;
    private javazoom.jl.player.Player player;
    private javax.swing.Timer musicAnimTimer;
    private int animTick = 0;

    // 模糊比对级别 (0: 精确, 1: 保留一位小数, 2: 个位, 3: 十位)
    private int fuzzyLevel = 0;
    private volatile boolean isChecking = false; // 新增：防止并发解析大表卡死

    // --- 高级现代感 UI 颜色配置 ---
    private final Color MODERN_BG = new Color(30, 32, 36);        // 极度深邃的背景黑
    private final Color PANEL_BG = new Color(42, 45, 52);         // 面板浮层黑
    private final Color BORDER_COLOR = new Color(65, 70, 80);     // 隐约可见的边框灰
    private final Color TEXT_PRIMARY = new Color(240, 240, 245);  // 高亮白字体
    private final Color TEXT_SECONDARY = new Color(170, 175, 185);// 次级灰字体
    private final Font TITLE_FONT = FontUtils.getChineseFont(Font.BOLD, 15);
    private final Font BODY_FONT = FontUtils.getChineseFont(Font.PLAIN, 14);

    public CheckerPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout());
        setOpaque(false);

        // 仅保留最基础的返回主菜单（防困死），其他按钮全部拔除
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic();
            parent.showMenu();
        });

        // 取消 FlowLayout 默认的 5px 间隙，实现精确像素对齐
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);
        leftTop.add(backBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        // --- 全新 SVG 矢量重绘：右上角模糊比对精度控制器 ---
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); // 消除边距
        rightTop.setOpaque(false);

        FuzzyModeIndicator fuzzyIndicator = new FuzzyModeIndicator();
        rightTop.add(fuzzyIndicator);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // 修复缩放消失：改用弹性边距包裹的 BorderLayout
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        // 这里的边距是弹性的，窗口变小也不会把中间的框挤碎
        centerPanel.setBorder(BorderFactory.createEmptyBorder(40, 80, 80, 80));

        // 直接实例化全局拖拽面板组件
        dropArea = new GlassDropPanel();
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 让黑洞本身变成交互按钮
        dropArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleMusic(); // 点击任意区域触发音乐
            }
        });

        // 带事件穿透 + 代码点阵绘制的中央组件 (仿照 ConverterPanel)
        dl = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                // 核心魔法：让系统认为文字是透明的空气，拖拽事件和鼠标点击将100%穿透到底层的黑洞面板上！
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintSpaceDotMatrix(g);
            }
        };
        dropArea.add(dl, BorderLayout.CENTER);

        // 保持拖拽进入区域执行校对的功能
        setCheckDropTarget(dropArea);

        centerPanel.add(dropArea, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        adjustFontSize();

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustFontSize();
            }
        });
    }

    private void adjustFontSize() {
        if (dl != null) {
            dl.setText("");
            dl.repaint();
        }
    }

    /**
     * 核心点阵渲染器：绘制飞船漫游星系点阵图卷与音乐微光动效 (仿照 ConverterPanel)
     */
    private void paintSpaceDotMatrix(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = dl.getWidth();
        int h = dl.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = SPACE_MATRIX.length;
        int cols = SPACE_MATRIX[0].length();

        // 根据当前面板宽高自适应计算点阵颗粒尺寸（严格参照 ConverterPanel 布局算法）
        int cellSize = Math.max(3, Math.min(w / (cols + 6), (h - 28) / (rows + 2)));
        int gap = cellSize >= 5 ? 1 : 0; // 点阵颗粒间隙，营造经典像素点阵屏质感
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH - (cellSize * 2)) / 2;

        // 1. 左上土星柔和背光光晕
        int saturnCenterX = startX + 11 * cellSize + cellSize / 2;
        int saturnCenterY = startY + 3 * cellSize + cellSize / 2;
        int saturnGlowRadius = cellSize * (isPlaying && (animTick % 2 == 0) ? 6 : 5);
        g2.setColor(new Color(251, 191, 36, isPlaying ? 32 : 16));
        g2.fillOval(saturnCenterX - saturnGlowRadius, saturnCenterY - saturnGlowRadius, saturnGlowRadius * 2, saturnGlowRadius * 2);

        // 2. 左下紫晶星云背光光晕
        int nebulaCenterX = startX + 9 * cellSize + cellSize / 2;
        int nebulaCenterY = startY + 22 * cellSize + cellSize / 2;
        int nebulaGlowRadius = cellSize * (isPlaying && (animTick % 2 == 1) ? 5 : 4);
        g2.setColor(new Color(217, 70, 239, isPlaying ? 30 : 14));
        g2.fillOval(nebulaCenterX - nebulaGlowRadius, nebulaCenterY - nebulaGlowRadius, nebulaGlowRadius * 2, nebulaGlowRadius * 2);

        // 3. 右下水系行星湛蓝大气光晕
        int earthCenterX = startX + 44 * cellSize + cellSize / 2;
        int earthCenterY = startY + 21 * cellSize + cellSize / 2;
        int earthGlowRadius = cellSize * 5;
        g2.setColor(new Color(56, 189, 248, isPlaying ? 28 : 14));
        g2.fillOval(earthCenterX - earthGlowRadius, earthCenterY - earthGlowRadius, earthGlowRadius * 2, earthGlowRadius * 2);

        // 4. 飞船等离子推进器与舰体氛围光晕
        int thrusterCenterX = startX + 19 * cellSize + cellSize / 2;
        int thrusterCenterY = startY + 13 * cellSize + cellSize / 2;
        int thrusterGlowRadius = cellSize * (isPlaying && ((animTick / 2) % 2 == 0) ? 6 : 5);
        g2.setColor(new Color(56, 189, 248, isPlaying ? 42 : 18));
        g2.fillOval(thrusterCenterX - thrusterGlowRadius, thrusterCenterY - thrusterGlowRadius, thrusterGlowRadius * 2, thrusterGlowRadius * 2);

        int shipCenterX = startX + 31 * cellSize;
        int shipCenterY = startY + 13 * cellSize + cellSize / 2;
        int shipGlowW = cellSize * 18;
        int shipGlowH = cellSize * 9;
        g2.setColor(new Color(148, 163, 184, isPlaying ? 22 : 10));
        g2.fillOval(shipCenterX - shipGlowW / 2, shipCenterY - shipGlowH / 2, shipGlowW, shipGlowH);

        // 逐行逐列扫描绘制点阵
        for (int r = 0; r < rows; r++) {
            String line = SPACE_MATRIX[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;

                Color color = getDotColor(ch, r, c);
                if (color != null) {
                    int px = startX + c * cellSize;
                    int py = startY + r * cellSize;
                    g2.setColor(color);
                    g2.fillRoundRect(px, py, dotSize, dotSize, arc, arc);
                }
            }
        }

        // 底部微缩点阵铭文（与 ConverterPanel 字体排版与位置完全保持一致）
        int fontSize = Math.max(11, (int) (cellSize * 1.8));
        g2.setFont(FontUtils.getEnglishFont(Font.BOLD, fontSize));
        if (isChecking) {
            g2.setColor(new Color(72, 219, 251, 230));
            String caption = "C H E C K I N G   D A T A . . .";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, cellSize * 3);
            g2.drawString(caption, textX, textY);
        } else {
            g2.setColor(new Color(226, 232, 240, isPlaying ? 210 : 140));
            String caption = "T R A V E L E R S";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, cellSize * 3);
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }

    // --- 隐形音乐播放引擎（联动点阵微光动效，仿照 ConverterPanel） ---
    private void toggleMusic() {
        if (isPlaying) {
            stopMusic();
        } else {
            isPlaying = true;
            startMusicAnimTimer();
            Thread.ofVirtual().name("Checker-MusicPlayer").start(() -> {
                try {
                    java.io.InputStream is = getClass().getResourceAsStream("/music/Travelers.mp3");

                    if (is != null) {
                        player = new javazoom.jl.player.Player(is);
                        player.play(); // 阻塞播放

                        if (isPlaying) {
                            SwingUtilities.invokeLater(this::stopMusic);
                        }
                    } else {
                        System.out.println("提示：类路径中未找到 /music/Travelers.mp3");
                        SwingUtilities.invokeLater(this::stopMusic);
                    }
                } catch (Exception e) {
                    System.out.println("音乐播放中断或异常: " + e.getMessage());
                    SwingUtilities.invokeLater(this::stopMusic);
                }
            });
        }
    }

    private void startMusicAnimTimer() {
        if (musicAnimTimer == null) {
            musicAnimTimer = new javax.swing.Timer(400, e -> {
                if (isPlaying && !isChecking) {
                    animTick++;
                    dl.repaint();
                }
            });
            musicAnimTimer.setCoalesce(true);
        }
        if (!musicAnimTimer.isRunning()) {
            musicAnimTimer.start();
        }
        dl.repaint();
    }

    private void stopMusic() {
        isPlaying = false;
        if (musicAnimTimer != null && musicAnimTimer.isRunning()) {
            musicAnimTimer.stop();
        }
        if (player != null) {
            player.close(); // 强行切断底层音频流
        }
        if (dl != null) {
            dl.repaint();
        }
    }

    @Override
    public void removeNotify() {
        stopMusic();
        super.removeNotify();
    }

    private void setCheckDropTarget(GlassDropPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                // 异步唤醒流光引擎，避免阻塞 Windows 原生 OLE 拖拽线程
                SwingUtilities.invokeLater(() -> p.setDragActive(true));
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // 保持空实现，dragEnter 已接收拖拽，避免高频轰炸 OLE 消息队列
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                // 异步关闭流光特效
                SwingUtilities.invokeLater(() -> p.setDragActive(false));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                // 接收文件瞬间异步恢复透明面板静默状态
                SwingUtilities.invokeLater(() -> p.setDragActive(false));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    // 立刻释放系统底层拖拽锁，闭合生命周期
                    dtde.dropComplete(true);

                    if (isChecking) return;
                    if (!files.isEmpty()) {
                        File targetFile = files.get(0);
                        isChecking = true;
                        dl.repaint();
                        Thread.ofVirtual().name("Checker-ExcelWorker").start(() -> {
                            try {
                                processExcel(targetFile);
                            } finally {
                                isChecking = false;
                                // 显式调用外层 CheckerPanel 的实例方法
                                SwingUtilities.invokeLater(() -> CheckerPanel.this.adjustFontSize());
                            }
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try { dtde.dropComplete(false); } catch (Exception ignored) {}
                }
            }
        });
    }

    static class QuoteDropNode {
        String phaseName;
        Double dropPercent;

        QuoteDropNode(String phaseName, Double dropPercent) {
            this.phaseName = phaseName;
            this.dropPercent = dropPercent;
        }
    }

    static class PriceNode {
        String roundName;
        Double price;

        PriceNode(String roundName, Double price) {
            this.roundName = roundName;
            this.price = price;
        }
    }

    /**
     * 根据当前选择的模糊级别对价格进行处理
     * 0: 精确
     * 1: 保留一位小数
     * 2: 个位
     * 3: 十位
     */
    private Double applyFuzzyRounding(Double price, int level) {
        if (price == null) return null;
        switch (level) {
            case 1: return (double) Math.round(price * 10) / 10.0; // 保留一位小数
            case 2: return (double) Math.round(price); // 个位
            case 3: return (double) (Math.round(price / 10.0) * 10); // 十位
            default: return price; // 精确
        }
    }

    private void processExcel(File file) {
        List<String> globalErrors = new ArrayList<>();
        Map<String, List<QuoteDropNode>> globalDropDataMap = new LinkedHashMap<>();
        Map<String, List<PriceNode>> globalPriceDataMap = new LinkedHashMap<>();
        Set<String> globalFlaggedKeys = new HashSet<>();

        Map<String, Map<Integer, Map<String, List<String>>>> pkgPercentDupMap = new HashMap<>();
        Map<String, Map<Integer, Map<String, List<String>>>> pkgValueDupMap = new HashMap<>();
        Map<String, Map<Integer, Map<String, Double>>> pkgRoundPricesMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            int supIdx = -1;
            int pkgIdx = -1;
            String[] rounds = {"首轮报价", "二轮报价", "三轮报价", "四轮报价", "五轮报价", "六轮报价", "终轮报价"};
            int[] colIdxMap = new int[rounds.length];
            Arrays.fill(colIdxMap, -1);
            boolean foundHeader = false;

            for (Row row : sheet) {
                if (!foundHeader) {
                    for (Cell cell : row) {
                        String val = getCellValue(cell);
                        if (val.contains("供应商名称")) supIdx = cell.getColumnIndex();
                        if (val.equals("标包编号")) pkgIdx = cell.getColumnIndex();
                        for (int i = 0; i < rounds.length; i++) {
                            if (val.equals(rounds[i])) colIdxMap[i] = cell.getColumnIndex();
                        }
                    }
                    if (supIdx != -1) foundHeader = true;
                    continue;
                }

                String company = getCellValue(row.getCell(supIdx)).replaceAll("[\\n\\r\\t]", "").trim();
                if (company.isEmpty() || company.contains("※")) continue;

                String pkgName = "默认标包";
                if (pkgIdx != -1) {
                    String rawPkg = getCellValue(row.getCell(pkgIdx));
                    if (!rawPkg.trim().isEmpty()) {
                        pkgName = rawPkg.trim().replace("^L^", "");
                    }
                }
                String uniqueKey = company + " [" + pkgName + "]";

                Double prevPrice = null;
                String prevRoundName = null;
                List<QuoteDropNode> companyDrops = new ArrayList<>();
                List<PriceNode> companyPrices = new ArrayList<>();

                for (int i = 0; i < colIdxMap.length; i++) {
                    int cIdx = colIdxMap[i];
                    if (cIdx == -1) continue;

                    Double currPrice = getNumericValue(row.getCell(cIdx));

                    if (currPrice != null) {
                        currPrice = applyFuzzyRounding(currPrice, fuzzyLevel);

                        companyPrices.add(new PriceNode(rounds[i], currPrice));

                        pkgRoundPricesMap.computeIfAbsent(pkgName, k -> new HashMap<>())
                                .computeIfAbsent(i, k -> new HashMap<>())
                                .put(uniqueKey, currPrice);

                        if (prevPrice != null && prevRoundName != null) {
                            if (currPrice > prevPrice) {
                                globalErrors.add("【涨价异常】" + uniqueKey + "\n    " + rounds[i] + "(" + currPrice + ") 高于上一轮(" + prevPrice + ")");
                                globalFlaggedKeys.add(uniqueKey);
                            }

                            double dropPercent = 0.0;
                            if (prevPrice > 0) {
                                dropPercent = (prevPrice - currPrice) / prevPrice * 100.0;
                                String percentStr = String.format("%.4f", dropPercent);

                                pkgPercentDupMap.computeIfAbsent(pkgName, k -> new HashMap<>())
                                        .computeIfAbsent(i, k -> new HashMap<>())
                                        .computeIfAbsent(percentStr, k -> new ArrayList<>())
                                        .add(uniqueKey);

                                double dropValue = prevPrice - currPrice;
                                if (dropValue > 0.0001) {
                                    String valueStr = String.format("%.2f", dropValue);
                                    pkgValueDupMap.computeIfAbsent(pkgName, k -> new HashMap<>())
                                            .computeIfAbsent(i, k -> new HashMap<>())
                                            .computeIfAbsent(valueStr, k -> new ArrayList<>())
                                            .add(uniqueKey);
                                }
                            }

                            String phaseName = prevRoundName.replace("报价", "") + "->" + rounds[i].replace("报价", "");
                            companyDrops.add(new QuoteDropNode(phaseName, dropPercent));
                        }
                        prevPrice = currPrice;
                        prevRoundName = rounds[i];
                    }
                }
                if (!companyDrops.isEmpty()) globalDropDataMap.put(uniqueKey, companyDrops);
                if (!companyPrices.isEmpty()) globalPriceDataMap.put(uniqueKey, companyPrices);
            }

            for (String pkgName : pkgRoundPricesMap.keySet()) {

                Map<Integer, Map<String, List<String>>> percentMap = pkgPercentDupMap.getOrDefault(pkgName, new HashMap<>());
                for (Map.Entry<Integer, Map<String, List<String>>> roundEntry : percentMap.entrySet()) {
                    String roundName = rounds[roundEntry.getKey()];
                    for (Map.Entry<String, List<String>> pEntry : roundEntry.getValue().entrySet()) {
                        List<String> keys = pEntry.getValue();
                        if (keys.size() > 1 && Double.parseDouble(pEntry.getKey()) > 0.0001) {
                            globalErrors.add("【一致比例降幅】[" + pkgName + "] " + roundName + " 降幅: " + pEntry.getKey() + "%\n    涉及:\n      - " + String.join("\n      - ", keys));
                            globalFlaggedKeys.addAll(keys);
                        }
                    }
                }

                Map<Integer, Map<String, List<String>>> valueMap = pkgValueDupMap.getOrDefault(pkgName, new HashMap<>());
                for (Map.Entry<Integer, Map<String, List<String>>> roundEntry : valueMap.entrySet()) {
                    String roundName = rounds[roundEntry.getKey()];
                    for (Map.Entry<String, List<String>> vEntry : roundEntry.getValue().entrySet()) {
                        List<String> keys = vEntry.getValue();
                        if (keys.size() > 1) {
                            globalErrors.add("【一致定额降幅】[" + pkgName + "] " + roundName + " 降幅: " + vEntry.getKey() + " \n    涉及:\n      - " + String.join("\n      - ", keys));
                            globalFlaggedKeys.addAll(keys);
                        }
                    }
                }

                Map<Integer, Map<String, Double>> roundPrices = pkgRoundPricesMap.get(pkgName);
                if (roundPrices != null) {
                    for (Map.Entry<Integer, Map<String, Double>> entry : roundPrices.entrySet()) {
                        int roundIdx = entry.getKey();
                        String roundName = rounds[roundIdx];
                        Map<String, Double> prices = entry.getValue();
                        if (prices.size() >= 3) {
                            checkAPGP("[" + pkgName + "] " + roundName, prices, globalErrors, globalFlaggedKeys);
                        }
                    }
                }
            }

            if (globalErrors.isEmpty() && globalFlaggedKeys.isEmpty()) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(CheckerPanel.this, "校对完成，数据逻辑正常，未发现同标包协同风险。")
                );
            } else {
                Map<String, List<QuoteDropNode>> flaggedHistoryDrops = new HashMap<>();
                Map<String, List<PriceNode>> flaggedHistoryPrices = new HashMap<>();

                for (String k : globalFlaggedKeys) {
                    if (globalDropDataMap.containsKey(k)) flaggedHistoryDrops.put(k, globalDropDataMap.get(k));
                    if (globalPriceDataMap.containsKey(k)) flaggedHistoryPrices.put(k, globalPriceDataMap.get(k));
                }
                SwingUtilities.invokeLater(() ->
                        showResultsDialog(globalErrors, flaggedHistoryPrices, flaggedHistoryDrops)
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(CheckerPanel.this, "解析失败，请检查文件格式。")
            );
        }
    }

    private void checkAPGP(String roundName, Map<String, Double> companyPrices, List<String> errors, Set<String> flaggedCompanies) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(companyPrices.entrySet());
        list.sort(Map.Entry.comparingByValue());
        int n = list.size();

        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                double p1 = list.get(i).getValue();
                double p2 = list.get(j).getValue();

                double diff = p2 - p1;
                if (diff > 0.0001) {
                    List<String> apSeq = new ArrayList<>();
                    List<String> rawNames = new ArrayList<>();
                    apSeq.add(list.get(i).getKey() + "(" + p1 + ")");
                    apSeq.add(list.get(j).getKey() + "(" + p2 + ")");
                    rawNames.add(list.get(i).getKey());
                    rawNames.add(list.get(j).getKey());

                    double expected = p2 + diff;
                    for (int k = j + 1; k < n; k++) {
                        if (Math.abs(list.get(k).getValue() - expected) < 0.0001) {
                            apSeq.add(list.get(k).getKey() + "(" + list.get(k).getValue() + ")");
                            rawNames.add(list.get(k).getKey());
                            expected += diff;
                        }
                    }
                    if (apSeq.size() >= 3) {
                        errors.add(String.format("【等差串通预警】%s 中发现等差报价序列 (等差额: %.2f)\n    涉及:\n      - %s", roundName, diff, String.join("\n      - ", apSeq)));
                        flaggedCompanies.addAll(rawNames);
                    }
                }

                if (p1 > 0.0001) {
                    double ratio = p2 / p1;
                    if (ratio > 1.0001) {
                        List<String> gpSeq = new ArrayList<>();
                        List<String> rawNamesGP = new ArrayList<>();
                        gpSeq.add(list.get(i).getKey() + "(" + p1 + ")");
                        gpSeq.add(list.get(j).getKey() + "(" + p2 + ")");
                        rawNamesGP.add(list.get(i).getKey());
                        rawNamesGP.add(list.get(j).getKey());

                        double expected = p2 * ratio;
                        for (int k = j + 1; k < n; k++) {
                            if (Math.abs(list.get(k).getValue() - expected) < 0.0001) {
                                gpSeq.add(list.get(k).getKey() + "(" + list.get(k).getValue() + ")");
                                rawNamesGP.add(list.get(k).getKey());
                                expected *= ratio;
                            }
                        }
                        if (gpSeq.size() >= 3) {
                            errors.add(String.format("【等比串通预警】%s 中发现等比报价序列 (比例系数: %.4f)\n    涉及:\n      - %s", roundName, ratio, String.join("\n      - ", gpSeq)));
                            flaggedCompanies.addAll(rawNamesGP);
                        }
                    }
                }
            }
        }

        List<Map.Entry<String, Double>> descList = new ArrayList<>(companyPrices.entrySet());
        descList.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < n - 3; i++) {
            for (int j = i + 1; j < n - 2; j++) {
                for (int k = j + 1; k < n - 1; k++) {
                    double p1 = descList.get(i).getValue();
                    double p2 = descList.get(j).getValue();
                    double p3 = descList.get(k).getValue();

                    double d1 = p1 - p2;
                    double d2 = p2 - p3;

                    if (d1 > 0.0001 && d2 > 0.0001) {
                        double diffRatio = d2 / d1;

                        List<String> diffGpSeq = new ArrayList<>();
                        List<String> rawNamesDiffGP = new ArrayList<>();

                        diffGpSeq.add(descList.get(i).getKey() + "(" + p1 + ")");
                        diffGpSeq.add(descList.get(j).getKey() + "(" + p2 + ")");
                        diffGpSeq.add(descList.get(k).getKey() + "(" + p3 + ")");

                        rawNamesDiffGP.add(descList.get(i).getKey());
                        rawNamesDiffGP.add(descList.get(j).getKey());
                        rawNamesDiffGP.add(descList.get(k).getKey());

                        double expectedDiff = d2 * diffRatio;
                        double expectedPrice = p3 - expectedDiff;

                        for (int m = k + 1; m < n; m++) {
                            if (Math.abs(descList.get(m).getValue() - expectedPrice) < 0.0001) {
                                diffGpSeq.add(descList.get(m).getKey() + "(" + descList.get(m).getValue() + ")");
                                rawNamesDiffGP.add(descList.get(m).getKey());

                                expectedDiff *= diffRatio;
                                expectedPrice -= expectedDiff;
                            }
                        }

                        if (diffGpSeq.size() >= 4) {
                            errors.add(String.format("【差值等比串通预警】%s 中发现差值呈等比递减规律 (差值衰减系数: %.4f)\n    涉及:\n      - %s",
                                    roundName, diffRatio, String.join("\n      - ", diffGpSeq)));
                            flaggedCompanies.addAll(rawNamesDiffGP);
                        }
                    }
                }
            }
        }
    }

    private void showResultsDialog(List<String> errors, Map<String, List<PriceNode>> priceData, Map<String, List<QuoteDropNode>> dropData) {
        Window parentWin = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentWin, "协同校验看板", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(1280, 760);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(MODERN_BG);

        // --- 1. 左侧：预警明细日志 ---
        JTextArea logArea = new JTextArea(String.join("\n\n", errors));
        logArea.setFont(BODY_FONT);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setMargin(new Insets(15, 15, 15, 15));
        logArea.setBackground(PANEL_BG);
        logArea.setForeground(TEXT_PRIMARY);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel logWrapper = new JPanel(new BorderLayout());
        logWrapper.setBackground(PANEL_BG);
        logWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JLabel logTitle = new JLabel(" 协同校验预警明细 (万元 / %)");
        logTitle.setFont(TITLE_FONT);
        logTitle.setForeground(new Color(255, 107, 107));
        logTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        logWrapper.add(logTitle, BorderLayout.NORTH);
        logWrapper.add(logScroll, BorderLayout.CENTER);

        // --- 2. 右侧上：高级折线图表 ---
        PriceTrendChartPanel chartPanel = new PriceTrendChartPanel(priceData);
        chartPanel.setBackground(PANEL_BG);

        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBackground(PANEL_BG);
        chartWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JLabel chartTitle = new JLabel(" 异常报价绝对走势图 (万元)");
        chartTitle.setFont(TITLE_FONT);
        chartTitle.setForeground(TEXT_SECONDARY);
        chartTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chartWrapper.add(chartTitle, BorderLayout.NORTH);
        chartWrapper.add(chartPanel, BorderLayout.CENTER);

        // --- 3. 右侧下：降幅明细 ---
        JTextArea detailArea = new JTextArea();
        detailArea.setFont(BODY_FONT);
        detailArea.setEditable(false);
        detailArea.setBackground(PANEL_BG);
        detailArea.setForeground(TEXT_PRIMARY);
        detailArea.setMargin(new Insets(15, 15, 15, 15));

        StringBuilder detailSb = new StringBuilder();
        for (Map.Entry<String, List<QuoteDropNode>> entry : dropData.entrySet()) {
            detailSb.append("  ").append(entry.getKey()).append("\n");
            for (QuoteDropNode node : entry.getValue()) {
                detailSb.append("    └─ ").append(node.phaseName).append(" : ")
                        .append(String.format("%.2f%%", node.dropPercent)).append("\n");
            }
            detailSb.append("\n");
        }
        detailArea.setText(detailSb.toString());
        detailArea.setCaretPosition(0);

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel detailWrapper = new JPanel(new BorderLayout());
        detailWrapper.setBackground(PANEL_BG);
        detailWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JLabel detailTitle = new JLabel(" 各轮降幅百分比穿透");
        detailTitle.setFont(TITLE_FONT);
        detailTitle.setForeground(TEXT_SECONDARY);
        detailTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        detailWrapper.add(detailTitle, BorderLayout.NORTH);
        detailWrapper.add(detailScroll, BorderLayout.CENTER);

        // --- 布局组合 ---
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartWrapper, detailWrapper);
        rightSplit.setOpaque(false);
        rightSplit.setResizeWeight(0.65);
        rightSplit.setDividerSize(8);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logWrapper, rightSplit);
        mainSplit.setOpaque(false);
        mainSplit.setDividerLocation(420);
        mainSplit.setDividerSize(8);
        mainSplit.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        dialog.add(mainSplit, BorderLayout.CENTER);

        // --- 底部：现代感导出按钮 ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 15));
        bottomPanel.setBackground(MODERN_BG);

        ActionButton exportBtn = new ActionButton("导出报告与截图", new Color(52, 152, 219));
        exportBtn.setPreferredSize(new Dimension(160, 36));
        exportBtn.addActionListener(e -> exportDashboard(dialog, errors, dropData));

        bottomPanel.add(exportBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // 看板导出功能代码
    private void exportDashboard(JDialog dialog, List<String> errors, Map<String, List<QuoteDropNode>> dropData) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择看板导出保存位置");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            try {
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

                File txtFile = new File(dir, "协同校验分析报告_" + timestamp + ".txt");
                try (PrintWriter writer = new PrintWriter(txtFile, "UTF-8")) {
                    writer.println("================ 数据逻辑协同预警明细 ================\n");
                    for (String err : errors) {
                        writer.println(err + "\n");
                    }
                    writer.println("\n================ 各轮降幅详细明细 ================\n");
                    for (Map.Entry<String, List<QuoteDropNode>> entry : dropData.entrySet()) {
                        writer.println("【" + entry.getKey() + "】");
                        for (QuoteDropNode node : entry.getValue()) {
                            writer.println("  " + node.phaseName + " : " + String.format("%.2f%%", node.dropPercent));
                        }
                        writer.println();
                    }
                }

                File imgFile = new File(dir, "综合看板截图_" + timestamp + ".png");
                BufferedImage img = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
                dialog.paint(img.getGraphics());
                ImageIO.write(img, "png", imgFile);

                JOptionPane.showMessageDialog(dialog, "看板数据与截图导出成功！\n保存路径：" + dir.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(dialog, "导出异常: " + ex.getMessage());
            }
        }
    }

    // --- 高级图表 UI 重构 ---
    class PriceTrendChartPanel extends JPanel {
        private Map<String, List<PriceNode>> data;
        private Color[] lineColors = {
                new Color(255, 107, 107), new Color(72, 219, 251), new Color(29, 209, 161),
                new Color(165, 94, 234), new Color(254, 202, 87), new Color(255, 159, 67)
        };

        private double scale = 1.0;
        private double translateX = 0.0;
        private double translateY = 0.0;
        private Point dragStart = null;
        private Map<Rectangle, String> tooltipMap = new HashMap<>();

        public PriceTrendChartPanel(Map<String, List<PriceNode>> data) {
            this.data = data;
            setOpaque(false);

            ToolTipManager.sharedInstance().registerComponent(this);
            setToolTipText("");

            addMouseWheelListener(e -> {
                double oldScale = scale;
                if (e.getWheelRotation() < 0) scale *= 1.15;
                else scale /= 1.15;
                scale = Math.max(0.3, Math.min(scale, 10.0));

                double f = scale / oldScale;
                translateX = e.getX() - f * (e.getX() - translateX);
                translateY = e.getY() - f * (e.getY() - translateY);
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        translateX += e.getX() - dragStart.getX();
                        translateY += e.getY() - dragStart.getY();
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            Point p = event.getPoint();
            for (Map.Entry<Rectangle, String> entry : tooltipMap.entrySet()) {
                if (entry.getKey().contains(p)) return entry.getValue();
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            tooltipMap.clear();
            if (data == null || data.isEmpty()) return;

            AffineTransform saveAT = g2d.getTransform();
            g2d.translate(translateX, translateY);
            g2d.scale(scale, scale);
            AffineTransform logicalAT = g2d.getTransform();

            int padding = 60;
            int width = Math.max(1, getWidth() - padding * 2);
            int height = Math.max(1, getHeight() - padding * 2);

            List<String> allRounds = new ArrayList<>();
            double maxPrice = 0.0, minPrice = Double.MAX_VALUE;
            for (List<PriceNode> nodes : data.values()) {
                for (PriceNode node : nodes) {
                    if (!allRounds.contains(node.roundName)) allRounds.add(node.roundName);
                    if (node.price > maxPrice) maxPrice = node.price;
                    if (node.price < minPrice) minPrice = node.price;
                }
            }
            if (minPrice == Double.MAX_VALUE) minPrice = 0;
            if (maxPrice == minPrice) {
                maxPrice += 10;
                minPrice = Math.max(0, minPrice - 10);
            } else {
                double diff = maxPrice - minPrice;
                maxPrice += diff * 0.15;
                minPrice = Math.max(0, minPrice - diff * 0.15);
            }

            int yTicks = 5;
            g2d.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
            FontMetrics fm = g2d.getFontMetrics();

            for (int i = 0; i <= yTicks; i++) {
                double tickValue = minPrice + (maxPrice - minPrice) * i / yTicks;
                int yPos = padding + height - (int) (height * i / yTicks);

                g2d.setColor(new Color(75, 80, 90));
                Stroke oldStroke = g2d.getStroke();
                g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2.0f}, 0.0f));
                g2d.drawLine(padding, yPos, padding + width, yPos);
                g2d.setStroke(oldStroke);

                g2d.setColor(TEXT_SECONDARY);
                String label = String.format("%.2f", tickValue);
                int labelWidth = fm.stringWidth(label);
                g2d.drawString(label, padding - labelWidth - 12, yPos + 4);
            }

            g2d.setColor(TEXT_PRIMARY);
            int roundCount = Math.max(1, allRounds.size());
            int xStep = width / roundCount;
            for (int i = 0; i < allRounds.size(); i++) {
                int x = padding + i * xStep + (xStep / 2);
                String rName = allRounds.get(i);
                int rWidth = fm.stringWidth(rName);
                g2d.drawString(rName, x - (rWidth / 2), padding + height + 25);
            }

            int colorIdx = 0;
            g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            for (Map.Entry<String, List<PriceNode>> entry : data.entrySet()) {
                Color c = lineColors[colorIdx % lineColors.length];
                List<PriceNode> nodes = entry.getValue();
                String uniqueKey = entry.getKey();

                Integer prevX = null, prevY = null;
                for (PriceNode node : nodes) {
                    int roundIdx = allRounds.indexOf(node.roundName);
                    if (roundIdx == -1) continue;

                    int x = padding + roundIdx * xStep + (xStep / 2);
                    int y = padding + height - (int) ((node.price - minPrice) / (maxPrice - minPrice) * height);

                    g2d.setColor(c);
                    if (prevX != null && prevY != null) {
                        g2d.drawLine(prevX, prevY, x, y);
                    }

                    g2d.setColor(PANEL_BG);
                    g2d.fillOval(x - 6, y - 6, 12, 12);
                    g2d.setColor(c);
                    g2d.drawOval(x - 6, y - 6, 12, 12);
                    g2d.fillOval(x - 3, y - 3, 6, 6);

                    Point2D.Double src = new Point2D.Double(x, y);
                    Point2D.Double dst = new Point2D.Double();
                    logicalAT.transform(src, dst);
                    Rectangle pointRect = new Rectangle((int) dst.x - 10, (int) dst.y - 10, 20, 20);
                    tooltipMap.put(pointRect, uniqueKey + " (" + node.roundName + ": " + node.price + " 万)");

                    prevX = x;
                    prevY = y;
                }
                colorIdx++;
            }

            g2d.setTransform(saveAT);

            int legendX = getWidth() - 160;
            int legendY = 30;
            colorIdx = 0;
            for (Map.Entry<String, List<PriceNode>> entry : data.entrySet()) {
                Color c = lineColors[colorIdx % lineColors.length];

                g2d.setColor(c);
                g2d.fillRoundRect(legendX, legendY, 14, 14, 4, 4);

                g2d.setColor(TEXT_PRIMARY);
                g2d.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
                String fullKey = entry.getKey();
                String shortName = fullKey.length() > 8 ? fullKey.substring(0, 8) + ".." : fullKey;
                g2d.drawString(shortName, legendX + 22, legendY + 12);

                Rectangle legRect = new Rectangle(legendX, legendY, 140, 15);
                tooltipMap.put(legRect, fullKey);

                legendY += 24;
                colorIdx++;
            }

            g2d.setColor(new Color(100, 105, 115));
            g2d.setFont(FontUtils.getChineseFont(Font.PLAIN, 11));
            g2d.drawString("提示: 图表支持鼠标拖拽平移与滚轮缩放细节", 15, getHeight() - 15);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
            return cell.getStringCellValue().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private Double getNumericValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA)
                return cell.getNumericCellValue();
            return Double.parseDouble(cell.getStringCellValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // 全新 SVG 矢量路径级重绘：毛玻璃胶囊精度控制器 + 矢量暗黑弹出菜单
    // =========================================================================
    class FuzzyModeIndicator extends JComponent {
        private boolean isHovered = false;
        private boolean isPopupVisible = false;

        private final String[] options = {"精确校对", "保留一位小数", "模糊至个位", "模糊至十位"};
        private final String[] badges = {"EXACT", "0.1", "1", "10"};
        private final Color[] accentColors = {
                new Color(52, 211, 153),  // 0: 精确校对 (翠绿星芒)
                new Color(56, 189, 248),  // 1: 保留一位小数 (天际冰蓝)
                new Color(251, 191, 36),  // 2: 模糊至个位 (琥珀暖金)
                new Color(248, 113, 113)  // 3: 模糊至十位 (珊瑚赤红)
        };

        public FuzzyModeIndicator() {
            // 与左侧 ActionButton (130x36) 保持高度一致，整体视觉极其协调
            setPreferredSize(new Dimension(168, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));


            // 构建自定义 SVG 风格暗黑毛玻璃弹出菜单
            JPopupMenu popupMenu = new JPopupMenu() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth();
                    int h = getHeight();

                    // 深空渐变背景
                    g2.setPaint(new LinearGradientPaint(
                            0, 0, 0, h,
                            new float[]{0f, 1f},
                            new Color[]{new Color(28, 33, 43, 250), new Color(18, 22, 30, 252)}
                    ));
                    g2.fillRoundRect(0, 0, w, h, 12, 12);

                    // 微妙的高光内边框
                    g2.setColor(new Color(255, 255, 255, 28));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

                    g2.dispose();
                }
            };
            popupMenu.setOpaque(false);
            popupMenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

            popupMenu.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    isPopupVisible = true;
                    repaint();
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    isPopupVisible = false;
                    repaint();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                    isPopupVisible = false;
                    repaint();
                }
            });

            for (int i = 0; i < options.length; i++) {
                final int levelIndex = i;
                JMenuItem item = createSvgMenuItem(levelIndex);
                item.addActionListener(e -> {
                    fuzzyLevel = levelIndex;
                    System.out.println("当前模糊比对级别切换为: " + options[levelIndex]);
                    repaint();
                });
                popupMenu.add(item);
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        // 在胶囊正下方右对齐弹出菜单
                        int menuWidth = popupMenu.getPreferredSize().width;
                        popupMenu.show(FuzzyModeIndicator.this, getWidth() - menuWidth, getHeight() + 6);
                    }
                }
            });
        }

        /**
         * 创建自定义 SVG 矢量渲染的下拉菜单项
         */
        private JMenuItem createSvgMenuItem(final int index) {
            JMenuItem item = new JMenuItem(options[index]) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

                    int w = getWidth();
                    int h = getHeight();
                    boolean armed = isArmed();
                    boolean selected = (fuzzyLevel == index);
                    Color accent = accentColors[index];

                    // 1. 悬浮或选中态圆角背景底板
                    if (armed) {
                        g2.setColor(new Color(255, 255, 255, 22));
                        g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                    } else if (selected) {
                        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 24));
                        g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                    }

                    // 2. 左侧绘制对应级别的 SVG 矢量图标
                    paintSvgPrecisionIcon(g2, 18, h / 2.0, index, accent, selected || armed);

                    // 3. 绘制主标题文字
                    g2.setFont(FontUtils.getChineseFont(selected ? Font.BOLD : Font.PLAIN, 13));
                    g2.setColor(selected ? Color.WHITE : (armed ? new Color(240, 244, 250) : new Color(195, 205, 218)));
                    FontMetrics fm = g2.getFontMetrics();
                    int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(options[index], 36, textY);

                    // 4. 右侧绘制精度数值胶囊 Badge (EXACT / 0.1 / 1 / 10)
                    g2.setFont(FontUtils.getEnglishFont(Font.BOLD, 10));
                    FontMetrics bfm = g2.getFontMetrics();
                    String badgeText = badges[index];
                    int bw = bfm.stringWidth(badgeText) + 10;
                    int bh = 16;
                    int bx = w - bw - (selected ? 26 : 10);
                    int by = (h - bh) / 2;

                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), selected ? 50 : 28));
                    g2.fillRoundRect(bx, by, bw, bh, 6, 6);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), selected ? 180 : 100));
                    g2.setStroke(new BasicStroke(0.8f));
                    g2.drawRoundRect(bx, by, bw, bh, 6, 6);

                    g2.setColor(accent);
                    g2.drawString(badgeText, bx + 5, by + (bh - bfm.getHeight()) / 2 + bfm.getAscent());

                    // 5. 当前激活项右侧绘制 SVG 矢量对勾 (Checkmark)
                    if (selected) {
                        Path2D.Double check = new Path2D.Double();
                        double cx = w - 15;
                        double cy = h / 2.0;
                        check.moveTo(cx - 4.5, cy + 0.2);
                        check.lineTo(cx - 1.2, cy + 3.5);
                        check.lineTo(cx + 4.8, cy - 3.2);
                        g2.setColor(accent);
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(check);
                    }

                    g2.dispose();
                }
            };
            item.setPreferredSize(new Dimension(208, 34));
            item.setOpaque(false);
            item.setBorder(BorderFactory.createEmptyBorder());
            item.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return item;
        }

        /**
         * 核心 SVG 矢量图标绘制器（基于 Path2D 矢量路径绘制 4 种不同精度的几何图标）
         */
        private void paintSvgPrecisionIcon(Graphics2D g2, double cx, double cy, int level, Color color, boolean activeGlow) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // 柔和的底部 SVG 径向光晕
            if (activeGlow) {
                RadialGradientPaint glow = new RadialGradientPaint(
                        new Point2D.Double(cx, cy), 12f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(color.getRed(), color.getGreen(), color.getBlue(), 85),
                                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)}
                );
                g.setPaint(glow);
                g.fill(new Ellipse2D.Double(cx - 12, cy - 12, 24, 24));
            }

            g.setColor(color);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (level) {
                case 0: {
                    // SVG 图标 0 (精确校对): 精密光学准星 + 中心菱形星芒 (Crosshair & Diamond Star)
                    g.draw(new Ellipse2D.Double(cx - 6.5, cy - 6.5, 13, 13));

                    Path2D.Double ticks = new Path2D.Double();
                    // 上下左右四个精密准星刻度 (M / L 指令)
                    ticks.moveTo(cx, cy - 9.0); ticks.lineTo(cx, cy - 5.2);
                    ticks.moveTo(cx, cy + 5.2); ticks.lineTo(cx, cy + 9.0);
                    ticks.moveTo(cx - 9.0, cy); ticks.lineTo(cx - 5.2, cy);
                    ticks.moveTo(cx + 5.2, cy); ticks.lineTo(cx + 9.0, cy);
                    g.draw(ticks);

                    // 中心四芒星矢量路径 (三次贝塞尔曲线 C 指令)
                    Path2D.Double star = new Path2D.Double();
                    double r = 3.2;
                    star.moveTo(cx, cy - r);
                    star.curveTo(cx, cy - 0.6, cx + 0.6, cy, cx + r, cy);
                    star.curveTo(cx + 0.6, cy, cx, cy + 0.6, cx, cy + r);
                    star.curveTo(cx, cy + 0.6, cx - 0.6, cy, cx - r, cy);
                    star.curveTo(cx - 0.6, cy, cx, cy - 0.6, cx, cy - r);
                    star.closePath();
                    g.fill(star);
                    break;
                }
                case 1: {
                    // SVG 图标 1 (保留一位小数): 双轨精密微分滑块 ( SVG Sliders Icon )
                    Path2D.Double tracks = new Path2D.Double();
                    // 上轨左右分段（避开空心圆环）
                    tracks.moveTo(cx - 7.5, cy - 3.5); tracks.lineTo(cx - 4.2, cy - 3.5);
                    tracks.moveTo(cx + 0.8, cy - 3.5); tracks.lineTo(cx + 7.5, cy - 3.5);
                    // 下轨左右分段
                    tracks.moveTo(cx - 7.5, cy + 3.5); tracks.lineTo(cx - 0.8, cy + 3.5);
                    tracks.moveTo(cx + 4.2, cy + 3.5); tracks.lineTo(cx + 7.5, cy + 3.5);
                    g.draw(tracks);

                    // 两个矢量空心调节旋钮
                    g.draw(new Ellipse2D.Double(cx - 4.2, cy - 6.0, 5.0, 5.0));
                    g.draw(new Ellipse2D.Double(cx - 0.8, cy + 1.0, 5.0, 5.0));
                    break;
                }
                case 2: {
                    // SVG 图标 2 (模糊至个位): 矢量等高量化阶梯矩阵 (Quantized Step Layers)
                    Path2D.Double layers = new Path2D.Double();
                    // 顶层菱形面
                    layers.moveTo(cx, cy - 7.0);
                    layers.lineTo(cx + 7.5, cy - 3.0);
                    layers.lineTo(cx, cy + 1.0);
                    layers.lineTo(cx - 7.5, cy - 3.0);
                    layers.closePath();
                    // 中层折线
                    layers.moveTo(cx - 7.5, cy + 1.2);
                    layers.lineTo(cx, cy + 5.0);
                    layers.lineTo(cx + 7.5, cy + 1.2);
                    // 底层折线
                    layers.moveTo(cx - 5.5, cy + 4.8);
                    layers.lineTo(cx, cy + 7.6);
                    layers.lineTo(cx + 5.5, cy + 4.8);
                    g.draw(layers);
                    break;
                }
                default: {
                    // SVG 图标 3 (模糊至十位): 广域容差声呐波纹 (Broad Tolerance Radar Waves)
                    g.fill(new Ellipse2D.Double(cx - 2.0, cy - 2.0, 4.0, 4.0));
                    g.draw(new Arc2D.Double(cx - 5.2, cy - 5.2, 10.4, 10.4, 35, 110, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(cx - 5.2, cy - 5.2, 10.4, 10.4, 215, 110, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(cx - 8.2, cy - 8.2, 16.4, 16.4, 45, 90, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(cx - 8.2, cy - 8.2, 16.4, 16.4, 225, 90, Arc2D.OPEN));
                    break;
                }
            }

            g.dispose();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            Color accent = accentColors[fuzzyLevel];
            boolean active = isHovered || isPopupVisible;

            // 1. 绘制胶囊毛玻璃主体底板 (深空渐变)
            RoundRectangle2D.Double pill = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 18, 18);
            g2.setPaint(new LinearGradientPaint(
                    0, 0, 0, h,
                    new float[]{0f, 1f},
                    new Color[]{
                            active ? new Color(46, 53, 68, 235) : new Color(32, 37, 48, 210),
                            active ? new Color(30, 35, 46, 245) : new Color(22, 26, 34, 225)
                    }
            ));
            g2.fill(pill);

            // 2. 左侧状态色环境微光映射
            RadialGradientPaint ambientGlow = new RadialGradientPaint(
                    new Point2D.Double(22, h / 2.0), active ? 48f : 32f,
                    new float[]{0f, 1f},
                    new Color[]{
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), active ? 55 : 32),
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                    }
            );
            g2.setPaint(ambientGlow);
            g2.fill(pill);

            // 3. 胶囊高光矢量边框 (悬停或展开时切换为霓虹主题色渐变边框)
            if (active) {
                g2.setPaint(new LinearGradientPaint(
                        0, 0, w, h,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{
                                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210),
                                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 95),
                                new Color(255, 255, 255, 60)
                        }
                ));
                g2.setStroke(new BasicStroke(1.3f));
            } else {
                g2.setColor(new Color(255, 255, 255, 38));
                g2.setStroke(new BasicStroke(1.0f));
            }
            g2.draw(pill);

            // 4. 绘制左侧 SVG 矢量精度图标
            paintSvgPrecisionIcon(g2, 21, h / 2.0, fuzzyLevel, accent, active);

            // 5. 绘制分割微线
            g2.setColor(new Color(255, 255, 255, 22));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(37, 9, 37, h - 9);

            // 6. 绘制当前模式文字与精度徽章
            g2.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
            g2.setColor(active ? Color.WHITE : new Color(230, 236, 245));
            FontMetrics fm = g2.getFontMetrics();
            String label = options[fuzzyLevel];
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, 45, textY);

            // 7. 右侧绘制 SVG 矢量下拉箭头 (Chevron Arrow，菜单展开时平滑翻转)
            double arrowCx = w - 16.5;
            double arrowCy = h / 2.0;
            Path2D.Double chevron = new Path2D.Double();
            if (isPopupVisible) {
                chevron.moveTo(arrowCx - 4.0, arrowCy + 2.0);
                chevron.lineTo(arrowCx, arrowCy - 2.2);
                chevron.lineTo(arrowCx + 4.0, arrowCy + 2.0);
            } else {
                chevron.moveTo(arrowCx - 4.0, arrowCy - 1.8);
                chevron.lineTo(arrowCx, arrowCy + 2.4);
                chevron.lineTo(arrowCx + 4.0, arrowCy - 1.8);
            }
            g2.setColor(active ? accent : new Color(165, 175, 190));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(chevron);

            g2.dispose();
        }
    }
}