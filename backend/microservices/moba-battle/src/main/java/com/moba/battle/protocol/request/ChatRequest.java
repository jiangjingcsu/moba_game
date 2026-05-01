package com.moba.battle.protocol.request;

import lombok.Data;

@Data
public class ChatRequest {
    private long playerId;
    private String battleId;
    private int chatType;
    private String message;
}
