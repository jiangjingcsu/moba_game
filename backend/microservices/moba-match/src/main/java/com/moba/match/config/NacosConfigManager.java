package com.moba.match.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NacosConfigManager {

    @Value("${nacos.server-addr}")
    private String serverAddr;

    @Value("${nacos.username}")
    private String username;

    @Value("${nacos.password}")
    private String password;

    @Value("${nacos.namespace}")
    private String namespace;

    @Value("${nacos.group}")
    private String group;

    @Value("${nacos.rankTier}")
    private String rankTier;

    @Value("${nacos.config.dataId}")
    private String configDataId;

    @Value("${nacos.config.timeoutMs}")
    private long configTimeoutMs;

    @Value("${match.rank.initialTolerance}")
    private int initialTolerance;

    @Value("${match.rank.maxTolerance}")
    private int maxTolerance;

    @Value("${match.rank.toleranceExpandStep}")
    private int toleranceExpandStep;

    @Value("${match.rank.toleranceExpandIntervalSeconds}")
    private int toleranceExpandIntervalSeconds;

    private ConfigService configService;
    private volatile RankTierConfig rankTierConfig;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();

    @Data
    public static class RankTierConfig {
        private String currentRankTier;
        private List<RankTierRange> rankTierRanges;
        private int initialTolerance = 200;
        private int maxTolerance = 800;
        private int toleranceExpandStep = 50;
        private int toleranceExpandIntervalSeconds = 10;
    }

    @Data
    public static class RankTierRange {
        private String tierName;
        private int minScore;
        private int maxScore;
    }

    @PostConstruct
    public void init() {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("namespace", namespace);
            properties.setProperty("username", username);
            properties.setProperty("password", password);

            configService = NacosFactory.createConfigService(properties);
            log.info("Nacos ConfigService 已创建: serverAddr={}", serverAddr);

            loadConfig();

            configService.addListener(configDataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor();
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("收到Nacos配置变更通知: dataId={}", configDataId);
                    parseConfig(configInfo);
                }
            });

            log.info("Nacos配置监听已注册: dataId={}, group={}", configDataId, group);
        } catch (NacosException e) {
            log.warn("Nacos ConfigService初始化失败, 使用本地默认配置: {}", e.getMessage());
            initDefaultConfig();
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        retryScheduler.scheduleAtFixedRate(() -> {
            try {
                if (configService == null) {
                    Properties properties = new Properties();
                    properties.setProperty("serverAddr", serverAddr);
                    properties.setProperty("namespace", namespace);
                    properties.setProperty("username", username);
                    properties.setProperty("password", password);
                    configService = NacosFactory.createConfigService(properties);
                    loadConfig();
                    log.info("Nacos ConfigService 重连成功");
                }
            } catch (Exception ex) {
                log.warn("Nacos ConfigService 重连失败: {}", ex.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);
    }

    private void loadConfig() {
        try {
            String config = configService.getConfig(configDataId, group, configTimeoutMs);
            if (config != null) {
                parseConfig(config);
            } else {
                log.warn("Nacos配置不存在: dataId={}, 使用本地默认配置", configDataId);
                initDefaultConfig();
            }
        } catch (NacosException e) {
            log.warn("从Nacos加载配置失败, 使用本地默认配置: {}", e.getMessage());
            initDefaultConfig();
        }
    }

    private void parseConfig(String configYaml) {
        try {
            RankTierConfig parsed = yamlMapper.readValue(configYaml, RankTierConfig.class);
            if (parsed.getCurrentRankTier() != null) {
                this.rankTierConfig = parsed;
                log.info("Nacos段位配置已更新: currentRankTier={}, 段位区间数={}",
                        parsed.getCurrentRankTier(),
                        parsed.getRankTierRanges() != null ? parsed.getRankTierRanges().size() : 0);
            } else {
                log.warn("Nacos配置缺少currentRankTier字段, 保持当前配置");
            }
        } catch (Exception e) {
            log.error("解析Nacos段位配置失败: {}", e.getMessage());
        }
    }

    private void initDefaultConfig() {
        RankTierConfig defaultConfig = new RankTierConfig();
        defaultConfig.setCurrentRankTier(rankTier);
        defaultConfig.setInitialTolerance(initialTolerance);
        defaultConfig.setMaxTolerance(maxTolerance);
        defaultConfig.setToleranceExpandStep(toleranceExpandStep);
        defaultConfig.setToleranceExpandIntervalSeconds(toleranceExpandIntervalSeconds);

        List<RankTierRange> ranges = new ArrayList<>();
        ranges.add(createRange("bronze", 0, 999));
        ranges.add(createRange("silver", 1000, 1499));
        ranges.add(createRange("gold", 1500, 1999));
        ranges.add(createRange("platinum", 2000, 2499));
        ranges.add(createRange("diamond", 2500, 9999));
        defaultConfig.setRankTierRanges(ranges);

        this.rankTierConfig = defaultConfig;
        log.info("使用本地默认段位配置: rankTier={}", rankTier);
    }

    private RankTierRange createRange(String name, int min, int max) {
        RankTierRange range = new RankTierRange();
        range.setTierName(name);
        range.setMinScore(min);
        range.setMaxScore(max);
        return range;
    }

    @PreDestroy
    public void destroy() {
        retryScheduler.shutdown();
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (NacosException e) {
                log.warn("Nacos ConfigService关闭失败", e);
            }
        }
    }

    public RankTierConfig getRankTierConfig() {
        return rankTierConfig;
    }

    public String getCurrentRankTier() {
        return rankTierConfig != null ? rankTierConfig.getCurrentRankTier() : rankTier;
    }

    public String resolveRankTier(int rankScore) {
        if (rankTierConfig == null || rankTierConfig.getRankTierRanges() == null) {
            return rankTier;
        }
        for (RankTierRange range : rankTierConfig.getRankTierRanges()) {
            if (rankScore >= range.getMinScore() && rankScore <= range.getMaxScore()) {
                return range.getTierName();
            }
        }
        return rankTierConfig.getRankTierRanges().get(rankTierConfig.getRankTierRanges().size() - 1).getTierName();
    }

    public int getInitialTolerance() {
        return rankTierConfig != null ? rankTierConfig.getInitialTolerance() : initialTolerance;
    }

    public int getMaxTolerance() {
        return rankTierConfig != null ? rankTierConfig.getMaxTolerance() : maxTolerance;
    }

    public int getToleranceExpandStep() {
        return rankTierConfig != null ? rankTierConfig.getToleranceExpandStep() : toleranceExpandStep;
    }

    public int getToleranceExpandIntervalSeconds() {
        return rankTierConfig != null ? rankTierConfig.getToleranceExpandIntervalSeconds() : toleranceExpandIntervalSeconds;
    }
}
