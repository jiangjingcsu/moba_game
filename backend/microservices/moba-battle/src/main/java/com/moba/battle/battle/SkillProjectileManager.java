package com.moba.battle.battle;

import com.moba.battle.model.SkillConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class SkillProjectileManager {

    private final PhysicsWorld physicsWorld;

    public SkillProjectileManager(PhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    public void fireProjectile(long casterId, int casterTeamId,
                                float casterX, float casterY,
                                float targetX, float targetY,
                                SkillConfig skillConfig, int currentFrame) {
        float dx = targetX - casterX;
        float dy = targetY - casterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance < 1.0f) {
            distance = 1.0f;
            dx = 1.0f;
            dy = 0.0f;
        }

        float dirX = dx / distance;
        float dirY = dy / distance;

        float speed = calculateProjectileSpeed(skillConfig);
        float radius = getProjectileRadius(skillConfig);
        float startX = casterX + dirX * 30;
        float startY = casterY + dirY * 30;

        switch (skillConfig.getType()) {
            case CIRCLE_AREA:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        targetX, targetY, radius,
                        0, 0, 0,
                        skillConfig.isCanHitSelf(), skillConfig.isCanHitAlly(), skillConfig.isCanHitEnemy()
                );
                break;

            case LINE:
            case TRAIL:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        startX, startY, radius,
                        dirX, dirY, speed,
                        skillConfig.isCanHitSelf(), skillConfig.isCanHitAlly(), skillConfig.isCanHitEnemy()
                );
                break;

            case FAN_AREA:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        casterX, casterY, skillConfig.getDamageRadius(),
                        0, 0, 0,
                        skillConfig.isCanHitSelf(), skillConfig.isCanHitAlly(), skillConfig.isCanHitEnemy()
                );
                break;

            case RECT_AREA:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        targetX, targetY, Math.max(skillConfig.getRectWidth(), skillConfig.getRectHeight()) / 2f,
                        0, 0, 0,
                        skillConfig.isCanHitSelf(), skillConfig.isCanHitAlly(), skillConfig.isCanHitEnemy()
                );
                break;

            case INSTANT:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        targetX, targetY, radius,
                        0, 0, 0,
                        skillConfig.isCanHitSelf(), skillConfig.isCanHitAlly(), skillConfig.isCanHitEnemy()
                );
                break;

            case SELF_BUFF:
                physicsWorld.createProjectileBody(
                        skillConfig.getSkillId(), casterId, casterTeamId,
                        casterX, casterY, 50,
                        0, 0, 0,
                        true, false, false
                );
                break;
        }
    }

    private float calculateProjectileSpeed(SkillConfig config) {
        return switch (config.getType()) {
            case LINE, TRAIL -> 1500.0f;
            default -> 0;
        };
    }

    private float getProjectileRadius(SkillConfig config) {
        return switch (config.getType()) {
            case CIRCLE_AREA -> config.getDamageRadius();
            case LINE, TRAIL -> config.getRectWidth() / 2f;
            case INSTANT -> config.getDamageRadius();
            default -> 50;
        };
    }

    public List<SkillHitResult> processCollisionEvents(List<PhysicsWorld.CollisionEvent> events) {
        List<SkillHitResult> results = new ArrayList<>();

        for (PhysicsWorld.CollisionEvent event : events) {
            if (!event.isSensor()) continue;

            PhysicsBodyMapper.BodyType typeA = event.getTypeA();
            PhysicsBodyMapper.BodyType typeB = event.getTypeB();

            long projectileBodyId = -1;
            long targetGameObjectId = -1;
            PhysicsBodyMapper.BodyType targetType = null;

            if (typeA == PhysicsBodyMapper.BodyType.PROJECTILE && isHittable(typeB)) {
                projectileBodyId = event.getBodyIdA();
                targetGameObjectId = event.getGameObjectIdB();
                targetType = typeB;
            } else if (typeB == PhysicsBodyMapper.BodyType.PROJECTILE && isHittable(typeA)) {
                projectileBodyId = event.getBodyIdB();
                targetGameObjectId = event.getGameObjectIdA();
                targetType = typeA;
            }

            if (projectileBodyId < 0 || targetGameObjectId < 0) continue;

            PhysicsWorld.ProjectileInfo info = physicsWorld.getProjectileInfo(projectileBodyId);
            if (info == null) continue;

            if (physicsWorld.hasProjectileHit(projectileBodyId, targetGameObjectId)) continue;

            if (!isValidHit(info, targetGameObjectId, targetType)) continue;

            physicsWorld.markProjectileHit(projectileBodyId, targetGameObjectId);

            SkillHitResult result = new SkillHitResult();
            result.casterId = info.getCasterId();
            result.casterTeamId = info.getCasterTeamId();
            result.skillId = info.getSkillId();
            result.targetId = targetGameObjectId;
            result.targetType = targetType;
            results.add(result);
        }

        return results;
    }

    private boolean isHittable(PhysicsBodyMapper.BodyType type) {
        return type == PhysicsBodyMapper.BodyType.PLAYER || type == PhysicsBodyMapper.BodyType.CREEP;
    }

    private boolean isValidHit(PhysicsWorld.ProjectileInfo info, long targetId, PhysicsBodyMapper.BodyType targetType) {
        if (targetId == info.getCasterId()) {
            return info.isCanHitSelf();
        }

        if (targetType == PhysicsBodyMapper.BodyType.PLAYER) {
            return info.isCanHitEnemy() || info.isCanHitAlly();
        }

        return info.isCanHitEnemy();
    }

    @lombok.Data
    public static class SkillHitResult {
        private long casterId;
        private int casterTeamId;
        private long skillId;
        private long targetId;
        private PhysicsBodyMapper.BodyType targetType;
    }
}
