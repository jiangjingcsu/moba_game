package com.moba.battle.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Data
@Component
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

    private String nacosServerAddr = "192.168.1.45:8848";
    private String nacosNamespace = "public";
    private String nacosGroup = "MOBA_GROUP";

    private String redisHost = "192.168.1.45";
    private int redisPort = 6379;
    private String redisPassword = "";
    private int redisDatabase = 2;

    private String rocketmqNameServer = "";
    private String rocketmqProducerGroup = "moba-battle-producer";

    private int dubboPort = 20883;

    public ServerConfig() {
        loadConfig();
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) return;

            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) return;

            Map<String, Object> battle = (Map<String, Object>) root.get("battle");
            if (battle != null) {
                loadBattleConfig(battle);
            }

            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            if (spring != null) {
                loadSpringConfig(spring);
            }

            Map<String, Object> dubbo = (Map<String, Object>) root.get("dubbo");
            if (dubbo != null) {
                loadDubboConfig(dubbo);
            }

            Map<String, Object> rocketmq = (Map<String, Object>) root.get("rocketmq");
            if (rocketmq != null) {
                loadRocketmqConfig(rocketmq);
            }

        } catch (Exception e) {
            System.err.println("Failed to load application.yml, using defaults: " + e.getMessage());
        }
    }

    private void loadBattleConfig(Map<String, Object> battle) {
        if (battle.containsKey("tick-interval-ms")) tickIntervalMs = ((Number) battle.get("tick-interval-ms")).intValue();
        if (battle.containsKey("max-rooms")) maxRooms = ((Number) battle.get("max-rooms")).intValue();
        if (battle.containsKey("reconnect-timeout-seconds")) reconnectTimeoutSeconds = ((Number) battle.get("reconnect-timeout-seconds")).intValue();
        if (battle.containsKey("hash-check-interval-frames")) hashCheckIntervalFrames = ((Number) battle.get("hash-check-interval-frames")).intValue();
        if (battle.containsKey("port")) port = ((Number) battle.get("port")).intValue();
        if (battle.containsKey("battle-server-port")) battleServerPort = ((Number) battle.get("battle-server-port")).intValue();
        if (battle.containsKey("host")) host = (String) battle.get("host");
        if (battle.containsKey("boss-thread-count")) bossThreadCount = ((Number) battle.get("boss-thread-count")).intValue();
        if (battle.containsKey("worker-thread-count")) workerThreadCount = ((Number) battle.get("worker-thread-count")).intValue();
        if (battle.containsKey("max-connections")) maxConnections = ((Number) battle.get("max-connections")).intValue();
        if (battle.containsKey("idle-timeout-seconds")) idleTimeoutSeconds = ((Number) battle.get("idle-timeout-seconds")).intValue();
        if (battle.containsKey("heartbeat-interval-seconds")) heartbeatIntervalSeconds = ((Number) battle.get("heartbeat-interval-seconds")).intValue();
    }

    @SuppressWarnings("unchecked")
    private void loadSpringConfig(Map<String, Object> spring) {
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        if (cloud != null) {
            Map<String, Object> nacos = (Map<String, Object>) cloud.get("nacos");
            if (nacos != null) {
                Map<String, Object> discovery = (Map<String, Object>) nacos.get("discovery");
                if (discovery != null && discovery.containsKey("server-addr")) {
                    nacosServerAddr = (String) discovery.get("server-addr");
                }
                if (discovery != null && discovery.containsKey("namespace")) {
                    nacosNamespace = (String) discovery.get("namespace");
                }
                if (discovery != null && discovery.containsKey("group")) {
                    nacosGroup = (String) discovery.get("group");
                }
            }
        }

        Map<String, Object> data = (Map<String, Object>) spring.get("data");
        if (data != null) {
            Map<String, Object> redis = (Map<String, Object>) data.get("redis");
            if (redis != null) {
                if (redis.containsKey("host")) redisHost = (String) redis.get("host");
                if (redis.containsKey("port")) redisPort = ((Number) redis.get("port")).intValue();
                if (redis.containsKey("password")) redisPassword = (String) redis.get("password");
                if (redis.containsKey("database")) redisDatabase = ((Number) redis.get("database")).intValue();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDubboConfig(Map<String, Object> dubbo) {
        Map<String, Object> protocol = (Map<String, Object>) dubbo.get("protocol");
        if (protocol != null && protocol.containsKey("port")) {
            dubboPort = ((Number) protocol.get("port")).intValue();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadRocketmqConfig(Map<String, Object> rocketmq) {
        if (rocketmq.containsKey("name-server")) {
            String ns = (String) rocketmq.get("name-server");
            if (ns != null && !ns.isEmpty() && !ns.startsWith("$")) {
                rocketmqNameServer = ns;
            }
        }
        Map<String, Object> producer = (Map<String, Object>) rocketmq.get("producer");
        if (producer != null && producer.containsKey("group")) {
            rocketmqProducerGroup = (String) producer.get("group");
        }
    }

    public static ServerConfig defaultConfig() {
        return new ServerConfig();
    }
}
