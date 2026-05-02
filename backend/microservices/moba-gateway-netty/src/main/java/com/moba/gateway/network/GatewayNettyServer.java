package com.moba.gateway.network;

import com.moba.gateway.config.GatewayConfig;
import com.moba.gateway.network.handler.GatewayAuthHandler;
import com.moba.gateway.network.handler.GatewayFrameHandler;
import com.moba.gateway.network.handler.GatewayHeartbeatHandler;
import com.moba.gateway.network.session.GatewaySessionManager;
import com.moba.gateway.route.MessageRouter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayNettyServer {

    private final GatewayConfig config;
    private final GatewaySessionManager sessionManager;
    private final MessageRouter messageRouter;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public GatewayNettyServer(GatewayConfig config, GatewaySessionManager sessionManager, MessageRouter messageRouter) {
        this.config = config;
        this.sessionManager = sessionManager;
        this.messageRouter = messageRouter;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(config.getBossThreadCount());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreadCount());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, config.getMaxConnections())
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        pipeline.addLast("http-aggregator", new HttpObjectAggregator(config.getHttpMaxContentLength()));
                        pipeline.addLast("auth", new GatewayAuthHandler(config.getJwtSecret(), sessionManager));
                        pipeline.addLast("ws-protocol", new WebSocketServerProtocolHandler(config.getWsPath()));
                        pipeline.addLast("ws-frame", new GatewayFrameHandler(sessionManager, messageRouter));
                        pipeline.addLast("idle", new IdleStateHandler(config.getIdleTimeoutSeconds(), 0, 0));
                        pipeline.addLast("heartbeat", new GatewayHeartbeatHandler(sessionManager));
                    }
                });

        serverChannel = bootstrap.bind(config.getHost(), config.getPort()).sync().channel();
        log.info("Netty网关已启动: {}:{}", config.getHost(), config.getPort());
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public void blockUntilShutdown() {
        if (serverChannel != null) {
            try {
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
