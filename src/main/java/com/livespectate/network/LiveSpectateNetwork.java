package com.livespectate.network;

import com.livespectate.AutoMode;
import com.livespectate.AutoSpectateState;
import com.livespectate.Config;
import com.livespectate.LiveSpectate;
import com.livespectate.SpectateManager;
import com.livespectate.SpectateSession;
import com.livespectate.util.TargetSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * F9 面板用的网络层：客户端请求状态，服务端回发快照。
 *
 * <p>所有操作本身仍然走原版命令（{@code /livespectate ...}），
 * 这里只负责"把状态显示出来"，所以权限校验、目标筛选等逻辑完全复用命令那一套。
 */
@EventBusSubscriber(modid = LiveSpectate.MOD_ID)
public final class LiveSpectateNetwork {

    private LiveSpectateNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // .optional() 是必须的：不装本模组的（原版）客户端也必须能正常进服。
        // 注册成"必需通道"会让服务端在握手阶段踢掉所有没装模组的玩家，
        // 那就把"纯服务端模组"变成了"人人必装"。
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(StatusRequestPayload.TYPE, StatusRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PacketDistributor.sendToPlayer(player, snapshot(player));
                    }
                }));

        registrar.playToClient(SpectateStatusPayload.TYPE, SpectateStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> StatusDispatcher.accept(payload)));
    }

    /** 打包某个管理员当前的附体 / 轮换状态。 */
    public static SpectateStatusPayload snapshot(ServerPlayer admin) {
        MinecraftServer server = admin.server;
        SpectateSession session = SpectateManager.session(admin.getUUID());
        AutoSpectateState auto = SpectateManager.autoState(admin.getUUID());

        boolean attached = session != null && session.targetId() != null;
        String target = "";
        if (attached) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.targetId());
            target = player != null ? player.getGameProfile().getName() : "";
        }

        String next = "";
        if (auto != null) {
            ServerPlayer player = TargetSelector.peekNext(server, admin, auto);
            if (player != null) {
                next = player.getGameProfile().getName();
            } else if (Config.mode() == AutoMode.RANDOM) {
                next = "RANDOM";
            }
        }

        int interval = auto != null ? auto.intervalSeconds() : Config.defaultIntervalSeconds();
        return new SpectateStatusPayload(attached, target, auto != null, interval, next);
    }
}
