package com.livespectate.network;

import com.livespectate.LiveSpectate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端：某个管理员当前的附体 / 自动轮换状态快照。
 *
 * <p>只在 F9 面板打开期间由客户端主动请求后回发，用于面板上的状态显示。
 *
 * @param attached        是否已附体
 * @param target          当前附体目标名（未附体时为空串）
 * @param auto            自动轮换是否开启
 * @param intervalSeconds 轮换间隔秒数（未开启时为配置默认值）
 * @param next            下一个目标名；随机模式为 "RANDOM"，无目标时为空串
 */
public record SpectateStatusPayload(boolean attached, String target, boolean auto, int intervalSeconds, String next)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpectateStatusPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LiveSpectate.MOD_ID, "status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectateStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SpectateStatusPayload::attached,
                    ByteBufCodecs.STRING_UTF8, SpectateStatusPayload::target,
                    ByteBufCodecs.BOOL, SpectateStatusPayload::auto,
                    ByteBufCodecs.VAR_INT, SpectateStatusPayload::intervalSeconds,
                    ByteBufCodecs.STRING_UTF8, SpectateStatusPayload::next,
                    SpectateStatusPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
