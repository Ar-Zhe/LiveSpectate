package com.livespectate.util;

import com.livespectate.AutoMode;
import com.livespectate.AutoSpectateState;
import com.livespectate.Config;
import com.livespectate.SpectateManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * 自动轮换的目标选择规则。
 *
 * <p>每次选择都会重新调用 {@link MinecraftServer#getPlayerList()} 获取最新的在线玩家列表，
 * 不做任何缓存。
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    /**
     * 计算当前所有合法的候选目标。
     *
     * <p>过滤规则：
     * <ul>
     *     <li>排除离线玩家（列表本身就是在线玩家）</li>
     *     <li>排除已死亡玩家</li>
     *     <li>默认排除执行者自己（auto-include-self = false）</li>
     *     <li>auto-exclude-spectators 时排除旁观模式玩家</li>
     *     <li>auto-exclude-creative 时排除创造模式玩家</li>
     *     <li>始终排除 auto-exclude-names 中列出的玩家名</li>
     *     <li>auto-cross-dimension-follow = false 时只保留与执行者同维度的玩家</li>
     * </ul>
     */
    public static List<ServerPlayer> candidates(MinecraftServer server, ServerPlayer admin) {
        boolean includeSelf = Config.AUTO_INCLUDE_SELF.get();
        boolean excludeSpectators = Config.AUTO_EXCLUDE_SPECTATORS.get();
        boolean excludeCreative = Config.AUTO_EXCLUDE_CREATIVE.get();
        boolean crossDimension = Config.AUTO_CROSS_DIMENSION_FOLLOW.get();
        Set<String> excludedNames = excludedNames();
        ResourceKey<Level> anchor = SpectateManager.anchorDimension(admin);

        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer candidate : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (candidate == null) {
                continue;
            }
            if (!includeSelf && candidate.getUUID().equals(admin.getUUID())) {
                continue;
            }
            if (!candidate.isAlive()) {
                continue;
            }
            if (excludeSpectators && candidate.isSpectator()) {
                continue;
            }
            if (excludeCreative && candidate.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                continue;
            }
            if (excludedNames.contains(candidate.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!crossDimension && !candidate.level().dimension().equals(anchor)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    /**
     * 按当前配置的模式选出下一个目标。
     *
     * @return 下一个目标；没有任何合法目标时返回 {@code null}
     */
    public static ServerPlayer pick(MinecraftServer server, ServerPlayer admin, AutoSpectateState state) {
        List<ServerPlayer> list = candidates(server, admin);
        if (list.isEmpty()) {
            return null;
        }
        UUID current = state.currentTargetId();
        if (Config.mode() == AutoMode.RANDOM) {
            return pickRandom(server, list, current);
        }
        return pickSequential(list, current);
    }

    /** 顺序模式：当前目标在候选列表中的下一个（循环）。 */
    private static ServerPlayer pickSequential(List<ServerPlayer> list, UUID current) {
        int index = -1;
        if (current != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getUUID().equals(current)) {
                    index = i;
                    break;
                }
            }
        }
        return list.get((index + 1) % list.size());
    }

    /** 随机模式：尽量不与当前目标相同。 */
    private static ServerPlayer pickRandom(MinecraftServer server, List<ServerPlayer> list, UUID current) {
        if (list.size() == 1) {
            return list.get(0);
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            ServerPlayer candidate = list.get(server.overworld().random.nextInt(list.size()));
            if (current == null || !candidate.getUUID().equals(current)) {
                return candidate;
            }
        }
        // 极端情况下（随机一直撞车）退化为确定性选择
        for (ServerPlayer candidate : list) {
            if (current == null || !candidate.getUUID().equals(current)) {
                return candidate;
            }
        }
        return list.get(0);
    }

    /**
     * 预测下一个目标，仅用于 {@code auto status} 展示。
     *
     * @return 下一个目标；随机模式或没有目标时返回 {@code null}
     */
    public static ServerPlayer peekNext(MinecraftServer server, ServerPlayer admin, AutoSpectateState state) {
        if (Config.mode() == AutoMode.RANDOM) {
            return null;
        }
        List<ServerPlayer> list = candidates(server, admin);
        if (list.isEmpty()) {
            return null;
        }
        return pickSequential(list, state.currentTargetId());
    }

    private static Set<String> excludedNames() {
        Set<String> names = new HashSet<>();
        for (String raw : Config.AUTO_EXCLUDE_NAMES.get()) {
            if (raw != null && !raw.isBlank()) {
                names.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
