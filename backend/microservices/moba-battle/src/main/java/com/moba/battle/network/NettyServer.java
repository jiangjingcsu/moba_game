package com.moba.battle.network;

import com.moba.battle.config.ServerConfig;
import com.moba.netty.protocol.codec.ProtocolDecoder;
import com.moba.netty.protocol.codec.ProtocolEncoder;
import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import com.moba.netty.server.AbstractNettyServer;
import com.moba.netty.server.NettyServerConfig;
import com.moba.netty.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

@Slf4j
public class NettyServer extends AbstractNettyServer {

    private final ServerConfig serverConfig;
    private final MessageDispatcher messageDispatcher;
    private final SessionManager sessionManager;
    private Channel battleChannel;

    public NettyServer(ServerConfig serverConfig, MessageDispatcher messageDispatcher, SessionManager sessionManager) {
        super(buildNettyConfig(serverConfig));
        this.serverConfig = serverConfig;
        this.messageDispatcher = messageDispatcher;
        this.sessionManager = sessionManager;
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
        nettyConfig.setBusinessThreadCount(config.getBusinessThreadCount());
        nettyConfig.setJwtSecret(config.getJwtSecret());
        return nettyConfig;
    }

    @Override
    protected void setupPipeline(ChannelPipeline pipeline) {
        setupStandardWebSocketPipeline(pipeline, messageDispatcher, sessionManager);
    }

    @Override
    protected void onServerStarted() {
        startBattleTcpServer();
    }

    private void startBattleTcpServer() {
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
                            pipeline.addLast("protocolDecoder", new ProtocolDecoder(serverConfig.getMaxFrameLength()));
                            pipeline.addLast("protocolEncoder", new ProtocolEncoder());
                            pipeline.addLast("messageDispatcher", messageDispatcher);
                        }
                    });

            ChannelFuture battleFuture = battleBootstrap.bind(
                    new InetSocketAddress(serverConfig.getHost(), serverConfig.getBattleServerPort())).sync();
            battleChannel = battleFuture.channel();
            log.info("战斗TCP服务器已启动 {}:{}", serverConfig.getHost(), serverConfig.getBattleServerPort());
        } catch (Exception e) {
            log.error("战斗TCP服务器启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (battleChannel != null) {
            battleChannel.close().syncUninterruptibly();
        }
        messageDispatcher.shutdown();
        super.stop();
    }

    @Override
    protected String getServerName() {
        return "BattleNettyServer";
    }
}
