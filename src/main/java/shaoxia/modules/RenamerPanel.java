package shaoxia.modules;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FrameTicker;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassComboBox;
import shaoxia.Utils.GlassDropPanel;
import shaoxia.Utils.GlassTextField;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 协同改名助手 RenamerPanel (无内框满幅点阵画卷 + 拖拽窗口3D卡片翻转版) */
public class RenamerPanel extends BackgroundPanel {
    private JComboBox<String> biaoCombo, baoCombo, typeCombo, companyCombo;
    private JTextField newCompanyInput;
    private JLabel previewLabel;
    private DefaultComboBoxModel<String> companyModel;

    private JPanel mainContentPanel;
    private JPanel leftPanel;
    private JPanel rightPanel;
    // 保留该字段以兼容外部状态反射捕获，避免报 NoSuchFieldException: isFlipped
    @SuppressWarnings("unused")
    private boolean isFlipped = false;

    // 撤销功能状态记录 [0]=原文件 [1]=新文件
    private List<File[]> lastRenamedBatch = new ArrayList<>();
    private ActionButton undoBtn;

    // 状态机：0=协同改名助手(皮卡丘), 1=协同批量改名助手(小火龙), 2=农业银行回单命名生成(杰尼龟), 3=招商银行回单命名生成(妙蛙种子)
    private int currentMode = 0;
    private int displayMode = 0; // 当前翻转帧实际渲染的宝可梦模式
    private JLabel dropInfoLabel;
    private GlassDropPanel dropArea;
    private ActionButton modeToggleBtn;

    // 拖拽窗口 3D 卡片翻转动画控制 (60FPS 丝滑过渡)
    private FrameTicker.FrameListener cardTransitionListener;
    private float transitionProgress = 1.0f;
    private boolean isTransitioning = false;

    // 回单解析状态与音乐播放引擎控制 (仿照 CheckerPanel)
    private volatile boolean isProcessingReceipt = false;
    private volatile boolean isPlaying = false;
    private javazoom.jl.player.Player mp3Player;
    private Clip wavClip;
    private javax.swing.Timer musicAnimTimer;
    private int animTick = 0;

    private final Color glassColor = new Color(30, 35, 40, 120);

    // =========================================================================
    // 模式 0：皮卡丘 (Pikachu) —— 28x34 竖向满幅点阵画卷
    // '#' 深色轮廓/耳尖  'Y' 明亮电光黄  'y' 暖金暗部阴影  'L' 胸腹浅黄高光
    // 'R' 电气红脸颊    'W' 眼部高光白  'E' 深邃眼瞳      'b' 闪电尾根棕部
    // 'm' 静谧弯月      '*' '+' '~' 电弧与星辰微粒
    // =========================================================================
    private static final String[] PIKACHU_MATRIX = {
            "...*..............+.....mm..",
            "........+..............mmm..",
            "..*..........~.........mmm..",
            ".....###................mm..",
            "....###Y#..........####.....",
            "....##YYY#.......####YY#.*..",
            ".+..#YYYYY#.....###YYYY#....",
            ".....#YYYYY#...##YYYYY#.....",
            ".....#YYYYYY###YYYYYY#..~...",
            "..~..#YYYYYYYYYYYYYYY#......",
            "....#YYYYYYYYYYYYYYYYY#..###",
            "...#YYYYWEYYYYYYYEWYYYY##YY#",
            "...#YYYYEEYYYYYYYEEYYYY#YYY#",
            "...#YYYYYYYYY#YYYYYYYYY#YY#.",
            "..#YYRRRYYYY###YYYYRRRYY#YY#",
            ".*#YYRRRYYYYYYYYYYYRRRYY#YY#",
            "..#yyyYYYYYYYYYYYYYYYyyy#Y#.",
            "...#yyyYYYYLLLLLYYYYyyy#YY#.",
            "...#YYYYYYLLLLLLLYYYYYY#bb#.",
            "..#YYYY#YYYYLLLLLYYYY#Ybb#..",
            ".~#YYYY#YYYYYYYYYYYYY#bb#...",
            "..#yyyyYYYYYYYYYYYYYYy#.....",
            "...#yyyyyyyyYYYYYyyyyy#..*..",
            "....#yyyyyyyyyyyyyyyyy#.....",
            ".....###yyyy#####yyyy##..+..",
            "....#YYYY###.....###YYYY#...",
            "....#####...........#####...",
            "............................",
            "...~....*..........+....~...",
            "......~~~~........~~~~......",
            "..+..........*...........+..",
            "............................",
            "....*..................*....",
            "..............~............."
    };

    // =========================================================================
    // 模式 1：小火龙 (Charmander) —— 28x34 竖向满幅点阵画卷
    // '#' 深色轮廓      'O' 炽热火龙橙  'o' 暖橙暗部阴影  'L' 头部暖光高光
    // 'C' 奶油黄肚皮    'c' 肚皮暖金边  'W' 眼高光/白爪/火芯
    // 'E' 宝石青眼瞳    'R' 外焰赤红    'F' 内焰金黄      '*' '+' '~' 火星微粒
    // =========================================================================
    private static final String[] CHARMANDER_MATRIX = {
            "...+..............*.........",
            "........*..............+....",
            ".....................R......",
            ".......#######......RRR.....",
            ".....##LLOOOOO##...RFFFR.*..",
            "....#LLOOOOOOOOO#.RFFWFFR...",
            ".*..#LOOOOOOOOOOO#RFFWFFR...",
            "...#OOWEOOOOOEWOO#.RFFFR....",
            "...#OOEEOOOOOEEOO#..RRR.....",
            "..#OOOOOOOOOOOOOOO#.#O#..~..",
            "..#OOOO#OOOOO#OOOO##OO#.....",
            "...#OOOO#####OOOO##OOO#.....",
            ".~..##OOOOOOOOO###OOO#...*..",
            ".....#OOCCCCCCOO##oo#.......",
            "...##OOCCCCCCCCOO#oo#.......",
            "..#OOOOCCCCCCCCOOOoo#.......",
            ".#W#OOCCCCCCCCCCOOoo#....+..",
            ".###OOCCCCCCCCCCOOo#........",
            "..*.#OOCCCCCCCCCCO#.........",
            "....#OOCCCCCCCCCCO#......~..",
            "....#oooCCCCCCCCoo#.........",
            "...#ooooCCCCCCCCooo#........",
            "...#oooooCCCCCCoooo#.....*..",
            "...#ooooo######oooo#........",
            "...#oooo#......#ooo#........",
            "..#WWoo#........#oWW#....+..",
            "..######........#####.......",
            "............................",
            "...~....*..........+....~...",
            "......~~~~........~~~~......",
            "..*..........+...........*..",
            "............................",
            "....+..................*....",
            "..............~............."
    };

    // =========================================================================
    // 模式 2：杰尼龟 (Squirtle) —— 28x34 竖向满幅点阵画卷
    // '#' 深色轮廓/甲缝 'B' 清澈天蓝    'b' 深海蔚蓝阴影  'L' 头部冰蓝高光
    // 'C' 浅黄腹甲      'S' 红褐坚硬背甲 'W' 眼高光/甲缘白  'E' 深色眼瞳
    // '*' '+' '~' 晶莹水泡与水波微粒
    // =========================================================================
    private static final String[] SQUIRTLE_MATRIX = {
            "...~..............*.........",
            "........+..............~....",
            "..*.................+.......",
            ".......#######..............",
            ".....##LLBBBBB##........*...",
            "....#LLBBBBBBBBB#...........",
            ".*..#LBBBBBBBBBBB#......~...",
            "...#BBWEBBBBBEWBB#..........",
            "...#BBEEBBBBBEEBB#..........",
            "..#BBBBBBBBBBBBBBB#.........",
            "..#BBBB#BBBBB#BBBB#.........",
            "...#BBBB#####BBBB#..........",
            ".+..##bbbbbbbbb##.###.......",
            "....##CCCCCCCCCC##SSS#...*..",
            "..##BB#CCCCCCCC#WWSSSS#.....",
            ".#BBBB##########WWSSSS#.###.",
            ".#BBB#CCCCCCCCCC#WWSSS##BBB#",
            ".###.#CCCCCCCCCC#WWSS#BBB#B#",
            "..~..############WWSS#BB##b#",
            ".....#CCCCCCCCCC#WWS#bb###b#",
            ".....##CCCCCCCC##WW##bbbbbb#",
            "....#BB########BB##..######.",
            "...#BBBB#....#BBBB#......+..",
            "...#bbbb#....#bbbb#.........",
            "..#bbbbb#....#bbbbb#.....~..",
            "..######......######........",
            "............................",
            "............................",
            "...~....*..........+....~...",
            ".....~~~~~~......~~~~~~.....",
            "..+..........*...........+..",
            "............................",
            "....*..................~....",
            "..............+............."
    };

    // =========================================================================
    // 模式 3：妙蛙种子 (Bulbasaur) —— 28x34 竖向满幅点阵画卷
    // '#' 深色轮廓      'L' 嫩叶高光绿  'G' 翡翠蒜头绿    'g' 森林深绿背光
    // 'T' 蓝绿身躯肤色  't' 深青斑纹    'W' 眼高光/白爪    'R' 宝石赤红眼眸/口
    // 'm' 静谧弯月      '*' '+' '~' 光合孢子微粒
    // =========================================================================
    private static final String[] BULBASAUR_MATRIX = {
            "...*..............+.....mm..",
            "........+..............mmm..",
            "..~....................mmm..",
            "..............#####.....mm..",
            "............##LLLGG##.......",
            "...........#LLLLGGGGG#...*..",
            ".+........#LLLLGGGGGGG#.....",
            "....###..#LLLGGGGGGGggg#....",
            "...#TTT###LLGGGGGGggggg#....",
            "..#TTTTTTT#GGGGGGGgggggg#~..",
            ".#TTTTTTTTT##GGGGggggggg#...",
            "#TTTttTTTTTTT###GGGGgggg#...",
            "#TTWRTTTTTWRTTTT########....",
            "#TTRRTTTTTRRTTTTTTTttTTT#*..",
            "#TTTTTTTTTTTTTTTTTttttTT#...",
            "#TTTT##########TTTTTTTTT#...",
            ".#TTT#RRRRRRRR#TTTTTTTTT#+..",
            "..###TTTTTTTTTTTTTTTTTTT#...",
            ".*..#TTTTttTTTTTTTtttTTT#...",
            "....#TTTTttTTTTTTTtttTTT#~..",
            "....#TTTTTTTTTTTTTTTTTT#....",
            "....#TTTT##########TTTT#....",
            "....#TTTT#.#TTTT#.#TTTT#....",
            "....#tttt#.#tttt#.#tttt#.*..",
            "...#WWttt#.#WWtt#.#WWtt#....",
            "...######..######.######....",
            "............................",
            "............................",
            "...~....*..........+....~...",
            "......~~~~........~~~~......",
            "..+..........*...........+..",
            "............................",
            "....*..................+....",
            "..............~............."
    };

    private static final String[] MODE_CAPTIONS = {
            "P I K A C H U",
            "C H A R M A N D E R",
            "S Q U I R T L E",
            "B U L B A S A U R"
    };

    class TextChunk {
        String text;
        float x, y, endX;

        public TextChunk(String text, float x, float y, float endX) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.endX = endX;
        }
    }

    public RenamerPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        setLayout(new BorderLayout());
        setOpaque(false);

        mainContentPanel = new JPanel(new GridLayout(1, 2));
        mainContentPanel.setOpaque(false);

        // ---------------- --- 控制面板区 ---------------- ---
        leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        JPanel topLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topLeftPanel.setOpaque(false);
        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> {
            stopMusic();
            parent.showMenu();
        });
        topLeftPanel.add(backBtn);
        leftPanel.add(topLeftPanel, BorderLayout.NORTH);

        JPanel centerLeftPanel = new JPanel(new GridBagLayout());
        centerLeftPanel.setOpaque(false);
        // 减小顶部偏移并增加底部微调间距，使左侧控件较上一版整体上移一点点，同时保持拉伸居中
        centerLeftPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.weightx = 1.0;
        gbc.weighty = 0;

        modeToggleBtn = new ActionButton("当前模式: 协同改名助手");
        modeToggleBtn.setPreferredSize(new Dimension(0, 38));
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        centerLeftPanel.add(modeToggleBtn, gbc);

        undoBtn = new ActionButton("撤销上一步改名");
        undoBtn.setPreferredSize(new Dimension(0, 38));
        gbc.gridy = 1;
        centerLeftPanel.add(undoBtn, gbc);
        undoBtn.addActionListener(e -> undoLastRename());

        biaoCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(generateList("标", 20)));
        biaoCombo.setFocusable(false);
        addControlRow(centerLeftPanel, gbc, 2, "1. 选择标段:", biaoCombo);

        baoCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(generateList("包", 20)));
        baoCombo.setFocusable(false);
        addControlRow(centerLeftPanel, gbc, 3, "2. 选择包号:", baoCombo);

        String[] types = {"价格文件", "商务文件", "技术文件", "2轮", "3轮", "4轮", "5轮", "6轮"};
        typeCombo = new GlassComboBox<>(new DefaultComboBoxModel<>(types));
        typeCombo.setFocusable(false);
        addControlRow(centerLeftPanel, gbc, 4, "3. 文件类型:", typeCombo);

        companyModel = new DefaultComboBoxModel<>(new String[]{"请先添加供应商"});
        companyCombo = new GlassComboBox<>(companyModel);
        companyCombo.setFocusable(false);
        addControlRow(centerLeftPanel, gbc, 5, "4. 选择公司:", companyCombo);

        newCompanyInput = new GlassTextField();
        addControlRow(centerLeftPanel, gbc, 6, "5. 管理供应商:", newCompanyInput);

        JPanel btnP = new JPanel(new GridLayout(1, 3, 8, 0));
        btnP.setOpaque(false);
        ActionButton save = new ActionButton("保存公司");
        ActionButton delete = new ActionButton("删除公司");
        ActionButton reset = new ActionButton("重置名单");

        save.setPreferredSize(new Dimension(0, 32));
        btnP.add(save);
        btnP.add(delete);
        btnP.add(reset);

        gbc.gridy = 7;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 5, 0, 5);

        centerLeftPanel.add(btnP, gbc);
        leftPanel.add(centerLeftPanel, BorderLayout.CENTER);

        // ---------------- --- 监听与预览面板区 ---------------- ---
        rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints rGbc = new GridBagConstraints();
        rGbc.insets = new Insets(10, 20, 20, 20);
        rGbc.fill = GridBagConstraints.BOTH;

        previewLabel = new JLabel("预览：等待输入...", JLabel.CENTER);
        previewLabel.setFont(MainLauncher.BOLD_FONT);
        previewLabel.setForeground(Color.WHITE);
        rGbc.gridy = 0;
        rGbc.weighty = 0.05;
        rGbc.weightx = 1.0;
        rightPanel.add(previewLabel, rGbc);

        // 直接利用现有的 GlassDropPanel 拖拽监听窗口作为翻转卡片载体，不加任何内嵌边框
        dropArea = new GlassDropPanel() {
            @Override
            public void paint(Graphics g) {
                if (isTransitioning) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth();
                    int h = getHeight();

                    // 计算 3D 卡片水平翻转角度 (0 -> PI)
                    double angle = transitionProgress * Math.PI;
                    double scaleX = Math.max(0.04, Math.abs(Math.cos(angle)));
                    double scaleY = 1.0 - 0.035 * Math.sin(angle);
                    float alpha = (float) Math.max(0.35, Math.min(1.0, 0.35 + 0.65 * scaleX));

                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    AffineTransform at = g2.getTransform();
                    at.translate(w / 2.0, h / 2.0);
                    at.scale(scaleX, scaleY);
                    at.translate(-w / 2.0, -h / 2.0);
                    g2.setTransform(at);

                    super.paint(g2);
                    g2.dispose();
                } else {
                    super.paint(g);
                }
            }
        };
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dropArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleMusic(); // 点击拖拽窗口任意位置播放/暂停「未白镇8bit」
            }
        });

        // 终极防御：带事件穿透 + 无内框满幅点阵绘制的中央组件 (严格仿照 CheckerPanel)
        dropInfoLabel = new JLabel("", JLabel.CENTER) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintPokemonDotMatrix(g);
            }
        };
        dropInfoLabel.setForeground(Color.WHITE);
        dropArea.add(dropInfoLabel, BorderLayout.CENTER);

        setDropTarget(dropArea); // 绑定拖拽事件

        rGbc.gridy = 1;
        rGbc.weighty = 0.95;
        rightPanel.add(dropArea, rGbc);

        mainContentPanel.add(leftPanel);
        mainContentPanel.add(rightPanel);
        add(mainContentPanel, BorderLayout.CENTER);

        // ---------------- --- 事件监听绑定 ---------------- ---
        ActionListener listener = e -> updatePreview();
        biaoCombo.addActionListener(listener);
        baoCombo.addActionListener(listener);
        typeCombo.addActionListener(listener);
        companyCombo.addActionListener(listener);

        save.addActionListener(e -> {
            String n = newCompanyInput.getText().trim();
            if (!n.isEmpty()) {
                if (companyModel.getSize() > 0 && companyModel.getElementAt(0).contains("请先"))
                    companyModel.removeElementAt(0);
                companyModel.addElement(n);
                companyCombo.setSelectedItem(n);
                newCompanyInput.setText("");
                updatePreview();
            }
        });

        delete.addActionListener(e -> {
            String selected = (String) companyCombo.getSelectedItem();
            if (selected != null && !selected.contains("请先添加")) {
                companyModel.removeElement(selected);
                if (companyModel.getSize() == 0) {
                    companyModel.addElement("请先添加供应商");
                }
                updatePreview();
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个具体的供应商！");
            }
        });

        newCompanyInput.addActionListener(e -> save.doClick());

        reset.addActionListener(e -> {
            companyModel.removeAllElements();
            companyModel.addElement("请先添加供应商");
            updatePreview();
        });

        modeToggleBtn.addActionListener(e -> {
            int nextMode = (currentMode + 1) % 4;
            // 先标记进入翻转过渡态（保持前半程 displayMode 为旧宝可梦画面），再更新 currentMode 与左侧 UI
            isTransitioning = true;
            currentMode = nextMode;
            switchUIByMode();
            startCardFlipTransition(nextMode);
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (dropInfoLabel != null) {
                    dropInfoLabel.repaint();
                }
            }
        });

        updatePreview();
    }

    /**
     * 启动拖拽窗口的 60FPS 3D 卡片丝滑翻转动画
     */
    private void startCardFlipTransition(final int targetMode) {
        transitionProgress = 0.0f;
        isTransitioning = true;
        if (cardTransitionListener != null) {
            FrameTicker.unregister(cardTransitionListener);
            cardTransitionListener = null;
        }
        cardTransitionListener = delta -> {
            transitionProgress += 0.06f;
            // 当翻转越过 90 度垂直面（0.5）时，无缝切换点阵画面为新模式宝可梦
            if (transitionProgress >= 0.5f && displayMode != targetMode) {
                displayMode = targetMode;
            }
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                displayMode = targetMode;
                isTransitioning = false;
                if (dropArea != null) {
                    dropArea.repaint();
                }
                cardTransitionListener = null;
                return false; // 动画完成，自动从全局 FrameTicker 注销
            }
            if (dropArea != null) {
                dropArea.repaint();
            }
            return true;
        };
        FrameTicker.register(cardTransitionListener);
    }

    /**
     * 核心点阵渲染器：无任何内嵌边框，直接在现有拖拽监听窗口中满幅绘制四大宝可梦与底部极简铭文 (严格仿照 CheckerPanel)
     */
    private void paintPokemonDotMatrix(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = dropInfoLabel.getWidth();
        int h = dropInfoLabel.getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        // 防守同步：非翻转动画期间（例如外部反射恢复 currentMode 后直接触发重绘），确保 displayMode 与 currentMode 严格一致
        if (!isTransitioning && displayMode != currentMode) {
            displayMode = currentMode;
        }

        int mode = displayMode;
        String[] matrix = getMatrixByMode(mode);
        int rows = matrix.length;
        int cols = matrix[0].length();

        // 根据拖拽窗口当前宽高自适应计算点阵颗粒尺寸（仿照 CheckerPanel 满幅算法）
        int cellSize = Math.max(3, Math.min(w / (cols + 2), (h - 32) / (rows + 2)));
        int gap = cellSize >= 5 ? 1 : 0; // 点阵颗粒间隙，营造经典像素点阵屏质感
        int dotSize = Math.max(2, cellSize - gap);
        int arc = Math.max(1, dotSize / 3);

        int totalW = cols * cellSize;
        int totalH = rows * cellSize;

        int startX = (w - totalW) / 2;
        int startY = (h - totalH - (cellSize * 2)) / 2;

        // 1. 宝可梦中心柔和元素氛围光晕（仿照 CheckerPanel 星体背光光晕）
        int centerX = startX + totalW / 2;
        int centerY = startY + (rows * cellSize) / 2;
        int mainGlowRadius = cellSize * (isPlaying && (animTick % 2 == 0) ? 11 : 10);
        g2.setColor(getModeThemeGlowColor(mode, isPlaying ? 34 : 18));
        g2.fillOval(centerX - mainGlowRadius, centerY - mainGlowRadius, mainGlowRadius * 2, mainGlowRadius * 2);

        // 2. 各模式局部特征副光晕（弯月光晕 / 尾焰火光 / 气泡水光 / 种子荧光）
        if (mode == 0) {
            int moonX = startX + 25 * cellSize;
            int moonY = startY + 2 * cellSize;
            int moonR = cellSize * 4;
            g2.setColor(new Color(253, 224, 71, isPlaying ? 30 : 15));
            g2.fillOval(moonX - moonR, moonY - moonR, moonR * 2, moonR * 2);
        } else if (mode == 1) {
            int flameX = startX + 21 * cellSize;
            int flameY = startY + 6 * cellSize;
            int flameR = cellSize * (isPlaying && (animTick % 2 == 0) ? 6 : 5);
            g2.setColor(new Color(251, 146, 60, isPlaying ? 48 : 26));
            g2.fillOval(flameX - flameR, flameY - flameR, flameR * 2, flameR * 2);
        } else if (mode == 2) {
            int tailX = startX + 23 * cellSize;
            int tailY = startY + 18 * cellSize;
            int tailR = cellSize * 5;
            g2.setColor(new Color(56, 189, 248, isPlaying ? 34 : 16));
            g2.fillOval(tailX - tailR, tailY - tailR, tailR * 2, tailR * 2);
        } else if (mode == 3) {
            int bulbX = startX + 18 * cellSize;
            int bulbY = startY + 8 * cellSize;
            int bulbR = cellSize * (isPlaying && (animTick % 2 == 0) ? 6 : 5);
            g2.setColor(new Color(52, 211, 153, isPlaying ? 38 : 20));
            g2.fillOval(bulbX - bulbR, bulbY - bulbR, bulbR * 2, bulbR * 2);
        }

        // 3. 逐行逐列扫描绘制点阵
        for (int r = 0; r < rows; r++) {
            String line = matrix[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;

                Color color = getPokemonDotColor(mode, ch, r, c);
                if (color != null) {
                    int px = startX + c * cellSize;
                    int py = startY + r * cellSize;
                    g2.setColor(color);
                    g2.fillRoundRect(px, py, dotSize, dotSize, arc, arc);
                }
            }
        }

        // 4. 底部微缩点阵铭文（与 CheckerPanel 字体排版与位置完全保持一致）
        int fontSize = Math.max(11, (int) (cellSize * 1.6));
        g2.setFont(FontUtils.getEnglishFont(Font.BOLD, fontSize));
        if (isProcessingReceipt) {
            g2.setColor(new Color(72, 219, 251, 230));
            String caption = "P A R S I N G   P D F . . .";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, (int) (cellSize * 2.2));
            g2.drawString(caption, textX, textY);
        } else {
            g2.setColor(new Color(226, 232, 240, isPlaying ? 215 : 145));
            String caption = MODE_CAPTIONS[mode];
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(caption)) / 2;
            int textY = startY + totalH + Math.max(16, (int) (cellSize * 2.2));
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }

    private String[] getMatrixByMode(int mode) {
        switch (mode) {
            case 1:
                return CHARMANDER_MATRIX;
            case 2:
                return SQUIRTLE_MATRIX;
            case 3:
                return BULBASAUR_MATRIX;
            default:
                return PIKACHU_MATRIX;
        }
    }

    private Color getModeThemeGlowColor(int mode, int alpha) {
        switch (mode) {
            case 0: // 皮卡丘 - 电光金黄
                return new Color(250, 204, 21, alpha);
            case 1: // 小火龙 - 烈焰赤橙
                return new Color(251, 146, 60, alpha);
            case 2: // 杰尼龟 - 激流湛蓝
                return new Color(56, 189, 248, alpha);
            case 3: // 妙蛙种子 - 翠叶生机绿
                return new Color(52, 211, 153, alpha);
            default:
                return new Color(200, 200, 200, alpha);
        }
    }

    /**
     * 四大宝可梦点阵调色盘映射（支持音乐播放时的星辰闪烁与元素动效）
     */
    private Color getPokemonDotColor(int mode, char ch, int row, int col) {
        boolean shimmer = isPlaying && ((row + col + animTick) % 3 == 0);
        boolean pulse = isPlaying && ((row + animTick) % 2 == 0);

        // 通用轮廓、高光、眼瞳与弯月
        if (ch == '#') return new Color(36, 48, 68);
        if (ch == 'W') return new Color(255, 255, 255);
        if (ch == 'E') return new Color(15, 23, 42);
        if (ch == 'm') return shimmer ? new Color(254, 249, 195) : new Color(253, 230, 138, 205);

        switch (mode) {
            case 0: // 皮卡丘 (Pikachu)
                switch (ch) {
                    case 'Y':
                        return new Color(250, 204, 21);
                    case 'L':
                        return new Color(254, 240, 138);
                    case 'y':
                        return new Color(217, 119, 6);
                    case 'R':
                        return pulse ? new Color(255, 100, 100) : new Color(239, 68, 68);
                    case 'b':
                        return new Color(146, 64, 14);
                    case '*':
                        return shimmer ? new Color(255, 255, 255) : new Color(253, 224, 71, 215);
                    case '+':
                        return shimmer ? new Color(165, 243, 252) : new Color(250, 204, 21, 180);
                    case '~':
                        return pulse ? new Color(254, 240, 138, 225) : new Color(250, 204, 21, 110);
                    default:
                        return null;
                }
            case 1: // 小火龙 (Charmander)
                switch (ch) {
                    case 'O':
                        return new Color(249, 115, 22);
                    case 'L':
                        return new Color(253, 186, 116);
                    case 'o':
                        return new Color(194, 65, 12);
                    case 'C':
                        return new Color(254, 240, 138);
                    case 'c':
                        return new Color(252, 211, 77);
                    case 'R':
                        return pulse ? new Color(251, 113, 133) : new Color(239, 68, 68);
                    case 'F':
                        return pulse ? new Color(254, 240, 138) : new Color(250, 204, 21);
                    case '*':
                        return shimmer ? new Color(254, 240, 138) : new Color(251, 146, 60, 215);
                    case '+':
                        return shimmer ? new Color(255, 255, 255) : new Color(248, 113, 113, 185);
                    case '~':
                        return pulse ? new Color(253, 186, 116, 225) : new Color(249, 115, 22, 110);
                    default:
                        return null;
                }
            case 2: // 杰尼龟 (Squirtle)
                switch (ch) {
                    case 'B':
                        return new Color(56, 189, 248);
                    case 'L':
                        return new Color(186, 230, 253);
                    case 'b':
                        return new Color(2, 132, 199);
                    case 'C':
                        return new Color(254, 243, 199);
                    case 'S':
                        return new Color(180, 83, 9);
                    case '*':
                        return shimmer ? new Color(255, 255, 255) : new Color(186, 230, 253, 215);
                    case '+':
                        return shimmer ? new Color(165, 243, 252) : new Color(56, 189, 248, 185);
                    case '~':
                        return pulse ? new Color(125, 211, 252, 225) : new Color(14, 165, 233, 110);
                    default:
                        return null;
                }
            case 3: // 妙蛙种子 (Bulbasaur)
                switch (ch) {
                    case 'L':
                        return pulse ? new Color(190, 242, 100) : new Color(163, 230, 53);
                    case 'G':
                        return new Color(34, 197, 94);
                    case 'g':
                        return new Color(21, 128, 61);
                    case 'T':
                        return new Color(45, 212, 191);
                    case 't':
                        return new Color(13, 148, 136);
                    case 'R':
                        return new Color(225, 29, 72);
                    case '*':
                        return shimmer ? new Color(254, 249, 195) : new Color(163, 230, 53, 215);
                    case '+':
                        return shimmer ? new Color(167, 243, 208) : new Color(52, 211, 153, 185);
                    case '~':
                        return pulse ? new Color(134, 239, 172, 225) : new Color(34, 197, 94, 110);
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    // =========================================================================
    // 隐形音乐播放引擎（支持 resources/music/未白镇8bit 的 mp3 与 wav 双格式自适应）
    // =========================================================================
    private void toggleMusic() {
        if (isPlaying) {
            stopMusic();
        } else {
            isPlaying = true;
            startMusicAnimTimer();
            Thread.ofVirtual().name("Renamer-MusicPlayer").start(() -> {
                try {
                    // 1. 优先尝试 WAV 格式加载
                    InputStream wavStream = findMusicStream("未白镇8bit.wav");
                    if (wavStream != null) {
                        AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(wavStream));
                        wavClip = AudioSystem.getClip();
                        wavClip.addLineListener(event -> {
                            if (event.getType() == LineEvent.Type.STOP && isPlaying) {
                                SwingUtilities.invokeLater(this::stopMusic);
                            }
                        });
                        wavClip.open(ais);
                        wavClip.start();
                        return;
                    }

                    // 2. 尝试 MP3 格式或无后缀原名加载
                    InputStream mp3Stream = findMusicStream("未白镇8bit.mp3");
                    if (mp3Stream == null) {
                        mp3Stream = findMusicStream("未白镇8bit");
                    }

                    if (mp3Stream != null) {
                        mp3Player = new javazoom.jl.player.Player(new BufferedInputStream(mp3Stream));
                        mp3Player.play(); // 阻塞播放
                        if (isPlaying) {
                            SwingUtilities.invokeLater(this::stopMusic);
                        }
                    } else {
                        System.out.println("提示：未找到音频资源 resources/music/未白镇8bit(.mp3/.wav)");
                        SwingUtilities.invokeLater(this::stopMusic);
                    }
                } catch (Exception e) {
                    System.out.println("音乐播放中断或异常: " + e.getMessage());
                    SwingUtilities.invokeLater(this::stopMusic);
                }
            });
        }
    }

    private InputStream findMusicStream(String fileName) {
        String[] classpathCandidates = {
                "/music/" + fileName,
                "/resources/music/" + fileName
        };
        for (String path : classpathCandidates) {
            InputStream is = getClass().getResourceAsStream(path);
            if (is != null) return is;
        }

        String[] fileCandidates = {
                "resources/music/" + fileName,
                "src/main/resources/music/" + fileName,
                "music/" + fileName
        };
        for (String path : fileCandidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                try {
                    return new FileInputStream(f);
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private void startMusicAnimTimer() {
        if (musicAnimTimer == null) {
            musicAnimTimer = new javax.swing.Timer(360, e -> {
                if (isPlaying) {
                    animTick++;
                    if (dropInfoLabel != null) {
                        dropInfoLabel.repaint();
                    }
                }
            });
            musicAnimTimer.setCoalesce(true);
        }
        if (!musicAnimTimer.isRunning()) {
            musicAnimTimer.start();
        }
        if (dropInfoLabel != null) {
            dropInfoLabel.repaint();
        }
    }

    private void stopMusic() {
        isPlaying = false;
        if (musicAnimTimer != null && musicAnimTimer.isRunning()) {
            musicAnimTimer.stop();
        }
        if (mp3Player != null) {
            try {
                mp3Player.close();
            } catch (Exception ignored) {
            }
            mp3Player = null;
        }
        if (wavClip != null) {
            try {
                wavClip.stop();
                wavClip.close();
            } catch (Exception ignored) {
            }
            wavClip = null;
        }
        if (dropInfoLabel != null) {
            dropInfoLabel.repaint();
        }
    }

    @Override
    public void removeNotify() {
        stopMusic();
        if (cardTransitionListener != null) {
            FrameTicker.unregister(cardTransitionListener);
            cardTransitionListener = null;
        }
        super.removeNotify();
    }

    private void switchUIByMode() {
        // 当非卡片翻转过渡期调用（如外部状态恢复）时，直接同步 displayMode 与 currentMode
        if (!isTransitioning) {
            displayMode = currentMode;
        }

        modeToggleBtn.updateColor(glassColor);

        // 仅在模式0和模式1下开启撤销按钮，2、3模式置灰禁用
        undoBtn.setEnabled(currentMode == 0 || currentMode == 1);

        if (currentMode == 0) {
            modeToggleBtn.setText("当前模式: 协同改名助手");
            toggleLeftControls(true, true);
        } else if (currentMode == 1) {
            modeToggleBtn.setText("当前模式: 协同批量改名助手");
            toggleLeftControls(true, false);
        } else if (currentMode == 2) {
            modeToggleBtn.setText("当前模式: 农业银行回单命名生成");
            toggleLeftControls(false, false);
        } else if (currentMode == 3) {
            modeToggleBtn.setText("当前模式: 招商银行回单命名生成");
            toggleLeftControls(false, false);
        }
        updatePreview();
        if (dropInfoLabel != null) {
            dropInfoLabel.repaint();
        }
    }

    private void toggleLeftControls(boolean basicControls, boolean supplierControls) {
        biaoCombo.setEnabled(basicControls);
        baoCombo.setEnabled(basicControls);
        typeCombo.setEnabled(basicControls);
        companyCombo.setEnabled(supplierControls);
        newCompanyInput.setEnabled(supplierControls);
    }

    // ✨ 撤销上一次改名逻辑
    private void undoLastRename() {
        if (lastRenamedBatch.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可以撤销的操作！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int successCount = 0;
        List<String> failedFiles = new ArrayList<>();

        // 倒序恢复改名
        for (int i = lastRenamedBatch.size() - 1; i >= 0; i--) {
            File[] pair = lastRenamedBatch.get(i);
            File oldFile = pair[0];
            File newFile = pair[1];

            if (newFile.exists()) {
                if (newFile.renameTo(oldFile)) {
                    successCount++;
                } else {
                    failedFiles.add(newFile.getName());
                }
            } else {
                failedFiles.add(newFile.getName() + " (原文件已移动或丢失)");
            }
        }

        lastRenamedBatch.clear();

        if (failedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "成功撤销 " + successCount + " 个文件的改名操作！", "撤销完成", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "部分文件撤销失败或被占用：\n" + String.join("\n", failedFiles), "撤销提醒", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addControlRow(JPanel p, GridBagConstraints g, int r, String t, Component c) {
        g.gridy = r;
        g.gridx = 0;
        g.weighty = 0;
        g.weightx = 0.3;
        g.gridwidth = 1;
        JLabel l = new JLabel(t);
        l.setForeground(Color.WHITE);
        p.add(l, g);
        g.gridx = 1;
        g.weightx = 0.7;
        p.add(c, g);
    }

    private void updatePreview() {
        if (currentMode == 2) {
            previewLabel.setText("预览：收款方账户-收款方户名-交易时间-金额.pdf");
            return;
        }
        if (currentMode == 3) {
            previewLabel.setText("预览：收款账号-收款人-交易日期-金额.pdf");
            return;
        }
        if (currentMode == 1) {
            previewLabel.setText("预览：" + biaoCombo.getSelectedItem() + baoCombo.getSelectedItem() + "-[自动提取供应商]-" + typeCombo.getSelectedItem());
            return;
        }
        Object selected = companyCombo.getSelectedItem();
        String c = (selected != null) ? selected.toString() : "未选公司";
        if (c.contains("请先")) c = "未选公司";
        previewLabel.setText("预览：" + biaoCombo.getSelectedItem() + baoCombo.getSelectedItem() + "-" + c + "-" + typeCombo.getSelectedItem());
    }

    private void setDropTarget(GlassDropPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                // ✨ 异步唤醒流光引擎
                SwingUtilities.invokeLater(() -> p.setDragActive(true));
            }

            @Override
            public void dragOver(java.awt.dnd.DropTargetDragEvent dtde) {
                // ✨ 保持空实现，彻底杜绝高频重复调用引发的底层死锁
            }

            @Override
            public void dragExit(java.awt.dnd.DropTargetEvent dte) {
                // ✨ 异步关闭特效恢复原状
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

                    // 在 EDT 线程安全地提前快照下拉框状态，避免后台线程跨线程访问 Swing 组件
                    final String biao = String.valueOf(biaoCombo.getSelectedItem());
                    final String bao = String.valueOf(baoCombo.getSelectedItem());
                    final String type = String.valueOf(typeCombo.getSelectedItem());
                    final String comp = String.valueOf(companyCombo.getSelectedItem());
                    final int modeSnapshot = currentMode;

                    // 开启独立虚拟线程剥离耗时任务，彻底解放拖拽线程
                    Thread worker = Thread.ofVirtual().name("Renamer-Worker-Thread").start(() -> {
                        if (modeSnapshot == 2 || modeSnapshot == 3) {
                            handleReceiptDrop(files);
                        } else if (modeSnapshot == 1) {
                            handleBatchDrop(files, biao, bao, type);
                        } else {
                            handleNormalDrop(files, biao, bao, type, comp);
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try { dtde.dropComplete(false); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void handleBatchDrop(List<File> files, String biao, String bao, String type) {
        try {
            lastRenamedBatch.clear();
            List<String> failedFiles = new ArrayList<>();
            int successCount = 0;

            for (File file : files) {
                if (file.isDirectory()) continue;

                String fileName = file.getName();
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";
                String nameNoExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;

                int lastUnder = nameNoExt.lastIndexOf('_');
                if (lastUnder == -1) continue;
                int secondLastUnder = nameNoExt.lastIndexOf('_', lastUnder - 1);
                if (secondLastUnder == -1) continue;

                String autoSupplier = nameNoExt.substring(secondLastUnder + 1, lastUnder);

                String baseName = biao + bao + "-" + autoSupplier + "-" + type;
                File newFile = new File(file.getParent(), baseName + ext);

                if (newFile.exists()) {
                    int count = 1;
                    while (newFile.exists()) {
                        newFile = new File(file.getParent(), baseName + "-复件" + count + ext);
                        count++;
                    }
                }

                if (file.renameTo(newFile)) {
                    successCount++;
                    lastRenamedBatch.add(new File[]{file, newFile});
                } else {
                    failedFiles.add(file.getName());
                }
            }

            if (!failedFiles.isEmpty()) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(RenamerPanel.this, "以下文件被占用或无权限，批量改名失败：\n" + String.join("\n", failedFiles), "部分文件失败", JOptionPane.ERROR_MESSAGE)
                );
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleNormalDrop(List<File> files, String biao, String bao, String type, String comp) {
        try {
            if (comp.contains("请先")) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(RenamerPanel.this, "请先输入并保存供应商名称！", "操作拦截", JOptionPane.WARNING_MESSAGE)
                );
                return;
            }

            lastRenamedBatch.clear();
            List<String> failedFiles = new ArrayList<>();
            List<String> autoRenamedDuplicates = new ArrayList<>();
            int successCount = 0;

            for (File file : files) {
                if (file.isDirectory()) continue;

                String ext = file.getName().contains(".") ? file.getName().substring(file.getName().lastIndexOf(".")) : "";
                String baseName = biao + bao + "-" + comp + "-" + type;
                File newFile = new File(file.getParent(), baseName + ext);

                // ✨ 彻底移除 invokeAndWait 阻塞弹窗，直接无阻塞生成“-复件N”并记录，杜绝拖入多文件时挂起线程
                if (newFile.exists()) {
                    int count = 1;
                    while (newFile.exists()) {
                        newFile = new File(file.getParent(), baseName + "-复件" + count + ext);
                        count++;
                    }
                    autoRenamedDuplicates.add(file.getName() + "  -->  " + newFile.getName());
                }

                if (file.renameTo(newFile)) {
                    successCount++;
                    lastRenamedBatch.add(new File[]{file, newFile});
                } else {
                    failedFiles.add(file.getName());
                }
            }

            // 全部文件瞬间处理完毕后，使用 invokeLater 非阻塞汇总提示一次
            if (!failedFiles.isEmpty() || !autoRenamedDuplicates.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    Window win = SwingUtilities.getWindowAncestor(RenamerPanel.this);
                    if (win != null) win.toFront();

                    if (!autoRenamedDuplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                RenamerPanel.this,
                                "检测到同名文件，已自动为您追加复件编号保存：\n" + String.join("\n", autoRenamedDuplicates) + "\n\n(注：若不希望保留复件，点击左侧「撤销上一步改名」即可一键还原)",
                                "重名自动处理提示",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                    if (!failedFiles.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                RenamerPanel.this,
                                "以下文件被占用或无权限，改名失败：\n" + String.join("\n", failedFiles),
                                "部分文件失败",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleReceiptDrop(List<File> files) {
        try {
            lastRenamedBatch.clear();
            isProcessingReceipt = true;
            SwingUtilities.invokeLater(() -> dropInfoLabel.repaint());

            int successCount = 0;
            for (File fileOrDir : files) {
                if (fileOrDir.isDirectory()) {
                    File outDir = new File(fileOrDir.getParent(), fileOrDir.getName() + "-重命名");
                    if (!outDir.exists()) outDir.mkdirs();

                    File[] pdfs = fileOrDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
                    if (pdfs != null) {
                        for (File pdf : pdfs) {
                            if (splitAndProcessPdf(pdf, outDir)) successCount++;
                        }
                    }
                } else if (fileOrDir.getName().toLowerCase().endsWith(".pdf")) {
                    File outDir = new File(fileOrDir.getParent(), "回单重命名-输出");
                    if (!outDir.exists()) outDir.mkdirs();
                    if (splitAndProcessPdf(fileOrDir, outDir)) successCount++;
                }
            }

            SwingUtilities.invokeLater(() -> {
                isProcessingReceipt = false;
                dropInfoLabel.repaint();
                JOptionPane.showMessageDialog(RenamerPanel.this, "电子回单处理完毕！\n处理流程已完成，请检查输出文件夹。");
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                isProcessingReceipt = false;
                dropInfoLabel.repaint();
                JOptionPane.showMessageDialog(RenamerPanel.this, "处理时发生异常: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private boolean splitAndProcessPdf(File sourcePdf, File outDir) {
        boolean processed = false;
        try (PDDocument document = PDDocument.load(sourcePdf)) {
            int totalPages = document.getNumberOfPages();
            String originalName = sourcePdf.getName().replaceFirst("[.][^.]+$", "");

            if (totalPages > 1) {
                for (int i = 0; i < totalPages; i++) {
                    PDDocument singleDoc = new PDDocument();
                    singleDoc.addPage(document.getPage(i));
                    File tempFile = new File(outDir, originalName + "-page" + (i + 1) + ".pdf");
                    singleDoc.save(tempFile);
                    singleDoc.close();
                    if (processSinglePdfReceipt(tempFile, outDir)) processed = true;
                    tempFile.delete();
                }
            } else {
                processed = processSinglePdfReceipt(sourcePdf, outDir);
            }
        } catch (Exception e) {
            System.err.println("读取文件失败: " + sourcePdf.getName());
            e.printStackTrace();
        }
        return processed;
    }

    private boolean processSinglePdfReceipt(File pdfFile, File outDir) {
        if (currentMode == 3) {
            return processCMBReceipt(pdfFile, outDir);
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {

            final List<TextChunk> chunks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
                    if (textPositions.isEmpty()) return;
                    String text = string.trim();
                    if (!text.isEmpty()) {
                        float x = textPositions.get(0).getXDirAdj();
                        float y = textPositions.get(0).getYDirAdj();
                        chunks.add(new TextChunk(text, x, y, x + textPositions.get(textPositions.size() - 1).getWidthDirAdj()));
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);

            float boundaryX = 200f;
            for (TextChunk c : chunks) {
                if (c.text.contains("收款方")) {
                    boundaryX = c.x - 40;
                    break;
                }
            }

            float rightAccountY = -1;
            float rightNameY = -1;
            float rightBankY = -1;

            for (TextChunk c : chunks) {
                if (c.x > boundaryX) {
                    if (c.text.contains("账号") && rightAccountY == -1) rightAccountY = c.y;
                    if (c.text.contains("户名") && rightNameY == -1) rightNameY = c.y;
                    if (c.text.contains("开户行") && rightBankY == -1) rightBankY = c.y;
                }
            }

            StringBuilder payeeNameBuilder = new StringBuilder();
            float minY = (rightAccountY != -1) ? rightAccountY + 5 : ((rightNameY != -1) ? rightNameY - 15 : 0);
            float maxY = (rightBankY != -1) ? rightBankY - 5 : Float.MAX_VALUE;

            for (TextChunk c : chunks) {
                if (c.x > boundaryX && c.y > minY && c.y < maxY) {
                    String t = c.text.trim();
                    t = t.replace("户名", "").replace("收款方", "").replace("付款方", "").replace("账号", "").replace(":", "").replace("：", "").trim();
                    if (!t.isEmpty()) {
                        payeeNameBuilder.append(t);
                    }
                }
            }

            String payeeName = payeeNameBuilder.toString().replaceAll("\\s+", "");
            if (payeeName.isEmpty()) {
                payeeName = "内部转取或未记载";
            }

            String payeeAccount = "未获取账号";
            if (rightAccountY != -1) {
                for (TextChunk c : chunks) {
                    if (c.x > boundaryX && Math.abs(c.y - rightAccountY) < 15) {
                        Matcher m = Pattern.compile("(\\d{10,30})").matcher(c.text.replaceAll("\\s", ""));
                        if (m.find()) {
                            payeeAccount = m.group(1);
                            break;
                        }
                    }
                }
            }

            StringBuilder globalText = new StringBuilder();
            for (TextChunk c : chunks) globalText.append(c.text).append(" ");
            String gText = globalText.toString();

            String transactionDate = "未知时间";
            Matcher dateM1 = Pattern.compile("交易时间\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})").matcher(gText);
            if (dateM1.find()) {
                transactionDate = dateM1.group(1).replace(":", ".");
            } else {
                Matcher dateM2 = Pattern.compile("交易时间\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(gText);
                if (dateM2.find()) {
                    transactionDate = dateM2.group(1);
                }
            }

            String amount = "0.00";
            Matcher amtM = Pattern.compile("金额[（(]小写[）)]\\s*([\\d,]+\\.\\d{2})").matcher(gText);
            if (amtM.find()) {
                amount = amtM.group(1).replace(",", "");
            }

            String newFileName = String.format("%s-%s-%s-%s.pdf", payeeAccount, payeeName, transactionDate, amount);

            File targetFile = new File(outDir, newFileName);
            int copyIndex = 1;
            while (targetFile.exists()) {
                targetFile = new File(outDir, newFileName.replace(".pdf", "-副本" + copyIndex + ".pdf"));
                copyIndex++;
            }

            Files.copy(pdfFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;

        } catch (Exception e) {
            System.err.println("解析PDF失败: " + pdfFile.getName());
            e.printStackTrace();
            return false;
        }
    }

    private boolean processCMBReceipt(File pdfFile, File outDir) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String gText = stripper.getText(document);

            String payeeAccount = "未获取账号";
            String payeeName = "未获取收款人";
            String transactionDate = "未知时间";
            String amount = "0.00";

            Matcher dateM = Pattern.compile("交易日期[：:]?\\s*(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})").matcher(gText);
            if (dateM.find()) {
                transactionDate = String.format("%04d-%02d-%02d",
                        Integer.parseInt(dateM.group(1)),
                        Integer.parseInt(dateM.group(2)),
                        Integer.parseInt(dateM.group(3)));
            }

            Matcher accM = Pattern.compile("收款账号[：:]?\\s*([0-9a-zA-Z]+)").matcher(gText);
            if (accM.find()) {
                payeeAccount = accM.group(1);
            }

            Matcher nameM = Pattern.compile("收款人[：:]?\\s*([^\\r\\n]+)").matcher(gText);
            if (nameM.find()) {
                payeeName = nameM.group(1).replaceAll("\\s+", "");
            }

            Matcher amtM = Pattern.compile("交易金额[（(]小写[）)][：:]?\\s*[A-Za-z]*\\s*([\\d,\\.]+)").matcher(gText);
            if (amtM.find()) {
                amount = amtM.group(1).replace(",", "");
            }

            String newFileName = String.format("%s-%s-%s-%s.pdf", payeeAccount, payeeName, transactionDate, amount);

            File targetFile = new File(outDir, newFileName);
            int copyIndex = 1;
            while (targetFile.exists()) {
                targetFile = new File(outDir, newFileName.replace(".pdf", "-副本" + copyIndex + ".pdf"));
                copyIndex++;
            }

            Files.copy(pdfFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;

        } catch (Exception e) {
            System.err.println("解析招商银行PDF失败: " + pdfFile.getName());
            e.printStackTrace();
            return false;
        }
    }

    private Vector<String> generateList(String p, int c) {
        Vector<String> v = new Vector<>();
        for (int i = 1; i <= c; i++) v.add(p + i);
        return v;
    }
}