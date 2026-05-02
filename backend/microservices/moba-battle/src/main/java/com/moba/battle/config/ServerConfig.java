package com.moba.battle.config;

import com.moba.common.config.NacosConfigLoader;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
public class ServerConfig {

    private String host;
    private String registerIp;
    private int port;
    private int bossThreadCount;
    private int workerThreadCount;
    private int maxConnections;
    private int idleTimeoutSeconds;
    private int maxFrameLength;
    private int battleServerPort;
    private int heartbeatIntervalSeconds;
    private int tickIntervalMs;
    private int inputDelayFrames;
    private int maxRooms;
    private int reconnectTimeoutSeconds;
    private int hashCheckIntervalFrames;

    private float physicsWorldScale;
    private int physicsVelocityIterations;
    private int physicsPositionIterations;
    private float playerRestitution;
    private float playerFriction;
    private float creepRestitution;
    private float creepFriction;
    private int projectileLifetimeFrames;

    private String nacosServerAddr;
    private String nacosNamespace;
    private String nacosGroup;
    private String nacosUsername;
    private String nacosPassword;

    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private int redisDatabase;
    private int redisTimeout;
    private int redisPoolMaxTotal;
    private int redisPoolMaxIdle;
    private int redisPoolMinIdle;

    private String rocketmqNameServer;
    private String rocketmqProducerGroup;
    private int rocketmqSendMsgTimeout;
    private String rocketmqConsumerGroup;

    private int dubboPort;
    private int dubboRegistryTimeout;

    private int loadingTimeoutSeconds;
    private int roomCleanupDelaySeconds;
    private int soBacklog;
    private String wsPath;
    private String wsServiceName;
    private String dubboServiceName;

    private long creepSpawnIntervalMs;
    private long runeSpawnIntervalMs;
    private int replaySnapshotInterval;
    private int roomKeyTtlSeconds;
    private int loadReportIntervalSeconds;
    private int metricHistorySize;
    private int aiTickIntervalFrames;
    private int defaultAiLevel;
    private int minBotLevel;
    private int maxBotLevel;
    private int defaultBotCountPerTeam;
    private int aggroRange;
    private int defaultGridSize;

    private final Map<String, Object> mergedConfig;

    public ServerConfig() {
        this.mergedConfig = NacosConfigLoader.load("application.yml");
        loadFromMergedConfig();
    }

    private void loadFromMergedConfig() {
        loadBattleConfig();
        loadPhysicsConfig();
        loadRedisConfig();
        loadDubboConfig();
        loadRocketmqConfig();
        loadNacosConfig();
        loadGameplayConfig();
    }

    private void loadBattleConfig() {
        host = NacosConfigLoader.getRequiredString(mergedConfig, "battle.host");
        registerIp = NacosConfigLoader.getNestedString(mergedConfig, "battle.register-ip", null);
        port = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.port");
        bossThreadCount = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.boss-thread-count");
        workerThreadCount = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.worker-thread-count");
        maxConnections = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.max-connections");
        idleTimeoutSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.idle-timeout-seconds");
        maxFrameLength = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.max-frame-length");
        battleServerPort = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.battle-server-port");
        heartbeatIntervalSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.heartbeat-interval-seconds");
        tickIntervalMs = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.tick-interval-ms");
        inputDelayFrames = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.input-delay-frames");
        maxRooms = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.max-rooms");
        reconnectTimeoutSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.reconnect-timeout-seconds");
        hashCheckIntervalFrames = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.hash-check-interval-frames");
        loadingTimeoutSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.loading-timeout-seconds");
        roomCleanupDelaySeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.room-cleanup-delay-seconds");
        soBacklog = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.so-backlog");
        wsPath = NacosConfigLoader.getRequiredString(mergedConfig, "battle.ws-path");
        wsServiceName = NacosConfigLoader.getRequiredString(mergedConfig, "battle.ws-service-name");
        dubboServiceName = NacosConfigLoader.getRequiredString(mergedConfig, "battle.dubbo-service-name");
    }

    private void loadPhysicsConfig() {
        physicsWorldScale = NacosConfigLoader.getRequiredFloat(mergedConfig, "battle.physics.world-scale");
        physicsVelocityIterations = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.physics.velocity-iterations");
        physicsPositionIterations = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.physics.position-iterations");
        playerRestitution = NacosConfigLoader.getRequiredFloat(mergedConfig, "battle.physics.player-restitution");
        playerFriction = NacosConfigLoader.getRequiredFloat(mergedConfig, "battle.physics.player-friction");
        creepRestitution = NacosConfigLoader.getRequiredFloat(mergedConfig, "battle.physics.creep-restitution");
        creepFriction = NacosConfigLoader.getRequiredFloat(mergedConfig, "battle.physics.creep-friction");
        projectileLifetimeFrames = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.physics.projectile-lifetime-frames");
    }

    private void loadRedisConfig() {
        redisHost = NacosConfigLoader.getRequiredString(mergedConfig, "spring.data.redis.host");
        redisPort = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.port");
        redisPassword = NacosConfigLoader.getRequiredString(mergedConfig, "spring.data.redis.password");
        redisDatabase = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.database");
        redisTimeout = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.timeout");
        redisPoolMaxTotal = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.pool.max-total");
        redisPoolMaxIdle = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.pool.max-idle");
        redisPoolMinIdle = NacosConfigLoader.getRequiredInt(mergedConfig, "spring.data.redis.pool.min-idle");
    }

    private void loadDubboConfig() {
        dubboPort = NacosConfigLoader.getRequiredInt(mergedConfig, "dubbo.protocol.port");
        dubboRegistryTimeout = NacosConfigLoader.getRequiredInt(mergedConfig, "dubbo.registry.timeout");
    }

    private void loadRocketmqConfig() {
        String ns = NacosConfigLoader.getNestedString(mergedConfig, "rocketmq.name-server");
        if (ns != null && !ns.isEmpty() && !ns.startsWith("$")) {
            rocketmqNameServer = ns;
        }
        rocketmqProducerGroup = NacosConfigLoader.getRequiredString(mergedConfig, "rocketmq.producer.group");
        rocketmqSendMsgTimeout = NacosConfigLoader.getRequiredInt(mergedConfig, "rocketmq.producer.send-message-timeout");
        rocketmqConsumerGroup = NacosConfigLoader.getRequiredString(mergedConfig, "rocketmq.consumer.group");
    }

    private void loadNacosConfig() {
        nacosServerAddr = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.server-addr");
        nacosNamespace = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.namespace");
        nacosGroup = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.group");
        nacosUsername = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.username");
        nacosPassword = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.password");
    }

    private void loadGameplayConfig() {
        creepSpawnIntervalMs = NacosConfigLoader.getRequiredLong(mergedConfig, "battle.creep-spawn-interval-ms");
        runeSpawnIntervalMs = NacosConfigLoader.getRequiredLong(mergedConfig, "battle.rune-spawn-interval-ms");
        replaySnapshotInterval = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.replay-snapshot-interval");
        roomKeyTtlSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.room-key-ttl-seconds");
        loadReportIntervalSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.load-report-interval-seconds");
        metricHistorySize = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.metric-history-size");
        aiTickIntervalFrames = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.ai-tick-interval-frames");
        defaultAiLevel = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.default-ai-level");
        minBotLevel = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.min-bot-level");
        maxBotLevel = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.max-bot-level");
        defaultBotCountPerTeam = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.default-bot-count-per-team");
        aggroRange = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.aggro-range");
        defaultGridSize = NacosConfigLoader.getRequiredInt(mergedConfig, "battle.default-grid-size");
    }

    public int getTickRate() {
        return 1000 / tickIntervalMs;
    }
}
