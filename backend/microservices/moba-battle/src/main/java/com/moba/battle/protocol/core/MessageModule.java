package com.moba.battle.protocol.core;

public enum MessageModule {
    SYSTEM(0x01),
    AUTH(0x02),
    BATTLE(0x04),
    ROOM(0x05),
    SOCIAL(0x06),
    SPECTATOR(0x07);

    private final int code;

    MessageModule(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageModule fromCode(int code) {
        for (MessageModule m : values()) {
            if (m.code == code) return m;
        }
        return null;
    }
}
