package com.moba.common.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    private long playerId;
    private String username;
    private String nickname;
    private int level;
    private int rankScore;
    private int totalBattles;
    private int winCount;
    private int loseCount;
    private PlayerState state;

    public enum PlayerState {
        OFFLINE,
        ONLINE,
        IN_QUEUE,
        IN_MATCH,
        IN_BATTLE,
        SPECTATING
    }

    public double getWinRate() {
        if (totalBattles == 0) return 0.0;
        return (double) winCount / totalBattles * 100;
    }
}
