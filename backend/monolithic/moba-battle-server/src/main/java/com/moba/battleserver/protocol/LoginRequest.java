package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @JsonProperty("playerName")
    private String playerName;
    @JsonProperty("clientVersion")
    private int clientVersion;
    @JsonProperty("platform")
    private String platform;
}
