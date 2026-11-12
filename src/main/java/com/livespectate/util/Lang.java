package com.livespectate.util;

import com.livespectate.Config;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 内置双语消息表。
 *
 * <p>消息用 {@link Component#literal(String)} 直接下发，因此服务端单装本模组即可，
 * 玩家不需要安装任何客户端模组或资源包。
 */
public final class Lang {

    private static final Map<String, String> ZH_CN = new HashMap<>();
    private static final Map<String, String> EN_US = new HashMap<>();

    static {
        put("cmd.player_only", "只有玩家可以执行该命令。", "Only a player can run this command.");
        put("cmd.player_not_found", "找不到在线玩家 {player}。", "Online player {player} not found.");
        put("cmd.self", "不能附体自己。", "You cannot spectate yourself.");
        put("cmd.attach", "已附体到 {target}。", "Now spectating {target}.");
        put("cmd.attach_switch", "已切换附体目标到 {target}。", "Switched spectate target to {target}.");
        put("cmd.detach", "已退出附体并恢复原状态。", "Stopped spectating and restored your previous state.");
        put("cmd.not_attached", "你当前没有处于附体状态。", "You are not spectating anyone.");
        put("cmd.manual_stops_auto", "手动附体已停止自动轮换。", "Manual spectate stopped the auto rotation.");
        put("cmd.reload_ok", "配置已重载 (config/livespectate-common.toml)。", "Config reloaded (config/livespectate-common.toml).");
        put("cmd.reload_failed", "配置重载失败，详情见服务器日志。", "Config reload failed, see server log for details.");
        put("cmd.reload_console", "请在游戏内执行，或使用 /livespectate reload。", "Run this in game, or use /livespectate reload.");

        put("status.player.state_attached", "当前附体目标：{target}", "Current spectate target: {target}");
        put("status.player.idle", "当前未附体。", "Not spectating anyone.");
        put("status.auto.on", "自动轮换：开启 | 间隔 {interval} 秒 | 当前目标 {current} | 下一个目标 {next}",
                "Auto rotation: ON | interval {interval}s | current {current} | next {next}");
        put("status.auto.off", "自动轮换：关闭", "Auto rotation: OFF");
        put("status.console.header", "LiveSpectate 状态：{attached} 个附体会话，{auto} 个自动轮换",
                "LiveSpectate status: {attached} spectate session(s), {auto} auto rotation(s)");
        put("status.console.line", " - {admin} -> {target} (自动轮换: {auto})",
                " - {admin} -> {target} (auto: {auto})");

        put("auto.started", "自动轮换已开启：每 {interval} 秒切换一次，当前目标 {target}。",
                "Auto rotation started: switching every {interval}s, current target {target}.");
        put("auto.started_waiting", "自动轮换已开启：每 {interval} 秒切换一次，但目前没有可用目标，等待中。",
                "Auto rotation started: every {interval}s, but no valid target yet - waiting.");
        put("auto.stopped", "自动轮换已停止（保持当前附体状态）。", "Auto rotation stopped (still spectating).");
        put("auto.not_running", "自动轮换未开启。", "Auto rotation is not running.");
        put("auto.switched", "自动切换到 {target}。", "Auto-switched to {target}.");
        put("auto.no_targets", "没有可用的切换目标。", "No valid target available.");
        put("auto.stopped_no_targets", "没有可用目标，自动轮换已停止（保持当前附体）。",
                "No valid target, auto rotation stopped (still spectating).");
        put("auto.stopped_not_spectator", "你已不在旁观模式，自动轮换已停止。",
                "You are no longer in spectator mode, auto rotation stopped.");
        put("auto.next_failed", "没有可用的切换目标，无法切换。", "No valid target, cannot switch.");
        put("auto.next_done", "已切换到 {target}。", "Switched to {target}.");

        put("help.header", "===== LiveSpectate =====", "===== LiveSpectate =====");
        put("help.spectate", "/livespectate <玩家> - 附体到指定玩家", "/livespectate <player> - spectate a player");
        put("help.stop", "/livespectate stop - 停止轮换并退出附体，恢复原状态",
                "/livespectate stop - stop rotation, leave spectate and restore state");
        put("help.auto_start", "/livespectate auto start [秒] - 开始定时自动轮换",
                "/livespectate auto start [seconds] - start timed auto rotation");
        put("help.auto_stop", "/livespectate auto stop - 停止自动轮换（保持附体）",
                "/livespectate auto stop - stop auto rotation (keep spectating)");
        put("help.auto_status", "/livespectate auto status - 查看自动轮换状态",
                "/livespectate auto status - show auto rotation status");
        put("help.auto_next", "/livespectate auto next - 立即切换下一个目标",
                "/livespectate auto next - switch to next target now");
        put("help.status", "/livespectate status - 查看当前状态", "/livespectate status - show current status");
        put("help.reload", "/livespectate reload - 重载配置文件", "/livespectate reload - reload the config file");

        put("common.none", "无", "none");
    }

    private Lang() {
    }

    private static void put(String key, String zh, String en) {
        ZH_CN.put(key, zh);
        EN_US.put(key, en);
    }

    /** 取原始模板文本（已按配置选择语言）。 */
    public static String raw(String key) {
        Map<String, String> table = Config.isEnglish() ? EN_US : ZH_CN;
        return table.getOrDefault(key, key);
    }

    /**
     * 生成消息组件，{@code args} 为 {@code 名称, 值, 名称, 值 ...} 形式的占位符替换。
     */
    public static Component msg(String key, Object... args) {
        String text = raw(key);
        for (int i = 0; i + 1 < args.length; i += 2) {
            text = text.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return Component.literal(text);
    }

    public static void send(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(msg(key, args));
    }

    public static void send(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> msg(key, args), false);
    }

    public static void fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(msg(key, args));
    }
}
