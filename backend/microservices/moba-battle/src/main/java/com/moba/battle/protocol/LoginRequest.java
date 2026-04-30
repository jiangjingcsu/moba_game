package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class LoginRequest {
    private String playerName;
    private int clientVersion;
    private String platform;

    public static LoginRequest parseFrom(byte[] data) {
        LoginRequest request = new LoginRequest();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length >= 1) {
            request.setPlayerName(parts[0]);
        }
        if (parts.length >= 2) {
            request.setClientVersion(Integer.parseInt(parts[1]));
        }
        if (parts.length >= 3) {
            request.setPlatform(parts[2]);
        }
        return request;
    }

    public byte[] toByteArray() {
        String content = playerName + "|" + clientVersion + "|" + platform;
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
