package shaoxia.modules;

import shaoxia.Utils.AppConfig;
import shaoxia.Utils.FontUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.GlassPanel;
import shaoxia.Utils.GlassTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 区域与邮编精准解析面板
 * （民政部 dmfw.mca.gov.cn 官方接口 + 科技蓝地球仪网络探针 + 街道级定位联动 + 横版导出）
 */
public class RegionFetcherPanel extends BackgroundPanel {
    private MainLauncher parent;
    private GlassTextField addressInput;

    // 双栏文本区（保持与 MainLauncher 状态持久化字段名一致）
    private JTextPane regionArea;
    private JTextPane cultureArea;

    // 左右布局约束引用（用于动态同步左右对齐基准线）
    private JPanel regionWrapper;
    private GridBagConstraints gbcReg;
    private ScrollableWrapperPanel cultureWrapper;
    private GridBagConstraints gbcCult;

    // 右上角蓝色地球仪探针
    private EarthIndicator earthIndicator;
    private volatile boolean isNetworkConnected = true;

    // 民政部国家地名信息库官方行政区划接口
    private static final String MCA_API_URL = "https://dmfw.mca.gov.cn/9095/xzqh/getList";

    // 高德 API Key 通过环境变量或 ~/.toolkit/config.properties 中的 AMAP_KEY 读取

    static {
        System.setProperty("jsse.enableSNIExtension", "true");
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    }

    private Timer debounceTimer;
    private Thread monitorThread;

    // 民政部官方行政区划内存缓存池
    private static final Map<String, McaDivision> MCA_CODE_MAP = new ConcurrentHashMap<>();
    private static final List<McaDivision> MCA_ALL_DIVISIONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static String CACHED_ADCODE_JSON = null;

    /**
     * 民政部行政区划标准数据结构
     */
    private static class McaDivision {
        String code;     // 6位标准行政区划代码 (如 110102)
        String rawCode;  // 接口原始代码
        String name;     // 官方标准名称 (如 西城区)
        int level;       // 行政级别 (1:省级, 2:地级, 3:县级)
        String type;     // 行政区划单位
        String province; // 所属省/直辖市标准名称
        String city;     // 所属地级市标准名称

        McaDivision(String code, String rawCode, String name, int level, String type, String province, String city) {
            this.code = code;
            this.rawCode = rawCode;
            this.name = name;
            this.level = level;
            this.type = type;
            this.province = province;
            this.city = city;
        }
    }

    /**
     * 支持 JScrollPane 宽度强制自适应的透明包装面板
     * 彻底解决 JTextPane 在 JPanel 包装后放进 JScrollPane 不自动换行、右半边裁切显示不全的 Bug
     */
    private static class ScrollableWrapperPanel extends JPanel implements Scrollable {
        public ScrollableWrapperPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 48;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            // 强制与右半边视口同宽，使 JTextPane 100% 自动换行并完整显示
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // 当文本高度小于视口高度时拉伸填满以支持对齐，超出时允许平滑纵向滚动
            if (getParent() instanceof JViewport) {
                return ((JViewport) getParent()).getHeight() > getPreferredSize().height;
            }
            return false;
        }
    }

    public RegionFetcherPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);

        initTopBar();
        initCenterArea();
        startNetworkMonitor();
    }

    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(15, 20, 0, 20));

        JPanel leftWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftWrapper.setOpaque(false);

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());
        leftWrapper.add(backBtn);

        topBar.add(leftWrapper, BorderLayout.WEST);

        earthIndicator = new EarthIndicator();
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.setOpaque(false);
        rightTop.add(earthIndicator);
        topBar.add(rightTop, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
    }

    private void initCenterArea() {
        JPanel centerContainer = new JPanel(new BorderLayout(0, 12));
        centerContainer.setOpaque(false);
        centerContainer.setBorder(new EmptyBorder(10, 20, 20, 20));

        GlassPanel inputWrapper = new GlassPanel(new BorderLayout(10, 0));
        inputWrapper.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("输入详细地址: ", JLabel.LEFT);
        titleLabel.setForeground(new Color(220, 220, 220));
        titleLabel.setFont(FontUtils.getChineseFont(Font.BOLD, 15));

        addressInput = new GlassTextField();
        addressInput.setFont(FontUtils.getChineseFont(Font.PLAIN, 15));
        addressInput.setPreferredSize(new Dimension(0, 38));

        inputWrapper.add(titleLabel, BorderLayout.WEST);
        inputWrapper.add(addressInput, BorderLayout.CENTER);

        GlassPanel outputWrapper = new GlassPanel(new BorderLayout());
        outputWrapper.setBorder(new EmptyBorder(15, 20, 15, 20));

        // 双栏布局：左侧区域信息与左下居中导出按钮，右侧完整展示传统文化与美誉
        JPanel splitPanel = new JPanel(new GridBagLayout());
        splitPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        regionArea = createTransparentTextPane("等待输入...");
        cultureArea = createTransparentTextPane("文化与美誉将在此显示...");

        // 1. 左侧栏：区域信息 + 底部居中“横版导出”按钮
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setOpaque(false);
        // 锁定基准尺寸为 (0, 0)，确保 GridBagLayout 严格按 weightx 比例分配左右宽度
        leftPanel.setPreferredSize(new Dimension(0, 0));

        regionWrapper = new JPanel(new GridBagLayout());
        regionWrapper.setOpaque(false);
        gbcReg = new GridBagConstraints();
        gbcReg.gridx = 0;
        gbcReg.gridy = 0;
        gbcReg.weightx = 1.0;
        gbcReg.weighty = 1.0;
        gbcReg.fill = GridBagConstraints.HORIZONTAL;
        gbcReg.anchor = GridBagConstraints.CENTER;
        regionWrapper.add(regionArea, gbcReg);
        leftPanel.add(regionWrapper, BorderLayout.CENTER);

        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
        exportPanel.setOpaque(false);
        ActionButton exportBtn = new ActionButton("横版导出");
        exportBtn.setPreferredSize(new Dimension(130, 36));
        exportBtn.addActionListener(e -> exportHorizontalTxt());
        exportPanel.add(exportBtn);
        leftPanel.add(exportPanel, BorderLayout.SOUTH);

        // 2. 右侧栏：使用 ScrollableWrapperPanel 确保 JTextPane 严格贴合右半边宽度自动折行并对齐左端
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(0, 0));

        cultureWrapper = new ScrollableWrapperPanel(new GridBagLayout());
        gbcCult = new GridBagConstraints();
        gbcCult.gridx = 0;
        gbcCult.gridy = 0;
        gbcCult.weightx = 1.0;
        gbcCult.weighty = 1.0;
        gbcCult.fill = GridBagConstraints.HORIZONTAL;
        gbcCult.anchor = GridBagConstraints.CENTER;
        // 默认等待状态下补偿左侧底部按钮高度（约 45px），使左右提示文字处于同一水平线
        gbcCult.insets = new Insets(0, 4, 45, 4);
        cultureWrapper.add(cultureArea, gbcCult);

        JScrollPane cultureScroll = new JScrollPane(cultureWrapper) {
            @Override
            public boolean isOpaque() { return false; }
        };
        cultureScroll.setOpaque(false);
        cultureScroll.getViewport().setOpaque(false);
        // 使用 SIMPLE_SCROLL_MODE 防止透明 JViewport 滚动或更新文本时出现残影 Bug
        cultureScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        cultureScroll.setBorder(BorderFactory.createEmptyBorder());
        cultureScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        cultureScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        cultureScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightPanel.add(cultureScroll, BorderLayout.CENTER);

        // 加入 splitPanel：左侧占 38%，右侧占 62%
        gbc.gridx = 0;
        gbc.weightx = 0.38;
        gbc.insets = new Insets(0, 0, 0, 16);
        splitPanel.add(leftPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.62;
        gbc.insets = new Insets(0, 0, 0, 0);
        splitPanel.add(rightPanel, gbc);

        outputWrapper.add(splitPanel, BorderLayout.CENTER);

        centerContainer.add(inputWrapper, BorderLayout.NORTH);
        centerContainer.add(outputWrapper, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        debounceTimer = new Timer(1000, e -> {
            String address = addressInput.getText().trim();
            if (address.isEmpty()) {
                regionArea.setText("等待输入...");
                cultureArea.setText("文化与美誉将在此显示...");
                return;
            }
            regionArea.setText("正在请求定位与官方区划...");
            cultureArea.setText("等待区域确认...");
            resolveAddressWithMcaOfficial(address);
        });
        debounceTimer.setRepeats(false);

        addressInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override
            public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override
            public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
        });
    }

    /**
     * 动态同步左右两栏的垂直对齐方式：
     * - 当显示多行解析结果（以 "[" 开头）时，左右两侧顶部标题同高对齐，右侧向下完整展开显示全部内容；
     * - 当处于单行等待提示时，左右两侧在完全相同的视觉中心线上居中对齐。
     */
    private void syncAlignmentMode() {
        if (regionWrapper == null || cultureWrapper == null || regionArea == null || cultureArea == null) {
            return;
        }
        String rText = regionArea.getText().trim();
        String cText = cultureArea.getText().trim();

        boolean isResultState = rText.startsWith("[ 定位") || cText.startsWith("[ 传统");

        if (isResultState) {
            gbcReg.anchor = GridBagConstraints.NORTH;
            gbcReg.insets = new Insets(18, 0, 0, 0);
            regionWrapper.add(regionArea, gbcReg);

            gbcCult.anchor = GridBagConstraints.NORTH;
            gbcCult.insets = new Insets(18, 4, 10, 4);
            cultureWrapper.add(cultureArea, gbcCult);
        } else {
            gbcReg.anchor = GridBagConstraints.CENTER;
            gbcReg.insets = new Insets(0, 0, 0, 0);
            regionWrapper.add(regionArea, gbcReg);

            gbcCult.anchor = GridBagConstraints.CENTER;
            gbcCult.insets = new Insets(0, 4, 45, 4);
            cultureWrapper.add(cultureArea, gbcCult);
        }
    }

    private JTextPane createTransparentTextPane(String defaultText) {
        JTextPane pane = new JTextPane() {
            @Override
            public void setText(String t) {
                super.setText(t);
                StyledDocument doc = this.getStyledDocument();
                SimpleAttributeSet center = new SimpleAttributeSet();
                StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
                doc.setParagraphAttributes(0, doc.getLength(), center, false);
                // 将光标重置回开头，防止 JScrollPane 自动滚动到文末导致首部被隐藏
                this.setCaretPosition(0);

                SwingUtilities.invokeLater(() -> {
                    syncAlignmentMode();
                    RegionFetcherPanel.this.revalidate();
                    RegionFetcherPanel.this.repaint();
                });
            }
        };

        pane.setOpaque(false);
        pane.setBackground(new Color(0, 0, 0, 0));
        pane.setForeground(new Color(240, 240, 240));
        pane.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        pane.setEditable(false);
        pane.setCursor(new Cursor(Cursor.HAND_CURSOR));

        pane.setText(defaultText);

        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String text = pane.getText();
                    if (!text.contains("等待") && !text.contains("失败") && !text.contains("正在") && !text.contains("异常")) {
                        String copyText = text.replaceFirst("^\\[.*?\\]\\s*", "").trim();
                        StringSelection selection = new StringSelection(copyText);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        JOptionPane.showMessageDialog(RegionFetcherPanel.this, "内容已成功复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });
        return pane;
    }

    // =========================================================================
    // 民政部官方接口 (https://dmfw.mca.gov.cn/9095/xzqh/getList) 实时按需调用
    // =========================================================================

    /**
     * 发送 GET 请求至 https://dmfw.mca.gov.cn/9095/xzqh/getList
     * @param year     年份（可为 null，默认最新年版）
     * @param code     行政区划代码（可为 null）
     * @param maxLevel 最大查询深度（必填，0~2）
     */
    private String requestMcaApi(Integer year, String code, int maxLevel) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(MCA_API_URL);
        urlBuilder.append("?maxLevel=").append(Math.max(0, Math.min(2, maxLevel)));
        if (year != null && year > 1900) {
            urlBuilder.append("&year=").append(year);
        }
        if (code != null && !code.trim().isEmpty()) {
            urlBuilder.append("&code=").append(URLEncoder.encode(code.trim(), "UTF-8"));
        }

        URL url = URI.create(urlBuilder.toString()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Referer", "https://dmfw.mca.gov.cn/interface.html");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(3500);
        conn.setReadTimeout(4500);

        if (conn.getResponseCode() == 200) {
            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return s.hasNext() ? s.next() : null;
            }
        }
        return null;
    }

    private void traverseMcaNode(JSONObject node, String parentProv, String parentCity, int depth,
                                 List<McaDivision> listOut, Map<String, McaDivision> mapOut) {
        String rawCode = node.optString("code", "").trim();
        String name = node.optString("name", "").trim();
        int level = node.optInt("level", depth);
        String type = node.optString("type", "").trim();

        if (rawCode.isEmpty() || name.isEmpty()) return;

        String code6 = normalizeTo6DigitCode(rawCode);
        String curProv = parentProv;
        String curCity = parentCity;

        if (level == 1 || depth == 1 || code6.endsWith("0000")) {
            curProv = name;
            if (isMunicipality(name)) {
                curCity = name;
            }
        } else if (level == 2 || depth == 2 || code6.endsWith("00")) {
            if ("市辖区".equals(name) || "县".equals(name) || "省直辖县级行政区划".equals(name) || "自治区直辖县级行政区划".equals(name)) {
                curCity = curProv;
            } else {
                curCity = name;
            }
        } else {
            if (curCity.isEmpty()) {
                curCity = curProv;
            }
        }

        McaDivision div = new McaDivision(code6, rawCode, name, level, type, curProv, curCity);
        listOut.add(div);
        mapOut.put(code6, div);
        if (!rawCode.equals(code6)) {
            mapOut.put(rawCode, div);
        }

        JSONArray children = node.optJSONArray("children");
        if (children != null && children.length() > 0) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child != null) {
                    traverseMcaNode(child, curProv, curCity, depth + 1, listOut, mapOut);
                }
            }
        }
    }

    private static boolean isMunicipality(String provName) {
        return provName.startsWith("北京") || provName.startsWith("天津")
                || provName.startsWith("上海") || provName.startsWith("重庆");
    }

    private static String normalizeTo6DigitCode(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 6) {
            return digits.substring(0, 6);
        }
        StringBuilder sb = new StringBuilder(digits);
        while (sb.length() < 6) {
            sb.append("0");
        }
        return sb.toString();
    }

    // =========================================================================
    // 核心定位与民政部官方核验主流程（无阻塞极速版）
    // =========================================================================

    private void resolveAddressWithMcaOfficial(String input) {
        Thread.ofVirtual().name("Region-AddressResolver").start(() -> {
            try {
                String trimmed = input.trim();
                String targetAdcode = null;

                // 1. 若用户直接输入了 6 位行政区划代码（如 110102）
                if (trimmed.matches("^\\d{6,12}$")) {
                    targetAdcode = normalizeTo6DigitCode(trimmed);
                }

                // 2. 通过地理编码将用户输入的地址（支持街道、小区、省市县）转换为 6 位区划代码
                if (targetAdcode == null) {
                    if (!AppConfig.isConfigured("AMAP_KEY")) {
                        SwingUtilities.invokeLater(() -> regionArea.setText(
                                "[提示] 未配置 AMAP_KEY，仅支持直接输入 6~12 位行政区划代码检索。\n"
                                        + "如需街道/门牌级地址解析，请在环境变量或 ~/.toolkit/config.properties 中配置 AMAP_KEY。"));
                        return;
                    }
                    targetAdcode = getAdcodeFromAmap(trimmed, trimmed);
                    if (targetAdcode == null) {
                        String prefix = extractAdminPrefix(trimmed);
                        if (!prefix.isEmpty() && !prefix.equals(trimmed)) {
                            SwingUtilities.invokeLater(() -> regionArea.setText("精准匹配未命中，启动省市县降级检索..."));
                            targetAdcode = getAdcodeFromAmap(prefix, trimmed);
                        }
                    }
                }

                // 3. 拿着锁定到的 6 位代码，调用民政部官方接口 (getList?code=XX0000&maxLevel=2) 获取最新官方标准名称
                if (targetAdcode != null && !targetAdcode.isEmpty()) {
                    McaDivision mcaVerified = lookupMcaByCodeLive(targetAdcode);
                    if (mcaVerified != null) {
                        displayMcaResult(mcaVerified);
                        return;
                    }
                    // 若政务网接口临时维护或超时，平滑降级至本地字典展示，绝不报错卡死
                    searchLocalAdcodeJson(targetAdcode);
                } else {
                    SwingUtilities.invokeLater(() -> regionArea.setText("[失败] 无法精准匹配该区域，\n请检查地址或尝试仅输入省市县名称"));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> regionArea.setText("[异常] 定位网络请求发生错误"));
            }
        });
    }

    /**
     * 按省级代码 + maxLevel=2 调用民政部接口，查询本级(省)、下级(市)及下下级(县区)官方数据
     */
    private McaDivision lookupMcaByCodeLive(String code6) {
        McaDivision cached = MCA_CODE_MAP.get(code6);
        if (cached != null && !cached.province.isEmpty()) {
            return cached;
        }

        String provCode = code6.substring(0, 2) + "0000";
        try {
            String provJson = requestMcaApi(null, provCode, 2);
            if (provJson == null || !provJson.contains(code6.substring(0, 4))) {
                provJson = requestMcaApi(null, code6.substring(0, 2), 2);
            }
            if (provJson != null) {
                JSONObject root = new JSONObject(provJson);
                Object dataObj = root.opt("data");
                List<McaDivision> tempList = new ArrayList<>();
                Map<String, McaDivision> tempMap = new ConcurrentHashMap<>();

                if (dataObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) dataObj;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.optJSONObject(i);
                        if (item != null) traverseMcaNode(item, "", "", 1, tempList, tempMap);
                    }
                } else if (dataObj instanceof JSONObject) {
                    traverseMcaNode((JSONObject) dataObj, "", "", 1, tempList, tempMap);
                }

                if (!tempMap.isEmpty()) {
                    MCA_CODE_MAP.putAll(tempMap);
                    for (McaDivision d : tempList) {
                        if (!MCA_ALL_DIVISIONS.contains(d)) MCA_ALL_DIVISIONS.add(d);
                    }
                }
            }
        } catch (Exception ignored) {}

        return MCA_CODE_MAP.get(code6);
    }

    /**
     * 展示民政部官方标准结果 + 本地邮编补齐
     */
    private void displayMcaResult(McaDivision div) {
        String zipCode = lookupZipCodeFromLocal(div.code, div.name);

        String province = !div.province.isEmpty() ? div.province : div.name;
        String city = div.city;
        String county = div.level >= 3 ? div.name : (div.level == 2 ? "全境 / 市辖区" : "全境");

        StringBuilder out = new StringBuilder();
        out.append("[ 定位与邮编解析成功 ]\n\n");
        out.append("省/直辖市: \t").append(province).append("\n");
        if (!city.isEmpty() && !city.equals(province)) {
            out.append("地 级 市: \t").append(city).append("\n");
        }
        out.append("区 / 县: \t").append(county).append("\n");
        out.append("邮政编码: \t").append(zipCode).append("\n");

        SwingUtilities.invokeLater(() -> {
            regionArea.setText(out.toString());
            String fullLocation = province + (city.equals(province) ? "" : city) + (div.level >= 3 ? div.name : "");
            fetchCultureAndReputation(fullLocation);
        });
    }

    private String lookupZipCodeFromLocal(String code6, String divisionName) {
        try {
            loadLocalAdcodeJsonIfNeeded();
            if (CACHED_ADCODE_JSON == null || CACHED_ADCODE_JSON.isEmpty()) return "000000";

            Matcher m = Pattern.compile("\"code\"\\s*:\\s*\"" + Pattern.quote(code6) + "\"").matcher(CACHED_ADCODE_JSON);
            if (m.find()) {
                int startIdx = CACHED_ADCODE_JSON.lastIndexOf("{", m.start());
                int endIdx = CACHED_ADCODE_JSON.indexOf("}", m.start());
                if (startIdx != -1 && endIdx != -1) {
                    String zip = extractJsonField(CACHED_ADCODE_JSON.substring(startIdx, endIdx + 1), "zipCode");
                    if (!zip.isEmpty()) return zip;
                }
            }

            if (divisionName != null && !divisionName.isEmpty()) {
                Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"" + Pattern.quote(divisionName) + "\"").matcher(CACHED_ADCODE_JSON);
                if (nameMatcher.find()) {
                    int startIdx = CACHED_ADCODE_JSON.lastIndexOf("{", nameMatcher.start());
                    int endIdx = CACHED_ADCODE_JSON.indexOf("}", nameMatcher.start());
                    if (startIdx != -1 && endIdx != -1) {
                        String zip = extractJsonField(CACHED_ADCODE_JSON.substring(startIdx, endIdx + 1), "zipCode");
                        if (!zip.isEmpty()) return zip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "000000";
    }

    private void loadLocalAdcodeJsonIfNeeded() {
        if (CACHED_ADCODE_JSON == null) {
            InputStream is = getClass().getResourceAsStream("/regionfetcher/Adcode.json");
            if (is != null) {
                try (Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                    CACHED_ADCODE_JSON = s.hasNext() ? s.next() : "";
                }
            }
        }
    }

    // ==========================================
    // 导出横板文本功能：自动解析冒号并拼接
    // ==========================================
    private void exportHorizontalTxt() {
        String text = regionArea.getText();

        if (text == null || text.trim().isEmpty() || text.contains("等待") || text.contains("失败") || text.contains("正在") || text.contains("异常") || text.contains("未在本地")) {
            JOptionPane.showMessageDialog(this, "当前无有效的解析数据可供导出！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder resultBuilder = new StringBuilder();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[")) {
                continue;
            }

            String[] parts = line.split("[:：]");
            if (parts.length > 1) {
                String val = parts[1].trim();
                if (!val.isEmpty()) {
                    if (resultBuilder.length() > 0) {
                        resultBuilder.append("_");
                    }
                    resultBuilder.append(val);
                }
            }
        }

        String finalName = resultBuilder.toString();

        if (finalName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未能解析到有效数据字段，请检查内容格式！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File desktopDir = new File(System.getProperty("user.home"), "Desktop");
        File fileToSave = new File(desktopDir, finalName + ".txt");

        try (FileWriter fw = new FileWriter(fileToSave, StandardCharsets.UTF_8)) {
            fw.write(finalName);
            JOptionPane.showMessageDialog(this, "文件已成功直接导出至桌面: \n" + fileToSave.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "文件导出至桌面失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getAdcodeFromAmap(String searchKeyword, String originalAddress) throws Exception {
        String amapKey = AppConfig.get("AMAP_KEY");
        if (amapKey.isEmpty()) {
            return null;
        }
        String urlStr = "https://restapi.amap.com/v3/geocode/geo?address="
                + URLEncoder.encode(searchKeyword, "UTF-8") + "&key=" + amapKey;

        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        if (conn.getResponseCode() == 200) {
            Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String json = s.hasNext() ? s.next() : "";
            s.close();

            JSONObject responseObj = new JSONObject(json);
            if ("1".equals(responseObj.optString("status"))) {
                JSONArray geocodes = responseObj.optJSONArray("geocodes");
                if (geocodes != null && geocodes.length() > 0) {
                    for (int i = 0; i < geocodes.length(); i++) {
                        JSONObject gc = geocodes.getJSONObject(i);

                        String prov = gc.optString("province").replace("[]", "");
                        String city = gc.optString("city").replace("[]", "");
                        String dist = gc.optString("district").replace("[]", "");

                        String coreProv = prov.replace("省", "").replace("市", "");
                        String coreCity = city.replace("市", "");
                        String coreDist = dist.replace("区", "").replace("县", "").replace("市", "");

                        if ((!coreDist.isEmpty() && originalAddress.contains(coreDist)) ||
                                (!coreCity.isEmpty() && originalAddress.contains(coreCity)) ||
                                (!coreProv.isEmpty() && originalAddress.contains(coreProv)) ||
                                originalAddress.contains("园区") ||
                                originalAddress.contains("开发区") ||
                                originalAddress.contains("新区")) {

                            return gc.optString("adcode");
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractAdminPrefix(String address) {
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5]+?[省市区县州盟])").matcher(address);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() == lastEnd) {
                sb.append(m.group());
                lastEnd = m.end();
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private void searchLocalAdcodeJson(String targetAdcode) {
        try {
            loadLocalAdcodeJsonIfNeeded();
            if (CACHED_ADCODE_JSON == null || CACHED_ADCODE_JSON.isEmpty()) {
                SwingUtilities.invokeLater(() -> regionArea.setText("[严重错误] 缺少 Adcode.json"));
                return;
            }
            String fullJson = CACHED_ADCODE_JSON;

            Matcher m = Pattern.compile("\"code\"\\s*:\\s*\"" + targetAdcode + "\"").matcher(fullJson);

            if (m.find()) {
                int targetIdx = m.start();
                int startIdx = fullJson.lastIndexOf("{", targetIdx);
                int endIdx = fullJson.indexOf("}", targetIdx);

                if (startIdx != -1 && endIdx != -1) {
                    String block = fullJson.substring(startIdx, endIdx + 1);

                    String province = extractJsonField(block, "province");
                    String city = extractJsonField(block, "city");
                    String name = extractJsonField(block, "name");
                    String zipCode = extractJsonField(block, "zipCode");

                    StringBuilder out = new StringBuilder();
                    out.append("[ 定位与邮编解析成功 ]\n\n");
                    out.append("省/直辖市: \t").append(province).append("\n");
                    if (!city.isEmpty() && !city.equals(province)) {
                        out.append("地 级 市: \t").append(city).append("\n");
                    }
                    out.append("区 / 县: \t").append(name).append("\n");
                    out.append("邮政编码: \t").append(zipCode).append("\n");

                    SwingUtilities.invokeLater(() -> {
                        regionArea.setText(out.toString());
                        String fullLocation = province + (city.equals(province) ? "" : city) + name;
                        fetchCultureAndReputation(fullLocation);
                    });
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> regionArea.setText("未在本地字典收录该信息。"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> regionArea.setText("[异常] 读取本地字典库失败"));
        }
    }

    private void fetchCultureAndReputation(String location) {
        cultureArea.setText("正在跨越山海，\n探寻属于这里的文化印记...");
        Thread.ofVirtual().name("Region-CultureFetcher").start(() -> {
            try {
                String apiKey = AppConfig.get("DEEPSEEK_API_KEY");
                if (apiKey.isEmpty()) {
                    SwingUtilities.invokeLater(() -> cultureArea.setText(
                            "[ 提示 ]\n未配置 DEEPSEEK_API_KEY。\n请在环境变量或 ~/.toolkit/config.properties 中填写后启用 AI 地区文化简介。"));
                    return;
                }
                String urlStr = "https://api.deepseek.com/chat/completions";
                URL url = URI.create(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(12000);

                String prompt = "请详细介绍一下“" + location + "”的独特传统文化、历史底蕴和地方美誉。字数控制在150字左右，内容请尽量丰富、充实且有深度。" +
                        "要求：必须突出该地的具体细节（如特色风俗、历史地标、人文故事或非遗文化等），直接返回介绍的正文，绝不要任何客套话、多余排版或Markdown格式，文本中绝对请勿使用双引号以免JSON解析出错。";

                String jsonInputString = "{\n" +
                        "  \"model\": \"deepseek-chat\",\n" +
                        "  \"messages\": [\n" +
                        "    {\"role\": \"user\", \"content\": \"" + prompt + "\"}\n" +
                        "  ]\n" +
                        "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A");
                    String responseJson = s.hasNext() ? s.next() : "";
                    s.close();

                    try {
                        JSONObject aiResponseObj = new JSONObject(responseJson);
                        JSONArray choices = aiResponseObj.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject messageObj = choices.getJSONObject(0).optJSONObject("message");
                            if (messageObj != null) {
                                String contentExtract = messageObj.optString("content").trim();
                                SwingUtilities.invokeLater(() ->
                                        cultureArea.setText("[ 传统文化与美誉 ]\n\n" + contentExtract)
                                );
                            } else {
                                SwingUtilities.invokeLater(() -> cultureArea.setText("[ 提示 ]\n未找到有效的回答正文。"));
                            }
                        } else {
                            SwingUtilities.invokeLater(() -> cultureArea.setText("[ 提示 ]\n大模型未返回有效选择结果。"));
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> cultureArea.setText("[ 异常 ]\n大模型返回的数据格式无法解析。"));
                    }

                } else {
                    int respCode = conn.getResponseCode();
                    SwingUtilities.invokeLater(() ->
                            cultureArea.setText("[ 获取失败 ]\n接口访问受限，状态码: " + respCode)
                    );
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> cultureArea.setText("[ 异常 ]\n探寻过程发生中断: " + e.getMessage()));
            }
        });
    }

    private String extractJsonField(String jsonBlock, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"").matcher(jsonBlock);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * 恢复高可用网络连通性探针（双节点兜底，确保本机联网时 100% 亮起 ON）
     */
    private void startNetworkMonitor() {
        this.monitorThread = Thread.ofVirtual().name("RegionNetworkMonitor").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                boolean connected = checkNodeReachable("https://restapi.amap.com")
                        || checkNodeReachable("https://www.baidu.com");
                setNetworkState(connected);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private boolean checkNodeReachable(String urlStr) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private void setNetworkState(boolean isOk) {
        if (this.isNetworkConnected != isOk) {
            this.isNetworkConnected = isOk;
            if (earthIndicator != null) {
                SwingUtilities.invokeLater(() -> earthIndicator.repaint());
            }
        }
    }

    /**
     * 右上角地球仪指示灯（已由绿色全面换装为科技蓝）
     */
    class EarthIndicator extends JPanel {
        private double angle = 0;
        private Timer rotateTimer;

        public EarthIndicator() {
            setOpaque(false);
            setPreferredSize(new Dimension(36, 36));

            rotateTimer = new Timer(50, e -> {
                if (isShowing()) {
                    angle += 0.05;
                    if (angle >= Math.PI * 2) angle -= Math.PI * 2;
                    repaint();
                }
            });
            rotateTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = 13;

            // 科技蓝配色：联网时呈深海蓝光晕 + 亮天蓝经纬线，断网时呈暗灰
            Color glowColor = isNetworkConnected ? new Color(0, 160, 255, 75) : new Color(60, 60, 60, 40);
            Color lineColor = isNetworkConnected ? new Color(30, 175, 255) : new Color(90, 90, 90);

            g2.setColor(glowColor);
            g2.fillOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);

            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            g2.drawLine(cx - r, cy, cx + r, cy);
            int halfDist = (int) (r * 0.866);
            g2.drawLine(cx - halfDist, cy - r / 2, cx + halfDist, cy - r / 2);
            g2.drawLine(cx - halfDist, cy + r / 2, cx + halfDist, cy + r / 2);

            int sweep1 = (int) (Math.sin(angle) * r);
            int sweep2 = (int) (Math.sin(angle + Math.PI / 2) * r);
            g2.drawOval(cx - Math.abs(sweep1), cy - r, Math.abs(sweep1) * 2, r * 2);
            g2.drawOval(cx - Math.abs(sweep2), cy - r, Math.abs(sweep2) * 2, r * 2);

            g2.setFont(FontUtils.getEnglishFont(Font.BOLD, 10));
            String statusText = isNetworkConnected ? "ON" : "OFF";
            int tw = g2.getFontMetrics().stringWidth(statusText);
            g2.drawString(statusText, cx - tw / 2, cy + r + 15);

            g2.dispose();
        }
    }
}