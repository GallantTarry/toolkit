package shaoxia.ui.components;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 桌面宠物 MITI 的持久化本地记忆管理系统
 * 1. 本地文件：存储于 ~/.toolkit/bluecat_memory.json
 * 2. 记忆内容：包含用户画像 (user_facts) 与对话记忆片段 (memories)
 * 3. 上限控制：条数上限 12 条记忆、8 条用户特征，FIFO 队列自动淘汰旧记忆，严格限制字符长度
 * 4. 动态注入：生成 System Prompt 上下文，供后续对话拼接
 */
public class BlueCatMemory {
    private static final int MAX_MEMORIES = 12;
    private static final int MAX_FACTS = 8;
    private static final int MAX_FACT_LEN = 35;
    private static final int MAX_SUMMARY_LEN = 50;

    private static final File DATA_DIR = new File(System.getProperty("user.home"), ".toolkit");
    private static final File MEMORY_FILE = new File(DATA_DIR, "bluecat_memory.json");
    private static final BlueCatMemory INSTANCE = new BlueCatMemory();

    static {
        if (!DATA_DIR.exists()) {
            DATA_DIR.mkdirs();
        }
    }

    public static BlueCatMemory getInstance() {
        return INSTANCE;
    }

    public static class MemoryEntry {
        public String time;
        public String summary;

        public MemoryEntry(String time, String summary) {
            this.time = time;
            this.summary = summary;
        }
    }

    private String nickname = "主人";
    private final List<String> userFacts = new ArrayList<>();
    private final List<MemoryEntry> recentMemories = new ArrayList<>();

    private BlueCatMemory() {
        loadFromFile();
    }

    /**
     * 生成拼接入 System Prompt 的记忆上下文
     */
    public synchronized String buildPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("【MITI的专属记忆库】：\n");
        sb.append("你拥有长期的记忆，能记住关于主人的画像与过往交流的精髓：\n");
        sb.append("- 主人称呼：").append(nickname).append("\n");
        if (!userFacts.isEmpty()) {
            sb.append("- 关于主人的认知与特征：\n");
            for (String fact : userFacts) {
                sb.append("  * ").append(fact).append("\n");
            }
        }
        if (!recentMemories.isEmpty()) {
            sb.append("- 你们过往的关键对话记忆（按时间先后）：\n");
            for (MemoryEntry mem : recentMemories) {
                sb.append("  * [").append(mem.time).append("] ").append(mem.summary).append("\n");
            }
        }
        sb.append("请在回复时自然地结合这些记忆，体现出你一直记得主人的喜好与过往交集，切忌生硬背诵记忆。\n");
        return sb.toString();
    }

    /**
     * 更新提炼出的记忆并持久化到本地 JSON 文件
     */
    public synchronized void updateMemory(String newFact, String newSummary) {
        boolean changed = false;
        String now = new SimpleDateFormat("MM-dd HH:mm").format(new Date());

        if (newFact != null && !newFact.trim().isEmpty() && !newFact.equalsIgnoreCase("null") && !newFact.equalsIgnoreCase("none")) {
            String cleanFact = newFact.trim();
            if (cleanFact.length() > MAX_FACT_LEN) {
                cleanFact = cleanFact.substring(0, MAX_FACT_LEN);
            }
            // 查重与相似过滤
            boolean exists = false;
            for (String f : userFacts) {
                if (f.equals(cleanFact) || f.contains(cleanFact) || cleanFact.contains(f)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                userFacts.add(cleanFact);
                while (userFacts.size() > MAX_FACTS) {
                    userFacts.remove(0); // 淘汰最旧特征
                }
                changed = true;
            }
        }

        if (newSummary != null && !newSummary.trim().isEmpty() && !newSummary.equalsIgnoreCase("null") && !newSummary.equalsIgnoreCase("none")) {
            String cleanSummary = newSummary.trim();
            if (cleanSummary.length() > MAX_SUMMARY_LEN) {
                cleanSummary = cleanSummary.substring(0, MAX_SUMMARY_LEN);
            }
            recentMemories.add(new MemoryEntry(now, cleanSummary));
            while (recentMemories.size() > MAX_MEMORIES) {
                recentMemories.remove(0); // 淘汰最旧对话事件
            }
            changed = true;
        }

        if (changed) {
            saveToFile();
        }
    }

    /**
     * 从本地文件加载 JSON
     */
    private synchronized void loadFromFile() {
        if (!MEMORY_FILE.exists()) {
            initDefaultMemory();
            saveToFile();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(MEMORY_FILE), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            JSONObject root = new JSONObject(sb.toString());

            JSONObject profile = root.optJSONObject("user_profile");
            if (profile != null) {
                this.nickname = profile.optString("nickname", "主人");
                JSONArray factsArr = profile.optJSONArray("facts");
                userFacts.clear();
                if (factsArr != null) {
                    for (int i = 0; i < factsArr.length() && userFacts.size() < MAX_FACTS; i++) {
                        String f = factsArr.optString(i, "").trim();
                        if (!f.isEmpty()) userFacts.add(f);
                    }
                }
            }

            JSONArray memArr = root.optJSONArray("memories");
            recentMemories.clear();
            if (memArr != null) {
                for (int i = 0; i < memArr.length() && recentMemories.size() < MAX_MEMORIES; i++) {
                    JSONObject m = memArr.optJSONObject(i);
                    if (m != null) {
                        String time = m.optString("time", "");
                        String summary = m.optString("summary", "");
                        if (!summary.isEmpty()) {
                            recentMemories.add(new MemoryEntry(time, summary));
                        }
                    }
                }
            }
        } catch (Exception e) {
            initDefaultMemory();
        }
    }

    /**
     * 保存记忆到本地 JSON 文件
     */
    private synchronized void saveToFile() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("updated_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            JSONObject profile = new JSONObject();
            profile.put("nickname", nickname);
            JSONArray factsArr = new JSONArray();
            for (String f : userFacts) {
                factsArr.put(f);
            }
            profile.put("facts", factsArr);
            root.put("user_profile", profile);

            JSONArray memArr = new JSONArray();
            for (MemoryEntry mem : recentMemories) {
                JSONObject m = new JSONObject();
                m.put("time", mem.time);
                m.put("summary", mem.summary);
                memArr.put(m);
            }
            root.put("memories", memArr);

            File parent = MEMORY_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MEMORY_FILE), StandardCharsets.UTF_8))) {
                writer.write(root.toString(2));
            }
        } catch (Exception ignored) {
        }
    }

    private void initDefaultMemory() {
        nickname = "主人";
        userFacts.clear();
        userFacts.add("喜欢敲代码与折腾工具箱");
        userFacts.add("把MITI当成可爱的桌面猫咪");
        recentMemories.clear();
    }

    public File getMemoryFile() {
        return MEMORY_FILE;
    }
}
