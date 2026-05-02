package com.moba.battle.manager;

import com.moba.battle.battle.GridCollisionDetector;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.battle.SkillCollisionSystem;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.*;
import com.moba.battle.monitor.ServerMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RoomManager {
    private final Map<String, BattleRoom> rooms;
    private final Map<Long, String> playerToRoom;

    private final int tickPoolSize;
    private final ExecutorService tickPool;
    private final ScheduledExecutorService tickScheduler;

    private final int maxRoomsPerProcess;
    private final int tickIntervalMs;
    private final int defaultGridSize;
    private final String serverId;

    public RoomManager(ServerConfig serverConfig) {
        this.rooms = new ConcurrentHashMap<>();
        this.playerToRoom = new ConcurrentHashMap<>();

        int cpus = Runtime.getRuntime().availableProcessors();
        this.tickPoolSize = Math.max(2, cpus);
        this.tickPool = Executors.newFixedThreadPool(tickPoolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "battle-tick-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        this.tickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "battle-tick-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.maxRoomsPerProcess = serverConfig.getMaxRooms();
        this.tickIntervalMs = serverConfig.getTickIntervalMs();
        this.defaultGridSize = serverConfig.getDefaultGridSize();
        this.serverId = "BATTLE_SERVER_" + System.currentTimeMillis();

        startTickLoop();
    }

    public static RoomManager getInstance() {
        return SpringContextHolder.getBean(RoomManager.class);
    }

    private void startTickLoop() {
        tickScheduler.scheduleAtFixedRate(() -> {
            long tickStart = System.currentTimeMillis();
            Collection<BattleRoom> runningRooms = new ArrayList<>();
            for (BattleRoom room : rooms.values()) {
                if (room.isRunning()) {
                    runningRooms.add(room);
                }
            }

            if (runningRooms.isEmpty()) return;

            CountDownLatch latch = new CountDownLatch(runningRooms.size());

            for (BattleRoom room : runningRooms) {
                tickPool.submit(() -> {
                    try {
                        room.tick();
                        MapManager.getInstance().updateMap(room.getBattleId(), room.getEngine().getCurrentFrame());
                    } catch (Exception e) {
                        log.error("房间Tick异常: {}", room.getBattleId(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                boolean completed = latch.await(tickIntervalMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                log.warn("Tick周期未在{}ms内完成, 部分房间可能存在延迟", tickIntervalMs);
            }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long tickDuration = System.currentTimeMillis() - tickStart;

            ServerMonitor monitor = ServerMonitor.getInstance();
            monitor.recordTick(tickDuration);
            monitor.updateRoomCount(rooms.size());
            monitor.updatePlayerCount(playerToRoom.size());

            if (tickDuration > tickIntervalMs) {
                log.warn("Tick周期耗时{}ms (预算={}ms), 房间数={}, 线程池大小={}",
                        tickDuration, tickIntervalMs, runningRooms.size(), tickPoolSize);
            }
        }, 0, tickIntervalMs, TimeUnit.MILLISECONDS);
    }

    public BattleRoom createRoom(String battleId, List<Long> playerIds, int teamCount) {
        if (rooms.size() >= maxRoomsPerProcess) {
            log.error("服务器已达最大容量: {}个房间", maxRoomsPerProcess);
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

        GridCollisionDetector gridDetector = new GridCollisionDetector(16000, 16000, defaultGridSize);
        SkillCollisionSystem collisionSystem = new SkillCollisionSystem(session, gridDetector);
        engine.setCollisionSystem(collisionSystem);

        BattleRoom room = new BattleRoom(battleId, session, engine, serverId);

        rooms.put(battleId, room);
        for (Long playerId : playerIds) {
            playerToRoom.put(playerId, battleId);
        }

        log.info("房间已创建: {} 服务器={}, 玩家数: {}", battleId, serverId, playerIds.size());
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
            log.info("房间已移除: {}", battleId);
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
        tickPool.shutdown();
        try {
            if (!tickPool.awaitTermination(5, TimeUnit.SECONDS)) {
                tickPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            tickPool.shutdownNow();
        }
        log.info("RoomManager关闭完成");
    }
}
