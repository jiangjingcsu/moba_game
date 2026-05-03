package com.moba.battle.manager;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.Creep;
import com.moba.battle.model.MOBAMap;
import com.moba.battle.model.MOBAMap.Building;
import com.moba.battle.model.MOBAMap.CreepCamp;
import com.moba.battle.model.MOBAMap.RuneSpawnPoint;
import com.moba.battle.model.MOBAMap.SpawnPoint;
import com.moba.battle.model.MOBAMap.Tower;
import com.moba.battle.model.Player;
import com.moba.common.constant.GameMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MapManager {
    private final Map<Long, MOBAMap> activeMaps;
    private final Map<Long, List<Creep>> roomCreeps;
    private final Map<Long, Map<Integer, Tower>> roomTowers;
    private final Map<Long, Map<Integer, Building>> roomBuildings;
    private final Map<Long, List<RuneSpawnPoint>> roomRunes;

    private final long creepSpawnIntervalMs;
    private final long runeSpawnIntervalMs;
    private final int aggroRange;

    private final BattleManager battleManager;

    public MapManager(ServerConfig serverConfig, @Lazy BattleManager battleManager) {
        this.activeMaps = new ConcurrentHashMap<>();
        this.roomCreeps = new ConcurrentHashMap<>();
        this.roomTowers = new ConcurrentHashMap<>();
        this.roomBuildings = new ConcurrentHashMap<>();
        this.roomRunes = new ConcurrentHashMap<>();
        this.creepSpawnIntervalMs = serverConfig.getCreepSpawnIntervalMs();
        this.runeSpawnIntervalMs = serverConfig.getRuneSpawnIntervalMs();
        this.aggroRange = serverConfig.getAggroRange();
        this.battleManager = battleManager;
    }

    public MOBAMap createMap(long battleId, int mapId, GameMode mode) {
        MOBAMap map = MOBAMap.createMap(mapId, mode);
        activeMaps.put(battleId, map);

        roomTowers.put(battleId, new ConcurrentHashMap<>());
        for (Tower tower : map.getTowers()) {
            roomTowers.get(battleId).put(tower.getTowerId(), tower);
        }

        roomBuildings.put(battleId, new ConcurrentHashMap<>());
        for (Building building : map.getBuildings()) {
            roomBuildings.get(battleId).put(building.getBuildingId(), building);
        }

        roomRunes.put(battleId, new ArrayList<>(map.getRuneSpawns()));
        for (RuneSpawnPoint rune : roomRunes.get(battleId)) {
            rune.setActive(true);
            rune.setLastSpawnTime(System.currentTimeMillis());
        }

        spawnInitialCreeps(battleId, map);

        log.info("为战斗{}创建地图: {}（模式={}, {}座防御塔, {}座建筑, {}个野怪）",
                battleId, map.getMapName(), mode,
                map.getTowers().size(), map.getBuildings().size(),
                roomCreeps.getOrDefault(battleId, Collections.emptyList()).size());

        return map;
    }

    private void spawnInitialCreeps(long battleId, MOBAMap map) {
        List<Creep> creeps = new ArrayList<>();
        long creepIdBase = battleId * 10000;
        int creepIndex = 0;

        for (CreepCamp camp : map.getCreepCamps()) {
            for (int i = 0; i < camp.getCreepCount(); i++) {
                long creepId = creepIdBase + creepIndex++;
                Creep.CreepType type = camp.getCampType() == CreepCamp.CreepCampType.SMALL ?
                        Creep.CreepType.MELEE : Creep.CreepType.RANGED;

                int offsetX = (i % 2 == 0 ? 1 : -1) * (i * 30);
                int offsetY = (i / 2) * 30;

                Creep creep = Creep.createCreep(
                        creepId, camp.getCampId(), -1,
                        camp.getX() + offsetX, camp.getY() + offsetY,
                        type, camp.getCreepLevel()
                );

                creeps.add(creep);
            }
        }

        for (int team = 0; team < map.getTeamCount(); team++) {
            SpawnPoint spawn = map.getSpawnPoint(team);
            if (spawn == null) continue;

            int creepsPerWave = map.getGameMode() == GameMode.MODE_5V5 ? 5 : 3;

            for (int i = 0; i < creepsPerWave; i++) {
                long creepId = creepIdBase + creepIndex++;
                Creep.CreepType type = i < 4 ? Creep.CreepType.MELEE : Creep.CreepType.RANGED;

                int offsetX = (i % 2 == 0 ? 50 : -50) * (i / 2 + 1);
                int offsetY = 100 + (i * 40);

                Creep creep = Creep.createCreep(
                        creepId, 0, team,
                        spawn.getX() + offsetX, spawn.getY() + offsetY,
                        type, 1
                );

                creeps.add(creep);
            }
        }

        roomCreeps.put(battleId, creeps);
    }

    public void updateMap(long battleId, long currentFrame) {
        MOBAMap map = activeMaps.get(battleId);
        if (map == null) return;

        updateTowers(battleId, currentFrame);
        updateCreeps(battleId, map, currentFrame);
        checkRuneSpawns(battleId, currentFrame);
        checkCreepRespawns(battleId, map);
    }

    private void updateTowers(long battleId, long currentFrame) {
        Map<Integer, Tower> towers = roomTowers.get(battleId);
        if (towers == null) return;

        for (Tower tower : towers.values()) {
            if (tower.getHp() <= 0) continue;

            if (tower.getCurrentTarget() != null) {
                BattlePlayer target = battleManager
                        .getBattleRoom(battleId)
                        .getSession()
                        .getPlayer(tower.getCurrentTarget());

                if (target == null || target.isDead() ||
                        target.getTeamId() == tower.getTeamId()) {
                    tower.setCurrentTarget(null);
                }
            }

            if (tower.getCurrentTarget() == null) {
                BattlePlayer nearestEnemy = findNearestEnemyInRange(
                        battleId, tower, tower.getTeamId());
                if (nearestEnemy != null) {
                    tower.setCurrentTarget(nearestEnemy.getUserId());
                }
            }

            if (tower.getCurrentTarget() != null && tower.canAttack(currentFrame)) {
                BattlePlayer target = battleManager
                        .getBattleRoom(battleId)
                        .getSession()
                        .getPlayer(tower.getCurrentTarget());

                if (target != null) {
                    target.takeDamage(tower.getDamage());
                    tower.attack(currentFrame);

                    log.debug("防御塔{}攻击玩家{}, 伤害={},范围={}",
                            tower.getTowerId(), target.getUserId(), tower.getDamage(), tower.getRange());
                }
            }
        }
    }

    private BattlePlayer findNearestEnemyInRange(long battleId, Tower tower, int myTeamId) {
        BattleRoom room = battleManager.getBattleRoom(battleId);
        if (room == null) return null;

        BattlePlayer nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (BattlePlayer player : room.getSession().getBattlePlayers().values()) {
            if (player.getTeamId() == myTeamId || player.isDead()) continue;

            int dx = player.getPosition().x - tower.getX();
            int dy = player.getPosition().y - tower.getY();
            int dist = (int) Math.sqrt(dx * dx + dy * dy);

            if (dist <= tower.getRange() && dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    private void updateCreeps(long battleId, MOBAMap map, long currentFrame) {
        List<Creep> creeps = roomCreeps.get(battleId);
        if (creeps == null) return;

        for (Creep creep : creeps) {
            if (!creep.isAlive()) continue;

            switch (creep.getState()) {
                case IDLE:
                    if (!creep.getWaypoints().isEmpty()) {
                        creep.setState(Creep.CreepState.MOVING);
                    }
                    break;

                case MOVING:
                    updateCreepMovement(creep, battleId, currentFrame);
                    break;

                case ATTACKING:
                    if (creep.getCurrentTarget() == null) {
                        creep.setState(Creep.CreepState.MOVING);
                    } else {
                        BattlePlayer target = battleManager
                                .getBattleRoom(battleId)
                                .getSession()
                                .getPlayer(creep.getCurrentTarget());

                        if (target == null || target.isDead() || target.getTeamId() == creep.getTeamId()) {
                            creep.setCurrentTarget(null);
                            creep.setState(Creep.CreepState.MOVING);
                        } else if (creep.canAttack(currentFrame)) {
                            int dist = creep.distanceTo(target.getPosition().x, target.getPosition().y);
                            if (dist <= creep.getAttackRange()) {
                                target.takeDamage(creep.getDamage());
                                creep.attack(currentFrame);
                            } else {
                                creep.setState(Creep.CreepState.MOVING);
                            }
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }

    private void updateCreepMovement(Creep creep, long battleId, long currentFrame) {
        BattleRoom room = battleManager.getBattleRoom(battleId);
        if (room == null) return;

        BattlePlayer nearestEnemy = findNearestEnemyInCreepRange(room, creep);
        if (nearestEnemy != null) {
            int dist = creep.distanceTo(nearestEnemy.getPosition().x, nearestEnemy.getPosition().y);
            if (dist <= creep.getAttackRange()) {
                creep.setCurrentTarget(nearestEnemy.getUserId());
                creep.setState(Creep.CreepState.ATTACKING);
                return;
            }
        }

        if (!creep.getWaypoints().isEmpty()) {
            Creep.Waypoint wp = creep.getWaypoints().get(0);
            int dist = creep.distanceTo(wp.getX(), wp.getY());

            if (dist < 50) {
                if (wp.getWaitTime() > 0) {
                    creep.setWaitUntilFrame(System.currentTimeMillis() + wp.getWaitTime());
                }
                creep.getWaypoints().remove(0);
            } else {
                creep.moveTo(wp.getX(), wp.getY());
            }
        }
    }

    private BattlePlayer findNearestEnemyInCreepRange(BattleRoom room, Creep creep) {
        BattlePlayer nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (BattlePlayer player : room.getSession().getBattlePlayers().values()) {
            if (player.isDead()) continue;

            int dx = player.getPosition().x - creep.getX();
            int dy = player.getPosition().y - creep.getY();
            int dist = (int) Math.sqrt(dx * dx + dy * dy);

            if (dist <= aggroRange && dist < nearestDist) {
                if (creep.getTeamId() >= 0 && player.getTeamId() == creep.getTeamId()) continue;
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    private void checkRuneSpawns(long battleId, long currentFrame) {
        List<RuneSpawnPoint> runes = roomRunes.get(battleId);
        if (runes == null) return;

        long now = System.currentTimeMillis();

        for (RuneSpawnPoint rune : runes) {
            if (!rune.isActive() && now - rune.getLastSpawnTime() >= rune.getSpawnInterval() * 1000) {
                rune.setActive(true);
                log.debug("符文{}刷新了{}, {})", rune.getRuneType(), rune.getX(), rune.getY());
            }
        }
    }

    private void checkCreepRespawns(long battleId, MOBAMap map) {
        List<Creep> creeps = roomCreeps.get(battleId);
        if (creeps == null) return;

        long now = System.currentTimeMillis();

        for (CreepCamp camp : map.getCreepCamps()) {
            for (Creep creep : creeps) {
                if (creep.getCampId() == camp.getCampId() && !creep.isAlive()) {
                    long respawnMs = camp.getRespawnTime() * 1000;
                    if (now - creep.getRespawnTime() >= respawnMs) {
                        int offsetX = (int) ((creep.getCreepId() % 3) * 30);
                        int offsetY = (int) ((creep.getCreepId() / 3) * 30);
                        creep.respawn(camp.getX() + offsetX, camp.getY() + offsetY);
                        log.debug("野怪{}重生了{}, {})", creep.getCreepId(), camp.getX(), camp.getY());
                    }
                }
            }
        }
    }

    public List<Creep> getCreeps(long battleId) {
        return roomCreeps.getOrDefault(battleId, Collections.emptyList());
    }

    public List<Creep> getAliveCreeps(long battleId) {
        List<Creep> all = roomCreeps.get(battleId);
        if (all == null) return Collections.emptyList();
        List<Creep> alive = new ArrayList<>();
        for (Creep c : all) {
            if (c.isAlive()) alive.add(c);
        }
        return alive;
    }

    public Tower getTower(long battleId, int towerId) {
        Map<Integer, Tower> towers = roomTowers.get(battleId);
        return towers != null ? towers.get(towerId) : null;
    }

    public Building getBuilding(long battleId, int buildingId) {
        Map<Integer, Building> buildings = roomBuildings.get(battleId);
        return buildings != null ? buildings.get(buildingId) : null;
    }

    public List<RuneSpawnPoint> getActiveRunes(long battleId) {
        List<RuneSpawnPoint> runes = roomRunes.get(battleId);
        if (runes == null) return Collections.emptyList();
        List<RuneSpawnPoint> active = new ArrayList<>();
        for (RuneSpawnPoint r : runes) {
            if (r.isActive()) active.add(r);
        }
        return active;
    }

    public MOBAMap getMap(long battleId) {
        return activeMaps.get(battleId);
    }

    public void removeMap(long battleId) {
        activeMaps.remove(battleId);
        roomCreeps.remove(battleId);
        roomTowers.remove(battleId);
        roomBuildings.remove(battleId);
        roomRunes.remove(battleId);
        log.info("战斗{}地图已移除", battleId);
    }

    public void applyTowerDamage(long battleId, int towerId, int damage) {
        Tower tower = getTower(battleId, towerId);
        if (tower != null) {
            tower.setHp(Math.max(0, tower.getHp() - damage));
            log.info("防御塔{}受到{}伤害, 生命值{}/{}", towerId, damage, tower.getHp(), tower.getMaxHp());
            if (tower.getHp() <= 0) {
                log.info("防御塔{}已被摧毁!", towerId);
            }
        }
    }

    public void applyBuildingDamage(long battleId, int buildingId, int damage) {
        Building building = getBuilding(battleId, buildingId);
        if (building != null) {
            building.setHp(Math.max(0, building.getHp() - damage));
            log.info("建筑{}受到{}伤害, 生命值{}/{}", buildingId, damage, building.getHp(), building.getMaxHp());
            if (building.getHp() <= 0) {
                log.info("建筑{}已被摧毁!", buildingId);
            }
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String getMapStateJson(long battleId) {
        MOBAMap map = getMap(battleId);
        if (map == null) return "{}";

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("mapId", map.getMapId());
            result.put("mapName", map.getMapName());
            result.put("mode", map.getGameMode());
            result.put("width", map.getWidth());
            result.put("height", map.getHeight());

            if (map.getTeamSpawns().size() >= 2) {
                List<Map<String, Object>> spawns = new ArrayList<>();
                for (SpawnPoint sp : map.getTeamSpawns()) {
                    Map<String, Object> spawn = new HashMap<>();
                    spawn.put("team", sp.getTeamId());
                    spawn.put("x", sp.getX());
                    spawn.put("y", sp.getY());
                    spawns.add(spawn);
                }
                result.put("spawns", spawns);
            }

            List<Tower> towers = roomTowers.getOrDefault(battleId, Collections.emptyMap()).values().stream().toList();
            List<Map<String, Object>> towerList = new ArrayList<>();
            for (Tower t : towers) {
                Map<String, Object> tower = new HashMap<>();
                tower.put("id", t.getTowerId());
                tower.put("team", t.getTeamId());
                tower.put("x", t.getX());
                tower.put("y", t.getY());
                tower.put("hp", t.getHp());
                tower.put("maxHp", t.getMaxHp());
                tower.put("tier", t.getTier());
                towerList.add(tower);
            }
            result.put("towers", towerList);

            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("序列化地图状态失败: battleId={}", battleId, e);
            return "{}";
        }
    }
}
