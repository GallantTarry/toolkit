package shaoxia.modules;

import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassPanel;
import shaoxia.Utils.GlassTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * 大声密谋助手面板 (动态 N 进制高维密文编解码)
 */
public class SecretChatPanel extends BackgroundPanel {

    private final MainLauncher parent;

    private JPanel leftTop;
    private ActionButton backBtn;

    private GlassTextField keyField;
    private JTextArea inputArea;
    private JTextArea outputArea;

    public SecretChatPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);

        initTopBar();
        add(createSecretChatCard(), BorderLayout.CENTER);
    }

    /**
     * 初始化顶部导航栏
     */
    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 20));
        topBar.setPreferredSize(new Dimension(0, 56));

        leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftTop.setOpaque(false);

        backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());
        leftTop.add(backBtn);

        ActionButton encryptBtn = new ActionButton("执行加密");
        encryptBtn.setPreferredSize(new Dimension(130, 36));
        encryptBtn.addActionListener(e -> processSecretChat(true));
        leftTop.add(encryptBtn);

        ActionButton decryptBtn = new ActionButton("执行解密");
        decryptBtn.setPreferredSize(new Dimension(130, 36));
        decryptBtn.addActionListener(e -> processSecretChat(false));
        leftTop.add(decryptBtn);
        topBar.add(leftTop, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);
    }

    private JPanel createSecretChatCard() {
        JPanel secretPanel = new JPanel(new BorderLayout(10, 10));
        secretPanel.setOpaque(false);
        secretPanel.setBorder(new EmptyBorder(5, 20, 20, 20));

        // 顶部：密钥设置区
        GlassPanel keyPanel = new GlassPanel(new BorderLayout(15, 0));
        keyPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel keyLabel = new JLabel("加密密钥: ");
        keyLabel.setForeground(Color.WHITE);
        keyLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 14));

        keyField = new GlassTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(FontUtils.getChineseFont(Font.PLAIN, 15));
                    g2.setColor(new Color(255, 255, 255, 60)); // 暗处理背景水印
                    FontMetrics fm = g2.getFontMetrics();
                    String placeholder = "密码#ab";
                    int x = (getWidth() - fm.stringWidth(placeholder)) / 2;
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(placeholder, x, y);
                    g2.dispose();
                }
            }
        };
        keyField.setFont(FontUtils.getChineseFont(Font.BOLD, 15));
        keyField.setForeground(Color.WHITE);
        keyField.setHorizontalAlignment(JTextField.CENTER);
        keyField.setPreferredSize(new Dimension(0, 36));

        // 安装自定义 2px 亮白闪烁光标，防止 BasicTextFieldUI 重置或居中边界裁剪导致无光标
        installVisibleCaret(keyField);

        keyPanel.add(keyLabel, BorderLayout.WEST);
        keyPanel.add(keyField, BorderLayout.CENTER);
        secretPanel.add(keyPanel, BorderLayout.NORTH);

        // 中间：输入与输出区
        JPanel textPanel = new JPanel(new GridLayout(1, 2, 25, 0));
        textPanel.setOpaque(false);

        MouseAdapter doubleClickCopyListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTextArea src = (JTextArea) e.getSource();
                    String text = src.getText().trim();
                    if (!text.isEmpty()) {
                        StringSelection selection = new StringSelection(text);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        JOptionPane.showMessageDialog(SecretChatPanel.this, "内容已成功复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        };

        // 左侧输入区
        GlassPanel leftWrapper = new GlassPanel(new BorderLayout(0, 10));
        leftWrapper.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel inputLabel = new JLabel("密文输入:", JLabel.LEFT);
        inputLabel.setForeground(Color.WHITE);
        inputLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 13));

        inputArea = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paintComponent(g);
            }
        };
        inputArea.setUI(new javax.swing.plaf.basic.BasicTextAreaUI());
        inputArea.setFont(FontUtils.getChineseFont(Font.PLAIN, 15));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setOpaque(false);
        inputArea.setForeground(Color.WHITE);
        inputArea.setBackground(new Color(0, 0, 0, 0));
        inputArea.setCaretColor(Color.WHITE);
        inputArea.addMouseListener(doubleClickCopyListener);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputScroll.setBorder(null);

        leftWrapper.add(inputLabel, BorderLayout.NORTH);
        leftWrapper.add(inputScroll, BorderLayout.CENTER);
        textPanel.add(leftWrapper);

        // 右侧输出区
        GlassPanel rightWrapper = new GlassPanel(new BorderLayout(0, 10));
        rightWrapper.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel outputLabel = new JLabel("密文结果:", JLabel.LEFT);
        outputLabel.setForeground(Color.WHITE);
        outputLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 13));

        outputArea = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paintComponent(g);
            }
        };
        outputArea.setUI(new javax.swing.plaf.basic.BasicTextAreaUI());
        outputArea.setFont(FontUtils.getChineseFont(Font.PLAIN, 15));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setEditable(false);
        outputArea.setOpaque(false);
        outputArea.setForeground(Color.WHITE);
        outputArea.setBackground(new Color(0, 0, 0, 0));
        outputArea.setCaretColor(Color.WHITE);
        outputArea.addMouseListener(doubleClickCopyListener);

        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setOpaque(false);
        outputScroll.getViewport().setOpaque(false);
        outputScroll.setBorder(null);

        rightWrapper.add(outputLabel, BorderLayout.NORTH);
        rightWrapper.add(outputScroll, BorderLayout.CENTER);
        textPanel.add(rightWrapper);

        secretPanel.add(textPanel, BorderLayout.CENTER);

        return secretPanel;
    }

    private void installVisibleCaret(JTextField field) {
        DefaultCaret caret = new DefaultCaret() {
            @Override
            protected synchronized void damage(Rectangle r) {
                if (r != null) {
                    x = r.x - 1;
                    y = r.y;
                    width = 4;
                    height = r.height;
                    repaint();
                }
            }

            @Override
            public void paint(Graphics g) {
                JTextComponent comp = getComponent();
                if (comp == null || !isVisible()) return;
                try {
                    Rectangle2D r2d = comp.modelToView2D(getDot());
                    if (r2d == null) return;
                    Rectangle r = r2d.getBounds();
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(comp.getCaretColor() != null ? comp.getCaretColor() : Color.WHITE);
                    g2.fillRect(r.x, r.y + 2, 2, Math.max(2, r.height - 4));
                    g2.dispose();
                } catch (Exception ignored) {
                }
            }
        };
        caret.setBlinkRate(500);
        field.setCaret(caret);
        field.setCaretColor(Color.WHITE);
    }

    private void processSecretChat(boolean isEncrypt) {
        String rawKey = keyField.getText().trim();
        if (rawKey.isEmpty()) {
            rawKey = "密码#ab";
        }

        // 支持 "真实密码#伪装字符" 格式（如 "123456#阿巴"），也可直接输入普通密钥
        String secretSeed = rawKey;
        String alphabetPart = rawKey.contains("#") ? rawKey.substring(rawKey.lastIndexOf("#") + 1) : rawKey;

        // 自动提取不重复字符作为表面进制字符集，不再死板拦截重复字符
        StringBuilder uniqueAlphabet = new StringBuilder();
        Set<Character> seen = new HashSet<>();
        for (char c : alphabetPart.toCharArray()) {
            if (!Character.isWhitespace(c) && seen.add(c)) {
                uniqueAlphabet.append(c);
            }
        }

        String alphabet = uniqueAlphabet.toString();
        if (alphabet.length() < 2) {
            JOptionPane.showMessageDialog(this, "伪装字符集至少需要包含 2 个不同的字符（例如输入：密码#阿巴）。", "拦截", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String input = inputArea.getText();
        if (input.isEmpty()) return;

        try {
            if (isEncrypt) {
                outputArea.setText(encryptSecret(input, secretSeed, alphabet));
            } else {
                outputArea.setText(decryptSecret(input, secretSeed, alphabet));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "解密失败：密码错误或密文已被篡改！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int getRequiredDigits(int radix) {
        int digits = 1;
        while (Math.pow(radix, digits) < 256) {
            digits++;
        }
        return digits;
    }

    /**
     * 基于 SHA-256 与随机 Salt 生成与数据等长的密钥流
     */
    private byte[] generateKeyStream(String secretSeed, byte[] salt, int length) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] stream = new byte[length];
        int offset = 0;
        int counter = 0;

        while (offset < length) {
            sha256.reset();
            sha256.update(secretSeed.getBytes(StandardCharsets.UTF_8));
            sha256.update(salt);
            sha256.update(new byte[]{
                    (byte) (counter >>> 24), (byte) (counter >>> 16),
                    (byte) (counter >>> 8), (byte) counter
            });
            byte[] hash = sha256.digest();
            int copyLen = Math.min(hash.length, length - offset);
            System.arraycopy(hash, 0, stream, offset, copyLen);
            offset += copyLen;
            counter++;
        }
        return stream;
    }

    private String encryptSecret(String plainText, String secretSeed, String alphabet) throws Exception {
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        // 1. 生成 4 字节随机 Salt，确保相同明文每次加密结果完全不同
        byte[] salt = new byte[4];
        new SecureRandom().nextBytes(salt);

        // 2. 计算 2 字节校验头（用于解密时立刻识别密码是否正确，防止解出乱码）
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        md.update(plainBytes);
        byte[] check = md.digest();

        // 3. 组装载荷：[2字节校验 + 明文字节]，并用 SHA-256 密钥流异或加密
        byte[] payload = new byte[2 + plainBytes.length];
        payload[0] = check[0];
        payload[1] = check[1];
        System.arraycopy(plainBytes, 0, payload, 2, plainBytes.length);

        byte[] keyStream = generateKeyStream(secretSeed, salt, payload.length);
        byte[] finalBytes = new byte[4 + payload.length];
        System.arraycopy(salt, 0, finalBytes, 0, 4);
        for (int i = 0; i < payload.length; i++) {
            finalBytes[4 + i] = (byte) (payload[i] ^ keyStream[i]);
        }

        // 4. 编码为表面上的 N 进制字符（如 abbabb...）
        int radix = alphabet.length();
        int digits = getRequiredDigits(radix);
        StringBuilder sb = new StringBuilder(finalBytes.length * digits);

        for (byte b : finalBytes) {
            int val = b & 0xFF;
            char[] chunk = new char[digits];
            for (int i = digits - 1; i >= 0; i--) {
                chunk[i] = alphabet.charAt(val % radix);
                val /= radix;
            }
            sb.append(chunk);
        }
        return sb.toString();
    }

    private String decryptSecret(String cipherText, String secretSeed, String alphabet) throws Exception {
        String cleanText = cipherText.replaceAll("\\s+", "");
        int radix = alphabet.length();
        int digits = getRequiredDigits(radix);

        if (cleanText.length() % digits != 0 || cleanText.length() / digits < 6) {
            throw new IllegalArgumentException("密文长度不合法");
        }

        byte[] finalBytes = new byte[cleanText.length() / digits];
        for (int i = 0; i < finalBytes.length; i++) {
            int val = 0;
            for (int j = 0; j < digits; j++) {
                char c = cleanText.charAt(i * digits + j);
                int digitValue = alphabet.indexOf(c);
                if (digitValue == -1) {
                    throw new IllegalArgumentException("包含非法字符");
                }
                val = val * radix + digitValue;
            }
            if (val > 255) {
                throw new IllegalArgumentException("数值越界");
            }
            finalBytes[i] = (byte) val;
        }

        // 1. 提取前 4 字节 Salt 并还原密钥流
        byte[] salt = new byte[4];
        System.arraycopy(finalBytes, 0, salt, 0, 4);

        int payloadLen = finalBytes.length - 4;
        byte[] keyStream = generateKeyStream(secretSeed, salt, payloadLen);
        byte[] payload = new byte[payloadLen];
        for (int i = 0; i < payloadLen; i++) {
            payload[i] = (byte) (finalBytes[4 + i] ^ keyStream[i]);
        }

        // 2. 验证 2 字节校验头，防止错误密码解出乱码
        byte[] plainBytes = new byte[payloadLen - 2];
        System.arraycopy(payload, 2, plainBytes, 0, plainBytes.length);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        md.update(plainBytes);
        byte[] check = md.digest();
        if (check[0] != payload[0] || check[1] != payload[1]) {
            throw new IllegalArgumentException("密码不匹配");
        }

        return new String(plainBytes, StandardCharsets.UTF_8);
    }
}