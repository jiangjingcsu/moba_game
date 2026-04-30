package com.moba.battleserver.model;

import lombok.Data;
import java.util.List;

@Data
public class MapConfig {
    private final int mapId;
    private final String mapName;
    private final MapSize size;
    private final int teamCount;
    private final List<SpawnPoint> spawnPoints;
    private final List<TowerConfig> towers;
    private final List<MonsterCamp> monsterCamps;

    public MapConfig(int mapId, String mapName, MapSize size, int teamCount) {
        this.mapId = mapId;
        this.mapName = mapName;
        this.size = size;
        this.teamCount = teamCount;
        this.spawnPoints = new java.util.ArrayList<>();
        this.towers = new java.util.ArrayList<>();
        this.monsterCamps = new java.util.ArrayList<>();

        for (int i = 0; i < teamCount; i++) {
            spawnPoints.add(new SpawnPoint(i, 0, 0));
        }
    }

    public static MapConfig create3v3v3Map() {
        MapConfig config = new MapConfig(1, "Triangular Arena", new MapSize(10000, 10000), 3);
        return config;
    }

    @Data
    public static class MapSize {
        private final int width;
        private final int height;

        public MapSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    @Data
    public static class SpawnPoint {
        private final int teamId;
        private final float x;
        private final float y;

        public SpawnPoint(int teamId, float x, float y) {
            this.teamId = teamId;
            this.x = x;
            this.y = y;
        }
    }

    @Data
    public static class TowerConfig {
        private final int towerId;
        private final int teamId;
        private final float x;
        private final float y;
        private final int hp;
        private final int attack;

        public TowerConfig(int towerId, int teamId, float x, float y, int hp, int attack) {
            this.towerId = towerId;
            this.teamId = teamId;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.attack = attack;
        }
    }

    @Data
    public static class MonsterCamp {
        private final int campId;
        private final float x;
        private final float y;
        private final int monsterLevel;
        private final int rewardGold;
        private final String buffType;

        public MonsterCamp(int campId, float x, float y, int monsterLevel, int rewardGold, String buffType) {
            this.campId = campId;
            this.x = x;
            this.y = y;
            this.monsterLevel = monsterLevel;
            this.rewardGold = rewardGold;
            this.buffType = buffType;
        }
    }
}
