package com.moba.match.config;

import com.moba.match.network.MatchNettyServer;
import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NettyServerRunner implements SmartLifecycle {

    private final MatchNettyServer nettyServer;
    private final MessageDispatcher messageDispatcher;
    private volatile boolean running = false;

    public NettyServerRunner(MatchNettyServer nettyServer, MessageDispatcher messageDispatcher) {
        this.nettyServer = nettyServer;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void start() {
        try {
            nettyServer.start();
            running = true;
            log.info("匹配WebSocket服务启动成功, 端口={}, 路径={}, 空闲超时={}秒",
                    nettyServer.getConfig().getPort(),
                    nettyServer.getConfig().getWebSocketPath(),
                    nettyServer.getConfig().getIdleTimeoutSeconds());
        } catch (Exception e) {
            log.error("匹配WebSocket服务启动失败", e);
        }
    }

    @Override
    public void stop() {
        messageDispatcher.shutdown();
        nettyServer.stop();
        running = false;
        log.info("匹配WebSocket服务已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
