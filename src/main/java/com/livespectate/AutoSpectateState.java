package com.livespectate;

import java.util.UUID;

/**
 * 单个管理员的自动轮换状态 —— 每个管理员一份，互不影响。
 *
 * <p>定时器完全基于服务器 tick 累加（见 {@link AutoRotationManager}），
 * 不使用 {@code java.util.Timer} 或任何异步线程。
 */
public final class AutoSpectateState {

    private final UUID adminId;
    private final int intervalSeconds;

    private long ticksSinceSwitch;
    private UUID currentTargetId;

    public AutoSpectateState(UUID adminId, int intervalSeconds) {
        this.adminId = adminId;
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    public UUID adminId() {
        return adminId;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    /** 间隔对应的 tick 数（20 tick = 1 秒）。 */
    public long intervalTicks() {
        return intervalSeconds * 20L;
    }

    public long ticksSinceSwitch() {
        return ticksSinceSwitch;
    }

    /** 每个服务器 tick 调用一次。 */
    public void tick() {
        ticksSinceSwitch++;
    }

    /** 完成一次切换（或一次尝试）后重置累加器。 */
    public void markAttempt() {
        ticksSinceSwitch = 0;
    }

    public UUID currentTargetId() {
        return currentTargetId;
    }

    public void setCurrentTargetId(UUID currentTargetId) {
        this.currentTargetId = currentTargetId;
    }
}
