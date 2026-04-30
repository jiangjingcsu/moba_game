package com.moba.common.model;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class BattleInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String battleId;
    private int gameMode;
    private BattleState state;
    private long startTime;
    private long endTime;
    private int mapWidth;
    private int mapHeight;
    private Map<Integer, TeamInfo> teams;
    private List<PlayerBattleInfo> players;

    public enum BattleState {
        WAITING,
        LOADING,
        RUNNING,
        PAUSED,
        FINISHED
    }

    @Data
    public static class TeamInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private int teamId;
        private long basePlayerId;
        private int towerCount;
        private int barracksCount;
    }

    @Data
    public static class PlayerBattleInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private long playerId;
        private int teamId;
        private int heroId;
        private int killCount;
        private int deathCount;
        private int assistCount;
        private int damageDealt;
        private int healing;
        private boolean isAI;
        private boolean isWinner;
    }
}
