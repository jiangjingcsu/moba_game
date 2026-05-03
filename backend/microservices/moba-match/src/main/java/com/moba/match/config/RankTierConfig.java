package com.moba.match.config;

import lombok.Data;

import java.util.List;

@Data
public class RankTierConfig {

    private String currentRankTier;
    private List<RankTierRange> rankTierRanges;
    private int initialTolerance = 200;
    private int maxTolerance = 800;
    private int toleranceExpandStep = 50;
    private int toleranceExpandIntervalSeconds = 10;
}
