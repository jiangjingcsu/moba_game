package com.moba.battle.network;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.network.codec.MessageDecoder;
import com.moba.battle.network.codec.MessageEncoder;
import com.moba.battle.network.handler.*;
import com.moba.netty.server.AbstractNettyServer;
import com.moba.netty.server.NettyServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyServer extends AbstractNettyServer {

    private final ServerConfig serverConfig;
    private Channel battleChannel;

    public NettyServer(ServerConfig config) {
        super(buildNettyConfig(config));
        this.serverConfig = config;
    }

    private static NettyServerConfig buildNettyConfig(ServerConfig config) {
        NettyServerConfig nettyConfig = new NettyServerConfig();
        nettyConfig.setHost(config.getHost());
        nettyConfig.setPort(config.getPort());
        nettyConfig.setBossThreadCount(config.getBossThreadCount());
        nettyConfig.setWorkerThreadCount(config.getWorkerThreadCount());
        nettyConfig.setMaxConnections(config.getMaxConnections());
        nettyConfig.setIdleTimeoutSeconds(config.getIdleTimeoutSeconds());
        nettyConfig.setMaxFrameLength(config.getMaxFrameLength());
        nettyConfig.setWebSocketPath(config.getWsPath());
        nettyConfig.setHeartbeatIntervalSeconds(config.getHeartbeatIntervalSeconds());
        return nettyConfig;
    }

    @Override
    protected void setupPipeline(ChannelPipeline pipeline) {
        setupWebSocketPipeline(pipeline);
        pipeline.addLast("gatewayTrustHandler", new GatewayTrustHandler());
        addWebSocketProtocolHandler(pipeline);
        pipeline.addLast("webSocketFrameHandler", new WebSocketFrameHandler());
        pipeline.addLast("webSocketMessageEncoder", new WebSocketMessageEncoder());
        pipeline.addLast("heartbeatHandler", new HeartbeatHandler());
        pipeline.addLast("clientRequestHandler", new ClientRequestHandler());
    }

    @Override
    protected void onServerStarted() {
        startBattleServer();
    }

    private void startBattleServer() {
        try {
            ServerBootstrap battleBootstrap = new ServerBootstrap();
            battleBootstrap.group(bossGroup, workerGroup)
                    .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, serverConfig.getSoBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("messageDecoder", new MessageDecoder(serverConfig.getMaxFrameLength()));
                            pipeline.addLast("messageEncoder", new MessageEncoder());
                            pipeline.addLast("battleServerHandler", new BattleServerHandler());
                        }
                    });

            ChannelFuture battleFuture = battleBootstrap.bind(
                    new InetSocketAddress(serverConfig.getHost(), serverConfig.getBattleServerPort())).sync();
            battleChannel = battleFuture.channel();
            log.info("战斗服务器已启动 {}:{}", serverConfig.getHost(), serverConfig.getBattleServerPort());
        } catch (Exception e) {
            log.error("战斗服务器启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (battleChannel != null) {
            battleChannel.close().syncUninterruptibly();
        }
        super.stop();
    }

    @Override
    public void blockUntilShutdown() throws InterruptedException {
        super.blockUntilShutdown();
        if (battleChannel != null) {
            battleChannel.closeFuture().sync();
        }
    }

    @Override
    protected String getServerName() {
        return "BattleNettyServer";
    }
}
