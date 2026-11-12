package com.livespectate.event;

import com.livespectate.AutoRotationManager;
import com.livespectate.LiveSpectate;
import com.livespectate.SpectateManager;
import com.livespectate.command.LiveSpectateCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 游戏总线事件监听：
 * <ul>
 *     <li>注册命令</li>
 *     <li>服务器 tick —— 驱动自动轮换（服务器主线程）</li>
 *     <li>管理员退出游戏 —— 停止其自动轮换并清理状态</li>
 *     <li>服务器关闭 —— 停止全部自动轮换并恢复所有附体的管理员</li>
 * </ul>
 */
@EventBusSubscriber(modid = LiveSpectate.MOD_ID)
public final class SpectateEvents {

    private SpectateEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiveSpectateCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 先给刚附体的账号补发摄像机包（修复"客户端还没收到目标实体就丢弃 set_camera"的问题），
        // 再同步朝向（越肩视角等解耦相机的模组只认玩家朝向），最后跑自动轮换逻辑
        SpectateManager.tickCameraReassert(event.getServer());
        SpectateManager.tickViewLock(event.getServer());
        AutoRotationManager.onServerTick(event);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpectateManager.onAdminLogout(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SpectateManager.onServerStopping(event.getServer());
    }
}
