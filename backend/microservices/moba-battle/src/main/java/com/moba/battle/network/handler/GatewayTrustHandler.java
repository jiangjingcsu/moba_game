package com.moba.battle.network.handler;

import com.moba.netty.handler.GatewayAuthHandler;

public class GatewayTrustHandler extends GatewayAuthHandler {

    public GatewayTrustHandler() {
        super("moba-gateway-internal-secret", true,
                "moba-game-jwt-secret-key-2024-must-be-at-least-256-bits");
    }
}
