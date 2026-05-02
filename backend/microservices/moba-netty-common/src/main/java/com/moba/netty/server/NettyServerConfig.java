package com.moba.netty.server;

import lombok.Data;

@Data
public class NettyServerConfig {

    private String host = "0.0.0.0";
    private int port = 9999;
    private int bossThreadCount = 1;
    private int workerThreadCount = Runtime.getRuntime().availableProcessors() * 2;
    private int maxConnections = 1024;
    private int idleTimeoutSeconds = 120;
    private int maxFrameLength = 65536;
    private String webSocketPath = "/ws";
    private int heartbeatIntervalSeconds = 30;

    public static NettyServerConfig of(String host, int port) {
        NettyServerConfig config = new NettyServerConfig();
        config.setHost(host);
        config.setPort(port);
        return config;
    }

    public static NettyServerConfig of(int port) {
        return of("0.0.0.0", port);
    }
}
