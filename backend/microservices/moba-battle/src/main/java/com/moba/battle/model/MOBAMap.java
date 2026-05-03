package com.moba.battle.model;

import com.moba.common.constant.GameMode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
public class MOBAMap {
    private int mapId;
    private String mapName;
    private int width;
    private int height;
    private int teamCount;
    private GameMode gameMode;

    private List<Lane> lanes;
    private List<Tower> towers;
    private List<Building> buildings;
    private List<CreepCamp> creepCamps;
    private List<RuneSpawnPoint> runeSpawns;
    private List<RoshanArea> roshanAreas;
    private List<SpawnPoint> teamSpawns;
    private List<PathNode> navigationPath;

    private MapRegion safeRegion;
    private MapRegion jungleRegion;
    private MapRegion riverRegion;

    public static MOBAMap createMap(int mapId, GameMode mode) {
        switch (mode) {
            case MODE_3V3V3:
                return create3v3v3Map(mapId);
            case MODE_5V5:
                return create5v5Map(mapId);
            default:
                return create3v3v3Map(mapId);
        }
    }

    private static MOBAMap create3v3v3Map(int mapId) {
        MOBAMap map = new MOBAMap();
        map.setMapId(mapId);
        map.setMapName("三角竞技场");
        map.setWidth(12000);
        map.setHeight(12000);
        map.setTeamCount(3);
        map.setGameMode(GameMode.MODE_3V3V3);
        map.setLanes(new ArrayList<>());
        map.setTowers(new ArrayList<>());
        map.setBuildings(new ArrayList<>());
        map.setCreepCamps(new ArrayList<>());
        map.setRuneSpawns(new ArrayList<>());
        map.setRoshanAreas(new ArrayList<>());
        map.setTeamSpawns(new ArrayList<>());
        map.setNavigationPath(new ArrayList<>());

        int centerX = 6000;
        int centerY = 6000;
        int baseRadius = 4500;

        for (int team = 0; team < 3; team++) {
            double angle = team * 2 * Math.PI / 3 - Math.PI / 2;
            int baseX = (int) (centerX + baseRadius * Math.cos(angle));
            int baseY = (int) (centerY + baseRadius * Math.sin(angle));

            SpawnPoint spawn = new SpawnPoint();
            spawn.setTeamId(team);
            spawn.setX(baseX);
            spawn.setY(baseY);
            spawn.setRadius(400);
            map.getTeamSpawns().add(spawn);

            for (int lane = 0; lane < 3; lane++) {
                Lane laneObj = new Lane();
                laneObj.setLaneId(team * 10 + lane);
                laneObj.setTeamId(team);
                laneObj.setLaneIndex(lane);

                int pathStartX = baseX;
                int pathStartY = baseY;
                int pathEndX = centerX;
                int pathEndY = centerY;

                List<MapPoint> path = generateLanePath(pathStartX, pathStartY, pathEndX, pathEndY, 400);
                laneObj.setPath(path);
                laneObj.setPathWidth(300);
                map.getLanes().add(laneObj);

                int midX = (pathStartX + pathEndX) / 2;
                int midY = (pathStartY + pathEndY) / 2;

                Tower tier1 = createTower(team, 1, midX + 400, midY + 400, 600);
                map.getTowers().add(tier1);

                Tower tier2 = createTower(team, 2, midX + 800, midY + 800, 600);
                map.getTowers().add(tier2);
            }

            Building barracks = new Building();
            barracks.setBuildingId(team * 100);
            barracks.setTeamId(team);
            barracks.setBuildingType(Building.BuildingType.BARRACKS);
            barracks.setX(baseX);
            barracks.setY(baseY);
            barracks.setHp(5000);
            barracks.setMaxHp(5000);
            map.getBuildings().add(barracks);

            Building base = new Building();
            base.setBuildingId(team * 100 + 1);
            base.setTeamId(team);
            base.setBuildingType(Building.BuildingType.BASE);
            base.setX(baseX);
            base.setY(baseY);
            base.setHp(20000);
            base.setMaxHp(20000);
            map.getBuildings().add(base);
        }

        RuneSpawnPoint rune1 = new RuneSpawnPoint();
        rune1.setRuneId(1);
        rune1.setX(centerX);
        rune1.setY(centerY);
        rune1.setRuneType(RuneSpawnPoint.RuneType.POWER);
        rune1.setSpawnInterval(180);
        map.getRuneSpawns().add(rune1);

        for (int i = 0; i < 3; i++) {
            int angle = i * 120;
            int rx = centerX + 2000 * (int) Math.cos(Math.toRadians(angle));
            int ry = centerY + 2000 * (int) Math.sin(Math.toRadians(angle));

            RuneSpawnPoint rune = new RuneSpawnPoint();
            rune.setRuneId(i + 2);
            rune.setX(rx);
            rune.setY(ry);
            rune.setRuneType(i % 2 == 0 ? RuneSpawnPoint.RuneType.WISDOM : RuneSpawnPoint.RuneType.AGILITY);
            rune.setSpawnInterval(120);
            map.getRuneSpawns().add(rune);
        }

        CreepCamp camp1 = new CreepCamp();
        camp1.setCampId(1);
        camp1.setCampType(CreepCamp.CreepCampType.SMALL);
        camp1.setX(centerX - 1000);
        camp1.setY(centerY);
        camp1.setRespawnTime(60);
        camp1.setCreepCount(3);
        camp1.setCreepLevel(1);
        map.getCreepCamps().add(camp1);

        CreepCamp camp2 = new CreepCamp();
        camp2.setCampId(2);
        camp2.setCampType(CreepCamp.CreepCampType.LARGE);
        camp2.setX(centerX + 1000);
        camp2.setY(centerY);
        camp2.setRespawnTime(90);
        camp2.setCreepCount(5);
        camp2.setCreepLevel(3);
        map.getCreepCamps().add(camp2);

        map.setSafeRegion(new MapRegion(0, 0, 12000, 12000, false));
        map.setJungleRegion(new MapRegion(4000, 4000, 4000, 4000, true));
        map.setRiverRegion(new MapRegion(5500, 5500, 1000, 1000, true));

        log.info("已创建3v3v3地图: {} ({}x{})", map.getMapName(), map.getWidth(), map.getHeight());
        return map;
    }

    private static MOBAMap create5v5Map(int mapId) {
        MOBAMap map = new MOBAMap();
        map.setMapId(mapId);
        map.setMapName("标准战场");
        map.setWidth(16000);
        map.setHeight(16000);
        map.setTeamCount(2);
        map.setGameMode(GameMode.MODE_5V5);
        map.setLanes(new ArrayList<>());
        map.setTowers(new ArrayList<>());
        map.setBuildings(new ArrayList<>());
        map.setCreepCamps(new ArrayList<>());
        map.setRuneSpawns(new ArrayList<>());
        map.setRoshanAreas(new ArrayList<>());
        map.setTeamSpawns(new ArrayList<>());
        map.setNavigationPath(new ArrayList<>());

        int radiantBaseX = 500;
        int radiantBaseY = 7500;
        int direBaseX = 15500;
        int direBaseY = 7500;

        for (int team = 0; team < 2; team++) {
            int baseX = team == 0 ? radiantBaseX : direBaseX;
            int baseY = team == 0 ? radiantBaseY : direBaseY;

            SpawnPoint spawn = new SpawnPoint();
            spawn.setTeamId(team);
            spawn.setX(baseX);
            spawn.setY(baseY);
            spawn.setRadius(500);
            map.getTeamSpawns().add(spawn);

            Lane midLane = createRadiantDireLane(team, 0, baseX, baseY, 8000, 8000, 0);
            map.getLanes().add(midLane);

            Lane topLane = createRadiantDireLane(team, 1, baseX, baseY, 0, 16000, 1);
            map.getLanes().add(topLane);

            Lane botLane = createRadiantDireLane(team, 2, baseX, baseY, 16000, 0, 2);
            map.getLanes().add(botLane);

            int baseOffsetX = team == 0 ? 800 : -800;
            int baseOffsetY = team == 0 ? -800 : 800;
            Building barracks = new Building();
            barracks.setBuildingId(team * 100);
            barracks.setTeamId(team);
            barracks.setBuildingType(Building.BuildingType.BARRACKS);
            barracks.setX(baseX + baseOffsetX);
            barracks.setY(baseY + baseOffsetY);
            barracks.setHp(5000);
            barracks.setMaxHp(5000);
            map.getBuildings().add(barracks);

            Building base = new Building();
            base.setBuildingId(team * 100 + 1);
            base.setTeamId(team);
            base.setBuildingType(Building.BuildingType.BASE);
            base.setX(baseX);
            base.setY(baseY);
            base.setHp(25000);
            base.setMaxHp(25000);
            map.getBuildings().add(base);

            add5v5Towers(map, team, baseX, baseY);
        }

        add5v5CreepCamps(map);
        add5v5Runes(map);
        addRoshan(map);

        map.setSafeRegion(new MapRegion(0, 0, 16000, 16000, false));
        map.setJungleRegion(new MapRegion(3000, 3000, 10000, 10000, true));
        map.setRiverRegion(new MapRegion(7500, 7500, 1000, 1000, true));

        log.info("已创建5v5地图: {} ({}x{})", map.getMapName(), map.getWidth(), map.getHeight());
        return map;
    }

    private static Lane createRadiantDireLane(int team, int laneIndex, int baseX, int baseY,
                                              int midX, int midY, int direction) {
        Lane lane = new Lane();
        lane.setLaneId(team * 10 + laneIndex);
        lane.setTeamId(team);
        lane.setLaneIndex(laneIndex);

        List<MapPoint> path = new ArrayList<>();

        if (laneIndex == 0) {
            path = generateLanePath(baseX, baseY, midX, midY, 350);
        } else if (laneIndex == 1) {
            int cornerX = team == 0 ? 0 : 16000;
            path = generateLanePath(baseX, baseY, cornerX, midY, 350);
            List<MapPoint> path2 = generateLanePath(cornerX, midY, midX, midY, 350);
            path.addAll(path2);
        } else {
            int cornerX = team == 0 ? 0 : 16000;
            path = generateLanePath(baseX, baseY, cornerX, midY, 350);
            List<MapPoint> path2 = generateLanePath(cornerX, midY, midX, midY, 350);
            path.addAll(path2);
        }

        lane.setPath(path);
        lane.setPathWidth(280);
        return lane;
    }

    private static void add5v5Towers(MOBAMap map, int team, int baseX, int baseY) {
        int baseOffset = team == 0 ? 1 : -1;

        Tower tier1 = createTower(team, 1, baseX + 1200 * baseOffset, baseY, 600);
        map.getTowers().add(tier1);

        Tower tier2 = createTower(team, 2, baseX + 2400 * baseOffset, baseY, 600);
        map.getTowers().add(tier2);

        Tower tier3 = createTower(team, 3, baseX + 3600 * baseOffset, baseY, 600);
        map.getTowers().add(tier3);

        Tower tier4 = createTower(team, 4, baseX + 4800 * baseOffset, baseY, 600);
        map.getTowers().add(tier4);

        Tower ancient = createTower(team, 5, baseX + 6000 * baseOffset, baseY, 600);
        ancient.setTowerType(Tower.TowerType.ANCIENT);
        map.getTowers().add(ancient);
    }

    private static void add5v5CreepCamps(MOBAMap map) {
        int[][] campPositions = {
            {2000, 6000, 1, 3, 60},
            {4000, 4000, 2, 4, 90},
            {6000, 2000, 1, 5, 60},
            {10000, 12000, 1, 3, 60},
            {12000, 10000, 2, 4, 90},
            {14000, 6000, 1, 5, 60},
            {4000, 10000, 1, 2, 60},
            {12000, 6000, 1, 2, 60},
        };

        for (int i = 0; i < campPositions.length; i++) {
            CreepCamp camp = new CreepCamp();
            camp.setCampId(i + 1);
            camp.setX(campPositions[i][0]);
            camp.setY(campPositions[i][1]);
            camp.setCampType(campPositions[i][2] == 1 ? CreepCamp.CreepCampType.SMALL : CreepCamp.CreepCampType.LARGE);
            camp.setCreepCount(3 + campPositions[i][3]);
            camp.setCreepLevel(campPositions[i][4]);
            camp.setRespawnTime(campPositions[i][4]);
            map.getCreepCamps().add(camp);
        }
    }

    private static void add5v5Runes(MOBAMap map) {
        RuneSpawnPoint.RuneType[] runeTypes = {
            RuneSpawnPoint.RuneType.POWER,
            RuneSpawnPoint.RuneType.AGILITY,
            RuneSpawnPoint.RuneType.WISDOM,
            RuneSpawnPoint.RuneType.HEALTH,
            RuneSpawnPoint.RuneType.MANA,
        };
        int[][] runePositions = {
            {8000, 4500, 0},
            {8000, 5500, 1},
            {8000, 5000, 2},
            {8000, 5000, 3},
            {8000, 5000, 4},
        };

        for (int i = 0; i < runePositions.length; i++) {
            RuneSpawnPoint rune = new RuneSpawnPoint();
            rune.setRuneId(i + 1);
            rune.setX(runePositions[i][0]);
            rune.setY(runePositions[i][1]);
            rune.setRuneType(runeTypes[runePositions[i][2]]);
            rune.setSpawnInterval(i < 3 ? 180 : 90);
            map.getRuneSpawns().add(rune);
        }
    }

    private static void addRoshan(MOBAMap map) {
        RoshanArea roshan = new RoshanArea();
        roshan.setAreaId(1);
        roshan.setX(8000);
        roshan.setY(8000);
        roshan.setRadius(500);
        roshan.setRespawnTime(480);
        roshan.setBaseHp(10000);
        roshan.setBaseDamage(350);
        map.getRoshanAreas().add(roshan);
    }

    private static Tower createTower(int teamId, int tier, int x, int y, int range) {
        Tower tower = new Tower();
        tower.setTowerId(teamId * 10 + tier);
        tower.setTeamId(teamId);
        tower.setTier(tier);
        tower.setX(x);
        tower.setY(y);
        tower.setRange(range);
        tower.setHp(3000 + tier * 500);
        tower.setMaxHp(tower.getHp());
        tower.setDamage(150 + tier * 50);
        tower.setAttackSpeed(1000);
        tower.setTowerType(tier <= 3 ? Tower.TowerType.OUTER : Tower.TowerType.INNER);
        return tower;
    }

    private static List<MapPoint> generateLanePath(int startX, int startY, int endX, int endY, int step) {
        List<MapPoint> path = new ArrayList<>();
        int dx = endX - startX;
        int dy = endY - startY;
        int steps = Math.max(Math.abs(dx), Math.abs(dy)) / step;

        for (int i = 0; i <= steps; i++) {
            MapPoint point = new MapPoint();
            point.setX(startX + dx * i / Math.max(1, steps));
            point.setY(startY + dy * i / Math.max(1, steps));
            point.setWalkable(true);
            point.setTerrainType(TerrainType.LANE);
            path.add(point);
        }
        return path;
    }

    public List<MapPoint> getPathBetween(int fromX, int fromY, int toX, int toY) {
        List<MapPoint> path = new ArrayList<>();
        int dx = toX - fromX;
        int dy = toY - fromY;
        int steps = Math.max(Math.abs(dx), Math.abs(dy)) / 100;

        for (int i = 0; i <= steps; i++) {
            MapPoint point = new MapPoint();
            point.setX(fromX + dx * i / Math.max(1, steps));
            point.setY(fromY + dy * i / Math.max(1, steps));
            point.setWalkable(isWalkable(point.getX(), point.getY()));
            path.add(point);
        }
        return path;
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }
        return true;
    }

    public Tower getTower(int towerId) {
        return towers.stream().filter(t -> t.getTowerId() == towerId).findFirst().orElse(null);
    }

    public Building getBuilding(int buildingId) {
        return buildings.stream().filter(b -> b.getBuildingId() == buildingId).findFirst().orElse(null);
    }

    public SpawnPoint getSpawnPoint(int teamId) {
        return teamSpawns.stream().filter(s -> s.getTeamId() == teamId).findFirst().orElse(null);
    }

    public List<Tower> getTowersForTeam(int teamId) {
        return towers.stream().filter(t -> t.getTeamId() == teamId).toList();
    }

    public List<RuneSpawnPoint> getActiveRuneSpawns() {
        return runeSpawns.stream().filter(RuneSpawnPoint::isActive).toList();
    }

    public static class MapPoint {
        private int x;
        private int y;
        private boolean walkable;
        private TerrainType terrainType;

        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public boolean isWalkable() { return walkable; }
        public void setWalkable(boolean walkable) { this.walkable = walkable; }
        public TerrainType getTerrainType() { return terrainType; }
        public void setTerrainType(TerrainType terrainType) { this.terrainType = terrainType; }
    }

    public enum TerrainType {
        PLAIN,
        LANE,
        JUNGLE,
        RIVER,
        OBSTACLE
    }

    @Data
    public static class Lane {
        private int laneId;
        private int teamId;
        private int laneIndex;
        private List<MapPoint> path;
        private int pathWidth;
    }

    @Data
    public static class Tower {
        private int towerId;
        private int teamId;
        private int tier;
        private int x;
        private int y;
        private int range;
        private int hp;
        private int maxHp;
        private int damage;
        private int attackSpeed;
        private TowerType towerType;
        private long lastAttackFrame;
        private Long currentTarget;

        public enum TowerType {
            OUTER,
            INNER,
            ANCIENT
        }

        public boolean canAttack(long currentFrame) {
            if (lastAttackFrame == 0) return true;
            long frameDiff = currentFrame - lastAttackFrame;
            long attackInterval = 1000 / attackSpeed;
            return frameDiff >= attackInterval;
        }

        public void attack(long currentFrame) {
            this.lastAttackFrame = currentFrame;
        }
    }

    @Data
    public static class Building {
        private int buildingId;
        private int teamId;
        private BuildingType buildingType;
        private int x;
        private int y;
        private int hp;
        private int maxHp;

        public enum BuildingType {
            BASE,
            BARRACKS,
            TOWER
        }
    }

    @Data
    public static class CreepCamp {
        private int campId;
        private int x;
        private int y;
        private CreepCampType campType;
        private int creepCount;
        private int creepLevel;
        private int respawnTime;
        private long lastClearTime;
        private boolean active;

        public enum CreepCampType {
            SMALL,
            LARGE,
            ANCIENT
        }
    }

    @Data
    public static class RuneSpawnPoint {
        private int runeId;
        private int x;
        private int y;
        private RuneType runeType;
        private int spawnInterval;
        private long lastSpawnTime;
        private boolean active;

        public enum RuneType {
            POWER,
            AGILITY,
            WISDOM,
            HEALTH,
            MANA
        }
    }

    @Data
    public static class RoshanArea {
        private int areaId;
        private int x;
        private int y;
        private int radius;
        private int respawnTime;
        private int baseHp;
        private int baseDamage;
        private long lastKillTime;
        private boolean alive;
    }

    @Data
    public static class SpawnPoint {
        private int teamId;
        private int x;
        private int y;
        private int radius;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MapRegion {
        private int x;
        private int y;
        private int width;
        private int height;
        private boolean blocked;

        public boolean contains(int px, int py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    @Data
    public static class PathNode {
        private int x;
        private int y;
        private int gCost;
        private int hCost;
        private int fCost;
        private PathNode parent;
    }
}
