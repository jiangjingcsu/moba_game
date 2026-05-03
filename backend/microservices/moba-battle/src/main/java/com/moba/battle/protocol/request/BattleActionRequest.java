package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class BattleActionRequest {
    private long battleId;
    private int actionType;
    private float targetX;
    private float targetY;
    private int targetId;
    private int frameNumber;
}
