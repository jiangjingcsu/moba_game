package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattleEnterResponse {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("battleId")
    private String battleId;
    @JsonProperty("mapId")
    private int mapId;
    @JsonProperty("mapConfig")
    private String mapConfig;
    @JsonProperty("errorMessage")
    private String errorMessage;

    public static BattleEnterResponse success(String battleId, int mapId, String mapConfig) {
        return new BattleEnterResponse(true, battleId, mapId, mapConfig, "");
    }

    public static BattleEnterResponse failure(String errorMessage) {
        return new BattleEnterResponse(false, "", 0, "", errorMessage);
    }
}
