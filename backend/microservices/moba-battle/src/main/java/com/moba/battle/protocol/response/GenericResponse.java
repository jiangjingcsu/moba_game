package com.moba.battle.protocol.response;

import lombok.Data;

@Data
public class GenericResponse {
    private boolean success;
    private String errorMessage;

    public static GenericResponse success() {
        GenericResponse r = new GenericResponse();
        r.setSuccess(true);
        return r;
    }

    public static GenericResponse failure(String errorMessage) {
        GenericResponse r = new GenericResponse();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
