package com.moba.match.repository;

public final class MatchRedisKeys {

    private MatchRedisKeys() {}

    private static final String PREFIX = "moba:match:";
    private static final String BATTLE_PREFIX = "moba:battle:";

    public static String playerMatch(long userId) {
        return PREFIX + "player:" + userId;
    }

    public static String matchResult(long userId) {
        return PREFIX + "result:" + userId;
    }

    public static String matchBattleId(long matchId) {
        return PREFIX + "battleId:" + matchId;
    }

    public static String battleMatchId(long battleId) {
        return BATTLE_PREFIX + "matchId:" + battleId;
    }

    public static String playerBattleRoom(long userId) {
        return BATTLE_PREFIX + "player_room:" + userId;
    }

    public static String battleRoom(long battleId) {
        return BATTLE_PREFIX + "room:" + battleId;
    }

    public static String battleRoomServer(long battleId) {
        return BATTLE_PREFIX + "room:" + battleId + ":server";
    }
}
