package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class ReconnectRequest {
    private long playerId;
    private String battleId;

    public static ReconnectRequest parseFrom(byte[] data) {
        ReconnectRequest request = new ReconnectRequest();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length >= 1) {
            request.setPlayerId(Long.parseLong(parts[0]));
        }
        if (parts.length >= 2) {
            request.setBattleId(parts[1]);
        }
        return request;
    }

    public byte[] toByteArray() {
        String content = playerId + "|" + battleId;
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
