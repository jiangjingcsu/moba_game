package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class MatchRequest {
    private long playerId;
    private int matchType;
    private int preferredRole;

    public static MatchRequest parseFrom(byte[] data) {
        MatchRequest request = new MatchRequest();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length >= 1) {
            request.setPlayerId(Long.parseLong(parts[0]));
        }
        if (parts.length >= 2) {
            request.setMatchType(Integer.parseInt(parts[1]));
        }
        if (parts.length >= 3) {
            request.setPreferredRole(Integer.parseInt(parts[2]));
        }
        return request;
    }

    public byte[] toByteArray() {
        String content = playerId + "|" + matchType + "|" + preferredRole;
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
