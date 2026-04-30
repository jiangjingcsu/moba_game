package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class MatchResponse {
    private boolean success;
    private int matchStatus;
    private long waitTime;
    private String battleId;
    private String errorMessage;

    public static final int STATUS_WAITING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_CANCELLED = 2;
    public static final int STATUS_TIMEOUT = 3;

    public byte[] toByteArray() {
        String content = success + "|" + matchStatus + "|" + waitTime + "|" +
                (battleId != null ? battleId : "") + "|" + (errorMessage != null ? errorMessage : "");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static MatchResponse waiting(long waitTime) {
        MatchResponse response = new MatchResponse();
        response.setSuccess(true);
        response.setMatchStatus(STATUS_WAITING);
        response.setWaitTime(waitTime);
        return response;
    }

    public static MatchResponse success(String battleId) {
        MatchResponse response = new MatchResponse();
        response.setSuccess(true);
        response.setMatchStatus(STATUS_SUCCESS);
        response.setBattleId(battleId);
        return response;
    }

    public static MatchResponse cancelled() {
        MatchResponse response = new MatchResponse();
        response.setSuccess(false);
        response.setMatchStatus(STATUS_CANCELLED);
        return response;
    }

    public static MatchResponse failure(String errorMessage) {
        MatchResponse response = new MatchResponse();
        response.setSuccess(false);
        response.setMatchStatus(STATUS_TIMEOUT);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
