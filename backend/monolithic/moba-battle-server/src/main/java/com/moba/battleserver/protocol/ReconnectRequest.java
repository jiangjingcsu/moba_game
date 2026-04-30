package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconnectRequest {
    @JsonProperty("playerId")
    private long playerId;
    @JsonProperty("battleId")
    private String battleId;
}
