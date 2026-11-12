package com.livespectate;

import com.livespectate.util.Lang;
import com.livespectate.util.TargetSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 自动轮换的定时器实现。
 *
 * <p>设计要点（对应需求里的"坑"）：
 * <ul>
 *     <li>使用 NeoForge 的 {@link ServerTickEvent.Post}，在服务器主线程上用 tick 累加判断间隔——
 *         不使用 {@code java.util.Timer}，也不开异步线程。</li>
 *     <li><b>只有到达切换时间（或当前目标失效）时</b>才调用
 *         {@link net.minecraft.server.level.ServerPlayer#setCamera}，绝不每 tick 调用。</li>
 *     <li>每个管理员一份 {@link AutoSpectateState}，互不影响。</li>
 *     <li>切换前重新获取在线玩家列表并校验目标有效性。</li>
 * </ul>
 */
public final class AutoRotationManager {

    /** 两次切换之间的最小间隔（tick），避免目标连续失效时每 tick 反复切换。 */
    private static final long MIN_SWITCH_COOLDOWN_TICKS = 20L;

    private AutoRotationManager() {
    }

    /** 每个服务器 tick 由事件监听器调用一次。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SpectateManager.autoStates().isEmpty()) {
            return;
        }

        List<UUID> finished = new ArrayList<>();
        for (AutoSpectateState state : new ArrayList<>(SpectateManager.autoStates().values())) {
            ServerPlayer admin = server.getPlayerList().getPlayer(state.adminId());
            if (admin == null) {
                // 执行者离线：清理状态
                finished.add(state.adminId());
                continue;
            }
            if (SpectateManager.isAttached(admin.getUUID()) && !admin.isSpectator()) {
                // 已经附体后又被改回其它游戏模式，自动附体不再安全，停止轮换。
                // 注意：等待模式（auto-stop-when-no-targets = false）下还没附体，
                // 此时执行者本来就不是旁观模式，不能据此停止。
                finished.add(state.adminId());
                Lang.send(admin, "auto.stopped_not_spectator");
                continue;
            }

            state.tick();

            ServerPlayer current = state.currentTargetId() == null
                    ? null
                    : server.getPlayerList().getPlayer(state.currentTargetId());

            // 目标在附体期间换了维度：原版摄像机不会自己跟过去，
            // 跟随开启时立刻把相机账号也搬过去并重新附体（否则画面会一直停在旧维度）。
            if (current != null && current.isAlive()
                    && admin.level() != current.level()
                    && Config.AUTO_CROSS_DIMENSION_FOLLOW.get()
                    && state.ticksSinceSwitch() >= MIN_SWITCH_COOLDOWN_TICKS) {
                SpectateManager.attach(admin, current);
                state.markAttempt();
                continue;
            }

            boolean currentInvalid = current == null || !isValidTarget(admin, current);

            if (!currentInvalid && state.ticksSinceSwitch() < state.intervalTicks()) {
                // 未到切换时间，什么都不做
                continue;
            }
            if (state.ticksSinceSwitch() < MIN_SWITCH_COOLDOWN_TICKS) {
                // 冷却中（例如上一 tick 刚切换过）
                continue;
            }

            state.markAttempt();
            ServerPlayer next = TargetSelector.pick(server, admin, state);
            if (next == null) {
                if (Config.AUTO_STOP_WHEN_NO_TARGETS.get()) {
                    finished.add(state.adminId());
                    Lang.send(admin, "auto.stopped_no_targets");
                }
                // 否则保持等待，下一个冷却周期再试
                continue;
            }

            SpectateManager.attach(admin, next);
            state.setCurrentTargetId(next.getUUID());
            Lang.send(admin, "auto.switched", "target", next.getGameProfile().getName());
        }

        for (UUID adminId : finished) {
            SpectateManager.stopAuto(adminId);
        }
    }

    /**
     * 当前目标是否仍然有效。
     *
     * <p>{@code auto-cross-dimension-follow = false} 时，目标跑到锚定维度（相机账号进入附体前
     * 所在的维度）之外也算失效，会立刻换回该维度里的其他玩家，而不是跟着穿维度。
     */
    private static boolean isValidTarget(ServerPlayer admin, ServerPlayer target) {
        if (!target.isAlive()) {
            return false;
        }
        if (!Config.AUTO_CROSS_DIMENSION_FOLLOW.get()
                && !target.level().dimension().equals(SpectateManager.anchorDimension(admin))) {
            return false;
        }
        return true;
    }
}
