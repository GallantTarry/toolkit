package shaoxia.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 轻量级外部服务配置读取器。
 * 优先级：系统环境变量 &gt; JVM 启动参数 (-Dkey=value) &gt; 本地配置文件 ~/.toolkit/config.properties
 */
public final class AppConfig {

    private static final File CONFIG_FILE = new File(
            new File(System.getProperty("user.home"), ".toolkit"),
            "config.properties"
    );

    private AppConfig() {
    }

    /**
     * 读取指定配置项，未配置或仍为占位符时返回空字符串。
     */
    public static String get(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String envVal = System.getenv(key);
        if (isValidValue(envVal)) {
            return envVal.trim();
        }

        String propVal = System.getProperty(key);
        if (isValidValue(propVal)) {
            return propVal.trim();
        }

        Properties fileProps = loadConfigFile();
        String fileVal = fileProps.getProperty(key);
        if (isValidValue(fileVal)) {
            return fileVal.trim();
        }

        return "";
    }

    public static boolean isConfigured(String key) {
        return !get(key).isEmpty();
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }

    private static boolean isValidValue(String val) {
        if (val == null) {
            return false;
        }
        String trimmed = val.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("YOUR_");
    }

    private static Properties loadConfigFile() {
        Properties props = new Properties();
        if (!CONFIG_FILE.isFile()) {
            return props;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception ignored) {
        }
        return props;
    }
}
