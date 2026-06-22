package xyz.qincai.velocitydynamicplayercount.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import xyz.qincai.velocitydynamicplayercount.config.PluginConfig;

import java.net.InetSocketAddress;
import java.util.Optional;

public final class PingListener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final PluginConfig config;
    private final Logger logger;

    public PingListener(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        String mode = config.mode();
        if ("disabled".equalsIgnoreCase(mode)) {
            return;
        }

        if (config.pingLogging()) {
            InboundConnection conn = event.getConnection();
            InetSocketAddress remote = conn.getRemoteAddress();
            String ip = remote != null ? remote.getAddress().getHostAddress() : "unknown";
            int port = remote != null ? remote.getPort() : 0;
            String host = conn.getVirtualHost()
                    .map(InetSocketAddress::toString)
                    .orElse("unknown");
            ProtocolVersion proto = conn.getProtocolVersion();
            logger.info("Ping from {}:{} (host: {}) protocol: {} ({})",
                    ip, port, host, proto, proto.getProtocol());
        }

        ServerPing ping = event.getPing();
        ServerPing.Builder builder = ServerPing.builder();

        ServerPing.Version existingVersion = ping.getVersion();
        Optional<ServerPing.Players> existingPlayers = ping.getPlayers();
        Component existingDescription = ping.getDescriptionComponent();
        Optional<Favicon> existingFavicon = ping.getFavicon();

        int online = existingPlayers.map(p -> p.getOnline()).orElse(0);
        int max;
        if ("fixed".equalsIgnoreCase(mode)) {
            max = config.fixedMaxPlayers();
        } else {
            max = online + 1;
        }

        builder.onlinePlayers(online);
        builder.maximumPlayers(max);

        existingPlayers.ifPresent(players ->
                builder.samplePlayers(players.getSample()));

        builder.version(existingVersion);
        Favicon configFavicon = config.favicon();
        if (configFavicon != null) {
            builder.favicon(configFavicon);
        } else {
            existingFavicon.ifPresent(builder::favicon);
        }

        String motdTemplate = config.motd();
        if (!motdTemplate.isBlank()) {
            String formatted = motdTemplate
                    .replace("%current%", String.valueOf(online))
                    .replace("%max%", String.valueOf(max));
            Component motd = legacyToComponent(formatted);
            builder.description(motd);
        } else if (existingDescription != null) {
            builder.description(existingDescription);
        }

        String versionOverride = config.versionOverride();
        if (!versionOverride.isBlank()) {
            String formatted = versionOverride
                    .replace("%current%", String.valueOf(online))
                    .replace("%max%", String.valueOf(max))
                    .replace("%version%", existingVersion.getName());
            builder.version(new ServerPing.Version(
                    existingVersion.getProtocol(),
                    formatted
            ));
        }

        event.setPing(builder.build());
    }

    private static Component legacyToComponent(String legacy) {
        String mini = legacy
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§k", "<obf>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<u>")
                .replace("§o", "<i>")
                .replace("§r", "<reset>")
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obf>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<u>")
                .replace("&o", "<i>")
                .replace("&r", "<reset>");
        return MINI_MESSAGE.deserialize(mini);
    }
}
