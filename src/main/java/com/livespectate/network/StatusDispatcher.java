package com.livespectate.network;

import java.util.function.Consumer;

/**
 * 状态快照的"分发口"。
 *
 * <p>网络处理器必须写在公共代码里（两端都会注册），但真正消费状态的
 * {@link Consumer} 只应该由客户端设置 —— 这样专用服务端永远不会去加载客户端类。
 */
public final class StatusDispatcher {

    private static volatile Consumer<SpectateStatusPayload> sink = payload -> {
    };

    private StatusDispatcher() {
    }

    /** 客户端初始化时调用，把面板的状态缓存接进来。 */
    public static void setSink(Consumer<SpectateStatusPayload> consumer) {
        sink = consumer;
    }

    public static void accept(SpectateStatusPayload payload) {
        sink.accept(payload);
    }
}
