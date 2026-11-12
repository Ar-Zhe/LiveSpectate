package com.livespectate.event;

import com.livespectate.AutoRotationManager;
import com.livespectate.LiveSpectate;
import com.livespectate.SpectateManager;
import com.livespectate.command.LiveSpectateCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 游戏总线事件监听：
 * <ul>
 *     <li>注册命令</li>
 *     <li>服务器 tick —— 驱动自动轮换（服务器主线程）</li>
 *     <li>管理员退出游戏 —— 停止其自动轮换并清理状态</li>
 *     <li>服务器关闭 —— 停止全部自动轮换并恢复所有附体的管理员</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LiveSpectate.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpectateEvents {

    private SpectateEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiveSpectateCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 先给刚附体的账号补发摄像机包（修复"客户端还没收到目标实体就丢弃 set_camera"的问题），
        // 再同步朝向（越肩视角等解耦相机的模组只认玩家朝向），最后跑自动轮换逻辑
        SpectateManager.tickCameraReassert(event.getServer());
        SpectateManager.tickViewLock(event.getServer());
        AutoRotationManager.onServerTick(event.getServer());
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
