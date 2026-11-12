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
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * F9 面板用的网络层：客户端请求状态，服务端回发快照。
 *
 * <p>所有操作本身仍然走原版命令（{@code /livespectate ...}），
 * 这里只负责"把状态显示出来"。
 *
 * <p>注意：这是**普通** SimpleChannel，没装模组的原版客户端不会参与握手，
 * 也不会被踢（它们只是从不发请求、从不收包）。
 */
public final class LiveSpectateNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new net.minecraft.resources.ResourceLocation(LiveSpectate.MOD_ID, "status"))
            // 必须接受任意版本（含"原版客户端"）：否则 Forge 会认为本通道是必需的，
            // 把所有没装 Forge/本模组的玩家踢掉（"This server has mods that require Forge..."）。
            // 原版客户端从不发这个包、也不会收到，所以放宽版本判断是安全的。
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(version -> true)
            .serverAcceptedVersions(version -> true)
            .simpleChannel();

    private LiveSpectateNetwork() {
    }

    /** 在模组构造阶段调用一次。 */
    public static void register() {
        CHANNEL.messageBuilder(StatusRequestPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StatusRequestPacket::encode)
                .decoder(StatusRequestPacket::decode)
                .consumerMainThread(StatusRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(SpectateStatusPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpectateStatusPacket::encode)
                .decoder(SpectateStatusPacket::decode)
                .consumerMainThread(SpectateStatusPacket::handle)
                .add();
    }

    /** 客户端 -> 服务端：请求状态。 */
    public static void requestStatus() {
        CHANNEL.sendToServer(new StatusRequestPacket());
    }

    /** 服务端 -> 某个玩家：回发状态快照。 */
    public static void sendStatusTo(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot(player));
    }

    /** 打包某个管理员当前的附体 / 轮换状态。 */
    public static SpectateStatusPacket snapshot(ServerPlayer admin) {
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
        return new SpectateStatusPacket(attached, target, auto != null, interval, next);
    }
}
