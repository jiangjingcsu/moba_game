package com.moba.match.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoreRange {

    private final String name;
    private final int minScore;
    private final int maxScore;
}
