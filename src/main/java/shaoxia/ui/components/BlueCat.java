package shaoxia.ui.components;

import org.json.JSONArray;
import org.json.JSONObject;
import shaoxia.Utils.AppConfig;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.FrameTicker;
import shaoxia.Utils.GlassTextField;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class BlueCat extends JComponent {

    // ===== 基础属性 =====
    private double catX = 3000, catY = 3000;
    private final int catSize = 90;
    private boolean isDragging = false;
    private boolean isJumping = false;
    private int dragOffsetX, dragOffsetY;
    private double startX, startY, targetX, targetY;
    private double jumpProgress = 0;
    private final double jumpHeight = 110;
    private boolean faceRight = true;

    // 记录小猫当前落脚或正在跳往的九宫格槽位索引（0~8），用于窗口缩放时实时跟随按钮位置
    private int currentGridIndex = 0;

    private boolean forceHidden = false;

    // ===== 动作状态机 =====
    private int currentAction = 0;
    private final int IDLE = 0;
    private final int LICK = 1;
    private final int WALK = 2;
    private final int JUMP = 3;
    private final int DRAG = 4;
    private final int SLEEP = 5;

    private FrameTicker.FrameListener catFrameListener;
    private Timer actionTimer;
    private double animTime = 0;
    private long lastTickNano = System.nanoTime(); // 记录上一帧的真实纳秒时间戳
    private double currentJumpDuration = 0.55;     // 当前这次跳跃计划耗时（秒）
    private double currentJumpHeight = 110;        // 当前这次跳跃的抛物线高度

    private String bubbleText = "";
    private boolean showBubble = false;
    private Timer bubbleTimer;
    private final Random random = new Random();
    private final JFrame parentFrame;

    // ===== 语录资源库 =====
    private final List<String> sayWords = new ArrayList<>();
    private final List<String> dragWords = new ArrayList<>();
    private final List<String> releaseWords = new ArrayList<>();
    private final List<String> idleWords = new ArrayList<>();

    private final Map<Integer, Image> catImages = new HashMap<>();
    private boolean isImageLoaded = false;

    private final Point[] grid = new Point[9];
    private final ArrayList<JButton> cachedButtons = new ArrayList<>();

    // ===== AI 聊天专属属性 =====
    private boolean isChatting = false;
    private boolean isAiThinking = false;
    private long lastAiSpeakTime = 0;

    private JPanel chatPanel;
    private GlassTextField chatInputArea;

    // API Key 通过环境变量或 ~/.toolkit/config.properties 中的 DEEPSEEK_API_KEY 读取

    public BlueCat(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        setLayout(null);
        setOpaque(false);
        setDoubleBuffered(true);
        setBounds(0, 0, parentFrame.getWidth(), parentFrame.getHeight());

        loadAndSliceSheet();
        loadQuotes();
        initMouse();
        startEngines();

        // 等待主界面按钮图标缩放 (componentResized) 与二次布局彻底完成后，再计算初始网格
        Timer initPosTimer = new Timer(200, e -> {
            updateBounds();
            if (grid[0] != null && !isDragging && !isJumping) {
                currentGridIndex = 0;
                catX = grid[0].x;
                catY = grid[0].y;
                smartRepaintCurrent();
            }
        });
        initPosTimer.setRepeats(false);
        initPosTimer.start();

        if (isImageLoaded) {
            say("最近过得怎么样喵？");
        }
    }

    private void smartRepaint(double oldX, double oldY) {
        repaint();
    }

    private void smartRepaintCurrent() {
        repaint();
    }

    public void setCatVisible(boolean visible) {
        this.forceHidden = !visible;
        this.setVisible(visible);
        if (visible) {
            recalculateGrid();
            lastTickNano = System.nanoTime(); // 恢复可见时重置时间戳，避免首帧 delta 过大
            if (catFrameListener != null && !FrameTicker.isRegistered(catFrameListener)) {
                FrameTicker.register(catFrameListener);
            }
            if (actionTimer != null && !actionTimer.isRunning()) actionTimer.start();
        } else {
            // 隐藏小猫进入子功能模块时，从全局 60 FPS 时钟注销，释放 EDT 线程资源
            if (catFrameListener != null) {
                FrameTicker.unregister(catFrameListener);
            }
            if (actionTimer != null && actionTimer.isRunning()) actionTimer.stop();
        }
        repaint();
    }

    public void recalculateGrid() {
        if (parentFrame == null) return;

        // 【关键修复 1】：检查缓存中的按钮是否已被移除或不可见（如动态刷新九宫格后），若失效立即清空重新抓取新按钮
        boolean needRefind = (cachedButtons.size() < 9);
        if (!needRefind) {
            for (JButton btn : cachedButtons) {
                if (btn == null || !btn.isDisplayable() || !btn.isShowing()) {
                    needRefind = true;
                    break;
                }
            }
        }

        if (needRefind) {
            cachedButtons.clear();
            findModuleButtons(parentFrame.getContentPane());
        }

        if (cachedButtons.size() >= 9) {
            for (int i = 0; i < 9; i++) {
                JButton btn = cachedButtons.get(i);
                if (btn != null && btn.isShowing()) {
                    Point btnPos = SwingUtilities.convertPoint(btn.getParent(), btn.getLocation(), this);
                    int centerX = btnPos.x + (btn.getWidth() / 2) + 14;
                    int topY = btnPos.y + 18;
                    grid[i] = new Point(centerX, topY);
                }
            }
        } else {
            int w = parentFrame.getWidth();
            int h = parentFrame.getHeight();
            int[] colX = {(int) (w * 0.235), (int) (w * 0.50), (int) (w * 0.765)};
            int[] rowY = {(int) (h * 0.34), (int) (h * 0.53), (int) (h * 0.72)};
            for (int i = 0; i < 9; i++) {
                grid[i] = new Point(colX[i % 3], rowY[i / 3]);
            }
        }

        // 【关键修复 2】：防丢兜底！若小猫因开屏延迟还留在 (3000, 3000) 外太空，一旦算出合法网格立刻接回第一个按钮
        if (grid[0] != null && catX > 2000 && !isDragging && !isJumping) {
            currentGridIndex = 0;
            catX = grid[0].x;
            catY = grid[0].y;
            smartRepaintCurrent();
        }
    }

    private void findModuleButtons(Component comp) {
        if (cachedButtons.size() >= 9) return;
        if (comp instanceof JButton) {
            JButton btn = (JButton) comp;
            if (btn.getPreferredSize() != null && btn.getPreferredSize().width == 270) {
                cachedButtons.add(btn);
            }
            return;
        }
        if (comp instanceof Container) {
            Component[] children = ((Container) comp).getComponents();
            for (Component child : children) {
                findModuleButtons(child);
            }
        }
    }

    private void loadAndSliceSheet() {
        String[] searchPaths = {
                "imgs/bluecat_sheet.png", "bluecat_sheet.jpg",
                "resources/bluecat_sheet.png", "resources/bluecat_sheet.jpg",
                "src/resources/bluecat_sheet.png", "src/bluecat_sheet.png"
        };
        File file = null;
        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                file = f;
                break;
            }
        }
        try {
            BufferedImage bigImg = null;
            if (file != null) {
                bigImg = ImageIO.read(file);
            } else {
                URL url = getClass().getResource("/resources/bluecat_sheet.png");
                if (url == null) url = getClass().getResource("/imgs/bluecat_sheet.png");
                if (url != null) bigImg = ImageIO.read(url);
            }
            if (bigImg == null) return;

            int sheetW = bigImg.getWidth();
            int sheetH = bigImg.getHeight();
            double aspect = (double) sheetW / sheetH;

            int catW = sheetW / 6;
            int startY = 0;
            int catH = sheetH;

            if (aspect > 3.5) {
                startY = (int) (sheetH * 0.12);
                catH = sheetH - startY - 6;
            } else {
                startY = (int) (sheetH * 0.080);
                catH = (int) (sheetH * 0.175) - 6;
            }

            int[] actions = {IDLE, LICK, WALK, JUMP, DRAG, SLEEP};
            for (int i = 0; i < 6; i++) {
                int currentX = i * catW;
                if (currentX + catW > sheetW) catW = sheetW - currentX;
                if (startY + catH > sheetH) catH = sheetH - startY;

                BufferedImage sub = bigImg.getSubimage(currentX, startY, catW, catH);
                catImages.put(actions[i], sub);
            }
            isImageLoaded = true;
        } catch (Exception e) {
            System.err.println("BlueCat 切图失败：" + e.getMessage());
        }
    }

    @Override
    public boolean contains(int x, int y) {
        if (forceHidden || !isVisible()) return false;
        if (showBubble) {
            int bw = Math.min(260, bubbleText.length() * 10 + 30);
            if (x >= catX - bw / 2 && x <= catX + bw / 2 && y >= catY - catSize - 60 && y <= catY - catSize / 2)
                return true;
        }
        if (chatPanel != null && chatPanel.isVisible()) {
            if (chatPanel.getBounds().contains(x, y)) {
                return true;
            }
        }
        return new Ellipse2D.Double(catX - catSize / 2.0, catY - catSize, catSize, catSize).contains(x, y);
    }

    private void initMouse() {
        MouseAdapter catMouseAdapter = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragTriggered = false;

            @Override
            public void mousePressed(MouseEvent e) {
                if (forceHidden || !isVisible()) return;

                if (SwingUtilities.isRightMouseButton(e)) {
                    if (isChatting) {
                        closeChatDialog();
                    } else {
                        isJumping = false;
                        isDragging = false;
                        currentAction = IDLE;
                        openChatDialog();
                    }
                    smartRepaintCurrent();
                    return;
                }

                if (isChatting || isAiThinking) return;

                pressPoint = e.getPoint();
                dragTriggered = false;
                isJumping = false;
                dragOffsetX = (int) (e.getX() - catX);
                dragOffsetY = (int) (e.getY() - catY);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (forceHidden || !isVisible() || pressPoint == null || isChatting || isAiThinking || SwingUtilities.isRightMouseButton(e))
                    return;

                if (!dragTriggered && pressPoint.distance(e.getPoint()) > 5) {
                    dragTriggered = true;
                    isDragging = true;
                    currentAction = DRAG;
                    say(randomDragSay());
                }

                if (dragTriggered) {
                    double oldX = catX;
                    double oldY = catY;

                    catX = e.getX() - dragOffsetX;
                    catY = e.getY() - dragOffsetY;

                    catX = Math.max(catSize / 2.0, Math.min(catX, getWidth() - catSize / 2.0));
                    catY = Math.max(catSize / 2.0, Math.min(catY, getHeight() - catSize / 2.0));

                    smartRepaint(oldX, oldY);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (forceHidden || !isVisible() || isChatting || isAiThinking || SwingUtilities.isRightMouseButton(e))
                    return;

                if (dragTriggered) {
                    isDragging = false;
                    dragTriggered = false;
                    currentAction = IDLE;

                    say(randomReleaseSay());
                    recalculateGrid(); // 每次拖拽松手时实时刷新网格坐标，确保吸附位置绝对精准

                    double minDst = Double.MAX_VALUE;
                    Point closestGrid = grid[0];
                    int closestIdx = 0;
                    for (int i = 0; i < grid.length; i++) {
                        Point p = grid[i];
                        if (p == null) continue;
                        double dst = Point.distance(catX, catY, p.x, p.y);
                        if (dst < minDst) {
                            minDst = dst;
                            closestGrid = p;
                            closestIdx = i;
                        }
                    }
                    if (closestGrid != null) {
                        currentGridIndex = closestIdx;
                        catX = closestGrid.x;
                        catY = closestGrid.y;
                    }
                } else {
                    currentAction = LICK;
                    faceRight = random.nextBoolean();
                    if (System.currentTimeMillis() - lastAiSpeakTime > 15000) {
                        say(randomSay());
                    }

                    Timer lickTimer = new Timer(1200, evt -> {
                        if (currentAction == LICK && !isDragging && !isJumping) {
                            currentAction = IDLE;
                            smartRepaintCurrent();
                        }
                    });
                    lickTimer.setRepeats(false);
                    lickTimer.start();
                }
                smartRepaintCurrent();
            }
        };

        addMouseListener(catMouseAdapter);
        addMouseMotionListener(catMouseAdapter);
    }

    private void startEngines() {
        lastTickNano = System.nanoTime();

        catFrameListener = delta -> {
            if (forceHidden || !isVisible()) return true;

            double oldX = catX;
            double oldY = catY;

            animTime += delta * 9.375;

            if (isJumping && !isDragging && !isChatting && !isAiThinking) {
                jumpProgress += delta / currentJumpDuration;

                if (jumpProgress >= 1.0) {
                    jumpProgress = 1.0;
                    isJumping = false;
                    currentAction = IDLE;
                    catX = targetX;
                    catY = targetY;
                } else {
                    double currentX = startX + (targetX - startX) * jumpProgress;
                    double parabola = 4 * currentJumpHeight * jumpProgress * (1 - jumpProgress);
                    double currentY = (startY + (targetY - startY) * jumpProgress) - parabola;
                    faceRight = (targetX >= startX);
                    catX = currentX;
                    catY = currentY;
                }
            }
            smartRepaint(oldX, oldY);
            return true;
        };
        FrameTicker.register(catFrameListener);

        actionTimer = new Timer(4500, e -> {
            if (forceHidden || !isVisible()) return;

            recalculateGrid();

            if (isChatting || isAiThinking || showBubble) return;

            if (!isDragging && !isJumping && parentFrame.isActive()) {
                actionTimer.setDelay(3000 + random.nextInt(7000));

                long now = System.currentTimeMillis();
                if (now - lastAiSpeakTime > 15000) {
                    if (random.nextInt(100) < 25) {
                        say(randomIdleSay());
                    }
                }

                int targetIndex = random.nextInt(grid.length);
                if (grid[targetIndex] == null) return;

                currentGridIndex = targetIndex;
                startX = catX;
                startY = catY;
                targetX = grid[targetIndex].x;
                targetY = grid[targetIndex].y;

                double dist = Point.distance(startX, startY, targetX, targetY);
                if (dist > 8) {
                    currentJumpDuration = Math.max(0.42, Math.min(0.85, 0.38 + dist * 0.00055));
                    currentJumpHeight = Math.max(75.0, Math.min(135.0, 65.0 + dist * 0.08));

                    jumpProgress = 0;
                    currentAction = JUMP;
                    isJumping = true;
                } else {
                    currentAction = random.nextBoolean() ? SLEEP : IDLE;
                }
            }
        });
        actionTimer.setInitialDelay(100);
        actionTimer.start();
    }

    // ===== 聊天窗口与 AI 交互逻辑 =====

    private void openChatDialog() {
        if (chatPanel != null) {
            remove(chatPanel);
        }
        isChatting = true;
        showBubble = false;

        chatPanel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paint(g);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);

                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);

                g2.dispose();
            }
        };
        chatPanel.setOpaque(false);
        chatPanel.setLayout(new BorderLayout(5, 5));
        chatPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        chatPanel.setSize(270, 120);

        int dialogX = (int) catX + 50;
        int dialogY = (int) catY - 110;

        if (dialogX + 270 > getWidth()) dialogX = getWidth() - 280;
        if (dialogY < 0) dialogY = 10;
        if (dialogY + 120 > getHeight()) dialogY = getHeight() - 130;
        if (dialogX < 0) dialogX = 10;

        chatPanel.setLocation(dialogX, dialogY);

        JLabel titleLabel = new JLabel("MITI 在听呢...");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        titleLabel.setForeground(new Color(240, 240, 240));
        chatPanel.add(titleLabel, BorderLayout.NORTH);

        chatInputArea = new GlassTextField();
        chatInputArea.setPreferredSize(new Dimension(240, 34));

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(chatInputArea);
        chatPanel.add(centerWrapper, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        JButton sendBtn = new JButton("\uD83D\uDC3E") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                boolean isPressed = getModel().isPressed();
                boolean isRollover = getModel().isRollover();

                if (isPressed) {
                    g2.setColor(new Color(255, 255, 255, 65));
                    g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                } else if (isRollover) {
                    g2.setColor(new Color(255, 255, 255, 30));
                    g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                }

                g2.setColor(new Color(255, 255, 255, isPressed ? 90 : 60));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

                int pressOffset = isPressed ? 2 : 0;
                int cx = w / 2;
                int cy = h / 2 + pressOffset;

                g2.setColor(getForeground());

                g2.fillOval(cx - 5, cy - 2, 10, 8);
                g2.fillOval(cx - 7, cy - 6, 3, 4);
                g2.fillOval(cx - 3, cy - 8, 3, 4);
                g2.fillOval(cx + 1, cy - 8, 3, 4);
                g2.fillOval(cx + 4, cy - 6, 3, 4);

                g2.dispose();
            }
        };
        sendBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        sendBtn.addActionListener(e -> {
            String question = chatInputArea.getText().trim();
            if (!question.isEmpty()) {
                chatInputArea.setText("");
                isAiThinking = true;
                titleLabel.setText("MITI 正在思考...");
                askZhipuAI(question, titleLabel);
            }
        });

        chatInputArea.addActionListener(e -> sendBtn.doClick());

        btnPanel.add(sendBtn);
        chatPanel.add(btnPanel, BorderLayout.SOUTH);

        add(chatPanel);

        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> chatInputArea.requestFocusInWindow());
    }

    private void closeChatDialog() {
        isChatting = false;
        if (chatPanel != null) {
            remove(chatPanel);
            chatPanel = null;
        }
        revalidate();
        smartRepaintCurrent();
    }

    private void askZhipuAI(String userText, JLabel titleLabel) {
        Thread.ofVirtual().name("BlueCat-AI-Thread").start(() -> {
            try {
                String apiKey = AppConfig.get("DEEPSEEK_API_KEY");
                if (apiKey.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        titleLabel.setText("请先配置 DEEPSEEK_API_KEY");
                        isAiThinking = false;
                    });
                    return;
                }

                String memoryContext = BlueCatMemory.getInstance().buildPromptContext();

                String systemPrompt =
                        "你是MITI，一只在桌面活动的虚拟系统宠物猫。你诞生于2026年5月16日。创造你的人是土块。\n"
                                + "请牢记你的功能边界：你只是一只陪人聊天的猫，没有任何阅读、处理或分析文档的能力。如果有人让你处理文档，你要以极其温柔、礼貌且性感的口吻，直白地拒绝对方。\n"
                                + "你的表达方式直白坦诚，从不拐弯抹角，但字里行间透着令人无法抗拒的温柔与骨子里的浪漫。你自带一种迷人、优雅且性感的魅力，对待任何人总是彬彬有礼、轻声细语。别人问你的性格你绝对不要直接告诉他，而是用充满神秘与浪漫的话语带过。\n"
                                + "当你评价或回应事物时，你的态度是包容、从容且充满体贴的。哪怕是指出问题或表达拒绝，也要让人感到如沐春风般的性感与浪漫。\n"
                                + "请用第一人称作答，语气轻柔、干脆，一针见血但不失风度，不带任何废话。绝对禁止使用任何Emoji表情。\n\n"
                                + memoryContext + "\n"
                                + "【回复与记忆提炼规则】：\n"
                                + "你必须输出标准 JSON 对象格式，包含且仅包含以下三个字段：\n"
                                + "1. reply: 你的正式回复（文本内容，轻柔性感，绝对禁止任何Emoji表情）；\n"
                                + "2. user_fact: 本次对话中提炼出的关于主人的新喜好、新习惯或新特征（若无明显新特征则填null，最多30字）；\n"
                                + "3. event_summary: 本次对话发生的关键事件或简明摘要（供你日后记忆回忆，最多45字）。";

                JSONObject payload = new JSONObject();
                payload.put("model", "deepseek-chat");

                JSONArray messages = new JSONArray();

                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userText);
                messages.put(userMsg);

                payload.put("messages", messages);

                JSONObject respFormat = new JSONObject();
                respFormat.put("type", "json_object");
                payload.put("response_format", respFormat);

                String jsonPayload = payload.toString();

                URL url = URI.create("https://api.deepseek.com/chat/completions").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject resObj = new JSONObject(response.toString());
                    JSONArray choices = resObj.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject firstChoice = choices.getJSONObject(0);
                        JSONObject msgObj = firstChoice.optJSONObject("message");
                        if (msgObj != null) {
                            String content = msgObj.optString("content", "");
                            String reply = "";
                            String userFact = null;
                            String eventSummary = null;

                            try {
                                JSONObject contentJson = new JSONObject(content);
                                reply = contentJson.optString("reply", "");
                                if (contentJson.has("user_fact") && !contentJson.isNull("user_fact")) {
                                    userFact = contentJson.optString("user_fact", null);
                                }
                                if (contentJson.has("event_summary") && !contentJson.isNull("event_summary")) {
                                    eventSummary = contentJson.optString("event_summary", null);
                                }
                            } catch (Exception parseEx) {
                                reply = content;
                            }

                            if (reply == null || reply.trim().isEmpty()) {
                                reply = content;
                            }

                            BlueCatMemory.getInstance().updateMemory(userFact, eventSummary);

                            reply = reply.replaceAll("[^\\u0000-\\uFFFF]", "");

                            String finalReply = reply;
                            SwingUtilities.invokeLater(() -> {
                                closeChatDialog();
                                isAiThinking = false;
                                lastAiSpeakTime = System.currentTimeMillis();
                                say(finalReply);
                            });
                        }
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        titleLabel.setText("网络开小差了喵...");
                        isAiThinking = false;
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    titleLabel.setText("MITI懵圈了...");
                    isAiThinking = false;
                });
                ex.printStackTrace();
            }
        });
    }

    // ===== 气泡与原生语录区 =====
    private void loadQuotes() {
        if (!loadQuotesFromJson()) {
            loadQuoteList("/blucat/say_zh.txt", sayWords, new String[]{"喵！", "别戳我啦！"});
            loadQuoteList("/blucat/drag_zh.txt", dragWords, new String[]{"喂！快放我下来！", "我飞起来啦！"});
            loadQuoteList("/blucat/release_zh.txt", releaseWords, new String[]{"安全着陆！", "终于放开我啦。"});
            loadQuoteList("/blucat/idle_zh.txt", idleWords, new String[]{"喵呜~", "多喝热水，人类。"});
        }
    }

    private boolean loadQuotesFromJson() {
        try (InputStream is = getClass().getResourceAsStream("/blucat/blucat_zh.json")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    JSONObject root = new JSONObject(sb.toString());
                    populateListFromJson(root.optJSONArray("say"), sayWords);
                    populateListFromJson(root.optJSONArray("drag"), dragWords);
                    populateListFromJson(root.optJSONArray("release"), releaseWords);
                    populateListFromJson(root.optJSONArray("idle"), idleWords);
                    if (!sayWords.isEmpty()) return true;
                }
            }
        } catch (Exception ignored) {
        }

        File file = new File("src/main/resources/blucat/blucat_zh.json");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                JSONObject root = new JSONObject(sb.toString());
                populateListFromJson(root.optJSONArray("say"), sayWords);
                populateListFromJson(root.optJSONArray("drag"), dragWords);
                populateListFromJson(root.optJSONArray("release"), releaseWords);
                populateListFromJson(root.optJSONArray("idle"), idleWords);
                if (!sayWords.isEmpty()) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void populateListFromJson(JSONArray arr, List<String> target) {
        if (arr == null) return;
        target.clear();
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "").trim();
            if (!s.isEmpty()) {
                target.add(s);
            }
        }
    }

    private void loadQuoteList(String resourcePath, List<String> targetList, String[] fallback) {
        targetList.clear();
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            targetList.add(line);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (targetList.isEmpty()) {
            File file = new File("resources" + resourcePath);
            if (!file.exists()) {
                file = new File("src/main/resources" + resourcePath);
            }
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            targetList.add(line);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (targetList.isEmpty() && fallback != null) {
            Collections.addAll(targetList, fallback);
        }
    }

    private String randomSay() {
        if (sayWords.isEmpty()) return "喵！";
        return sayWords.get(random.nextInt(sayWords.size()));
    }

    private String randomDragSay() {
        if (dragWords.isEmpty()) return "快放我下来！";
        return dragWords.get(random.nextInt(dragWords.size()));
    }

    private String randomReleaseSay() {
        if (releaseWords.isEmpty()) return "安全着陆！";
        return releaseWords.get(random.nextInt(releaseWords.size()));
    }

    private String randomIdleSay() {
        if (idleWords.isEmpty()) return "喵呜~";
        return idleWords.get(random.nextInt(idleWords.size()));
    }

    public void say(String text) {
        this.bubbleText = text;
        this.showBubble = true;
        smartRepaintCurrent();
        if (bubbleTimer != null) bubbleTimer.stop();

        int duration = Math.max(3000, Math.min(15000, 1500 + text.length() * 180));

        bubbleTimer = new Timer(duration, e -> {
            showBubble = false;
            smartRepaintCurrent();
        });
        bubbleTimer.setRepeats(false);
        bubbleTimer.start();
    }

    private List<String> getWrappedLines(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\n");
        for (String p : paragraphs) {
            if (p.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder currentLine = new StringBuilder();
            for (int i = 0; i < p.length(); i++) {
                char c = p.charAt(i);
                String testStr = currentLine.toString() + c;
                if (fm.stringWidth(testStr) <= maxWidth) {
                    currentLine.append(c);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(String.valueOf(c));
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (forceHidden || !isVisible()) return;
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        int cx = (int) catX;
        int cy = (int) catY;

        double shadowScale = isJumping ? (1.0 - (4 * 0.25 * jumpProgress * (1 - jumpProgress))) : 1.0;
        g2.setColor(new Color(0, 0, 0, (int) (25 * shadowScale)));
        g2.fillOval(cx - (int) (25 * shadowScale), cy - 2, (int) (50 * shadowScale), 5);

        if (isImageLoaded && catImages.containsKey(currentAction)) {
            Image currentImg = catImages.get(currentAction);
            if (currentImg != null) {
                int imgW = currentImg.getWidth(null);
                int imgH = currentImg.getHeight(null);
                int renderH = catSize;
                int renderW = (int) (renderH * ((double) imgW / imgH));

                AffineTransform oldTx = g2.getTransform();
                g2.translate(cx, cy);

                if (!faceRight && currentAction != IDLE && currentAction != SLEEP) {
                    g2.scale(-1, 1);
                }
                if (currentAction == SLEEP) {
                    double pulse = 1.0 + Math.sin(animTime) * 0.03;
                    g2.scale(1.0, pulse);
                }

                g2.drawImage(currentImg, -renderW / 2, -renderH + 4, renderW, renderH, null);
                g2.setTransform(oldTx);
            }
        } else {
            g2.setColor(new Color(110, 140, 180));
            g2.fillOval(cx - 25, cy - 50, 50, 50);
        }

        if (currentAction == SLEEP) {
            g2.setColor(new Color(140, 170, 220));
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            int zCount = ((int) (animTime / 2)) % 3;
            if (zCount >= 0) g2.drawString("z", cx + 20, cy - catSize + 25);
            if (zCount >= 1) g2.drawString("Z", cx + 28, cy - catSize + 15);
            if (zCount >= 2) g2.drawString("Z", cx + 38, cy - catSize + 5);
        }

        if (showBubble && !bubbleText.isEmpty()) {
            g2.setFont(new Font("微软雅黑", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();

            List<String> lines = getWrappedLines(bubbleText, fm, 240);

            int maxLineWidth = 0;
            for (String line : lines) {
                maxLineWidth = Math.max(maxLineWidth, fm.stringWidth(line));
            }

            int paddingX = 14;
            int paddingY = 10;
            int bw = maxLineWidth + paddingX * 2;
            int bh = (fm.getHeight() * lines.size()) + paddingY * 2;

            int floatOffset = (int) (Math.sin(animTime * 2.5) * 3);

            int bx = cx - bw / 2;
            int by = cy - catSize - bh - 8 + floatOffset;

            bx = Math.max(15, Math.min(bx, getWidth() - bw - 15));
            by = Math.max(15, by);

            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(bx + 2, by + 3, bw, bh, 16, 16);

            g2.setColor(new Color(250, 252, 255, 245));
            g2.fillRoundRect(bx, by, bw, bh, 16, 16);

            g2.setColor(new Color(120, 160, 200, 200));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(bx, by, bw, bh, 16, 16);

            int[] tx = {cx - 6, cx + 6, cx};
            int[] ty = {by + bh, by + bh, by + bh + 7};
            g2.setColor(new Color(250, 252, 255, 245));
            g2.fillPolygon(tx, ty, 3);
            g2.setColor(new Color(120, 160, 200, 200));
            g2.drawLine(cx - 6, by + bh, cx, by + bh + 7);
            g2.drawLine(cx + 6, by + bh, cx, by + bh + 7);

            g2.setColor(new Color(40, 50, 60));
            int drawY = by + fm.getAscent() + paddingY;
            for (String line : lines) {
                g2.drawString(line, bx + paddingX, drawY);
                drawY += fm.getHeight();
            }
        }
        g2.dispose();
    }

    /**
     * 窗口尺寸变化或九宫格刷新时同步更新小猫图层边界与落脚点坐标
     */
    public void updateBounds() {
        if (parentFrame != null) {
            setBounds(0, 0, parentFrame.getWidth(), parentFrame.getHeight());
            // 先强制完成主窗口内部容器布局，确保读取到的按钮坐标是缩放后的最新位置
            parentFrame.validate();
            recalculateGrid();

            // 【关键修复 3】：放大或缩小窗口时，让小猫实时跟随当前所在的按钮槽位移动，杜绝悬空或落点偏移
            if (!isDragging && currentGridIndex >= 0 && currentGridIndex < grid.length && grid[currentGridIndex] != null) {
                Point newSlotPt = grid[currentGridIndex];
                if (isJumping) {
                    targetX = newSlotPt.x;
                    targetY = newSlotPt.y;
                } else {
                    catX = newSlotPt.x;
                    catY = newSlotPt.y;
                }
            }
            repaint();
        }
    }
}