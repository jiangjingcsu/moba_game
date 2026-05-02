package com.moba.battle.model;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public class MapTemplate {

    private MapInfo map;
    private TerrainInfo terrain;
    private List<TowerConfig> towers;
    private List<BuildingConfig> buildings;
    private List<CreepCampConfig> creepCamps;
    private List<RuneSpawnConfig> runeSpawns;
    private RoshanConfig roshan;
    private List<SpawnPointConfig> spawnPoints;
    private List<LaneConfig> lanes;

    @Data
    public static class MapInfo {
        private int id;
        private String name;
        private int width;
        private int height;
        private int teamCount;
        private String gameMode;
    }

    @Data
    public static class TerrainInfo {
        private List<TerrainRegion> obstacles;
        private List<TerrainRegion> flyableZones;
        private List<TerrainRegion> bushes;
        private List<TerrainRegion> river;
        private List<TerrainRegion> bases;
    }

    @Data
    public static class TowerConfig {
        private int id;
        private int teamId;
        private int tier;
        private String towerType;
        private int x;
        private int y;
        private int hp;
        private int damage;
        private int attackSpeed;
        private int range;
    }

    @Data
    public static class BuildingConfig {
        private int id;
        private int teamId;
        private String type;
        private int x;
        private int y;
        private int hp;
    }

    @Data
    public static class CreepCampConfig {
        private int id;
        private int x;
        private int y;
        private String type;
        private int count;
        private int level;
        private int respawn;
    }

    @Data
    public static class RuneSpawnConfig {
        private int id;
        private int x;
        private int y;
        private String type;
        private int interval;
    }

    @Data
    public static class RoshanConfig {
        private int id;
        private int x;
        private int y;
        private int radius;
        private int respawn;
        private int hp;
        private int damage;
    }

    @Data
    public static class SpawnPointConfig {
        private int teamId;
        private int x;
        private int y;
        private int radius;
    }

    @Data
    public static class LaneConfig {
        private int id;
        private int teamId;
        private int index;
        private List<MapPointConfig> path;
        private int width;
    }

    @Data
    public static class MapPointConfig {
        private int x;
        private int y;
    }

    public List<TerrainRegion> getAllTerrainRegions() {
        List<TerrainRegion> all = new ArrayList<>();
        if (terrain != null) {
            addRegions(all, terrain.getObstacles(), TerrainRegion.TerrainType.OBSTACLE);
            addRegions(all, terrain.getFlyableZones(), TerrainRegion.TerrainType.FLYABLE);
            addRegions(all, terrain.getBushes(), TerrainRegion.TerrainType.BUSH);
            addRegions(all, terrain.getRiver(), TerrainRegion.TerrainType.RIVER);
            addRegions(all, terrain.getBases(), TerrainRegion.TerrainType.BASE);
        }
        return all;
    }

    private void addRegions(List<TerrainRegion> target, List<TerrainRegion> source, TerrainRegion.TerrainType type) {
        if (source == null) return;
        for (TerrainRegion region : source) {
            region.setTerrainType(type);
            target.add(region);
        }
    }

    public static MapTemplate loadFromYaml(String resourcePath) {
        try {
            YAMLMapper yamlMapper = new YAMLMapper();
            InputStream is = MapTemplate.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                log.error("地图配置未找到: {}", resourcePath);
                return null;
            }
            MapTemplate template = yamlMapper.readValue(is, MapTemplate.class);
            log.info("地图配置已加载: {} ({})", template.getMap().getName(), resourcePath);
            return template;
        } catch (Exception e) {
            log.error("加载地图配置失败: {}", resourcePath, e);
            return null;
        }
    }

    public static MapTemplate loadByMapId(int mapId) {
        String path = "maps/map_" + (mapId == 2 ? "5v5" : "3v3v3") + ".yml";
        return loadFromYaml(path);
    }
}
