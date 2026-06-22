package xyz.qincai.velocitydynamicplayercount;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import xyz.qincai.velocitydynamicplayercount.command.VelocityPlayercountCommand;
import xyz.qincai.velocitydynamicplayercount.config.PluginConfig;
import xyz.qincai.velocitydynamicplayercount.listener.PingListener;
import xyz.qincai.velocitydynamicplayercount.updatechecker.UpdateChecker;

import java.nio.file.Path;

@Plugin(
        id = "velocitydynamicplayercount",
        name = "VelocityDynamicPlayercount",
        version = "1.0.0",
        description = "Allows Velocity to show a dynamic playercount on server list ping",
        authors = {"QinCai-rui"},
        url = "https://github.com/QinCai-rui/VelocityDynamicPlayercount"
)
public final class VelocityDynamicPlayercount {

    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final UpdateChecker updateChecker;
    private final PingListener pingListener;

    @Inject
    public VelocityDynamicPlayercount(
            ProxyServer proxy,
            PluginContainer container,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.config = new PluginConfig(dataDirectory, logger);
        this.updateChecker = new UpdateChecker(container, config, proxy.getScheduler(), logger, dataDirectory);
        this.pingListener = new PingListener(config);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("========================================");
        logger.info("VelocityDynamicPlayercount v{}",
                containerDescription().getVersion().orElse("unknown"));
        logger.info("========================================");

        if (!config.load()) {
            logger.warn("Failed to load config, using defaults");
        }

        registerCommand();
        proxy.getEventManager().register(this, pingListener);

        updateChecker.start();

        logger.info("VelocityDynamicPlayercount enabled successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        updateChecker.stop();
        logger.info("VelocityDynamicPlayercount disabled.");
    }

    private void registerCommand() {
        proxy.getCommandManager().register(
                "velocitydynamicplayercount",
                new VelocityPlayercountCommand(proxy, config, updateChecker),
                "vdpc", "vdp"
        );
    }

    private PluginContainer containerDescription() {
        return proxy.getPluginManager().fromInstance(this).orElseThrow();
    }
}
