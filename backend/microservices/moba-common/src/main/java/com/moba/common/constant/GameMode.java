package com.moba.common.constant;

public class GameMode {
    public static final int MODE_3V3V3 = 3;
    public static final int MODE_5V5 = 2;

    public static final String MODE_NAME_3V3V3 = "3v3v3";
    public static final String MODE_NAME_5V5 = "5v5";

    public static String getModeName(int mode) {
        if (mode == MODE_3V3V3) return MODE_NAME_3V3V3;
        if (mode == MODE_5V5) return MODE_NAME_5V5;
        return "unknown";
    }

    public static int getTeamCount(int mode) {
        if (mode == MODE_3V3V3) return 3;
        if (mode == MODE_5V5) return 2;
        return 2;
    }

    public static int getNeededPlayers(int mode) {
        if (mode == MODE_3V3V3) return 9;
        if (mode == MODE_5V5) return 10;
        return 10;
    }
}
