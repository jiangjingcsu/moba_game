package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String playerName;
    private int clientVersion;
    private String platform;
}
