package com.moba.battleserver.ai;

import com.moba.battleserver.model.BattlePlayer;
import com.moba.battleserver.model.BattleSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
public class AIBot {
    private final long botId;
    private final BattlePlayer battlePlayer;
    private final int aiLevel;
    private final BattleSession session;

    private AIState currentState;
    private AIState previousState;
    private long stateStartFrame;
    private long lastDecisionFrame;

    private Long currentTargetId;
    private int[] patrolPoints;
    private int currentPatrolIndex;

    private long lastMoveFrame;
    private long lastAttackFrame;
    private long lastSkillCastFrame;

    private Map<Long, Float> threatTable;

    public static final int DEFAULT_AI_LEVEL = 5;

    public AIBot(long botId, BattlePlayer battlePlayer, int aiLevel, BattleSession session) {
        this.botId = botId;
        this.battlePlayer = battlePlayer;
        this.aiLevel = aiLevel;
        this.session = session;
        this.currentState = AIState.IDLE;
        this.previousState = AIState.IDLE;
        this.stateStartFrame = 0;
        this.lastDecisionFrame = 0;
        this.currentTargetId = null;
        this.patrolPoints = new int[0];
        this.currentPatrolIndex = 0;
        this.lastMoveFrame = 0;
        this.lastAttackFrame = 0;
        this.lastSkillCastFrame = 0;
        this.threatTable = new HashMap<>();
    }

    public void setState(AIState newState, long currentFrame) {
        if (this.currentState != newState) {
            this.previousState = this.currentState;
            this.currentState = newState;
            this.stateStartFrame = currentFrame;
            log.debug("Bot {} state changed: {} -> {}", botId, previousState, newState);
        }
    }

    public AIState getState() {
        return currentState;
    }

    public long getStateDuration(long currentFrame) {
        return currentFrame - stateStartFrame;
    }

    public BattlePlayer getBattlePlayer() {
        return battlePlayer;
    }

    public int getTeamId() {
        return battlePlayer.getTeamId();
    }

    public int getPositionX() {
        return battlePlayer.getPosition().x;
    }

    public int getPositionY() {
        return battlePlayer.getPosition().y;
    }

    public boolean isDead() {
        return battlePlayer.isDead();
    }

    public int getCurrentHp() {
        return battlePlayer.getCurrentHp();
    }

    public int getMaxHp() {
        return battlePlayer.getMaxHp();
    }

    public float getHpPercent() {
        return (float) battlePlayer.getCurrentHp() / battlePlayer.getMaxHp();
    }

    public int getAttackRange() {
        return battlePlayer.getAttackPower() > 150 ? 150 : 200;
    }

    public int getMoveSpeed() {
        return battlePlayer.getMoveSpeed();
    }

    public void setTarget(Long targetId) {
        this.currentTargetId = targetId;
    }

    public Long getTarget() {
        return currentTargetId;
    }

    public BattlePlayer getTargetPlayer() {
        if (currentTargetId == null) return null;
        return session.getPlayer(currentTargetId);
    }

    public void updateThreatTable(long playerId, float threat) {
        threatTable.merge(playerId, threat, Float::sum);
    }

    public Float getThreatLevel(long playerId) {
        return threatTable.getOrDefault(playerId, 0f);
    }

    public void clearThreatTable() {
        threatTable.clear();
    }

    public void addPatrolPoint(int x, int y) {
        int[] newPoints = Arrays.copyOf(patrolPoints, patrolPoints.length + 2);
        newPoints[newPoints.length - 2] = x;
        newPoints[newPoints.length - 1] = y;
        this.patrolPoints = newPoints;
    }

    public int[] getNextPatrolPoint() {
        if (patrolPoints.length == 0) return null;
        int[] point = new int[]{patrolPoints[currentPatrolIndex], patrolPoints[currentPatrolIndex + 1]};
        currentPatrolIndex = (currentPatrolIndex + 2) % patrolPoints.length;
        return point;
    }

    public boolean canAttack(long currentFrame) {
        int attackSpeed = 1000 / Math.max(1, battlePlayer.getAttackPower() / 50);
        return currentFrame - lastAttackFrame >= attackSpeed / 66;
    }

    public boolean canMove(long currentFrame) {
        return currentFrame - lastMoveFrame >= 1;
    }

    public boolean canCastSkill(long currentFrame, int skillId) {
        BattlePlayer.Skill skill = battlePlayer.getSkills().get(skillId);
        if (skill == null) return false;
        return currentFrame - lastSkillCastFrame >= skill.getCooldown() / 66;
    }

    public void recordAttack(long currentFrame) {
        this.lastAttackFrame = currentFrame;
    }

    public void recordMove(long currentFrame) {
        this.lastMoveFrame = currentFrame;
    }

    public void recordSkillCast(long currentFrame) {
        this.lastSkillCastFrame = currentFrame;
    }

    public List<Long> getNearbyEnemies(int radius) {
        List<Long> enemies = new ArrayList<>();
        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            if (player.getTeamId() != getTeamId() && !player.isDead()) {
                int dx = player.getPosition().x - getPositionX();
                int dy = player.getPosition().y - getPositionY();
                int distance = (int) Math.sqrt(dx * dx + dy * dy);
                if (distance <= radius) {
                    enemies.add(entry.getKey());
                }
            }
        }
        return enemies;
    }

    public List<Long> getNearbyAllies(int radius) {
        List<Long> allies = new ArrayList<>();
        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            if (player.getTeamId() == getTeamId() && player.getPlayerId() != botId && !player.isDead()) {
                int dx = player.getPosition().x - getPositionX();
                int dy = player.getPosition().y - getPositionY();
                int distance = (int) Math.sqrt(dx * dx + dy * dy);
                if (distance <= radius) {
                    allies.add(entry.getKey());
                }
            }
        }
        return allies;
    }

    public BattlePlayer getNearestEnemy() {
        BattlePlayer nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            if (player.getTeamId() != getTeamId() && !player.isDead()) {
                int dx = player.getPosition().x - getPositionX();
                int dy = player.getPosition().y - getPositionY();
                int dist = (int) Math.sqrt(dx * dx + dy * dy);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    public BattlePlayer getLowestHpEnemy() {
        BattlePlayer lowest = null;
        int lowestHp = Integer.MAX_VALUE;

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            if (player.getTeamId() != getTeamId() && !player.isDead()) {
                if (player.getCurrentHp() < lowestHp) {
                    lowestHp = player.getCurrentHp();
                    lowest = player;
                }
            }
        }
        return lowest;
    }

    public int distanceTo(int x, int y) {
        int dx = x - getPositionX();
        int dy = y - getPositionY();
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    public int distanceTo(BattlePlayer target) {
        return distanceTo(target.getPosition().x, target.getPosition().y);
    }

    public static AIBot createBot(long botId, int heroId, int teamId, BattleSession session, int aiLevel) {
        com.moba.battleserver.model.HeroConfig heroConfig = com.moba.battleserver.model.HeroConfig.getHeroConfig(heroId);
        BattlePlayer battlePlayer = new BattlePlayer(botId, heroId, teamId, heroConfig);
        session.addPlayer(botId, battlePlayer);
        return new AIBot(botId, battlePlayer, aiLevel, session);
    }
}
