package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class SkillCastRequest {
    private long playerId;
    private String battleId;
    private int skillId;
    private float targetX;
    private float targetY;
    private long targetEntityId;
    private int frameNumber;
}
