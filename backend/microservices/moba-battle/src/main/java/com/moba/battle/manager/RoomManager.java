package com.moba.battle.manager;

import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.FrameInput;
import com.moba.battle.model.HeroConfig;
import com.moba.battle.model.MOBAMap;
import com.moba.battle.model.Player;
import com.moba.battle.monitor.ServerMonitor;
import com.moba.battle.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RoomManager implements DisposableBean {
    private final Map<Long, BattleRoom> rooms;
    private final Map<Long, Long> userToRoom;
    private final ScheduledExecutorService scheduler;
    private final int tickRate;
    private final int maxRooms;
    private final int maxPlayersPerRoom;
    private final int defaultGridSize;
    private final String serverId;
    private final int loadingTimeoutSeconds;

    private final MapManager mapManager;
    private final ServerMonitor serverMonitor;

    public RoomManager(ServerConfig serverConfig,
                       @Lazy MapManager mapManager,
                       ServerMonitor serverMonitor) {
        this.rooms = new ConcurrentHashMap<>();
        this.userToRoom = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.tickRate = serverConfig.getTickRate();
        this.maxRooms = serverConfig.getMaxRooms();
        this.maxPlayersPerRoom = serverConfig.getMaxPlayersPerRoom();
        this.defaultGridSize = serverConfig.getDefaultGridSize();
        this.serverId = "BATTLE_SERVER_" + serverConfig.getPort();
        this.loadingTimeoutSeconds = serverConfig.getLoadingTimeoutSeconds();

        this.mapManager = mapManager;
        this.serverMonitor = serverMonitor;

        scheduler.scheduleAtFixedRate(this::tickAllRooms, 0, 1000 / tickRate, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::updateMaps, 0, 200, TimeUnit.MILLISECONDS);
        log.info("RoomManager初始化完成: tickRate={}, maxRooms={}, maxPlayers={}", tickRate, maxRooms, maxPlayersPerRoom);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public BattleRoom createRoom(long battleId, List<Long> userIds, int teamCount) {
        if (rooms.size() >= maxRooms) {
            log.warn("房间数已达上限({}), 无法创建新房间", maxRooms);
            return null;
        }

        BattleRoom room = new BattleRoom(battleId, teamCount, maxPlayersPerRoom, serverId, loadingTimeoutSeconds);
        rooms.put(battleId, room);

        for (Long userId : userIds) {
            userToRoom.put(userId, battleId);
        }

        BattleSession session = room.getSession();
        int playersPerTeam = teamCount > 0 ? Math.max(1, userIds.size() / teamCount) : 1;
        for (int i = 0; i < userIds.size(); i++) {
            long userId = userIds.get(i);
            int teamId = playersPerTeam > 0 ? i / playersPerTeam : 0;
            if (teamId >= teamCount) teamId = teamCount - 1;

            HeroConfig heroConfig = HeroConfig.getHeroConfig(1);
            BattlePlayer bp = new BattlePlayer(userId, 1, teamId, heroConfig);
            session.addPlayer(userId, bp);
        }

        log.info("创建战斗房间: battleId={}, 玩家数={}, 队伍数={}", battleId, userIds.size(), teamCount);
        return room;
    }

    public BattleRoom getRoom(long battleId) {
        return rooms.get(battleId);
    }

    public BattleRoom getPlayerRoom(long userId) {
        Long battleId = userToRoom.get(userId);
        return battleId != null ? rooms.get(battleId) : null;
    }

    public Collection<BattleRoom> getAllRooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public void removeRoom(long battleId) {
        BattleRoom room = rooms.remove(battleId);
        if (room != null) {
            for (Long userId : room.getSession().getBattlePlayers().keySet()) {
                userToRoom.remove(userId);
            }
            mapManager.removeMap(battleId);
            log.info("移除战斗房间: {}", battleId);
        }
    }

    public int getRoomCount() {
        return rooms.size();
    }

    public int getTotalPlayers() {
        return userToRoom.size();
    }

    private void tickAllRooms() {
        long startTime = System.nanoTime();
        int tickedRooms = 0;

        for (BattleRoom room : rooms.values()) {
            if (room.isRunning()) {
                try {
                    room.getEngine().tick();
                    tickedRooms++;
                } catch (Exception e) {
                    log.error("房间{}tick异常", room.getBattleId(), e);
                }
            }
        }

        long elapsed = System.nanoTime() - startTime;
        serverMonitor.recordTick((long)(elapsed / 1_000_000.0));
    }

    private void updateMaps() {
        for (BattleRoom room : rooms.values()) {
            if (room.isRunning()) {
                try {
                    mapManager.updateMap(room.getBattleId(), room.getEngine().getCurrentFrame());
                } catch (Exception e) {
                    log.error("房间{}地图更新异常", room.getBattleId(), e);
                }
            }
        }
    }
}
