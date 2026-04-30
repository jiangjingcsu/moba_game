package com.moba.battle.protocol;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class BattleEnterRequest {
    private long playerId;
    private String battleId;
    private int heroId;
    private int teamId;

    public static BattleEnterRequest parseFrom(byte[] data) {
        BattleEnterRequest request = new BattleEnterRequest();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length >= 1) {
            request.setPlayerId(Long.parseLong(parts[0]));
        }
        if (parts.length >= 2) {
            request.setBattleId(parts[1]);
        }
        if (parts.length >= 3) {
            request.setHeroId(Integer.parseInt(parts[2]));
        }
        if (parts.length >= 4) {
            request.setTeamId(Integer.parseInt(parts[3]));
        }
        return request;
    }

    public byte[] toByteArray() {
        String content = playerId + "|" + battleId + "|" + heroId + "|" + teamId;
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
