package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class BattleEnterResponse {
    private boolean success;
    private String battleId;
    private int mapId;
    private String mapConfig;
    private String errorMessage;

    public byte[] toByteArray() {
        String content = success + "|" + battleId + "|" + mapId + "|" +
                (mapConfig != null ? mapConfig : "") + "|" + (errorMessage != null ? errorMessage : "");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static BattleEnterResponse success(String battleId, int mapId, String mapConfig) {
        BattleEnterResponse response = new BattleEnterResponse();
        response.setSuccess(true);
        response.setBattleId(battleId);
        response.setMapId(mapId);
        response.setMapConfig(mapConfig);
        return response;
    }

    public static BattleEnterResponse failure(String errorMessage) {
        BattleEnterResponse response = new BattleEnterResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
