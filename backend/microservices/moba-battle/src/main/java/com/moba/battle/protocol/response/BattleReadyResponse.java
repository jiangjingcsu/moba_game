package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class BattleReadyResponse {
    private boolean success;
    private int readyCount;
    private int expectedCount;
    private boolean allReady;
    private String errorMessage;

    public static BattleReadyResponse success(int readyCount, int expectedCount, boolean allReady) {
        BattleReadyResponse r = new BattleReadyResponse();
        r.setSuccess(true);
        r.setReadyCount(readyCount);
        r.setExpectedCount(expectedCount);
        r.setAllReady(allReady);
        return r;
    }

    public static BattleReadyResponse failure(String errorMessage) {
        BattleReadyResponse r = new BattleReadyResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
