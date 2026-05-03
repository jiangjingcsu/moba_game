package com.moba.battle.battle;

import com.moba.battle.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.jbox2d.collision.AABB;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.contacts.Contact;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PhysicsWorld {

    private final World world;
    private final PhysicsBodyMapper bodyMapper;
    private final float worldScale;
    private final int velocityIterations;
    private final int positionIterations;
    private final float playerRestitution;
    private final float playerFriction;
    private final float creepRestitution;
    private final float creepFriction;
    private final int projectileLifetimeFrames;

    private final List<CollisionEvent> collisionEvents;
    private final Map<Long, ProjectileInfo> activeProjectiles;

    private int currentFrame;

    public PhysicsWorld(ServerConfig config) {
        this.worldScale = config.getPhysicsWorldScale();
        this.velocityIterations = config.getPhysicsVelocityIterations();
        this.positionIterations = config.getPhysicsPositionIterations();
        this.playerRestitution = config.getPlayerRestitution();
        this.playerFriction = config.getPlayerFriction();
        this.creepRestitution = config.getCreepRestitution();
        this.creepFriction = config.getCreepFriction();
        this.projectileLifetimeFrames = config.getProjectileLifetimeFrames();

        this.world = new World(new Vec2(0, 0));
        this.bodyMapper = new PhysicsBodyMapper();
        this.collisionEvents = new ArrayList<>();
        this.activeProjectiles = new ConcurrentHashMap<>();
        this.currentFrame = 0;

        world.setContactListener(new CollisionListener(bodyMapper, collisionEvents));

        log.info("物理世界已初始化: 缩放={}, 速度迭代={}, 位置迭代={}",
                worldScale, velocityIterations, positionIterations);
    }

    public float toPhysics(float gameCoord) {
        return gameCoord * worldScale;
    }

    public float toGame(float physicsCoord) {
        return physicsCoord / worldScale;
    }

    public long createPlayerBody(long userId, float gameX, float gameY, float collisionRadius) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));
        bodyDef.fixedRotation = true;
        bodyDef.linearDamping = 0.0f;

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(toPhysics(collisionRadius));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;
        fixtureDef.restitution = playerRestitution;
        fixtureDef.friction = playerFriction;
        fixtureDef.filter.categoryBits = CollisionCategory.PLAYER;
        fixtureDef.filter.maskBits = CollisionCategory.PLAYER_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, userId, PhysicsBodyMapper.BodyType.PLAYER);
        log.debug("玩家物理体已创建: 玩家ID={}, 物理体ID={}, 位置=({},{})",
                userId, bodyId, gameX, gameY);
        return bodyId;
    }

    public long createCreepBody(long creepId, float gameX, float gameY, float collisionRadius) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));
        bodyDef.fixedRotation = true;
        bodyDef.linearDamping = 0.0f;

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(toPhysics(collisionRadius));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 0.8f;
        fixtureDef.restitution = creepRestitution;
        fixtureDef.friction = creepFriction;
        fixtureDef.filter.categoryBits = CollisionCategory.CREEP;
        fixtureDef.filter.maskBits = CollisionCategory.CREEP_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, creepId, PhysicsBodyMapper.BodyType.CREEP);
        return bodyId;
    }

    public long createStaticBody(int objectId, float gameX, float gameY, float halfWidth, float halfHeight) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(toPhysics(halfWidth), toPhysics(halfHeight));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 0.0f;
        fixtureDef.restitution = 0.0f;
        fixtureDef.friction = 1.0f;
        fixtureDef.filter.categoryBits = CollisionCategory.STATIC;
        fixtureDef.filter.maskBits = CollisionCategory.STATIC_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, objectId, PhysicsBodyMapper.BodyType.STATIC);
        return bodyId;
    }

    public long createCircleStaticBody(int objectId, float gameX, float gameY, float radius) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(toPhysics(radius));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 0.0f;
        fixtureDef.restitution = 0.0f;
        fixtureDef.friction = 1.0f;
        fixtureDef.filter.categoryBits = CollisionCategory.STATIC;
        fixtureDef.filter.maskBits = CollisionCategory.STATIC_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, objectId, PhysicsBodyMapper.BodyType.STATIC);
        return bodyId;
    }

    public long createProjectileBody(long skillId, long casterId, int casterTeamId,
                                      float gameX, float gameY, float radius,
                                      float dirX, float dirY, float speed,
                                      boolean canHitSelf, boolean canHitAlly, boolean canHitEnemy) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.KINEMATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));
        bodyDef.fixedRotation = true;

        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (length > 0) {
            dirX /= length;
            dirY /= length;
        }

        float physSpeed = toPhysics(speed);
        bodyDef.linearVelocity.set(dirX * physSpeed, dirY * physSpeed);

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(toPhysics(radius));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;
        fixtureDef.density = 0.0f;
        fixtureDef.filter.categoryBits = CollisionCategory.PROJECTILE;
        fixtureDef.filter.maskBits = CollisionCategory.PROJECTILE_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, skillId, PhysicsBodyMapper.BodyType.PROJECTILE);

        ProjectileInfo info = new ProjectileInfo();
        info.bodyId = bodyId;
        info.skillId = skillId;
        info.casterId = casterId;
        info.casterTeamId = casterTeamId;
        info.canHitSelf = canHitSelf;
        info.canHitAlly = canHitAlly;
        info.canHitEnemy = canHitEnemy;
        info.spawnFrame = currentFrame;
        info.hitTargets = new HashSet<>();

        activeProjectiles.put(bodyId, info);

        return bodyId;
    }

    public void createMapBoundaries(int mapWidth, int mapHeight, float wallThickness) {
        float hw = mapWidth / 2f;
        float hh = mapHeight / 2f;
        float ht = wallThickness / 2f;

        createStaticBody(-1, hw, -ht, hw, ht);
        createStaticBody(-2, hw, mapHeight + ht, hw, ht);
        createStaticBody(-3, -ht, hh, ht, hh);
        createStaticBody(-4, mapWidth + ht, hh, ht, hh);
    }

    public long createFlyableBody(int objectId, float gameX, float gameY, float halfWidth, float halfHeight) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(toPhysics(halfWidth), toPhysics(halfHeight));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 0.0f;
        fixtureDef.restitution = 0.0f;
        fixtureDef.friction = 1.0f;
        fixtureDef.filter.categoryBits = CollisionCategory.FLYABLE;
        fixtureDef.filter.maskBits = CollisionCategory.FLYABLE_MASK;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, objectId, PhysicsBodyMapper.BodyType.STATIC);
        return bodyId;
    }

    public long createSensorBody(int objectId, float gameX, float gameY, float halfWidth, float halfHeight, short categoryBits) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(toPhysics(halfWidth), toPhysics(halfHeight));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;
        fixtureDef.density = 0.0f;
        fixtureDef.filter.categoryBits = categoryBits;
        fixtureDef.filter.maskBits = CollisionCategory.PLAYER | CollisionCategory.CREEP;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, objectId, PhysicsBodyMapper.BodyType.STATIC);
        return bodyId;
    }

    public long createCircleSensorBody(int objectId, float gameX, float gameY, float radius, short categoryBits) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(toPhysics(gameX), toPhysics(gameY));

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(toPhysics(radius));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;
        fixtureDef.density = 0.0f;
        fixtureDef.filter.categoryBits = categoryBits;
        fixtureDef.filter.maskBits = CollisionCategory.PLAYER | CollisionCategory.CREEP;

        body.createFixture(fixtureDef);

        long bodyId = bodyMapper.registerBody(body, objectId, PhysicsBodyMapper.BodyType.STATIC);
        return bodyId;
    }

    public void setFlying(long bodyId, boolean flying) {
        Body body = bodyMapper.getBody(bodyId);
        if (body == null) return;

        org.jbox2d.dynamics.Fixture fixture = body.getFixtureList();
        while (fixture != null) {
            if (!fixture.isSensor()) {
                Filter filter = fixture.getFilterData();
                if (flying) {
                    filter.categoryBits = CollisionCategory.FLYING_UNIT;
                    filter.maskBits = CollisionCategory.FLYING_MASK;
                } else {
                    filter.categoryBits = CollisionCategory.PLAYER;
                    filter.maskBits = CollisionCategory.PLAYER_MASK;
                }
                fixture.setFilterData(filter);
            }
            fixture = fixture.getNext();
        }
    }

    public void step(float dt) {
        collisionEvents.clear();
        currentFrame++;

        world.step(dt, velocityIterations, positionIterations);

        updateProjectiles();
    }

    private void updateProjectiles() {
        Iterator<Map.Entry<Long, ProjectileInfo>> it = activeProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ProjectileInfo> entry = it.next();
            ProjectileInfo info = entry.getValue();
            if (currentFrame - info.spawnFrame >= projectileLifetimeFrames) {
                destroyBody(info.bodyId);
                it.remove();
            }
        }
    }

    public void setBodyVelocity(long bodyId, float gameVx, float gameVy) {
        Body body = bodyMapper.getBody(bodyId);
        if (body != null) {
            body.setLinearVelocity(new Vec2(toPhysics(gameVx), toPhysics(gameVy)));
        }
    }

    public void setBodyPosition(long bodyId, float gameX, float gameY) {
        Body body = bodyMapper.getBody(bodyId);
        if (body != null) {
            body.setTransform(new Vec2(toPhysics(gameX), toPhysics(gameY)), 0);
        }
    }

    public void stopBody(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        if (body != null) {
            body.setLinearVelocity(new Vec2(0, 0));
        }
    }

    public void applyImpulse(long bodyId, float gameIx, float gameIy) {
        Body body = bodyMapper.getBody(bodyId);
        if (body != null) {
            body.applyLinearImpulse(new Vec2(toPhysics(gameIx), toPhysics(gameIy)), body.getWorldCenter());
        }
    }

    public float getBodyGameX(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        return body != null ? toGame(body.getPosition().x) : 0;
    }

    public float getBodyGameY(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        return body != null ? toGame(body.getPosition().y) : 0;
    }

    public float getBodyGameVX(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        return body != null ? toGame(body.getLinearVelocity().x) : 0;
    }

    public float getBodyGameVY(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        return body != null ? toGame(body.getLinearVelocity().y) : 0;
    }

    public List<Long> queryCircle(float gameCX, float gameCY, float gameRadius) {
        float physCX = toPhysics(gameCX);
        float physCY = toPhysics(gameCY);
        float physRadius = toPhysics(gameRadius);

        AABB aabb = new AABB(
                new Vec2(physCX - physRadius, physCY - physRadius),
                new Vec2(physCX + physRadius, physCY + physRadius)
        );

        List<Long> result = new ArrayList<>();
        world.queryAABB(fixture -> {
            Body body = fixture.getBody();
            long bodyId = bodyMapper.getBodyId(body);
            if (bodyId >= 0) {
                Vec2 pos = body.getPosition();
                float dx = pos.x - physCX;
                float dy = pos.y - physCY;
                if (dx * dx + dy * dy <= physRadius * physRadius) {
                    result.add(bodyMapper.getGameObjectId(bodyId));
                }
            }
            return true;
        }, aabb);

        return result;
    }

    public List<Long> queryAABB(float gameMinX, float gameMinY, float gameMaxX, float gameMaxY) {
        AABB aabb = new AABB(
                new Vec2(toPhysics(gameMinX), toPhysics(gameMinY)),
                new Vec2(toPhysics(gameMaxX), toPhysics(gameMaxY))
        );

        List<Long> result = new ArrayList<>();
        world.queryAABB(fixture -> {
            Body body = fixture.getBody();
            long bodyId = bodyMapper.getBodyId(body);
            if (bodyId >= 0) {
                result.add(bodyMapper.getGameObjectId(bodyId));
            }
            return true;
        }, aabb);

        return result;
    }

    public List<CollisionEvent> drainCollisionEvents() {
        List<CollisionEvent> events = new ArrayList<>(collisionEvents);
        collisionEvents.clear();
        return events;
    }

    public ProjectileInfo getProjectileInfo(long bodyId) {
        return activeProjectiles.get(bodyId);
    }

    public void markProjectileHit(long bodyId, long targetId) {
        ProjectileInfo info = activeProjectiles.get(bodyId);
        if (info != null) {
            info.hitTargets.add(targetId);
        }
    }

    public boolean hasProjectileHit(long bodyId, long targetId) {
        ProjectileInfo info = activeProjectiles.get(bodyId);
        return info != null && info.hitTargets.contains(targetId);
    }

    public void destroyBody(long bodyId) {
        Body body = bodyMapper.getBody(bodyId);
        if (body != null) {
            world.destroyBody(body);
            bodyMapper.unregisterBody(bodyId);
        }
        activeProjectiles.remove(bodyId);
    }

    public void destroyAllBodies() {
        for (Long bodyId : new ArrayList<>(bodyMapper.getAllBodyIds())) {
            Body body = bodyMapper.getBody(bodyId);
            if (body != null) {
                world.destroyBody(body);
            }
        }
        bodyMapper.clear();
        activeProjectiles.clear();
    }

    public PhysicsBodyMapper getBodyMapper() {
        return bodyMapper;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public static class CollisionCategory {
        public static final short PLAYER = 0x0001;
        public static final short CREEP = 0x0002;
        public static final short STATIC = 0x0004;
        public static final short PROJECTILE = 0x0008;
        public static final short FLYABLE = 0x0010;
        public static final short BUSH = 0x0020;
        public static final short RIVER = 0x0040;
        public static final short BASE = 0x0080;
        public static final short FLYING_UNIT = 0x0100;

        public static final short PLAYER_MASK = PLAYER | CREEP | STATIC | FLYABLE;
        public static final short CREEP_MASK = PLAYER | CREEP | STATIC | FLYABLE;
        public static final short STATIC_MASK = PLAYER | CREEP;
        public static final short PROJECTILE_MASK = PLAYER | CREEP | STATIC;
        public static final short FLYING_MASK = PLAYER | CREEP | STATIC;
        public static final short FLYABLE_MASK = PLAYER | CREEP;
        public static final short BUSH_MASK = 0;
        public static final short RIVER_MASK = 0;
        public static final short BASE_MASK = 0;
    }

    @lombok.Data
    public static class ProjectileInfo {
        private long bodyId;
        private long skillId;
        private long casterId;
        private int casterTeamId;
        private boolean canHitSelf;
        private boolean canHitAlly;
        private boolean canHitEnemy;
        private int spawnFrame;
        private Set<Long> hitTargets;
    }

    @lombok.Data
    public static class CollisionEvent {
        private long bodyIdA;
        private long bodyIdB;
        private long gameObjectIdA;
        private long gameObjectIdB;
        private PhysicsBodyMapper.BodyType typeA;
        private PhysicsBodyMapper.BodyType typeB;
        private boolean isSensor;
    }
}
