package com.moba.match.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NacosConfigManager {

    @Value("${nacos.rankTier}")
    private String rankTier;

    @Value("${match.rank.initialTolerance}")
    private int initialTolerance;

    @Value("${match.rank.maxTolerance}")
    private int maxTolerance;

    @Value("${match.rank.toleranceExpandStep}")
    private int toleranceExpandStep;

    @Value("${match.rank.toleranceExpandIntervalSeconds}")
    private int toleranceExpandIntervalSeconds;

    private volatile RankTierConfig rankTierConfig;

    private final NacosConfigConnector connector;

    public NacosConfigManager(NacosConfigConnector connector) {
        this.connector = connector;
    }

    @PostConstruct
    public void init() {
        String configYaml = connector.getConfig();
        if (configYaml != null) {
            parseConfig(configYaml);
        } else {
            initDefaultConfig();
        }

        connector.addConfigListener(this::parseConfig);
    }

    private void parseConfig(String configYaml) {
        try {
            RankTierConfig parsed = connector.getYamlMapper().readValue(configYaml, RankTierConfig.class);
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
