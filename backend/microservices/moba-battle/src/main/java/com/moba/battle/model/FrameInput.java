package com.moba.battle.model;

import lombok.Data;
import java.util.List;

@Data
public class FrameInput {
    private int frameNumber;
    private long userId;
    private InputType type;
    private byte[] data;

    public enum InputType {
        MOVE,
        ATTACK,
        SKILL_CAST,
        USE_ITEM,
        BUY_ITEM
    }

    public byte[] serialize() {
        return data;
    }

    public static FrameInput deserialize(int frameNumber, long userId, InputType type, byte[] data) {
        FrameInput input = new FrameInput();
        input.setFrameNumber(frameNumber);
        input.setUserId(userId);
        input.setType(type);
        input.setData(data);
        return input;
    }
}
