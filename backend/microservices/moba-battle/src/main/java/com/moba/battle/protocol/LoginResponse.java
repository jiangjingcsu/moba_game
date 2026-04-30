package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class LoginResponse {
    private boolean success;
    private long playerId;
    private String playerName;
    private int rank;
    private int rankScore;
    private String errorMessage;

    public static LoginResponse parseFrom(byte[] data) {
        LoginResponse response = new LoginResponse();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length >= 1) {
            response.setSuccess(Boolean.parseBoolean(parts[0]));
        }
        if (parts.length >= 2) {
            response.setPlayerId(Long.parseLong(parts[1]));
        }
        if (parts.length >= 3) {
            response.setPlayerName(parts[2]);
        }
        if (parts.length >= 4) {
            response.setRank(Integer.parseInt(parts[3]));
        }
        if (parts.length >= 5) {
            response.setRankScore(Integer.parseInt(parts[4]));
        }
        if (parts.length >= 6) {
            response.setErrorMessage(parts[5]);
        }
        return response;
    }

    public byte[] toByteArray() {
        String content = success + "|" + playerId + "|" + playerName + "|" + rank + "|" + rankScore + "|" + (errorMessage != null ? errorMessage : "");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static LoginResponse success(long playerId, String playerName, int rank, int rankScore) {
        LoginResponse response = new LoginResponse();
        response.setSuccess(true);
        response.setPlayerId(playerId);
        response.setPlayerName(playerName);
        response.setRank(rank);
        response.setRankScore(rankScore);
        return response;
    }

    public static LoginResponse failure(String errorMessage) {
        LoginResponse response = new LoginResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
