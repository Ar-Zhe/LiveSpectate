package com.livespectate.client;

import com.livespectate.network.SpectateStatusPayload;

/** 客户端缓存：最近一次从服务端收到的状态快照。 */
public final class ClientSpectateState {

    private static volatile SpectateStatusPayload latest;
    private static volatile long updatedAt;

    private ClientSpectateState() {
    }

    static void accept(SpectateStatusPayload payload) {
        latest = payload;
        updatedAt = System.currentTimeMillis();
    }

    /** 最近一次快照；从未收到过则为 {@code null}。 */
    public static SpectateStatusPayload latest() {
        return latest;
    }

    /** 距上次收到快照的毫秒数；从未收到过返回 {@link Long#MAX_VALUE}。 */
    public static long ageMillis() {
        SpectateStatusPayload payload = latest;
        if (payload == null) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - updatedAt;
    }
}
