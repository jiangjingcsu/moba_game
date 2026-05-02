package com.moba.battle.protocol.core;

import lombok.Data;

@Data
public class GamePacket {
    public static final int MAGIC = 0x4D4F;
    public static final int HEADER_SIZE = 14;
    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private SerializeType serializeType = SerializeType.JSON;
    private MessageType messageType;
    private int sequenceId;
    private byte[] body;

    public GamePacket() {}

    public GamePacket(MessageType messageType, int sequenceId) {
        this.messageType = messageType;
        this.sequenceId = sequenceId;
    }

    public GamePacket(MessageType messageType, int sequenceId, byte[] body) {
        this.messageType = messageType;
        this.sequenceId = sequenceId;
        this.body = body;
    }

    public static GamePacket response(MessageType requestType, int sequenceId, byte[] body) {
        MessageType respType = requestType.correspondingResponse();
        if (respType == null) {
            throw new IllegalArgumentException("无对应响应: " + requestType);
        }
        return new GamePacket(respType, sequenceId, body);
    }

    public static GamePacket notify(MessageType messageType, byte[] body) {
        return new GamePacket(messageType, 0, body);
    }

    public int getCommandCode() {
        return messageType != null ? messageType.getCode() : 0;
    }

    public boolean isRequest() {
        return messageType != null && messageType.isRequest();
    }

    public boolean isResponse() {
        return messageType != null && messageType.isResponse();
    }

    public boolean isNotify() {
        return messageType != null && messageType.isNotify();
    }
}
