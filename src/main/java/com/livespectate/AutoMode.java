package com.livespectate;

import java.util.Locale;

/**
 * 自动轮换的目标选择模式。
 *
 * <p>配置项 {@code auto-mode} 只接受这两个字符串（大小写不敏感）。
 */
public enum AutoMode {

    /** 按在线玩家列表顺序循环切换。 */
    SEQUENTIAL,

    /** 随机选择，并尽量避免与当前目标相同。 */
    RANDOM;

    public static AutoMode parse(String raw) {
        if (raw == null) {
            return SEQUENTIAL;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return "RANDOM".equals(normalized) ? RANDOM : SEQUENTIAL;
    }
}
