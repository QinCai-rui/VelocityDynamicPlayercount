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
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.yaml.snakeyaml.Yaml;

public final class PluginConfig {
    private static final String CONFIG_FILE_NAME = "config.yml";

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

            if (upgradeConfig(configFile, config)) {
                try (InputStream in2 = new FileInputStream(configFile)) {
                    Object raw2 = yaml.load(in2);
                    if (raw2 instanceof Map) {
                        config = (Map<String, Object>) raw2;
                    }
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

    @SuppressWarnings("unchecked")
    private boolean upgradeConfig(File configFile, Map<String, Object> userConfig) {
        String defaultText;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (in == null) return false;
            defaultText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }

        Map<String, Object> defaultMap;
        try {
            defaultMap = new Yaml().load(defaultText);
        } catch (Exception e) {
            return false;
        }
        if (defaultMap == null) return false;

        List<String> defaultLines = List.of(defaultText.split("\n", -1));
        StringBuilder toAppend = new StringBuilder();

        for (Map.Entry<String, Object> entry : defaultMap.entrySet()) {
            String key = entry.getKey();
            if (userConfig.containsKey(key)) {
                if (entry.getValue() instanceof Map<?, ?> defaultSub
                        && userConfig.get(key) instanceof Map<?, ?> userSub) {
                    for (Map.Entry<?, ?> subEntry : defaultSub.entrySet()) {
                        if (!userSub.containsKey(subEntry.getKey())) {
                            String block = extractIndentedBlock(defaultLines, key, (String) subEntry.getKey());
                            if (block != null) {
                                toAppend.append('\n').append(block);
                            }
                        }
                    }
                }
                continue;
            }
            String block = extractBlock(defaultLines, key);
            if (block != null) {
                toAppend.append('\n').append(block);
            }
        }

        if (toAppend.isEmpty()) return false;

        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8, true)) {
            writer.write(toAppend.toString());
            logger.info("Added new config options to config.yml");
            return true;
        } catch (IOException e) {
            logger.warn("Failed to upgrade config.yml: {}", e.getMessage());
            return false;
        }
    }

    private static String extractBlock(List<String> lines, String topKey) {
        int keyIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && line.contains(":")) {
                String lineKey = line.substring(0, line.indexOf(':')).trim();
                if (lineKey.equals(topKey)) {
                    keyIdx = i;
                    break;
                }
            }
        }
        if (keyIdx == -1) return null;

        int start = keyIdx;
        while (start > 0) {
            String prev = lines.get(start - 1);
            if (prev.isBlank() || prev.startsWith("#")) {
                start--;
            } else {
                break;
            }
        }

        int end = keyIdx + 1;
        while (end < lines.size()) {
            String cur = lines.get(end);
            if (cur.isEmpty() || cur.startsWith(" ") || cur.startsWith("\t") || cur.startsWith("#")) {
                end++;
            } else {
                break;
            }
        }

        return String.join("\n", lines.subList(start, end));
    }

    private static String extractIndentedBlock(List<String> lines, String parentKey, String subKey) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && line.contains(":")) {
                String lineKey = line.substring(0, line.indexOf(':')).trim();
                if (lineKey.equals(parentKey)) {
                    int j = i + 1;
                    while (j < lines.size() && (lines.get(j).startsWith(" ") || lines.get(j).startsWith("\t") || lines.get(j).isBlank() || lines.get(j).startsWith("#"))) {
                        String trimmed = lines.get(j).trim();
                        if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                            String foundKey = trimmed.substring(0, trimmed.indexOf(':')).trim();
                            if (foundKey.equals(subKey)) {
                                start = j;
                                break;
                            }
                        }
                        j++;
                    }
                    break;
                }
            }
        }
        if (start == -1) return null;

        int end = start + 1;
        while (end < lines.size()) {
            String cur = lines.get(end);
            if (cur.isBlank() || cur.startsWith("#") || cur.startsWith("  ") || cur.startsWith("\t")) {
                end++;
            } else {
                break;
            }
        }

        return String.join("\n", lines.subList(start, end));
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
