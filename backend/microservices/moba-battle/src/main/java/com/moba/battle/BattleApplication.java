package com.moba.battle;

import com.moba.battle.config.BattleBeanConfig;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.event.BattleEventProducer;
import com.moba.battle.event.MatchSuccessConsumer;
import com.moba.battle.network.NettyServer;
import com.moba.battle.service.impl.BattleServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Slf4j
public class BattleApplication {

    private static volatile boolean running = true;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        log.info("MOBA Battle Server starting...");

        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.register(BattleBeanConfig.class);
            ctx.refresh();
            ctx.registerShutdownHook();

            ServerConfig serverConfig = ctx.getBean(ServerConfig.class);
            NettyServer nettyServer = ctx.getBean(NettyServer.class);

            BattleServiceImpl battleService = ctx.getBean(BattleServiceImpl.class);
            battleService.startDubboService(serverConfig);

            new Thread(() -> {
                try {
                    nettyServer.start();
                    log.info("Netty server started successfully");
                } catch (Exception e) {
                    log.error("Netty server start failed", e);
                    shutdown();
                }
            }, "netty-server-starter").start();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("MOBA Battle Server started in {}ms", elapsed);
            log.info("  Client WebSocket port: {}", serverConfig.getPort());
            log.info("  Battle TCP port: {}", serverConfig.getBattleServerPort());
            log.info("  Dubbo RPC port: {}", serverConfig.getDubboPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down MOBA Battle Server...");
                shutdown();

                try {
                    battleService.stopDubboService();
                } catch (Exception e) {
                    log.error("Error stopping Dubbo service", e);
                }

                try {
                    BattleEventProducer eventProducer = ctx.getBean(BattleEventProducer.class);
                    eventProducer.shutdown();
                } catch (Exception e) {
                    log.error("Error stopping event producer", e);
                }

                try {
                    MatchSuccessConsumer matchConsumer = ctx.getBean(MatchSuccessConsumer.class);
                    matchConsumer.shutdown();
                } catch (Exception e) {
                    log.error("Error stopping match consumer", e);
                }

                try {
                    nettyServer.stop();
                } catch (Exception e) {
                    log.error("Error stopping Netty server", e);
                }

                ctx.close();
                log.info("MOBA Battle Server stopped");
            }));

            nettyServer.blockUntilShutdown();

        } catch (Exception e) {
            log.error("Failed to start MOBA Battle Server", e);
            System.exit(1);
        }
    }

    private static void shutdown() {
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }
}
