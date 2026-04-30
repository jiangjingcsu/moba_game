package com.moba.common.constant;

public class BattleConstants {

    private BattleConstants() {
    }

    public static final int TICK_INTERVAL_MS = 67;
    public static final int TICK_RATE_HZ = 15;
    public static final int INPUT_DELAY_FRAMES = 3;

    public static final int MAX_ROOMS_PER_PROCESS = 100;
    public static final int HASH_CHECK_INTERVAL = 10;

    public static final int RECONNECT_TIMEOUT_SECONDS = 30;
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    public static final int DEFAULT_GRID_SIZE = 200;
    public static final int MAP_3V3V3_WIDTH = 12000;
    public static final int MAP_3V3V3_HEIGHT = 12000;
    public static final int MAP_5V5_WIDTH = 16000;
    public static final int MAP_5V5_HEIGHT = 16000;

    public static final int AI_TICK_INTERVAL_FRAMES = 2;
    public static final int DEFAULT_AI_LEVEL = 5;

    public static final int SPECTATOR_BUFFER_SECONDS = 30;
    public static final int REPLAY_SNAPSHOT_INTERVAL = 60;

    public static final long MATCHMAKING_TIMEOUT_SECONDS = 60;
    public static final int MIN_PLAYERS_FOR_3V3V3 = 3;
    public static final int MIN_PLAYERS_FOR_5V5 = 5;
}
