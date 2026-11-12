package com.livespectate.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * 面板上的小段文案：跟随客户端语言，中英二选一。
 *
 * <p>不走语言文件是为了让面板在只有服务端装了模组的情况下也能正常显示
 * （虽然面板本身需要客户端装模组，但玩家的语言包五花八门，直接判断更省事）。
 */
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
