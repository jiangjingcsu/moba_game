package com.moba.match.network;

import com.moba.match.network.handler.MatchWebSocketHandler;
import com.moba.netty.handler.GatewayAuthHandler;
import com.moba.netty.handler.HeartbeatHandler;
import com.moba.netty.server.AbstractNettyServer;
import com.moba.netty.server.NettyServerConfig;
import io.netty.channel.ChannelPipeline;

public class MatchNettyServer extends AbstractNettyServer {

    private final String webSocketPath;
    private final int idleTimeoutSeconds;

    public MatchNettyServer(int port, String webSocketPath, int idleTimeoutSeconds) {
        super(NettyServerConfig.of(port));
        this.webSocketPath = webSocketPath;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        config.setWebSocketPath(webSocketPath);
        config.setIdleTimeoutSeconds(idleTimeoutSeconds);
    }

    @Override
    protected void setupPipeline(ChannelPipeline pipeline) {
        setupWebSocketPipeline(pipeline);
        pipeline.addLast("gatewayAuth", new GatewayAuthHandler());
        addWebSocketProtocolHandler(pipeline);
        pipeline.addLast("webSocketHandler", new MatchWebSocketHandler());
        pipeline.addLast("heartbeatHandler", new HeartbeatHandler());
    }

    @Override
    protected String getServerName() {
        return "MatchNettyServer";
    }
}
