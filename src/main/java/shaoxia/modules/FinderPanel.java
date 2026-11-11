package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import org.json.JSONObject;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件检索助手 FinderPanel
 * (极限暗夜毛玻璃 + 大磁盘防卡死虚拟化极速渲染 + 动态圆圈实时进度 + 扫描与搜索结果跨会话持久化版)
 */
public class FinderPanel extends BackgroundPanel {
    private JTextField pathDisplayField;
    private JTextArea rulesTextArea;

    private JList<File> fileList;
    private DefaultListModel<File> listModel;

    // 保留 allFiles 字段以兼容 MainLauncher 反射，真实全量扫描数据存储在 scannedFiles 中防止被主框架 5000 条限制清空
    private List<File> allFiles = new CopyOnWriteArrayList<>();
    private final List<File> scannedFiles = new CopyOnWriteArrayList<>();
    private final List<File> displayedFilesSnapshot = new CopyOnWriteArrayList<>();

    private JLabel statusLabel;

    // 左下角旋转进度小圆圈与后台任务控制
    private SpinnerLabel spinnerLabel;
    private Thread scanThread;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // 缓存文件是否为文件夹及渲染元数据，避免 JList 滚动或重启筛选时反复读取磁盘导致掉帧
    private final Map<File, Boolean> dirFlagCache = new ConcurrentHashMap<>();
    private final Map<File, FileMeta> metaCache = Collections.synchronizedMap(new WeakHashMap<>());

    // 跨会话持久化文件 (~/.toolkit/)
    private final File sessionMetaFile;
    private final File allFilesCacheFile;
    private final File displayedFilesCacheFile;

    // 恢复状态保护锁，防止启动时外部反射或复选框回填覆盖上次的搜索/提取结果
    private volatile boolean restoringSession = true;
    private String persistedPathText = " 未选择任何文件夹...";
    private String persistedRulesText = "评审报告\n专家签到表\n应答文件上传情况统计";
    private String persistedStatusText = " 准备就绪";
    private boolean persistedChkWord = true;
    private boolean persistedChkExcel = true;
    private boolean persistedChkPdf = true;
    private boolean persistedChkOther = true;
    private boolean persistedChkFolderOnly = false;
    private javax.swing.Timer rulesSaveDebounceTimer;

    // 跳过系统黑洞目录与超碎缓存目录，防止扫描整盘时死循环或卡死
    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            "$recycle.bin", "system volume information", "windows", "programdata",
            "$windows.~bt", "$windows.~ws", "recovery", "config.msi",
            "node_modules", ".git", ".idea", ".gradle", "__pycache__"
    ));

    // 文件与文件夹类型过滤复选框
    private JCheckBox chkWord, chkExcel, chkPdf, chkOther, chkFolderOnly;

    public FinderPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout(10, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        File dataDir = new File(System.getProperty("user.home"), ".toolkit");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.sessionMetaFile = new File(dataDir, "finder_session.json");
        this.allFilesCacheFile = new File(dataDir, "finder_all_files.cache");
        this.displayedFilesCacheFile = new File(dataDir, "finder_displayed_files.cache");

        initTopBar(parent);
        initLeftRulesArea();
        initCenterListArea();
        initBottomStatusBar();

        // 启动时加载上次退出前保存的扫描池与列表显示结果
        loadFinderSessionFromDisk();
        applyPersistedUIState();

        // 在 MainLauncher 的 invokeLater 反射恢复执行完毕后，再次锁定还原精确的列表与状态
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            applyPersistedUIState();
            restoringSession = false;
        }));

        // 注册 JVM 退出钩子，确保在任何页面直接关闭或从托盘退出时都能完整落盘
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveFinderSessionSync));
    }

    private void initTopBar(MainLauncher parent) {
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        // --- 第一行：核心操作按钮 ---
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        row1.setOpaque(false);

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            saveFinderSessionAsync();
            parent.showMenu();
        });

        ActionButton loadBtn = new ActionButton("扫描文件夹");
        loadBtn.setPreferredSize(new Dimension(130, 36));
        loadBtn.addActionListener(e -> selectAndLoad());

        ActionButton extractBtn = new ActionButton("提取关键项");
        extractBtn.setPreferredSize(new Dimension(130, 36));
        extractBtn.addActionListener(e -> quickExtract());

        ActionButton showAllBtn = new ActionButton("刷新总览");
        showAllBtn.setPreferredSize(new Dimension(130, 36));
        showAllBtn.addActionListener(e -> {
            restoringSession = true;
            resetFilters();
            restoringSession = false;
            onUserFilterChanged();
        });

        row1.add(backBtn);
        row1.add(loadBtn);
        row1.add(extractBtn);
        row1.add(showAllBtn);

        // --- 第二行：类型与文件夹过滤 ---
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        row2.setOpaque(false);

        JLabel filterIcon = new JLabel("类型筛选: ");
        filterIcon.setForeground(new Color(200, 200, 200));
        filterIcon.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
        row2.add(filterIcon);

        chkWord = createStyledCheckBox("Word");
        chkExcel = createStyledCheckBox("Excel");
        chkPdf = createStyledCheckBox("PDF");
        chkOther = createStyledCheckBox("其他");

        chkFolderOnly = createStyledCheckBox("仅文件夹");
        chkFolderOnly.setForeground(new Color(255, 255, 255));

        resetFilters();

        chkWord.addItemListener(e -> {
            if (!restoringSession) onUserFilterChanged();
        });
        chkExcel.addItemListener(e -> {
            if (!restoringSession) onUserFilterChanged();
        });
        chkPdf.addItemListener(e -> {
            if (!restoringSession) onUserFilterChanged();
        });
        chkOther.addItemListener(e -> {
            if (!restoringSession) onUserFilterChanged();
        });
        chkFolderOnly.addItemListener(e -> {
            boolean folderOnly = chkFolderOnly.isSelected();
            chkWord.setEnabled(!folderOnly);
            chkExcel.setEnabled(!folderOnly);
            chkPdf.setEnabled(!folderOnly);
            chkOther.setEnabled(!folderOnly);
            if (!restoringSession) onUserFilterChanged();
        });

        row2.add(chkWord);
        row2.add(chkExcel);
        row2.add(chkPdf);
        row2.add(chkOther);
        row2.add(new JLabel("  |  "));
        row2.add(chkFolderOnly);

        // --- 第三行：路径展示框 ---
        JPanel row3 = new JPanel(new BorderLayout());
        row3.setOpaque(false);
        row3.setBorder(new EmptyBorder(5, 10, 5, 10));

        pathDisplayField = new JTextField(" 未选择任何文件夹...") {
            @Override
            public boolean isOpaque() { return false; }
        };
        pathDisplayField.setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
        pathDisplayField.setEditable(false);
        pathDisplayField.setBackground(new Color(0, 0, 0, 0));
        pathDisplayField.setForeground(new Color(255, 255, 255));
        pathDisplayField.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        pathDisplayField.setFont(FontUtils.getChineseFont(Font.BOLD, 13));

        GlassPanel pathGlass = new GlassPanel(new BorderLayout());
        pathGlass.setPreferredSize(new Dimension(0, 35));
        pathGlass.add(pathDisplayField, BorderLayout.CENTER);
        row3.add(pathGlass, BorderLayout.CENTER);

        topContainer.add(row1);
        topContainer.add(row2);
        topContainer.add(row3);

        add(topContainer, BorderLayout.NORTH);
    }

    private void initLeftRulesArea() {
        rulesTextArea = new JTextArea("评审报告\n专家签到表\n应答文件上传情况统计") {
            @Override
            public boolean isOpaque() { return false; }
        };
        rulesTextArea.setUI(new javax.swing.plaf.basic.BasicTextAreaUI());
        rulesTextArea.setBackground(new Color(0, 0, 0, 0));
        rulesTextArea.setForeground(Color.WHITE);
        rulesTextArea.setCaretColor(Color.WHITE);
        rulesTextArea.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        rulesTextArea.setBorder(new EmptyBorder(5, 5, 5, 5));

        rulesSaveDebounceTimer = new javax.swing.Timer(500, e -> {
            if (!restoringSession) {
                saveFinderSessionAsync();
            }
        });
        rulesSaveDebounceTimer.setRepeats(false);

        rulesTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { triggerDebounceSave(); }
            @Override
            public void removeUpdate(DocumentEvent e) { triggerDebounceSave(); }
            @Override
            public void changedUpdate(DocumentEvent e) { triggerDebounceSave(); }

            private void triggerDebounceSave() {
                if (!restoringSession && rulesSaveDebounceTimer != null) {
                    rulesSaveDebounceTimer.restart();
                }
            }
        });

        JScrollPane rulesScroll = new JScrollPane(rulesTextArea) {
            @Override
            public boolean isOpaque() { return false; }
        };
        rulesScroll.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
        rulesScroll.getViewport().setUI(new javax.swing.plaf.basic.BasicViewportUI());
        rulesScroll.setOpaque(false);
        rulesScroll.getViewport().setOpaque(false);
        rulesScroll.setBackground(new Color(0, 0, 0, 0));
        rulesScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        rulesScroll.setBorder(null);
        rulesScroll.setViewportBorder(null);

        GlassPanel rulesGlassWrapper = new GlassPanel(new BorderLayout(0, 5));
        rulesGlassWrapper.setPreferredSize(new Dimension(250, 0));
        rulesGlassWrapper.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        rulesGlassWrapper.add(rulesScroll, BorderLayout.CENTER);

        JPanel leftAlignWrapper = new JPanel(new BorderLayout());
        leftAlignWrapper.setOpaque(false);
        leftAlignWrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        leftAlignWrapper.add(rulesGlassWrapper, BorderLayout.CENTER);

        add(leftAlignWrapper, BorderLayout.WEST);
    }

    private void initCenterListArea() {
        listModel = new DefaultListModel<>();

        fileList = new JList<>(listModel) {
            @Override
            public boolean isOpaque() { return false; }

            @Override
            public String getToolTipText(MouseEvent e) {
                int index = locationToIndex(e.getPoint());
                if (index > -1 && index < getModel().getSize()) {
                    Rectangle cellBounds = getCellBounds(index, index);
                    if (cellBounds != null && cellBounds.contains(e.getPoint())) {
                        File f = getModel().getElementAt(index);
                        return "<html><body style='font-family:微软雅黑; font-size:11px; color:#E0E0E0; margin:0; padding:2px; width:450px;'>"
                                + "<span style='color:#888888; font-size:10px;'>文件路径：</span><br>"
                                + "<div style='word-break: break-all; margin-top:3px; line-height:1.4;'>"
                                + f.getAbsolutePath()
                                + "</div>"
                                + "</body></html>";
                    }
                }
                return null;
            }

            @Override
            public JToolTip createToolTip() {
                JToolTip tip = super.createToolTip();
                tip.setOpaque(true);
                tip.setBackground(new Color(35, 38, 42));
                tip.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(85, 85, 85), 1),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)
                ));
                return tip;
            }
        };

        fileList.setOpaque(false);
        fileList.setBackground(new Color(0, 0, 0, 0));
        fileList.setBorder(BorderFactory.createEmptyBorder());

        // 防卡死核心设置：固定单元格宽高，阻止 Swing 在插入数万条数据时同步遍历调用所有元素的 Renderer 和磁盘 I/O
        fileList.setFixedCellHeight(50);
        fileList.setFixedCellWidth(200);

        // 极速自定义 Graphics2D 渲染器
        fileList.setCellRenderer(new FastFileCellRenderer());

        fileList.setDragEnabled(true);
        fileList.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                List<File> selectedFiles = fileList.getSelectedValuesList();
                if (selectedFiles.isEmpty()) return null;
                return new Transferable() {
                    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
                    public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.javaFileListFlavor.equals(f); }
                    public Object getTransferData(DataFlavor f) { return selectedFiles; }
                };
            }
            @Override
            public int getSourceActions(JComponent c) { return COPY; }
        });

        JScrollPane listScroll = new JScrollPane(fileList) {
            @Override
            public boolean isOpaque() { return false; }
        };
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        listScroll.setBackground(new Color(0, 0, 0, 0));
        listScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        listScroll.setBorder(null);
        listScroll.setViewportBorder(null);

        // 当视口宽度变化时，动态同步列表固定宽度，保证高光背景条 100% 铺满横向空间
        listScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int vw = listScroll.getViewport().getWidth();
                if (vw > 50 && fileList.getFixedCellWidth() != vw) {
                    fileList.setFixedCellWidth(vw);
                }
            }
        });

        GlassPanel listGlassWrapper = new GlassPanel(new BorderLayout());
        listGlassWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        listGlassWrapper.add(listScroll, BorderLayout.CENTER);

        JPanel centerAlignWrapper = new JPanel(new BorderLayout());
        centerAlignWrapper.setOpaque(false);
        centerAlignWrapper.setBorder(new EmptyBorder(0, 0, 0, 10));
        centerAlignWrapper.add(listGlassWrapper, BorderLayout.CENTER);

        add(centerAlignWrapper, BorderLayout.CENTER);
    }

    private void initBottomStatusBar() {
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(2, 10, 2, 10));

        spinnerLabel = new SpinnerLabel();
        spinnerLabel.setVisible(false);

        statusLabel = new JLabel(" 准备就绪");
        statusLabel.setForeground(new Color(180, 180, 180));
        statusLabel.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));

        bottomBar.add(spinnerLabel);
        bottomBar.add(statusLabel);

        add(bottomBar, BorderLayout.SOUTH);
    }

    // =========================================================================
    // 💾 跨会话扫描结果与搜索结果持久化引擎
    // =========================================================================

    private void loadFinderSessionFromDisk() {
        if (!sessionMetaFile.exists()) return;
        try {
            String jsonStr = Files.readString(sessionMetaFile.toPath(), StandardCharsets.UTF_8);
            JSONObject meta = new JSONObject(jsonStr);

            persistedPathText = meta.optString("pathText", " 未选择任何文件夹...");
            persistedRulesText = meta.optString("rulesText", "评审报告\n专家签到表\n应答文件上传情况统计");
            persistedStatusText = meta.optString("statusText", " 准备就绪");
            persistedChkWord = meta.optBoolean("chkWord", true);
            persistedChkExcel = meta.optBoolean("chkExcel", true);
            persistedChkPdf = meta.optBoolean("chkPdf", true);
            persistedChkOther = meta.optBoolean("chkOther", true);
            persistedChkFolderOnly = meta.optBoolean("chkFolderOnly", false);

            scannedFiles.clear();
            dirFlagCache.clear();
            if (allFilesCacheFile.exists()) {
                List<File> loadedAll = new ArrayList<>(4096);
                try (BufferedReader br = Files.newBufferedReader(allFilesCacheFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.length() > 2 && line.charAt(1) == '|') {
                            boolean isDir = line.charAt(0) == 'D';
                            File f = new File(line.substring(2));
                            dirFlagCache.put(f, isDir);
                            loadedAll.add(f);
                        } else if (!line.trim().isEmpty()) {
                            File f = new File(line.trim());
                            loadedAll.add(f);
                        }
                    }
                }
                scannedFiles.addAll(loadedAll);
            }

            displayedFilesSnapshot.clear();
            if (displayedFilesCacheFile.exists()) {
                List<File> loadedDisplayed = new ArrayList<>(1024);
                try (BufferedReader br = Files.newBufferedReader(displayedFilesCacheFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.length() > 2 && line.charAt(1) == '|') {
                            boolean isDir = line.charAt(0) == 'D';
                            File f = new File(line.substring(2));
                            dirFlagCache.putIfAbsent(f, isDir);
                            loadedDisplayed.add(f);
                        } else if (!line.trim().isEmpty()) {
                            File f = new File(line.trim());
                            loadedDisplayed.add(f);
                        }
                    }
                }
                displayedFilesSnapshot.addAll(loadedDisplayed);
            }
        } catch (Exception e) {
            System.err.println("读取 FinderPanel 跨会话缓存失败: " + e.getMessage());
        }
    }

    private void applyPersistedUIState() {
        restoringSession = true;
        try {
            pathDisplayField.setText(persistedPathText);
            rulesTextArea.setText(persistedRulesText);

            chkWord.setSelected(persistedChkWord);
            chkExcel.setSelected(persistedChkExcel);
            chkPdf.setSelected(persistedChkPdf);
            chkOther.setSelected(persistedChkOther);
            chkFolderOnly.setSelected(persistedChkFolderOnly);

            chkWord.setEnabled(!persistedChkFolderOnly);
            chkExcel.setEnabled(!persistedChkFolderOnly);
            chkPdf.setEnabled(!persistedChkFolderOnly);
            chkOther.setEnabled(!persistedChkFolderOnly);

            listModel.clear();
            if (!displayedFilesSnapshot.isEmpty()) {
                listModel.addAll(displayedFilesSnapshot);
            }
            statusLabel.setText(persistedStatusText);
        } finally {
            restoringSession = false;
        }
    }

    private void updateSnapshotFromUI(List<File> currentDisplayed) {
        persistedPathText = pathDisplayField.getText();
        persistedRulesText = rulesTextArea.getText();
        persistedStatusText = statusLabel.getText();
        persistedChkWord = chkWord.isSelected();
        persistedChkExcel = chkExcel.isSelected();
        persistedChkPdf = chkPdf.isSelected();
        persistedChkOther = chkOther.isSelected();
        persistedChkFolderOnly = chkFolderOnly.isSelected();

        if (currentDisplayed != null) {
            displayedFilesSnapshot.clear();
            displayedFilesSnapshot.addAll(currentDisplayed);
        }
    }

    private void saveFinderSessionAsync() {
        if (restoringSession) return;
        persistedPathText = pathDisplayField.getText();
        persistedRulesText = rulesTextArea.getText();
        persistedStatusText = statusLabel.getText();
        persistedChkWord = chkWord.isSelected();
        persistedChkExcel = chkExcel.isSelected();
        persistedChkPdf = chkPdf.isSelected();
        persistedChkOther = chkOther.isSelected();
        persistedChkFolderOnly = chkFolderOnly.isSelected();

        Thread.ofVirtual().name("Finder-Session-Saver").start(this::saveFinderSessionSync);
    }

    private synchronized void saveFinderSessionSync() {
        try {
            JSONObject meta = new JSONObject();
            meta.put("pathText", pathDisplayField != null ? pathDisplayField.getText() : persistedPathText);
            meta.put("rulesText", rulesTextArea != null ? rulesTextArea.getText() : persistedRulesText);
            meta.put("statusText", statusLabel != null ? statusLabel.getText() : persistedStatusText);
            meta.put("chkWord", chkWord != null ? chkWord.isSelected() : persistedChkWord);
            meta.put("chkExcel", chkExcel != null ? chkExcel.isSelected() : persistedChkExcel);
            meta.put("chkPdf", chkPdf != null ? chkPdf.isSelected() : persistedChkPdf);
            meta.put("chkOther", chkOther != null ? chkOther.isSelected() : persistedChkOther);
            meta.put("chkFolderOnly", chkFolderOnly != null ? chkFolderOnly.isSelected() : persistedChkFolderOnly);

            Files.writeString(sessionMetaFile.toPath(), meta.toString(2), StandardCharsets.UTF_8);

            try (BufferedWriter bw = Files.newBufferedWriter(allFilesCacheFile.toPath(), StandardCharsets.UTF_8)) {
                for (File f : scannedFiles) {
                    boolean isDir = isDirectoryCached(f);
                    bw.write(isDir ? "D|" : "F|");
                    bw.write(f.getAbsolutePath());
                    bw.newLine();
                }
            }

            try (BufferedWriter bw = Files.newBufferedWriter(displayedFilesCacheFile.toPath(), StandardCharsets.UTF_8)) {
                for (File f : displayedFilesSnapshot) {
                    boolean isDir = isDirectoryCached(f);
                    bw.write(isDir ? "D|" : "F|");
                    bw.write(f.getAbsolutePath());
                    bw.newLine();
                }
            }
        } catch (Exception e) {
            System.err.println("保存 FinderPanel 跨会话缓存失败: " + e.getMessage());
        }
    }

    private boolean isDirectoryCached(File f) {
        Boolean cached = dirFlagCache.get(f);
        if (cached != null) return cached;
        FileMeta meta = metaCache.get(f);
        if (meta != null) {
            dirFlagCache.put(f, meta.isDir);
            return meta.isDir;
        }
        boolean isDir = f.isDirectory();
        dirFlagCache.put(f, isDir);
        return isDir;
    }

    // =========================================================================
    // 🔍 核心检索与扫描逻辑
    // =========================================================================

    private void selectAndLoad() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        // 若上次已扫描过有效目录，自动定位至上次目录方便切换
        String curText = pathDisplayField.getText();
        if (curText != null && curText.contains("：")) {
            String lastPath = curText.substring(curText.indexOf("：") + 1).trim();
            File lastDir = new File(lastPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                chooser.setCurrentDirectory(lastDir);
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File sel = chooser.getSelectedFile();
            pathDisplayField.setText(" 当前扫描路径： " + sel.getAbsolutePath());
            scanDirectory(sel);
        }
    }

    private void scanDirectory(File dir) {
        cancelCurrentScan();

        cancelFlag.set(false);
        scannedFiles.clear();
        allFiles.clear();
        displayedFilesSnapshot.clear();
        dirFlagCache.clear();
        metaCache.clear();
        listModel.clear();

        spinnerLabel.startSpinning();
        statusLabel.setText(" 正在初始化扫描引擎...");

        AtomicInteger counter = new AtomicInteger(0);
        final long[] lastUiUpdateTime = {System.currentTimeMillis()};
        final int MAX_CAP = 250000;

        scanThread = Thread.ofVirtual().name("Finder-Disk-Scanner").start(() -> {
            List<File> localBuffer = new ArrayList<>(4096);
            recursiveWalkSafe(dir, localBuffer, counter, lastUiUpdateTime, MAX_CAP);

            if (cancelFlag.get()) return;

            scannedFiles.addAll(localBuffer);

            SwingUtilities.invokeLater(() -> {
                if (cancelFlag.get()) return;
                statusLabel.setText(" 正在整理并渲染结果 (" + scannedFiles.size() + " 项)...");
            });

            List<File> matched = computeFilteredFiles(null);

            SwingUtilities.invokeLater(() -> {
                if (cancelFlag.get()) return;
                listModel.clear();
                listModel.addAll(matched);
                spinnerLabel.stopSpinning();
                String capTip = scannedFiles.size() >= MAX_CAP ? " (已达单次安全上限)" : "";
                statusLabel.setText(" 扫描完成。共发现： " + scannedFiles.size() + " 项，当前显示： " + matched.size() + " 项" + capTip);

                updateSnapshotFromUI(matched);
                saveFinderSessionAsync();
            });
        });
    }

    private void cancelCurrentScan() {
        cancelFlag.set(true);
        if (scanThread != null && scanThread.isAlive()) {
            scanThread.interrupt();
        }
    }

    /**
     * 安全防死循环递归扫描：跳过符号链接、系统受保护目录，并实时节流刷新进度
     */
    private void recursiveWalkSafe(File dir, List<File> buffer, AtomicInteger counter, long[] lastUpdate, int maxCap) {
        if (cancelFlag.get() || buffer.size() >= maxCap) return;

        File[] list;
        try {
            list = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (list == null) return;

        for (File child : list) {
            if (cancelFlag.get() || buffer.size() >= maxCap) return;

            String name = child.getName();
            boolean isDir = child.isDirectory();

            // 过滤系统黑洞文件夹与开发缓存目录
            if (isDir && SKIP_DIRS.contains(name.toLowerCase())) {
                continue;
            }

            // 跳过 Windows 软链接 / Junctions，杜绝死循环递归
            try {
                if (Files.isSymbolicLink(child.toPath())) {
                    continue;
                }
            } catch (Exception ignored) {
            }

            dirFlagCache.put(child, isDir);
            buffer.add(child);
            int count = counter.incrementAndGet();

            // 每隔 100ms 向左下角推送一次实时扫描进度与当前目录名，丝滑不卡顿
            long now = System.currentTimeMillis();
            if (now - lastUpdate[0] >= 100) {
                lastUpdate[0] = now;
                final String currentFolder = dir.getName().isEmpty() ? dir.getPath() : dir.getName();
                final int currentCount = count;
                SwingUtilities.invokeLater(() -> {
                    if (!cancelFlag.get() && spinnerLabel.isSpinning()) {
                        statusLabel.setText(String.format(" 正在扫描: 已发现 %,d 项  [ %s ]", currentCount, truncateText(currentFolder, 28)));
                    }
                });
            }

            if (isDir) {
                recursiveWalkSafe(child, buffer, counter, lastUpdate, maxCap);
            }
        }
    }

    /**
     * 仅供 MainLauncher 反射调用的兼容入口：
     * 拦截主框架启动时的反射重置动作，确保恢复我们持久化的真实搜索列表与状态
     */
    private void filterFiles() {
        if (restoringSession) {
            SwingUtilities.invokeLater(this::applyPersistedUIState);
            return;
        }
        onUserFilterChanged();
    }

    /**
     * 用户主动切换类型复选框或点击“刷新总览”时触发的筛选逻辑
     */
    private void onUserFilterChanged() {
        if (scannedFiles.isEmpty()) {
            listModel.clear();
            displayedFilesSnapshot.clear();
            saveFinderSessionAsync();
            return;
        }

        if (scannedFiles.size() <= 10000) {
            List<File> matched = computeFilteredFiles(null);
            listModel.clear();
            listModel.addAll(matched);
            if (!spinnerLabel.isSpinning()) {
                statusLabel.setText(" 筛选完成。当前显示： " + matched.size() + " / " + scannedFiles.size() + " 项");
            }
            updateSnapshotFromUI(matched);
            saveFinderSessionAsync();
        } else {
            spinnerLabel.startSpinning();
            statusLabel.setText(" 正在筛选 " + scannedFiles.size() + " 个项目...");
            Thread.ofVirtual().name("Finder-Filter-Worker").start(() -> {
                List<File> matched = computeFilteredFiles(null);
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    listModel.addAll(matched);
                    spinnerLabel.stopSpinning();
                    statusLabel.setText(" 筛选完成。当前显示： " + matched.size() + " / " + scannedFiles.size() + " 项");
                    updateSnapshotFromUI(matched);
                    saveFinderSessionAsync();
                });
            });
        }
    }

    private void quickExtract() {
        if (scannedFiles.isEmpty()) {
            listModel.clear();
            displayedFilesSnapshot.clear();
            statusLabel.setText(" 列表为空，请先扫描文件夹");
            saveFinderSessionAsync();
            return;
        }

        String[] rawRules = rulesTextArea.getText().split("\n");
        List<String> validRules = new ArrayList<>();
        for (String r : rawRules) {
            if (!r.trim().isEmpty()) {
                validRules.add(r.trim().toLowerCase());
            }
        }

        spinnerLabel.startSpinning();
        statusLabel.setText(" 正在按规则提取关键项...");

        Thread.ofVirtual().name("Finder-Extract-Worker").start(() -> {
            List<File> matched = computeFilteredFiles(validRules);
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                listModel.addAll(matched);
                spinnerLabel.stopSpinning();
                statusLabel.setText(" 关键项提取完成。总计找到匹配： " + matched.size() + " 项");

                updateSnapshotFromUI(matched);
                saveFinderSessionAsync();
            });
        });
    }

    /**
     * 在后台计算过滤结果，基于内存级 dirFlagCache 避免磁盘 I/O
     */
    private List<File> computeFilteredFiles(List<String> keywordRules) {
        List<File> matched = new ArrayList<>();

        boolean folderOnly = chkFolderOnly.isSelected();
        boolean showWord = chkWord.isSelected();
        boolean showExcel = chkExcel.isSelected();
        boolean showPdf = chkPdf.isSelected();
        boolean showOther = chkOther.isSelected();

        for (File f : scannedFiles) {
            String name = f.getName();
            String lowerName = name.toLowerCase();

            boolean isDir = isDirectoryCached(f);

            boolean typeMatch = false;
            if (folderOnly) {
                if (isDir) typeMatch = true;
            } else {
                if (isDir) continue;
                if (showWord && (lowerName.endsWith(".doc") || lowerName.endsWith(".docx"))) typeMatch = true;
                else if (showExcel && (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv"))) typeMatch = true;
                else if (showPdf && lowerName.endsWith(".pdf")) typeMatch = true;
                else if (showOther && !(lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                        lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") ||
                        lowerName.endsWith(".csv") || lowerName.endsWith(".pdf"))) {
                    typeMatch = true;
                }
            }

            if (!typeMatch) continue;

            if (keywordRules != null) {
                boolean ruleHit = false;
                for (String r : keywordRules) {
                    if (lowerName.contains(r)) {
                        ruleHit = true;
                        break;
                    }
                }
                if (!ruleHit) continue;
            }

            matched.add(f);
        }
        return matched;
    }

    private void resetFilters() {
        chkWord.setSelected(true);
        chkExcel.setSelected(true);
        chkPdf.setSelected(true);
        chkOther.setSelected(true);
        chkFolderOnly.setSelected(false);
        chkWord.setEnabled(true);
        chkExcel.setEnabled(true);
        chkPdf.setEnabled(true);
        chkOther.setEnabled(true);
    }

    private String truncateText(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox chk = new JCheckBox(text);
        chk.setOpaque(false);
        chk.setForeground(Color.WHITE);
        chk.setFocusPainted(false);
        chk.setFont(FontUtils.getChineseFont(Font.PLAIN, 13));
        chk.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return chk;
    }

    // =========================================================================
    // ✨ 组件 1：左下角丝滑旋转进度小圆圈
    // =========================================================================
    private static class SpinnerLabel extends JComponent {
        private int angle = 0;
        private final javax.swing.Timer timer;

        public SpinnerLabel() {
            setPreferredSize(new Dimension(20, 18));
            setOpaque(false);
            timer = new javax.swing.Timer(25, e -> {
                angle = (angle + 14) % 360;
                repaint();
            });
        }

        public void startSpinning() {
            setVisible(true);
            if (!timer.isRunning()) {
                timer.start();
            }
        }

        public void stopSpinning() {
            timer.stop();
            setVisible(false);
        }

        public boolean isSpinning() {
            return timer.isRunning();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = 12;
            int x = 2;
            int y = (getHeight() - size) / 2;

            // 底层半透明暗轨圆环
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 35));
            g2.drawOval(x, y, size, size);

            // 顶部高亮青蓝色旋转圆弧
            g2.setColor(new Color(80, 195, 255));
            g2.draw(new Arc2D.Double(x, y, size, size, -angle, 110, Arc2D.OPEN));

            g2.dispose();
        }
    }

    // =========================================================================
    // ✨ 组件 2：零 HTML 开销 + 元数据按需懒加载缓存的极速渲染器
    // =========================================================================
    private static class FileMeta {
        final boolean isDir;
        final String title;
        final String subInfo;

        FileMeta(boolean isDir, String title, String subInfo) {
            this.isDir = isDir;
            this.title = title;
            this.subInfo = subInfo;
        }
    }

    private class FastFileCellRenderer extends JPanel implements ListCellRenderer<File> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        private final Font titleFont = FontUtils.getChineseFont(Font.BOLD, 14);
        private final Font subFont = FontUtils.getChineseFont(Font.PLAIN, 11);

        private String currentTitle = "";
        private String currentSub = "";
        private boolean selected = false;

        public FastFileCellRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends File> list, File f, int index, boolean isSelected, boolean cellHasFocus) {
            this.selected = isSelected;

            if (f != null) {
                // 仅对当前屏幕可见的十几行进行按需磁盘属性读取并缓存，翻页如丝般顺滑
                FileMeta meta = metaCache.get(f);
                if (meta == null) {
                    boolean isDir = isDirectoryCached(f);
                    String name = f.getName().isEmpty() ? f.getPath() : f.getName();

                    String type = isDir ? "文件夹" : "未知类型";
                    int dotIdx = name.lastIndexOf('.');
                    if (!isDir && dotIdx > 0 && dotIdx < name.length() - 1) {
                        type = name.substring(dotIdx + 1).toUpperCase() + " 文件";
                    }

                    String sizeStr = "--";
                    if (!isDir) {
                        long bytes = f.length();
                        if (bytes < 1024) sizeStr = bytes + " B";
                        else if (bytes < 1024 * 1024) sizeStr = String.format("%.2f KB", bytes / 1024.0);
                        else sizeStr = String.format("%.2f MB", bytes / (1024.0 * 1024.0));
                    }

                    long lastMod = f.lastModified();
                    String timeStr = lastMod > 0 ? sdf.format(new Date(lastMod)) : "--";
                    String sub = "类型: " + type + "   |   大小: " + sizeStr + "   |   修改: " + timeStr;

                    meta = new FileMeta(isDir, name, sub);
                    metaCache.put(f, meta);
                }
                this.currentTitle = meta.title;
                this.currentSub = meta.subInfo;
            } else {
                this.currentTitle = "";
                this.currentSub = "";
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();

            if (selected) {
                g2.setColor(new Color(255, 255, 255, 32));
                g2.fillRoundRect(4, 2, w - 8, h - 4, 12, 12);
            }

            // 绘制第一行：主文件名
            g2.setFont(titleFont);
            g2.setColor(selected ? Color.WHITE : new Color(230, 230, 230));
            g2.drawString(currentTitle, 18, 22);

            // 绘制第二行：副标题属性信息
            g2.setFont(subFont);
            g2.setColor(selected ? new Color(195, 195, 195) : new Color(136, 136, 136));
            g2.drawString(currentSub, 18, 41);

            g2.dispose();
        }
    }

    // 核心复用组件：极暗毛玻璃容器 (优化对比度版)
    class GlassPanel extends JPanel {
        public GlassPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(255, 255, 255, 10));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);
            g2.dispose();
        }
    }
}