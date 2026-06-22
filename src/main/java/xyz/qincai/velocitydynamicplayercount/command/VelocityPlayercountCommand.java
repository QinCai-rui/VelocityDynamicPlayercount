package xyz.qincai.velocitydynamicplayercount.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import xyz.qincai.velocitydynamicplayercount.config.PluginConfig;
import xyz.qincai.velocitydynamicplayercount.updatechecker.UpdateChecker;

import java.util.List;

public final class VelocityPlayercountCommand implements SimpleCommand {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ProxyServer proxy;
    private final PluginConfig config;
    private final UpdateChecker updateChecker;

    public VelocityPlayercountCommand(ProxyServer proxy, PluginConfig config, UpdateChecker updateChecker) {
        this.proxy = proxy;
        this.config = config;
        this.updateChecker = updateChecker;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(source);
            case "status" -> sendStatus(source);
            case "reload" -> reloadConfig(source);
            case "update" -> checkUpdate(source);
            default -> source.sendMessage(msg("<red>Unknown subcommand. Use <white>/vdpc help</white></red>"));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length == 0) {
            return List.of("help", "status", "reload", "update");
        }
        return List.of();
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(msg("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
        source.sendMessage(msg("<gray>├ <dark_aqua>VelocityDynamicPlayercount</dark_aqua> <dark_gray>-</dark_gray> <white>Help</white></gray>"));
        source.sendMessage(msg("<gray>├ <white>/vdpc help</white> <dark_gray>-</dark_gray> <gray>Show this help</gray></gray>"));
        source.sendMessage(msg("<gray>├ <white>/vdpc status</white> <dark_gray>-</dark_gray> <gray>Show plugin status</gray></gray>"));
        source.sendMessage(msg("<gray>├ <white>/vdpc reload</white> <dark_gray>-</dark_gray> <gray>Reload config</gray></gray>"));
        source.sendMessage(msg("<gray>├ <white>/vdpc update</white> <dark_gray>-</dark_gray> <gray>Check for updates</gray></gray>"));
        source.sendMessage(msg("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
    }

    private void sendStatus(CommandSource source) {
        int playerCount = proxy.getAllPlayers().size();
        source.sendMessage(msg("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
        source.sendMessage(msg("<dark_aqua>VelocityDynamicPlayercount</dark_aqua> <dark_gray>-</dark_gray> <white>Status</white>"));
        source.sendMessage(msg("<gray>Online Players:</gray> <white>" + playerCount + "</white>"));
        source.sendMessage(msg("<gray>Mode:</gray> <white>" + config.mode() + "</white>"));
        source.sendMessage(msg("<gray>Updates:</gray> <white>" + updateChecker.statusSummary() + "</white>"));
        source.sendMessage(msg("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
    }

    private void reloadConfig(CommandSource source) {
        config.reload();
        source.sendMessage(msg("<green>Config reloaded.</green>"));
    }

    private void checkUpdate(CommandSource source) {
        source.sendMessage(msg("<gray>Checking for updates...</gray>"));
        updateChecker.runCheckAsync(result -> {
            switch (result) {
                case UP_TO_DATE -> source.sendMessage(msg("<green>Plugin is up-to-date.</green>"));
                case UPDATE_AVAILABLE -> source.sendMessage(
                        msg("<gold>Update available! Run <white>/vdpc status</white> for details.</gold>")
                );
                case UPDATE_DOWNLOADED -> source.sendMessage(
                        msg("<green>Update downloaded! Restart the proxy to apply.</green>")
                );
                case DOWNLOAD_FAILED -> source.sendMessage(
                        msg("<red>Update found but download failed. Check console.</red>")
                );
                case ERROR -> source.sendMessage(
                        msg("<red>Update check failed. Check console.</red>")
                );
            }
        });
    }

    private static Component msg(String mini) {
        return MM.deserialize(mini);
    }
}
