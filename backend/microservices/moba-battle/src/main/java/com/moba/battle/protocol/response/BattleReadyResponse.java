package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class BattleReadyResponse {
    private boolean success;
    private String message;
    private int readyCount;
    private int expectedCount;
    private boolean allReady;

    public static BattleReadyResponse success(int readyCount, int expectedCount, boolean allReady) {
        BattleReadyResponse r = new BattleReadyResponse();
        r.setSuccess(true);
        r.setMessage("就绪成功");
        r.setReadyCount(readyCount);
        r.setExpectedCount(expectedCount);
        r.setAllReady(allReady);
        return r;
    }

    public static BattleReadyResponse failure(String message) {
        BattleReadyResponse r = new BattleReadyResponse();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
