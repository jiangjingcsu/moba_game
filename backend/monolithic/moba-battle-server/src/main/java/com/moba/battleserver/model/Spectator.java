package com.moba.battleserver.model;

import lombok.Data;

@Data
public class Spectator {
    private final long spectatorId;
    private final String battleId;
    private long joinTime;
    private int currentFrame;
    private SpectatorMode mode;
    private boolean isFriendSpectate;
    private long followPlayerId;

    public enum SpectatorMode {
        FREE,
        FOLLOW,
        TEAM
    }

    public Spectator(long spectatorId, String battleId) {
        this.spectatorId = spectatorId;
        this.battleId = battleId;
        this.joinTime = System.currentTimeMillis();
        this.currentFrame = 0;
        this.mode = SpectatorMode.FREE;
        this.isFriendSpectate = false;
        this.followPlayerId = 0;
    }

    public void switchMode(SpectatorMode mode) {
        this.mode = mode;
    }

    public void followPlayer(long playerId) {
        this.mode = SpectatorMode.FOLLOW;
        this.followPlayerId = playerId;
    }
}
