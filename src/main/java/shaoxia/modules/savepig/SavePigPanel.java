package shaoxia.modules.savepig;

import shaoxia.Utils.FontUtils;
import shaoxia.modules.BackgroundPanel;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 拯救公猪游戏 - 终极优化版 (支持全屏自适应拖拽、专属设置项与背景音乐分离)
 * * ✨ 角色设定与站位 (从左到右):
 * 1. 盗贼/忍者 (Thief): 位于最左侧后方，伺机追击并投掷匕首
 * 2. 骑士 (Knight): 玩家控制的中间主角 (Dino)
 * 3. 小偷 (Robber): 位于最右侧前方，扛着被变成猪的公主逃跑 (Orc)
 */
public class SavePigPanel extends BackgroundPanel implements ActionListener {

    private Timer timer;
    private Random rand = new Random();

    // 多线程相关
    private volatile boolean running = true;
    private Thread logicThread;
    private final Object logicLock = new Object();

    // 核心状态开关
    private boolean isMainMenu = true;
    private boolean isSettingsMenu = false;
    private boolean isPrologue = false;
    private long prologueStartTime = 0;
    private boolean isPaused = false;
    private boolean isEndlessMode = false;

    // 排行榜状态
    private boolean isLeaderboard = false;
    private boolean isLoadingLeaderboard = false;
    private List<RankApiClient.ScoreRecord> topScores = null;

    private boolean isStarted = false;
    private boolean isOver = false;
    private boolean isBossMode = false;

    // 胜利/死亡动画相关
    private boolean isVictory = false;
    private long victoryStartTime = 0;
    private boolean thiefCrashed = false;

    // --- 速度与分数变量 (难度挂钩) ---
    private double gameSpeed = 7.0;
    private double ACCELERATION = 0.0018;
    private double MAX_SPEED = 26.0;
    private int difficulty = 1; // 0:简单, 1:普通, 2:困难
    private double score = 0;
    private int screenShake = 0;

    // --- 生命周期与护盾系统 ---
    private int lives = 3;
    private boolean isShieldActive = false;
    private long shieldStartTime = 0;
    private long lastShieldUseTime = -99999;
    private static final long SHIELD_COOLDOWN = 30000;
    private long hitInvincibilityTime = 0;

    // 动态距离变量
    private double currentOrcOffset = 350;
    private double currentThiefOffset = -800; // ✨ 初始在屏幕极左侧(画面外)
    private double victoryTreeOffset = 320;

    // 物理逻辑
    private double dinoY = 0;
    private double dinoVelY = 0;
    private boolean dinoOnGround = false;
    private boolean isBlockingRight = false;
    private boolean isBlockingLeft = false;

    private double orcY = 0;
    private double orcVelY = 0;

    private double thiefY = 0;
    private double thiefVelY = 0;
    private int thiefThrowTimer = 0;

    // 集合
    private List<Obstacle> obstacles = new ArrayList<>();
    private List<DustParticle> dust = new ArrayList<>();
    private List<Point> clouds = new ArrayList<>();
    private List<Bomb> bombs = new ArrayList<>();
    private List<Dagger> daggers = new ArrayList<>();

    private int bombCooldown = 0;
    private int daggerCooldown = 0;

    // 鼠标交互
    private Point mousePoint = new Point(-1, -1);
    private boolean isMousePressed = false;

    // 窗口拖拽调整变量
    private int cursorType = Cursor.DEFAULT_CURSOR;
    private Point dragStartPos = null;
    private Rectangle dragStartBounds = null;

    // 按钮的碰撞箱
    private Rectangle btnStory = new Rectangle();
    private Rectangle btnEndless = new Rectangle();
    private Rectangle btnRank = new Rectangle();
    private Rectangle btnSettings = new Rectangle();
    private Rectangle btnUpload = new Rectangle();
    private Rectangle btnMenu = new Rectangle();
    private Rectangle btnBack = new Rectangle();
    private Rectangle btnResume = new Rectangle();

    // 结局专属交互按钮
    private Rectangle btnVictoryEndless = new Rectangle();
    private Rectangle btnVictoryMenu = new Rectangle();

    // 设置界面的按钮
    private Rectangle btnDiffEasy = new Rectangle();
    private Rectangle btnDiffNormal = new Rectangle();
    private Rectangle btnDiffHard = new Rectangle();
    private Rectangle btnMusicToggle = new Rectangle();
    private Rectangle btnSettingsBack = new Rectangle();

    // 音乐系统与背景
    private boolean isMusicEnabled = true;
    private MusicThread currentMusicThread = null;

    private int getGroundY() { return Math.max(270, getHeight() - 50); }
    private int getDinoX() { return Math.max(320, getWidth() / 2 - 80); }

    public SavePigPanel() {
        super("bg_imgs/月球与地球.png");
        setDoubleBuffered(true);
        setFocusable(true);
        for (int i = 0; i < 6; i++) clouds.add(new Point(rand.nextInt(1500), 30 + rand.nextInt(50)));

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                int w = getWidth(), h = getHeight(), BORDER = 10;
                boolean left = mousePoint.x < BORDER, right = mousePoint.x > w - BORDER;
                boolean top = mousePoint.y < BORDER, bottom = mousePoint.y > h - BORDER;

                if (top && left) cursorType = Cursor.NW_RESIZE_CURSOR;
                else if (top && right) cursorType = Cursor.NE_RESIZE_CURSOR;
                else if (bottom && left) cursorType = Cursor.SW_RESIZE_CURSOR;
                else if (bottom && right) cursorType = Cursor.SE_RESIZE_CURSOR;
                else if (top) cursorType = Cursor.N_RESIZE_CURSOR;
                else if (bottom) cursorType = Cursor.S_RESIZE_CURSOR;
                else if (left) cursorType = Cursor.W_RESIZE_CURSOR;
                else if (right) cursorType = Cursor.E_RESIZE_CURSOR;
                else cursorType = Cursor.DEFAULT_CURSOR;

                setCursor(Cursor.getPredefinedCursor(cursorType));
                repaint();
            }
            public void mouseDragged(MouseEvent e) {
                mousePoint = e.getPoint();
                Window win = SwingUtilities.getWindowAncestor(SavePigPanel.this);
                if (dragStartPos != null && dragStartBounds != null && win != null) {
                    Point p = e.getLocationOnScreen();
                    int dx = p.x - dragStartPos.x;
                    int dy = p.y - dragStartPos.y;

                    if (cursorType == Cursor.DEFAULT_CURSOR) {
                        win.setLocation(dragStartBounds.x + dx, dragStartBounds.y + dy);
                    } else {
                        Rectangle bounds = new Rectangle(dragStartBounds);
                        if (cursorType == Cursor.E_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR) bounds.width += dx;
                        if (cursorType == Cursor.S_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR) bounds.height += dy;
                        if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR) { bounds.width -= dx; bounds.x += dx; }
                        if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR) { bounds.height -= dy; bounds.y += dy; }

                        int minWidth = 640, minHeight = 520;
                        if (bounds.width < minWidth) { bounds.width = minWidth; if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR) bounds.x = dragStartBounds.x + dragStartBounds.width - minWidth; }
                        if (bounds.height < minHeight) { bounds.height = minHeight; if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR) bounds.y = dragStartBounds.y + dragStartBounds.height - minHeight; }
                        win.setBounds(bounds);
                    }
                }
                repaint();
            }
        });

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                isMousePressed = true;
                dragStartPos = e.getLocationOnScreen();
                Window win = SwingUtilities.getWindowAncestor(SavePigPanel.this);
                if (win != null) dragStartBounds = win.getBounds();
                repaint();
            }
            public void mouseReleased(MouseEvent e) { isMousePressed = false; handleMouseClick(e.getPoint()); repaint(); }
        });

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();

                if (isLeaderboard && code == KeyEvent.VK_SPACE) { isLeaderboard = false; isMainMenu = true; repaint(); return; }
                if (isSettingsMenu) return;

                if (isMainMenu) {
                    if (code == KeyEvent.VK_1) startStoryMode();
                    else if (code == KeyEvent.VK_2) startEndlessMode();
                    else if (code == KeyEvent.VK_3) openLeaderboard();
                    repaint(); return;
                }

                if (isPaused) {
                    if (code == KeyEvent.VK_SPACE) { isPaused = false; if (isMusicEnabled) playMusic(); }
                    repaint(); return;
                }

                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP) {
                    if (isPrologue) {
                        if (System.currentTimeMillis() - prologueStartTime > 8500) {
                            isPrologue = false; isEndlessMode = false; resetGame();
                        }
                    } else if (!isOver && !isStarted) {
                        resetGame();
                    } else if (!isOver && isStarted && (dinoOnGround || dinoY >= getGroundY() - 100)) {
                        dinoVelY = -28.0;
                        dinoOnGround = false;
                        createDust(getDinoX() + 10);
                    }
                }

                if (!isOver && isStarted && !isBossMode && !isPrologue && !isPaused) {
                    if (code == KeyEvent.VK_RIGHT) { isBlockingRight = true; isBlockingLeft = false; }
                    if (code == KeyEvent.VK_LEFT) { isBlockingLeft = true; isBlockingRight = false; }
                    if (code == KeyEvent.VK_DOWN && !isShieldActive && System.currentTimeMillis() - lastShieldUseTime > SHIELD_COOLDOWN) {
                        isShieldActive = true; shieldStartTime = System.currentTimeMillis(); lastShieldUseTime = System.currentTimeMillis();
                    }
                }
            }
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (isVictory) return;
                if (code == KeyEvent.VK_RIGHT) isBlockingRight = false;
                if (code == KeyEvent.VK_LEFT) isBlockingLeft = false;
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) { requestFocusInWindow(); }
        });

        startLogicThread();
        timer = new Timer(16, this);
        timer.start();
    }

    public void stopGame() {
        running = false;
        if (timer != null) {
            timer.stop();
        }
        stopMusic();
    }

    private void applyDifficulty() {
        if (!isEndlessMode) {
            gameSpeed = 7.0;
            MAX_SPEED = 26.0;
            ACCELERATION = 0.0018;
        } else {
            if (difficulty == 0) {
                gameSpeed = 5.5; MAX_SPEED = 20.0; ACCELERATION = 0.0012;
            } else if (difficulty == 1) {
                gameSpeed = 7.0; MAX_SPEED = 26.0; ACCELERATION = 0.0018;
            } else {
                gameSpeed = 12.0; MAX_SPEED = 42.0; ACCELERATION = 0.0045;
            }
        }
    }

    private void updateButtonBounds() {
        int cx = getWidth() / 2; int cy = getHeight() / 2;

        int menuStartY = cy - 30;
        btnStory.setBounds(cx - 100, menuStartY, 200, 40);
        btnEndless.setBounds(cx - 100, menuStartY + 55, 200, 40);
        btnRank.setBounds(cx - 100, menuStartY + 110, 200, 40);
        btnSettings.setBounds(cx - 100, menuStartY + 165, 200, 40);

        btnUpload.setBounds(cx - 180, cy + 30, 170, 45);
        btnMenu.setBounds(cx + 10, cy + 30, 170, 45);

        btnBack.setBounds(cx - 100, getHeight() - 60, 200, 35);
        btnResume.setBounds(cx - 100, cy + 35, 200, 40);

        btnVictoryEndless.setBounds(cx - 180, cy + 60, 170, 45);
        btnVictoryMenu.setBounds(cx + 10, cy + 60, 170, 45);

        btnDiffEasy.setBounds(cx - 150, cy - 50, 90, 40);
        btnDiffNormal.setBounds(cx - 45, cy - 50, 90, 40);
        btnDiffHard.setBounds(cx + 60, cy - 50, 90, 40);
        btnMusicToggle.setBounds(cx - 80, cy + 20, 160, 40);
        btnSettingsBack.setBounds(cx - 100, cy + 90, 200, 40);
    }

    private void handleMouseClick(Point p) {
        if (isMainMenu) {
            if (btnStory.contains(p)) startStoryMode();
            else if (btnEndless.contains(p)) startEndlessMode();
            else if (btnRank.contains(p)) openLeaderboard();
            else if (btnSettings.contains(p)) { isMainMenu = false; isSettingsMenu = true; }
        } else if (isSettingsMenu) {
            if (btnDiffEasy.contains(p)) difficulty = 0;
            else if (btnDiffNormal.contains(p)) difficulty = 1;
            else if (btnDiffHard.contains(p)) difficulty = 2;
            else if (btnMusicToggle.contains(p)) isMusicEnabled = !isMusicEnabled;
            else if (btnSettingsBack.contains(p)) { isSettingsMenu = false; isMainMenu = true; }
        } else if (isVictory && (System.currentTimeMillis() - victoryStartTime > 10000)) {
            if (btnVictoryEndless.contains(p)) startEndlessMode();
            else if (btnVictoryMenu.contains(p)) returnToMainMenu();
        } else if (isOver && !isVictory) {
            if (btnUpload.contains(p)) triggerUploadScore();
            else if (btnMenu.contains(p)) returnToMainMenu();
        } else if (isLeaderboard) {
            if (btnBack.contains(p)) { isLeaderboard = false; isMainMenu = true; }
        } else if (isPaused) {
            if (btnResume.contains(p)) { isPaused = false; if(isMusicEnabled) playMusic(); }
        }
    }

    private void startStoryMode() { isMainMenu = false; isPrologue = true; isEndlessMode = false; score = 0; prologueStartTime = System.currentTimeMillis(); }
    private void startEndlessMode() { isMainMenu = false; isPrologue = false; isEndlessMode = true; resetGame(); }
    private void openLeaderboard() {
        isMainMenu = false; isLeaderboard = true; isLoadingLeaderboard = true; topScores = null;
        RankApiClient.fetchTopScores(scores -> { topScores = scores; isLoadingLeaderboard = false; repaint(); });
    }
    private void returnToMainMenu() { stopMusic(); isPaused = false; isMainMenu = true; isStarted = false; isOver = false; isVictory = false; screenShake = 0; }

    private void triggerUploadScore() {
        String name = JOptionPane.showInputDialog(this, "请输入您的登榜名称！：", "上传战绩", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            RankApiClient.uploadScore(name.trim(), (long) score);
            JOptionPane.showMessageDialog(this, "战绩已发送至云端，稍后请前往排行查阅！");
        }
    }

    private void resetGame() {
        // ✨ 修复1：每次重置游戏（含剧情胜利后进入排名模式）先彻底销毁当前正在播放的音乐（如 end.mp3）
        stopMusic();

        score = 0; screenShake = 0; lives = 3; isShieldActive = false;
        shieldStartTime = 0; lastShieldUseTime = -99999; hitInvincibilityTime = 0; thiefCrashed = false;
        isStarted = true; isOver = false; isBossMode = false; isVictory = false; victoryStartTime = 0;

        applyDifficulty();

        dinoY = getGroundY() - 39; dinoVelY = 0; dinoOnGround = true;
        orcY = getGroundY() - 44; orcVelY = 0;
        thiefY = getGroundY() - 33; thiefVelY = 0; thiefThrowTimer = 0;

        currentOrcOffset = 350;
        currentThiefOffset = -800; // ✨ 初始在屏幕左侧远端，准备在100分时冲入画面

        obstacles.clear(); dust.clear(); bombs.clear(); daggers.clear(); bombCooldown = 120; daggerCooldown = 60;

        // ✨ 重新启动游戏默认背景音乐
        if (isMusicEnabled) playMusic();
        requestFocusInWindow();
    }

    private void playMusic() {
        // 如果当前已有其他曲目（例如结局音乐 end.mp3）在运行，先停止并销毁
        if (currentMusicThread != null && currentMusicThread.isAlive()
                && !"/music/Catch_That_Swine.mp3".equals(currentMusicThread.trackPath)) {
            stopMusic();
        }
        if (currentMusicThread == null || !currentMusicThread.isAlive()) {
            currentMusicThread = new MusicThread("/music/Catch_That_Swine.mp3", true);
            currentMusicThread.start();
        }
    }

    private void playEndMusic() {
        stopMusic();
        if (isMusicEnabled) {
            currentMusicThread = new MusicThread("/music/end.mp3", false);
            currentMusicThread.start();
        }
    }

    private void stopMusic() {
        if (currentMusicThread != null) {
            currentMusicThread.stopMusic();
            currentMusicThread = null;
        }
    }

    /**
     * ✨ 修复2：播放 WAV 撞击音效（支持从 classpath /music/... 或本地 resources/music/... 读取）
     */
    private void playSoundEffect(String wavPath) {
        if (!isMusicEnabled) return;
        Thread.ofVirtual().name("SoundEffectThread").start(() -> {
            try {
                InputStream rawStream = getClass().getResourceAsStream(wavPath);
                if (rawStream == null && wavPath.startsWith("/")) {
                    rawStream = getClass().getResourceAsStream("/resources" + wavPath);
                }

                AudioInputStream audioIn;
                if (rawStream != null) {
                    audioIn = AudioSystem.getAudioInputStream(new BufferedInputStream(rawStream));
                } else {
                    File file = new File("resources" + wavPath);
                    if (!file.exists()) {
                        file = new File("src/main/resources" + wavPath);
                    }
                    if (!file.exists()) {
                        System.out.println("警告: 找不到音效文件 " + wavPath);
                        return;
                    }
                    audioIn = AudioSystem.getAudioInputStream(file);
                }

                AudioFormat baseFormat = audioIn.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                AudioInputStream decodedIn = AudioSystem.isConversionSupported(decodedFormat, baseFormat)
                        ? AudioSystem.getAudioInputStream(decodedFormat, audioIn)
                        : audioIn;

                DataLine.Info info = new DataLine.Info(Clip.class, decodedIn.getFormat());
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.open(decodedIn);
                clip.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void startLogicThread() {
        logicThread = new Thread(() -> {
            while (running) {
                long startTime = System.currentTimeMillis();
                if (isStarted && !isOver && !isBossMode && !isPrologue && !isPaused && !isMainMenu && !isLeaderboard && !isSettingsMenu) {
                    synchronized (logicLock) { runBackgroundLogic(); }
                }
                long sleepTime = 16 - (System.currentTimeMillis() - startTime);
                if (sleepTime > 0) { try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; } }
            }
        }, "GameLogicThread");
        logicThread.setPriority(Thread.MAX_PRIORITY);
        logicThread.start();
    }

    private void runBackgroundLogic() {
        long t = System.currentTimeMillis();
        if (gameSpeed < MAX_SPEED) gameSpeed += ACCELERATION;

        double scoreMultiplier = 1.0;
        if (isEndlessMode) {
            if (difficulty == 0) {
                scoreMultiplier = 0.2;
            } else if (difficulty == 1) {
                scoreMultiplier = 1.0;
            } else if (difficulty == 2) {
                scoreMultiplier = 4.0;
            }
        }
        score += (gameSpeed / 42.0) * scoreMultiplier;

        if (thiefThrowTimer > 0) thiefThrowTimer--;
        if (isShieldActive && (t - shieldStartTime > 10000)) isShieldActive = false;

        // 统一角色跑动逻辑
        if (!isVictory) {
            // Orc(小偷扛猪)的移动
            if (isEndlessMode || score < 1000) {
                currentOrcOffset = 320 + Math.sin(t / 800.0) * 60;
            } else {
                // 结局动画触发逻辑
                double orcRealX = getDinoX() + currentOrcOffset; Obstacle target = null;
                for (Obstacle ob : obstacles) { if (ob.x > orcRealX) { target = ob; break; } }
                if (target == null) { target = new Obstacle(getWidth() + 100); obstacles.add(target); }
                currentOrcOffset += 20; orcRealX = getDinoX() + currentOrcOffset;
                if (orcRealX + 30 >= target.x) {
                    isVictory = true;
                    victoryTreeOffset = target.x - getDinoX();
                    isOver = true;
                    victoryStartTime = System.currentTimeMillis();
                    screenShake = 0;
                    dinoY = getGroundY() - 39; dinoVelY = 0; dinoOnGround = true;

                    dust.clear(); // ✨ 清空现有的灰尘列表，消灭残留圆点
                    bombs.clear(); daggers.clear();

                    // ✨ 播放小偷撞晕音效 + 结局音乐
                    playSoundEffect("/music/小偷撞击.wav");
                    playEndMusic();
                }
            }

            // Thief(忍者/盗贼)的移动与进场逻辑
            if (!thiefCrashed) {
                if (score >= 100) {
                    // ✨ 满100分时，盗贼开始登场入画
                    if (!isEndlessMode && score >= 500) {
                        // 故事模式 500 分后的撞树逻辑
                        double thiefRealX = getDinoX() + currentThiefOffset;
                        boolean dodging = false;
                        for (Obstacle ob : obstacles) {
                            if (ob.x > thiefRealX && ob.x < thiefRealX + 150) {
                                currentThiefOffset += 18; thiefRealX = getDinoX() + currentThiefOffset;
                                if (thiefRealX + 15 >= ob.x) {
                                    thiefCrashed = true;
                                    ob.hasCrashedThief = true;
                                    createDust((int)ob.x);
                                    // ✨ 播放忍者撞到树后的音效
                                    playSoundEffect("/music/忍者撞击.wav");
                                }
                                dodging = true;
                                break;
                            }
                        }
                        if (!dodging) {
                            double hoverOffset = -220 + Math.cos(t / 600.0) * 50;
                            if (currentThiefOffset < hoverOffset - 5) currentThiefOffset += 5;
                            else currentThiefOffset = hoverOffset;
                        }
                    } else {
                        // 正常入场及跟随逻辑：平滑从 -800 追到指定位置
                        double hoverOffset = -220 + Math.cos(t / 600.0) * 50;
                        if (currentThiefOffset < hoverOffset - 5) {
                            currentThiefOffset += 5; // ✨ 奔跑入场速度
                        } else {
                            currentThiefOffset = hoverOffset;
                        }
                    }
                }
            }
        }
        updatePhysics(); handleAIAndEnemies(); handleCollisions();
    }

    private void createDust(int x) { for (int i = 0; i < 6; i++) dust.add(new DustParticle(x, getGroundY())); }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isBossMode) return;
        if (screenShake > 0) screenShake--;

        for (Point p : clouds) {
            p.x -= 1;
            if (p.x < -150) { p.x = getWidth() + rand.nextInt(300); p.y = 30 + rand.nextInt(50); }
        }
        repaint();
    }

    private void updatePhysics() {
        dinoY += dinoVelY;
        double dinoGround = getGroundY() - 39;
        if (dinoY >= dinoGround) {
            if (dinoVelY > 5 && !isVictory) { screenShake = 6; createDust(getDinoX() + 10); }
            dinoY = dinoGround;
            dinoVelY = 0;
            dinoOnGround = true;
        } else {
            dinoVelY += (dinoVelY > 0) ? 6.5 : 3.5;
            dinoOnGround = false;
        }

        orcY += orcVelY;
        double orcGround = getGroundY() - 44;
        if (orcY >= orcGround) { orcY = orcGround; orcVelY = 0; }
        else { orcVelY += (orcVelY > 0) ? 6.5 : 3.5; }

        if (!thiefCrashed) {
            thiefY += thiefVelY;
            double thiefGround = getGroundY() - 33;
            if (thiefY >= thiefGround) { thiefY = thiefGround; thiefVelY = 0; }
            else { thiefVelY += (thiefVelY > 0) ? 6.5 : 3.5; }
        }
    }

    private void handleAIAndEnemies() {
        double orcRealX = getDinoX() + currentOrcOffset; double thiefRealX = getDinoX() + currentThiefOffset;
        if (isEndlessMode || score < 1000) {
            if (bombCooldown > 0) bombCooldown--;
            if (bombCooldown <= 0 && rand.nextInt(100) < (difficulty == 0 ? 1 : 2)) { bombs.add(new Bomb((int) orcRealX, (int) orcY)); bombCooldown = 90 + rand.nextInt(100); }
            if (isEndlessMode || score < 950) {
                for (Obstacle ob : obstacles) {
                    double optimalJumpDist = gameSpeed * 8.5 + 20;
                    if (ob.x > orcRealX && ob.x < orcRealX + optimalJumpDist && orcY >= getGroundY() - 45) {
                        orcVelY = -28.0;
                        break;
                    }
                }
            }
        }
        // ✨ 添加条件 currentThiefOffset > -300：确保盗贼正式跑进画面后才开始跳跃和丢飞镖
        if (score >= 100 && !thiefCrashed && currentThiefOffset > -300) {
            if ((isEndlessMode || score < 470) && thiefY >= getGroundY() - 33 && rand.nextInt(100) < 2) thiefVelY = -28.0;
            if (daggerCooldown > 0) daggerCooldown--;
            if (daggerCooldown <= 0 && rand.nextInt(100) < (difficulty == 2 ? 5 : 3)) {
                daggers.add(new Dagger((int) thiefRealX + 20, (int) thiefY + 12)); daggerCooldown = 60 + rand.nextInt(70); thiefThrowTimer = 15;
            }
        }
    }

    private void takeDamage() {
        if (isShieldActive || System.currentTimeMillis() - hitInvincibilityTime < 2000) return;
        lives--;
        if (lives <= 0) { isOver = true; screenShake = 20; stopMusic(); }
        else { hitInvincibilityTime = System.currentTimeMillis(); screenShake = 15; }
    }

    private void handleCollisions() {
        Rectangle dinoHitbox = new Rectangle(getDinoX() + 12, (int) dinoY - 10, 20, 48);
        Iterator<Obstacle> obIter = obstacles.iterator();
        while (obIter.hasNext()) {
            Obstacle ob = obIter.next();
            if (!isVictory) ob.x -= gameSpeed;
            if (!isVictory && ob.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive) { if (!ob.isKnockedDown) { ob.isKnockedDown = true; score += 15; screenShake = 5; createDust((int)ob.x); } }
                else if (!ob.isKnockedDown) takeDamage();
            }
            if (ob.x < -100) obIter.remove();
        }

        Iterator<Bomb> bIter = bombs.iterator();
        while (bIter.hasNext()) {
            Bomb b = bIter.next();
            if (b.update(gameSpeed)) { bIter.remove(); continue; }
            if (!isVictory && b.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive || isBlockingRight) { bIter.remove(); score += 10; screenShake = 3; } else { takeDamage(); bIter.remove(); }
            }
        }

        Iterator<Dagger> dIter = daggers.iterator();
        while (dIter.hasNext()) {
            Dagger d = dIter.next();
            if (d.update(gameSpeed)) { dIter.remove(); continue; }
            if (!isVictory && d.getBounds().intersects(dinoHitbox)) {
                if (isShieldActive || isBlockingLeft) { dIter.remove(); score += 15; screenShake = 2; } else { takeDamage(); dIter.remove(); }
            }
        }

        if (!isVictory && rand.nextInt(100) < 5 && (obstacles.isEmpty() || obstacles.get(obstacles.size() - 1).x < getWidth() - 400)) {
            obstacles.add(new Obstacle(getWidth()));
        }
        for (int i = 0; i < dust.size(); i++) { if (dust.get(i).update()) dust.remove(i--); }
    }

    private void drawPixelButton(Graphics2D g2, String text, Rectangle bounds, Color baseColor) {
        boolean isHovered = bounds.contains(mousePoint);
        boolean isPressed = isHovered && isMousePressed;

        int x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
        int p = 3;

        Color bgColor = isPressed ? baseColor.darker() : (isHovered ? baseColor.brighter() : baseColor);
        Color shadowColor = new Color(30, 30, 30);
        Color highlightColor = isPressed ? bgColor.darker() : bgColor.brighter().brighter();

        g2.setColor(new Color(0, 0, 0, 100)); g2.fillRect(x + p * 2, y + p * 2, w, h);
        g2.setColor(shadowColor); g2.fillRect(x, y, w, h);
        g2.setColor(bgColor); g2.fillRect(x + p, y + p, w - p * 2, h - p * 2);

        if (!isPressed) {
            g2.setColor(highlightColor); g2.fillRect(x + p, y + p, w - p * 2, p); g2.fillRect(x + p, y + p, p, h - p * 2);
        } else {
            g2.setColor(new Color(0, 0, 0, 60)); g2.fillRect(x + p, y + p, w - p * 2, p * 2); g2.fillRect(x + p, y + p, p * 2, h - p * 2);
        }

        Font font = FontUtils.getPixelFont(Font.BOLD, 18);
        int off = isPressed ? 2 : 0;
        FontUtils.drawCenteredText(g2, text, font, new Color(0, 0, 0, 150), x + off + 1, y + off + 2, w, h);
        FontUtils.drawCenteredText(g2, text, font, Color.WHITE, x + off, y + off, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // ✨ 这句话会呼叫父类 BackgroundPanel，自动把高清缩放+缓存的背景图画到底层！
        super.paintComponent(g);

        updateButtonBounds();

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (isStarted && !isOver) {
            g2.setColor(new Color(10, 15, 20, 100));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        if (screenShake > 0) g2.translate(rand.nextInt(screenShake) - screenShake / 2, rand.nextInt(screenShake) - screenShake / 2);

        if (isStarted && !isOver) {
            g2.setColor(new Color(10, 15, 20, 100));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        if (screenShake > 0) g2.translate(rand.nextInt(screenShake) - screenShake / 2, rand.nextInt(screenShake) - screenShake / 2);

        g2.setColor(new Color(180, 180, 180, 120));
        g2.drawLine(0, getGroundY(), getWidth(), getGroundY());

        if (isMainMenu) { drawMainMenu(g2); return; }
        if (isSettingsMenu) { drawSettingsMenu(g2); return; }
        if (isLeaderboard) { drawLeaderboard(g2); return; }

        if (isVictory && !obstacles.isEmpty()) {
            obstacles.get(obstacles.size() - 1).x = getDinoX() + victoryTreeOffset;
        }
        for (Obstacle ob : obstacles) ob.render(g2);

        if (!isPrologue) {
            for (DustParticle p : dust) p.draw(g2);
            for (Bomb b : bombs) b.render(g2);
            for (Dagger d : daggers) d.render(g2);

            if (isVictory) {
                long elapsed = System.currentTimeMillis() - victoryStartTime;
                drawVictoryCutsceneForeground(g2, elapsed);
            } else if (!isOver) {
                boolean drawKnight = true;
                if (!isVictory && (System.currentTimeMillis() - hitInvincibilityTime < 2000)) { if ((System.currentTimeMillis() % 200) < 100) drawKnight = false; }
                if (drawKnight) drawDino(g2, getDinoX(), (int) dinoY, isShieldActive);

                if (isShieldActive) {
                    long elapsed = System.currentTimeMillis() - shieldStartTime;
                    boolean drawShield = true;
                    if (elapsed > 7000) {
                        int cycle = 500;
                        if (elapsed > 8500) cycle = 250;
                        if (elapsed > 9500) cycle = 100;
                        if ((elapsed % cycle) < (cycle / 2)) drawShield = false;
                    }
                    if (drawShield) {
                        double angle = (elapsed % 300) / 300.0 * Math.PI * 2; int radius = 40;
                        int cx = getDinoX() + 15 + (int)(Math.cos(angle) * radius); int cy = (int)dinoY + 10 + (int)(Math.sin(angle) * radius);
                        AffineTransform oldTx = g2.getTransform();
                        g2.translate(cx, cy); g2.rotate(angle + Math.PI / 2); drawVividShield(g2, -10, -15);
                        g2.setTransform(oldTx);
                    }
                }
                if (isEndlessMode || score < 1000) drawOrcWithPig(g2, getDinoX() + (int)currentOrcOffset, (int) orcY);

                // ✨ 无论如何，100分时盗贼开始绘制 (他会在左边 -800 外面开始被渲染跑进来)
                if (score >= 100 && !thiefCrashed) drawThief(g2, getDinoX() + (int)currentThiefOffset, (int) thiefY, thiefY < getGroundY() - 33);
            }

            if (!isVictory) {
                for (int i = 0; i < 3; i++) drawUIHeart(g2, 30 + i * 35, 30, i < lives);
                g2.setFont(FontUtils.getPixelFont(Font.BOLD, 14));
                long timeSinceLastShield = System.currentTimeMillis() - lastShieldUseTime;
                if (timeSinceLastShield < SHIELD_COOLDOWN) {
                    g2.setColor(new Color(180, 180, 180)); g2.drawString("SHIELD CD: " + (int)((SHIELD_COOLDOWN - timeSinceLastShield) / 1000) + "s", 20, 65);
                } else {
                    g2.setColor(new Color(255, 255, 255)); g2.drawString("SHIELD READY [DOWN]", 20, 65);
                }
                if (isEndlessMode) { g2.setColor(new Color(255, 100, 100)); g2.drawString("ENDLESS MODE", getWidth() - 180, 25); }

                g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 18)); g2.drawString(String.format("SCORE: %05d", (int) score), getWidth() - 180, 45);
            }

            if (isOver && !isVictory) {
                int cx = getWidth() / 2; int cy = getHeight() / 2;
                g2.setColor(new Color(0, 0, 0, 180)); g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 100, 100)); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 36)); FontMetrics fm = g2.getFontMetrics();
                String t1 = "MISSION FAILED"; g2.drawString(t1, cx - fm.stringWidth(t1) / 2, cy - 60);
                g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 20)); fm = g2.getFontMetrics();

                String t2 = String.format("SCORE: %d", (int)score); g2.drawString(t2, cx - fm.stringWidth(t2) / 2, cy - 20);

                drawPixelButton(g2, "上传战绩", btnUpload, new Color(50, 150, 200));
                drawPixelButton(g2, "返回菜单", btnMenu, new Color(150, 100, 100));
            }
        }

        if (isPrologue) drawPrologue(g2);
        if (isPaused) drawPauseMenu(g2);

        Toolkit.getDefaultToolkit().sync();
    }

    private void drawMainMenu(Graphics2D g2) {
        int cx = getWidth() / 2; int cy = getHeight() / 2;
        g2.setColor(new Color(15, 15, 25, 200)); g2.fillRect(0, 0, getWidth(), getHeight());

        int titleY = cy - 150 + (int)(Math.sin(System.currentTimeMillis() / 300.0) * 8);
        g2.setColor(new Color(255, 210, 40)); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 44));
        FontMetrics fm = g2.getFontMetrics(); String title = "拯救公猪"; g2.drawString(title, cx - fm.stringWidth(title) / 2, titleY);
        g2.setFont(FontUtils.getPixelFont(Font.BOLD, 16)); g2.setColor(new Color(200, 200, 200)); fm = g2.getFontMetrics();
        String subTitle = "Save the Pig"; g2.drawString(subTitle, cx - fm.stringWidth(subTitle) / 2, titleY + 30);

        int baselineY = getGroundY();
        drawDino(g2, 80, baselineY - 39, false);
        drawOrcWithPig(g2, getWidth() - 180, baselineY - 44);

        drawPixelButton(g2, "故事模式", btnStory, new Color(200, 120, 50));
        drawPixelButton(g2, "排名模式", btnEndless, new Color(180, 50, 50));
        drawPixelButton(g2, "全球排行", btnRank, new Color(50, 150, 100));
        drawPixelButton(g2, "游戏设置", btnSettings, new Color(100, 100, 150));
    }

    private void drawSettingsMenu(Graphics2D g2) {
        int cx = getWidth() / 2; int cy = getHeight() / 2;
        g2.setColor(new Color(15, 15, 25, 220)); g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 32));
        FontMetrics fm = g2.getFontMetrics(); String title = "【 游 戏 设 置 】"; g2.drawString(title, cx - fm.stringWidth(title) / 2, cy - 140);

        g2.setFont(FontUtils.getPixelFont(Font.BOLD, 16));
        g2.drawString("骑士难度(仅排名模式)：", cx - 180, cy - 80);

        drawPixelButton(g2, "简单", btnDiffEasy, difficulty == 0 ? new Color(50, 200, 100) : new Color(60, 80, 70));
        drawPixelButton(g2, "普通", btnDiffNormal, difficulty == 1 ? new Color(200, 150, 50) : new Color(80, 70, 50));
        drawPixelButton(g2, "硬核", btnDiffHard, difficulty == 2 ? new Color(200, 50, 50) : new Color(80, 50, 50));

        String musicTxt = isMusicEnabled ? "背景音乐：[开启]" : "背景音乐：[关闭]";
        drawPixelButton(g2, musicTxt, btnMusicToggle, isMusicEnabled ? new Color(50, 150, 200) : new Color(150, 50, 50));

        drawPixelButton(g2, "返回主菜单", btnSettingsBack, new Color(100, 100, 100));
    }

    private void drawPauseMenu(Graphics2D g2) {
        int cx = getWidth() / 2; int cy = getHeight() / 2;
        g2.setColor(new Color(0, 0, 0, 210)); g2.fillRect(0, 0, getWidth(), getHeight());
        int titleY = cy - 100 + (int)(Math.sin(System.currentTimeMillis() / 300.0) * 8);
        g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 32)); FontMetrics fm = g2.getFontMetrics();
        String title = "【 游 戏 暂 停 】"; g2.drawString(title, cx - fm.stringWidth(title) / 2, titleY);
        drawPixelButton(g2, "继续游戏 [SPACE]", btnResume, new Color(50, 150, 100));
    }

    private void drawLeaderboard(Graphics2D g2) {
        int cx = getWidth() / 2; int cy = getHeight() / 2;

        g2.setColor(new Color(15, 15, 25, 240)); g2.fillRect(0, 0, getWidth(), getHeight());

        int boardW = 600; int boardH = 340;
        int boardX = cx - boardW / 2; int boardY = cy - boardH / 2 - 20;
        int p = 4;

        g2.setColor(new Color(40, 45, 55)); g2.fillRect(boardX, boardY, boardW, boardH);
        g2.setColor(new Color(80, 85, 95)); g2.fillRect(boardX-p, boardY+p, p, boardH-p*2); g2.fillRect(boardX+boardW, boardY+p, p, boardH-p*2);
        g2.fillRect(boardX+p, boardY-p, boardW-p*2, p); g2.fillRect(boardX+p, boardY+boardH, boardW-p*2, p);
        g2.setColor(new Color(100, 110, 120)); g2.drawRect(boardX + p, boardY + p, boardW - p*2, boardH - p*2);

        g2.setColor(new Color(255, 210, 40)); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 26)); FontMetrics fm = g2.getFontMetrics();
        String title = "★ 全球骑士战力榜 TOP 10 ★"; g2.drawString(title, cx - fm.stringWidth(title) / 2, boardY + 40);

        if (isLoadingLeaderboard) {
            g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 18));
            fm = g2.getFontMetrics(); String t = "正在从云端飞鸽传书，请稍候..."; g2.drawString(t, cx - fm.stringWidth(t)/2, cy);
        } else if (topScores == null) {
            g2.setColor(new Color(255, 100, 100)); g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 18));
            fm = g2.getFontMetrics(); String t = "网络连接异常，未能获取排行数据！"; g2.drawString(t, cx - fm.stringWidth(t)/2, cy);
        } else if (topScores.isEmpty()) {
            g2.setColor(Color.WHITE); g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 18));
            fm = g2.getFontMetrics(); String t = "榜单空空如也，快去留下你的传说吧！"; g2.drawString(t, cx - fm.stringWidth(t)/2, cy);
        } else {
            g2.setFont(FontUtils.getPixelFont(Font.BOLD, 14)); g2.setColor(new Color(150, 150, 150));
            int rankX = cx - 220; int nameX = cx - 100; int scoreX = cx + 80; int timeX = cx + 180;
            g2.drawString("排名", rankX, boardY + 80); g2.drawString("骑士名号", nameX, boardY + 80);

            g2.drawString("SCORE", scoreX, boardY + 80); g2.drawString("登榜时间", timeX, boardY + 80);

            g2.setColor(new Color(80, 85, 95));
            for (int i=boardX+20; i<boardX+boardW-20; i+=8) g2.fillRect(i, boardY + 90, 4, 2);

            g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 14));
            for (int i = 0; i < topScores.size(); i++) {
                RankApiClient.ScoreRecord r = topScores.get(i);
                int y = boardY + 115 + i * 21;
                if (i == 0) g2.setColor(new Color(255, 215, 0)); else if (i == 1) g2.setColor(new Color(192, 192, 192)); else if (i == 2) g2.setColor(new Color(205, 127, 50)); else g2.setColor(Color.WHITE);
                g2.drawString(String.format("NO.%d", i + 1), rankX, y);
                String name = r.playerName; if (name.length() > 10) name = name.substring(0, 10) + "...";
                g2.drawString(name, nameX, y); g2.drawString(String.valueOf(r.score), scoreX, y); g2.drawString(r.createdAt, timeX, y);
            }
        }
        drawPixelButton(g2, "返回主菜单", btnBack, new Color(100, 100, 100));
    }

    private void drawCenteredTextWithIcon(Graphics2D g2, String text, String iconType, int cy, Color color) {
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int iconW = 35;
        int gap = 8;
        int totalW = textW + gap + iconW;

        int startX = (getWidth() - totalW) / 2;

        g2.setColor(color);
        g2.drawString(text, startX, cy);

        drawPixelIcon(g2, iconType, startX + textW + gap, cy - 14);
    }

    private void drawPrologue(Graphics2D g2) {
        if (prologueStartTime == 0) prologueStartTime = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - prologueStartTime;

        int cx = getWidth() / 2; int cy = getHeight() / 2;
        g2.setColor(new Color(10, 12, 18, 230)); g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setFont(FontUtils.getPixelFont(Font.BOLD | Font.ITALIC, 18));

        int startY = cy - 90;
        Color textColor = new Color(200, 200, 210);

        if (elapsed > 500) drawCenteredTextWithIcon(g2, "【一个月黑风高的夜晚】", "MOON", startY, textColor);
        if (elapsed > 2500) drawCenteredTextWithIcon(g2, "小偷施展了魔法，把公主变成了一头猪，扛着就跑！", "MASKED_HEAD", startY + 50, textColor);
        if (elapsed > 4500) drawCenteredTextWithIcon(g2, "盗贼闻讯赶来，伺机阻挠...", "THIEF", startY + 100, textColor);
        if (elapsed > 6500) drawCenteredTextWithIcon(g2, "骑士来不及拿宝剑，只带上盾牌，轻装上阵开始追击！", "CROSS_SHIELD", startY + 150, textColor);

        if (elapsed > 8500) {
            if (elapsed % 1000 > 500) {
                g2.setColor(new Color(255, 210, 40));
                g2.setFont(FontUtils.getPixelFont(Font.PLAIN, 18));
                FontMetrics fm = g2.getFontMetrics();
                String t = "- 按 SPACE 键开启追击 -";
                g2.drawString(t, cx - fm.stringWidth(t)/2, getHeight() - 40);
            }
        }
    }

    private void drawPixelIcon(Graphics2D g2, String type, int x, int y) {
        if (type.equals("MOON")) {
            g2.setColor(new Color(255, 240, 150, 30)); g2.fillOval(x - 12, y - 12, 44, 44);
            g2.setColor(new Color(255, 240, 150, 60)); g2.fillOval(x - 5, y - 5, 30, 30);
            g2.setColor(new Color(255, 250, 210)); g2.fillOval(x, y, 20, 20);
            g2.setColor(new Color(220, 210, 170));
            g2.fillOval(x + 4, y + 4, 6, 6);
            g2.fillOval(x + 12, y + 8, 4, 4);
            g2.fillOval(x + 5, y + 13, 5, 3);
        }
        else if (type.equals("MASKED_HEAD")) {
            Color suit = new Color(25, 25, 30);
            Color skin = new Color(240, 190, 150);
            g2.setColor(suit);
            g2.fillRect(x, y - 8, 20, 20);
            g2.setColor(skin);
            g2.fillRect(x + 4, y, 12, 4);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 6, y + 1, 2, 2);
            g2.fillRect(x + 12, y + 1, 2, 2);
        }
        else if (type.equals("THIEF")) {
            Color suit = new Color(80, 40, 120);
            Color skin = new Color(240, 190, 150);
            Color belt = new Color(200, 30, 30);
            g2.setColor(suit);
            g2.fillRect(x, y - 8, 20, 20);
            g2.setColor(skin);
            g2.fillRect(x + 4, y, 12, 8);
            g2.setColor(Color.WHITE);
            g2.fillRect(x + 4, y, 4, 4); g2.fillRect(x + 12, y, 4, 4);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 6, y + 2, 2, 2); g2.fillRect(x + 14, y + 2, 2, 2);
            g2.setColor(belt);
            g2.fillRect(x + 20, y - 4, 10, 4);
        }
        else if (type.equals("CROSS_SHIELD")) { drawVividShield(g2, x, y - 10); }
    }

    private void drawUIHeart(Graphics2D g2, int x, int y, boolean active) {
        int p = 2; if (active) g2.setColor(new Color(255, 50, 80)); else g2.setColor(new Color(100, 100, 100, 150));
        g2.fillRect(x - 4*p, y - 2*p, 3*p, 2*p); g2.fillRect(x + p, y - 2*p, 3*p, 2*p); g2.fillRect(x - 5*p, y, 10*p, 2*p);
        g2.fillRect(x - 4*p, y + 2*p, 8*p, 2*p); g2.fillRect(x - 2*p, y + 4*p, 4*p, 2*p); g2.fillRect(x - p, y + 6*p, 2*p, 2*p);
    }

    private void drawAlphaHeart(Graphics2D g2, int x, int y, int alpha) {
        int p = 2; g2.setColor(new Color(255, 50, 80, alpha));
        g2.fillRect(x - 4*p, y - 2*p, 3*p, 2*p); g2.fillRect(x + p, y - 2*p, 3*p, 2*p); g2.fillRect(x - 5*p, y, 10*p, 2*p);
        g2.fillRect(x - 4*p, y + 2*p, 8*p, 2*p); g2.fillRect(x - 2*p, y + 4*p, 4*p, 2*p); g2.fillRect(x - p, y + 6*p, 2*p, 2*p);
    }

    private void drawVictoryCutsceneForeground(Graphics2D g2, long elapsed) {
        Obstacle crashTree = obstacles.isEmpty() ? null : obstacles.get(obstacles.size()-1);
        int crashX = crashTree != null ? (int)crashTree.x : getDinoX() + 320;

        int pigLandX = crashX - 130;
        int baseGroundY = getGroundY();
        int pigLandY = baseGroundY - 5;
        int pigX = pigLandX;
        int pigY = pigLandY;
        int knightX = getDinoX();
        int baseKnightY = baseGroundY - 39;

        if (elapsed > 2000 && elapsed < 6500) { double progress = (elapsed - 2000) / 4500.0; knightX = getDinoX() + (int)(progress * (pigLandX - 16 - getDinoX())); }
        else if (elapsed >= 6500) { knightX = pigLandX - 16; }

        drawDino(g2, knightX, baseKnightY, (elapsed >= 6000));

        drawDeadOrcBody(g2, crashX - 20, baseGroundY);

        if (elapsed < 1000) {
            double t = elapsed / 1000.0;
            pigX = crashX - (int)(t * 130);
            pigY = (baseGroundY - 50) - (int)(Math.sin(t * Math.PI) * 40);
        }

        if (elapsed < 4500) drawStandalonePig(g2, pigX, pigY - 20); else drawMonkeyLeftFacing(g2, pigLandX, pigLandY + 5);

        if (elapsed >= 6500) {
            int p = 3; Color armor = new Color(210, 210, 220); Color fur = new Color(139, 69, 19);
            g2.setColor(fur);
            g2.fillRect(pigLandX - 3*p, (pigLandY + 5) - 7*p, 5*p, 2*p);
            g2.fillRect(pigLandX - 3*p, (pigLandY + 5) - 7*p, 2*p, 3*p);
            g2.setColor(armor);
            g2.fillRect(knightX + 5*p, baseKnightY - 1*p, 6*p, 2*p);
            g2.fillRect(knightX + 11*p, baseKnightY - 1*p, 2*p, 3*p);
        }

        if (elapsed > 3500 && elapsed < 5000) {
            Random magicRand = new Random(elapsed / 80);
            for (int i = 0; i < 25; i++) {
                g2.setColor(magicRand.nextBoolean() ? Color.WHITE : new Color(255, 230, 100));
                g2.fillRect(pigLandX + magicRand.nextInt(60) - 10, pigLandY - magicRand.nextInt(90), 4, 4);
            }
        }

        if (elapsed >= 4500 && elapsed <= 5500) {
            int flashAlpha = (int)(elapsed < 5000 ? ((elapsed - 4500) * 255 / 500) : ((5500 - elapsed) * 255 / 500));
            if (flashAlpha > 0) { g2.setColor(new Color(255, 255, 255, Math.max(0, Math.min(255, flashAlpha)))); g2.fillRect(0, 0, getWidth(), getHeight()); }
        }

        if (elapsed > 6500) {
            long embraceTime = elapsed - 6500; int maxHearts = (int) (embraceTime / 300);
            for (int i = 0; i <= maxHearts; i++) {
                long lifeTime = embraceTime - (i * 300L);
                if (lifeTime < 2500) {
                    drawAlphaHeart(g2, knightX + 18 + (int)(Math.sin(lifeTime / 150.0) * 15), pigLandY - 30 - (int)(lifeTime / 15), Math.max(0, 255 - (int)(lifeTime * 255 / 2500)));
                }
            }
        }

        if (elapsed > 8000) {
            int textAlpha = (int) Math.min(255, (elapsed - 8000) / 10); int cx = getWidth() / 2; int cy = getHeight() / 2;
            g2.setColor(new Color(180, 20, 60, textAlpha)); g2.setFont(FontUtils.getPixelFont(Font.BOLD, 22)); FontMetrics fm = g2.getFontMetrics();
            g2.drawString("魔法解除！被抓走的公猪原来是一只猿猴！", cx - fm.stringWidth("魔法解除！被抓走的公猪原来是一只猿猴！") / 2, cy - 70);
            g2.drawString("骑士和猿猴从此幸福地相拥在一起~", cx - fm.stringWidth("骑士和猿猴从此幸福地相拥在一起~") / 2, cy - 40);

            if (elapsed > 10000) {
                drawPixelButton(g2, "开启排名模式", btnVictoryEndless, new Color(180, 50, 50));
                drawPixelButton(g2, "返回主菜单", btnVictoryMenu, new Color(100, 100, 100));
            }
        }
    }

    private void drawStandalonePig(Graphics2D g2, int px, int py) {
        int p = 5; py = py - 7 * p + 5;
        Color pigPink = new Color(255, 170, 190); Color pigDark = new Color(220, 130, 150); Color crownGold = new Color(255, 200, 0);
        g2.setColor(pigDark); g2.fillRect(px, py + 2 * p, 16 * p, 10 * p);
        g2.setColor(pigPink); g2.fillRect(px + p, py + p, 14 * p, 10 * p);
        g2.setColor(new Color(255, 110, 140)); g2.fillRect(px - 2 * p, py + 5 * p, 4 * p, 4 * p);
        g2.setColor(new Color(60, 20, 30)); g2.fillRect(px - p, py + 6 * p, p, 2 * p);
        g2.setColor(Color.WHITE); g2.fillRect(px + 3 * p, py + 3 * p, 2 * p, 2 * p); g2.fillRect(px + 8 * p, py + 3 * p, 2 * p, 2 * p);
        g2.setColor(Color.BLACK); g2.fillRect(px + 4 * p, py + 3 * p, p, p);
        g2.setColor(crownGold); g2.fillRect(px + 6 * p, py - 2 * p, 5 * p, p); g2.fillRect(px + 6 * p, py - 3 * p, p, p); g2.fillRect(px + 8 * p, py - 4 * p, p, 2 * p); g2.fillRect(px + 10 * p, py - 3 * p, p, p);
    }

    private void drawMonkeyLeftFacing(Graphics2D g2, int x, int y) {
        int p = 3; Color fur = new Color(139, 69, 19); Color skin = new Color(222, 184, 135);
        g2.setColor(fur); g2.fillRect(x + 2*p, y - 3*p, 2*p, 3*p); g2.fillRect(x + 6*p, y - 3*p, 2*p, 3*p); g2.fillRect(x + 2*p, y - 10*p, 6*p, 7*p);
        g2.setColor(skin); g2.fillRect(x + 3*p, y - 8*p, 3*p, 4*p);
        g2.setColor(fur); g2.fillRect(x + 1*p, y - 16*p, 8*p, 6*p);
        g2.setColor(skin); g2.fillRect(x + 1*p, y - 15*p, 6*p, 4*p);
        g2.setColor(Color.BLACK); g2.fillRect(x + 2*p, y - 14*p, p, p); g2.fillRect(x + 4*p, y - 14*p, p, p);
        g2.setColor(fur); g2.fillRect(x + 8*p, y - 9*p, 2*p, 5*p); g2.fillRect(x + 9*p, y - 8*p, 3*p, 2*p); g2.fillRect(x + 12*p, y - 9*p, 2*p, 4*p); g2.fillRect(x + 13*p, y - 12*p, 2*p, 4*p); g2.fillRect(x + 10*p, y - 14*p, 3*p, 2*p); g2.fillRect(x + 9*p, y - 13*p, 2*p, 2*p);
    }

    private void drawDeadOrcBody(Graphics2D g2, int dx, int dy) {
        int p = 3;
        Color clothesColor = new Color(30, 30, 35);
        Color skinColor = new Color(240, 190, 150);

        g2.setColor(clothesColor);
        g2.fillRect(dx - 5*p, dy - 7*p, 7*p, 7*p);
        g2.fillRect(dx + 2*p, dy - 2*p, 6*p, 2*p);

        g2.setColor(skinColor);
        g2.fillRect(dx - 2*p, dy - 11*p, 5*p, 5*p);

        g2.setColor(clothesColor);
        g2.fillRect(dx - 2*p, dy - 12*p, 5*p, 2*p);

        g2.setColor(Color.BLACK);
        g2.drawLine(dx - 1*p, dy - 10*p, dx, dy - 9*p);
        g2.drawLine(dx, dy - 10*p, dx - 1*p, dy - 9*p);
        g2.drawLine(dx + 1*p, dy - 10*p, dx + 2*p, dy - 9*p);
        g2.drawLine(dx + 2*p, dy - 10*p, dx + 1*p, dy - 9*p);

        long t = System.currentTimeMillis() / 200;
        g2.setColor(new Color(255, 210, 40));
        if (t % 2 == 0) {
            g2.fillRect(dx - 4*p, dy - 15*p, p, p);
            g2.fillRect(dx + 3*p, dy - 13*p, p, p);
        } else {
            g2.fillRect(dx - 1*p, dy - 16*p, p, p);
            g2.fillRect(dx - 5*p, dy - 12*p, p, p);
        }
    }

    private void drawDeadThiefBody(Graphics2D g2, int dx, int dy) {
        int p = 3;
        Color suit = new Color(80, 40, 120);
        Color skinColor = new Color(240, 190, 150);
        Color belt = new Color(200, 30, 30);

        g2.setColor(suit);
        g2.fillRect(dx - 6*p, dy - 1*p, 10*p, 2*p);
        g2.fillRect(dx - 8*p, dy - 1*p, 2*p, 1*p);

        g2.setColor(belt);
        g2.fillRect(dx - 1*p, dy - 2*p, 2*p, 3*p);

        g2.setColor(skinColor);
        g2.fillRect(dx + 4*p, dy - 1*p, 3*p, 2*p);

        g2.setColor(Color.BLACK);
        int eyeX = dx + 5*p;
        int eyeY = dy;
        g2.drawLine(eyeX, eyeY, eyeX+1, eyeY+1); g2.drawLine(eyeX+1, eyeY, eyeX, eyeY+1);
        g2.drawLine(eyeX+3, eyeY, eyeX+4, eyeY+1); g2.drawLine(eyeX+4, eyeY, eyeX+3, eyeY+1);

        g2.setColor(suit);
        g2.fillRect(dx + 8*p, dy, 3*p, 2*p);

        g2.setColor(new Color(255, 210, 40));
        if (System.currentTimeMillis() / 200 % 2 == 0) {
            g2.fillRect(dx + 4*p, dy - 4*p, p, p);
            g2.fillRect(dx + 7*p, dy - 3*p, p, p);
        } else {
            g2.fillRect(dx + 3*p, dy - 3*p, p, p);
            g2.fillRect(dx + 6*p, dy - 5*p, p, p);
        }
    }

    private void drawDino(Graphics2D g2, int x, int y, boolean hideShield) {
        int p = 3; Color armor = new Color(210, 210, 220); Color plume = new Color(220, 40, 40);
        boolean isBlocking = (!isVictory && (isBlockingRight || isBlockingLeft)); int bodyYOffset = isBlocking ? 3 : 0;

        g2.setColor(plume); g2.fillRect(x + 2 * p, y - 6 * p + bodyYOffset, 5 * p, 2 * p);
        g2.setColor(armor); g2.fillRect(x + 4 * p, y - 4 * p + bodyYOffset, 6 * p, 6 * p); g2.fillRect(x + 3 * p, y + 2 * p + bodyYOffset, 7 * p, 8 * p);

        if (!hideShield) {
            if (!isVictory) {
                if (isBlockingRight) drawVividShield(g2, x + 9 * p, y - 1 * p); else if (isBlockingLeft) drawVividShield(g2, x - 5 * p, y - 1 * p); else drawBackShield(g2, x + 7 * p, y + 3 * p);
            } else { drawBackShield(g2, x + 7 * p, y + 3 * p); }
        }

        g2.setColor(new Color(80, 85, 100));

        if (isMainMenu || isSettingsMenu || (isVictory && (System.currentTimeMillis() - victoryStartTime >= 6500))) {
            g2.fillRect(x + 4 * p, y + 10 * p, 2 * p, 3 * p);
            g2.fillRect(x + 7 * p, y + 10 * p, 2 * p, 3 * p);
        } else if ((System.currentTimeMillis() / 120) % 2 == 0) {
            g2.fillRect(x + 4 * p, y + 10 * p, 2 * p, 3 * p); g2.fillRect(x + 7 * p, y + 10 * p, 2 * p, 1 * p);
        } else {
            g2.fillRect(x + 4 * p, y + 10 * p, 2 * p, 1 * p); g2.fillRect(x + 7 * p, y + 10 * p, 2 * p, 3 * p);
        }
    }

    private void drawVividShield(Graphics2D g2, int sx, int sy) {
        int p = 3; if (System.currentTimeMillis() % 400 > 200) sy += 1; int width = 6 * p; int height = 12 * p;
        g2.setColor(new Color(170, 175, 185)); g2.fillPolygon(new int[]{sx, sx + width, sx + width, sx + width/2, sx}, new int[]{sy, sy, sy + height/2 + p, sy + height, sy + height/2 + p}, 5);
        int i = 2; g2.setColor(new Color(30, 60, 140)); g2.fillPolygon(new int[]{sx + i, sx + width - i, sx + width - i, sx + width/2, sx + i}, new int[]{sy + i, sy + i, sy + height/2 + p, sy + height - 2*i, sy + height/2 + p}, 5);
        g2.setColor(new Color(255, 210, 40)); g2.fillRect(sx + width/2 - 1, sy + 3 * p, 2, height - 6 * p); g2.fillRect(sx + p + 1, sy + 5 * p, width - 2 * p - 2, 2);
        g2.setColor(new Color(255, 255, 255, Math.max(0, 40 + (int)(Math.sin(System.currentTimeMillis() / 150.0) * 20))));
        g2.fillPolygon(new int[]{sx, sx + width/2, sx + width/2, sx}, new int[]{sy, sy, sy + height/2, sy + height/2 + p}, 4);
    }

    private void drawBackShield(Graphics2D g2, int sx, int sy) { int p = 3; g2.setColor(new Color(170, 175, 185)); g2.fillRect(sx, sy, 3 * p, 7 * p); g2.setColor(new Color(30, 60, 140)); g2.fillRect(sx + 1, sy + 1, 3 * p - 2, 7 * p - 2); }

    private void drawOrcWithPig(Graphics2D g2, int x, int y) {
        Graphics2D g = (Graphics2D) g2.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int p = 5; int walkFrame = (int) (System.currentTimeMillis() / 200 % 2); int bounce = (walkFrame == 0) ? p : 0; int offsetY = -3 * p - 6;
        g.setColor(new Color(30, 30, 35));
        if (walkFrame == 0) { g.fillRect(x + 3*p, y + 10*p + offsetY, 2*p, 3*p); g.fillRect(x + 7*p, y + 11*p + offsetY, 3*p, 2*p); } else { g.fillRect(x + 3*p, y + 11*p + offsetY, 3*p, 2*p); g.fillRect(x + 8*p, y + 10*p + offsetY, 2*p, 3*p); }
        int px = x - 5 * p; int py = y - 7 * p + bounce + offsetY;
        g.setColor(new Color(220, 130, 150)); g.fillRect(px, py + 2 * p, 16 * p, 10 * p); g.setColor(new Color(255, 170, 190)); g.fillRect(px + p, py + p, 14 * p, 10 * p);
        g.setColor(new Color(255, 110, 140)); g.fillRect(px - 2 * p, py + 5 * p, 4 * p, 4 * p); g.setColor(new Color(60, 20, 30)); g.fillRect(px - p, py + 6 * p, p, 2 * p);
        g.setColor(Color.WHITE); g.fillRect(px + 3 * p, py + 3 * p, 2 * p, 2 * p); g.fillRect(px + 8 * p, py + 3 * p, 2 * p, 2 * p); g.setColor(Color.BLACK); g.fillRect(px + 4 * p, py + 3 * p, p, p);
        g.setColor(new Color(255, 200, 0)); g.fillRect(px + 6 * p, py - 2 * p, 5 * p, p); g.fillRect(px + 6 * p, py - 3 * p, p, p); g.fillRect(px + 8 * p, py - 4 * p, p, 2 * p); g.fillRect(px + 10 * p, py - 3 * p, p, p);
        g.setColor(new Color(30, 30, 35)); g.fillRect(x + 2 * p, y + 5 * p + offsetY, 10 * p, 5 * p); int hx = x + 10 * p; int hy = y + 5 * p + offsetY; g.fillRect(hx, hy, 5 * p, 4 * p);
        g.setColor(new Color(240, 190, 150)); g.fillRect(hx + 2 * p, hy + p, 3 * p, 2 * p); g.setColor(Color.BLACK); int eyeShift = (int) (System.currentTimeMillis() / 400 % 2); g.fillRect(hx + 2 * p + eyeShift, hy + p, 1, 1); g.fillRect(hx + 4 * p, hy + p, 1, 1);
        g.setColor(new Color(101, 67, 33)); g.fillRect(px + 5 * p, py, p, 12 * p); g.fillRect(px + 11 * p, py, p, 11 * p); g.setColor(new Color(240, 190, 150)); g.fillRect(x + p, y + 7 * p + bounce + offsetY, 2 * p, 2 * p); g.dispose();
    }

    private void drawThief(Graphics2D g2, int x, int y, boolean inAir) {
        int p = 3; AffineTransform oldTx = g2.getTransform(); int cx = x + 4 * p; int cy = y + 5 * p; g2.translate(cx, cy);
        if (inAir) g2.rotate((System.currentTimeMillis() % 600) / 600.0 * 2 * Math.PI); else if (System.currentTimeMillis() / 150 % 2 == 0) g2.translate(0, 2);

        int rx = -4 * p; int ry = -5 * p;
        Color suit = new Color(80, 40, 120);
        Color skin = new Color(240, 190, 150);
        Color belt = new Color(200, 30, 30);

        g2.setColor(suit); g2.fillRect(rx + p, ry + 2 * p, 6 * p, 7 * p);
        g2.fillRect(rx + p, ry - 3 * p, 6 * p, 5 * p);

        g2.setColor(skin); g2.fillRect(rx + 2 * p, ry - p, 4 * p, 2 * p);
        g2.setColor(Color.WHITE); g2.fillRect(rx + 2 * p, ry - p, p, p); g2.fillRect(rx + 4 * p, ry - p, p, p);
        g2.setColor(Color.BLACK); g2.fillRect(rx + 3 * p, ry - p, 1, 1); g2.fillRect(rx + 5 * p, ry - p, 1, 1);

        g2.setColor(belt); g2.fillRect(rx + 7 * p, ry - 2 * p, 3 * p, p);
        if (System.currentTimeMillis() / 150 % 2 == 0) g2.fillRect(rx + 9 * p, ry - p, 2 * p, p); else g2.fillRect(rx + 9 * p, ry - 3 * p, 2 * p, p);
        g2.fillRect(rx + p, ry + 5 * p, 6 * p, p);

        g2.setColor(suit);
        if (inAir) { g2.fillRect(rx + p, ry + 9 * p, 2 * p, 2 * p); g2.fillRect(rx + 5 * p, ry + 7 * p, 2 * p, 2 * p); }
        else { if (System.currentTimeMillis() / 150 % 2 == 0) { g2.fillRect(rx + 2 * p, ry + 9 * p, 2 * p, 2 * p); g2.fillRect(rx + 4 * p, ry + 8 * p, 2 * p, 2 * p); } else { g2.fillRect(rx + 2 * p, ry + 8 * p, 2 * p, 2 * p); g2.fillRect(rx + 5 * p, ry + 9 * p, 2 * p, 2 * p); } }

        if (thiefThrowTimer > 0) { g2.setColor(suit); g2.fillRect(rx + 6 * p, ry + 3 * p, 4 * p, 2 * p); g2.setColor(skin); g2.fillRect(rx + 10 * p, ry + 3 * p, p, 2 * p); }
        else { g2.setColor(suit); if (inAir) { g2.fillRect(rx + 2 * p, ry + 4 * p, 2 * p, 3 * p); } else { if (System.currentTimeMillis() / 150 % 2 == 0) g2.fillRect(rx + 3 * p, ry + 4 * p, 2 * p, 3 * p); else g2.fillRect(rx + 5 * p, ry + 4 * p, 2 * p, 3 * p); } }

        g2.setTransform(oldTx);
    }

    class Bomb {
        double x, y, vx, vy; boolean onGround = false; Bomb(int x, int y) { this.x = x; this.y = y; this.vx = -4.5 - rand.nextDouble() * 2; this.vy = -7; }
        boolean update(double speed) { if (!onGround) { x += vx; y += vy; vy += 0.5; if (y >= getGroundY() - 15) { y = getGroundY() - 15; onGround = true; } } else x -= speed; return x < -50; }
        void render(Graphics2D g2) { g2.setColor(Color.BLACK); g2.fillOval((int) x, (int) y, 15, 15); g2.setColor(new Color(150, 150, 150)); g2.fillOval((int) x + 3, (int) y + 3, 5, 5); g2.setColor(new Color(180, 50, 50)); g2.fillRect((int) x + 6, (int) y - 4, 3, 5); g2.setColor(new Color(255, 200 + rand.nextInt(55), 0)); for (int i = 0; i < 4; i++) g2.fillRect((int) x + 7 + rand.nextInt(10) - 5, (int) y - 6 + rand.nextInt(8) - 4, 2, 2); g2.setColor(Color.WHITE); g2.fillRect((int) x + 7 + rand.nextInt(4) - 2, (int) y - 5 + rand.nextInt(4) - 2, 2, 2); }
        Rectangle getBounds() { return new Rectangle((int) x + 3, (int) y + 3, 9, 9); }
    }
    class Dagger {
        double x, y; Dagger(int x, int y) { this.x = x; this.y = y; } boolean update(double speed) { x += (speed + 4.5); return x > 1000; }
        void render(Graphics2D g2) { AffineTransform oldTx = g2.getTransform(); g2.translate(x + 10, y + 5); g2.rotate(System.currentTimeMillis() / 80.0); g2.setColor(new Color(200, 200, 205)); g2.fillPolygon(new int[]{0, 12, 0}, new int[]{-3, 0, 3}, 3); g2.setColor(new Color(101, 67, 33)); g2.fillRect(-8, -2, 8, 4); g2.setColor(new Color(80, 80, 80)); g2.fillRect(-2, -4, 3, 8); g2.setTransform(oldTx); }
        Rectangle getBounds() { return new Rectangle((int) x, (int) y, 16, 10); }
    }
    class Obstacle {
        double x; boolean isKnockedDown = false; boolean hasCrashedThief = false;
        Obstacle(int x) { this.x = x; }
        void render(Graphics2D g2) {
            int y = getGroundY() - 60;
            AffineTransform oldTx = g2.getTransform();
            if (isKnockedDown) { g2.translate(x + 10, y + 65); g2.rotate(Math.PI / 2); g2.translate(-(x + 10), -(y + 65)); }
            g2.setColor(new Color(90, 55, 30)); g2.fillRect((int) x + 10, y + 30, 14, 35); g2.setColor(new Color(60, 35, 15)); g2.drawLine((int) x + 13, y + 30, (int) x + 13, y + 65); g2.drawLine((int) x + 17, y + 35, (int) x + 17, y + 65); g2.drawLine((int) x + 21, y + 30, (int) x + 21, y + 65);
            g2.setColor(new Color(30, 110, 50)); g2.fillPolygon(new int[]{(int) x + 17, (int) x - 18, (int) x + 52}, new int[]{y + 25, y + 55, y + 55}, 3); g2.fillPolygon(new int[]{(int) x + 17, (int) x - 13, (int) x + 47}, new int[]{y + 10, y + 40, y + 40}, 3); g2.fillPolygon(new int[]{(int) x + 17, (int) x - 8, (int) x + 42}, new int[]{y - 5, y + 20, y + 20}, 3); g2.setColor(new Color(50, 140, 70)); g2.fillPolygon(new int[]{(int) x + 17, (int) x + 5, (int) x + 29}, new int[]{y - 5, y + 15, y + 15}, 3);
            g2.setTransform(oldTx);
            if (hasCrashedThief) drawDeadThiefBody(g2, (int)x, y + 60);
        }
        Rectangle getBounds() { return new Rectangle((int) x + 10, getGroundY() - 60 + 25, 6, 35); }
    }
    class DustParticle {
        double x, y, vx, vy; int life = 255; DustParticle(int x, int y) { this.x = x; this.y = y; this.vx = -3 - rand.nextDouble() * 2; this.vy = -rand.nextDouble(); }
        boolean update() { x += vx; y += vy; life -= 12; return life <= 0; } void draw(Graphics2D g2) { g2.setColor(new Color(200, 200, 200, life)); g2.fillOval((int) x, (int) y, 5, 5); }
    }

    class MusicThread extends Thread {
        private volatile boolean playing = true;
        private Object playerInstance;
        private Method closeMethod;
        private InputStream currentStream;
        private String trackPath;
        private boolean loop;

        public MusicThread(String trackPath, boolean loop) {
            this.trackPath = trackPath;
            this.loop = loop;
        }

        public void run() {
            try {
                Class<?> playerClass = Class.forName("javazoom.jl.player.Player");
                Constructor<?> constructor = playerClass.getConstructor(InputStream.class);
                closeMethod = playerClass.getMethod("close");
                Method playMethod = playerClass.getMethod("play");

                do {
                    currentStream = getClass().getResourceAsStream(trackPath);
                    if (currentStream == null) { System.out.println("警告: 找不到音乐文件 " + trackPath); break; }

                    playerInstance = constructor.newInstance(currentStream);
                    if (!playing) {
                        closeMethod.invoke(playerInstance);
                        break;
                    }
                    playMethod.invoke(playerInstance);
                    if (closeMethod != null) closeMethod.invoke(playerInstance);
                } while (playing && loop);
            } catch (Exception ex) {
                playing = false;
            } finally {
                try { if (currentStream != null) currentStream.close(); } catch (Exception ignored) {}
            }
        }

        public void stopMusic() {
            playing = false;
            try { if (closeMethod != null && playerInstance != null) closeMethod.invoke(playerInstance); } catch (Exception ignored) {}
            try { if (currentStream != null) currentStream.close(); } catch (Exception ignored) {}
            this.interrupt();
        }
    }
}