package com.moba.battle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "battle")
public class ServerConfig {
    private String host = "0.0.0.0";
    private int port = 8888;
    private int bossThreadCount = 1;
    private int workerThreadCount = Runtime.getRuntime().availableProcessors() * 2;
    private int maxConnections = 10000;
    private int idleTimeoutSeconds = 120;
    private int maxFrameLength = 10240;
    private int battleServerPort = 9999;
    private int heartbeatIntervalSeconds = 30;
    private int matchTimeoutSeconds = 60;
    private int tickIntervalMs = 66;
    private int maxRooms = 100;
    private int reconnectTimeoutSeconds = 30;
    private int hashCheckIntervalFrames = 10;

    public static ServerConfig defaultConfig() {
        return new ServerConfig();
    }
}
