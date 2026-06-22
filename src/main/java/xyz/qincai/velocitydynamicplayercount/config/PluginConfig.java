package xyz.qincai.velocitydynamicplayercount.config;

import com.velocitypowered.api.util.Favicon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

public final class PluginConfig {
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final int CONFIG_VERSION = 2;

    private final Path dataDirectory;
    private final org.slf4j.Logger logger;

    private volatile String mode;
    private volatile int fixedMaxPlayers;
    private volatile String motd;
    private volatile String versionOverride;

    private volatile String faviconSource;
    private volatile Favicon favicon;
    private volatile boolean pingLogging;

    private volatile boolean updateCheckerEnabled;
    private volatile boolean autoDownload;
    private volatile long intervalMinutes;
    private volatile String apiUrl;
    private volatile long timeoutMillis;

    public PluginConfig(Path dataDirectory, org.slf4j.Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public boolean load() {
        File configFile = dataDirectory.resolve(CONFIG_FILE_NAME).toFile();
        if (!configFile.exists()) {
            saveDefaultConfig(configFile);
        }

        Yaml yaml = new Yaml();

        try (InputStream in = new FileInputStream(configFile)) {
            Object raw = yaml.load(in);
            if (!(raw instanceof Map)) {
                logger.warn("Config file is empty or invalid, using defaults");
                return false;
            }

            Map<String, Object> config = (Map<String, Object>) raw;

            int fileVersion = getInt(config, "config-version", 0);
            if (fileVersion < CONFIG_VERSION) {
                logger.info("Config version {} is outdated (latest {}), upgrading...", fileVersion, CONFIG_VERSION);
                upgradeConfig(configFile);
                try (InputStream in2 = new FileInputStream(configFile)) {
                    raw = yaml.load(in2);
                    if (!(raw instanceof Map)) return false;
                    config = (Map<String, Object>) raw;
                }
            }

            mode = getString(config, "mode", "current-plus-one");
            fixedMaxPlayers = getInt(config, "fixed-max-players", 100);
            motd = getString(config, "motd", "");
            versionOverride = getString(config, "version-override", "");
            faviconSource = getString(config, "favicon", "");
            favicon = loadFavicon();
            pingLogging = getBoolean(config, "ping-logging", false);

            Object ucRaw = config.getOrDefault("update-checker", Map.of());
            Map<String, Object> uc = ucRaw instanceof Map ? (Map<String, Object>) ucRaw : Map.of();
            updateCheckerEnabled = getBoolean(uc, "enabled", true);
            autoDownload = getBoolean(uc, "auto-download", true);
            intervalMinutes = getLong(uc, "interval-minutes", 60L);
            apiUrl = getString(uc, "api-url", "https://api.github.com/repos/QinCai-rui/VelocityDynamicPlayercount/releases/latest");
            timeoutMillis = getLong(uc, "timeout-millis", 10000L);

            return true;
        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage());
            return false;
        }
    }

    public void reload() {
        load();
    }

    private void saveDefaultConfig(File configFile) {
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
                logger.info("Created default config at {}", configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create default config: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void upgradeConfig(File configFile) throws IOException {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME);
        if (bundled == null) {
            logger.warn("Cannot upgrade config: bundled resource not found");
            return;
        }

        Yaml yaml = new Yaml();

        Map<String, Object> current;
        try (InputStream in = new FileInputStream(configFile)) {
            Object raw = yaml.load(in);
            current = raw instanceof Map ? (Map<String, Object>) raw : new HashMap<>();
        }
        Map<String, Object> defaults = yaml.load(bundled);

        if (defaults == null) return;

        mergeDefaults(current, defaults);
        current.put("config-version", CONFIG_VERSION);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setPrettyFlow(true);
        Yaml outYaml = new Yaml(dumperOptions);

        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            outYaml.dump(current, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeDefaults(Map<String, Object> current, Map<String, Object> defaults) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();
            if (!current.containsKey(key)) {
                current.put(key, defaultValue);
            } else if (defaultValue instanceof Map && current.get(key) instanceof Map) {
                mergeDefaults(
                        (Map<String, Object>) current.get(key),
                        (Map<String, Object>) defaultValue
                );
            }
        }
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val instanceof String s ? s : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    private static long getLong(Map<String, Object> map, String key, long def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        return def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean b ? b : def;
    }

    public String mode() { return mode; }
    public int fixedMaxPlayers() { return fixedMaxPlayers; }
    public String motd() { return motd; }
    public String versionOverride() { return versionOverride; }
    public Favicon favicon() { return favicon; }
    public boolean pingLogging() { return pingLogging; }

    private Favicon loadFavicon() {
        if (faviconSource.isBlank()) return null;
        try {
            BufferedImage image;
            if (faviconSource.startsWith("http://") || faviconSource.startsWith("https://")) {
                image = ImageIO.read(new URL(faviconSource));
            } else {
                Path resolved = dataDirectory.resolve(faviconSource);
                image = ImageIO.read(resolved.toFile());
            }

            if (image == null) {
                logger.warn("Failed to read favicon from {}", faviconSource);
                return null;
            }

            BufferedImage resized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, 64, 64, null);
            g.dispose();

            return Favicon.create(resized);
        } catch (Exception e) {
            logger.error("Failed to load favicon from {}: {}", faviconSource, e.getMessage());
            return null;
        }
    }

    public boolean updateCheckerEnabled() { return updateCheckerEnabled; }
    public boolean autoDownload() { return autoDownload; }
    public long intervalMinutes() { return intervalMinutes; }
    public String apiUrl() { return apiUrl; }
    public long timeoutMillis() { return timeoutMillis; }
}
