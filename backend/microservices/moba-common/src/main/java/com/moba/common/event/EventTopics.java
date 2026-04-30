package com.moba.common.event;

public final class EventTopics {

    private EventTopics() {
    }

    public static final String MATCH_SUCCESS = "match-success";
    public static final String MATCH_CANCEL = "match-cancel";
    public static final String MATCH_TIMEOUT = "match-timeout";
    public static final String BATTLE_START = "battle-start";
    public static final String BATTLE_END = "battle-end";
    public static final String PLAYER_ONLINE = "player-online";
    public static final String PLAYER_OFFLINE = "player-offline";
    public static final String BATTLE_STATE_CHANGE = "battle-state-change";
}
