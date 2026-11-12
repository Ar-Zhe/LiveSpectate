package com.livespectate.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** 客户端 -> 服务端：请求一次状态快照。 */
public record StatusRequestPacket() {

    void encode(FriendlyByteBuf buf) {
    }

    static StatusRequestPacket decode(FriendlyByteBuf buf) {
        return new StatusRequestPacket();
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                LiveSpectateNetwork.sendStatusTo(context.getSender());
            }
        });
        context.setPacketHandled(true);
    }
}
