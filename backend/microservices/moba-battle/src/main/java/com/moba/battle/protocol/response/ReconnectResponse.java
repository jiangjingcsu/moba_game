package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class ReconnectResponse {
    private boolean success;
    private String message;
    private String stateJson;

    public static ReconnectResponse success(String stateJson) {
        ReconnectResponse r = new ReconnectResponse();
        r.setSuccess(true);
        r.setMessage("重连成功");
        r.setStateJson(stateJson);
        return r;
    }

    public static ReconnectResponse failure(String message) {
        ReconnectResponse r = new ReconnectResponse();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
