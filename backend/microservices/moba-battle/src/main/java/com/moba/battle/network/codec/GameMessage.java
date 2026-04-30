package com.moba.battle.network.codec;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GameMessage {
    private int messageId;
    private byte[] body;

    public static final int HEARTBEAT_REQUEST = 0x0001;
    public static final int HEARTBEAT_RESPONSE = 0x0002;
    public static final int LOGIN_REQUEST = 0x0010;
    public static final int LOGIN_RESPONSE = 0x0011;
    public static final int MATCH_REQUEST = 0x0020;
    public static final int MATCH_RESPONSE = 0x0021;
    public static final int MATCH_CANCEL_REQUEST = 0x0022;
    public static final int BATTLE_ENTER_REQUEST = 0x0030;
    public static final int BATTLE_ENTER_RESPONSE = 0x0031;
    public static final int PLAYER_ACTION_REQUEST = 0x0040;
    public static final int BATTLE_STATE_UPDATE = 0x0041;
    public static final int SKILL_CAST_REQUEST = 0x0042;
    public static final int BATTLE_END_NOTIFY = 0x0050;
    public static final int RECONNECT_REQUEST = 0x0060;
    public static final int RECONNECT_RESPONSE = 0x0061;

    private static final Map<Integer, String> MESSAGE_NAMES = new HashMap<>();

    static {
        MESSAGE_NAMES.put(HEARTBEAT_REQUEST, "HeartbeatRequest");
        MESSAGE_NAMES.put(HEARTBEAT_RESPONSE, "HeartbeatResponse");
        MESSAGE_NAMES.put(LOGIN_REQUEST, "LoginRequest");
        MESSAGE_NAMES.put(LOGIN_RESPONSE, "LoginResponse");
        MESSAGE_NAMES.put(MATCH_REQUEST, "MatchRequest");
        MESSAGE_NAMES.put(MATCH_RESPONSE, "MatchResponse");
        MESSAGE_NAMES.put(MATCH_CANCEL_REQUEST, "MatchCancelRequest");
        MESSAGE_NAMES.put(BATTLE_ENTER_REQUEST, "BattleEnterRequest");
        MESSAGE_NAMES.put(BATTLE_ENTER_RESPONSE, "BattleEnterResponse");
        MESSAGE_NAMES.put(PLAYER_ACTION_REQUEST, "PlayerActionRequest");
        MESSAGE_NAMES.put(BATTLE_STATE_UPDATE, "BattleStateUpdate");
        MESSAGE_NAMES.put(SKILL_CAST_REQUEST, "SkillCastRequest");
        MESSAGE_NAMES.put(BATTLE_END_NOTIFY, "BattleEndNotify");
        MESSAGE_NAMES.put(RECONNECT_REQUEST, "ReconnectRequest");
        MESSAGE_NAMES.put(RECONNECT_RESPONSE, "ReconnectResponse");
    }

    public static String getMessageName(int messageId) {
        return MESSAGE_NAMES.getOrDefault(messageId, "Unknown(0x" + Integer.toHexString(messageId) + ")");
    }
}
