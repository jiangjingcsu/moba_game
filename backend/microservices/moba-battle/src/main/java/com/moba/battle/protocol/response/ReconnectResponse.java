package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class ReconnectResponse {
    private boolean success;
    private String battleState;
    private String errorMessage;

    public static ReconnectResponse success(String battleState) {
        ReconnectResponse r = new ReconnectResponse();
        r.setSuccess(true);
        r.setBattleState(battleState);
        return r;
    }

    public static ReconnectResponse failure(String errorMessage) {
        ReconnectResponse r = new ReconnectResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
