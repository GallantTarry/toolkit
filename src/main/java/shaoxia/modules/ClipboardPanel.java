package shaoxia.modules;

import org.json.JSONArray;
import org.json.JSONObject;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassComboBox;
import shaoxia.Utils.GlassTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 极简九宫格粘贴板 ClipboardPanel
 * 零功能按钮设计：点击即焦点，Ctrl+V 直接贴入自动保存，Ctrl+C 免全选整格复制，双击卡片放大/还原，完美支持 Emoji 防乱码
 */
public class ClipboardPanel extends BackgroundPanel {

    private static final int SLOT_COUNT = 9;

    private final File storageFile;
    private final SlotCard[] slots = new SlotCard[SLOT_COUNT];
    private JPanel gridContainer;
    private JLabel statusLabel;

    // 当前是否处于单格放大模式（-1 表示九宫格模式，0~8 表示当前放大的槽位索引）
    private int zoomedSlotIndex = -1;

    // 标记本地磁盘数据是否已加载完毕，防止初始化阶段或外部反射冲掉磁盘数据
    private boolean isDiskLoaded = false;
    private final Timer debounceSaveTimer;

    private final Font badgeFont;
    private final Font editorFont;
    private final Font hintFont;
    private final Font emojiFont;

    // ================= 保留供 MainLauncher 反射存档兼容的占位字段（不在界面显示） =================
    private boolean isMonitoring = false;
    private GlassTextField searchField = new GlassTextField();
    private GlassComboBox<String> categoryCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(new String[]{"默认"}));
    private DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> clipList = new JList<>(listModel);
    private JTextArea detailArea = new JTextArea();

    public ClipboardPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout(10, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // 初始化字体：中文字体 + 独立探测系统 Emoji 字体
        this.badgeFont = createCompositeFont(FontUtils.getChineseFont(Font.BOLD, 13));
        this.editorFont = createCompositeFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        this.hintFont = createCompositeFont(FontUtils.getChineseFont(Font.PLAIN, 13));
        this.emojiFont = detectSystemEmojiFont(14);

        // 存储在用户主目录 .toolkit 文件夹下，跨重启永久保留
        File dataDir = new File(System.getProperty("user.home"), ".toolkit");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.storageFile = new File(dataDir, "clipboard_history.json");

        // 防抖自动保存定时器（输入或粘贴后 250ms 自动异步落盘）
        this.debounceSaveTimer = new Timer(250, e -> saveToDiskAsync());
        this.debounceSaveTimer.setRepeats(false);

        initTopBar(parent);
        initNineGridArea();
        initBottomStatusBar();
        initGlobalShortcuts();

        // 启动时从本地磁盘加载 9 个槽位的数据
        loadFromDisk();
    }

    /**
     * 将自定义字体注册并包装为支持系统回退的复合字体
     */
    private static Font createCompositeFont(Font baseFont) {
        if (baseFont == null) {
            return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        }
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(baseFont);
            return StyleContext.getDefaultStyleContext()
                    .getFont(baseFont.getFamily(), baseFont.getStyle(), baseFont.getSize());
        } catch (Exception e) {
            return baseFont;
        }
    }

    /**
     * 自动探测当前操作系统的原生 Emoji 字体（Windows / macOS / Linux）
     */
    private static Font detectSystemEmojiFont(int size) {
        String[] candidates = {
                "Segoe UI Emoji",      // Windows 10 / 11
                "Apple Color Emoji",   // macOS
                "Noto Color Emoji",    // Linux
                "Segoe UI Symbol",     // Windows 兼容符号
                Font.SANS_SERIF
        };
        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String candidate : candidates) {
            for (String name : installed) {
                if (name.equalsIgnoreCase(candidate)) {
                    return new Font(name, Font.PLAIN, size);
                }
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    /**
     * 判断 Unicode 码点是否属于 Emoji 或特殊符号范围
     */
    private static boolean isEmojiCodePoint(int cp) {
        if (Character.isSupplementaryCodePoint(cp)) return true;
        if (cp >= 0x2600 && cp <= 0x27BF) return true;
        if (cp >= 0x2300 && cp <= 0x23FF) return true;
        if (cp >= 0x2B00 && cp <= 0x2BFF) return true;
        if (cp >= 0x2190 && cp <= 0x21FF) return true;
        return cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || cp == 0x00A9 || cp == 0x00AE || cp == 0x2122;
    }

    /**
     * 顶部栏：极简布局，仅保留返回按钮，保持界面干净清晰
     */
    private void initTopBar(MainLauncher parent) {
        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(2, 10, 2, 10));

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());

        topRow.add(backBtn, BorderLayout.WEST);

        add(topRow, BorderLayout.NORTH);
    }

    /**
     * 主体区域：3×3 九宫格独立毛玻璃粘贴板
     */
    private void initNineGridArea() {
        gridContainer = new JPanel(new GridLayout(3, 3, 12, 12));
        gridContainer.setOpaque(false);
        gridContainer.setBorder(new EmptyBorder(2, 10, 2, 10));

        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = new SlotCard(i);
            gridContainer.add(slots[i]);
        }

        add(gridContainer, BorderLayout.CENTER);
    }

    /**
     * 左下角状态展示栏：集中展示提示与操作状态，保持界面干净清晰
     */
    private void initBottomStatusBar() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(2, 10, 2, 10));

        statusLabel = new JLabel("九宫格粘贴板  |  双击卡片放大/还原  |  支持 Emoji 自动保存");
        statusLabel.setForeground(new Color(180, 180, 180));
        statusLabel.setFont(createCompositeFont(FontUtils.getChineseFont(Font.PLAIN, 12)));

        bottomBar.add(statusLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    /**
     * 全局快捷键支持：Alt + 1~9 快速聚焦，Esc 退出放大
     */
    private void initGlobalShortcuts() {
        InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = getActionMap();

        for (int i = 0; i < SLOT_COUNT; i++) {
            final int slotIdx = i;
            String key = "focus_slot_" + (i + 1);
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK), key);
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1 + i, InputEvent.ALT_DOWN_MASK), key);
            am.put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    slots[slotIdx].focusAndSelectAll();
                }
            });
        }

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit_zoom");
        am.put("exit_zoom", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (zoomedSlotIndex != -1) {
                    toggleZoomSlot(zoomedSlotIndex);
                }
            }
        });
    }

    /**
     * 切换单格放大 / 九宫格平铺模式
     */
    private void toggleZoomSlot(int slotIndex) {
        gridContainer.removeAll();
        if (zoomedSlotIndex == slotIndex) {
            zoomedSlotIndex = -1;
            gridContainer.setLayout(new GridLayout(3, 3, 12, 12));
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i].setZoomed(false);
                gridContainer.add(slots[i]);
            }
            statusLabel.setText("九宫格粘贴板  |  双击卡片放大/还原  |  支持 Emoji 自动保存");
        } else {
            zoomedSlotIndex = slotIndex;
            gridContainer.setLayout(new BorderLayout());
            slots[slotIndex].setZoomed(true);
            gridContainer.add(slots[slotIndex], BorderLayout.CENTER);
            statusLabel.setText("正在放大查看 #" + (slotIndex + 1) + " 号粘贴板（双击卡片或按 Esc 键还原九宫格）");
        }
        gridContainer.revalidate();
        gridContainer.repaint();
        SwingUtilities.invokeLater(() -> slots[slotIndex].textArea.requestFocusInWindow());
    }

    // ================= 本地磁盘自动持久化 =================

    private void scheduleAutoSave() {
        if (!isDiskLoaded) return;
        debounceSaveTimer.restart();
    }

    private void loadFromDisk() {
        Thread.ofVirtual().name("Clipboard-DiskLoader").start(() -> {
            String[] loadedTexts = new String[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) {
                loadedTexts[i] = "";
            }

            if (storageFile.exists()) {
                try {
                    String jsonStr = Files.readString(storageFile.toPath(), StandardCharsets.UTF_8);
                    JSONArray array = new JSONArray(jsonStr);
                    int limit = Math.min(array.length(), SLOT_COUNT);
                    for (int i = 0; i < limit; i++) {
                        Object elem = array.get(i);
                        if (elem instanceof JSONObject) {
                            loadedTexts[i] = ((JSONObject) elem).optString("content", "");
                        } else if (elem != null && elem != JSONObject.NULL) {
                            loadedTexts[i] = elem.toString();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("读取九宫格粘贴板记录失败: " + e.getMessage());
                }
            }

            SwingUtilities.invokeLater(() -> {
                isDiskLoaded = false;
                int nonEmptyCount = 0;
                for (int i = 0; i < SLOT_COUNT; i++) {
                    slots[i].setTextSafely(loadedTexts[i]);
                    if (!loadedTexts[i].trim().isEmpty()) {
                        nonEmptyCount++;
                    }
                }
                isDiskLoaded = true;
                statusLabel.setText(" 已就绪（当前 " + nonEmptyCount + " / 9 个粘贴板存有内容，自动保存已启用）");
            });
        });
    }

    private void saveToDiskAsync() {
        if (!isDiskLoaded) return;
        final String[] snapshot = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            snapshot[i] = slots[i].getRawText();
        }

        Thread.ofVirtual().name("Clipboard-DiskSaver").start(() -> {
            try {
                JSONArray array = new JSONArray();
                for (String s : snapshot) {
                    array.put(s == null ? "" : s);
                }
                Files.writeString(storageFile.toPath(), array.toString(2), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("保存九宫格粘贴板失败: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // 单个粘贴板卡片组件（单标签顶栏防重叠 + 独立 Emoji 混合渲染引擎）
    // =========================================================================
    private class SlotCard extends JPanel {
        private final int index;
        private final JPanel headerBar;
        private final JLabel headerLabel;
        private final JTextArea textArea;

        private boolean hovered = false;
        private boolean focused = false;
        private boolean zoomed = false;

        // 0 = 无闪烁, 1 = 复制成功(翠绿), 2 = 粘贴成功(天青)
        private int flashState = 0;
        private String flashText = "";
        private final Timer flashResetTimer;

        private boolean internalUpdating = false;

        public SlotCard(int index) {
            super(new BorderLayout(0, 4));
            this.index = index;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 10, 12));

            this.flashResetTimer = new Timer(650, e -> {
                flashState = 0;
                flashText = "";
                updateHeaderDisplay();
                repaint();
            });
            this.flashResetTimer.setRepeats(false);

            headerBar = new JPanel(new BorderLayout());
            headerBar.setOpaque(false);
            headerBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            headerLabel = new JLabel("#" + (index + 1));
            headerLabel.setFont(badgeFont);
            headerLabel.setForeground(new Color(190, 190, 190));

            headerBar.add(headerLabel, BorderLayout.CENTER);

            MouseAdapter headerMouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (e.getClickCount() == 2) {
                            e.consume();
                            toggleZoomSlot(index);
                        } else if (e.getClickCount() == 1) {
                            focusAndSelectAll();
                        }
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHovered(false);
                }
            };
            headerBar.addMouseListener(headerMouseHandler);
            headerLabel.addMouseListener(headerMouseHandler);

            textArea = new JTextArea() {
                @Override
                public boolean isOpaque() {
                    return false;
                }

                @Override
                public void setText(String t) {
                    if (!internalUpdating && !isDiskLoaded && (t == null || t.isEmpty())) {
                        return;
                    }
                    super.setText(t);
                }

                @Override
                public void copy() {
                    if (getSelectionStart() == getSelectionEnd()) {
                        String all = getText();
                        if (all != null && !all.isEmpty()) {
                            copyWholeSlotToClipboard(all);
                        } else {
                            statusLabel.setText(" #" + (index + 1) + " 号粘贴板当前为空");
                        }
                    } else {
                        super.copy();
                        triggerFlash(1, "✓ 已复制");
                        statusLabel.setText(" 已从 #" + (index + 1) + " 号粘贴板复制选中文本");
                    }
                }

                @Override
                public void paste() {
                    String clip = readSystemClipboardText();
                    super.paste();
                    if (!clip.isEmpty()) {
                        triggerFlash(2, "✓ 已粘贴");
                        statusLabel.setText(" 已粘贴到 #" + (index + 1) + " 号粘贴板并自动保存");
                    }
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    super.paintComponent(g);

                    if (getText().isEmpty() && getWidth() >= 60 && getHeight() >= 28) {
                        g2.setFont(editorFont);
                        g2.setColor(focused ? new Color(255, 255, 255, 70) : new Color(255, 255, 255, 35));
                        FontMetrics fm = g2.getFontMetrics();
                        String watermark = "Ctrl+V 粘贴";
                        if (fm.stringWidth(watermark) > getWidth() - 12) {
                            watermark = "Ctrl+V";
                        }
                        if (fm.stringWidth(watermark) <= getWidth() - 6) {
                            int x = (getWidth() - fm.stringWidth(watermark)) / 2;
                            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 2;
                            g2.drawString(watermark, Math.max(4, x), Math.max(16, y));
                        }
                    }
                }
            };

            textArea.setUI(new EmojiTextAreaUI());
            textArea.setBackground(new Color(0, 0, 0, 0));
            textArea.setForeground(Color.WHITE);
            textArea.setCaretColor(new Color(130, 210, 255));
            textArea.setSelectionColor(new Color(80, 160, 255, 110));
            textArea.setSelectedTextColor(Color.WHITE);
            textArea.setFont(editorFont);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(new EmptyBorder(4, 2, 4, 2));

            textArea.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    onContentChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onContentChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    onContentChanged();
                }
            });

            textArea.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    updateHeaderDisplay();
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    updateHeaderDisplay();
                    repaint();
                }
            });

            textArea.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        e.consume();
                        toggleZoomSlot(index);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHovered(false);
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        e.consume();
                        toggleZoomSlot(index);
                    } else {
                        textArea.requestFocusInWindow();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHovered(false);
                }
            });

            JScrollPane scrollPane = createCleanScrollPane(textArea);

            add(headerBar, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }

        private void onContentChanged() {
            repaint();
            scheduleAutoSave();
        }

        public void setTextSafely(String text) {
            internalUpdating = true;
            textArea.setText(text == null ? "" : text);
            textArea.setCaretPosition(0);
            internalUpdating = false;
            updateHeaderDisplay();
            repaint();
        }

        public String getRawText() {
            return textArea.getText();
        }

        public void setZoomed(boolean zoomed) {
            this.zoomed = zoomed;
            updateHeaderDisplay();
        }

        public void focusAndSelectAll() {
            textArea.requestFocusInWindow();
            if (!textArea.getText().isEmpty()) {
                textArea.selectAll();
                statusLabel.setText(" 已全选 #" + (index + 1) + " 号粘贴板（Ctrl+C 复制，Ctrl+V 覆盖）");
            } else {
                statusLabel.setText(" 已聚焦 #" + (index + 1) + " 号粘贴板（直接按 Ctrl+V 粘贴）");
            }
        }

        private void setHovered(boolean h) {
            if (this.hovered != h) {
                this.hovered = h;
                updateHeaderDisplay();
                repaint();
            }
        }

        private void triggerFlash(int state, String shortMsg) {
            this.flashState = state;
            this.flashText = shortMsg;
            updateHeaderDisplay();
            repaint();
            flashResetTimer.restart();
        }

        private void updateHeaderDisplay() {
            String baseTitle = "#" + (index + 1);
            if (flashState == 1) {
                headerLabel.setText(baseTitle + "  " + flashText);
                headerLabel.setForeground(new Color(90, 235, 155));
            } else if (flashState == 2) {
                headerLabel.setText(baseTitle + "  " + flashText);
                headerLabel.setForeground(new Color(110, 205, 255));
            } else {
                headerLabel.setText(baseTitle);
                if (focused) {
                    headerLabel.setForeground(new Color(130, 210, 255));
                } else if (hovered) {
                    headerLabel.setForeground(Color.WHITE);
                } else {
                    headerLabel.setForeground(new Color(190, 190, 190));
                }
            }
        }

        private void copyWholeSlotToClipboard(String text) {
            try {
                StringSelection selection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                triggerFlash(1, "✓ 已复制");
                statusLabel.setText(" 已将 #" + (index + 1) + " 号粘贴板全部内容复制到系统剪贴板");
            } catch (Exception e) {
                statusLabel.setText(" 复制失败，系统剪贴板可能正被其他程序占用");
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean hasText = !textArea.getText().isEmpty();

            g2.setColor(new Color(255, 255, 255, focused ? 18 : (hovered ? 14 : 10)));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);

            int darkAlpha = focused ? 145 : (hasText ? 120 : 85);
            g2.setColor(new Color(0, 0, 0, darkAlpha));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);

            int lineY = headerBar.getY() + headerBar.getHeight() + 2;
            if (h > lineY + 10) {
                g2.setColor(new Color(255, 255, 255, focused ? 45 : 22));
                g2.drawLine(12, lineY, Math.max(12, w - 12), lineY);
            }

            if (flashState == 1) {
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(new Color(80, 230, 150, 220));
            } else if (flashState == 2) {
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(new Color(100, 195, 255, 220));
            } else if (focused) {
                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(new Color(130, 200, 255, 185));
            } else if (hovered) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 255, 105));
            } else {
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(255, 255, 255, hasText ? 65 : 35));
            }

            g2.drawRoundRect(1, 1, w - 3, h - 3, 20, 20);
            g2.dispose();
        }
    }

    // =========================================================================
    // 独立 Emoji 双字体混合渲染视图
    // =========================================================================
    private class EmojiTextAreaUI extends javax.swing.plaf.basic.BasicTextAreaUI {
        @Override
        public View create(Element elem) {
            JTextComponent comp = getComponent();
            if (comp instanceof JTextArea) {
                JTextArea area = (JTextArea) comp;
                if (area.getLineWrap()) {
                    return new EmojiWrappedPlainView(elem, area.getWrapStyleWord());
                }
            }
            return super.create(elem);
        }
    }

    private class EmojiWrappedPlainView extends WrappedPlainView {
        public EmojiWrappedPlainView(Element elem, boolean wordWrap) {
            super(elem, wordWrap);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected int drawUnselectedText(Graphics g, int x, int y, int p0, int p1) throws BadLocationException {
            JTextComponent tc = (JTextComponent) getContainer();
            return (int) drawMixedEmojiText((Graphics2D) g, (float) x, (float) y, p0, p1, tc.getForeground());
        }

        @Override
        protected float drawUnselectedText(Graphics2D g, float x, float y, int p0, int p1) throws BadLocationException {
            JTextComponent tc = (JTextComponent) getContainer();
            return drawMixedEmojiText(g, x, y, p0, p1, tc.getForeground());
        }

        @Override
        @SuppressWarnings("deprecation")
        protected int drawSelectedText(Graphics g, int x, int y, int p0, int p1) throws BadLocationException {
            JTextComponent tc = (JTextComponent) getContainer();
            return (int) drawMixedEmojiText((Graphics2D) g, (float) x, (float) y, p0, p1, tc.getSelectedTextColor());
        }

        @Override
        protected float drawSelectedText(Graphics2D g, float x, float y, int p0, int p1) throws BadLocationException {
            JTextComponent tc = (JTextComponent) getContainer();
            return drawMixedEmojiText(g, x, y, p0, p1, tc.getSelectedTextColor());
        }

        private float drawMixedEmojiText(Graphics2D g2, float x, float y, int p0, int p1, Color fgColor) throws BadLocationException {
            Document doc = getDocument();
            String text = doc.getText(p0, p1 - p0);
            if (text.isEmpty()) {
                return x;
            }

            g2.setColor(fgColor);
            Font primaryFont = editorFont;
            Font fallbackEmojiFont = emojiFont.deriveFont(primaryFont.getStyle(), (float) primaryFont.getSize());
            FontMetrics primaryFm = g2.getFontMetrics(primaryFont);
            FontMetrics emojiFm = g2.getFontMetrics(fallbackEmojiFont);

            float curX = x;
            int i = 0;
            int len = text.length();

            while (i < len) {
                char ch = text.charAt(i);
                if (ch == '\t') {
                    curX = nextTabStop(curX, p0 + i);
                    i++;
                    continue;
                }

                int cp = text.codePointAt(i);
                int charCount = Character.charCount(cp);

                while (i + charCount < len) {
                    int nextCp = text.codePointAt(i + charCount);
                    if (nextCp == 0xFE0F || nextCp == 0xFE0E || nextCp == 0x20E3) {
                        charCount += Character.charCount(nextCp);
                    } else if (nextCp == 0x200D) {
                        charCount += Character.charCount(nextCp);
                        if (i + charCount < len) {
                            int joinedCp = text.codePointAt(i + charCount);
                            charCount += Character.charCount(joinedCp);
                        }
                    } else {
                        break;
                    }
                }

                String cluster = text.substring(i, i + charCount);
                boolean useEmoji = isEmojiCodePoint(cp) || charCount > 1 || !primaryFont.canDisplay(cp);

                if (useEmoji) {
                    g2.setFont(fallbackEmojiFont);
                    g2.drawString(cluster, curX, y);
                    curX += emojiFm.stringWidth(cluster);
                } else {
                    g2.setFont(primaryFont);
                    g2.drawString(cluster, curX, y);
                    curX += primaryFm.stringWidth(cluster);
                }

                i += charCount;
            }

            g2.setFont(primaryFont);
            return curX;
        }
    }

    private JScrollPane createCleanScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view) {
            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        sp.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
        sp.getViewport().setUI(new javax.swing.plaf.basic.BasicViewportUI());
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBackground(new Color(0, 0, 0, 0));
        sp.getViewport().setBackground(new Color(0, 0, 0, 0));
        sp.setBorder(null);
        sp.setViewportBorder(null);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        return sp;
    }

    private String readSystemClipboardText() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String clipText = (String) t.getTransferData(DataFlavor.stringFlavor);
                if (clipText != null) {
                    return clipText;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}