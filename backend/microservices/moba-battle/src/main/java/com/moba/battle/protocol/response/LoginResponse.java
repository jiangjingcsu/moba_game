package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class LoginResponse {
    private boolean success;
    private String message;
    private long userId;
    private String playerName;
    private int rank;
    private int rankScore;
    private String token;

    public static LoginResponse success(long userId, String playerName, int rank, int rankScore) {
        LoginResponse r = new LoginResponse();
        r.setSuccess(true);
        r.setMessage("登录成功");
        r.setUserId(userId);
        r.setPlayerName(playerName);
        r.setRank(rank);
        r.setRankScore(rankScore);
        return r;
    }

    public static LoginResponse failure(String message) {
        LoginResponse r = new LoginResponse();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
