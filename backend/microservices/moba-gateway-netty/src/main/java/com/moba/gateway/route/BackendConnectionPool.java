package com.moba.gateway.route;

import com.moba.gateway.config.GatewayConfig;
import com.moba.gateway.discovery.NacosServiceDiscovery;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BackendConnectionPool {

    private final GatewayConfig config;
    private final NacosServiceDiscovery serviceDiscovery;
    private final EventLoopGroup backendGroup;
    private final Map<String, Channel> matchChannels = new ConcurrentHashMap<>();
    private final Map<String, Channel> battleChannels = new ConcurrentHashMap<>();

    public BackendConnectionPool(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.serviceDiscovery = serviceDiscovery;
        this.backendGroup = new NioEventLoopGroup(config.getBackendThreadCount());
    }

    public void init() {
        new Thread(() -> {
            try {
                refreshMatchServiceConnections();
            } catch (Exception e) {
                log.error("初始化匹配服务连接失败", e);
            }
        }, "match-connector").start();

        new Thread(() -> {
            try {
                refreshBattleServiceConnections();
            } catch (Exception e) {
                log.error("初始化战斗服务连接失败", e);
            }
        }, "battle-connector").start();
    }

    private void refreshMatchServiceConnections() {
        if (serviceDiscovery == null || !serviceDiscovery.isNacosAvailable()) {
            log.warn("Nacos服务发现不可用，无法发现匹配服务实例");
            return;
        }

        List<Instance> instances = serviceDiscovery.getInstances(config.getMatchServiceName());
        if (instances.isEmpty()) {
            log.warn("未从Nacos发现匹配服务实例: serviceName={}", config.getMatchServiceName());
            return;
        }

        for (Instance instance : instances) {
            String key = instance.getIp() + ":" + instance.getPort();
            if (!matchChannels.containsKey(key) || !matchChannels.get(key).isActive()) {
                try {
                    String wsPath = resolveWsPath(instance, config.getMatchServiceWsPath());
                    String url = "ws://" + instance.getIp() + ":" + instance.getPort() + wsPath;
                    Channel channel = connectBackend(url, "match-" + key);
                    matchChannels.put(key, channel);
                    log.info("通过Nacos连接到匹配服务实例: {} (rankTier={})", key, instance.getMetadata().get("rankTier"));
                } catch (Exception e) {
                    log.error("连接匹配服务实例{}失败", key, e);
                }
            }
        }

        matchChannels.entrySet().removeIf(entry -> {
            if (!entry.getValue().isActive()) {
                boolean stillExists = instances.stream()
                        .anyMatch(inst -> (inst.getIp() + ":" + inst.getPort()).equals(entry.getKey()));
                if (!stillExists) {
                    log.info("移除已断开的匹配服务连接: {}", entry.getKey());
                    return true;
                }
            }
            return false;
        });
    }

    private void refreshBattleServiceConnections() {
        if (serviceDiscovery == null || !serviceDiscovery.isNacosAvailable()) {
            log.warn("Nacos服务发现不可用，无法发现战斗服务实例");
            return;
        }

        List<Instance> instances = serviceDiscovery.getInstances(config.getBattleServiceName());
        if (instances.isEmpty()) {
            log.warn("未从Nacos发现战斗服务实例: serviceName={}", config.getBattleServiceName());
            return;
        }

        for (Instance instance : instances) {
            String key = instance.getIp() + ":" + instance.getPort();
            if (!battleChannels.containsKey(key) || !battleChannels.get(key).isActive()) {
                try {
                    String wsPath = resolveWsPath(instance, config.getBattleServiceWsPath());
                    String url = "ws://" + instance.getIp() + ":" + instance.getPort() + wsPath;
                    Channel channel = connectBackend(url, "battle-" + key);
                    battleChannels.put(key, channel);
                    log.info("通过Nacos连接到战斗服务实例: {}", key);
                } catch (Exception e) {
                    log.error("连接战斗服务实例{}失败", key, e);
                }
            }
        }

        battleChannels.entrySet().removeIf(entry -> {
            if (!entry.getValue().isActive()) {
                boolean stillExists = instances.stream()
                        .anyMatch(inst -> (inst.getIp() + ":" + inst.getPort()).equals(entry.getKey()));
                if (!stillExists) {
                    log.info("移除已断开的战斗服务连接: {}", entry.getKey());
                    return true;
                }
            }
            return false;
        });
    }

    private String resolveWsPath(Instance instance, String defaultPath) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata != null && metadata.containsKey("wsPath")) {
            return metadata.get("wsPath");
        }
        return defaultPath;
    }

    private Channel connectBackend(String url, String serviceName) throws InterruptedException {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, null);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(backendGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("http-codec", new HttpClientCodec());
                        pipeline.addLast("http-aggregator", new HttpObjectAggregator(config.getHttpMaxContentLength()));
                        pipeline.addLast("ws-handler", new BackendWebSocketHandler(handshaker, serviceName));
                    }
                });

        Channel channel = bootstrap.connect(host, port).sync().channel();
        handshaker.handshake(channel).sync();
        return channel;
    }

    public void sendToMatchService(String json) {
        for (Map.Entry<String, Channel> entry : matchChannels.entrySet()) {
            if (entry.getValue().isActive()) {
                entry.getValue().writeAndFlush(new TextWebSocketFrame(json));
                return;
            }
        }
        log.warn("匹配服务连接不可用，尝试刷新连接");
        refreshMatchServiceConnections();
        for (Map.Entry<String, Channel> entry : matchChannels.entrySet()) {
            if (entry.getValue().isActive()) {
                entry.getValue().writeAndFlush(new TextWebSocketFrame(json));
                return;
            }
        }
        log.error("匹配服务无可用连接");
    }

    public void sendToMatchService(String serviceKey, String json) {
        Channel channel = matchChannels.get(serviceKey);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(json));
            return;
        }
        log.warn("匹配服务实例{}连接不可用，尝试刷新连接", serviceKey);
        refreshMatchServiceConnections();
        channel = matchChannels.get(serviceKey);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(json));
            return;
        }
        sendToMatchService(json);
    }

    public void sendToBattleService(String json) {
        for (Map.Entry<String, Channel> entry : battleChannels.entrySet()) {
            if (entry.getValue().isActive()) {
                entry.getValue().writeAndFlush(new TextWebSocketFrame(json));
                return;
            }
        }
        log.warn("战斗服务连接不可用，尝试刷新连接");
        refreshBattleServiceConnections();
        for (Map.Entry<String, Channel> entry : battleChannels.entrySet()) {
            if (entry.getValue().isActive()) {
                entry.getValue().writeAndFlush(new TextWebSocketFrame(json));
                return;
            }
        }
        log.error("战斗服务无可用连接");
    }

    public void shutdown() {
        matchChannels.values().forEach(ch -> {
            if (ch != null) ch.close();
        });
        matchChannels.clear();
        battleChannels.values().forEach(ch -> {
            if (ch != null) ch.close();
        });
        battleChannels.clear();
        backendGroup.shutdownGracefully();
    }

    private MessageRouter messageRouter;

    public void setMessageRouter(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }

    private class BackendWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final String serviceName;

        BackendWebSocketHandler(WebSocketClientHandshaker handshaker, String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            String json = frame.text();
            log.debug("收到{}服务响应: {}B", serviceName, json.length());
            if (messageRouter != null) {
                messageRouter.handleBackendResponse(json, serviceName);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("{}服务连接异常", serviceName, cause);
        }
    }
}
