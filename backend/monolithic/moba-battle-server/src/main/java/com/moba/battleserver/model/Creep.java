package com.moba.battleserver.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class Creep {
    private long creepId;
    private int campId;
    private int teamId;
    private int x;
    private int y;
    private int targetX;
    private int targetY;
    private int hp;
    private int maxHp;
    private int damage;
    private int moveSpeed;
    private int attackRange;
    private int attackSpeed;
    private long lastAttackFrame;
    private CreepState state;
    private CreepType creepType;
    private int level;
    private long respawnTime;
    private boolean isAlive;

    private Long currentTarget;
    private List<Waypoint> waypoints;

    public enum CreepState {
        IDLE,
        MOVING,
        ATTACKING,
        RETURNING,
        DEAD
    }

    public enum CreepType {
        MELEE,
        RANGED,
        SIEGE,
        SUPER,
        MEGA
    }

    public static Creep createCreep(long creepId, int campId, int teamId, int x, int y, CreepType type, int level) {
        Creep creep = new Creep();
        creep.setCreepId(creepId);
        creep.setCampId(campId);
        creep.setTeamId(teamId);
        creep.setX(x);
        creep.setY(y);
        creep.setTargetX(x);
        creep.setTargetY(y);
        creep.setCreepType(type);
        creep.setLevel(level);
        creep.setState(CreepState.IDLE);
        creep.setAlive(true);
        creep.setRespawnTime(60000);

        int baseHp = 500 + level * 150;
        int baseDamage = 20 + level * 10;

        switch (type) {
            case MELEE:
                creep.setMaxHp(baseHp);
                creep.setHp(baseHp);
                creep.setDamage(baseDamage);
                creep.setMoveSpeed(280);
                creep.setAttackRange(100);
                creep.setAttackSpeed(1000);
                break;
            case RANGED:
                creep.setMaxHp(baseHp * 3 / 4);
                creep.setHp((int) (baseHp * 0.75));
                creep.setDamage((int) (baseDamage * 1.2));
                creep.setMoveSpeed(260);
                creep.setAttackRange(400);
                creep.setAttackSpeed(1200);
                break;
            case SIEGE:
                creep.setMaxHp(baseHp / 2);
                creep.setHp(baseHp / 2);
                creep.setDamage(baseDamage * 2);
                creep.setMoveSpeed(220);
                creep.setAttackRange(500);
                creep.setAttackSpeed(2000);
                break;
            case SUPER:
                creep.setMaxHp(baseHp * 3);
                creep.setHp(baseHp * 3);
                creep.setDamage((int) (baseDamage * 1.5));
                creep.setMoveSpeed(250);
                creep.setAttackRange(100);
                creep.setAttackSpeed(900);
                break;
            case MEGA:
                creep.setMaxHp(baseHp * 5);
                creep.setHp(baseHp * 5);
                creep.setDamage(baseDamage * 2);
                creep.setMoveSpeed(220);
                creep.setAttackRange(100);
                creep.setAttackSpeed(800);
                break;
        }

        creep.setWaypoints(new ArrayList<>());
        return creep;
    }

    public void takeDamage(int damage) {
        this.hp = Math.max(0, this.hp - damage);
        if (this.hp <= 0) {
            this.state = CreepState.DEAD;
            this.isAlive = false;
            log.debug("Creep {} killed (type={}, level={})", creepId, creepType, level);
        }
    }

    public void heal(int amount) {
        this.hp = Math.min(maxHp, this.hp + amount);
    }

    public void respawn(int x, int y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.hp = this.maxHp;
        this.state = CreepState.IDLE;
        this.isAlive = true;
        this.currentTarget = null;
    }

    public boolean canAttack(long currentFrame) {
        return currentFrame - lastAttackFrame >= attackSpeed / 66;
    }

    public void attack(long currentFrame) {
        this.lastAttackFrame = currentFrame;
    }

    public int distanceTo(int px, int py) {
        int dx = px - this.x;
        int dy = py - this.y;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    public void moveTo(int px, int py) {
        int dx = px - this.x;
        int dy = py - this.y;
        int dist = (int) Math.sqrt(dx * dx + dy * dy);

        if (dist > 0) {
            int moveStep = Math.min(moveSpeed / 10, dist);
            this.x += dx * moveStep / dist;
            this.y += dy * moveStep / dist;
        }
    }

    @Data
    public static class Waypoint {
        private int x;
        private int y;
        private int waitTime;

        public Waypoint(int x, int y, int waitTime) {
            this.x = x;
            this.y = y;
            this.waitTime = waitTime;
        }
    }
}
