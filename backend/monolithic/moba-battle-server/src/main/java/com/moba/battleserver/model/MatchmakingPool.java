package com.moba.battleserver.model;

import lombok.Data;
import java.util.List;

@Data
public class MatchmakingPool {
    private final String poolId;
    private int requiredPlayerCount;
    private int minPlayerCount;
    private int teamCount;
    private List<Long> waitingPlayers;
    private long createTime;
    private long lastUpdateTime;

    public enum MatchType {
        THREE_VS_THREE_VS_THREE(9, 3, 3),
        THREE_VS_THREE(6, 3, 2);

        private final int playerCount;
        private final int teamSize;
        private final int teamCount;

        MatchType(int playerCount, int teamSize, int teamCount) {
            this.playerCount = playerCount;
            this.teamSize = teamSize;
            this.teamCount = teamCount;
        }

        public int getPlayerCount() { return playerCount; }
        public int getTeamSize() { return teamSize; }
        public int getTeamCount() { return teamCount; }
    }

    public MatchmakingPool(String poolId, MatchType matchType) {
        this.poolId = poolId;
        this.requiredPlayerCount = matchType.getPlayerCount();
        this.minPlayerCount = matchType.getPlayerCount() - 2;
        this.teamCount = matchType.getTeamCount();
        this.waitingPlayers = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public boolean addPlayer(Long playerId) {
        if (waitingPlayers.contains(playerId)) {
            return false;
        }
        waitingPlayers.add(playerId);
        this.lastUpdateTime = System.currentTimeMillis();
        return true;
    }

    public boolean removePlayer(Long playerId) {
        boolean removed = waitingPlayers.remove(playerId);
        if (removed) {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        return removed;
    }

    public boolean isReady() {
        return waitingPlayers.size() >= requiredPlayerCount;
    }

    public boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - createTime > timeoutMs;
    }

    public boolean isFlexibleReady() {
        return waitingPlayers.size() >= minPlayerCount;
    }

    public int getWaitingCount() {
        return waitingPlayers.size();
    }
}
