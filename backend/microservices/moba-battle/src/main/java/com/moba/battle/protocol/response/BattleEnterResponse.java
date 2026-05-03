package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class BattleEnterResponse {
    private boolean success;
    private String errorCode;
    private String message;
    private long battleId;
    private int mapId;
    private String mapConfig;

    public static BattleEnterResponse success(long battleId, int mapId, String mapConfig) {
        BattleEnterResponse r = new BattleEnterResponse();
        r.setSuccess(true);
        r.setMessage("进入战斗成功");
        r.setBattleId(battleId);
        r.setMapId(mapId);
        r.setMapConfig(mapConfig);
        return r;
    }

    public static BattleEnterResponse failure(String errorCode, String message) {
        BattleEnterResponse r = new BattleEnterResponse();
        r.setSuccess(false);
        r.setErrorCode(errorCode);
        r.setMessage(message);
        return r;
    }

    public static BattleEnterResponse failure(String message) {
        return failure(null, message);
    }
}
