package xyz.qincai.velocitydynamicplayercount.updatechecker;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import org.slf4j.Logger;
import xyz.qincai.velocitydynamicplayercount.config.PluginConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_DOWNLOAD_URL_PATTERN = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"");
    private static final String JAR_PREFIX = "VelocityDynamicPlayercount-";

    private static String updateJarName(String version) {
        return JAR_PREFIX + version + ".jar";
    }

    private final PluginContainer container;
    private final PluginConfig config;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Path pluginsDir;
    private final Path pluginJar;
    private final HttpClient httpClient;
    private final String pluginId;

    private volatile ScheduledTask scheduledTask;
    private volatile State state = State.none();

    public UpdateChecker(PluginContainer container, PluginConfig config, Scheduler scheduler, Logger logger, Path dataDirectory, Path pluginJar) {
        this.container = container;
        this.config = config;
        this.scheduler = scheduler;
        this.logger = logger;
        this.pluginsDir = pluginJar != null ? pluginJar.getParent() : dataDirectory.getParent();
        this.pluginJar = pluginJar;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        this.pluginId = container.getDescription().getId();
    }

    public void start() {
        stop();
        if (!config.updateCheckerEnabled()) {
            state = State.none();
            return;
        }

        runCheckAsync();

        long intervalMinutes = config.intervalMinutes();
        if (intervalMinutes > 0L) {
            scheduledTask = scheduler.buildTask(
                    container,
                    this::checkNow
            ).delay(intervalMinutes, java.util.concurrent.TimeUnit.MINUTES)
             .repeat(intervalMinutes, java.util.concurrent.TimeUnit.MINUTES)
             .schedule();
        }
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public void runCheckAsync() {
        if (!config.updateCheckerEnabled()) {
            return;
        }
        scheduler.buildTask(container, this::checkNow).schedule();
    }

    public void runCheckAsync(Consumer<UpdateResult> callback) {
        if (!config.updateCheckerEnabled()) {
            return;
        }
        scheduler.buildTask(container, () -> {
            UpdateResult result = checkNow();
            callback.accept(result);
        }).schedule();
    }

    public String statusSummary() {
        State snapshot = state;
        if (snapshot.errorMessage() != null) {
            return "error: " + snapshot.errorMessage();
        }
        if (snapshot.currentVersion().isBlank()) {
            return "pending";
        }
        if (snapshot.updateAvailable()) {
            return "update available " + snapshot.currentVersion() + " -> " + snapshot.latestVersion();
        }
        return "up-to-date " + snapshot.currentVersion();
    }

    private UpdateResult checkNow() {
        String apiUrl = config.apiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return UpdateResult.ERROR;
        }

        long timeoutMillis = config.timeoutMillis();
        boolean autoDownload = config.autoDownload();

        String currentVersion = normalizeVersion(
                container.getDescription().getVersion().orElse("unknown")
        );
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", pluginId + "-UpdateChecker")
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                state = new State(false, currentVersion, "", "", "HTTP " + response.statusCode());
                logger.warn("Update check failed with HTTP status {}", response.statusCode());
                return UpdateResult.ERROR;
            }

            String body = response.body();
            String latestVersion = normalizeVersion(findFirstGroup(TAG_NAME_PATTERN, body));
            String downloadUrl = findFirstGroup(HTML_URL_PATTERN, body);
            if (latestVersion.isBlank()) {
                state = new State(false, currentVersion, "", downloadUrl, "missing tag_name in response");
                logger.warn("Update check failed: missing tag_name in response body");
                return UpdateResult.ERROR;
            }

            boolean updateAvailable = !currentVersion.equalsIgnoreCase(latestVersion);
            state = new State(updateAvailable, currentVersion, latestVersion, downloadUrl, null);

            if (updateAvailable) {
                String updateJar = updateJarName(latestVersion);
                File targetFile = pluginsDir.resolve(updateJar).toFile();

                if (targetFile.exists()) {
                    cleanOldPluginJars(updateJar);
                    logger.info("{} update ({}) is already downloaded and pending restart.", pluginId, latestVersion);
                    return UpdateResult.UPDATE_DOWNLOADED;
                } else {
                    logger.warn("New {} version available: {} -> {}",
                            pluginId, currentVersion, latestVersion);

                    if (autoDownload) {
                        String assetUrl = findFirstGroup(ASSET_DOWNLOAD_URL_PATTERN, body);
                        if (!assetUrl.isBlank()) {
                            boolean success = downloadUpdate(assetUrl, latestVersion);
                            return success ? UpdateResult.UPDATE_DOWNLOADED : UpdateResult.DOWNLOAD_FAILED;
                        }
                        return UpdateResult.DOWNLOAD_FAILED;
                    }

                    return UpdateResult.UPDATE_AVAILABLE;
                }
            } else {
                logger.info("{} is up-to-date ({})", pluginId, currentVersion);
                return UpdateResult.UP_TO_DATE;
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            state = new State(false, currentVersion, "", "", ex.getMessage());
            logger.warn("Update check failed: {}", ex.getMessage());
            return UpdateResult.ERROR;
        } catch (IllegalArgumentException ex) {
            state = new State(false, currentVersion, "", "", ex.getMessage());
            logger.warn("Update check failed: invalid URL configured");
            return UpdateResult.ERROR;
        }
    }

    private static String findFirstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean downloadUpdate(String url, String version) {
        try {
            String updateJar = updateJarName(version);
            File targetFile = pluginsDir.resolve(updateJar).toFile();

            cleanOldPluginJars(updateJar);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", pluginId + "-Updater")
                    .timeout(Duration.ofMinutes(1))
                    .GET()
                    .build();

            logger.info("Downloading auto-update from {}...", url);

            HttpResponse<Path> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(targetFile.toPath())
            );

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            if (success) {
                logger.info("Update downloaded successfully to {}! It will be applied on the next server restart.", targetFile.getAbsolutePath());
            }

            return success;
        } catch (Exception ex) {
            logger.warn("Failed to auto-download update: {}", ex.getMessage());
            return false;
        }
    }

    private void cleanOldPluginJars(String excludeUpdateJar) {
        String currentJarName = pluginJar != null ? pluginJar.getFileName().toString() : null;
        File[] oldFiles = pluginsDir.toFile().listFiles((dir, name) ->
                name.startsWith(JAR_PREFIX) && name.endsWith(".jar")
                        && !name.equals(excludeUpdateJar)
                        && !name.equals(currentJarName));
        if (oldFiles == null) {
            return;
        }
        for (File oldFile : oldFiles) {
            if (oldFile.delete()) {
                logger.info("Deleted old plugin jar: {}", oldFile.getName());
            } else {
                logger.warn("Failed to delete old plugin jar: {}", oldFile.getName());
            }
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private record State(
            boolean updateAvailable,
            String currentVersion,
            String latestVersion,
            String downloadUrl,
            String errorMessage
    ) {
        private static State none() {
            return new State(false, "", "", "", null);
        }
    }

    public enum UpdateResult {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        UPDATE_DOWNLOADED,
        DOWNLOAD_FAILED,
        ERROR
    }
}
