package com.moba.battleserver;

import com.moba.battleserver.config.ServerConfig;
import com.moba.battleserver.network.NettyServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BattleServerApplication {
    private static NettyServer server;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down battle server...");
            if (server != null) {
                server.stop();
            }
            ServiceLocator.getInstance().getRoomManager().shutdown();
        }));

        try {
            ServerConfig config = loadConfig();
            ServiceLocator.initialize(config);
            log.info("ServiceLocator initialized");

            server = new NettyServer(config);
            server.start();
            log.info("MOBA Battle Server started successfully on port {}", config.getPort());
            server.blockUntilShutdown();
        } catch (Exception e) {
            log.error("Failed to start battle server", e);
            System.exit(1);
        }
    }

    private static ServerConfig loadConfig() {
        ServerConfig config = ServerConfig.defaultConfig();

        String host = System.getenv("SERVER_HOST");
        if (host != null && !host.isEmpty()) {
            config.setHost(host);
        }

        String port = System.getenv("SERVER_PORT");
        if (port != null && !port.isEmpty()) {
            config.setPort(Integer.parseInt(port));
        }

        return config;
    }
}
