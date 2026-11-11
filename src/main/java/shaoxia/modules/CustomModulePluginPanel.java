package shaoxia.modules;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import shaoxia.MainLauncher;
import shaoxia.Utils.ActionButton;
import shaoxia.Utils.FontUtils;
import shaoxia.Utils.GlassPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义模块插件面板 CustomModulePluginPanel
 * 允许用户直接编写或粘贴 Java Swing 源码，通过 JDK 21 原生编译器 ToolProvider 内存即时编译，保存并永久挂载为独立模块。
 */
public class CustomModulePluginPanel extends BackgroundPanel {

    private static final String CARD_EDITOR = "EDITOR";
    private static final String CARD_RUNNER = "RUNNER";

    // 苦海卷轴提示词文本（封装在类内部，通过【获取卷轴】按钮一键复制到剪贴板，不占界面空间）
    private static final String KU_HAI_SCROLL_TEXT =
            "请用 Java Swing 编写一个独立的工具面板，代码将直接用于动态编译运行。严格遵守以下规范：\n\n"
            + "1.【结构与规范】：\n"
            + "- 单一完整 Java 源文件（包含完整 import 语句），仅使用 JDK 21 原生标准库，严禁使用任何第三方依赖。\n"
            + "- 必须提供一个 public class 且必须继承 javax.swing.JPanel。\n"
            + "- 必须包含 public 无参构造函数，在构造函数内完成所有的界面布局与事件监听绑定。\n\n"
            + "2.【界面与字体（极重要）】：\n"
            + "- 严禁 Emoji：所有界面组件（按钮、标签、日志、弹窗等）严禁包含任何 Emoji 表情/特殊符号（Swing 字体无法渲染会导致方块或乱码），请一律使用纯文字表达。\n"
            + "- 防乱码字体：文本组件请使用 new Font(\"Microsoft YaHei\", Font.PLAIN, 14) 或 Font.SANS_SERIF / Font.MONOSPACED，严禁指定 Arial、Consolas 等纯英文字体。\n"
            + "- 深色主题：面板建议 setOpaque(false) 或使用深色底色，文本与标签使用浅色/白色。\n\n"
            + "功能需求：\n"
            + "【在此填写具体功能需求，例如：简易计算器 / 文本加密工具 / 汇率转换器】";

    private final MainLauncher parent;
    private final File pluginSourceFile;

    private CardLayout cardLayout;
    private JPanel cardContainer;

    // 编辑器视图组件
    private JTextArea codeArea;
    private JLabel statusLabel;
    private JTextArea consoleArea;
    private ActionButton viewRunningBtn;

    // 运行视图组件
    private JPanel runnerMountContainer;
    private String currentClassName = "";
    private Component currentLoadedComponent = null;

    public CustomModulePluginPanel(MainLauncher parent) {
        super("bg_imgs/月球与地球.png");
        this.parent = parent;

        // 本地持久化存储路径: ~/.toolkit/CustomPluginComponent.java
        File dataDir = new File(System.getProperty("user.home"), ".toolkit");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.pluginSourceFile = new File(dataDir, "CustomPluginComponent.java");

        initUI();
        loadInitialState();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        cardContainer = new JPanel(cardLayout);
        cardContainer.setOpaque(false);

        // 初始化编辑器视图和运行器视图
        JPanel editorCard = createEditorView();
        JPanel runnerCard = createRunnerView();

        cardContainer.add(editorCard, CARD_EDITOR);
        cardContainer.add(runnerCard, CARD_RUNNER);

        add(cardContainer, BorderLayout.CENTER);
    }

    /**
     * 构建代码编辑器视图（大方简洁版）
     */
    private JPanel createEditorView() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. 顶部操作栏：每个按钮与“<< 返回菜单”尺寸完全一致（130 x 36）
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBar.setOpaque(false);

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());
        topBar.add(backBtn);

        ActionButton compileBtn = new ActionButton("编译并运行");
        compileBtn.setPreferredSize(new Dimension(130, 36));
        compileBtn.addActionListener(e -> compileAndLaunch());
        topBar.add(compileBtn);

        ActionButton getScrollBtn = new ActionButton("获取卷轴");
        getScrollBtn.setPreferredSize(new Dimension(130, 36));
        getScrollBtn.addActionListener(e -> copyKuHaiScrollToClipboard());
        topBar.add(getScrollBtn);

        viewRunningBtn = new ActionButton("查看当前运行");
        viewRunningBtn.setPreferredSize(new Dimension(130, 36));
        viewRunningBtn.setEnabled(false);
        viewRunningBtn.addActionListener(e -> cardLayout.show(cardContainer, CARD_RUNNER));
        topBar.add(viewRunningBtn);

        panel.add(topBar, BorderLayout.NORTH);

        // 2. 主体编辑区：左边距设为 10，右边距设为 10，与 topBar 首个按钮“<< 返回菜单”垂直严谨对齐
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);
        contentArea.setBorder(new EmptyBorder(0, 10, 5, 10));

        JPanel editorPanel = createCodeEditorPanel();
        contentArea.add(editorPanel, BorderLayout.CENTER);

        panel.add(contentArea, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 构建代码编辑面板
     */
    private JPanel createCodeEditorPanel() {
        GlassPanel centerGlass = new GlassPanel(new BorderLayout(8, 8));
        centerGlass.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 代码文本区：采用思源中文字体，彻底解决中文注释和字符串显示为问号乱码的问题
        codeArea = new JTextArea();
        codeArea.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        codeArea.setBackground(new Color(15, 23, 42, 210));
        codeArea.setForeground(new Color(241, 245, 249));
        codeArea.setCaretColor(Color.WHITE);
        codeArea.setSelectionColor(new Color(59, 130, 246, 120));
        codeArea.setTabSize(4);
        codeArea.setLineWrap(false);

        JScrollPane codeScrollPane = new JScrollPane(codeArea);
        codeScrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 40)));
        codeScrollPane.setOpaque(false);
        codeScrollPane.getViewport().setOpaque(false);
        centerGlass.add(codeScrollPane, BorderLayout.CENTER);

        // 底部控制台 / 错误反馈区域
        JPanel bottomArea = new JPanel(new BorderLayout(5, 5));
        bottomArea.setOpaque(false);

        statusLabel = new JLabel("状态: 就绪");
        statusLabel.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
        statusLabel.setForeground(new Color(203, 213, 225));
        bottomArea.add(statusLabel, BorderLayout.NORTH);

        consoleArea = new JTextArea(4, 20);
        consoleArea.setFont(FontUtils.getChineseFont(Font.PLAIN, 12));
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(10, 15, 25, 220));
        consoleArea.setForeground(new Color(203, 213, 225));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);

        JScrollPane consoleScrollPane = new JScrollPane(consoleArea);
        consoleScrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 30)));
        bottomArea.add(consoleScrollPane, BorderLayout.CENTER);

        centerGlass.add(bottomArea, BorderLayout.SOUTH);

        return centerGlass;
    }

    /**
     * 构建插件运行展示视图
     */
    private JPanel createRunnerView() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 10, 15, 10));

        // 顶部操作栏：所有按钮全部左对齐，尺寸统一为 130 x 36
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBar.setOpaque(false);

        ActionButton backBtn = new ActionButton("<< 返回菜单");
        backBtn.setPreferredSize(new Dimension(130, 36));
        backBtn.addActionListener(e -> parent.showMenu());
        topBar.add(backBtn);

        ActionButton editCodeBtn = new ActionButton("修改代码");
        editCodeBtn.setPreferredSize(new Dimension(130, 36));
        editCodeBtn.addActionListener(e -> cardLayout.show(cardContainer, CARD_EDITOR));
        topBar.add(editCodeBtn);

        ActionButton reloadBtn = new ActionButton("重新加载");
        reloadBtn.setPreferredSize(new Dimension(130, 36));
        reloadBtn.addActionListener(e -> compileAndLaunch());
        topBar.add(reloadBtn);

        panel.add(topBar, BorderLayout.NORTH);

        // 中间挂载容器（左边距设为 10，与顶部按钮严密对齐）
        runnerMountContainer = new JPanel(new BorderLayout());
        runnerMountContainer.setOpaque(false);
        runnerMountContainer.setBorder(new EmptyBorder(0, 10, 0, 0));
        panel.add(runnerMountContainer, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 初始加载持久化代码；若无代码则保持空白
     */
    private void loadInitialState() {
        if (pluginSourceFile.exists() && pluginSourceFile.length() > 0) {
            String source = readFileSafely(pluginSourceFile);
            if (source != null && !source.trim().isEmpty()) {
                codeArea.setText(source);
                codeArea.setCaretPosition(0);

                // 尝试静默编译并挂载
                if (doCompileAndMount(source, false)) {
                    viewRunningBtn.setEnabled(true);
                    cardLayout.show(cardContainer, CARD_RUNNER);
                    return;
                }
            }
        } else {
            codeArea.setText("");
        }

        cardLayout.show(cardContainer, CARD_EDITOR);
    }

    /**
     * 编译并启动运行
     */
    private void compileAndLaunch() {
        String source = codeArea.getText().trim();
        if (source.isEmpty()) {
            statusLabel.setText("状态: 代码为空");
            consoleArea.setForeground(new Color(248, 113, 113));
            consoleArea.setText("错误: 请先编写或粘贴有效的 Java 源码。");
            return;
        }

        statusLabel.setText("状态: 正在编译中...");
        consoleArea.setText("");

        boolean ok = doCompileAndMount(source, true);
        if (ok) {
            // 保存到磁盘文件实现持久化（强制 UTF-8）
            saveSourceToFile(source);
            viewRunningBtn.setEnabled(true);
            cardLayout.show(cardContainer, CARD_RUNNER);
        }
    }

    /**
     * 编译并挂载核心实现
     */
    private boolean doCompileAndMount(String source, boolean updateStatusUI) {
        try {
            // 1. 提取完整类名
            String fullClassName = extractFullClassName(source);
            if (fullClassName == null || fullClassName.isEmpty()) {
                throw new IllegalArgumentException("未检测到有效的 class 定义，请确保代码包含 class 声明。");
            }

            // 2. 配置 JDK 21 原生编译器 (支持 Java 21 全部新特性)
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("当前运行环境未检测到 JDK 编译器 (jdk.compiler 模块缺失)！\n"
                        + "如果您使用的是 jpackage 打包运行，请确保打包命令包含参数: --add-modules jdk.compiler");
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager stdManager = compiler.getStandardFileManager(diagnostics, Locale.CHINESE, StandardCharsets.UTF_8);
            MemoryJavaFileManager memManager = new MemoryJavaFileManager(stdManager);

            List<String> options = new ArrayList<>();
            options.add("-source");
            options.add("21");
            options.add("-target");
            options.add("21");
            options.add("-encoding");
            options.add("UTF-8");
            String classPath = System.getProperty("java.class.path");
            if (classPath != null && !classPath.trim().isEmpty()) {
                options.add("-classpath");
                options.add(classPath);
            }

            SourceJavaFileObject sourceFile = new SourceJavaFileObject(fullClassName, source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, memManager, diagnostics, options, null, Collections.singletonList(sourceFile));

            boolean success = task.call();
            if (!success) {
                StringBuilder errorMsg = new StringBuilder("编译失败，错误详情如下:\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        errorMsg.append(String.format("• [行 %d, 列 %d] %s\n",
                                d.getLineNumber(), d.getColumnNumber(), d.getMessage(Locale.CHINESE)));
                    }
                }
                throw new IllegalArgumentException(errorMsg.toString());
            }

            // 3. 反射加载并实例化
            MemoryClassLoader cl = new MemoryClassLoader(memManager.getCompiledBytecodes(), CustomModulePluginPanel.class.getClassLoader());
            Class<?> clazz = cl.loadClass(fullClassName);

            if (!Component.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("类 [" + fullClassName + "] 必须继承 java.awt.Component 或 javax.swing.JPanel！");
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Component comp = (Component) instance;

            // 4. 挂载到运行面板
            runnerMountContainer.removeAll();
            runnerMountContainer.add(comp, BorderLayout.CENTER);
            runnerMountContainer.revalidate();
            runnerMountContainer.repaint();

            this.currentClassName = fullClassName;
            this.currentLoadedComponent = comp;

            if (updateStatusUI) {
                statusLabel.setText("状态: 编译并加载成功 (Java 21 原生引擎)");
                consoleArea.setForeground(new Color(74, 222, 128));
                consoleArea.setText("成功编译并装载类: " + fullClassName + "\n组件就绪，已切换至运行视图。");
            }
            return true;

        } catch (Throwable ex) {
            if (updateStatusUI) {
                statusLabel.setText("状态: 编译或装载失败");
                consoleArea.setForeground(new Color(248, 113, 113));
                consoleArea.setText(formatException(ex));
                consoleArea.setCaretPosition(0);
            }
            return false;
        }
    }

    /**
     * 正则匹配包名与类名，拼接出完全限定名
     */
    private String extractFullClassName(String source) {
        String pkg = "";
        Pattern pkgPattern = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*;");
        Matcher pkgMatcher = pkgPattern.matcher(source);
        if (pkgMatcher.find()) {
            pkg = pkgMatcher.group(1).trim() + ".";
        }

        // 优先匹配 public class
        Pattern pubClassPattern = Pattern.compile("public\\s+class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)");
        Matcher pubClassMatcher = pubClassPattern.matcher(source);
        if (pubClassMatcher.find()) {
            return pkg + pubClassMatcher.group(1).trim();
        }

        // 其次匹配普通 class
        Pattern anyClassPattern = Pattern.compile("class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)");
        Matcher anyClassMatcher = anyClassPattern.matcher(source);
        if (anyClassMatcher.find()) {
            return pkg + anyClassMatcher.group(1).trim();
        }

        return null;
    }

    /**
     * 安全读取本地文件源码，强制使用 UTF-8 编码，彻底杜绝乱码
     */
    private String readFileSafely(File file) {
        if (!file.exists() || file.length() == 0) {
            return "";
        }
        try {
            // 抛弃 GBK 猜测，直接强制使用 UTF-8 进行读取
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            logError("读取本地源码异常: " + ex.getMessage());
            return "";
        }
    }

    /**
     * 保存源码至本地磁盘（强制使用 UTF-8，并处理空指针与权限异常）
     */
    private void saveSourceToFile(String source) {
        if (source == null) return;
        try {
            // 确保写入也是纯净的 UTF-8
            Files.writeString(pluginSourceFile.toPath(), source, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            logError("持久化源码到磁盘失败: " + ex.getMessage());
        }
    }

    /**
     * 一键获取苦海卷轴并复制到剪贴板
     */
    private void copyKuHaiScrollToClipboard() {
        try {
            StringSelection sel = new StringSelection(KU_HAI_SCROLL_TEXT);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            statusLabel.setText("状态: 已获取苦海卷轴并复制到剪贴板，可直接发送给 AI");
            consoleArea.setForeground(new Color(74, 222, 128));
            consoleArea.setText("已将苦海卷轴提示词约束复制到系统剪贴板。\n可直接粘贴发送给外部 AI，要求其生成满足规范的 Java Swing 工具代码。");
        } catch (Exception ex) {
            logError("获取卷轴失败: " + ex.getMessage());
        }
    }

    /**
     * 格式化异常调用栈
     */
    private String formatException(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    private void logError(String msg) {
        statusLabel.setText("状态: 操作异常");
        consoleArea.setForeground(new Color(248, 113, 113));
        consoleArea.setText(msg);
    }

    // =========================================================================
    // Java 21 原生内存编译与类加载支持
    // =========================================================================

    private static class SourceJavaFileObject extends SimpleJavaFileObject {
        private final String content;

        public SourceJavaFileObject(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static class BytecodeJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public BytecodeJavaFileObject(String name) {
            super(URI.create("bytes:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return baos;
        }

        public byte[] getBytecode() {
            return baos.toByteArray();
        }
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, BytecodeJavaFileObject> outputFiles = new HashMap<>();

        public MemoryJavaFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            BytecodeJavaFileObject file = new BytecodeJavaFileObject(className);
            outputFiles.put(className, file);
            return file;
        }

        public Map<String, byte[]> getCompiledBytecodes() {
            Map<String, byte[]> map = new HashMap<>();
            for (Map.Entry<String, BytecodeJavaFileObject> entry : outputFiles.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getBytecode());
            }
            return map;
        }
    }

    private static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> bytecodes;

        public MemoryClassLoader(Map<String, byte[]> bytecodes, ClassLoader parent) {
            super(parent);
            this.bytecodes = bytecodes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = bytecodes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
