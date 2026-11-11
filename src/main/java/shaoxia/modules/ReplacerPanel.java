package shaoxia.modules;

import shaoxia.Utils.FontUtils;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;

import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassPanel;
import shaoxia.Utils.GlassTextField;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档替换助手 ReplacerPanel - 强韧防御版
 * (全局统一极暗玻璃 + 完美拖拽穿透 + A红黄蓝三原色 + B黄名称替换 + C蓝全自定义排版 + D红清空日志)
 */
public class ReplacerPanel extends BackgroundPanel {
    // 保持原有字段名，100% 兼容 MainLauncher 跨重启状态持久化反射
    private JTextField[] inputs = new JTextField[6];
    private JTextField customKeyInput;

    // 前 5 行的自定义被替换词输入框与左侧 CardLayout 容器
    private final JTextField[] customKeyInputs = new JTextField[5];
    private final JPanel[] leftSlotPanels = new JPanel[5];
    private final CardLayout[] leftCardLayouts = new CardLayout[5];

    private final String[] placeholders = {
            "XXX项目", "PRJ-XXXXXX", "2026年XX月XX日", "XXXXXXXXXXX", "****@example.com"
    };

    // 默认警示文字常量，供初始化与 D 按钮清空日志时复用
    private static final String DEFAULT_WARNING_TEXT =
            "\n" +
                    "     >_ 请将整个 文件夹 或 单/多份文件 拖拽至此...\n" +
                    "     >_ [WARN] 核心引擎不支持 .doc 旧版文档！\n" +
                    "     >_ [FAIL] 强行解析将导致未知错误 (X_X) 后果自负。\n";

    // ✨ 统一三原色常量（红、黄、蓝）
    private static final Color PRIMARY_RED = new Color(255, 71, 87);
    private static final Color PRIMARY_YELLOW = new Color(255, 195, 18);
    private static final Color PRIMARY_BLUE = new Color(30, 144, 255);

    private JTextArea logArea;

    // A 按钮：纯黑模式状态标志
    private volatile boolean isBlackModeEnabled = true;

    // B 按钮：名称替换模式状态标志 (默认关闭，以保护原有文件结构)
    private volatile boolean isRenameEnabled = false;

    // C 按钮：6 项全自定义文本替换模式状态标志
    private volatile boolean isAllCustomMode = false;

    private volatile boolean isReplacing = false; // 防止替换过程中重复拖入
    private final MainLauncher parent;

    public ReplacerPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;

        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        setLayout(new BorderLayout());

        // 顶部导航：完全对标其他面板的嵌套结构与边缘距离参数
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTop.setOpaque(false);
        leftTop.add(backBtn);
        topBar.add(leftTop, BorderLayout.WEST);

        // 右上角指示灯组件接入 (包含 D红清空日志、C蓝全自定义、B黄名称替换、A红黄蓝纯黑模式)
        JPanel rightIndicators = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightIndicators.setOpaque(false);

        ClearLogIndicator clearLogIndicator = new ClearLogIndicator();
        CustomModeIndicator customModeIndicator = new CustomModeIndicator();
        RenameModeIndicator renameIndicator = new RenameModeIndicator();
        BlackModeIndicator blackModeIndicator = new BlackModeIndicator();

        rightIndicators.add(clearLogIndicator);   // D (红)：清空日志（保留默认警示文字）
        rightIndicators.add(customModeIndicator); // C (蓝)：6个全部修改自定义文本
        rightIndicators.add(renameIndicator);     // B (黄)：名称替换开关
        rightIndicators.add(blackModeIndicator);  // A (红黄蓝)：纯黑/彩色模式开关

        topBar.add(rightIndicators, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 主配置区
        JPanel p = new JPanel(new GridLayout(7, 1, 5, 2));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(25, 50, 5, 50));

        // 前 5 行：支持在「固定预设模式 (PRESET)」与「自定义文本模式 (CUSTOM)」之间丝滑切换
        for (int i = 0; i < 5; i++) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);

            leftCardLayouts[i] = new CardLayout();
            leftSlotPanels[i] = new JPanel(leftCardLayouts[i]);
            leftSlotPanels[i].setOpaque(false);
            leftSlotPanels[i].setPreferredSize(new Dimension(220, 30));

            // 卡片 1：默认固定占位符标签
            JLabel presetLabel = new JLabel((i + 1) + ". 修改 [" + placeholders[i] + "] 为: ");
            presetLabel.setForeground(Color.WHITE);
            leftSlotPanels[i].add(presetLabel, "PRESET");

            // 卡片 2：与第 6 行同款的自定义被替换词输入排版
            JPanel customLeftPanel = new JPanel(new BorderLayout(5, 0));
            customLeftPanel.setOpaque(false);
            JLabel lblStart = new JLabel((i + 1) + ". 修改 ");
            lblStart.setForeground(Color.WHITE);
            customLeftPanel.add(lblStart, BorderLayout.WEST);

            customKeyInputs[i] = new GlassTextField();
            customKeyInputs[i].setToolTipText("输入需要被替换的自定义内容");
            customLeftPanel.add(customKeyInputs[i], BorderLayout.CENTER);

            JLabel lblEnd = new JLabel(" 为: ");
            lblEnd.setForeground(Color.WHITE);
            customLeftPanel.add(lblEnd, BorderLayout.EAST);

            leftSlotPanels[i].add(customLeftPanel, "CUSTOM");

            row.add(leftSlotPanels[i], BorderLayout.WEST);

            inputs[i] = new GlassTextField();
            row.add(inputs[i], BorderLayout.CENTER);

            p.add(row);
        }

        // 第 6 行：原生自定义替换行
        JPanel row6 = new JPanel(new BorderLayout(10, 0));
        row6.setOpaque(false);
        JPanel leftPanel = new JPanel(new BorderLayout(5, 0));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(220, 30));
        JLabel label6 = new JLabel("6. 修改 ");
        label6.setForeground(Color.WHITE);
        leftPanel.add(label6, BorderLayout.WEST);

        customKeyInput = new GlassTextField();
        customKeyInput.setToolTipText("输入需要被替换的自定义内容");
        leftPanel.add(customKeyInput, BorderLayout.CENTER);

        JLabel label6End = new JLabel(" 为: ");
        label6End.setForeground(Color.WHITE);
        leftPanel.add(label6End, BorderLayout.EAST);
        row6.add(leftPanel, BorderLayout.WEST);

        inputs[5] = new GlassTextField();
        row6.add(inputs[5], BorderLayout.CENTER);
        p.add(row6);

        // 透明日志区本体
        logArea = new JTextArea(DEFAULT_WARNING_TEXT);
        logArea.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
        logArea.setUI(new javax.swing.plaf.basic.BasicTextAreaUI());
        logArea.setEditable(false);
        logArea.setOpaque(false);
        logArea.setBackground(new Color(0, 0, 0, 0));
        logArea.setForeground(Color.WHITE);
        logArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        logArea.setTransferHandler(null);

        JScrollPane scroll = new JScrollPane(logArea) {
            @Override
            public boolean isOpaque() { return false; }
        };
        scroll.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
        scroll.getViewport().setUI(new javax.swing.plaf.basic.BasicViewportUI());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBackground(new Color(0, 0, 0, 0));
        scroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        scroll.setBorder(null);
        scroll.setViewportBorder(null);

        GlassPanel logWrapper = new GlassPanel(new BorderLayout());
        logWrapper.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));
        logWrapper.add(scroll, BorderLayout.CENTER);

        DropTargetAdapter dropAdapter = new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // ✨ 保持空实现，避免高频重复调用
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                if (isReplacing) {
                    dtde.rejectDrop();
                    return;
                }
                // ✨ 收到 drop 瞬间立即上锁！防止在 80ms 延时期间或确认弹窗未关闭前触发二次拖拽
                isReplacing = true;

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dtde.dropComplete(true);

                    Map<String, String> map = new LinkedHashMap<>();
                    for (int i = 0; i < 5; i++) {
                        String key = isAllCustomMode ? customKeyInputs[i].getText().trim() : placeholders[i];
                        String val = inputs[i].getText().trim();
                        if (!key.isEmpty() && !val.isEmpty()) {
                            map.put(key, val);
                        }
                    }
                    String customKey = customKeyInput.getText().trim();
                    String customVal = inputs[5].getText().trim();
                    if (!customKey.isEmpty() && !customVal.isEmpty()) {
                        map.put(customKey, customVal);
                    }

                    if (map.isEmpty()) {
                        isReplacing = false; // 校验未通过，立即释放锁
                        log(isAllCustomMode ? "请至少输入一组完整的[被替换内容]与[替换内容]！" : "请至少输入一个替换内容！");
                        return;
                    }

                    // 使用非阻塞单次 Timer (延迟 80ms) 彻底脱离 OLE 拖拽事件栈，待系统释放拖拽锁后再弹窗
                    Timer dropTimer = new Timer(80, evt -> {
                        Window win = SwingUtilities.getWindowAncestor(ReplacerPanel.this);
                        if (win != null) {
                            win.toFront();
                        }

                        String msg = "您好呀 (,,•́ . •̀,,) 检测到您拖入了 " + files.size() + " 个项目（若是文件夹则包含内部所有文件）。\n\n确定要进行批量内容替换吗？\n(此操作不可逆，请记得提前做好文件备份哦~ )";

                        int confirm = JOptionPane.showConfirmDialog(
                                ReplacerPanel.this,
                                msg,
                                "批量替换确认",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                        );

                        if (confirm == JOptionPane.YES_OPTION) {
                            log(">>> 开始执行批量替换任务...");
                            Thread worker = Thread.ofVirtual().name("Replacer-Worker-Thread").start(() -> {
                                try {
                                    for (File f : files) {
                                        recursiveScan(f, map);
                                    }
                                    log(">>> 任务全部完成！");
                                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                            ReplacerPanel.this,
                                            "所有文件处理完成！"
                                    ));
                                } finally {
                                    isReplacing = false; // 虚拟线程彻底结束后释放锁
                                }
                            });
                        } else {
                            isReplacing = false; // 用户点击取消或关闭弹窗时，立即释放锁
                            log("--- 已取消本次拖入操作 ---");
                        }
                    });
                    dropTimer.setRepeats(false);
                    dropTimer.start();

                } catch (Exception e) {
                    isReplacing = false; // 异常兜底释放锁
                    log("拖拽异常: " + e.getMessage());
                    try { dtde.dropComplete(false); } catch (Exception ignored) {}
                }
            }
        };

        // 仅在实际可视顶层组件 logArea 上注册主 DropTarget（logWrapper 仅作外层 15px 边距兜底，移除中间层 scroll 的冗余注册）
        new DropTarget(logArea, DnDConstants.ACTION_COPY, dropAdapter);
        new DropTarget(logWrapper, DnDConstants.ACTION_COPY, dropAdapter);

        JPanel centerContainer = new JPanel(new BorderLayout(10, 5));
        centerContainer.setOpaque(false);
        centerContainer.add(p, BorderLayout.NORTH);
        centerContainer.add(logWrapper, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);
    }

    // 切换前 5 行排版模式（预设模式 vs 6项全自定义模式）
    private void updateRowsLayoutMode() {
        String targetCard = isAllCustomMode ? "CUSTOM" : "PRESET";
        for (int i = 0; i < 5; i++) {
            leftCardLayouts[i].show(leftSlotPanels[i], targetCard);
        }
        revalidate();
        repaint();
    }

    private void recursiveScan(File file, Map<String, String> map) {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) for (File c : list) recursiveScan(c, map);
        } else {
            processFile(file, map);
        }

        if (isRenameEnabled) {
            renameFileOrDir(file, map);
        }
    }

    private void renameFileOrDir(File file, Map<String, String> map) {
        String originalName = file.getName();
        String newName = originalName;

        for (Map.Entry<String, String> entry : map.entrySet()) {
            newName = newName.replace(entry.getKey(), entry.getValue());
        }

        if (!newName.equals(originalName)) {
            File newFile = new File(file.getParent(), newName);

            if (newFile.exists()) {
                int count = 1;
                String base = newName;
                String ext = "";
                if (file.isFile() && newName.contains(".")) {
                    int lastDot = newName.lastIndexOf(".");
                    base = newName.substring(0, lastDot);
                    ext = newName.substring(lastDot);
                }
                while (newFile.exists()) {
                    newFile = new File(file.getParent(), base + "_复件" + count + ext);
                    count++;
                }
            }

            if (file.renameTo(newFile)) {
                log("[重命名成功] " + originalName + "  -->  " + newFile.getName());
            } else {
                log("[重命名失败] 文件可能被占用或权限不足: " + originalName);
            }
        }
    }

    private void processFile(File src, Map<String, String> map) {
        String name = src.getName().toLowerCase();
        File temp = new File(src.getAbsolutePath() + ".tmp");
        boolean success = false;

        try {
            try (InputStream is = FileMagic.prepareToCheckMagic(new FileInputStream(src))) {
                FileMagic fm = FileMagic.valueOf(is);

                if (fm == FileMagic.OOXML && name.endsWith(".docx")) {
                    try (XWPFDocument doc = new XWPFDocument(is)) {
                        for (XWPFParagraph p : doc.getParagraphs()) replaceInDocx(p, map);
                        for (XWPFTable t : doc.getTables()) {
                            for (XWPFTableRow r : t.getRows()) {
                                for (XWPFTableCell c : r.getTableCells()) {
                                    for (XWPFParagraph p : c.getParagraphs()) replaceInDocx(p, map);
                                }
                            }
                        }
                        try (FileOutputStream fos = new FileOutputStream(temp)) { doc.write(fos); }
                        success = true;
                    }
                }
                else if (fm == FileMagic.OOXML && name.endsWith(".pptx")) {
                    try (XMLSlideShow ppt = new XMLSlideShow(is)) {
                        for (XSLFSlide slide : ppt.getSlides()) {
                            for (XSLFShape shape : slide.getShapes()) {
                                if (shape instanceof XSLFTextShape) {
                                    replaceInPptx((XSLFTextShape) shape, map);
                                }
                                else if (shape instanceof XSLFTable) {
                                    XSLFTable table = (XSLFTable) shape;
                                    for (XSLFTableRow row : table.getRows()) {
                                        for (XSLFTableCell cell : row.getCells()) {
                                            replaceInPptx(cell, map);
                                        }
                                    }
                                }
                            }
                        }
                        try (FileOutputStream fos = new FileOutputStream(temp)) { ppt.write(fos); }
                        success = true;
                    }
                }
                else if (fm == FileMagic.OLE2 && name.endsWith(".doc")) {
                    try (HWPFDocument doc = new HWPFDocument(is)) {
                        org.apache.poi.hwpf.usermodel.Range r = doc.getRange();
                        for (Map.Entry<String, String> e : map.entrySet()) {
                            r.replaceText(e.getKey(), e.getValue());
                        }
                        try (FileOutputStream fos = new FileOutputStream(temp)) { doc.write(fos); }
                        success = true;
                    }
                }
                else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                    boolean isOoxml = (fm == FileMagic.OOXML) || name.endsWith(".xlsx");
                    try (Workbook wb = isOoxml
                            ? new org.apache.poi.xssf.usermodel.XSSFWorkbook(is)
                            : new org.apache.poi.hssf.usermodel.HSSFWorkbook(is)) {
                        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                            for (Row r : wb.getSheetAt(i)) {
                                for (Cell c : r) {
                                    if (c.getCellType() == CellType.STRING) {
                                        String v = c.getStringCellValue();
                                        for (Map.Entry<String, String> e : map.entrySet()) {
                                            v = v.replace(e.getKey(), e.getValue());
                                        }
                                        c.setCellValue(v);
                                    }
                                }
                            }
                        }
                        try (FileOutputStream fos = new FileOutputStream(temp)) { wb.write(fos); }
                        success = true;
                    }
                }
            }

            if (success && temp.exists()) {
                Files.copy(temp.toPath(), src.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("[处理成功] " + src.getName());
            }

        } catch (Throwable t) {
            log("[报错跳过] " + src.getName() + " -> " + t.toString());
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private void replaceInPptx(XSLFTextShape shape, Map<String, String> map) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) continue;

            String pText = "";
            for (XSLFTextRun run : runs) pText += run.getRawText();

            boolean hit = false;
            for (String key : map.keySet()) {
                if (pText.contains(key)) { hit = true; break; }
            }

            if (hit) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    pText = pText.replace(entry.getKey(), entry.getValue());
                }

                XSLFTextRun firstRun = runs.get(0);
                String text = pText;

                for (int i = runs.size() - 1; i >= 1; i--) {
                    runs.get(i).setText("");
                }
                firstRun.setText(text);

                if (isBlackModeEnabled) {
                    firstRun.setFontColor(Color.BLACK);
                }
            }
        }
    }

    private void replaceInDocx(XWPFParagraph p, Map<String, String> map) {
        String pText = p.getText();
        if (pText == null || pText.isEmpty()) return;
        boolean hit = false;
        for (String key : map.keySet()) {
            if (pText.contains(key)) { hit = true; break; }
        }
        if (hit) {
            String newText = pText;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                newText = newText.replace(entry.getKey(), entry.getValue());
            }
            List<XWPFRun> runs = p.getRuns();
            if (!runs.isEmpty()) {
                XWPFRun origin = runs.get(0);
                String fontFamily = origin.getFontFamily();
                int fontSize = origin.getFontSize();
                String color = origin.getColor();
                boolean isBold = origin.isBold();
                boolean isItalic = origin.isItalic();
                UnderlinePatterns underline = origin.getUnderline();
                for (int i = runs.size() - 1; i >= 0; i--) p.removeRun(i);
                XWPFRun newRun = p.createRun();
                newRun.setText(newText);
                if (fontFamily != null) newRun.setFontFamily(fontFamily);
                if (fontSize != -1) newRun.setFontSize(fontSize);

                if (isBlackModeEnabled) {
                    newRun.setColor("000000");
                } else if (color != null) {
                    newRun.setColor(color);
                }

                newRun.setBold(isBold);
                newRun.setItalic(isItalic);
                newRun.setUnderline(underline);
                newRun.getCTR().addNewRPr().addNewRFonts().setEastAsia(fontFamily);
            }
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * ✨ 核心视觉修复：基于 GlyphVector 真实字形像素边界（VisualBounds）精确居中绘制徽章字母
     * 彻底解决字母 "C" 因右侧开口字距留白导致的偏左、偏下问题
     */
    /**
     * ✨ 完美视觉居中：基于 GlyphVector 并加入像素级微调偏移，彻底解决视觉偏差
     */
    private void drawCenteredBadgeChar(Graphics2D g2, String ch, int cx, int cy) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = FontUtils.getEnglishFont(Font.BOLD, 13);
        g2.setFont(font);
        FontRenderContext frc = g2.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, ch);
        Rectangle2D visualBounds = gv.getVisualBounds();

        // 🎯 核心修改：在数学中心的基础上，向右微调 +0.5 像素（消除英文字母右侧空白造成的视觉偏左），垂直方向保持精准居中
        float x = (float) (cx - visualBounds.getCenterX() + 0.5);
        float y = (float) (cy - visualBounds.getCenterY());
        g2.drawGlyphVector(gv, x, y);
    }

    // =========================================================================
    // ✨ D 按钮（红色）：清空全部运行日志（保留默认警示文字 + 删除前弹窗确认）
    // =========================================================================
    class ClearLogIndicator extends JComponent {
        private boolean isHover = false;

        public ClearLogIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (DEFAULT_WARNING_TEXT.equals(logArea.getText())) {
                        JOptionPane.showMessageDialog(
                                ReplacerPanel.this,
                                "当前没有额外的运行日志需要清理哦~",
                                "提示",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        return;
                    }

                    int confirm = JOptionPane.showConfirmDialog(
                            ReplacerPanel.this,
                            "确定要清空全部运行日志吗？\n（顶部的默认警示文字将会保留）",
                            "清空日志确认",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );

                    if (confirm == JOptionPane.YES_OPTION) {
                        logArea.setText(DEFAULT_WARNING_TEXT);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                        if (parent != null) {
                            parent.saveWorkspaceStateAsync();
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int ringRadius = 15;
            int ringDiameter = ringRadius * 2;
            int ringX = cx - ringRadius;
            int ringY = cy - ringRadius;

            if (isHover) {
                // 悬停激活态：三原色之「正红色」光环
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(PRIMARY_RED);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(40, 40, 40));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            } else {
                // 默认常态：暗灰色闭合环
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(80, 80, 80));
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(15, 15, 15));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            }

            // 几何居中绘制字符 "D"
            g2.setColor(isHover ? Color.WHITE : new Color(150, 150, 150));
            drawCenteredBadgeChar(g2, "D", cx, cy);
            g2.dispose();
        }
    }

    // =========================================================================
    // ✨ C 按钮（蓝色）：切换 6 个全部修改自定义文本模式
    // =========================================================================
    class CustomModeIndicator extends JComponent {
        public CustomModeIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    isAllCustomMode = !isAllCustomMode;
                    updateRowsLayoutMode();
                    repaint();
                    if (parent != null) {
                        parent.saveWorkspaceStateAsync();
                    }
                    log(isAllCustomMode ?
                            ">>> [设置已变] 全自定义模式已开启：全部 6 行均可自由输入被替换文本。" :
                            ">>> [设置已变] 预设模板模式已恢复：前 5 行使用固定占位符替换。");
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int ringRadius = 15;
            int ringDiameter = ringRadius * 2;
            int ringX = cx - ringRadius;
            int ringY = cy - ringRadius;

            if (isAllCustomMode) {
                // 开启时：三原色之「正蓝色」闭合环
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(PRIMARY_BLUE);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(40, 40, 40));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            } else {
                // 关闭时：暗灰色闭合环
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(80, 80, 80));
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(15, 15, 15));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            }

            // 几何居中绘制字符 "C"（彻底消除偏左偏下）
            g2.setColor(isAllCustomMode ? Color.WHITE : new Color(150, 150, 150));
            drawCenteredBadgeChar(g2, "C", cx, cy);
            g2.dispose();
        }
    }

    // =========================================================================
    // ✨ B 按钮（黄色）：名称替换模式右上角指示灯组件
    // =========================================================================
    class RenameModeIndicator extends JComponent {
        public RenameModeIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    isRenameEnabled = !isRenameEnabled;
                    repaint();
                    if (parent != null) {
                        parent.saveWorkspaceStateAsync();
                    }
                    log(isRenameEnabled ?
                            ">>> [设置已变] 名称替换已开启：将同时替换文件夹和文件的名称。" :
                            ">>> [设置已变] 名称替换已关闭：仅替换文件内容。");
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int ringRadius = 15;
            int ringDiameter = ringRadius * 2;
            int ringX = cx - ringRadius;
            int ringY = cy - ringRadius;

            if (isRenameEnabled) {
                // 开启时：三原色之「明黄色」闭合环
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(PRIMARY_YELLOW);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(40, 40, 40));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            } else {
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(80, 80, 80));
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(15, 15, 15));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            }

            // 几何居中绘制字符 "B"
            g2.setColor(isRenameEnabled ? Color.WHITE : new Color(150, 150, 150));
            drawCenteredBadgeChar(g2, "B", cx, cy);
            g2.dispose();
        }
    }

    // =========================================================================
    // ✨ A 按钮（红、黄、蓝 三原色环绕）：纯黑/彩色模式右上角指示灯组件
    // =========================================================================
    class BlackModeIndicator extends JComponent {
        public BlackModeIndicator() {
            setPreferredSize(new Dimension(36, 36));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    isBlackModeEnabled = !isBlackModeEnabled;
                    repaint();
                    if (parent != null) {
                        parent.saveWorkspaceStateAsync();
                    }
                    log(isBlackModeEnabled ?
                            ">>> [设置已变] 纯黑模式已开启：新替换的文本颜色强制为黑色。" :
                            ">>> [设置已变] 彩色模式已开启：新替换的文本保留原色彩。");
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            int ringRadius = 15;
            int ringDiameter = ringRadius * 2;
            int ringX = cx - ringRadius;
            int ringY = cy - ringRadius;

            if (!isBlackModeEnabled) {
                // 关闭纯黑 (即彩色模式)：三原色 (红、黄、蓝) 环绕
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // 顶部：红色弧
                g2.setColor(PRIMARY_RED);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 95, 110);

                // 左下：黄色弧
                g2.setColor(PRIMARY_YELLOW);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 215, 110);

                // 右下：蓝色弧
                g2.setColor(PRIMARY_BLUE);
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 335, 110);

                // 彩色模式内核
                g2.setColor(new Color(40, 40, 40));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            } else {
                // 默认/开启纯黑模式：极简暗色闭合灰环
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(80, 80, 80));
                g2.drawArc(ringX, ringY, ringDiameter, ringDiameter, 0, 360);

                g2.setColor(new Color(15, 15, 15));
                g2.fillOval(cx - 10, cy - 10, 20, 20);
            }

            // 绘制内部徽章：代表文字排版的 "A" 字符
            g2.setColor(isBlackModeEnabled ? new Color(150, 150, 150) : Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int[] aX = {cx - 4, cx, cx + 4};
            int[] aY = {cy + 4, cy - 5, cy + 4};
            g2.drawPolyline(aX, aY, 3);
            g2.drawLine(cx - 2, cy + 1, cx + 2, cy + 1);

            g2.dispose();
        }
    }
}