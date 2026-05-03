package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateBattleResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private long battleId;
    private String errorMessage;

    public static CreateBattleResponse ok(long battleId) {
        CreateBattleResponse r = new CreateBattleResponse();
        r.setSuccess(true);
        r.setBattleId(battleId);
        return r;
    }

    public static CreateBattleResponse fail(String errorMessage) {
        CreateBattleResponse r = new CreateBattleResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
