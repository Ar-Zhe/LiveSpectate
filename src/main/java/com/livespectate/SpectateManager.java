package com.livespectate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * 附体会话与自动轮换状态的中心管理类。
 *
 * <p>所有方法都必须在服务器主线程上调用（命令回调、服务器 tick 事件、玩家事件
 * 都满足这一前提）。
 */
public final class SpectateManager {

    /**
     * 附体后持续补发摄像机包的时间（tick）。
     *
     * <p>为什么需要：原版 {@code ServerPlayer#setCamera} 会先调用
     * {@code chunkSource().move(this)} 再立刻发 {@link ClientboundSetCameraPacket}，
     * 而 {@code ChunkMap#move} 是先跑实体配对、后更新玩家的区块跟踪视图，
     * 所以当目标玩家原本不在相机账号的视野范围内时，目标实体的 spawn 包这一 tick 还发不出去。
     * 客户端收到 set_camera 后执行 {@code packet.getEntity(level)} 拿到 null，
     * 就会<b>静默丢弃</b>这个包 —— 表现就是"人瞬移过去了，但视界停在原地"。
     *
     * <p>这里在接下来的 3 秒内反复补发同一个摄像机包，等区块/实体跟踪追上之后
     * 客户端就能正确解析并附体，效果与原版旁观完全一致。
     */
    private static final int CAMERA_REASSERT_TICKS = 60;

    /**
     * 朝向同步的最小角度差（度）。
     *
     * <p>低于这个差值不发包：目标正常走路时朝向一直在微调，每个 tick 都发包既浪费
     * 又会让画面抖动；只有明显转头（超过 12°）时才纠正一次。
     */
    private static final float VIEW_LOCK_MIN_DELTA_DEGREES = 12.0F;

    /** 管理员 UUID -> 附体会话（保存原始状态）。 */
    private static final Map<UUID, SpectateSession> SESSIONS = new HashMap<>();

    /** 管理员 UUID -> 自动轮换状态。每个管理员一份，互不影响。 */
    private static final Map<UUID, AutoSpectateState> AUTO_STATES = new LinkedHashMap<>();

    /** 管理员 UUID -> 还需要补发摄像机包的剩余 tick 数。 */
    private static final Map<UUID, Integer> CAMERA_REASSERT = new HashMap<>();

    private SpectateManager() {
    }

    // ------------------------------------------------------------------
    // 状态访问
    // ------------------------------------------------------------------

    public static Map<UUID, AutoSpectateState> autoStates() {
        return AUTO_STATES;
    }

    public static SpectateSession session(UUID adminId) {
        return SESSIONS.get(adminId);
    }

    public static boolean isAttached(UUID adminId) {
        return SESSIONS.containsKey(adminId);
    }

    public static AutoSpectateState autoState(UUID adminId) {
        return AUTO_STATES.get(adminId);
    }

    public static boolean isAutoRunning(UUID adminId) {
        return AUTO_STATES.containsKey(adminId);
    }

    public static void putAutoState(AutoSpectateState state) {
        AUTO_STATES.put(state.adminId(), state);
    }

    /** 停止某个管理员的自动轮换（保持当前附体状态）。 */
    public static AutoSpectateState stopAuto(UUID adminId) {
        return AUTO_STATES.remove(adminId);
    }

    public static List<UUID> attachedAdmins() {
        return new ArrayList<>(SESSIONS.keySet());
    }

    // ------------------------------------------------------------------
    // 附体 / 解除附体
    // ------------------------------------------------------------------

    /**
     * 让 {@code admin} 附体到 {@code target}。
     *
     * <p>第一次调用时会保存原始状态并在必要时切换到旁观模式；
     * 之后（自动轮换）只会重新调用 {@link ServerPlayer#setCamera} 指向新目标，
     * 不会传送目标玩家、不会修改目标玩家的游戏模式、也不会给目标玩家发消息。
     *
     * @return 本次创建的（或复用的）会话
     */
    /**
     * 让 {@code admin} 附体到 {@code target}。
     *
     * <p>第一次调用时会保存原始状态并在必要时切换到旁观模式；
     * 之后（自动轮换）只会把摄像机指向新目标，不会传送目标玩家、
     * 不会修改目标玩家的游戏模式、也不会给目标玩家发消息。
     *
     * @return 本次创建的（或复用的）会话
     */
    public static SpectateSession attach(ServerPlayer admin, ServerPlayer target) {
        SpectateSession session = SESSIONS.get(admin.getUUID());
        if (session == null) {
            boolean mustSwitch = admin.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
            session = new SpectateSession(admin, mustSwitch);
            SESSIONS.put(admin.getUUID(), session);
            if (mustSwitch) {
                admin.setGameMode(GameType.SPECTATOR);
            }
        }
        session.setTargetId(target.getUUID());

        // 目标在别的维度时先换维度：原版 setCamera 内部走的是
        // teleportTo(6 参数) -> setCamera(this)，会把刚设好的摄像机重置掉，
        // 所以必须先把人搬过去，再在"同维度"的前提下附体。
        if (admin.level() != target.level()) {
            admin.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                    target.getYRot(), target.getXRot());
        }

        applyCamera(admin, target);

        // 相机账号自身的朝向也一起对齐到目标：原版第三人称不依赖它，
        // 但越肩视角（Shoulder Surfing）这类把相机角度"解耦"的模组只认玩家朝向，
        // 不同步的话切换目标后镜头会停在上一个目标的方向（表现为"拍脸"）。
        syncViewRotation(admin, target, true);

        // 客户端可能还没收到目标实体的 spawn 包，接下来几秒持续补发摄像机包
        CAMERA_REASSERT.put(admin.getUUID(), CAMERA_REASSERT_TICKS);
        return session;
    }

    /**
     * 周期性把相机账号的朝向对齐到目标（跟着目标转头）。
     *
     * <p>只在 {@code camera-lock-rotation = true} 时生效；用服务器 tick 计数做节流，
     * 所以没有额外状态需要清理。
     */
    public static void tickViewLock(MinecraftServer server) {
        if (SESSIONS.isEmpty() || !Config.CAMERA_LOCK_ROTATION.get()) {
            return;
        }
        int interval = Math.max(1, Config.CAMERA_LOCK_INTERVAL_TICKS.get());
        if (server.getTickCount() % interval != 0) {
            return;
        }
        for (UUID adminId : new ArrayList<>(SESSIONS.keySet())) {
            SpectateSession session = SESSIONS.get(adminId);
            ServerPlayer admin = server.getPlayerList().getPlayer(adminId);
            if (session == null || admin == null || session.targetId() == null) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetId());
            if (target == null || !target.isAlive()) {
                continue;
            }
            syncViewRotation(admin, target, false);
        }
    }

    /**
     * 把相机账号自身（不是目标）的朝向设成目标的朝向，并推给它的客户端。
     *
     * <p>为什么必须发包：玩家的朝向是客户端权威的，只改服务端字段客户端不会跟着转。
     * 这里用 {@code Connection#teleport}（坐标保持不动、只改 yaw/pitch）把朝向推下去，
     * 客户端只会转视角、不会位移。副作用只有一次位置确认包，对旁观状态的相机账号无害。
     *
     * @param force true = 不做角度差判断，切换目标时立即对齐
     */
    private static void syncViewRotation(ServerPlayer admin, ServerPlayer target, boolean force) {
        if (!Config.CAMERA_LOCK_ROTATION.get() || admin.connection == null) {
            return;
        }
        float yaw = target.getYRot();
        float pitch = Mth.clamp(target.getXRot(), -90.0F, 90.0F);
        if (!force && Math.abs(Mth.wrapDegrees(yaw - admin.getYRot())) < VIEW_LOCK_MIN_DELTA_DEGREES) {
            return;
        }
        admin.setYRot(yaw);
        admin.setXRot(pitch);
        admin.setYHeadRot(target.getYHeadRot());
        try {
            admin.connection.teleport(admin.getX(), admin.getY(), admin.getZ(), yaw, pitch);
        } catch (Exception e) {
            LiveSpectate.LOGGER.debug("[LiveSpectate] view rotation sync failed for {}",
                    admin.getGameProfile().getName(), e);
        }
    }

    /**
     * 把摄像机指向目标。
     *
     * <p>{@link ServerPlayer#setCamera} 在相机已经是该目标时不会再发包，
     * 所以这里在服务端相机已正确的情况下直接补发一次
     * {@link ClientboundSetCameraPacket}，用来修复客户端当时解析不到实体的丢包。
     */
    private static void applyCamera(ServerPlayer admin, ServerPlayer target) {
        if (admin.getCamera() != target) {
            admin.setCamera(target);
        } else {
            admin.connection.send(new ClientboundSetCameraPacket(target));
        }
    }

    /**
     * 每个服务器 tick 调用：给刚附体的账号持续补发摄像机包，直到客户端的
     * 区块/实体跟踪追上目标（见 {@link #CAMERA_REASSERT_TICKS} 的说明）。
     */
    public static void tickCameraReassert(MinecraftServer server) {
        if (CAMERA_REASSERT.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = CAMERA_REASSERT.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;

            SpectateSession session = SESSIONS.get(entry.getKey());
            ServerPlayer admin = server.getPlayerList().getPlayer(entry.getKey());
            if (session == null || admin == null || session.targetId() == null || !admin.isSpectator()) {
                iterator.remove();
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetId());
            if (target == null || !target.isAlive()) {
                iterator.remove();
                continue;
            }

            applyCamera(admin, target);

            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /**
     * 解除附体并恢复执行者原始状态（维度、坐标、朝向、以及被我们改过的游戏模式）。
     *
     * @return 是否本来就处于附体状态
     */
    public static boolean detach(ServerPlayer admin) {
        SpectateSession session = SESSIONS.remove(admin.getUUID());
        AUTO_STATES.remove(admin.getUUID());
        CAMERA_REASSERT.remove(admin.getUUID());

        if (session == null) {
            // 即使没有会话也把摄像机复位，避免残留附体
            if (admin.getCamera() != admin) {
                safeSetCameraSelf(admin);
            }
            return false;
        }

        safeSetCameraSelf(admin);

        ServerLevel level = admin.server.getLevel(session.originalDimension());
        if (level != null) {
            admin.teleportTo(level, session.x(), session.y(), session.z(), session.yRot(), session.xRot());
        }
        if (session.gameModeChanged()) {
            admin.setGameMode(session.originalGameType());
        }
        return true;
    }

    private static void safeSetCameraSelf(ServerPlayer admin) {
        try {
            admin.setCamera(admin);
        } catch (Exception e) {
            LiveSpectate.LOGGER.warn("[LiveSpectate] failed to reset camera for {}", admin.getGameProfile().getName(), e);
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 执行者退出游戏：停止其自动轮换、清理状态。
     * 按配置（restore-on-logout）尽量把游戏模式恢复回去，避免退服后卡在旁观。
     */
    public static void onAdminLogout(ServerPlayer admin) {
        AUTO_STATES.remove(admin.getUUID());
        CAMERA_REASSERT.remove(admin.getUUID());
        SpectateSession session = SESSIONS.remove(admin.getUUID());
        if (session == null) {
            return;
        }
        if (Config.RESTORE_ON_LOGOUT.get() && session.gameModeChanged()) {
            try {
                admin.setCamera(admin);
            } catch (Exception ignored) {
                // 连接正在关闭，忽略
            }
            admin.setGameMode(session.originalGameType());
        }
    }

    /** 服务器关闭：停止全部自动轮换并尽量恢复所有正在附体的管理员。 */
    public static void onServerStopping(MinecraftServer server) {
        AUTO_STATES.clear();
        CAMERA_REASSERT.clear();
        List<UUID> admins = new ArrayList<>(SESSIONS.keySet());
        for (UUID adminId : admins) {
            ServerPlayer admin = server.getPlayerList().getPlayer(adminId);
            if (admin != null) {
                try {
                    detach(admin);
                } catch (Exception e) {
                    LiveSpectate.LOGGER.warn("[LiveSpectate] failed to restore player {} on shutdown", adminId, e);
                }
            }
        }
        SESSIONS.clear();
    }

    /** 仅供命令使用：判断某个执行者的附体是否由本模组创建。 */
    public static boolean isManagedSpectator(ServerPlayer admin) {
        SpectateSession session = SESSIONS.get(admin.getUUID());
        return session != null && session.gameModeChanged();
    }

    /**
     * {@code auto-cross-dimension-follow = false} 时使用的"锚定维度"：
     * 执行者进入附体前所在的维度（没有会话时就是当前维度）。
     *
     * <p>相机账号通常停在主世界，这个值就是"只拍主世界"的基准。
     */
    public static ResourceKey<Level> anchorDimension(ServerPlayer admin) {
        SpectateSession session = SESSIONS.get(admin.getUUID());
        return session != null ? session.originalDimension() : admin.level().dimension();
    }
}
