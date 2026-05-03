package com.moba.battle.model;

import lombok.Data;

@Data
public class Spectator {
    private final long spectatorId;
    private final long battleId;
    private long joinTime;
    private int currentFrame;
    private SpectatorMode mode;
    private boolean isFriendSpectate;
    private long followUserId;

    public enum SpectatorMode {
        FREE,
        FOLLOW,
        TEAM
    }

    public Spectator(long spectatorId, long battleId) {
        this.spectatorId = spectatorId;
        this.battleId = battleId;
        this.joinTime = System.currentTimeMillis();
        this.currentFrame = 0;
        this.mode = SpectatorMode.FREE;
        this.isFriendSpectate = false;
        this.followUserId = 0;
    }

    public void switchMode(SpectatorMode mode) {
        this.mode = mode;
    }

    public void followPlayer(long userId) {
        this.mode = SpectatorMode.FOLLOW;
        this.followUserId = userId;
    }
}
