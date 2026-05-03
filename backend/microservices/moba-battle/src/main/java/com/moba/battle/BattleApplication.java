package com.moba.battle;

import com.moba.battle.config.BattleBeanConfig;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.event.BattleEventProducer;
import com.moba.battle.event.MatchSuccessConsumer;
import com.moba.battle.network.NettyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Slf4j
public class BattleApplication {

    private static volatile boolean running = true;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        log.info("MOBA战斗服务器启动中...");

        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.register(BattleBeanConfig.class);
            ctx.refresh();
            ctx.registerShutdownHook();

            ServerConfig serverConfig = ctx.getBean(ServerConfig.class);
            NettyServer nettyServer = ctx.getBean(NettyServer.class);

            new Thread(() -> {
                try {
                    nettyServer.start();
                    log.info("Netty服务器启动成功");
                } catch (Exception e) {
                    log.error("Netty服务器启动失败", e);
                    shutdown();
                }
            }, "netty-server-starter").start();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("MOBA战斗服务器启动完成, 耗时{}ms", elapsed);
            log.info("  客户端WebSocket端口: {}", serverConfig.getPort());
            log.info("  战斗TCP端口: {}", serverConfig.getBattleServerPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("MOBA战斗服务器关闭中...");
                shutdown();

                try {
                    BattleEventProducer eventProducer = ctx.getBean(BattleEventProducer.class);
                    eventProducer.shutdown();
                } catch (Exception e) {
                    log.error("停止事件生产者异常", e);
                }

                try {
                    MatchSuccessConsumer matchConsumer = ctx.getBean(MatchSuccessConsumer.class);
                    matchConsumer.shutdown();
                } catch (Exception e) {
                    log.error("停止匹配消费者异常", e);
                }

                try {
                    nettyServer.stop();
                } catch (Exception e) {
                    log.error("停止Netty服务器异常", e);
                }

                ctx.close();
                log.info("MOBA战斗服务器已停止");
            }));

            nettyServer.blockUntilShutdown();

        } catch (Exception e) {
            log.error("MOBA战斗服务器启动失败", e);
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
