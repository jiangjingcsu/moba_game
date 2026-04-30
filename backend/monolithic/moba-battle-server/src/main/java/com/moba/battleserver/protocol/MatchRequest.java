package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRequest {
    @JsonProperty("playerId")
    private long playerId;
    @JsonProperty("matchType")
    private int matchType;
    @JsonProperty("preferredRole")
    private int preferredRole;
}
