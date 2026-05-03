package com.moba.battle.model;

import com.moba.netty.user.User;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class Player {
    private long userId;
    private String playerName;
    private int level;
    private int rank;
    private int rankScore;
    private User user;
    private PlayerState state;
    private long currentBattleId;
    private long lastHeartbeatTime;
    private Map<String, Integer> heroStats;
    private boolean isReconnecting;

    public enum PlayerState {
        OFFLINE,
        ONLINE,
        MATCHING,
        IN_BATTLE
    }

    public Player() {
        this.state = PlayerState.OFFLINE;
        this.heroStats = new ConcurrentHashMap<>();
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.isReconnecting = false;
    }

    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public boolean isInBattle() {
        return state == PlayerState.IN_BATTLE;
    }

    public boolean isMatching() {
        return state == PlayerState.MATCHING;
    }

    public boolean isConnected() {
        return user != null && user.isActive();
    }

    public void sendToClient(Object msg) {
        if (user != null && user.isActive()) {
            if (msg instanceof com.moba.netty.protocol.MessagePacket) {
                user.send((com.moba.netty.protocol.MessagePacket) msg);
            }
        }
    }
}
