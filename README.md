<p align="center">
  <img src="src/main/resources/imgs/app.png" alt="ToolKit Logo" width="100" height="100" />
</p>

<h1 align="center">ToolKit</h1>

<p align="center">
  <strong>面向日常办公、数据核对与高频文件处理的现代化 Java Swing 桌面工具箱</strong>
</p>

<p align="center">
  <a href="https://www.oracle.com/java/technologies/downloads/#java21"><img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21" /></a>
  <a href="https://www.formdev.com/flatlaf/"><img src="https://img.shields.io/badge/UI-FlatLaf%203.5.4-blue?style=flat-square" alt="FlatLaf" /></a>
  <a href="https://maven.apache.org/"><img src="https://img.shields.io/badge/Build-Maven%203-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Maven" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="MIT License" /></a>
  <a href="https://github.com/GallantTarry/toolkit/releases"><img src="https://img.shields.io/badge/Version-v1.9.8-brightgreen?style=flat-square" alt="Version" /></a>
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=flat-square&logo=windows&logoColor=white" alt="Windows" />
</p>

<p align="center">
  <a href="#核心特性">核心特性</a> •
  <a href="#功能模块全览">功能全览</a> •
  <a href="#快速上手">快速上手</a> •
  <a href="#详细使用指南">使用指南</a> •
  <a href="#外部服务配置">配置说明</a> •
  <a href="#构建与运行">构建与运行</a> •
  <a href="#项目结构">项目结构</a> •
  <a href="#技术栈与依赖">技术栈</a>
</p>

---

## <a id="核心特性"></a>💡 核心特性

- **13 大独立功能模块，主页九宫格自由定制**：主界面展示 9 个高频核心模块，支持在「控制中心」拖拽排序或将备选模块自由换入主页。
- **全模块常驻内存与本地持久化**：面板按需懒加载且常驻内存，切页零延迟；配合本地 JSON 状态保存，表单内容、常用路径、筛选规则与操作记录支持跨重启无缝恢复。
- **拖拽流极简交互（Drag & Drop）**：批量改名、内容替换、数据校对、格式转换、解压缩、文件互传等模块全面支持从资源管理器直接拖入文件或文件夹触发处理。
- **原生深色磨砂美学与渲染优化**：
  - 基于 FlatLaf 深色主题深度定制，具备 4px 纤薄磨砂玻璃圆角外框与 3D 翻转卡片转场动效；
  - 集成 JNA 调用 Windows 1ms 硬件定时器精度锁（消除默认 15.6ms 调度抖动，保持流畅 60 FPS 动画）；
  - 针对 Windows 透明窗口与中文输入法（IME）兼容进行了专项优化。
- **Java 21 内存动态插件扩展**：内置「编程助手」，利用 JDK 21 原生 `ToolProvider` 编译器在内存中即时编译并热挂载自定义 `JPanel` 插件；内置极简 AI 提示词卷轴，方便配合外部大模型零门槛扩展新功能。
- **桌面交互伴侣与彩蛋微游戏**：
  - **MITI 桌面小猫**：可自由拖拽吸附在主界面九宫格边角，支持互动语录、短期记忆沉淀与外部 AI 对话；
  - **《拯救公猪》**：主界面按 <kbd>G</kbd> 键即可切换进入的像素风横版格挡小游戏，支持剧情模式与排位挑战。

---

## <a id="功能模块全览"></a>🗂️ 功能模块全览

<table>
  <thead>
    <tr>
      <th align="center" width="60">图标</th>
      <th width="120">模块名称</th>
      <th width="110">标识 ID</th>
      <th>主要功能与核心场景</th>
      <th>适用输入与格式范围</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img src="src/main/resources/icons/renamer.png" width="32" height="32" /></td>
      <td><strong>重命名助手</strong></td>
      <td><code>RENAMER</code></td>
      <td>标段/包号/供应商标准化重命名、批量改名（支持撤销），以及农行/招行电子回单 PDF 智能拆分与命名</td>
      <td>常规文件；银行回单需为矢量文字版 PDF</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/checker.png" width="32" height="32" /></td>
      <td><strong>数据校对</strong></td>
      <td><code>CHECKER</code></td>
      <td>多轮报价异常波动预警、阶梯涨价检测与报价走势折线图展示，支持生成分析报告</td>
      <td>符合表头规范的 <code>.xlsx</code> 文件</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/finder.png" width="32" height="32" /></td>
      <td><strong>查找助手</strong></td>
      <td><code>FINDER</code></td>
      <td>多层级目录递归极速扫描、按扩展名分类、多行关键词模糊筛选，结果支持直接多选拖出</td>
      <td>任意本地文件与多层级目录</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/scoring.png" width="32" height="32" /></td>
      <td><strong>评分助手</strong></td>
      <td><code>SCORING</code></td>
      <td>自动识别文档中的技术/商务评分细则表，智能解析合并单元格并整理导出为规整 Excel</td>
      <td>包含标准结构表格的 <code>.docx</code> 文档</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/replacer.png" width="32" height="32" /></td>
      <td><strong>内容替换</strong></td>
      <td><code>REPLACER</code></td>
      <td>批量深度替换 Office 文档正文内容，支持预设槽位与自定义映射，可选同步替换文件名与目录名</td>
      <td><code>.docx</code>、<code>.pptx</code>、<code>.xls</code>、<code>.xlsx</code></td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/archive.png" width="32" height="32" /></td>
      <td><strong>解压缩助手</strong></td>
      <td><code>TREASURE</code></td>
      <td>左右双栏极简交互：左侧拖放极速解压，右侧拖放合并归档打包，支持全局怀表按钮设置加密密码</td>
      <td>ZIP / RAR 压缩包、任意待打包文件</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/region.png" width="32" height="32" /></td>
      <td><strong>地区助手</strong></td>
      <td><code>REGION</code></td>
      <td>支持 6~12 位行政区划代码检索、离线字典匹配及政务接口核验，可选对接地理编码与 AI 地方简介</td>
      <td>自然文本地址、6~12 位区划代码</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/converter.png" width="32" height="32" /></td>
      <td><strong>转换助手</strong></td>
      <td><code>CONVERTER</code></td>
      <td>调用本机 Word 引擎实现高质量 DOC/DOCX ↔ PDF 互转，以及多格式图片批量转 PDF</td>
      <td>Word 文档、PDF、常见图片格式</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/cloud.png" width="32" height="32" /></td>
      <td><strong>传输助手</strong></td>
      <td><code>CLOUD</code></td>
      <td>拖入文件一键开启局域网（LAN）或公网（WAN SSH 隧道）HTTP 文件服务，手机浏览器扫码即可双向传文件</td>
      <td>任意文件或文件夹</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/clipboard.png" width="32" height="32" /></td>
      <td><strong>粘贴板助手</strong></td>
      <td><code>CLIPBOARD</code></td>
      <td>九宫格独立文本卡槽，支持快捷键秒选、双击缩放预览与本地实时自动保存常用片段</td>
      <td>常用文本、指令模板、高频词条</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/secret.png" width="32" height="32" /></td>
      <td><strong>大声密谋</strong></td>
      <td><code>SECRET_CHAT</code></td>
      <td>基于对称口令的字符级文本加密与伪装混淆，支持一键复制与还原</td>
      <td>任意纯文本内容</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/comparison.png" width="32" height="32" /></td>
      <td><strong>公告比对</strong></td>
      <td><code>COMPARISON</code></td>
      <td>双栏智能核对 Word 与 PDF 公告中的项目编号、文件获取截止时间、应答截止时间等关键信息</td>
      <td>一份 Word 文档 + 一份矢量 PDF 文档</td>
    </tr>
    <tr>
      <td align="center"><img src="src/main/resources/icons/custommoduleplugin.png" width="32" height="32" /></td>
      <td><strong>编程助手</strong></td>
      <td><code>CUSTOM_PLUGIN</code></td>
      <td>运行时 Java Swing 源码编辑器与内存编译器，基于 JDK 21 即时编译并热加载到界面</td>
      <td>继承 <code>JPanel</code> 的自包含 Java 21 源码</td>
    </tr>
  </tbody>
</table>

---

## <a id="快速上手"></a>🚀 快速上手

### 环境要求

| 依赖项 | 推荐配置 | 说明 |
| :--- | :--- | :--- |
| **操作系统** | **Windows 10 / 11 (64-bit)** | 部分特性依赖 Windows 1ms 定时器锁与 Office COM 自动化接口 |
| **Java 环境** | **JDK 21（完整版）** | 「编程助手」动态编译依赖 `jdk.compiler` 模块；打包后的 EXE 需系统安装 JDK 21 |
| **文档转换** | **Microsoft Office Word**（可选） | 转换助手调用本机 Word COM 接口进行格式互转 |
| **RAR 解压** | `UnRAR.exe`（可选） | 见下方 [附加组件部署](#附加组件部署) |
| **WAN 穿透** | `OpenSSH`（可选） | 见下方 [附加组件部署](#附加组件部署) |

### 基本操作流程

1. **启动程序**：运行打包后的 `ToolKit.exe`，或通过命令行执行 `java --add-modules=jdk.compiler -jar target/ToolKit-1.9.8.jar`。
2. **进入与返回**：主界面点击图标触发 3D 翻转进入对应模块；点击左上角 `<< 返回菜单` 返回主面板。
3. **拖拽触发**：在 Windows 文件资源管理器中选中文件或文件夹，直接拖入目标模块的交互区域即可执行。
4. **定制主页**：点击主界面左上角齿轮打开「控制中心」，拖拽调整九宫格模块顺序或替换候补模块，保存后即时生效。
5. **系统托盘**：窗口关闭默认最小化至系统托盘（可在设置中更改）；右键托盘图标可选择完全退出。

---

## <a id="详细使用指南"></a>📖 详细使用指南

### 1. 重命名与银行回单拆分
- **协同改名模式**：在界面维护常用主体名单后，选定标段、包号与文件类型，拖入文件即按规范命名；支持「撤销上一步改名」。
- **电子回单模式（农行 / 招行）**：
  - 将汇总 PDF 回单或所在文件夹拖入面板；
  - 程序通过 PDFBox 提取文字流中的收款人、账号、交易时间与金额，自动拆分并输出到同级 `回单重命名-输出` 目录（不破坏原文件）。

### 2. 报价校对与评分提取
- **数据校对**：Excel 需包含「供应商名称」「标包编号」以及各轮次报价表头；拖入后自动识别相邻轮次异常波动与阶梯涨价，支持可视化图表缩放查看。
- **评分助手**：拖入包含「技术评分标准」「商务评分细则」等表格的 `.docx` 文档，自动处理合并单元格并整理导出为 `.xlsx` 表格至桌面。

### 3. 内容替换与公告比对
> [!WARNING]
> **内容替换模块会直接原地覆盖修改原文件**，执行批量替换前请务必先做好文件备份！

- **内容替换**：支持对 `.docx` / `.xlsx` / `.xls` / `.pptx` 正文批量映射替换，提供「纯黑模式 / 保留原色」与「同步替换文件及目录名」开关。
- **公告比对**：左栏拖入 Word 文档、右栏拖入 PDF 文档，一键核对项目编号及关键截止时间是否一致。

### 4. 传输助手（LAN / WAN 文件互传）
- 拖入要分享的文件或文件夹，按需开启「LAN 直连」或「WAN 穿透」；
- 手机扫码打开网页端即可下载电脑端挂载的文件，或向电脑上传文件（默认保存在用户 `下载/CloudShareUploads` 目录）。

### 5. 编程助手（JDK 21 运行时插件）
- 基于 `ToolProvider.getSystemJavaCompiler()`、`MemoryJavaFileManager` 与 `MemoryClassLoader` 实现纯内存编译加载，无需写入临时 `.class` 文件；
- 点击「获取卷轴」可复制适配好的 AI Prompt 规范，发给任意大模型即可生成兼容深色主题、无字体乱码的自定义 Swing 插件源码。

### 6. 互动彩蛋
- **MITI 桌面小猫**：左键点击触发语录与动作，按住可拖拽吸附至九宫格任意位置，右键点击可打开 AI 对话气泡（需配置 `DEEPSEEK_API_KEY`）。
- **《拯救公猪》**：在主界面按 <kbd>G</kbd> 键进入游戏（<kbd>Space</kbd>/<kbd>↑</kbd> 跳跃、<kbd>←</kbd>/<kbd>→</kbd> 格挡、<kbd>↓</kbd> 护盾），按 <kbd>E</kbd> 返回主界面。

---

## <a id="外部服务配置"></a>🔑 外部服务配置（可选）

为了仓库安全，**本开源版本不包含任何硬编码密钥或私人后端地址**。所有离线办公功能（文件查找、重命名、回单拆分、数据校对、评分提取、内容替换、解压缩、格式转换、大声密谋、公告比对、编程助手、局域网传输等）**零配置即可直接使用**。

如需开启在线 AI 对话、街道级地址解析或小游戏在线排行榜，可通过以下**任意一种方式**配置（优先级：**系统环境变量 > JVM `-D` 参数 > 本地配置文件**）：

### 方式一：本地配置文件（推荐，免改系统环境变量）

1. 在用户主目录下创建 `.toolkit` 目录（软件首次运行会自动创建）；
2. 将仓库根目录的 [`config.example.properties`](config.example.properties) 复制为 `~/.toolkit/config.properties`（Windows 下通常为 `C:\Users\<你的用户名>\.toolkit\config.properties`）；
3. 按需填入对应配置项并保存（支持热读取，无需重新编译）：

| 配置键名 | 所属模块 | 作用说明 | 未配置时的行为 |
| :--- | :--- | :--- | :--- |
| `DEEPSEEK_API_KEY` | MITI 小猫 / 地区助手 | 驱动小猫 AI 对话与地区文化简介生成 | 友好提示需配置密钥，不影响离线语录与行政代码查询 |
| `AMAP_KEY` | 地区助手 | 高德地理编码接口，用于街道/门牌级地址转区划代码 | 仍支持直接输入 6~12 位行政代码及离线字典检索 |
| `SAVEPIG_WORKER_URL` | 《拯救公猪》 | 自定义排行榜接口地址（Cloudflare Workers 等） | 游戏正常本地游玩，排行榜仅显示离线提示 |
| `SAVEPIG_GAME_KEY` | 《拯救公猪》 | 排行榜接口请求头 `X-Game-Key` 校验密钥 | 可选；如你的后端不需要校验可留空 |

### 方式二：系统环境变量或启动参数

```powershell
# 示例：通过 JVM 启动参数传入
java --add-modules=jdk.compiler -DDEEPSEEK_API_KEY=你的密钥 -jar target/ToolKit-1.9.8.jar
```

---

## <a id="构建与运行"></a>🛠️ 构建与运行

### 1. 准备环境
- **JDK 21**（需完整 JDK，确保 `java -version` 与 `javac -version` 均为 21）
- **Apache Maven 3.8+**

### 2. 克隆与打包

```powershell
# 1. 克隆本仓库
git clone https://github.com/GallantTarry/toolkit.git
cd toolkit

# 2. 使用 Maven 清理并打包
mvn clean package
```

打包完成后，会在 `target/` 目录下生成：
- `target/ToolKit-1.9.8.jar`：包含全部依赖的可执行 Fat-JAR
- `target/ToolKit.exe`：由 Launch4j 插件生成的 Windows 原生启动器

### 3. 启动运行

```powershell
# 方式 A：运行 Windows 启动器
.\target\ToolKit.exe

# 方式 B：命令行直接启动 JAR（务必保留 --add-modules=jdk.compiler 以启用编程助手）
java --add-modules=jdk.compiler -jar target/ToolKit-1.9.8.jar
```

### <a id="附加组件部署"></a>4. 附加组件部署（按需）

| 组件 | 用途 | 放置路径（相对程序运行目录） |
| :--- | :--- | :--- |
| **UnRAR.exe** | 解压 `.rar` 压缩包 | `app/tools/UnRAR.exe` 或 `tools/UnRAR.exe` |
| **ssh.exe** | 传输助手公网 WAN 隧道映射 | `app/engines/openssh/ssh.exe` 或 `engines/openssh/ssh.exe` |
| **Microsoft Word** | 转换助手文档格式互转 | 本机安装正版微软 Office Word 即可 |

---

## <a id="项目结构"></a>📂 项目结构

```text
toolkit/
├── .gitignore                          # Git 忽略规则（忽略编译产物、IDE 配置与本地密钥）
├── LICENSE                             # MIT 开源许可证
├── README.md                           # 中文项目文档
├── config.example.properties           # 外部 API 配置示例模板
├── pom.xml                             # Maven 构建配置 (JDK 21 / Shade / Launch4j)
└── src/main/
    ├── java/shaoxia/
    │   ├── MainLauncher.java           # 程序入口、窗口框架、3D 翻转调度与状态持久化
    │   ├── modules/                    # 13 大核心功能面板 + 彩蛋游戏
    │   │   ├── ArchivePanel.java               # 解压缩助手
    │   │   ├── BackgroundPanel.java            # 统一玻璃底板基类
    │   │   ├── CheckerPanel.java               # 报价数据校对
    │   │   ├── ClipboardPanel.java             # 粘贴板助手
    │   │   ├── CloudSharePanel.java            # 局域网/公网文件传输助手
    │   │   ├── ComparisonPanel.java            # 公告比对助手
    │   │   ├── ConverterPanel.java             # 文档与图片格式转换
    │   │   ├── CustomModulePluginPanel.java    # 编程助手（JDK 21 内存动态编译）
    │   │   ├── FinderPanel.java                # 文件查找助手
    │   │   ├── RegionFetcherPanel.java         # 行政区划与邮编助手
    │   │   ├── RenamerPanel.java               # 协同改名与银行回单拆分
    │   │   ├── ReplacerPanel.java              # 文档内容批量替换
    │   │   ├── ScoringPanel.java               # 评分细则提取
    │   │   ├── SecretChatPanel.java            # 大声密谋文字加密
    │   │   └── savepig/                        # 《拯救公猪》小游戏与排行客户端
    │   ├── ui/components/              # MITI 桌面小猫、记忆管理、控制中心与托盘菜单
    │   └── Utils/                      # AppConfig 配置读取、字体、磨砂控件与 1ms 定时器锁
    └── resources/
        ├── bg_imgs/                    # 默认背景壁纸
        ├── blucat/                     # MITI 小猫互动语料
        ├── fonts/                      # 内置字体资源 (Inter, Zpix, 思源黑体等)
        ├── icons/                      # 模块图标
        ├── imgs/                       # 应用图标与精灵图
        ├── music/                      # 背景音乐与音效
        └── regionfetcher/              # 全国行政区划离线 JSON 字典
```

---

## <a id="技术栈与依赖"></a>🧰 技术栈与依赖

所有版本均以 [`pom.xml`](pom.xml) 为准：

| 组件库 | 版本 | 核心用途 |
| :--- | :--- | :--- |
| **Java Platform** | `21` | Record、虚拟线程（Virtual Threads）、JDK 内存编译器 API |
| **FlatLaf** | `3.5.4` | 现代化 Swing 深色主题与圆角控件外观 |
| **Apache POI** | `5.4.0` | Word / Excel / PowerPoint 文档解析与内容替换 |
| **Apache PDFBox** | `2.0.33` | 银行回单 PDF 矢量文本提取与页面拆分 |
| **Zip4j** | `2.11.5` | 流式 ZIP 归档打包与 AES / ZipCrypto 加解密 |
| **ZXing** | `3.5.3` | 传输助手局域网/公网扫码二维码生成 |
| **JNA** | `5.14.0` | Windows Win32 底层互操作（1ms 硬件定时器精度锁） |
| **JLayer** | `1.0.1` | 音频流与游戏背景音乐播放 |
| **org.json** | `20240303` | JSON 数据解析与工作区状态持久化 |
| **Log4j2** | `2.24.1` | 日志基础支撑 |
| **Launch4j Plugin** | `2.4.1` | 将 Fat-JAR 封装为 Windows 原生 `ToolKit.exe` |

---

## <a id="数据与安全"></a>🔒 数据与安全说明

程序运行时会在用户主目录 `~/.toolkit/` 下保存本地工作状态与配置：
- `config.properties`：可选的外部 API 密钥配置文件（需自行按模板创建，**切勿上传至公开仓库**）；
- `workspace_state.json`：各模块表单与窗口状态；
- `finder_session.json` / `finder_*.cache`：查找助手缓存；
- `clipboard_history.json`：粘贴板卡槽记录；
- `bluecat_memory.json`：MITI 小猫对话记忆；
- `CustomPluginComponent.java`：编程助手保存的自定义源码。

---

## <a id="开源许可证"></a>📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源发布。  
Copyright © 2026 TuKuai. All rights reserved.
