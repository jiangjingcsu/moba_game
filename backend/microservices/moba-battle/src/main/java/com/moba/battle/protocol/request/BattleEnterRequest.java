package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class BattleEnterRequest {
    private String roomId;
    private int heroId;
    private int teamId;
}
