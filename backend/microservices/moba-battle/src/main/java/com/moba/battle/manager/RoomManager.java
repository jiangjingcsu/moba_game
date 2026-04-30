package com.moba.battle.manager;

import com.moba.battle.battle.GridCollisionDetector;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.battle.SkillCollisionSystem;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.*;
import com.moba.battle.monitor.ServerMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RoomManager {
    private final Map<String, BattleRoom> rooms;
    private final Map<Long, String> playerToRoom;
    private final ScheduledExecutorService tickScheduler;

    private final int maxRoomsPerProcess;
    private final String serverId;

    public RoomManager() {
        this.rooms = new ConcurrentHashMap<>();
        this.playerToRoom = new ConcurrentHashMap<>();
        this.tickScheduler = Executors.newScheduledThreadPool(1);
        this.maxRoomsPerProcess = 100;
        this.serverId = "BATTLE_SERVER_" + System.currentTimeMillis();

        startTickLoop();
    }

    public static RoomManager getInstance() {
        return SpringContextHolder.getBean(RoomManager.class);
    }

    private void startTickLoop() {
        tickScheduler.scheduleAtFixedRate(() -> {
            long tickStart = System.currentTimeMillis();
            for (BattleRoom room : new ArrayList<>(rooms.values())) {
                try {
                    room.tick();
                    if (room.isRunning()) {
                        MapManager.getInstance().updateMap(room.getBattleId(), room.getEngine().getCurrentFrame());
                    }
                } catch (Exception e) {
                    log.error("Error ticking room: {}", room.getBattleId(), e);
                }
            }
            long tickDuration = System.currentTimeMillis() - tickStart;

            ServerMonitor monitor = ServerMonitor.getInstance();
            monitor.recordTick(tickDuration);
            monitor.updateRoomCount(rooms.size());
            monitor.updatePlayerCount(playerToRoom.size());
        }, 0, 66, TimeUnit.MILLISECONDS);
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
            MapManager.getInstance().removeMap(battleId);
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
}

