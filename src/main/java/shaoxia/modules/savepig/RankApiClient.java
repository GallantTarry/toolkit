package shaoxia.modules.savepig;

import org.json.JSONArray;
import org.json.JSONObject;
import shaoxia.Utils.AppConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * 全球排行榜客户端接口 (已由 Supabase 升级为 Cloudflare Workers + KV 驱动)
 * 保持原有类名与方法签名，无缝兼容 SavePigPanel
 */
public class RankApiClient {

    // 排行榜后端地址与校验密钥通过环境变量或 ~/.toolkit/config.properties 读取
    // 配置项：SAVEPIG_WORKER_URL、SAVEPIG_GAME_KEY

    // 数据结构：用于存储排行榜的一行记录
    public static class ScoreRecord {
        public String playerName;
        public long score;
        public String createdAt;

        public ScoreRecord(String playerName, long score, String createdAt) {
            this.playerName = playerName;
            this.score = score;
            this.createdAt = createdAt;
        }
    }

    /**
     * 上传分数到 Cloudflare Worker KV
     */
    public static void uploadScore(String playerName, long score) {
        String workerUrl = AppConfig.get("SAVEPIG_WORKER_URL");
        String gameSecretKey = AppConfig.get("SAVEPIG_GAME_KEY");
        if (workerUrl.isEmpty()) {
            System.out.println("未配置排行榜接口 SAVEPIG_WORKER_URL，已跳过云端战绩同步。");
            return;
        }
        Thread.ofVirtual().name("SavePig-RankUploader").start(() -> {
            try {
                URL url = URI.create(workerUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                if (!gameSecretKey.isEmpty()) {
                    conn.setRequestProperty("X-Game-Key", gameSecretKey);
                }
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("User-Agent", "ToolKit-Desktop/1.5");
                conn.setDoOutput(true);

                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JSONObject payload = new JSONObject();
                payload.put("player_name", playerName);
                payload.put("score", score);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    System.out.println("战绩已成功同步至 Cloudflare 云端！");
                } else {
                    System.out.println("上传受阻，状态码: " + code);
                }
            } catch (Exception e) {
                System.out.println("排行榜上传网络异常: " + e.getMessage());
            }
        });
    }

    /**
     * 从 Cloudflare Worker 拉取全球前 10 名排行榜数据
     */
    public static void fetchTopScores(Consumer<List<ScoreRecord>> callback) {
        String workerUrl = AppConfig.get("SAVEPIG_WORKER_URL");
        String gameSecretKey = AppConfig.get("SAVEPIG_GAME_KEY");
        if (workerUrl.isEmpty()) {
            javax.swing.SwingUtilities.invokeLater(() -> callback.accept(null));
            return;
        }
        Thread.ofVirtual().name("SavePig-RankFetcher").start(() -> {
            try {
                URL url = URI.create(workerUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (!gameSecretKey.isEmpty()) {
                    conn.setRequestProperty("X-Game-Key", gameSecretKey);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "ToolKit-Desktop/1.5");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                        String jsonStr = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "[]";
                        List<ScoreRecord> records = parseJson(jsonStr);
                        javax.swing.SwingUtilities.invokeLater(() -> callback.accept(records));
                    }
                } else {
                    System.out.println("拉取排行榜失败，状态码: " + code);
                    javax.swing.SwingUtilities.invokeLater(() -> callback.accept(null));
                }
            } catch (Exception e) {
                System.out.println("获取排行榜网络异常: " + e.getMessage());
                javax.swing.SwingUtilities.invokeLater(() -> callback.accept(null));
            }
        });
    }

    /**
     * 使用 org.json 稳健解析排行榜 JSON 数组
     */
    private static List<ScoreRecord> parseJson(String jsonStr) {
        List<ScoreRecord> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.optString("player_name", "无名骑士");
                long score = obj.optLong("score", 0);
                String date = obj.optString("created_at", "--");
                list.add(new ScoreRecord(name, score, date));
            }
        } catch (Exception e) {
            System.out.println("排行榜数据解析异常: " + e.getMessage());
        }
        return list;
    }
}