package com.moba.battleserver.config;

import lombok.Data;

@Data
public class ServerConfig {
    private String host;
    private int port;
    private int bossThreadCount;
    private int workerThreadCount;
    private int maxConnections;
    private int idleTimeoutSeconds;
    private int maxFrameLength;
    private int battleServerPort;
    private int heartbeatIntervalSeconds;
    private int matchTimeoutSeconds;

    public static ServerConfig defaultConfig() {
        ServerConfig config = new ServerConfig();
        config.host = "0.0.0.0";
        config.port = 8888;
        config.bossThreadCount = 1;
        config.workerThreadCount = Runtime.getRuntime().availableProcessors() * 2;
        config.maxConnections = 10000;
        config.idleTimeoutSeconds = 120;
        config.maxFrameLength = 10240;
        config.battleServerPort = 9999;
        config.heartbeatIntervalSeconds = 30;
        config.matchTimeoutSeconds = 60;
        return config;
    }
}
