package com.moba.battleserver.manager;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.model.*;
import com.moba.battleserver.model.MOBAMap.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MapManager {
    private final Map<String, MOBAMap> activeMaps;
    private final Map<String, List<Creep>> roomCreeps;
    private final Map<String, Map<Integer, Tower>> roomTowers;
    private final Map<String, Map<Integer, Building>> roomBuildings;
    private final Map<String, List<RuneSpawnPoint>> roomRunes;

    private static final long CREEP_SPAWN_INTERVAL = 60000;
    private static final long RUNE_SPAWN_INTERVAL = 90000;

    public MapManager() {
        this.activeMaps = new ConcurrentHashMap<>();
        this.roomCreeps = new ConcurrentHashMap<>();
        this.roomTowers = new ConcurrentHashMap<>();
        this.roomBuildings = new ConcurrentHashMap<>();
        this.roomRunes = new ConcurrentHashMap<>();
    }

    public MOBAMap createMap(String battleId, int mapId, MOBAMap.GameMode mode) {
        MOBAMap map = MOBAMap.createMap(mapId, mode);
        activeMaps.put(battleId, map);

        roomCreeps.put(battleId, new ArrayList<>());
        roomTowers.put(battleId, new ConcurrentHashMap<>());
        roomBuildings.put(battleId, new ConcurrentHashMap<>());
        roomRunes.put(battleId, new ArrayList<>());

        initializeTowers(battleId, map);
        initializeBuildings(battleId, map);

        log.info("Map created for battle {}: mapId={}, mode={}, size={}x{}",
                battleId, mapId, mode, map.getWidth(), map.getHeight());
        return map;
    }

    private void initializeTowers(String battleId, MOBAMap map) {
        Map<Integer, Tower> towers = roomTowers.get(battleId);
        if (towers == null) return;
        int towerId = 1;
        for (MOBAMap.Lane lane : map.getLanes()) {
            Tower tower = new Tower();
            tower.setTowerId(towerId);
            tower.setTeamId((towerId % 3) + 1);
            tower.setPosition(new Position(500 * towerId, 500 * towerId));
            tower.setHp(5000);
            tower.setMaxHp(5000);
            tower.setAttackDamage(200);
            tower.setAttackRange(600);
            tower.setAlive(true);
            towers.put(towerId, tower);
            towerId++;
        }
    }

    private void initializeBuildings(String battleId, MOBAMap map) {
        Map<Integer, Building> buildings = roomBuildings.get(battleId);
        if (buildings == null) return;
        int buildingId = 1;
        for (int teamId = 1; teamId <= 3; teamId++) {
            Building building = new Building();
            building.setBuildingId(buildingId);
            building.setTeamId(teamId);
            building.setPosition(new Position(2000 * teamId, 2000 * teamId));
            building.setHp(10000);
            building.setMaxHp(10000);
            building.setAlive(true);
            building.setType(Building.BuildingType.BASE);
            buildings.put(buildingId, building);
            buildingId++;
        }
    }

    public MOBAMap getMap(String battleId) {
        return activeMaps.get(battleId);
    }

    public void updateMap(String battleId, int frame) {
        updateTowers(battleId, frame);
        updateCreeps(battleId, frame);
        updateRunes(battleId, frame);
    }

    private void updateTowers(String battleId, int frame) {
        Map<Integer, Tower> towers = roomTowers.get(battleId);
        if (towers == null) return;

        for (Tower tower : towers.values()) {
            if (!tower.isAlive()) continue;

            if (tower.getCurrentTarget() != null) {
                BattlePlayer target = ServiceLocator.getInstance().getBattleManager()
                        .getBattleRoom(battleId)
                        .getSession()
                        .getPlayer(tower.getCurrentTarget());
                if (target != null && !target.isDead()) {
                    double dist = Math.sqrt(
                            Math.pow(target.getPosition().x - tower.getPosition().x, 2) +
                            Math.pow(target.getPosition().y - tower.getPosition().y, 2));
                    if (dist <= tower.getAttackRange()) {
                        if (frame % 15 == 0) {
                            target.takeDamage(tower.getAttackDamage());
                        }
                    } else {
                        tower.setCurrentTarget(null);
                    }
                } else {
                    tower.setCurrentTarget(null);
                }
            }

            if (tower.getCurrentTarget() == null) {
                BattleRoom room = ServiceLocator.getInstance().getBattleManager().getBattleRoom(battleId);
                if (room == null) continue;
                for (BattlePlayer player : room.getSession().getBattlePlayers().values()) {
                    if (player.getTeamId() != tower.getTeamId() && !player.isDead()) {
                        double dist = Math.sqrt(
                                Math.pow(player.getPosition().x - tower.getPosition().x, 2) +
                                Math.pow(player.getPosition().y - tower.getPosition().y, 2));
                        if (dist <= tower.getAttackRange()) {
                            tower.setCurrentTarget(player.getPlayerId());
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updateCreeps(String battleId, int frame) {
        List<Creep> creeps = roomCreeps.get(battleId);
        if (creeps == null) return;

        long now = System.currentTimeMillis();
        if (creeps.isEmpty() && now - getLastCreepSpawnTime(battleId) > CREEP_SPAWN_INTERVAL) {
            spawnCreeps(battleId);
            setLastCreepSpawnTime(battleId, now);
        }

        creeps.removeIf(creep -> !creep.isAlive());
    }

    private final Map<String, Long> lastCreepSpawnTime = new ConcurrentHashMap<>();

    private long getLastCreepSpawnTime(String battleId) {
        return lastCreepSpawnTime.getOrDefault(battleId, 0L);
    }

    private void setLastCreepSpawnTime(String battleId, long time) {
        lastCreepSpawnTime.put(battleId, time);
    }

    private void spawnCreeps(String battleId) {
        MOBAMap map = activeMaps.get(battleId);
        if (map == null) return;

        List<Creep> creeps = roomCreeps.get(battleId);
        if (creeps == null) return;

        for (MOBAMap.Lane lane : map.getLanes()) {
            for (int teamId : new int[]{1, 2, 3}) {
                Creep creep = new Creep();
                creeps.add(creep);
            }
        }
    }

    private void updateRunes(String battleId, int frame) {
        List<RuneSpawnPoint> runes = roomRunes.get(battleId);
        if (runes == null) return;

        long now = System.currentTimeMillis();
        for (RuneSpawnPoint rune : runes) {
            if (!rune.isSpawned() && now - rune.getLastSpawnTime() > RUNE_SPAWN_INTERVAL) {
                rune.setSpawned(true);
                rune.setLastSpawnTime(now);
            }
        }
    }

    public String getMapStateJson(String battleId) {
        MOBAMap map = activeMaps.get(battleId);
        if (map == null) return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"mapId\":").append(map.getMapId());
        sb.append(",\"width\":").append(map.getWidth());
        sb.append(",\"height\":").append(map.getHeight());

        Map<Integer, Tower> towers = roomTowers.get(battleId);
        if (towers != null) {
            sb.append(",\"towers\":[");
            boolean first = true;
            for (Tower t : towers.values()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"id\":").append(t.getTowerId());
                sb.append(",\"teamId\":").append(t.getTeamId());
                sb.append(",\"hp\":").append(t.getHp());
                sb.append(",\"alive\":").append(t.isAlive());
                sb.append("}");
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    public void removeMap(String battleId) {
        activeMaps.remove(battleId);
        roomCreeps.remove(battleId);
        roomTowers.remove(battleId);
        roomBuildings.remove(battleId);
        roomRunes.remove(battleId);
        lastCreepSpawnTime.remove(battleId);
    }

    @Data
    public static class Tower {
        private int towerId;
        private int teamId;
        private Position position;
        private int hp;
        private int maxHp;
        private int attackDamage;
        private int attackRange;
        private boolean alive;
        private Long currentTarget;
    }

    @Data
    public static class Building {
        public enum BuildingType { BASE, BARRACKS }
        private int buildingId;
        private int teamId;
        private Position position;
        private int hp;
        private int maxHp;
        private boolean alive;
        private BuildingType type;
    }

    @Data
    public static class Position {
        private int x;
        private int y;
        public Position(int x, int y) { this.x = x; this.y = y; }
    }

    @Data
    public static class RuneSpawnPoint {
        private Position position;
        private boolean spawned;
        private long lastSpawnTime;
        private int runeType;

        public RuneSpawnPoint(Position position, int runeType) {
            this.position = position;
            this.runeType = runeType;
            this.spawned = false;
            this.lastSpawnTime = 0;
        }
    }
}
