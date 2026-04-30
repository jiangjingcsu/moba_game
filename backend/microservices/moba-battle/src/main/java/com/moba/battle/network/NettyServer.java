package com.moba.battle.network;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.network.codec.MessageDecoder;
import com.moba.battle.network.codec.MessageEncoder;
import com.moba.battle.network.handler.*;
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
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyServer {
    private final ServerConfig config;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel clientChannel;
    private Channel battleChannel;

    public NettyServer(ServerConfig config) {
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
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("httpCodec", new HttpServerCodec());
                            pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));
                            pipeline.addLast("jwtAuthHandler", new JwtAuthHandler());
                            pipeline.addLast("webSocketProtocol",
                                    new WebSocketServerProtocolHandler("/ws/battle", null, true, 65536));
                            pipeline.addLast("webSocketFrameHandler", new WebSocketFrameHandler());
                            pipeline.addLast("webSocketMessageEncoder", new WebSocketMessageEncoder());
                            pipeline.addLast("idleStateHandler",
                                    new IdleStateHandler(config.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS));
                            pipeline.addLast("heartbeatHandler", new HeartbeatHandler());
                            pipeline.addLast("clientRequestHandler", new ClientRequestHandler());
                        }
                    });

            ChannelFuture clientFuture = bootstrap.bind(new InetSocketAddress(config.getHost(), config.getPort())).sync();
            clientChannel = clientFuture.channel();
            log.info("Client server (WebSocket) started on {}:{}", config.getHost(), config.getPort());

            ServerBootstrap battleBootstrap = new ServerBootstrap();
            battleBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("messageDecoder", new MessageDecoder(config.getMaxFrameLength()));
                            pipeline.addLast("messageEncoder", new MessageEncoder());
                            pipeline.addLast("battleServerHandler", new BattleServerHandler());
                        }
                    });

            ChannelFuture battleFuture = battleBootstrap.bind(new InetSocketAddress(config.getHost(), config.getBattleServerPort())).sync();
            battleChannel = battleFuture.channel();
            log.info("Battle server started on {}:{}", config.getHost(), config.getBattleServerPort());

        } catch (Exception e) {
            log.error("Server start failed", e);
            stop();
            throw e;
        }
    }

    public void stop() {
        if (clientChannel != null) {
            clientChannel.close().syncUninterruptibly();
        }
        if (battleChannel != null) {
            battleChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Server stopped");
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (clientChannel != null) {
            clientChannel.closeFuture().sync();
        }
        if (battleChannel != null) {
            battleChannel.closeFuture().sync();
        }
    }
}
