package com.moba.common.model;

import com.moba.common.constant.GameMode;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class MatchInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private long matchId;
    private GameMode gameMode;
    private MatchState state;
    private long createTime;
    private long startTime;
    private List<Long> userIds;
    private int neededPlayers;
    private int aiLevel;
    private boolean aiMode;
    private long assignedBattleId;

    public enum MatchState {
        PENDING,
        FILLING,
        READY,
        CANCELLED,
        TIMEOUT
    }
}
