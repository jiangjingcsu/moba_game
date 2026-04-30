package com.moba.battleserver.manager;

import com.moba.battleserver.battle.GridCollisionDetector;
import com.moba.battleserver.battle.LockstepEngine;
import com.moba.battleserver.battle.SkillCollisionSystem;
import com.moba.battleserver.manager.BattleRoom;
import com.moba.battleserver.model.*;
import com.moba.battleserver.monitor.ServerMonitor;
import com.moba.battleserver.service.BattleStateBroadcaster;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class RoomManager {
    private final Map<String, BattleRoom> rooms;
    private final Map<Long, String> playerToRoom;
    private final ScheduledExecutorService tickScheduler;
    private final BattleStateBroadcaster stateBroadcaster;
    private final MapManager mapManager;
    private final ServerMonitor serverMonitor;

    private final int maxRoomsPerProcess;
    private final String serverId;

    public RoomManager(BattleStateBroadcaster stateBroadcaster, MapManager mapManager, ServerMonitor serverMonitor) {
        this.rooms = new ConcurrentHashMap<>();
        this.playerToRoom = new ConcurrentHashMap<>();
        this.tickScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        this.stateBroadcaster = stateBroadcaster;
        this.mapManager = mapManager;
        this.serverMonitor = serverMonitor;
        this.maxRoomsPerProcess = 100;
        this.serverId = "BATTLE_SERVER_" + System.currentTimeMillis();
    }

    public BattleRoom createRoom(String battleId, List<Long> playerIds, int teamCount) {
        if (rooms.size() >= maxRoomsPerProcess) {
            log.error("Server at max capacity: {} rooms", maxRoomsPerProcess);
            return null;
        }

        BattleSession session = new BattleSession(battleId, teamCount, 1);

        int playersPerTeam = playerIds.size() / teamCount;
        int heroId = 1;

        for (int i = 0; i < playerIds.size(); i++) {
            Long playerId = playerIds.get(i);
            int teamId = i / playersPerTeam;
            HeroConfig heroConfig = HeroConfig.getHeroConfig(heroId);
            BattlePlayer battlePlayer = new BattlePlayer(playerId, heroId, teamId, heroConfig);
            session.addPlayer(playerId, battlePlayer);

            HeroConfig nextHero = HeroConfig.getHeroConfig((heroId % 6) + 1);
            battlePlayer.getSkills().put(1, createDefaultSkill(1, nextHero));
            battlePlayer.getSkills().put(2, createDefaultSkill(2, nextHero));
            battlePlayer.getSkills().put(3, createDefaultSkill(3, nextHero));

            heroId = (heroId % 6) + 1;
        }

        LockstepEngine engine = new LockstepEngine(battleId, session);

        GridCollisionDetector gridDetector = new GridCollisionDetector(16000, 16000, 200);
        SkillCollisionSystem collisionSystem = new SkillCollisionSystem(session, gridDetector);
        engine.setCollisionSystem(collisionSystem);

        BattleRoom room = new BattleRoom(battleId, session, engine, serverId);

        rooms.put(battleId, room);
        for (Long playerId : playerIds) {
            playerToRoom.put(playerId, battleId);
        }

        log.info("Room created: {} on server {}, players: {}", battleId, serverId, playerIds.size());
        return room;
    }

    private BattlePlayer.Skill createDefaultSkill(int skillId, HeroConfig heroConfig) {
        BattlePlayer.Skill skill = new BattlePlayer.Skill();
        skill.setSkillId(skillId);
        skill.setLevel(1);
        skill.setCooldown(3000 + skillId * 2000);
        skill.setMpCost(50 + skillId * 50);
        return skill;
    }

    public void startRoomTick(BattleRoom room) {
        final int tickIntervalMs = 66;
        room.setTickFuture(tickScheduler.scheduleAtFixedRate(() -> {
            if (!room.isRunning()) return;
            try {
                long tickStart = System.nanoTime();
                room.tick();
                mapManager.updateMap(room.getBattleId(), room.getEngine().getCurrentFrame());
                stateBroadcaster.broadcastFrameState(room);

                long tickDurationMs = (System.nanoTime() - tickStart) / 1_000_000;
                serverMonitor.recordTick(tickDurationMs);
            } catch (Exception e) {
                log.error("Error ticking room: {}", room.getBattleId(), e);
            }
        }, 0, tickIntervalMs, TimeUnit.MILLISECONDS));
        log.info("Room {} tick started", room.getBattleId());
    }

    public BattleRoom getRoom(String battleId) {
        return rooms.get(battleId);
    }

    public BattleRoom getPlayerRoom(long playerId) {
        String battleId = playerToRoom.get(playerId);
        if (battleId != null) {
            return rooms.get(battleId);
        }
        return null;
    }

    public void removeRoom(String battleId) {
        BattleRoom room = rooms.remove(battleId);
        if (room != null) {
            room.stop();
            ScheduledFuture<?> future = room.getTickFuture();
            if (future != null) {
                future.cancel(false);
            }
            mapManager.removeMap(battleId);
            for (Long playerId : room.getSession().getBattlePlayers().keySet()) {
                playerToRoom.remove(playerId);
            }
            log.info("Room removed: {}", battleId);
        }
    }

    public int getRoomCount() {
        return rooms.size();
    }

    public int getTotalPlayers() {
        return playerToRoom.size();
    }

    public String getServerId() {
        return serverId;
    }

    public boolean hasCapacity() {
        return rooms.size() < maxRoomsPerProcess;
    }

    public double getCpuUsage() {
        return (double) rooms.size() / maxRoomsPerProcess;
    }

    public Collection<BattleRoom> getAllRooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public void shutdown() {
        tickScheduler.shutdown();
        try {
            if (!tickScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                tickScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            tickScheduler.shutdownNow();
        }
    }
}
