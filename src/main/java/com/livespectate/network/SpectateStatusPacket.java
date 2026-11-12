package com.livespectate.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：某个管理员当前的附体 / 自动轮换状态快照。
 *
 * @param attached        是否已附体
 * @param target          当前附体目标名（未附体时为空串）
 * @param auto            自动轮换是否开启
 * @param intervalSeconds 轮换间隔秒数
 * @param next            下一个目标名；随机模式为 "RANDOM"，无目标时为空串
 */
public record SpectateStatusPacket(boolean attached, String target, boolean auto, int intervalSeconds, String next) {

    void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.attached);
        buf.writeUtf(this.target);
        buf.writeBoolean(this.auto);
        buf.writeVarInt(this.intervalSeconds);
        buf.writeUtf(this.next);
    }

    static SpectateStatusPacket decode(FriendlyByteBuf buf) {
        return new SpectateStatusPacket(
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readUtf());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> StatusDispatcher.accept(this));
        context.setPacketHandled(true);
    }
}
