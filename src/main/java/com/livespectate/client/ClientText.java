package com.livespectate.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;

/** 面板上的小段文案：跟随客户端语言，中英二选一。 */
final class ClientText {

    private ClientText() {
    }

    static boolean chinese() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getLanguageManager() == null) {
            return false;
        }
        return minecraft.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("zh");
    }

    static String t(String chinese, String english) {
        return chinese() ? chinese : english;
    }
}
