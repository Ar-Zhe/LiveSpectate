package com.livespectate.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.livespectate.LiveSpectate;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化：注册 F9 按键，并把状态缓存的接收口接上。
 *
 * <p>整个类只在客户端加载（{@code value = Dist.CLIENT}），专用服务端不会碰它。
 */
@EventBusSubscriber(modid = LiveSpectate.MOD_ID, value = Dist.CLIENT)
public final class LiveSpectateKeys {

    /** 默认 F9，可在「选项 → 按键控制」里改。 */
    public static final KeyMapping OPEN_PANEL = new KeyMapping(
            "key.livespectate.open_panel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.livespectate");

    private LiveSpectateKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        StatusDispatcherBridge.install();
    }
}
