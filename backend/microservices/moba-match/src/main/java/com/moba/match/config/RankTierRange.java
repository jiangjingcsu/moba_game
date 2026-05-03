package com.moba.match.config;

import lombok.Data;

import java.util.List;

@Data
public class RankTierRange {

    private String tierName;
    private int minScore;
    private int maxScore;
}
