package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class BattleResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String battleId;
    private int gameMode;
    private long startTime;
    private long endTime;
    private long duration;
    private int winnerTeamId;
    private List<PlayerStatDTO> players;
    private Map<Integer, TeamStatDTO> teamStats;
}
