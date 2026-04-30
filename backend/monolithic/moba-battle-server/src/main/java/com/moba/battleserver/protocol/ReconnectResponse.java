package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconnectResponse {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("battleState")
    private String battleState;
    @JsonProperty("errorMessage")
    private String errorMessage;

    public static ReconnectResponse success(String battleState) {
        return new ReconnectResponse(true, battleState, "");
    }

    public static ReconnectResponse failure(String errorMessage) {
        return new ReconnectResponse(false, "", errorMessage);
    }
}
