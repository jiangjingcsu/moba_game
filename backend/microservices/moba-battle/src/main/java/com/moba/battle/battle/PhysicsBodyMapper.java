package com.moba.battle.battle;

import lombok.extern.slf4j.Slf4j;
import org.jbox2d.dynamics.Body;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PhysicsBodyMapper {

    public enum BodyType {
        PLAYER,
        CREEP,
        STATIC,
        PROJECTILE
    }

    private final AtomicLong bodyIdCounter;
    private final Map<Long, Body> bodies;
    private final Map<Long, BodyType> bodyTypes;
    private final Map<Long, Long> bodyToGameObject;
    private final Map<Long, Long> gameObjectToBody;
    private final Map<Body, Long> bodyToBodyId;

    public PhysicsBodyMapper() {
        this.bodyIdCounter = new AtomicLong(0);
        this.bodies = new ConcurrentHashMap<>();
        this.bodyTypes = new ConcurrentHashMap<>();
        this.bodyToGameObject = new ConcurrentHashMap<>();
        this.gameObjectToBody = new ConcurrentHashMap<>();
        this.bodyToBodyId = new ConcurrentHashMap<>();
    }

    public long registerBody(Body body, long gameObjectId, BodyType type) {
        long bodyId = bodyIdCounter.incrementAndGet();
        bodies.put(bodyId, body);
        bodyTypes.put(bodyId, type);
        bodyToGameObject.put(bodyId, gameObjectId);
        gameObjectToBody.put(gameObjectId, bodyId);
        bodyToBodyId.put(body, bodyId);
        return bodyId;
    }

    public void unregisterBody(long bodyId) {
        Body body = bodies.remove(bodyId);
        bodyTypes.remove(bodyId);
        Long gameObjectId = bodyToGameObject.remove(bodyId);
        if (gameObjectId != null) {
            gameObjectToBody.remove(gameObjectId);
        }
        if (body != null) {
            bodyToBodyId.remove(body);
        }
    }

    public Body getBody(long bodyId) {
        return bodies.get(bodyId);
    }

    public long getBodyId(Body body) {
        Long id = bodyToBodyId.get(body);
        return id != null ? id : -1;
    }

    public long getGameObjectId(long bodyId) {
        Long id = bodyToGameObject.get(bodyId);
        return id != null ? id : -1;
    }

    public Long getBodyIdByGameObjectId(long gameObjectId) {
        return gameObjectToBody.get(gameObjectId);
    }

    public BodyType getBodyType(long bodyId) {
        return bodyTypes.get(bodyId);
    }

    public List<Long> getAllBodyIds() {
        return new ArrayList<>(bodies.keySet());
    }

    public void clear() {
        bodies.clear();
        bodyTypes.clear();
        bodyToGameObject.clear();
        gameObjectToBody.clear();
        bodyToBodyId.clear();
    }
}
