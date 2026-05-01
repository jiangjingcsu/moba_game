package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class BattleEnterResponse {
    private boolean success;
    private String roomId;
    private int mapId;
    private String mapConfig;
    private String errorCode;
    private String errorMessage;

    public static BattleEnterResponse success(String roomId, int mapId, String mapConfig) {
        BattleEnterResponse r = new BattleEnterResponse();
        r.setSuccess(true);
        r.setRoomId(roomId);
        r.setMapId(mapId);
        r.setMapConfig(mapConfig);
        return r;
    }

    public static BattleEnterResponse failure(String errorMessage) {
        BattleEnterResponse r = new BattleEnterResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }

    public static BattleEnterResponse failure(String errorCode, String errorMessage) {
        BattleEnterResponse r = new BattleEnterResponse();
        r.setSuccess(false);
        r.setErrorCode(errorCode);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
