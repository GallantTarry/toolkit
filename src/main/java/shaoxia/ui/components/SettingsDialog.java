package shaoxia.ui.components;

import shaoxia.MainLauncher;
import shaoxia.MainLauncher.ModuleMeta;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassComboBox;
import shaoxia.modules.BackgroundPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * 系统外观与主页板块拖拽定制对话框
 * （底部双按钮尺寸完全统一 + 实时通知小猫显隐与网格更新 + 局部脏矩形极速滑块）
 */
public class SettingsDialog {

    private static final Color DIALOG_BG_COLOR = new Color(35, 38, 43);
    private static final Map<String, Image> ICON_CACHE = new HashMap<>();

    public static void showDialog(JFrame parentFrame) {

        JDialog dialog = new JDialog(parentFrame, "系统设置", true);
        dialog.setSize(600, 485);
        dialog.setLocationRelativeTo(parentFrame);

        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);

        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(DIALOG_BG_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                g2.setColor(new Color(255, 255, 255, 20));
                g2.drawLine(20, 48, getWidth() - 20, 48);

                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

                g2.dispose();
            }
        };
        rootPanel.setOpaque(false);
        rootPanel.setFocusable(true);

        // ================= 1. 自定义标题栏 (支持鼠标拖拽 + 顶部页签切换) =================
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 16));

        JLabel titleLabel = new JLabel("<html><span style='font-family:Montserrat'>ToolKit</span> 控制中心</html>");
        titleLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 15));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);

        CardLayout tabCardLayout = new CardLayout();
        JPanel centerSwitchContainer = new JPanel(tabCardLayout);
        centerSwitchContainer.setOpaque(true);
        centerSwitchContainer.setBackground(DIALOG_BG_COLOR);

        SegmentedTabBar segmentedTabBar = new SegmentedTabBar(
                new String[]{"主页板块定制", "外观与常规设置"},
                selectedIndex -> {
                    if (selectedIndex == 0) {
                        tabCardLayout.show(centerSwitchContainer, "TAB_HOME_MODULES");
                    } else {
                        tabCardLayout.show(centerSwitchContainer, "TAB_APPEARANCE");
                    }
                }
        );
        JPanel tabBarWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        tabBarWrapper.setOpaque(false);
        tabBarWrapper.add(segmentedTabBar);
        titleBar.add(tabBarWrapper, BorderLayout.CENTER);

        CloseButton closeBtn = new CloseButton();
        closeBtn.addActionListener(e -> dialog.dispose());
        titleBar.add(closeBtn, BorderLayout.EAST);

        MouseAdapter dragListener = new MouseAdapter() {
            Point loc;

            @Override
            public void mousePressed(MouseEvent e) {
                loc = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (loc == null) return;
                Point p = dialog.getLocation();
                dialog.setLocation(p.x + e.getX() - loc.x, p.y + e.getY() - loc.y);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragListener);

        rootPanel.add(titleBar, BorderLayout.NORTH);

        // ================= 2A. 页签一：主页板块拖拽定制画布 =================
        List<String> initialHomeIds = (parentFrame instanceof MainLauncher)
                ? ((MainLauncher) parentFrame).getHomeModuleIds()
                : new ArrayList<>(Arrays.asList(MainLauncher.DEFAULT_HOME_MODULES.split(",")));

        HomeModuleDragPanel homeDragPanel = new HomeModuleDragPanel(initialHomeIds, updatedIds -> {
            if (parentFrame instanceof MainLauncher) {
                ((MainLauncher) parentFrame).updateHomeModules(updatedIds);
            }
        });
        centerSwitchContainer.add(homeDragPanel, "TAB_HOME_MODULES");

        // ================= 2B. 页签二：原有的外观与常规设置表单区 =================
        Font labelFont = FontUtils.getChineseFont(Font.PLAIN, 15);
        Font controlFont = FontUtils.getChineseFont(Font.PLAIN, 14);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(true);
        formPanel.setBackground(DIALOG_BG_COLOR);
        formPanel.setBorder(BorderFactory.createEmptyBorder(18, 35, 10, 35));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(14, 6, 14, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;

        // --- 第 0 行：主窗口关闭行为 ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(createRowLabel("主窗口关闭行为:", labelFont), gbc);

        String[] closeOptions = {"最小化到系统托盘", "直接退出软件"};
        GlassComboBox<String> closeCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(closeOptions));
        closeCombo.setFont(controlFont);
        closeCombo.setPreferredSize(new Dimension(220, 34));
        closeCombo.setFocusable(false);
        closeCombo.setSelectedIndex(prefs.getInt("close_action", 0));

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(closeCombo, gbc);

        // --- 第 1 行：全局背景壁纸 ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(createRowLabel("全局背景壁纸:", labelFont), gbc);

        JPanel bgRightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bgRightPanel.setOpaque(true);
        bgRightPanel.setBackground(DIALOG_BG_COLOR);

        String[] bgNames = {"月球与地球"};
        String[] bgPaths = {"bg_imgs/月球与地球.png"};

        GlassComboBox<String> bgCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(bgNames));
        bgCombo.setFont(controlFont);
        bgCombo.setPreferredSize(new Dimension(220, 34));
        bgCombo.setFocusable(false);
        bgCombo.setRenderer(createFontAwareRenderer(controlFont));

        String currentBg = prefs.get("bg_image_path", "bg_imgs/月球与地球.png");
        boolean isBuiltIn = false;
        for (int i = 0; i < bgPaths.length; i++) {
            if (bgPaths[i].equals(currentBg)) {
                bgCombo.setSelectedIndex(i);
                isBuiltIn = true;
                break;
            }
        }
        if (!isBuiltIn) {
            bgCombo.addItem(currentBg);
            bgCombo.setSelectedItem(currentBg);
        }

        ActionButton customBgBtn = new ActionButton("本地图片");
        customBgBtn.setFont(FontUtils.getChineseFont(Font.BOLD, 12));
        customBgBtn.setPreferredSize(new Dimension(95, 34));
        customBgBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择自定义背景图片");
            chooser.setFileFilter(new FileNameExtensionFilter("图片文件 (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                String customPath = selectedFile.getAbsolutePath();
                bgCombo.addItem(customPath);
                bgCombo.setSelectedItem(customPath);
            }
        });

        bgRightPanel.add(bgCombo);
        bgRightPanel.add(Box.createHorizontalStrut(10));
        bgRightPanel.add(customBgBtn);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(bgRightPanel, gbc);

        // --- 第 2 行：背景明暗调节 ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(createRowLabel("背景明暗调节:", labelFont), gbc);

        JPanel darknessRightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        darknessRightPanel.setOpaque(true);
        darknessRightPanel.setBackground(DIALOG_BG_COLOR);

        JLabel lightTip = new JLabel("明亮 ");
        lightTip.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
        lightTip.setForeground(new Color(190, 195, 205));

        SmoothDarknessSlider darknessSlider = new SmoothDarknessSlider(0, 255, prefs.getInt("bg_darkness", 120));

        JLabel darkTip = new JLabel(" 暗沉");
        darkTip.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
        darkTip.setForeground(new Color(190, 195, 205));

        darknessRightPanel.add(lightTip);
        darknessRightPanel.add(darknessSlider);
        darknessRightPanel.add(darkTip);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(darknessRightPanel, gbc);

        // --- 第 3 行：桌面小猫开关 ---
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(createRowLabel("<html>桌面小猫(<span style='font-family:" + FontUtils.getEnglishFamily() + "'>MITI</span>):</html>", labelFont), gbc);

        JCheckBox catCheckBox = new JCheckBox("启用可恶的小猫？");
        catCheckBox.setFont(controlFont);
        catCheckBox.setForeground(Color.WHITE);
        catCheckBox.setOpaque(true);
        catCheckBox.setBackground(DIALOG_BG_COLOR);
        catCheckBox.setFocusPainted(false);
        catCheckBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        catCheckBox.setSelected(prefs.getBoolean("cat_enabled", true));

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(catCheckBox, gbc);

        centerSwitchContainer.add(formPanel, "TAB_APPEARANCE");
        rootPanel.add(centerSwitchContainer, BorderLayout.CENTER);

        // ================= 3. 底部保存按钮区（两颗按钮尺寸与字号 1:1 完全统一） =================
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 14));
        btnPanel.setOpaque(false);

        Dimension unifiedBtnSize = new Dimension(165, 40);
        Font unifiedBtnFont = FontUtils.getChineseFont(Font.BOLD, 14);

        ActionButton resetHomeBtn = new ActionButton("恢复默认主页");
        resetHomeBtn.setFont(unifiedBtnFont);
        resetHomeBtn.setPreferredSize(unifiedBtnSize);
        resetHomeBtn.addActionListener(e -> homeDragPanel.resetToDefault());

        ActionButton applyBtn = new ActionButton("保存设置");
        applyBtn.setFont(unifiedBtnFont);
        applyBtn.setPreferredSize(unifiedBtnSize);

        applyBtn.addActionListener(e -> {
            prefs.putInt("close_action", closeCombo.getSelectedIndex());
            prefs.putInt("bg_darkness", darknessSlider.getValue());

            String selectedName = (String) bgCombo.getSelectedItem();
            String finalPath = selectedName;
            for (int i = 0; i < bgNames.length; i++) {
                if (bgNames[i].equals(selectedName)) {
                    finalPath = bgPaths[i];
                    break;
                }
            }
            prefs.put("bg_image_path", finalPath);

            updateAllBackgrounds(parentFrame, finalPath);

            boolean enableCat = catCheckBox.isSelected();
            prefs.putBoolean("cat_enabled", enableCat);

            try {
                prefs.flush();
            } catch (Exception ignored) {
            }

            if (parentFrame instanceof MainLauncher) {
                MainLauncher launcher = (MainLauncher) parentFrame;
                launcher.resetSharedBackdropCache();
                launcher.updateHomeModules(homeDragPanel.getCurrentHomeIds());
                // 【关键补充】：立即应用小猫显隐设置与最新网格坐标
                launcher.reloadCatSettings();
                launcher.switchPanel("MENU");
            }

            dialog.dispose();
        });

        btnPanel.add(resetHomeBtn);
        btnPanel.add(applyBtn);
        rootPanel.add(btnPanel, BorderLayout.SOUTH);

        dialog.add(rootPanel);
        dialog.setVisible(true);
    }

    private static ListCellRenderer<Object> createFontAwareRenderer(Font baseFont) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (index == -1) {
                    label.setOpaque(false);
                    label.setForeground(Color.WHITE);
                } else {
                    label.setOpaque(true);
                    label.setBackground(isSelected ? new Color(80, 90, 100) : new Color(40, 45, 50));
                    label.setForeground(Color.WHITE);
                }
                String text = value == null ? "" : value.toString();
                label.setFont(fontForText(text, baseFont));
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return label;
            }
        };
    }

    private static Font fontForText(String text, Font baseFont) {
        if (text != null) {
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                if (script == Character.UnicodeScript.HAN
                        || script == Character.UnicodeScript.HIRAGANA
                        || script == Character.UnicodeScript.KATAKANA
                        || script == Character.UnicodeScript.HANGUL
                        || script == Character.UnicodeScript.BOPOMOFO) {
                    return FontUtils.getChineseFont(baseFont.getStyle(), baseFont.getSize2D());
                }
                offset += Character.charCount(codePoint);
            }
            if (!text.isEmpty()) {
                return FontUtils.getEnglishFont(baseFont.getStyle(), baseFont.getSize2D());
            }
        }
        return FontUtils.getChineseFont(baseFont.getStyle(), baseFont.getSize2D());
    }

    private static JLabel createRowLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(new Color(230, 235, 240));
        label.setPreferredSize(new Dimension(125, 34));
        return label;
    }

    private static void updateAllBackgrounds(Container container, String newPath) {
        for (Component c : container.getComponents()) {
            if (c instanceof BackgroundPanel) {
                ((BackgroundPanel) c).loadImage(newPath);
            } else if (c instanceof Container) {
                updateAllBackgrounds((Container) c, newPath);
            }
        }
    }

    private static Image loadModuleIcon(String iconPath, int targetSize) {
        String key = iconPath + "#" + targetSize;
        Image cached = ICON_CACHE.get(key);
        if (cached != null) return cached;

        try {
            URL url = SettingsDialog.class.getResource(iconPath);
            if (url != null) {
                Image raw = new ImageIcon(url).getImage();
                BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = scaled.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(raw, 0, 0, targetSize, targetSize, null);
                g2.dispose();
                ICON_CACHE.put(key, scaled);
                return scaled;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // =========================================================================
    // 局部不透明极速滑块组件
    // =========================================================================
    static class SmoothDarknessSlider extends JComponent {
        private final int min;
        private final int max;
        private int value;
        private boolean isHover = false;
        private boolean isDragging = false;
        private static final int PAD_X = 12;

        public SmoothDarknessSlider(int min, int max, int initialValue) {
            this.min = min;
            this.max = max;
            this.value = Math.max(min, Math.min(max, initialValue));

            setOpaque(true);
            setBackground(DIALOG_BG_COLOR);
            setPreferredSize(new Dimension(220, 34));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
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
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    isDragging = true;
                    updateValueFromMouse(e.getX());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!isDragging) return;
                    updateValueFromMouse(e.getX());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging) {
                        isDragging = false;
                        repaint();
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void updateValueFromMouse(int mouseX) {
            int trackW = Math.max(1, getWidth() - PAD_X * 2);
            double ratio = (double) (mouseX - PAD_X) / trackW;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            int newVal = min + (int) Math.round(ratio * (max - min));
            if (newVal != this.value) {
                this.value = newVal;
                repaint();
            }
        }

        public int getValue() {
            return value;
        }

        public void setValue(int val) {
            int clamped = Math.max(min, Math.min(max, val));
            if (this.value != clamped) {
                this.value = clamped;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(DIALOG_BG_COLOR);
            g2.fillRect(0, 0, w, h);

            int trackW = Math.max(1, w - PAD_X * 2);
            int trackY = h / 2 - 2;
            int trackH = 6;

            double ratio = (double) (value - min) / Math.max(1, (max - min));
            int thumbX = PAD_X + (int) Math.round(ratio * trackW);

            g2.setColor(new Color(22, 25, 30));
            g2.fillRoundRect(PAD_X, trackY - trackH / 2, trackW, trackH, trackH, trackH);
            g2.setColor(new Color(255, 255, 255, 28));
            g2.drawRoundRect(PAD_X, trackY - trackH / 2, trackW, trackH, trackH, trackH);

            int fillW = Math.max(0, thumbX - PAD_X);
            if (fillW > 0) {
                g2.setColor(isDragging || isHover ? new Color(56, 189, 248) : new Color(41, 152, 215));
                g2.fillRoundRect(PAD_X, trackY - trackH / 2, fillW, trackH, trackH, trackH);
            }

            g2.setColor(new Color(148, 163, 184, 130));
            int tickTop = trackY + 7;
            for (int i = 0; i <= 5; i++) {
                int tx = PAD_X + (trackW * i) / 5;
                g2.drawLine(tx, tickTop, tx, tickTop + 4);
            }

            int thumbRadius = isDragging ? 8 : (isHover ? 7 : 6);
            if (isDragging || isHover) {
                g2.setColor(new Color(56, 189, 248, 55));
                g2.fillOval(thumbX - thumbRadius - 3, trackY - thumbRadius - 3, (thumbRadius + 3) * 2, (thumbRadius + 3) * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillOval(thumbX - thumbRadius, trackY - thumbRadius, thumbRadius * 2, thumbRadius * 2);
            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawOval(thumbX - thumbRadius, trackY - thumbRadius, thumbRadius * 2, thumbRadius * 2);

            g2.dispose();
        }
    }

    // =========================================================================
    // 顶部胶囊式双页签切换组件
    // =========================================================================
    static class SegmentedTabBar extends JComponent {
        private final String[] labels;
        private int selectedIndex = 0;
        private int hoverIndex = -1;
        private final java.util.function.IntConsumer onTabChanged;

        public SegmentedTabBar(String[] labels, java.util.function.IntConsumer onTabChanged) {
            this.labels = labels;
            this.onTabChanged = onTabChanged;
            setOpaque(true);
            setBackground(DIALOG_BG_COLOR);
            setPreferredSize(new Dimension(268, 28));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = e.getX() < getWidth() / 2 ? 0 : 1;
                    if (idx != hoverIndex) {
                        hoverIndex = idx;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIndex = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    int idx = e.getX() < getWidth() / 2 ? 0 : 1;
                    if (idx != selectedIndex) {
                        selectedIndex = idx;
                        repaint();
                        if (onTabChanged != null) {
                            onTabChanged.accept(selectedIndex);
                        }
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int halfW = w / 2;

            g2.setColor(DIALOG_BG_COLOR);
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(22, 25, 30, 210));
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(new Color(255, 255, 255, 35));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

            int pillX = selectedIndex == 0 ? 2 : halfW;
            int pillW = halfW - 2;
            g2.setColor(new Color(56, 189, 248, 55));
            g2.fillRoundRect(pillX, 2, pillW, h - 4, 12, 12);
            g2.setColor(new Color(125, 211, 252, 140));
            g2.drawRoundRect(pillX, 2, pillW - 1, h - 5, 12, 12);

            Font font = FontUtils.getChineseFont(Font.BOLD, 12);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < 2; i++) {
                String text = labels[i];
                int cellX = i * halfW;
                int tx = cellX + (halfW - fm.stringWidth(text)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();

                if (i == selectedIndex) {
                    g2.setColor(Color.WHITE);
                } else if (i == hoverIndex) {
                    g2.setColor(new Color(220, 230, 242));
                } else {
                    g2.setColor(new Color(155, 165, 180));
                }
                g2.drawString(text, tx, ty);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // 主页板块 9 宫格 + 备选仓库 原生丝滑拖拽替换画布
    // =========================================================================
    static class HomeModuleDragPanel extends JPanel {
        private final List<String> homeIds = new ArrayList<>();
        private final List<String> poolIds = new ArrayList<>();
        private final java.util.function.Consumer<List<String>> onLayoutChanged;

        private final Rectangle[] gridRects = new Rectangle[9];
        private final List<Rectangle> poolRects = new ArrayList<>();
        private Rectangle leftPageBtnRect = new Rectangle();
        private Rectangle rightPageBtnRect = new Rectangle();
        private int poolOffset = 0;
        private static final int MAX_VISIBLE_POOL = 4;

        private String draggingId = null;
        private boolean draggingFromGrid = false;
        private int draggingSourceIdx = -1;
        private Point pressPt = null;
        private Point currentMousePt = null;
        private boolean isDraggingActive = false;

        private String selectedModuleId = null;
        private boolean selectedFromGrid = false;
        private int selectedSourceIdx = -1;

        private int hoverGridIdx = -1;
        private int hoverPoolIdx = -1;

        public HomeModuleDragPanel(List<String> initialHomeIds, java.util.function.Consumer<List<String>> onLayoutChanged) {
            this.onLayoutChanged = onLayoutChanged;
            setOpaque(true);
            setBackground(DIALOG_BG_COLOR);
            setLayout(null);

            this.homeIds.addAll(initialHomeIds);
            rebuildPoolList();

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHoverState(e.getPoint());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverGridIdx = -1;
                    hoverPoolIdx = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    Point pt = e.getPoint();

                    if (poolIds.size() > MAX_VISIBLE_POOL) {
                        if (leftPageBtnRect.contains(pt) && poolOffset > 0) {
                            poolOffset--;
                            repaint();
                            return;
                        }
                        if (rightPageBtnRect.contains(pt) && poolOffset + MAX_VISIBLE_POOL < poolIds.size()) {
                            poolOffset++;
                            repaint();
                            return;
                        }
                    }

                    pressPt = pt;
                    currentMousePt = pt;
                    isDraggingActive = false;
                    draggingId = null;
                    draggingSourceIdx = -1;

                    for (int i = 0; i < 9; i++) {
                        if (gridRects[i] != null && gridRects[i].contains(pt)) {
                            draggingId = homeIds.get(i);
                            draggingFromGrid = true;
                            draggingSourceIdx = i;
                            break;
                        }
                    }

                    if (draggingId == null) {
                        for (int i = 0; i < poolRects.size(); i++) {
                            int actualPoolIdx = poolOffset + i;
                            if (actualPoolIdx < poolIds.size() && poolRects.get(i).contains(pt)) {
                                draggingId = poolIds.get(actualPoolIdx);
                                draggingFromGrid = false;
                                draggingSourceIdx = actualPoolIdx;
                                break;
                            }
                        }
                    }
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (draggingId == null) return;
                    currentMousePt = e.getPoint();
                    if (!isDraggingActive && pressPt != null && pressPt.distance(currentMousePt) > 5) {
                        isDraggingActive = true;
                        selectedModuleId = null;
                    }
                    updateHoverState(currentMousePt);
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (draggingId == null) return;
                    Point releasePt = e.getPoint();

                    if (isDraggingActive) {
                        int targetGridIdx = -1;
                        for (int i = 0; i < 9; i++) {
                            if (gridRects[i] != null && gridRects[i].contains(releasePt)) {
                                targetGridIdx = i;
                                break;
                            }
                        }

                        if (targetGridIdx != -1) {
                            performSwap(draggingFromGrid, draggingSourceIdx, true, targetGridIdx);
                        } else if (draggingFromGrid) {
                            for (int i = 0; i < poolRects.size(); i++) {
                                int actualPoolIdx = poolOffset + i;
                                if (actualPoolIdx < poolIds.size() && poolRects.get(i).contains(releasePt)) {
                                    performSwap(true, draggingSourceIdx, false, actualPoolIdx);
                                    break;
                                }
                            }
                        }
                    } else {
                        handleClickSwap(draggingFromGrid, draggingSourceIdx, draggingId);
                    }

                    draggingId = null;
                    draggingSourceIdx = -1;
                    isDraggingActive = false;
                    updateHoverState(releasePt);
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (poolIds.size() > MAX_VISIBLE_POOL) {
                        if (e.getWheelRotation() > 0 && poolOffset + MAX_VISIBLE_POOL < poolIds.size()) {
                            poolOffset++;
                            repaint();
                        } else if (e.getWheelRotation() < 0 && poolOffset > 0) {
                            poolOffset--;
                            repaint();
                        }
                    }
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        private void rebuildPoolList() {
            poolIds.clear();
            for (ModuleMeta meta : MainLauncher.ALL_MODULES) {
                if (!homeIds.contains(meta.id)) {
                    poolIds.add(meta.id);
                }
            }
            if (poolOffset > Math.max(0, poolIds.size() - MAX_VISIBLE_POOL)) {
                poolOffset = Math.max(0, poolIds.size() - MAX_VISIBLE_POOL);
            }
        }

        private void handleClickSwap(boolean clickedGrid, int clickedIdx, String clickedId) {
            if (selectedModuleId == null) {
                selectedModuleId = clickedId;
                selectedFromGrid = clickedGrid;
                selectedSourceIdx = clickedIdx;
                return;
            }

            if (selectedFromGrid == clickedGrid && selectedSourceIdx == clickedIdx) {
                selectedModuleId = null;
                selectedSourceIdx = -1;
                return;
            }

            if (selectedFromGrid || clickedGrid) {
                performSwap(selectedFromGrid, selectedSourceIdx, clickedGrid, clickedIdx);
            }
            selectedModuleId = null;
            selectedSourceIdx = -1;
        }

        private void performSwap(boolean fromGrid, int srcIdx, boolean toGrid, int dstIdx) {
            if (fromGrid && toGrid) {
                if (srcIdx == dstIdx || srcIdx < 0 || dstIdx < 0 || srcIdx >= 9 || dstIdx >= 9) return;
                String temp = homeIds.get(srcIdx);
                homeIds.set(srcIdx, homeIds.get(dstIdx));
                homeIds.set(dstIdx, temp);
            } else if (!fromGrid && toGrid) {
                if (srcIdx < 0 || srcIdx >= poolIds.size() || dstIdx < 0 || dstIdx >= 9) return;
                String newModId = poolIds.get(srcIdx);
                homeIds.set(dstIdx, newModId);
                rebuildPoolList();
            } else if (fromGrid && !toGrid) {
                if (srcIdx < 0 || srcIdx >= 9 || dstIdx < 0 || dstIdx >= poolIds.size()) return;
                String newModId = poolIds.get(dstIdx);
                homeIds.set(srcIdx, newModId);
                rebuildPoolList();
            }

            if (onLayoutChanged != null) {
                onLayoutChanged.accept(new ArrayList<>(homeIds));
            }
        }

        public void resetToDefault() {
            homeIds.clear();
            homeIds.addAll(Arrays.asList(MainLauncher.DEFAULT_HOME_MODULES.split(",")));
            rebuildPoolList();
            selectedModuleId = null;
            if (onLayoutChanged != null) {
                onLayoutChanged.accept(new ArrayList<>(homeIds));
            }
            repaint();
        }

        public List<String> getCurrentHomeIds() {
            return new ArrayList<>(homeIds);
        }

        private void updateHoverState(Point pt) {
            int newHoverGrid = -1;
            int newHoverPool = -1;
            for (int i = 0; i < 9; i++) {
                if (gridRects[i] != null && gridRects[i].contains(pt)) {
                    newHoverGrid = i;
                    break;
                }
            }
            if (newHoverGrid == -1) {
                for (int i = 0; i < poolRects.size(); i++) {
                    int actualIdx = poolOffset + i;
                    if (actualIdx < poolIds.size() && poolRects.get(i).contains(pt)) {
                        newHoverPool = actualIdx;
                        break;
                    }
                }
            }
            if (newHoverGrid != hoverGridIdx || newHoverPool != hoverPoolIdx) {
                hoverGridIdx = newHoverGrid;
                hoverPoolIdx = newHoverPool;
                setCursor((hoverGridIdx != -1 || hoverPoolIdx != -1 || isDraggingActive)
                        ? new Cursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }

            g2.setColor(DIALOG_BG_COLOR);
            g2.fillRect(0, 0, w, h);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawLine(0, 0, 0, h);
            g2.drawLine(w - 1, 0, w - 1, h);

            int padX = 28;
            int contentW = w - padX * 2;

            g2.setFont(FontUtils.getEnglishFont(Font.BOLD, 13));
            g2.setColor(new Color(210, 220, 232));
            g2.drawString("Bound to this earth by a covenant of ninefold souls.", padX, 24);

            int gridTop = 34;
            int gapX = 12;
            int gapY = 10;
            int cellW = (contentW - gapX * 2) / 3;
            int cellH = 60;

            for (int i = 0; i < 9; i++) {
                int row = i / 3;
                int col = i % 3;
                int cx = padX + col * (cellW + gapX);
                int cy = gridTop + row * (cellH + gapY);
                gridRects[i] = new Rectangle(cx, cy, cellW, cellH);

                String modId = homeIds.get(i);
                boolean isBeingDragged = isDraggingActive && draggingFromGrid && draggingSourceIdx == i;
                boolean isDropTarget = isDraggingActive && hoverGridIdx == i && (!draggingFromGrid || draggingSourceIdx != i);
                boolean isSelected = !isDraggingActive && selectedModuleId != null && selectedFromGrid && selectedSourceIdx == i;
                boolean isHover = !isDraggingActive && hoverGridIdx == i;

                drawModuleSlotCard(g2, gridRects[i], modId, i + 1, isBeingDragged, isDropTarget, isSelected, isHover, true);
            }

            int poolTitleY = gridTop + 3 * cellH + 2 * gapY + 24;
            g2.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
            g2.setColor(new Color(148, 163, 184));
            g2.drawString("待命板块仓库（拖动到上方九宫格即可上架到主页）：", padX, poolTitleY);

            int poolTop = poolTitleY + 10;
            int poolH = 74;

            g2.setColor(new Color(25, 28, 33, 190));
            g2.fillRoundRect(padX, poolTop, contentW, poolH, 14, 14);
            g2.setColor(new Color(255, 255, 255, 24));
            g2.drawRoundRect(padX, poolTop, contentW - 1, poolH - 1, 14, 14);

            poolRects.clear();
            int visibleCount = Math.min(MAX_VISIBLE_POOL, poolIds.size() - poolOffset);
            int innerPad = 10;
            int availW = contentW - innerPad * 2;
            int poolGap = 10;
            int poolCardW = (availW - poolGap * (MAX_VISIBLE_POOL - 1)) / MAX_VISIBLE_POOL;
            int poolCardH = poolH - 16;

            for (int i = 0; i < visibleCount; i++) {
                int actualIdx = poolOffset + i;
                String modId = poolIds.get(actualIdx);
                int px = padX + innerPad + i * (poolCardW + poolGap);
                int py = poolTop + 8;
                Rectangle r = new Rectangle(px, py, poolCardW, poolCardH);
                poolRects.add(r);

                boolean isBeingDragged = isDraggingActive && !draggingFromGrid && draggingSourceIdx == actualIdx;
                boolean isDropTarget = isDraggingActive && draggingFromGrid && hoverPoolIdx == actualIdx;
                boolean isSelected = !isDraggingActive && selectedModuleId != null && !selectedFromGrid && selectedSourceIdx == actualIdx;
                boolean isHover = !isDraggingActive && hoverPoolIdx == actualIdx;

                drawModuleSlotCard(g2, r, modId, -1, isBeingDragged, isDropTarget, isSelected, isHover, false);
            }

            if (poolIds.size() > MAX_VISIBLE_POOL) {
                leftPageBtnRect.setBounds(padX - 20, poolTop + 22, 18, 30);
                rightPageBtnRect.setBounds(padX + contentW + 2, poolTop + 22, 18, 30);
                g2.setFont(FontUtils.getChineseFont(Font.BOLD, 14));
                g2.setColor(poolOffset > 0 ? new Color(125, 211, 252) : new Color(90, 95, 105));
                g2.drawString("◀", leftPageBtnRect.x + 2, leftPageBtnRect.y + 20);
                g2.setColor(poolOffset + MAX_VISIBLE_POOL < poolIds.size() ? new Color(125, 211, 252) : new Color(90, 95, 105));
                g2.drawString("▶", rightPageBtnRect.x + 2, rightPageBtnRect.y + 20);
            }

            if (isDraggingActive && draggingId != null && currentMousePt != null) {
                int ghostW = 148;
                int ghostH = 54;
                Rectangle ghostRect = new Rectangle(
                        currentMousePt.x - ghostW / 2,
                        currentMousePt.y - ghostH / 2,
                        ghostW,
                        ghostH
                );
                Composite oldComp = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f));
                drawModuleSlotCard(g2, ghostRect, draggingId, -1, false, true, false, true, true);
                g2.setComposite(oldComp);
            }

            g2.dispose();
        }

        private void drawModuleSlotCard(Graphics2D g2, Rectangle r, String modId, int slotNumber,
                                        boolean isBeingDragged, boolean isDropTarget, boolean isSelected,
                                        boolean isHover, boolean horizontalLayout) {
            ModuleMeta meta = MainLauncher.getModuleMeta(modId);
            if (meta == null) return;

            Graphics2D gc = (Graphics2D) g2.create();
            if (isBeingDragged) {
                gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            }

            if (isDropTarget) {
                gc.setColor(new Color(56, 189, 248, 58));
            } else if (isSelected) {
                gc.setColor(new Color(56, 189, 248, 45));
            } else if (isHover) {
                gc.setColor(new Color(55, 62, 72, 220));
            } else {
                gc.setColor(new Color(43, 47, 55, 210));
            }
            gc.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));

            if (isDropTarget || isSelected) {
                gc.setStroke(new BasicStroke(1.8f));
                gc.setColor(new Color(56, 189, 248, 230));
            } else if (isHover) {
                gc.setStroke(new BasicStroke(1.2f));
                gc.setColor(new Color(255, 255, 255, 110));
            } else {
                gc.setStroke(new BasicStroke(1.0f));
                gc.setColor(new Color(255, 255, 255, 38));
            }
            gc.draw(new RoundRectangle2D.Double(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 12, 12));

            if (slotNumber > 0) {
                gc.setFont(FontUtils.getEnglishFont(Font.BOLD, 10));
                gc.setColor(new Color(148, 163, 184, 130));
                gc.drawString("#" + slotNumber, r.x + r.width - 20, r.y + 14);
            }

            int iconSize = horizontalLayout ? 28 : 24;
            Image icon = loadModuleIcon(meta.iconPath, iconSize);

            if (horizontalLayout) {
                int iconX = r.x + 14;
                int iconY = r.y + (r.height - iconSize) / 2;
                if (icon != null) {
                    gc.drawImage(icon, iconX, iconY, null);
                }
                gc.setFont(FontUtils.getChineseFont(Font.BOLD, 13));
                gc.setColor(Color.WHITE);
                FontMetrics fm = gc.getFontMetrics();
                int textX = iconX + iconSize + 10;
                int textY = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
                gc.drawString(meta.title, textX, textY);
            } else {
                int iconX = r.x + (r.width - iconSize) / 2;
                int iconY = r.y + 7;
                if (icon != null) {
                    gc.drawImage(icon, iconX, iconY, null);
                }
                gc.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
                gc.setColor(new Color(230, 235, 242));
                FontMetrics fm = gc.getFontMetrics();
                int textX = r.x + (r.width - fm.stringWidth(meta.title)) / 2;
                int textY = r.y + r.height - 7;
                gc.drawString(meta.title, textX, textY);
            }

            gc.dispose();
        }
    }

    static class CloseButton extends JButton {
        public CloseButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(30, 30));
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

            g2.setColor(isHover ? Color.WHITE : new Color(170, 175, 185));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int gap = 10;
            g2.drawLine(gap, gap, getWidth() - gap, getHeight() - gap);
            g2.drawLine(getWidth() - gap, gap, gap, getHeight() - gap);

            g2.dispose();
        }
    }
}