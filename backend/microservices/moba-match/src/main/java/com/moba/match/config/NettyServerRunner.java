package com.moba.match.config;

import com.moba.match.network.MatchNettyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NettyServerRunner implements SmartLifecycle {

    @Value("${match.websocket.port}")
    private int websocketPort;

    @Value("${match.websocket.path}")
    private String webSocketPath;

    @Value("${match.websocket.idleTimeoutSeconds}")
    private int idleTimeoutSeconds;

    private MatchNettyServer nettyServer;
    private volatile boolean running = false;

    @Override
    public void start() {
        try {
            nettyServer = new MatchNettyServer(websocketPort, webSocketPath, idleTimeoutSeconds);
            nettyServer.start();
            running = true;
            log.info("匹配WebSocket服务启动成功, 端口={}, 路径={}, 空闲超时={}秒",
                    websocketPort, webSocketPath, idleTimeoutSeconds);
        } catch (Exception e) {
            log.error("匹配WebSocket服务启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (nettyServer != null) {
            nettyServer.stop();
        }
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
