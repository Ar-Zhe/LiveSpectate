package com.livespectate.network;

import com.livespectate.LiveSpectate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 -> 服务端：请求一次状态快照（无内容）。 */
public record StatusRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatusRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LiveSpectate.MOD_ID, "status_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatusRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new StatusRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
