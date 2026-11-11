package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import javazoom.jl.player.Player;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassDropPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 评分生成助手 ScoringPanel (一体式极简自动导出版 + 玉珍单曲循环 + 继承BackgroundPanel) */
public class ScoringPanel extends BackgroundPanel {

    private JLabel dl;
    private List<TableDataHolder> extractedTables = new ArrayList<>();
    private volatile boolean isParsing = false; // 解析状态锁
    // 音乐播放与点阵微动效控制参数
    private boolean isMusicPlaying = false;
    private Thread musicThread;
    private Player player;
    private javax.swing.Timer musicAnimTimer;
    private int animTick = 0;

    // =========================================================================
    // 🎨 悬崖与灯塔下，男孩女孩坐在长椅上看月亮，脚下是一片大海 —— 56x27 像素点阵矩阵
    // 图例说明：
    // '.' 透明夜空   '*' 璀璨恒星   '+' 暖金微星
    // 'M' 皓月核心   'm' 月轮边缘   'Q' 月面环形山暗影
    // 'R' 灯塔穹顶   'L' 灯塔透镜   'l' 灯塔漫射光束   'D' 灯塔暖光窗/门
    // 'H' 灯塔白石   'N' 白石暗部   'h' 灯塔红环       'n' 红环暗部
    // 'S' 男孩黑发   'F' 女孩长发   'K' 仰望月光的脸庞
    // 'J' 男孩蓝衣   'V' 女孩粉裙   'B' 木质长椅板     'b' 长椅扶手与椅腿
    // 'G' 崖顶嫩草   'g' 崖顶深草   'P' 崖畔小花
    // 'C' 悬崖亮岩   'c' 悬崖暗岩   'X' 悬崖深层岩影
    // 'w' 拍岸浪花   '~' 静谧夜海   'O' 海面波峰
    // '=' 护栏与海面月光核心倒影    'Y' 海面月光泛金波纹
    // =========================================================================
    private static final String[] MOON_MATRIX = {
            "....+.............*.................................*...",
            "........R................................mmmmmm.........",
            ".......RRR...............+.............mmMMMMMMmm.......",
            "......RRRRR...........................mMMQQMMMMMMm......",
            "......LLLLL.llllll...................mMMQQQMMMMMMMm.....",
            "......LLLLL.lllllllllll..............mMMMMMMMMQQMMm.....",
            ".....=======..llllllllllllll.........mMMMMMMMQQQMMm..*..",
            "......HHHHN......llllllllllll........mMMMQQMMMMMMMm.....",
            "......hhhhn.........lllllllll.........mMMMMMMMMMMm......",
            "......HHDHN............llllll..........mmMMMMMMmm.......",
            "......HHHHN..............................mmmmmm.........",
            ".....hhhhhhn......SSS..FFF..................+...........",
            ".....HHHHHHN......SKK.FFKK..............................",
            ".....HHDDHHN....BBJJJBFVVVBB....*.......................",
            "....HHHHHHHHN...bBJJJJVVVVBb............................",
            "...GHHHDDHHHNGGGBBBBBBBBBBBBGG..........................",
            "..GGGGGGGGGGGGPGgb.b.JJ.VVb.bgGGGG......................",
            ".GGggggggggggggggggggggggggggggGGG~~~~~O~~~~YYYY~~~~O~~~",
            ".CCCCCCCcCCCCCccccccccccccCCCCCCC~~~~~~~YYY======YYY~~~~",
            "..CCCCcccccccccXXXXXXXXccccccCCC~~~O~~~~~YY======YY~~~O~",
            "....CCCcccccXXXXXXXXXXXXXXcccCC~~~~~~~~~YYY======YYY~~~~",
            "~~w~~wwww~~~wwwwwwwwwww~~~wwww~~~~O~~~~~~YY======YY~~~~~",
            "~~~~~~~O~~~~~~~~~~~O~~~~~~~~~~~~~~~O~~~~YYY======YYY~~O~",
            "~~~O~~~~~~~~~~~~~~~~~~~~~~~~~O~~~~~~~~~~~YY======YY~~~~~",
            "~~~~~~~~~~~~O~~~~~~~~~~O~~~~~~~~~~~~O~~~YYY======YYY~~~~",
            "~~~~~~O~~~~~~~~~~~~~~~~~~~~~~~~~O~~~~~~~~~YY====YY~~O~~~",
            "~~~~~~~~~~~~~~~~~O~~~~~~~~~~~~~~~~~~~~~~~~~YYYYYY~~~~~~~"
    };

    /**
     * 点阵调色盘映射（支持音乐播放时的星辰闪烁、灯塔光束律动与海面月光波浪起伏）
     */
    private Color getDotColor(char ch, int row, int col) {
        boolean shimmer = isMusicPlaying && ((row + col + animTick) % 3 == 0);
        boolean waveShift = isMusicPlaying && ((col + animTick) % 4 == 0);
        switch (ch) {
            case '*': // 璀璨恒星
                return shimmer ? new Color(255, 255, 255) : new Color(210, 235, 255, 200);
            case '+': // 暖金微星
                return shimmer ? new Color(255, 248, 190) : new Color(254, 202, 87, 180);
            case 'M': // 皓月核心高光
                return new Color(255, 253, 232);
            case 'm': // 月轮温暖外缘
                return shimmer ? new Color(254, 243, 160) : new Color(253, 224, 71, 235);
            case 'Q': // 月面环形山柔和暗影
                return new Color(243, 215, 138);
            case 'R': // 灯塔穹顶
                return new Color(220, 38, 38);
            case 'L': // 灯塔探照灯发光源
                return shimmer ? new Color(255, 255, 255) : new Color(254, 240, 138);
            case 'l': // 灯塔探照光束
                return isMusicPlaying && ((col - animTick * 2) % 4 == 0)
                        ? new Color(254, 240, 138, 165)
                        : new Color(254, 240, 138, 85);
            case '=': // 观景护栏与海面月光核心倒影
                return shimmer ? new Color(255, 251, 215, 245) : new Color(254, 240, 138, 195);
            case 'Y': // 海面月光外围泛金波光
                return waveShift ? new Color(253, 224, 71, 160) : new Color(234, 179, 8, 105);
            case 'H': // 灯塔主塔身白石 (向光面)
                return new Color(241, 245, 249);
            case 'N': // 灯塔主塔身白石 (背光面)
                return new Color(203, 213, 225);
            case 'h': // 灯塔红色环带 (向光面)
                return new Color(239, 68, 68);
            case 'n': // 灯塔红色环带 (背光面)
                return new Color(185, 28, 28);
            case 'D': // 灯塔暖光小窗与门
                return shimmer ? new Color(254, 240, 138) : new Color(251, 191, 36);
            case 'S': // 男孩深色短发
                return new Color(30, 41, 59);
            case 'F': // 女孩栗色长发
                return new Color(146, 64, 14);
            case 'K': // 两人仰望月亮的侧面肤色
                return new Color(254, 215, 170);
            case 'J': // 男孩 (Johnny) 蓝色外套
                return new Color(59, 130, 246);
            case 'V': // 女孩 (River) 粉红衣裙
                return new Color(244, 114, 182);
            case 'B': // 崖畔木质长椅板
                return new Color(217, 119, 6);
            case 'b': // 长椅深木扶手与椅腿
                return new Color(120, 53, 15);
            case 'G': // 悬崖表层青草高光
                return new Color(74, 222, 128);
            case 'g': // 悬崖深层草坪
                return new Color(22, 163, 74);
            case 'P': // 悬崖野花
                return new Color(253, 224, 71);
            case 'C': // 悬崖受光岩面
                return new Color(100, 116, 139);
            case 'c': // 悬崖中层暗岩
                return new Color(51, 65, 85);
            case 'X': // 悬崖底部深层岩影
                return new Color(30, 41, 59);
            case 'w': // 悬崖底部拍岸白浪花
                return waveShift ? new Color(224, 242, 254, 230) : new Color(186, 230, 253, 165);
            case 'O': // 大海波峰浪纹
                return shimmer ? new Color(56, 189, 248, 220) : new Color(14, 165, 233, 175);
            case '~': // 静谧深海夜浪
                return shimmer ? new Color(14, 116, 144, 235) : new Color(12, 74, 110, 210);
            default:
                return null;
        }
    }

    public ScoringPanel(MainLauncher parent) {
        // 核心修复：直接调用父类构造统一背景图，彻底抛弃冗余的图片读取代码
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout());

        // 顶部返回按钮区 (关联停止音乐机制)
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic(); // 返回主菜单时自动销毁音乐线程
            parent.showMenu();
        });

        // 取消 FlowLayout 默认的 5px 间隙，实现精确像素对齐
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);
        leftTop.add(backBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        // 修改：右上角跳转图标 (圆绵羊版)
        SheepIndicator sheepLink = new SheepIndicator();
        sheepLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://tools.pdf24.org/zh/"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(ScoringPanel.this, "无法打开浏览器，请检查网络设置。");
                }
            }
        });
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.setOpaque(false);
        rightTop.add(sheepLink);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // 一体式超大拖拽区域 (毛玻璃拟态 UI)
        GlassDropPanel dropArea = new GlassDropPanel();
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 带事件穿透 + 代码点阵绘制的中央标签组件 (仿照 ConverterPanel)
        dl = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                // 核心魔法：让系统认为文字是透明的空气，拖拽事件和鼠标点击将100%穿透到底层的黑洞面板上！
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintToTheMoonDotMatrix(g);
            }
        };
        dropArea.add(dl, BorderLayout.CENTER);

        // 神来之笔：让黑洞本身变成音乐播放/暂停的隐藏按钮
        dropArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleMusic(); // 点击任意区域触发音乐
            }
        });

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(40, 80, 80, 80));
        centerWrapper.add(dropArea, BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);

        this.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (dl != null) dl.repaint();
            }
        });

        // 利用新的 GlassDropPanel 机制实现流光与事件拦截
        new DropTarget(dropArea, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                SwingUtilities.invokeLater(() -> dropArea.setDragActive(true));
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // 保持空实现，避免高频调用阻塞 OLE 队列
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                SwingUtilities.invokeLater(() -> dropArea.setDragActive(false));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                // 接收文件瞬间异步恢复透明面板静默状态
                SwingUtilities.invokeLater(() -> dropArea.setDragActive(false));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    // 立刻释放系统底层拖拽锁，闭合生命周期
                    dtde.dropComplete(true);

                    if (isParsing) return;
                    if (!files.isEmpty()) {
                        File targetFile = files.get(0);
                        isParsing = true;
                        if (dl != null) dl.repaint();
                        Thread.ofVirtual().name("Scoring-DocxParser").start(() -> {
                            try {
                                parseDocx(targetFile);
                            } finally {
                                isParsing = false;
                                if (dl != null) SwingUtilities.invokeLater(() -> dl.repaint());
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

    /**
     * 核心点阵渲染器：绘制月夜悬崖灯塔、长椅上的男孩女孩与脚下大海点阵图卷
     */
    private void paintToTheMoonDotMatrix(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = dl.getWidth();
        int h = dl.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = MOON_MATRIX.length;
        int cols = MOON_MATRIX[0].length();

        // 根据当前面板宽高自适应计算点阵颗粒尺寸（严格参照 ConverterPanel 布局算法）
        int cellSize = Math.max(3, Math.min(w / (cols + 6), (h - 28) / (rows + 2)));
        int gap = cellSize >= 5 ? 1 : 0; // 点阵颗粒间隙，营造经典像素点阵屏质感
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH - (cellSize * 2)) / 2;

        // 满月与灯塔的柔和背光光晕
        int moonCenterX = startX + 44 * cellSize;
        int moonCenterY = startY + 5 * cellSize + cellSize / 2;
        int moonGlowRadius = cellSize * (isMusicPlaying && (animTick % 2 == 0) ? 9 : 8);
        g2.setColor(new Color(254, 240, 138, isMusicPlaying ? 32 : 18));
        g2.fillOval(moonCenterX - moonGlowRadius, moonCenterY - moonGlowRadius, moonGlowRadius * 2, moonGlowRadius * 2);

        int lightCenterX = startX + 8 * cellSize + cellSize / 2;
        int lightCenterY = startY + 5 * cellSize;
        int lightGlowRadius = cellSize * (isMusicPlaying && ((animTick / 2) % 2 == 0) ? 5 : 4);
        g2.setColor(new Color(254, 240, 138, isMusicPlaying ? 42 : 20));
        g2.fillOval(lightCenterX - lightGlowRadius, lightCenterY - lightGlowRadius, lightGlowRadius * 2, lightGlowRadius * 2);

        // 逐行逐列扫描绘制点阵
        for (int r = 0; r < rows; r++) {
            String line = MOON_MATRIX[r];
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
        if (isParsing) {
            g2.setColor(new Color(72, 219, 251, 230));
            String caption = "P A R S I N G   D O C X . . .";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, cellSize * 3);
            g2.drawString(caption, textX, textY);
        } else {
            g2.setColor(new Color(226, 232, 240, isMusicPlaying ? 210 : 140));
            String caption = "T O   T H E   M O O N";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, cellSize * 3);
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }

    // 音乐控制区：单曲循环引擎 (仿照 ConverterPanel 联动微动效)
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
        musicThread = Thread.ofVirtual().name("Scoring-MusicPlayer").start(() -> {
            while (isMusicPlaying) {
                try {
                    InputStream is = getClass().getResourceAsStream("/music/To The Moon.mp3");
                    if (is == null) {
                        System.err.println("找不到 /music/To The Moon.mp3，请检查 resources 目录");
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
                }
            }
        });
    }

    private void startMusicAnimTimer() {
        if (musicAnimTimer == null) {
            musicAnimTimer = new javax.swing.Timer(400, e -> {
                if (isMusicPlaying && !isParsing) {
                    animTick++;
                    if (dl != null) dl.repaint();
                }
            });
            musicAnimTimer.setCoalesce(true);
        }
        if (!musicAnimTimer.isRunning()) {
            musicAnimTimer.start();
        }
        if (dl != null) dl.repaint();
    }

    private void stopMusic() {
        isMusicPlaying = false;
        if (musicAnimTimer != null && musicAnimTimer.isRunning()) {
            musicAnimTimer.stop();
        }
        if (player != null) {
            player.close();
        }
        if (musicThread != null) {
            musicThread.interrupt();
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

    private void parseDocx(File file) {
        extractedTables.clear();
        try (FileInputStream fis = new FileInputStream(file); XWPFDocument doc = new XWPFDocument(fis)) {
            List<IBodyElement> elements = doc.getBodyElements();

            String currentMainTitle = "";
            String currentSubTitle = "";
            boolean isCapturing = false;

            for (int i = 0; i < elements.size(); i++) {
                IBodyElement el = elements.get(i);

                if (el instanceof XWPFParagraph) {
                    String text = ((XWPFParagraph) el).getText().trim();
                    if (text.isEmpty()) continue;

                    boolean isTargetHeader = (text.contains("技术评分标准") || text.contains("商务评分标准")
                            || text.contains("技术评审细则") || text.contains("商务评审细则"))
                            && (text.contains("%") || text.contains("权重"));

                    if (isTargetHeader) {
                        currentMainTitle = text;
                        currentSubTitle = "";
                        isCapturing = true;
                        continue;
                    }

                    if (isCapturing) {
                        if ((text.startsWith("第") && text.contains("章")) || text.contains("应答文件格式")) {
                            isCapturing = false;
                        } else {
                            if (text.length() <= 30) {
                                currentSubTitle = text.replaceAll("[:：]$", "");
                            } else {
                                currentSubTitle = "补充说明";
                            }
                        }
                    }
                }

                if (isCapturing && el instanceof XWPFTable) {
                    XWPFTable table = (XWPFTable) el;
                    if (table.getRows().size() > 0) {
                        String finalTitle = currentMainTitle;
                        if (!currentSubTitle.isEmpty()) {
                            finalTitle = finalTitle + " - " + currentSubTitle;
                        }
                        processTable(table, finalTitle);
                    }
                }
            }

            if (!extractedTables.isEmpty()) {
                autoExportToExcel();
            } else {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "很抱歉，我读取不到。",
                                "该回去啦",
                                JOptionPane.WARNING_MESSAGE)
                );
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "解析过程出错，文件是否正在被其他程序占用？")
            );
        }
    }

    private void processTable(XWPFTable table, String title) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows == null || rows.isEmpty()) return;

        int startDataIdx = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getTableCells().size() > 1) {
                startDataIdx = i;
                break;
            }
        }

        if (startDataIdx >= rows.size()) return;

        int originalColCount = rows.get(startDataIdx).getTableCells().size();
        int skipCols = 3;
        if (originalColCount <= skipCols) skipCols = 0;
        int newColCount = originalColCount - skipCols;

        List<String[]> data = new ArrayList<>();
        String[] lastRowMemory = new String[originalColCount];
        Arrays.fill(lastRowMemory, "");

        for (int r = startDataIdx; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> cells = row.getTableCells();
            String[] rowData = new String[newColCount];
            boolean hasContent = false;

            for (int j = 0; j < originalColCount; j++) {
                String currentVal = "";
                if (j < cells.size()) {
                    currentVal = cells.get(j).getText().trim().replaceAll("[\\n\\r\\t]+", " ");
                }

                if (currentVal.isEmpty()) {
                    currentVal = lastRowMemory[j];
                } else {
                    lastRowMemory[j] = currentVal;
                }

                if (j >= skipCols) {
                    rowData[j - skipCols] = currentVal;
                    if (!currentVal.isEmpty()) hasContent = true;
                }
            }
            if (hasContent) data.add(rowData);
        }

        if (!data.isEmpty()) {
            TableDataHolder existingHolder = null;
            for (TableDataHolder holder : extractedTables) {
                if (holder.title.equals(title)) {
                    existingHolder = holder;
                    break;
                }
            }

            if (existingHolder != null) {
                if (existingHolder.data.get(0).length == newColCount) {
                    existingHolder.data.addAll(data);
                } else {
                    extractedTables.add(new TableDataHolder(title + "_续表", data));
                }
            } else {
                TableDataHolder holder = new TableDataHolder(title, data);
                extractedTables.add(holder);
            }
        }
    }

    private void autoExportToExcel() {
        try {
            // 核心修改 1：获取所有可能存在的桌面路径 (兼容本地与 OneDrive)
            List<File> targetDesktops = new ArrayList<>();
            String home = System.getProperty("user.home");

            File stdDesktop = new File(home, "Desktop");
            File odDesktopEn = new File(home, "OneDrive" + File.separator + "Desktop");
            File odDesktopCn = new File(home, "OneDrive" + File.separator + "桌面"); // Windows 某些版本的中文实体路径

            // 只要物理文件夹真实存在，就加入输出队列
            if (stdDesktop.exists()) targetDesktops.add(stdDesktop);
            if (odDesktopEn.exists() && !odDesktopEn.equals(stdDesktop)) targetDesktops.add(odDesktopEn);
            if (odDesktopCn.exists() && !odDesktopCn.equals(stdDesktop)) targetDesktops.add(odDesktopCn);

            // 兜底防错：如果全都不存在，直接输出到 C盘用户根目录
            if (targetDesktops.isEmpty()) targetDesktops.add(new File(home));

            int techCount = 0;
            int bizCount = 0;
            String[] cnNumbers = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

            for (TableDataHolder holder : extractedTables) {
                boolean isTech = holder.title.contains("技术");
                boolean isBiz = holder.title.contains("商务");

                String baseType = isTech ? "技术评分标准" : (isBiz ? "商务评分标准" : "其他评分标准");
                int currentIdx = isTech ? (++techCount) : (isBiz ? (++bizCount) : 1);

                String cnIndex = currentIdx < cnNumbers.length ? cnNumbers[currentIdx] : String.valueOf(currentIdx);
                String fileNameBase = baseType + "第" + cnIndex + "份";

                XSSFWorkbook wb = new XSSFWorkbook();
                XSSFCellStyle style = createBaseStyle(wb);
                XSSFSheet sheet = wb.createSheet("Sheet1");
                List<String[]> data = holder.data;

                for (int r = 0; r < data.size(); r++) {
                    XSSFRow row = sheet.createRow(r);
                    for (int c = 0; c < data.get(r).length; c++) {
                        XSSFCell cell = row.createCell(c);
                        cell.setCellValue(data.get(r)[c]);
                        cell.setCellStyle(style);
                    }
                }
                for (int c = 0; c < data.get(0).length; c++) physicalMerge(sheet, data, c);
                for (int i = 0; i < data.get(0).length; i++) sheet.setColumnWidth(i, 256 * 40);

                // 核心修改 2：遍历收集到的有效桌面路径，实现“两端同时导出”
                for (File dir : targetDesktops) {
                    File targetFile = getUniqueFile(dir, fileNameBase, "xlsx");
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        wb.write(fos);
                    }
                }
                wb.close(); // 写入完成后释放内存
            }

            // 动态更新弹窗提示语
            String syncMsg = targetDesktops.size() > 1 ? " (包含 OneDrive 同步目录)" : "";
            final String finalMessage = "读取成功\n" +
                    "共生成 " + techCount + " 份技术评分标准，" + bizCount + " 份商务评分标准。\n" +
                    "文件已成功送达您的桌面" + syncMsg + "。";

            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                            finalMessage,
                            "起风了",
                            JOptionPane.INFORMATION_MESSAGE)
            );

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Excel 自动生成过程出现异常！", "错误", JOptionPane.ERROR_MESSAGE)
            );
        }
    }

    private File getUniqueFile(File dir, String baseName, String ext) {
        File file = new File(dir, baseName + "." + ext);
        if (!file.exists()) return file;
        int count = 1;
        while (true) {
            file = new File(dir, baseName + "_副本" + count + "." + ext);
            if (!file.exists()) return file;
            count++;
        }
    }

    private void physicalMerge(XSSFSheet sheet, List<String[]> data, int colIdx) {
        int startRow = 0;
        for (int r = 1; r <= data.size(); r++) {
            String current = (r < data.size()) ? data.get(r)[colIdx] : "EOF";
            String previous = data.get(startRow)[colIdx];
            if (!current.equals(previous)) {
                if (r - 1 > startRow && !previous.isEmpty()) {
                    sheet.addMergedRegion(new CellRangeAddress(startRow, r - 1, colIdx, colIdx));
                }
                startRow = r;
            }
        }
    }

    private XSSFCellStyle createBaseStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    static class TableDataHolder {
        String title;
        List<String[]> data;

        TableDataHolder(String t, List<String[]> d) {
            this.title = t;
            this.data = d;
        }
    }

    /**
     *  原生自绘的“圆绵羊”图标指示器组件 (对标 50x50 标准)
     */
    static class SheepIndicator extends JComponent {

        private final double SCALE = 0.8;
        private final int OFFSET_X = 10;
        private final int OFFSET_Y = 4;

        public SheepIndicator() {
            setPreferredSize(new Dimension(65, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(12 + OFFSET_X, OFFSET_Y);
            g2.scale(SCALE, SCALE);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 耳朵
            g2.setColor(new Color(120, 120, 120));
            g2.fillOval(4, 22, 8, 10);
            g2.fillOval(38, 22, 8, 10);
            g2.setColor(Color.BLACK);
            g2.drawOval(4, 22, 8, 10);
            g2.drawOval(38, 22, 8, 10);

            // 羊毛
            Area wool = new Area();
            wool.add(new Area(new Ellipse2D.Float(9, 7, 14, 14)));
            wool.add(new Area(new Ellipse2D.Float(18, 4, 15, 15)));
            wool.add(new Area(new Ellipse2D.Float(28, 7, 14, 14)));
            wool.add(new Area(new Ellipse2D.Float(7, 15, 16, 16)));
            wool.add(new Area(new Ellipse2D.Float(17, 12, 18, 18)));
            wool.add(new Area(new Ellipse2D.Float(27, 15, 16, 16)));
            g2.setColor(Color.WHITE);
            g2.fill(wool);
            g2.setColor(Color.BLACK);
            g2.draw(wool);

            // 鼻口
            Area snout = new Area(new Ellipse2D.Float(10, 21, 30, 19));
            g2.setColor(new Color(244, 205, 160));
            g2.fill(snout);
            g2.setColor(Color.BLACK);
            g2.draw(snout);

            // 左眼
            g2.setColor(Color.WHITE);
            g2.fillOval(12, 12, 15, 15);
            g2.setColor(Color.BLACK);
            g2.drawOval(12, 12, 15, 15);
            g2.fillOval(19, 18, 3, 3);

            // 右眼
            g2.setColor(Color.WHITE);
            g2.fillOval(23, 8, 20, 20);
            g2.setColor(Color.BLACK);
            g2.drawOval(23, 8, 20, 20);
            g2.fillOval(32, 17, 3, 3);

            // 嘴巴
            g2.drawArc(14, 28, 10, 8, 150, 130);
            g2.drawArc(18, 27, 4, 4, 20, 90);

            // 鼻孔
            g2.fillOval(20, 27, 2, 3);
            g2.fillOval(27, 26, 2, 3);

            // 眉毛
            g2.drawLine(14, 10, 18, 11);
            g2.drawLine(34, 6, 38, 8);
            g2.dispose();
        }
    }
}