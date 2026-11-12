package com.livespectate.command;

import com.livespectate.AutoMode;
import com.livespectate.AutoSpectateState;
import com.livespectate.Config;
import com.livespectate.LiveSpectate;
import com.livespectate.SpectateManager;
import com.livespectate.SpectateSession;
import com.livespectate.util.Lang;
import com.livespectate.util.TargetSelector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;

/**
 * {@code /livespectate} 命令树（权限：OP 等级 2）。
 *
 * <pre>
 * /livespectate &lt;玩家&gt;           附体到指定玩家
 * /livespectate stop              停止自动轮换并退出附体，恢复原状态
 * /livespectate reload            重载 config/livespectate-common.toml
 * /livespectate status            查看当前状态
 * /livespectate auto start [秒]   开始定时自动轮换
 * /livespectate auto stop         停止自动轮换（保持当前附体）
 * /livespectate auto status       查看自动轮换状态
 * /livespectate auto next         立即切换到下一个目标
 * </pre>
 */
public final class LiveSpectateCommand {

    /** OP 等级 2。 */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private static final List<String> ROOT_KEYWORDS = List.of("stop", "reload", "status", "auto", "help");
    private static final List<String> INTERVAL_SUGGESTIONS = List.of("10", "15", "30", "60", "120", "300");

    private LiveSpectateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 主命令 + 短别名 /livespec
        dispatcher.register(buildRoot("livespectate"));
        dispatcher.register(buildRoot("livespec"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(auto())
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(LiveSpectateCommand::suggestRoot)
                        .executes(context -> spectate(context.getSource(), StringArgumentType.getString(context, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> auto() {
        return Commands.literal("auto")
                .executes(context -> autoStatus(context.getSource()))
                .then(Commands.literal("start")
                        .executes(context -> autoStart(context.getSource(), -1))
                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 86_400))
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(INTERVAL_SUGGESTIONS, builder))
                                .executes(context -> autoStart(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "interval")))))
                .then(Commands.literal("stop")
                        .executes(context -> autoStop(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> autoStatus(context.getSource())))
                .then(Commands.literal("next")
                        .executes(context -> autoNext(context.getSource())));
    }

    // ------------------------------------------------------------------
    // 手动附体 / 停止 / 状态 / 重载
    // ------------------------------------------------------------------

    private static int spectate(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            Lang.fail(source, "cmd.player_not_found", "player", targetName);
            return 0;
        }
        if (target.getUUID().equals(admin.getUUID())) {
            Lang.fail(source, "cmd.self");
            return 0;
        }

        // 默认：手动附体覆盖自动轮换，避免手动与自动互相打架
        if (Config.AUTO_MANUAL_OVERRIDES_AUTO.get() && SpectateManager.stopAuto(admin.getUUID()) != null) {
            Lang.send(admin, "cmd.manual_stops_auto");
        }

        boolean wasAttached = SpectateManager.isAttached(admin.getUUID());
        SpectateManager.attach(admin, target);
        Lang.send(admin, wasAttached ? "cmd.attach_switch" : "cmd.attach",
                "target", target.getGameProfile().getName());
        return 1;
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        boolean hadAuto = SpectateManager.stopAuto(admin.getUUID()) != null;
        boolean hadSession = SpectateManager.detach(admin);
        if (!hadAuto && !hadSession) {
            Lang.send(admin, "cmd.not_attached");
            return 0;
        }
        Lang.send(admin, "cmd.detach");
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        try {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
            Lang.send(source, "cmd.reload_ok");
            return 1;
        } catch (Exception e) {
            LiveSpectate.LOGGER.error("[LiveSpectate] failed to reload config", e);
            Lang.fail(source, "cmd.reload_failed");
            return 0;
        }
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        ServerPlayer admin = source.getPlayer();
        if (admin == null) {
            // 控制台：输出总览
            Lang.send(source, "status.console.header",
                    "attached", SpectateManager.attachedAdmins().size(),
                    "auto", SpectateManager.autoStates().size());
            for (UUID adminId : SpectateManager.attachedAdmins()) {
                SpectateSession session = SpectateManager.session(adminId);
                ServerPlayer player = server.getPlayerList().getPlayer(adminId);
                ServerPlayer target = session == null || session.targetId() == null
                        ? null
                        : server.getPlayerList().getPlayer(session.targetId());
                Lang.send(source, "status.console.line",
                        "admin", player == null ? adminId.toString() : player.getGameProfile().getName(),
                        "target", target == null ? Lang.raw("common.none") : target.getGameProfile().getName(),
                        "auto", SpectateManager.isAutoRunning(adminId) ? "ON" : "OFF");
            }
            return 1;
        }

        SpectateSession session = SpectateManager.session(admin.getUUID());
        if (session != null && session.targetId() != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetId());
            Lang.send(admin, "status.player.state_attached",
                    "target", target == null ? Lang.raw("common.none") : target.getGameProfile().getName());
        } else {
            Lang.send(admin, "status.player.idle");
        }
        return autoStatus(source);
    }

    private static int help(CommandSourceStack source) {
        for (String key : List.of("help.header", "help.spectate", "help.stop",
                "help.auto_start", "help.auto_stop", "help.auto_status", "help.auto_next",
                "help.status", "help.reload")) {
            Lang.send(source, key);
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // 自动轮换子命令
    // ------------------------------------------------------------------

    private static int autoStart(CommandSourceStack source, int requestedInterval) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        int interval = requestedInterval > 0 ? requestedInterval : Config.defaultIntervalSeconds();

        AutoSpectateState state = new AutoSpectateState(admin.getUUID(), interval);
        ServerPlayer next = TargetSelector.pick(server, admin, state);
        if (next == null) {
            if (Config.AUTO_STOP_WHEN_NO_TARGETS.get()) {
                Lang.fail(source, "auto.no_targets");
                return 0;
            }
            // 保持等待：先登记状态，不切换游戏模式
            SpectateManager.putAutoState(state);
            Lang.send(admin, "auto.started_waiting", "interval", interval);
            return 1;
        }

        SpectateManager.attach(admin, next);
        state.setCurrentTargetId(next.getUUID());
        state.markAttempt();
        SpectateManager.putAutoState(state);
        Lang.send(admin, "auto.started",
                "interval", interval, "target", next.getGameProfile().getName());
        return 1;
    }

    private static int autoStop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        if (SpectateManager.stopAuto(admin.getUUID()) == null) {
            Lang.send(admin, "auto.not_running");
            return 0;
        }
        Lang.send(admin, "auto.stopped");
        return 1;
    }

    private static int autoStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        AutoSpectateState state = SpectateManager.autoState(admin.getUUID());
        if (state == null) {
            Lang.send(admin, "auto.not_running");
            return 0;
        }
        ServerPlayer current = state.currentTargetId() == null
                ? null
                : server.getPlayerList().getPlayer(state.currentTargetId());
        ServerPlayer next = TargetSelector.peekNext(server, admin, state);
        String currentName = current == null ? Lang.raw("common.none") : current.getGameProfile().getName();
        String nextName;
        if (next != null) {
            nextName = next.getGameProfile().getName();
        } else {
            nextName = Config.mode() == AutoMode.RANDOM ? "RANDOM" : Lang.raw("common.none");
        }
        Lang.send(admin, "status.auto.on",
                "interval", state.intervalSeconds(), "current", currentName, "next", nextName);
        return 1;
    }

    private static int autoNext(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        AutoSpectateState state = SpectateManager.autoState(admin.getUUID());
        boolean temporary = false;
        if (state == null) {
            state = new AutoSpectateState(admin.getUUID(), Config.defaultIntervalSeconds());
            SpectateSession session = SpectateManager.session(admin.getUUID());
            if (session != null) {
                state.setCurrentTargetId(session.targetId());
            }
            temporary = true;
        }

        ServerPlayer next = TargetSelector.pick(server, admin, state);
        if (next == null) {
            Lang.fail(source, "auto.next_failed");
            return 0;
        }
        SpectateManager.attach(admin, next);
        if (!temporary) {
            state.setCurrentTargetId(next.getUUID());
            state.markAttempt();
        }
        Lang.send(admin, "auto.next_done", "target", next.getGameProfile().getName());
        return 1;
    }

    // ------------------------------------------------------------------
    // Tab 补全
    // ------------------------------------------------------------------

    /**
     * {@code /livespectate } 之后的补全：子命令关键字 + 在线玩家名。
     */
    private static CompletableFuture<Suggestions> suggestRoot(CommandContext<CommandSourceStack> context,
                                                              SuggestionsBuilder builder) {
        List<String> options = new ArrayList<>(ROOT_KEYWORDS);
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            options.add(player.getGameProfile().getName());
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }
}
