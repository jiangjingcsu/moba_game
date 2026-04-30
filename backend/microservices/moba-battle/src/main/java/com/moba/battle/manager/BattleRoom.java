package com.moba.battle.manager;

import com.moba.battle.model.MOBAMap;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.model.BattleSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@Slf4j
public class BattleRoom {
    private final String battleId;
    private final BattleSession session;
    private final LockstepEngine engine;
    private final String serverId;
    private volatile RoomState state;
    private long createTime;
    private long startTime;
    private long endTime;
    private final AtomicInteger spectatorCount = new AtomicInteger(0);
    private MOBAMap.GameMode gameMode;

    public enum RoomState {
        WAITING,
        LOADING,
        RUNNING,
        FINISHED,
        DESTROYED
    }

    public BattleRoom(String battleId, BattleSession session, LockstepEngine engine, String serverId) {
        this.battleId = battleId;
        this.session = session;
        this.engine = engine;
        this.serverId = serverId;
        this.state = RoomState.WAITING;
        this.createTime = System.currentTimeMillis();
    }

    public void tick() {
        if (state != RoomState.RUNNING) return;
        engine.tick();
    }

    public void start() {
        state = RoomState.RUNNING;
        startTime = System.currentTimeMillis();
        session.start();
        engine.start();
        log.info("Room {} started", battleId);
    }

    public void stop() {
        state = RoomState.DESTROYED;
        engine.stop();
        log.info("Room {} stopped", battleId);
    }

    public void addSpectator() {
        spectatorCount.incrementAndGet();
    }

    public void removeSpectator() {
        spectatorCount.decrementAndGet();
    }

    public int getSpectatorCount() {
        return spectatorCount.get();
    }

    public boolean isRunning() {
        return state == RoomState.RUNNING;
    }

    public int getPlayerCount() {
        return session.getPlayerCount();
    }
}

