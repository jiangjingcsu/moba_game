package com.moba.common.protocol;

public enum SerializeType {
    JSON(1),
    PROTOBUF(2);

    private final int code;

    SerializeType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SerializeType fromCode(int code) {
        for (SerializeType t : values()) {
            if (t.code == code) return t;
        }
        return JSON;
    }
}
