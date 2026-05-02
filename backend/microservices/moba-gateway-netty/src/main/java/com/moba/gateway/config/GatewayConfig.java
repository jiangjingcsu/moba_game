package com.moba.gateway.config;

import com.moba.common.config.NacosConfigLoader;
import lombok.Data;

import java.util.*;

@Data
public class GatewayConfig {

    private String host;
    private int port;
    private int bossThreadCount;
    private int workerThreadCount;
    private int maxConnections;
    private int idleTimeoutSeconds;
    private String jwtSecret;

    private String nacosServerAddr;
    private String nacosNamespace;
    private String nacosGroup;
    private String nacosUsername;
    private String nacosPassword;
    private String matchServiceName;
    private String battleServiceName;
    private boolean nacosEnabled;
    private long nacosRefreshIntervalSeconds;

    private String gatewayServiceName;
    private String wsPath;
    private String matchServiceWsPath;
    private String battleServiceWsPath;
    private String gatewayProtocol;
    private String gatewayVersion;
    private int httpMaxContentLength;
    private int backendThreadCount;

    private List<RankTierRange> rankTierRanges = new ArrayList<>();

    private final Map<String, Object> mergedConfig;

    @Data
    public static class RankTierRange {
        private String tierName;
        private int minScore;
        private int maxScore;

        public RankTierRange() {
        }

        public RankTierRange(String tierName, int minScore, int maxScore) {
            this.tierName = tierName;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
    }

    public static GatewayConfig load() {
        return new GatewayConfig();
    }

    public GatewayConfig() {
        this.mergedConfig = NacosConfigLoader.load("application.yml");
        loadFromMergedConfig();
    }

    private void loadFromMergedConfig() {
        loadServerConfig();
        loadJwtConfig();
        loadNacosConfig();
        loadGatewayMetadataConfig();
        loadRankTierRanges();
    }

    private void loadServerConfig() {
        host = NacosConfigLoader.getRequiredString(mergedConfig, "server.host");
        port = NacosConfigLoader.getRequiredInt(mergedConfig, "server.port");
        bossThreadCount = NacosConfigLoader.getRequiredInt(mergedConfig, "server.bossThreadCount");
        workerThreadCount = NacosConfigLoader.getRequiredInt(mergedConfig, "server.workerThreadCount");
        maxConnections = NacosConfigLoader.getRequiredInt(mergedConfig, "server.maxConnections");
        idleTimeoutSeconds = NacosConfigLoader.getRequiredInt(mergedConfig, "server.idleTimeoutSeconds");
    }

    private void loadJwtConfig() {
        jwtSecret = NacosConfigLoader.getRequiredString(mergedConfig, "jwt.secret");
    }

    private void loadNacosConfig() {
        nacosServerAddr = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.server-addr");
        nacosNamespace = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.namespace");
        nacosGroup = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.group");
        nacosUsername = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.username");
        nacosPassword = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.password");
        matchServiceName = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.match-service-name");
        battleServiceName = NacosConfigLoader.getRequiredString(mergedConfig, "nacos.battle-service-name");
        nacosEnabled = NacosConfigLoader.getRequiredBoolean(mergedConfig, "nacos.enabled");
        nacosRefreshIntervalSeconds = NacosConfigLoader.getRequiredLong(mergedConfig, "nacos.refresh-interval-seconds");
    }

    private void loadGatewayMetadataConfig() {
        gatewayServiceName = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.service-name");
        wsPath = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.ws-path");
        matchServiceWsPath = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.match-service-ws-path");
        battleServiceWsPath = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.battle-service-ws-path");
        gatewayProtocol = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.protocol");
        gatewayVersion = NacosConfigLoader.getRequiredString(mergedConfig, "gateway.version");
        httpMaxContentLength = NacosConfigLoader.getRequiredInt(mergedConfig, "gateway.http-max-content-length");
        backendThreadCount = NacosConfigLoader.getRequiredInt(mergedConfig, "gateway.backend-thread-count");
    }

    @SuppressWarnings("unchecked")
    private void loadRankTierRanges() {
        List<Map<String, Object>> ranges = NacosConfigLoader.getNestedList(mergedConfig, "rank-tier-ranges");
        if (ranges == null || ranges.isEmpty()) {
            throw new com.moba.common.exception.BusinessException(500, "缺少必要配置项: rank-tier-ranges");
        }
        rankTierRanges.clear();
        for (Map<String, Object> tier : ranges) {
            String tierName = (String) tier.get("tier-name");
            if (tierName == null || tierName.isEmpty()) {
                throw new com.moba.common.exception.BusinessException(500, "rank-tier-ranges中缺少tier-name配置");
            }
            Object minScoreObj = tier.get("min-score");
            Object maxScoreObj = tier.get("max-score");
            if (minScoreObj == null) {
                throw new com.moba.common.exception.BusinessException(500, "rank-tier-ranges中缺少min-score配置, tier=" + tierName);
            }
            if (maxScoreObj == null) {
                throw new com.moba.common.exception.BusinessException(500, "rank-tier-ranges中缺少max-score配置, tier=" + tierName);
            }
            RankTierRange range = new RankTierRange();
            range.setTierName(tierName);
            range.setMinScore(((Number) minScoreObj).intValue());
            range.setMaxScore(((Number) maxScoreObj).intValue());
            rankTierRanges.add(range);
        }
    }

    public String resolveRankTier(int rankScore) {
        for (RankTierRange range : rankTierRanges) {
            if (rankScore >= range.getMinScore() && rankScore <= range.getMaxScore()) {
                return range.getTierName();
            }
        }
        return rankTierRanges.get(rankTierRanges.size() - 1).getTierName();
    }
}
