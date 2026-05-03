package com.moba.battle.manager;

import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.common.constant.GameMode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Data
@Slf4j
public class BattleRoom {
    private final long battleId;
    private final BattleSession session;
    private final LockstepEngine engine;
    private final String serverId;
    private final AtomicReference<RoomState> stateRef;
    private long createTime;
    private long startTime;
    private long endTime;
    private final AtomicInteger spectatorCount = new AtomicInteger(0);
    private GameMode gameMode;
    private boolean aiMode;

    private final Set<Long> humanUserIds;
    private final Set<Long> readyUserIds;
    private final Set<Long> botUserIds;
    private volatile long loadingDeadline;
    private final int loadingTimeoutSeconds;

    public enum RoomState {
        WAITING,
        LOADING,
        COUNTDOWN,
        RUNNING,
        FINISHED,
        DESTROYED
    }

    public BattleRoom(long battleId, int teamCount, int maxPlayersPerRoom, String serverId, int loadingTimeoutSeconds) {
        this.battleId = battleId;
        this.session = new BattleSession(battleId, teamCount, maxPlayersPerRoom);
        this.engine = new LockstepEngine(battleId, session, 20, 2, 60);
        this.serverId = serverId;
        this.stateRef = new AtomicReference<>(RoomState.WAITING);
        this.createTime = System.currentTimeMillis();
        this.humanUserIds = ConcurrentHashMap.newKeySet();
        this.readyUserIds = ConcurrentHashMap.newKeySet();
        this.botUserIds = ConcurrentHashMap.newKeySet();
        this.loadingTimeoutSeconds = loadingTimeoutSeconds;
    }

    public RoomState getState() {
        return stateRef.get();
    }

    public void setState(RoomState newState) {
        stateRef.set(newState);
    }

    public void tick() {
        if (stateRef.get() != RoomState.RUNNING) return;
        engine.tick();
    }

    public void start() {
        stateRef.set(RoomState.RUNNING);
        startTime = System.currentTimeMillis();
        session.start();
        engine.start();
        log.info("房间{}已开始, {}名真人玩家, {}名机器人",
                battleId, humanUserIds.size(), botUserIds.size());
    }

    public void stop() {
        stateRef.set(RoomState.DESTROYED);
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
        return stateRef.get() == RoomState.RUNNING;
    }

    public int getPlayerCount() {
        return session.getPlayerCount();
    }

    public void registerHumanPlayer(long userId) {
        humanUserIds.add(userId);
        log.debug("真人玩家已注册: {} 在房间{}", userId, battleId);
    }

    public void registerBotPlayer(long userId) {
        botUserIds.add(userId);
        log.debug("机器人玩家已注册: {} 在房间{}", userId, battleId);
    }

    public boolean markPlayerReady(long userId) {
        if (!humanUserIds.contains(userId)) {
            log.warn("非真人玩家尝试准备: {} 在房间{}", userId, battleId);
            return false;
        }
        boolean added = readyUserIds.add(userId);
        if (added) {
            log.info("玩家{}在房间{}已准备（{}/{}）",
                    userId, battleId, readyUserIds.size(), humanUserIds.size());
        }
        return added;
    }

    public boolean isPlayerReady(long userId) {
        return readyUserIds.contains(userId);
    }

    public boolean allHumanPlayersReady() {
        if (humanUserIds.isEmpty()) return true;
        return readyUserIds.size() >= humanUserIds.size();
    }

    public int getReadyCount() {
        return readyUserIds.size();
    }

    public int getExpectedHumanCount() {
        return humanUserIds.size();
    }

    public Set<Long> getUnreadyuserIds() {
        Set<Long> unready = ConcurrentHashMap.newKeySet();
        unready.addAll(humanUserIds);
        unready.removeAll(readyUserIds);
        return unready;
    }

    public void setLoadingDeadline(long deadlineMs) {
        this.loadingDeadline = deadlineMs;
    }

    public boolean isLoadingTimeout() {
        return loadingDeadline > 0 && System.currentTimeMillis() > loadingDeadline;
    }

    public void transitionToLoading() {
        if (!stateRef.compareAndSet(RoomState.WAITING, RoomState.LOADING)) {
            log.warn("房间{}无法转入加载状态, 当前状态={}", battleId, stateRef.get());
            return;
        }
        session.setState(BattleSession.BattleState.LOADING);
        setLoadingDeadline(System.currentTimeMillis() + loadingTimeoutSeconds * 1000L);
        log.info("房间{}转入加载状态, 预期{}名真人玩家, 超时={}秒",
                battleId, humanUserIds.size(), loadingTimeoutSeconds);
    }

    public void transitionToCountdown() {
        if (!stateRef.compareAndSet(RoomState.LOADING, RoomState.COUNTDOWN)) {
            log.warn("房间{}无法转入倒计时状态, 当前状态={}", battleId, stateRef.get());
            return;
        }
        log.info("房间{}转入倒计时状态, 所有{}名真人玩家已准备",
                battleId, humanUserIds.size());
    }
}
