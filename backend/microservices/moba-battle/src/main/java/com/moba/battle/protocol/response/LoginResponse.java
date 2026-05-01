package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class LoginResponse {
    private boolean success;
    private long playerId;
    private String playerName;
    private int rank;
    private int rankScore;
    private String errorMessage;

    public static LoginResponse success(long playerId, String playerName, int rank, int rankScore) {
        LoginResponse r = new LoginResponse();
        r.setSuccess(true);
        r.setPlayerId(playerId);
        r.setPlayerName(playerName);
        r.setRank(rank);
        r.setRankScore(rankScore);
        return r;
    }

    public static LoginResponse failure(String errorMessage) {
        LoginResponse r = new LoginResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
