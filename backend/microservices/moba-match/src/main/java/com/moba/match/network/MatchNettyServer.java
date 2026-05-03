package com.moba.match.network;

import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import com.moba.netty.server.AbstractNettyServer;
import com.moba.netty.server.NettyServerConfig;
import com.moba.netty.session.SessionManager;
import io.netty.channel.ChannelPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchNettyServer extends AbstractNettyServer {

    private final MessageDispatcher messageDispatcher;
    private final SessionManager sessionManager;

    public MatchNettyServer(@Value("${match.websocket.port}") int port,
                            @Value("${match.websocket.path}") String webSocketPath,
                            @Value("${match.websocket.idleTimeoutSeconds}") int idleTimeoutSeconds,
                            @Value("${jwt.secret}") String jwtSecret,
                            @Value("${netty.business-thread-count:8}") int businessThreadCount,
                            MessageDispatcher messageDispatcher,
                            SessionManager sessionManager) {
        super(buildNettyConfig(port, webSocketPath, idleTimeoutSeconds, businessThreadCount, jwtSecret));
        this.messageDispatcher = messageDispatcher;
        this.sessionManager = sessionManager;
    }

    private static NettyServerConfig buildNettyConfig(int port, String webSocketPath,
                                                      int idleTimeoutSeconds, int businessThreadCount,
                                                      String jwtSecret) {
        NettyServerConfig nettyConfig = NettyServerConfig.of(port);
        nettyConfig.setWebSocketPath(webSocketPath);
        nettyConfig.setIdleTimeoutSeconds(idleTimeoutSeconds);
        nettyConfig.setBusinessThreadCount(businessThreadCount);
        nettyConfig.setJwtSecret(jwtSecret);
        return nettyConfig;
    }

    @Override
    protected void setupPipeline(ChannelPipeline pipeline) {
        setupStandardWebSocketPipeline(pipeline, messageDispatcher, sessionManager);
    }

    @Override
    protected String getServerName() {
        return "MatchNettyServer";
    }
}
