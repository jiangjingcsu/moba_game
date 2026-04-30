package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class ReconnectResponse {
    private boolean success;
    private String battleState;
    private String errorMessage;

    public byte[] toByteArray() {
        String content = success + "|" + (battleState != null ? battleState : "") + "|" +
                (errorMessage != null ? errorMessage : "");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static ReconnectResponse success(String battleState) {
        ReconnectResponse response = new ReconnectResponse();
        response.setSuccess(true);
        response.setBattleState(battleState);
        return response;
    }

    public static ReconnectResponse failure(String errorMessage) {
        ReconnectResponse response = new ReconnectResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
