package com.livespectate.client;

import com.livespectate.network.StatusDispatcher;

/** 把 {@link ClientSpectateState} 接到公共分发口上（单独一层，避免公共代码引用客户端类）。 */
final class StatusDispatcherBridge {

    private StatusDispatcherBridge() {
    }

    static void install() {
        StatusDispatcher.setSink(ClientSpectateState::accept);
    }
}
