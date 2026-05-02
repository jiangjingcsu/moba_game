package com.moba.common.protocol;

public enum MessageDirection {
    REQUEST(1),
    RESPONSE(2),
    NOTIFY(3);

    private final int code;

    MessageDirection(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
