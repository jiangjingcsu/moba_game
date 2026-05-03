package com.moba.netty.protocol;

public final class ProtocolConstants {

    public static final int HEADER_SIZE = 11;
    public static final int LENGTH_FIELD_OFFSET = 0;
    public static final int LENGTH_FIELD_LENGTH = 4;
    public static final int LENGTH_ADJUSTMENT = -LENGTH_FIELD_LENGTH;
    public static final int INITIAL_BYTES_TO_STRIP = 0;
    public static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;
    public static final String CHARSET_NAME = "UTF-8";

    public static final int SEQ_FIELD_OFFSET = 4;
    public static final int SEQ_FIELD_LENGTH = 4;
    public static final int EXTENSION_ID_FIELD_OFFSET = 8;
    public static final int EXTENSION_ID_FIELD_LENGTH = 2;
    public static final int CMD_ID_FIELD_OFFSET = 10;
    public static final int CMD_ID_FIELD_LENGTH = 1;
    public static final int DATA_FIELD_OFFSET = 11;

    public static final short EXTENSION_SYSTEM = 0x01;
    public static final short EXTENSION_AUTH = 0x02;
    public static final short EXTENSION_MATCH = 0x03;
    public static final short EXTENSION_BATTLE = 0x04;
    public static final short EXTENSION_ROOM = 0x05;
    public static final short EXTENSION_SOCIAL = 0x06;
    public static final short EXTENSION_SPECTATOR = 0x07;

    public static final byte CMD_HEARTBEAT = 1;

    public static final byte CMD_AUTH_LOGIN = 1;
    public static final byte CMD_AUTH_RECONNECT = 2;

    public static final byte CMD_MATCH_JOIN = 1;
    public static final byte CMD_MATCH_STATUS = 2;
    public static final byte CMD_MATCH_CANCEL = 3;
    public static final byte CMD_MATCH_SUCCESS_NOTIFY = 4;
    public static final byte CMD_MATCH_PROGRESS_NOTIFY = 5;

    public static final byte CMD_BATTLE_ENTER = 1;
    public static final byte CMD_BATTLE_READY = 2;
    public static final byte CMD_BATTLE_ACTION = 3;
    public static final byte CMD_BATTLE_SKILL_CAST = 4;
    public static final byte CMD_BATTLE_RECONNECT = 5;
    public static final byte CMD_BATTLE_STATE_NOTIFY = 6;
    public static final byte CMD_BATTLE_END_NOTIFY = 7;
    public static final byte CMD_BATTLE_FRAME_SYNC = 8;
    public static final byte CMD_BATTLE_COUNTDOWN_NOTIFY = 9;
    public static final byte CMD_BATTLE_START_NOTIFY = 10;
    public static final byte CMD_BATTLE_EVENT_NOTIFY = 11;
    public static final byte CMD_BATTLE_HASH_CHECK = 12;
    public static final byte CMD_BATTLE_STATE_CORRECTION = 13;

    public static final byte CMD_ROOM_CREATE = 1;
    public static final byte CMD_ROOM_JOIN = 2;
    public static final byte CMD_ROOM_LEAVE = 3;
    public static final byte CMD_ROOM_STATE_NOTIFY = 4;

    public static final byte CMD_SOCIAL_CHAT = 1;
    public static final byte CMD_SOCIAL_EMOTE = 2;

    public static final byte CMD_SPECTATOR_JOIN = 1;
    public static final byte CMD_SPECTATOR_LEAVE = 2;

    private ProtocolConstants() {
    }
}
