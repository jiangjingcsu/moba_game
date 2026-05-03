package com.moba.netty.server;

import com.moba.netty.handler.GatewayAuthHandler;
import com.moba.netty.handler.HeartbeatHandler;
import com.moba.netty.protocol.codec.WebSocketProtocolDecoder;
import com.moba.netty.protocol.codec.WebSocketProtocolEncoder;
import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import com.moba.netty.session.SessionManager;
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractNettyServer {

    protected final NettyServerConfig config;
    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;
    private final List<Channel> serverChannels = new ArrayList<>();

    protected AbstractNettyServer(NettyServerConfig config) {
        this.config = config;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(config.getBossThreadCount());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreadCount());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, config.getMaxConnections())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(createChannelInitializer());

            ChannelFuture future = bootstrap.bind(new InetSocketAddress(config.getHost(), config.getPort())).sync();
            serverChannels.add(future.channel());
            log.info("{}已启动, 地址={}:{}", getServerName(), config.getHost(), config.getPort());

            onServerStarted();

        } catch (Exception e) {
            log.error("{}启动失败", getServerName(), e);
            stop();
            throw e;
        }
    }

    protected ChannelInitializer<SocketChannel> createChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                setupPipeline(pipeline);
            }
        };
    }

    protected void setupStandardWebSocketPipeline(ChannelPipeline pipeline,
                                                  MessageDispatcher messageDispatcher,
                                                  SessionManager sessionManager) {
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(config.getMaxFrameLength()));
        pipeline.addLast("idleState", new IdleStateHandler(config.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS));

        String jwtSecret = config.getJwtSecret();
        if (jwtSecret != null && !jwtSecret.isEmpty()) {
            pipeline.addLast("gatewayAuth",
                    new GatewayAuthHandler("", jwtSecret, sessionManager));
        }

        pipeline.addLast("webSocketProtocol",
                new WebSocketServerProtocolHandler(config.getWebSocketPath(), null, true, config.getMaxFrameLength()));
        pipeline.addLast("wsProtocolDecoder", new WebSocketProtocolDecoder());
        pipeline.addLast("wsProtocolEncoder", new WebSocketProtocolEncoder());
        pipeline.addLast("messageDispatcher", messageDispatcher);
        pipeline.addLast("heartbeatHandler", new HeartbeatHandler());
    }

    @Deprecated
    protected void setupWebSocketPipeline(ChannelPipeline pipeline) {
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(config.getMaxFrameLength()));
        pipeline.addLast("idleState", new IdleStateHandler(config.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS));
    }

    @Deprecated
    protected void addWebSocketProtocolHandler(ChannelPipeline pipeline) {
        pipeline.addLast("webSocketProtocol",
                new WebSocketServerProtocolHandler(config.getWebSocketPath(), null, true, config.getMaxFrameLength()));
    }

    protected abstract void setupPipeline(ChannelPipeline pipeline);

    protected String getServerName() {
        return getClass().getSimpleName();
    }

    protected void onServerStarted() {
    }

    public void stop() {
        for (Channel channel : serverChannels) {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
        }
        serverChannels.clear();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("{}已停止", getServerName());
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (!serverChannels.isEmpty()) {
            serverChannels.get(0).closeFuture().sync();
        }
    }

    public NettyServerConfig getConfig() {
        return config;
    }
}
