package com.livespectate;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * LiveSpectate —— Minecraft 1.20.1 / Forge 服务端旁观（附体）直播模组。
 *
 * <p>功能概览：
 * <ul>
 *     <li>{@code /livespectate <玩家>} 把执行者切到旁观模式并附体到目标玩家视角</li>
 *     <li>{@code /livespectate auto start [秒]} 定时自动切换附体目标</li>
 *     <li>{@code /livespectate auto stop|status|next} 自动轮换控制</li>
 *     <li>{@code /livespectate stop} 退出附体并恢复执行者原状态</li>
 *     <li>{@code /livespectate reload} 重载配置</li>
 * </ul>
 *
 * <p>所有需要权限的命令都要求 OP 等级 2。
 */
@Mod(LiveSpectate.MOD_ID)
public final class LiveSpectate {

    public static final String MOD_ID = "livespectate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LiveSpectate() {
        // 生成 config/livespectate-common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("[LiveSpectate] loaded; config -> {}", "config/livespectate-common.toml");
    }
}
