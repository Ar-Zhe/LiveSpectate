package com.livespectate.network;

import com.livespectate.LiveSpectate;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 网络层注册入口。
 *
 * <p>刻意做成独立类：服务端精简版把整个 {@code network} 包剥离后，
 * 公共代码里没有任何地方再引用它，模组仍能正常加载。
 */
@Mod.EventBusSubscriber(modid = LiveSpectate.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NetworkSetup {

    private NetworkSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LiveSpectateNetwork::register);
    }
}
