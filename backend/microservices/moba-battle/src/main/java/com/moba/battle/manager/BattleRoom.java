package com.moba.battle.manager;

import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.MOBAMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Set<Long> humanPlayerIds;
    private final Set<Long> readyPlayerIds;
    private final Set<Long> botPlayerIds;
    private volatile long loadingDeadline;

    public enum RoomState {
        WAITING,
        LOADING,
        COUNTDOWN,
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
        this.humanPlayerIds = ConcurrentHashMap.newKeySet();
        this.readyPlayerIds = ConcurrentHashMap.newKeySet();
        this.botPlayerIds = ConcurrentHashMap.newKeySet();
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
        log.info("房间{}已开始, {}名真人玩家, {}名机器人",
                battleId, humanPlayerIds.size(), botPlayerIds.size());
    }

    public void stop() {
        state = RoomState.DESTROYED;
        engine.stop();
        log.info("房间{}已停止", battleId);
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

    public void registerHumanPlayer(long playerId) {
        humanPlayerIds.add(playerId);
        log.debug("真人玩家已注册: {} 在房间{}", playerId, battleId);
    }

    public void registerBotPlayer(long playerId) {
        botPlayerIds.add(playerId);
        log.debug("机器人玩家已注册: {} 在房间{}", playerId, battleId);
    }

    public boolean markPlayerReady(long playerId) {
        if (!humanPlayerIds.contains(playerId)) {
            log.warn("非真人玩家尝试准备: {} 在房间{}", playerId, battleId);
            return false;
        }
        boolean added = readyPlayerIds.add(playerId);
        if (added) {
            log.info("玩家{}在房间{}已准备（{}/{}）",
                    playerId, battleId, readyPlayerIds.size(), humanPlayerIds.size());
        }
        return added;
    }

    public boolean isPlayerReady(long playerId) {
        return readyPlayerIds.contains(playerId);
    }

    public boolean allHumanPlayersReady() {
        if (humanPlayerIds.isEmpty()) return true;
        return readyPlayerIds.size() >= humanPlayerIds.size();
    }

    public int getReadyCount() {
        return readyPlayerIds.size();
    }

    public int getExpectedHumanCount() {
        return humanPlayerIds.size();
    }

    public Set<Long> getUnreadyPlayerIds() {
        Set<Long> unready = ConcurrentHashMap.newKeySet();
        unready.addAll(humanPlayerIds);
        unready.removeAll(readyPlayerIds);
        return unready;
    }

    public void setLoadingDeadline(long deadlineMs) {
        this.loadingDeadline = deadlineMs;
    }

    public boolean isLoadingTimeout() {
        return loadingDeadline > 0 && System.currentTimeMillis() > loadingDeadline;
    }

    public void transitionToLoading() {
        state = RoomState.LOADING;
        session.setState(BattleSession.BattleState.LOADING);
        ServerConfig config = SpringContextHolder.getBean(ServerConfig.class);
        int loadingTimeoutSeconds = config.getLoadingTimeoutSeconds();
        setLoadingDeadline(System.currentTimeMillis() + loadingTimeoutSeconds * 1000L);
        log.info("房间{}转入加载状态, 预期{}名真人玩家, 超时={}秒",
                battleId, humanPlayerIds.size(), loadingTimeoutSeconds);
    }

    public void transitionToCountdown() {
        state = RoomState.COUNTDOWN;
        log.info("房间{}转入倒计时状态, 所有{}名真人玩家已准备",
                battleId, humanPlayerIds.size());
    }
}
