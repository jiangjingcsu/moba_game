package com.moba.battle.model;

import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class Player {
    private long playerId;
    private String playerName;
    private int level;
    private int rank;
    private int rankScore;
    private ChannelHandlerContext ctx;
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
        return ctx != null && ctx.channel().isActive();
    }

    public void sendToClient(Object msg) {
        if (ctx != null && ctx.channel().isActive()) {
            ctx.writeAndFlush(msg);
        }
    }
}
