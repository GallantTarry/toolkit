package shaoxia.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

/**
 * 全局通用的高质感毛玻璃输入框
 * 完美融合版：15px 圆润弧度 + 全局统一黑雾底色 + 底层防撕裂 + 输入法全流程安全防护
 */
public class GlassTextField extends JTextField {

    public static final Color DEFAULT_GLASS_COLOR = new Color(30, 35, 40, 120);

    public GlassTextField() {
        super();
        setUI(new javax.swing.plaf.basic.BasicTextFieldUI());

        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        setForeground(Color.WHITE);
        setCaretColor(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        setFont(FontUtils.getChineseFont(Font.PLAIN, 14));

        // 监听中文输入法的组字状态，强迫父容器与自身同步重绘，防止输入法上屏过程中的残影与撕裂
        addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent event) {
                forceRepaint();
            }

            @Override
            public void caretPositionChanged(InputMethodEvent event) {
                forceRepaint();
            }
        });
    }

    @Override
    public void updateUI() {
        setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
    }

    private void forceRepaint() {
        Container p = getParent();
        if (p != null) {
            p.repaint(getX(), getY(), getWidth(), getHeight());
        } else {
            repaint();
        }
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        super.repaint(tm, x, y, width, height);
        Container p = getParent();
        if (p != null) {
            p.repaint(tm, getX() + x, getY() + y, width, height);
        }
    }

    /**
     * 重写 InputMethodRequests，提供底层异常熔断防护：
     * 当输入法询问光标坐标或候选框位置时，杜绝由于组件未完全上屏或分层透明窗口导致 NPE 或崩溃
     */
    @Override
    public InputMethodRequests getInputMethodRequests() {
        InputMethodRequests parentRequests = super.getInputMethodRequests();
        if (parentRequests == null) {
            return null;
        }
        return new InputMethodRequests() {
            @Override
            public Rectangle getTextLocation(TextHitInfo offset) {
                try {
                    if (!isShowing()) {
                        return new Rectangle(0, 0, 0, 0);
                    }
                    Rectangle r = parentRequests.getTextLocation(offset);
                    return r != null ? r : new Rectangle(0, 0, 0, 0);
                } catch (Throwable t) {
                    try {
                        Point p = getLocationOnScreen();
                        return new Rectangle(p.x, p.y + getHeight(), 0, 0);
                    } catch (Throwable ignored) {
                        return new Rectangle(0, 0, 0, 0);
                    }
                }
            }

            @Override
            public TextHitInfo getLocationOffset(int x, int y) {
                try {
                    return parentRequests.getLocationOffset(x, y);
                } catch (Throwable t) {
                    return null;
                }
            }

            @Override
            public int getInsertPositionOffset() {
                try {
                    return parentRequests.getInsertPositionOffset();
                } catch (Throwable t) {
                    return getCaretPosition();
                }
            }

            @Override
            public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
                try {
                    return parentRequests.getCommittedText(beginIndex, endIndex, attributes);
                } catch (Throwable t) {
                    return new AttributedString("").getIterator();
                }
            }

            @Override
            public int getCommittedTextLength() {
                try {
                    return parentRequests.getCommittedTextLength();
                } catch (Throwable t) {
                    return getText() != null ? getText().length() : 0;
                }
            }

            @Override
            public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
                try {
                    return parentRequests.cancelLatestCommittedText(attributes);
                } catch (Throwable t) {
                    return null;
                }
            }

            @Override
            public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
                try {
                    return parentRequests.getSelectedText(attributes);
                } catch (Throwable t) {
                    return null;
                }
            }
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(DEFAULT_GLASS_COLOR);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
        g2.dispose();
    }

    @Override
    public String getToolTipText() {
        String text = getText().trim();
        if (!text.isEmpty()) {
            return "<html><body style='font-family:微软雅黑; font-size:13px; padding:2px; color:white;'>"
                    + "<b>当前输入内容：</b><br>" + text + "</body></html>";
        }
        String defaultTip = super.getToolTipText();
        if (defaultTip != null) {
            return "<html><body style='font-family:微软雅黑; font-size:13px; padding:2px; color:white;'>"
                    + defaultTip + "</body></html>";
        }
        return null;
    }

    @Override
    public JToolTip createToolTip() {
        JToolTip tip = new JToolTip() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 20, 20, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tip.setComponent(this);
        tip.setOpaque(false);
        tip.setBackground(new Color(0, 0, 0, 0));
        tip.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return tip;
    }
}
