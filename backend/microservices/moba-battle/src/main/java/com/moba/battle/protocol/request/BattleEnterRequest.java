package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class BattleEnterRequest {
    private long battleId;
    private int heroId;
    private int teamId;
}
