package com.livespectate;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * 一次"附体会话"中保存的执行者原始状态。
 *
 * <p>只在第一次附体时创建，之后自动轮换切换目标时复用同一份记录，
 * 所以 {@code /livespectate stop} 恢复的始终是最初进入附体前的状态。
 */
public final class SpectateSession {

    private final UUID adminId;
    private final ResourceKey<Level> originalDimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yRot;
    private final float xRot;
    private final GameType originalGameType;
    /** 是否由本模组把执行者切成了旁观模式（原本就是旁观则不需要恢复）。 */
    private final boolean gameModeChanged;

    private UUID targetId;

    public SpectateSession(ServerPlayer admin, boolean gameModeChanged) {
        this.adminId = admin.getUUID();
        this.originalDimension = admin.level().dimension();
        this.x = admin.getX();
        this.y = admin.getY();
        this.z = admin.getZ();
        this.yRot = admin.getYRot();
        this.xRot = admin.getXRot();
        this.originalGameType = admin.gameMode.getGameModeForPlayer();
        this.gameModeChanged = gameModeChanged;
    }

    public UUID adminId() {
        return adminId;
    }

    public ResourceKey<Level> originalDimension() {
        return originalDimension;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yRot() {
        return yRot;
    }

    public float xRot() {
        return xRot;
    }

    public GameType originalGameType() {
        return originalGameType;
    }

    public boolean gameModeChanged() {
        return gameModeChanged;
    }

    public UUID targetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }
}
