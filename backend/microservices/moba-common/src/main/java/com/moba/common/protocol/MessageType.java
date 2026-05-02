package com.moba.common.protocol;

import lombok.Getter;

@Getter
public enum MessageType {
    HEARTBEAT_REQ(MessageModule.SYSTEM, 1, MessageDirection.REQUEST),
    HEARTBEAT_RESP(MessageModule.SYSTEM, 1, MessageDirection.RESPONSE),

    LOGIN_REQ(MessageModule.AUTH, 1, MessageDirection.REQUEST),
    LOGIN_RESP(MessageModule.AUTH, 1, MessageDirection.RESPONSE),
    RECONNECT_REQ(MessageModule.AUTH, 2, MessageDirection.REQUEST),
    RECONNECT_RESP(MessageModule.AUTH, 2, MessageDirection.RESPONSE),

    MATCH_JOIN_REQ(MessageModule.MATCH, 1, MessageDirection.REQUEST),
    MATCH_JOIN_RESP(MessageModule.MATCH, 1, MessageDirection.RESPONSE),
    MATCH_STATUS_REQ(MessageModule.MATCH, 2, MessageDirection.REQUEST),
    MATCH_STATUS_RESP(MessageModule.MATCH, 2, MessageDirection.RESPONSE),
    MATCH_CANCEL_REQ(MessageModule.MATCH, 3, MessageDirection.REQUEST),
    MATCH_CANCEL_RESP(MessageModule.MATCH, 3, MessageDirection.RESPONSE),
    MATCH_SUCCESS_NOTIFY(MessageModule.MATCH, 4, MessageDirection.NOTIFY),

    BATTLE_ENTER_REQ(MessageModule.BATTLE, 1, MessageDirection.REQUEST),
    BATTLE_ENTER_RESP(MessageModule.BATTLE, 1, MessageDirection.RESPONSE),
    BATTLE_READY_REQ(MessageModule.BATTLE, 2, MessageDirection.REQUEST),
    BATTLE_READY_RESP(MessageModule.BATTLE, 2, MessageDirection.RESPONSE),
    BATTLE_ACTION_REQ(MessageModule.BATTLE, 3, MessageDirection.REQUEST),
    BATTLE_SKILL_CAST_REQ(MessageModule.BATTLE, 4, MessageDirection.REQUEST),
    BATTLE_STATE_NOTIFY(MessageModule.BATTLE, 5, MessageDirection.NOTIFY),
    BATTLE_END_NOTIFY(MessageModule.BATTLE, 6, MessageDirection.NOTIFY),
    BATTLE_FRAME_SYNC_NOTIFY(MessageModule.BATTLE, 7, MessageDirection.NOTIFY),
    BATTLE_COUNTDOWN_NOTIFY(MessageModule.BATTLE, 8, MessageDirection.NOTIFY),
    BATTLE_START_NOTIFY(MessageModule.BATTLE, 9, MessageDirection.NOTIFY),
    BATTLE_EVENT_NOTIFY(MessageModule.BATTLE, 10, MessageDirection.NOTIFY),
    BATTLE_HASH_CHECK_NOTIFY(MessageModule.BATTLE, 11, MessageDirection.NOTIFY),
    BATTLE_STATE_CORRECTION_NOTIFY(MessageModule.BATTLE, 12, MessageDirection.NOTIFY),

    ROOM_CREATE_REQ(MessageModule.ROOM, 1, MessageDirection.REQUEST),
    ROOM_CREATE_RESP(MessageModule.ROOM, 1, MessageDirection.RESPONSE),
    ROOM_JOIN_REQ(MessageModule.ROOM, 2, MessageDirection.REQUEST),
    ROOM_JOIN_RESP(MessageModule.ROOM, 2, MessageDirection.RESPONSE),
    ROOM_LEAVE_REQ(MessageModule.ROOM, 3, MessageDirection.REQUEST),
    ROOM_STATE_NOTIFY(MessageModule.ROOM, 4, MessageDirection.NOTIFY),

    CHAT_REQ(MessageModule.SOCIAL, 1, MessageDirection.REQUEST),
    CHAT_NOTIFY(MessageModule.SOCIAL, 1, MessageDirection.NOTIFY),
    EMOTE_REQ(MessageModule.SOCIAL, 2, MessageDirection.REQUEST),
    EMOTE_NOTIFY(MessageModule.SOCIAL, 2, MessageDirection.NOTIFY),

    SPECTATOR_JOIN_REQ(MessageModule.SPECTATOR, 1, MessageDirection.REQUEST),
    SPECTATOR_JOIN_RESP(MessageModule.SPECTATOR, 1, MessageDirection.RESPONSE),
    SPECTATOR_LEAVE_REQ(MessageModule.SPECTATOR, 2, MessageDirection.REQUEST),
    ;

    private final MessageModule module;
    private final int cmdId;
    private final MessageDirection direction;
    private final int code;

    MessageType(MessageModule module, int cmdId, MessageDirection direction) {
        this.module = module;
        this.cmdId = cmdId;
        this.direction = direction;
        this.code = (module.getCode() << 8) | (cmdId << 3) | direction.getCode();
    }

    public static MessageType fromCode(int code) {
        for (MessageType mt : values()) {
            if (mt.code == code) return mt;
        }
        return null;
    }

    public boolean isRequest() {
        return direction == MessageDirection.REQUEST;
    }

    public boolean isResponse() {
        return direction == MessageDirection.RESPONSE;
    }

    public boolean isNotify() {
        return direction == MessageDirection.NOTIFY;
    }

    public MessageType correspondingResponse() {
        if (direction != MessageDirection.REQUEST) return null;
        String respName = name().replace("_REQ", "_RESP");
        try {
            return valueOf(respName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
