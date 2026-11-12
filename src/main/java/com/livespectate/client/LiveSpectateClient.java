package com.livespectate.client;

import com.livespectate.LiveSpectate;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 客户端 tick：按 F9 打开 / 关闭控制面板。 */
@EventBusSubscriber(modid = LiveSpectate.MOD_ID, value = Dist.CLIENT)
public final class LiveSpectateClient {

    private LiveSpectateClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        while (LiveSpectateKeys.OPEN_PANEL.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new LiveSpectateScreen());
            } else if (minecraft.screen instanceof LiveSpectateScreen) {
                minecraft.screen.onClose();
            }
        }
    }
}
