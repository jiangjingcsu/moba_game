package com.moba.battleserver.manager;

import com.moba.battleserver.battle.LockstepEngine;
import com.moba.battleserver.model.BattleSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;

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
    private int spectatorCount;
    private com.moba.battleserver.model.MOBAMap.GameMode gameMode;
    private ScheduledFuture<?> tickFuture;

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
        this.spectatorCount = 0;
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
        if (tickFuture != null) {
            tickFuture.cancel(false);
            tickFuture = null;
        }
        log.info("Room {} stopped", battleId);
    }

    public void addSpectator() {
        spectatorCount++;
    }

    public void removeSpectator() {
        if (spectatorCount > 0) {
            spectatorCount--;
        }
    }

    public boolean isRunning() {
        return state == RoomState.RUNNING;
    }

    public int getPlayerCount() {
        return session.getPlayerCount();
    }
}
