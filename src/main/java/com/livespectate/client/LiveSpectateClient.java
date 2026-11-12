package com.livespectate.client;

import com.livespectate.LiveSpectate;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端 tick：按 F9 打开 / 关闭控制面板。 */
@Mod.EventBusSubscriber(modid = LiveSpectate.MOD_ID, value = Dist.CLIENT)
public final class LiveSpectateClient {

    private LiveSpectateClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
