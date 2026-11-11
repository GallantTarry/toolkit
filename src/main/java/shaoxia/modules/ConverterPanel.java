package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassDropPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * 原生文件转换助手 ConverterPanel (牧羊人溪畔歇息点阵画卷 + 音乐播放 + 多文件总进度批量转换)
 */
public class ConverterPanel extends BackgroundPanel {

    // 本地持久化配置：实现“一次搜索、终身复用”
    private static final Preferences PREFS = Preferences.userNodeForPackage(ConverterPanel.class);
    private static final String PREF_KEY_OFFICE_CHECKED = "office_engine_checked";
    private static final String PREF_KEY_OFFICE_READY = "office_engine_ready";

    private JLabel dl;
    private GlassDropPanel dropArea;
    private OfficeEngineIndicator engineIndicator;

    private volatile boolean isEngineReady = false;
    private volatile boolean isCheckingEngine = false;
    private volatile boolean isConverting = false;

    private volatile int currentProgress = 0;
    private volatile int targetProgressCeiling = 0;
    private Timer progressTimer;

    // 音乐播放与点阵微动效控制
    private volatile boolean isPlaying = false;
    private javazoom.jl.player.Player player;
    private Timer musicAnimTimer;
    private int animTick = 0;

    // =========================================================================
    // 牧羊人引领小羊在草地与溪水旁歇息 —— 56x27 像素点阵矩阵
    // 图例说明：
    // '.' 透明背景   '*' 引路星核   '+' 夜空微星   'Y' 牧羊杖/星芒金
    // 'H' 牧人头巾   'K' 牧人面庞   'T' 牧人内袍   'R' 牧人外袍
    // 'W' 小羊白毛   'S' 羊毛暗部   'D' 小羊脸与蹄
    // 'L' 嫩绿草尖   'G' 丰美牧草   'E' 草地深影   'F' 小黄花   'P' 小粉花
    // 'C' 清澈溪水   'B' 溪水深处   'Q' 溪水波光
    // =========================================================================
    private static final String[] PASTORAL_MATRIX = {
            "........................................................",
            "......+.....................*..................+........",
            "...........................*Y*..........................",
            "..............+.............*..........+................",
            "................YYYY....................................",
            ".........HHHH..Y....Y...................................",
            "........HHHHHH.Y....Y...................................",
            "........HHKKHH.Y...Y....................................",
            "........HHTTHH.Y........................................",
            ".......HRRRRRRHY........................................",
            "......HRRTRRRRRY........................................",
            "......HRRTRRRR.Y........................................",
            "......HRRTRRRR.Y....WWW.................................",
            ".......HRTRRRR.Y...DWWWSWWWW............................",
            ".......HRTRRRR.Y....DWWWWWWWW...WWW.....................",
            ".......HRRRRRR.Y....SWWWWWWWWS.DWWWSWWWW.....WWW........",
            "........DD..DD.Y.....D.D...D.D.SWWWWWWWWS...DWWWSWWWW...",
            "....LLLLGGGGGGGLLLL..D.D...D.D.SSWWWWWWSS....DWWWWWWWS..",
            "..LLGGGGGGGGGGGGGGGLLLLLLLLLLLLGGGGGGGGGGLLCCC.SWWWWSS..",
            ".LGGGGGGFGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGLLCCCCCCQCCCCGG..",
            ".GGGGGGGGGGGGGGGGGPGGGGGGGGGGGGGGGGGGLLCCCCQQCCCCCCCGGG.",
            ".GGGGGGGGGGGGGGGGGGGGGGGGGFGGGGGGGGLLCCCCCCCCCCCCGGGGGG.",
            "..EGGGGGGGFGGGGGGGGGGGGGGGGGGGGGGLLCCCCQQCCCCCCCGGGGFGG.",
            "...EEGGGGGGGGGGGGGPGGGGGGGGGGGGLLCCCCCCCCCCCQQCCGGGGGG..",
            ".....EEEGGGGGGGGGGGGGGGGGGGGGLLCCCQQCCCCCCCCCCGGGGGGEE..",
            "........EEEEGGGGGGGGGGGGGGGLLCCCCCCCCCCCQQCCGGGGGEEE....",
            "............EEEEGGGGGGGGGLLBBBBCCCCCCCCCCCBBEEEE........"
    };

    public ConverterPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic(); // 返回时切断圣歌与动效
            parent.showMenu();
        });

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);
        leftTop.add(backBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        engineIndicator = new OfficeEngineIndicator();
        engineIndicator.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 仅在非转换且非检测状态下，点击右上角按钮才重新搜索部署
                if (!isConverting && !isCheckingEngine) {
                    checkOfficeEngine();
                }
            }
        });
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.setOpaque(false);
        rightTop.add(engineIndicator);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(40, 80, 80, 80));

        dropArea = new GlassDropPanel();
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dropArea.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 隐形交互：点击面板控制圣歌播放
        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleMusic();
            }
        });

        // 带事件穿透 + 代码点阵绘制的中央组件
        dl = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false; // 让拖拽事件和点击穿透到底层
            }

            @Override
            protected void paintComponent(Graphics g) {
                // 转换进行中时，交给原生 JLabel 渲染诗篇与进度 HTML
                if (isConverting) {
                    super.paintComponent(g);
                    return;
                }
                // 静默/待机状态时，直接用代码点阵绘制“牧羊人引领小羊在草地与溪水旁歇息”
                paintPastoralDotMatrix(g);
            }
        };
        dropArea.add(dl, BorderLayout.CENTER);

        setConvertDropTarget(dropArea);

        centerPanel.add(dropArea, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        adjustFontSize();
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { adjustFontSize(); }
        });

        // 优先复用历史检测结果，仅在首次运行时自动搜索
        initOfficeEngineState();
    }

    /**
     * 初始化 Office 引擎状态：
     * 若曾经检测过则直接加载持久化结果（无需等待），仅在首次打开时执行搜索。
     */
    private void initOfficeEngineState() {
        boolean hasCheckedBefore = PREFS.getBoolean(PREF_KEY_OFFICE_CHECKED, false);
        if (hasCheckedBefore) {
            isEngineReady = PREFS.getBoolean(PREF_KEY_OFFICE_READY, false);
            isCheckingEngine = false;
            engineIndicator.repaint();
        } else {
            checkOfficeEngine();
        }
    }

    /**
     * 核心点阵渲染器：绘制牧羊人、羊群、草地与小溪点阵图
     */
    private void paintPastoralDotMatrix(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = dl.getWidth();
        int h = dl.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        int rows = PASTORAL_MATRIX.length;
        int cols = PASTORAL_MATRIX[0].length();

        // 根据当前面板宽高自适应计算点阵颗粒尺寸（预留底部微缩铭文空间）
        int cellSize = Math.max(3, Math.min(w / (cols + 6), (h - 28) / (rows + 2)));
        int gap = cellSize >= 5 ? 1 : 0; // 点阵颗粒间隙，营造经典像素点阵屏质感
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH - (cellSize * 2)) / 2;

        // 引路之星的柔和背光光晕
        int starCenterX = startX + 28 * cellSize + cellSize / 2;
        int starCenterY = startY + 2 * cellSize + cellSize / 2;
        int glowRadius = cellSize * (isPlaying && (animTick % 2 == 0) ? 5 : 4);
        g2.setColor(new Color(251, 191, 36, 28));
        g2.fillOval(starCenterX - glowRadius, starCenterY - glowRadius, glowRadius * 2, glowRadius * 2);

        // 逐行逐列扫描绘制点阵
        for (int r = 0; r < rows; r++) {
            String line = PASTORAL_MATRIX[r];
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

        // 底部微缩点阵铭文
        int fontSize = Math.max(11, (int) (cellSize * 1.8));
        g2.setFont(FontUtils.getEnglishFont(Font.BOLD, fontSize));
        g2.setColor(new Color(226, 232, 240, isPlaying ? 210 : 140));
        String caption = "G O D   L E A D S   U S   A L O N G";
        FontMetrics fm = g2.getFontMetrics();
        int textX = (w - fm.stringWidth(caption)) / 2;
        int textY = startY + totalH + Math.max(16, cellSize * 3);
        g2.drawString(caption, textX, textY);

        g2.dispose();
    }

    /**
     * 点阵调色盘映射（支持圣歌播放时的星光闪烁与溪水流动）
     */
    private Color getDotColor(char ch, int row, int col) {
        boolean shimmer = isPlaying && ((row + col + animTick) % 3 == 0);
        switch (ch) {
            case '*': // 引路星核
                return shimmer ? new Color(255, 255, 255) : new Color(254, 243, 199);
            case '+': // 夜空微星
                return shimmer ? new Color(224, 242, 254) : new Color(147, 197, 253, 190);
            case 'Y': // 牧羊杖 / 金色星芒
                return new Color(251, 191, 36);
            case 'H': // 牧羊人头巾与外披肩
                return new Color(217, 119, 6);
            case 'R': // 牧羊人深暖褐长袍
                return new Color(180, 83, 9);
            case 'T': // 牧羊人浅米色内袍
                return new Color(253, 230, 138);
            case 'K': // 牧羊人面庞
                return new Color(252, 211, 77);
            case 'W': // 小羊蓬松白毛
                return new Color(248, 250, 252);
            case 'S': // 小羊羊毛柔和暗部与耳朵
                return new Color(203, 213, 225);
            case 'D': // 小羊脸部与小蹄子
                return new Color(71, 85, 105);
            case 'L': // 嫩绿草尖高光
                return new Color(74, 222, 128);
            case 'G': // 丰美青草地
                return new Color(34, 197, 94);
            case 'E': // 草坡深层阴影
                return new Color(21, 128, 61);
            case 'F': // 牧场小黄花
                return new Color(253, 224, 71);
            case 'P': // 牧场小粉花
                return new Color(244, 114, 182);
            case 'C': // 清澈溪流
                return shimmer ? new Color(125, 211, 252) : new Color(56, 189, 248);
            case 'B': // 溪水深处
                return new Color(2, 132, 199);
            case 'Q': // 溪水粼粼波光
                return shimmer ? new Color(56, 189, 248) : new Color(224, 242, 254);
            default:
                return null;
        }
    }

    // 静态化四句诗篇常量，避免在定时器中高频创建数组
    private static final String[] CONVERT_PHRASES = {
            "The LORD is my shepherd,",
            "Resting in the green pastures,",
            "Led beside the still waters,",
            "He restores my weary soul."
    };

    private void adjustFontSize() {
        int width = getWidth();
        if (width <= 0) return;

        double scale = Math.max(0.6, Math.min(width / 800.0, 1.4));
        int titleSize = (int) (48 * scale);
        int englishSize = (int) (26 * scale);

        if (isConverting) {
            int stage = Math.min(3, currentProgress / 25);

            String displayText = "<span style='font-family: \"" + FontUtils.getGodArtFamily() + "\", cursive, serif; font-size:" + (int)(englishSize * 1.35) + "px; font-style:italic; letter-spacing: 2px;'>" + CONVERT_PHRASES[stage] + "</span>" +
                    "<br><br><span style='font-family: \"" + FontUtils.getEnglishFamily() + "\", sans-serif; font-size:" + (titleSize / 3) + "px; color:#FFFFFF; opacity: 0.8; letter-spacing: 4px; font-weight:bold;'> " + currentProgress + "% </span>";

            dl.setText("<html><center>" +
                    "<span style='color:#EAEAEA;'>" +
                    displayText + "</span>" +
                    "</center></html>");
        } else {
            // 非转换状态下清空文本，交由 dl.paintComponent 绘制代码点阵图
            if (!dl.getText().isEmpty()) {
                dl.setText("");
            }
            dl.repaint();
        }

        // 仅在转换期间同步刷新右上角指示灯的呼吸脉冲特效
        if (isConverting && engineIndicator != null) {
            engineIndicator.repaint();
        }
    }

    // --- 隐形音乐播放引擎（联动点阵微光动效） ---
    private void toggleMusic() {
        if (isPlaying) {
            stopMusic();
        } else {
            isPlaying = true;
            startMusicAnimTimer();
            Thread.ofVirtual().name("Converter-MusicPlayer").start(() -> {
                try {
                    java.io.InputStream is = getClass().getResourceAsStream("/music/God Leads Us Along.mp3");

                    if (is != null) {
                        player = new javazoom.jl.player.Player(is);
                        player.play();

                        if (isPlaying) {
                            SwingUtilities.invokeLater(this::stopMusic);
                        }
                    } else {
                        System.out.println("提示：类路径中未找到 /music/God Leads Us Along.mp3");
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
            musicAnimTimer = new Timer(400, e -> {
                if (isPlaying && !isConverting) {
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
            player.close();
        }
        if (dl != null) {
            dl.repaint();
        }
    }

    @Override
    public void removeNotify() {
        stopMusic();
        if (progressTimer != null && progressTimer.isRunning()) {
            progressTimer.stop();
        }
        super.removeNotify();
    }

    private String runPowerShellInMemory(String psCode) throws Exception {
        String fullPsCode = "$ProgressPreference = 'SilentlyContinue'\n" + psCode;
        byte[] bytes = fullPsCode.getBytes("UTF-16LE");
        String b64 = Base64.getEncoder().encodeToString(bytes);

        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoLogo", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", b64
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        process.getOutputStream().close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("#< CLIXML") && !line.startsWith("<Objs ")) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "转换超时：后台 Word 进程响应过慢或文档受保护";
        }
        return output.toString().trim();
    }

    /**
     * 严格检测本机是否安装了完整可用的 Microsoft Office (Word)
     * 检测完成后将结果写入 Preferences 持久化缓存
     */
    private void checkOfficeEngine() {
        isCheckingEngine = true;
        engineIndicator.repaint();

        Thread.ofVirtual().name("Converter-OfficeDetector").start(() -> {
            try {
                String psCode =
                        "$ErrorActionPreference = 'Stop'\n" +
                                "$ids = @('Word.Application.16', 'Word.Application.15', 'Word.Application.14', 'Word.Application')\n" +
                                "foreach ($id in $ids) {\n" +
                                "    $temp = $null\n" +
                                "    try {\n" +
                                "        $temp = New-Object -ComObject $id\n" +
                                "        if ($null -ne $temp) {\n" +
                                "            $appName = [string]$temp.Name\n" +
                                "            $appVer = [string]$temp.Version\n" +
                                "            $docsObj = $temp.Documents\n" +
                                "            if ((-not [string]::IsNullOrWhiteSpace($appName)) -and ($appName -match 'Word') -and ($appName -notmatch 'WPS|Kingsoft') -and (-not [string]::IsNullOrWhiteSpace($appVer)) -and ($null -ne $docsObj)) {\n" +
                                "                try { $temp.Quit() } catch {}\n" +
                                "                try { [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($temp) | Out-Null } catch {}\n" +
                                "                Write-Output 'OFFICE_OK'\n" +
                                "                exit 0\n" +
                                "            }\n" +
                                "            try { $temp.Quit() } catch {}\n" +
                                "        }\n" +
                                "    } catch {\n" +
                                "        if ($null -ne $temp) { try { $temp.Quit() } catch {} }\n" +
                                "        continue\n" +
                                "    }\n" +
                                "}\n" +
                                "Write-Output 'NO_OFFICE'\n" +
                                "exit 1";

                String result = runPowerShellInMemory(psCode);
                isEngineReady = result.contains("OFFICE_OK");

                // 写入持久化记录，后续启动直接复用
                PREFS.putBoolean(PREF_KEY_OFFICE_CHECKED, true);
                PREFS.putBoolean(PREF_KEY_OFFICE_READY, isEngineReady);

            } catch (Exception e) {
                isEngineReady = false;
                PREFS.putBoolean(PREF_KEY_OFFICE_CHECKED, true);
                PREFS.putBoolean(PREF_KEY_OFFICE_READY, false);
            } finally {
                isCheckingEngine = false;
                SwingUtilities.invokeLater(() -> {
                    engineIndicator.repaint();
                    ConverterPanel.this.repaint();
                });
            }
        });
    }

    /**
     * 判断文件扩展名并返回转换类型标志 (1: Word->PDF, 2: PDF->Word, 3: 图片->PDF, 0: 不支持)
     */
    private int getConvertTypeFlag(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".doc") || name.endsWith(".docx")) {
            return 1;
        } else if (name.endsWith(".pdf")) {
            return 2;
        } else if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
            return 3;
        }
        return 0;
    }

    private void setConvertDropTarget(GlassDropPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);

                if (!isEngineReady || isConverting) return;
                SwingUtilities.invokeLater(() -> p.setDragActive(true));
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // 保持空实现，杜绝高频 IPC 调用引发的底层死锁
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                SwingUtilities.invokeLater(() -> p.setDragActive(false));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                SwingUtilities.invokeLater(() -> p.setDragActive(false));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dtde.dropComplete(true);

                    if (files == null || files.isEmpty()) return;

                    if (isConverting) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(ConverterPanel.this, "转换任务正在执行中，请耐心等待。")
                        );
                        return;
                    }

                    if (isCheckingEngine) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(ConverterPanel.this, "正在检测系统 Office 环境，请稍候再试。")
                        );
                        return;
                    }

                    if (!isEngineReady) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(
                                        ConverterPanel.this,
                                        "未检测到安装 Microsoft Office！\n请先安装完整版 Microsoft Office (Word) 后，点击右上角指示灯重新检测。",
                                        "未安装 Office",
                                        JOptionPane.WARNING_MESSAGE
                                )
                        );
                        return;
                    }

                    // 校验所有拖入的文件（支持多文件批量拖入）
                    List<File> validFiles = new ArrayList<>();
                    for (File f : files) {
                        if (f.isDirectory()) {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(ConverterPanel.this, "系统暂不支持对文件夹进行操作。")
                            );
                            return;
                        }
                        if (getConvertTypeFlag(f) == 0) {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(ConverterPanel.this, "格式不兼容，系统仅支持 Word、PDF 或图片格式：\n" + f.getName())
                            );
                            return;
                        }
                        validFiles.add(f);
                    }

                    Thread.ofVirtual().name("Converter-BatchWorker").start(() -> startBatchConversionTask(validFiles));

                } catch (Exception ex) {
                    ex.printStackTrace();
                    try { dtde.dropComplete(false); } catch (Exception ignored) {}
                }
            }
        });
    }

    /**
     * 批量转换任务项结构体
     */
    private static class ConvertItem {
        final File inputFile;
        final File outputFile;
        final int typeFlag;

        ConvertItem(File inputFile, File outputFile, int typeFlag) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
            this.typeFlag = typeFlag;
        }
    }

    /**
     * 判断异常信息是否属于未安装 Office 或 COM 空对象错误
     */
    private boolean isNoOfficeError(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return msg.contains("NO_OFFICE")
                || msg.contains("DOC_NULL")
                || msg.contains("无法唤醒")
                || msg.contains("不能对 null 值表达式调用方法")
                || lower.contains("null-valued expression")
                || lower.contains("cannot call a method on a null")
                || lower.contains("80040154")
                || msg.contains("没有注册类")
                || msg.contains("未注册类");
    }

    /**
     * 多文件总进度批量转换核心逻辑
     */
    private void startBatchConversionTask(List<File> inputFiles) {
        final int totalFiles = inputFiles.size();
        if (totalFiles == 0) return;

        // 预计算每个输入文件对应的输出路径（防止多文件同名互相覆盖）
        List<ConvertItem> items = new ArrayList<>(totalFiles);
        Set<String> reservedPaths = new HashSet<>();

        for (File inputFile : inputFiles) {
            int typeFlag = getConvertTypeFlag(inputFile);
            String ext = (typeFlag == 2) ? "docx" : "pdf";
            String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");

            File outDir = inputFile.getParentFile();
            if (outDir == null || !outDir.exists()) {
                outDir = new File(System.getProperty("user.home"));
            }

            File outFile = new File(outDir, baseName + "." + ext);
            int count = 1;
            while (outFile.exists() || reservedPaths.contains(outFile.getAbsolutePath().toLowerCase())) {
                outFile = new File(outDir, baseName + "_复件" + count + "." + ext);
                count++;
            }
            reservedPaths.add(outFile.getAbsolutePath().toLowerCase());
            items.add(new ConvertItem(inputFile, outFile, typeFlag));
        }

        isConverting = true;
        currentProgress = 0;
        // 第一个文件的初始爬升上限
        targetProgressCeiling = Math.min(95, Math.max(1, (100 / totalFiles) - 2));

        SwingUtilities.invokeLater(() -> {
            if (progressTimer != null && progressTimer.isRunning()) {
                progressTimer.stop();
            }
            adjustFontSize();
            progressTimer = new Timer(120, e -> {
                if (currentProgress < targetProgressCeiling) {
                    int step = (totalFiles == 1 && currentProgress < 60) ? (int) (Math.random() * 3 + 1) : 1;
                    currentProgress = Math.min(targetProgressCeiling, currentProgress + step);
                    adjustFontSize();
                }
            });
            progressTimer.setCoalesce(true);
            progressTimer.start();
        });

        // 构建单实例多文件复用的 PowerShell 脚本
        StringBuilder psBuilder = new StringBuilder();
        psBuilder.append("$ErrorActionPreference = 'Stop'\n")
                .append("$ProgressPreference = 'SilentlyContinue'\n")
                .append("$word = $null\n")
                .append("$ids = @('Word.Application.16', 'Word.Application.15', 'Word.Application.14', 'Word.Application')\n")
                .append("foreach ($id in $ids) {\n")
                .append("    $temp = $null\n")
                .append("    try {\n")
                .append("        $temp = New-Object -ComObject $id\n")
                .append("        if ($null -ne $temp) {\n")
                .append("            $appName = [string]$temp.Name\n")
                .append("            $appVer = [string]$temp.Version\n")
                .append("            $docsObj = $temp.Documents\n")
                .append("            if ((-not [string]::IsNullOrWhiteSpace($appName)) -and ($appName -match 'Word') -and ($appName -notmatch 'WPS|Kingsoft') -and (-not [string]::IsNullOrWhiteSpace($appVer)) -and ($null -ne $docsObj)) {\n")
                .append("                $word = $temp\n")
                .append("                break\n")
                .append("            }\n")
                .append("            try { $temp.Quit() } catch {}\n")
                .append("        }\n")
                .append("    } catch {\n")
                .append("        if ($null -ne $temp) { try { $temp.Quit() } catch {} }\n")
                .append("        continue\n")
                .append("    }\n")
                .append("}\n")
                .append("if ($null -eq $word -or $null -eq $word.Documents) {\n")
                .append("    [Console]::Out.WriteLine('::NO_OFFICE::')\n")
                .append("    [Console]::Out.Flush()\n")
                .append("    exit 1\n")
                .append("}\n")
                .append("try {\n")
                .append("    try { $word.Visible = $false } catch {}\n")
                .append("    try { $word.DisplayAlerts = 0 } catch {}\n")
                .append("    try { $word.AutomationSecurity = 3 } catch {}\n");

        for (int i = 0; i < totalFiles; i++) {
            ConvertItem item = items.get(i);
            String inPath = item.inputFile.getAbsolutePath().replace("'", "''");
            String outPath = item.outputFile.getAbsolutePath().replace("'", "''");

            psBuilder.append("    [Console]::Out.WriteLine('::FILE_START:").append(i).append("::')\n")
                    .append("    [Console]::Out.Flush()\n")
                    .append("    $doc = $null\n")
                    .append("    try {\n")
                    .append("        if ($null -eq $word.Documents) { throw 'NO_OFFICE' }\n");

            if (item.typeFlag == 1) {
                psBuilder.append("        $doc = $word.Documents.Open('").append(inPath).append("', $false, $true)\n")
                        .append("        if ($null -eq $doc) { throw 'DOC_NULL' }\n")
                        .append("        $doc.SaveAs([ref]'").append(outPath).append("', [ref]17)\n");
            } else if (item.typeFlag == 2) {
                psBuilder.append("        $doc = $word.Documents.Open('").append(inPath).append("', $false, $false)\n")
                        .append("        if ($null -eq $doc) { throw 'DOC_NULL' }\n")
                        .append("        $doc.SaveAs([ref]'").append(outPath).append("', [ref]16)\n");
            } else if (item.typeFlag == 3) {
                psBuilder.append("        $doc = $word.Documents.Add()\n")
                        .append("        if ($null -eq $doc -or $null -eq $doc.InlineShapes) { throw 'DOC_NULL' }\n")
                        .append("        $null = $doc.InlineShapes.AddPicture('").append(inPath).append("', $false, $true)\n")
                        .append("        $doc.SaveAs([ref]'").append(outPath).append("', [ref]17)\n");
            }

            psBuilder.append("        if ($null -ne $doc) {\n")
                    .append("            $doc.Close([ref]$false)\n")
                    .append("            $doc = $null\n")
                    .append("        }\n")
                    .append("        [Console]::Out.WriteLine('::FILE_OK:").append(i).append("::')\n")
                    .append("        [Console]::Out.Flush()\n")
                    .append("    } catch {\n")
                    .append("        if ($null -ne $doc) { try { $doc.Close([ref]$false) } catch {} }\n")
                    .append("        $errMsg = ([string]$_.Exception.Message) -replace '[\\r\\n]+', ' '\n")
                    .append("        [Console]::Out.WriteLine('::FILE_ERR:").append(i).append(":' + $errMsg)\n")
                    .append("        [Console]::Out.Flush()\n")
                    .append("    }\n");
        }

        psBuilder.append("} finally {\n")
                .append("    if ($null -ne $word) {\n")
                .append("        try { $word.Quit() } catch {}\n")
                .append("        try { [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($word) | Out-Null } catch {}\n")
                .append("    }\n")
                .append("}\n")
                .append("[Console]::Out.WriteLine('::BATCH_DONE::')\n")
                .append("[Console]::Out.Flush()\n")
                .append("exit 0\n");

        boolean missingOfficeDetected = false;
        int successCount = 0;
        List<String> errorMessages = new ArrayList<>();
        StringBuilder rawLog = new StringBuilder();

        try {
            byte[] bytes = psBuilder.toString().getBytes("UTF-16LE");
            String b64 = Base64.getEncoder().encodeToString(bytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoLogo", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", b64
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            process.getOutputStream().close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#< CLIXML") || line.startsWith("<Objs ")) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.contains("::NO_OFFICE::")) {
                    missingOfficeDetected = true;
                } else if (trimmed.startsWith("::FILE_START:")) {
                    int idx = parseMarkerIndex(trimmed, "::FILE_START:");
                    if (idx >= 0 && idx < totalFiles) {
                        int basePct = (idx * 100) / totalFiles;
                        int nextPct = ((idx + 1) * 100) / totalFiles;
                        currentProgress = Math.max(currentProgress, basePct);
                        targetProgressCeiling = Math.min(95, Math.max(basePct, nextPct - 2));
                        SwingUtilities.invokeLater(this::adjustFontSize);
                    }
                } else if (trimmed.startsWith("::FILE_OK:")) {
                    int idx = parseMarkerIndex(trimmed, "::FILE_OK:");
                    if (idx >= 0 && idx < totalFiles) {
                        if (items.get(idx).outputFile.exists()) {
                            successCount++;
                        } else {
                            errorMessages.add(items.get(idx).inputFile.getName() + "：未生成输出文件");
                        }
                        // 前序文件完成时推进到对应总进度百分比，最后一个文件留到全部结束时置为 100%
                        if (idx < totalFiles - 1) {
                            int milestone = Math.min(99, ((idx + 1) * 100) / totalFiles);
                            currentProgress = Math.max(currentProgress, milestone);
                            targetProgressCeiling = Math.max(targetProgressCeiling, currentProgress);
                            SwingUtilities.invokeLater(this::adjustFontSize);
                        }
                    }
                } else if (trimmed.startsWith("::FILE_ERR:")) {
                    String payload = trimmed.substring("::FILE_ERR:".length());
                    int colonPos = payload.indexOf(':');
                    if (colonPos > 0) {
                        int idx = Integer.parseInt(payload.substring(0, colonPos));
                        String err = payload.substring(colonPos + 1).trim();
                        if (isNoOfficeError(err)) {
                            missingOfficeDetected = true;
                        } else {
                            String fileName = (idx >= 0 && idx < totalFiles) ? items.get(idx).inputFile.getName() : "文件";
                            errorMessages.add(fileName + "：" + err);
                        }
                        if (idx >= 0 && idx < totalFiles - 1) {
                            int milestone = Math.min(99, ((idx + 1) * 100) / totalFiles);
                            currentProgress = Math.max(currentProgress, milestone);
                            SwingUtilities.invokeLater(this::adjustFontSize);
                        }
                    }
                } else if (!trimmed.equals("::BATCH_DONE::")) {
                    rawLog.append(trimmed).append("\n");
                    if (isNoOfficeError(trimmed)) {
                        missingOfficeDetected = true;
                    }
                }
            }

            long timeoutSeconds = Math.max(120L, totalFiles * 90L);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                errorMessages.add("转换超时：后台 Word 进程响应过慢或文档受保护");
            }

        } catch (Exception e) {
            if (isNoOfficeError(e.getMessage())) {
                missingOfficeDetected = true;
            } else {
                errorMessages.add("执行异常：" + e.getMessage());
            }
        }

        final boolean finalMissingOffice = missingOfficeDetected;
        final int finalSuccessCount = successCount;
        final List<String> finalErrors = errorMessages;
        final String finalRawLog = rawLog.toString().trim();

        SwingUtilities.invokeLater(() -> {
            if (progressTimer != null) progressTimer.stop();

            // 1. 检测到未安装 Office 或 Office 组件残缺导致 null 表达式错误
            if (finalMissingOffice) {
                isConverting = false;
                isEngineReady = false;
                PREFS.putBoolean(PREF_KEY_OFFICE_CHECKED, true);
                PREFS.putBoolean(PREF_KEY_OFFICE_READY, false);
                engineIndicator.repaint();
                adjustFontSize();
                JOptionPane.showMessageDialog(
                        ConverterPanel.this,
                        "未检测到安装 Microsoft Office！\n请确认系统已安装完整版 Microsoft Office (Word) 后，点击右上角按钮重新检测。",
                        "未安装 Office",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            // 2. 至少有一个文件成功转换（完成全部后进度达 100%）
            if (finalSuccessCount > 0) {
                currentProgress = 100;
                adjustFontSize();
                new Timer(500, evt -> {
                    ((Timer) evt.getSource()).stop();
                    isConverting = false;
                    adjustFontSize();

                    if (totalFiles == 1 && finalErrors.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                ConverterPanel.this,
                                "转换已完成。\n文件已保存在原文件同级目录：\n" + items.get(0).outputFile.getName(),
                                "系统通知",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else if (finalErrors.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                ConverterPanel.this,
                                "全部转换已完成（共 " + finalSuccessCount + " 个文件）。\n文件已保存在原文件同级目录。",
                                "系统通知",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        String errDetail = String.join("\n", finalErrors);
                        if (errDetail.length() > 150) errDetail = errDetail.substring(0, 150) + "...";
                        JOptionPane.showMessageDialog(
                                ConverterPanel.this,
                                "转换完成：成功 " + finalSuccessCount + " 个，失败 " + finalErrors.size() + " 个。\n异常明细：\n" + errDetail,
                                "系统通知",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                }).start();
            } else {
                // 3. 全部失败
                isConverting = false;
                adjustFontSize();
                String failMsg = !finalErrors.isEmpty() ? String.join("\n", finalErrors) : finalRawLog;
                if (isNoOfficeError(failMsg)) {
                    isEngineReady = false;
                    PREFS.putBoolean(PREF_KEY_OFFICE_CHECKED, true);
                    PREFS.putBoolean(PREF_KEY_OFFICE_READY, false);
                    engineIndicator.repaint();
                    JOptionPane.showMessageDialog(
                            ConverterPanel.this,
                            "未检测到安装 Microsoft Office！\n请确认系统已安装完整版 Microsoft Office (Word) 后，点击右上角按钮重新检测。",
                            "未安装 Office",
                            JOptionPane.WARNING_MESSAGE
                    );
                } else {
                    if (failMsg.isEmpty()) failMsg = "内存注入被系统安全策略拦截。";
                    if (failMsg.length() > 80) failMsg = failMsg.substring(0, 80) + "...";
                    JOptionPane.showMessageDialog(
                            ConverterPanel.this,
                            "转换中止。\n系统日志：\n" + failMsg,
                            "系统异常",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }

    private int parseMarkerIndex(String line, String prefix) {
        try {
            int endIdx = line.indexOf("::", prefix.length());
            if (endIdx > prefix.length()) {
                return Integer.parseInt(line.substring(prefix.length(), endIdx).trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    class OfficeEngineIndicator extends JComponent {

        public OfficeEngineIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            Color coreColor;
            Color glowColor;
            if (isCheckingEngine) {
                coreColor = new Color(255, 180, 0);
                glowColor = new Color(255, 180, 0, 80);
            } else if (isEngineReady) {
                coreColor = new Color(0, 150, 255);
                glowColor = new Color(0, 150, 255, 100);
            } else {
                coreColor = new Color(100, 100, 100);
                glowColor = new Color(50, 50, 50, 50);
            }

            // 无论是否在转换，都只画一圈安定的固定微光，杜绝高频辐射跳动
            g2.setColor(glowColor);
            // 改为半径 15，直径 30，与 CloudSharePanel 的基础光晕完全同款
            g2.fillOval(cx - 15, cy - 15, 30, 30);
            g2.setColor(coreColor);
            g2.fillRoundRect(cx - 10, cy - 10, 20, 20, 6, 6);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int[] wX = {cx - 5, cx - 3, cx, cx + 3, cx + 5};
            int[] wY = {cy - 4, cy + 4, cy, cy + 4, cy - 4};
            g2.drawPolyline(wX, wY, 5);

            g2.dispose();
        }
    }
}