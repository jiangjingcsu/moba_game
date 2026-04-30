package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("playerId")
    private long playerId;
    @JsonProperty("playerName")
    private String playerName;
    @JsonProperty("rank")
    private int rank;
    @JsonProperty("rankScore")
    private int rankScore;
    @JsonProperty("errorMessage")
    private String errorMessage;

    public static LoginResponse success(long playerId, String playerName, int rank, int rankScore) {
        return new LoginResponse(true, playerId, playerName, rank, rankScore, "");
    }

    public static LoginResponse failure(String errorMessage) {
        return new LoginResponse(false, 0, "", 0, 0, errorMessage);
    }
}
