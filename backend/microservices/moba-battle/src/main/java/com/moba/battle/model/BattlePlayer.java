package com.moba.battle.model;

import lombok.Data;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class BattlePlayer {
    private final long playerId;
    private final int heroId;
    private final int teamId;
    private Position position;
    private int currentHp;
    private int maxHp;
    private int currentMp;
    private int maxMp;
    private int level;
    private int gold;
    private int killCount;
    private int deathCount;
    private int assistCount;
    private boolean isDead;
    private long deadTimer;
    private int moveSpeed;
    private int attackPower;
    private int defense;
    private int collisionRadius;
    private Map<Integer, Skill> skills;
    private Map<Integer, Integer> items;

    public BattlePlayer(long playerId, int heroId, int teamId, HeroConfig heroConfig) {
        this.playerId = playerId;
        this.heroId = heroId;
        this.teamId = teamId;
        this.position = new Position(0, 0);
        this.currentHp = heroConfig != null ? heroConfig.getBaseHp() : 5000;
        this.maxHp = heroConfig != null ? heroConfig.getBaseHp() : 5000;
        this.currentMp = heroConfig != null ? heroConfig.getBaseMp() : 2000;
        this.maxMp = heroConfig != null ? heroConfig.getBaseMp() : 2000;
        this.level = 1;
        this.gold = 0;
        this.killCount = 0;
        this.deathCount = 0;
        this.assistCount = 0;
        this.isDead = false;
        this.deadTimer = 0;
        this.moveSpeed = heroConfig != null ? heroConfig.getMoveSpeed() : 350;
        this.attackPower = heroConfig != null ? heroConfig.getBaseAttack() : 100;
        this.defense = heroConfig != null ? heroConfig.getBaseDefense() : 50;
        this.collisionRadius = heroConfig != null ? heroConfig.getCollisionRadius() : 50;
        this.skills = new ConcurrentHashMap<>();
        this.items = new ConcurrentHashMap<>();
    }

    public void takeDamage(int damage) {
        int actualDamage = Math.max(1, damage - defense / 10);
        this.currentHp -= actualDamage;
        if (this.currentHp <= 0) {
            this.currentHp = 0;
            this.isDead = true;
            this.deadTimer = System.currentTimeMillis() + getRespawnTime();
            this.deathCount++;
        }
    }

    public void heal(int amount) {
        this.currentHp = Math.min(maxHp, currentHp + amount);
    }

    public void addGold(int amount) {
        this.gold += amount;
    }

    public void levelUp() {
        this.level++;
        this.maxHp += 200;
        this.currentHp = maxHp;
        this.maxMp += 100;
        this.currentMp = maxMp;
        this.attackPower += 10;
        this.defense += 5;
    }

    public void respawn() {
        this.isDead = false;
        this.currentHp = maxHp;
        this.currentMp = maxMp;
    }

    public void updateDeadState() {
        if (isDead && System.currentTimeMillis() > deadTimer) {
            respawn();
        }
    }

    private long getRespawnTime() {
        return 5000 + level * 2000;
    }

    @Data
    public static class Position {
        public int x;
        public int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int distanceTo(Position other) {
            int dx = this.x - other.x;
            int dy = this.y - other.y;
            return (int) Math.sqrt(dx * dx + dy * dy);
        }
    }

    @Data
    public static class Skill {
        private int skillId;
        private int level;
        private long cooldown;
        private long lastCastTime;
        private int mpCost;

        public boolean canCast(long currentTime) {
            return currentTime - lastCastTime >= cooldown;
        }
    }
}
