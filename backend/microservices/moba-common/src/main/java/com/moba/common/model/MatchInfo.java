package com.moba.common.model;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class MatchInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String matchId;
    private int gameMode;
    private MatchState state;
    private long createTime;
    private long startTime;
    private List<Long> playerIds;
    private int neededPlayers;
    private int aiLevel;
    private String assignedBattleId;

    public enum MatchState {
        PENDING,
        FILLING,
        READY,
        CANCELLED,
        TIMEOUT
    }
}
