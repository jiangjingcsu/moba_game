package com.moba.common.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GameMode {

    MODE_5V5(2, "5v5", 2, 5, 10, false),
    MODE_3V3V3(3, "3v3v3", 3, 3, 9, false),
    MODE_AI_5V5(4, "AI_5v5", 2, 5, 1, true),
    MODE_AI_3V3V3(5, "AI_3v3v3", 3, 3, 1, true);

    private final int code;
    private final String name;
    private final int teamCount;
    private final int teamSize;
    private final int neededPlayers;
    private final boolean aiMode;

    GameMode(int code, String name, int teamCount, int teamSize, int neededPlayers, boolean aiMode) {
        this.code = code;
        this.name = name;
        this.teamCount = teamCount;
        this.teamSize = teamSize;
        this.neededPlayers = neededPlayers;
        this.aiMode = aiMode;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public int getNeededPlayers() {
        return neededPlayers;
    }

    public boolean isAiMode() {
        return aiMode;
    }

    public int getMaxRealPlayers() {
        return aiMode ? 1 : neededPlayers;
    }

    public GameMode getBaseMode() {
        return switch (this) {
            case MODE_AI_3V3V3 -> MODE_3V3V3;
            case MODE_AI_5V5 -> MODE_5V5;
            default -> this;
        };
    }

    public int getDefaultAiLevel() {
        return aiMode ? 5 : 0;
    }

    @JsonCreator
    public static GameMode fromCode(int code) {
        for (GameMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的游戏模式: " + code);
    }

    public static GameMode fromCodeOrNull(int code) {
        for (GameMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return null;
    }
}
