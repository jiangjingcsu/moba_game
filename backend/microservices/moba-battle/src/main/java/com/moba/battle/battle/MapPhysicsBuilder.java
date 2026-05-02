package com.moba.battle.battle;

import com.moba.battle.model.MapTemplate;
import com.moba.battle.model.TerrainRegion;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class MapPhysicsBuilder {

    public void build(PhysicsWorld world, MapTemplate template) {
        if (world == null || template == null) return;

        MapTemplate.MapInfo mapInfo = template.getMap();
        world.createMapBoundaries(mapInfo.getWidth(), mapInfo.getHeight(), 50);

        MapTemplate.TerrainInfo terrain = template.getTerrain();
        if (terrain != null) {
            buildObstacles(world, terrain.getObstacles());
            buildFlyableZones(world, terrain.getFlyableZones());
            buildBushes(world, terrain.getBushes());
            buildRivers(world, terrain.getRiver());
            buildBases(world, terrain.getBases());
        }

        buildTowers(world, template.getTowers());
        buildBuildings(world, template.getBuildings());

        log.info("地图物理构建完成: {} ({}x{}), 障碍物={}, 可飞行={}, 草丛={}, 河道={}, 基地={}, 防御塔={}, 建筑={}",
                mapInfo.getName(), mapInfo.getWidth(), mapInfo.getHeight(),
                count(terrain != null ? terrain.getObstacles() : null),
                count(terrain != null ? terrain.getFlyableZones() : null),
                count(terrain != null ? terrain.getBushes() : null),
                count(terrain != null ? terrain.getRiver() : null),
                count(terrain != null ? terrain.getBases() : null),
                count(template.getTowers()),
                count(template.getBuildings()));
    }

    private void buildObstacles(PhysicsWorld world, List<TerrainRegion> obstacles) {
        if (obstacles == null) return;
        for (TerrainRegion obs : obstacles) {
            world.createStaticBody(obs.getId(), obs.getCenterX(), obs.getCenterY(),
                    obs.getWidth() / 2f, obs.getHeight() / 2f);
        }
    }

    private void buildFlyableZones(PhysicsWorld world, List<TerrainRegion> flyableZones) {
        if (flyableZones == null) return;
        for (TerrainRegion zone : flyableZones) {
            world.createFlyableBody(zone.getId(), zone.getCenterX(), zone.getCenterY(),
                    zone.getWidth() / 2f, zone.getHeight() / 2f);
        }
    }

    private void buildBushes(PhysicsWorld world, List<TerrainRegion> bushes) {
        if (bushes == null) return;
        for (TerrainRegion bush : bushes) {
            world.createSensorBody(bush.getId(), bush.getCenterX(), bush.getCenterY(),
                    bush.getWidth() / 2f, bush.getHeight() / 2f, PhysicsWorld.CollisionCategory.BUSH);
        }
    }

    private void buildRivers(PhysicsWorld world, List<TerrainRegion> rivers) {
        if (rivers == null) return;
        for (TerrainRegion river : rivers) {
            world.createSensorBody(river.getId(), river.getCenterX(), river.getCenterY(),
                    river.getWidth() / 2f, river.getHeight() / 2f, PhysicsWorld.CollisionCategory.RIVER);
        }
    }

    private void buildBases(PhysicsWorld world, List<TerrainRegion> bases) {
        if (bases == null) return;
        for (TerrainRegion base : bases) {
            world.createSensorBody(base.getId(), base.getCenterX(), base.getCenterY(),
                    base.getWidth() / 2f, base.getHeight() / 2f, PhysicsWorld.CollisionCategory.BASE);
        }
    }

    private void buildTowers(PhysicsWorld world, List<MapTemplate.TowerConfig> towers) {
        if (towers == null) return;
        for (MapTemplate.TowerConfig tower : towers) {
            world.createCircleStaticBody(tower.getId(), tower.getX(), tower.getY(), 80);
        }
    }

    private void buildBuildings(PhysicsWorld world, List<MapTemplate.BuildingConfig> buildings) {
        if (buildings == null) return;
        for (MapTemplate.BuildingConfig building : buildings) {
            world.createStaticBody(building.getId(), building.getX(), building.getY(), 100, 100);
        }
    }

    private int count(List<?> list) {
        return list != null ? list.size() : 0;
    }
}
