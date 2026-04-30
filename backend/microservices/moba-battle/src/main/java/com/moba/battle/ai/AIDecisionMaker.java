package com.moba.battle.ai;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.SkillConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class AIDecisionMaker {
    private final AIBot bot;
    private final int aiLevel;
    private final Random random;

    private static final int DECISION_INTERVAL_FRAMES = 3;
    private static final int ATTACK_RANGE = 200;
    private static final int SKILL_RANGE = 400;
    private static final int RETREAT_HP_PERCENT = 30;
    private static final int CHASE_RANGE = 800;

    public AIDecisionMaker(AIBot bot) {
        this.bot = bot;
        this.aiLevel = bot.getAiLevel();
        this.random = new Random();
    }

    public AIState decide(long currentFrame) {
        if (bot.isDead()) {
            return AIState.REVIVE;
        }

        if (currentFrame - bot.getLastDecisionFrame() < DECISION_INTERVAL_FRAMES) {
            return bot.getState();
        }
        bot.setLastDecisionFrame(currentFrame);

        AIState currentState = bot.getState();

        if (shouldRetreat()) {
            bot.setState(AIState.RETREAT, currentFrame);
            return AIState.RETREAT;
        }

        BattlePlayer nearestEnemy = bot.getNearestEnemy();
        if (nearestEnemy == null) {
            if (currentState == AIState.IDLE || currentState == AIState.PATROL) {
                if (hasPatrolPoints()) {
                    bot.setState(AIState.PATROL, currentFrame);
                    return AIState.PATROL;
                } else {
                    bot.setState(AIState.IDLE, currentFrame);
                    return AIState.IDLE;
                }
            }
            return currentState;
        }

        int distanceToEnemy = bot.distanceTo(nearestEnemy);

        if (shouldCastSkill(nearestEnemy, distanceToEnemy)) {
            bot.setTarget(nearestEnemy.getPlayerId());
            bot.setState(AIState.CAST_SKILL, currentFrame);
            return AIState.CAST_SKILL;
        }

        if (distanceToEnemy <= ATTACK_RANGE) {
            if (bot.canAttack(currentFrame)) {
                bot.setTarget(nearestEnemy.getPlayerId());
                bot.setState(AIState.ATTACK, currentFrame);
                return AIState.ATTACK;
            }
        }

        if (distanceToEnemy <= CHASE_RANGE && shouldChase(nearestEnemy)) {
            bot.setTarget(nearestEnemy.getPlayerId());
            bot.setState(AIState.CHASE, currentFrame);
            return AIState.CHASE;
        }

        if (distanceToEnemy > CHASE_RANGE) {
            if (shouldGuard()) {
                bot.setState(AIState.GUARD, currentFrame);
                return AIState.GUARD;
            }
        }

        if (hasPatrolPoints() && currentState == AIState.IDLE) {
            bot.setState(AIState.PATROL, currentFrame);
            return AIState.PATROL;
        }

        return currentState;
    }

    private boolean shouldRetreat() {
        float hpPercent = bot.getHpPercent();
        int retreatThreshold = Math.max(RETREAT_HP_PERCENT - (aiLevel * 2), 10);
        return hpPercent < retreatThreshold / 100f;
    }

    private boolean shouldChase(BattlePlayer enemy) {
        float hpPercent = bot.getHpPercent();

        if (hpPercent < 0.3f) {
            return random.nextInt(100) < 20 + aiLevel * 5;
        }

        if (enemy.getCurrentHp() < bot.getBattlePlayer().getAttackPower() * 2) {
            return true;
        }

        return random.nextInt(100) < 40 + aiLevel * 10;
    }

    private boolean shouldCastSkill(BattlePlayer target, int distance) {
        if (!bot.canCastSkill(bot.getLastDecisionFrame(), 2)) {
            return false;
        }

        if (distance > SKILL_RANGE) {
            return false;
        }

        int hpPercent = target.getCurrentHp() * 100 / target.getMaxHp();
        if (hpPercent > 70 && random.nextInt(100) > 30) {
            return false;
        }

        return random.nextInt(100) < 50 + aiLevel * 8;
    }

    private boolean shouldGuard() {
        BattlePlayer lowestAlly = getLowestHpAlly();
        if (lowestAlly == null) return false;

        int allyHpPercent = lowestAlly.getCurrentHp() * 100 / lowestAlly.getMaxHp();
        return allyHpPercent < 40;
    }

    private BattlePlayer getLowestHpAlly() {
        BattlePlayer lowest = null;
        int lowestHp = Integer.MAX_VALUE;

        for (Long allyId : bot.getNearbyAllies(500)) {
            BattlePlayer ally = bot.getSession().getPlayer(allyId);
            if (ally != null && ally.getCurrentHp() < lowestHp) {
                lowestHp = ally.getCurrentHp();
                lowest = ally;
            }
        }
        return lowest;
    }

    private boolean hasPatrolPoints() {
        return bot.getPatrolPoints() != null && bot.getPatrolPoints().length > 0;
    }

    public int[] decideMoveTarget() {
        AIState state = bot.getState();

        switch (state) {
            case PATROL:
                return bot.getNextPatrolPoint();

            case CHASE:
            case MOVE_TO_TARGET:
                BattlePlayer target = bot.getTargetPlayer();
                if (target != null) {
                    return new int[]{target.getPosition().x, target.getPosition().y};
                }
                break;

            case RETREAT:
                return calculateRetreatPosition();

            case GUARD:
                BattlePlayer ally = getLowestHpAlly();
                if (ally != null) {
                    return new int[]{ally.getPosition().x, ally.getPosition().y};
                }
                break;

            case FOLLOW_ALLY:
                return getAllyPosition();

            default:
                break;
        }

        return null;
    }

    private int[] calculateRetreatPosition() {
        BattlePlayer nearestAlly = null;
        int nearestDist = Integer.MAX_VALUE;

        for (Long allyId : bot.getNearbyAllies(1000)) {
            BattlePlayer ally = bot.getSession().getPlayer(allyId);
            if (ally != null) {
                int dist = bot.distanceTo(ally);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestAlly = ally;
                }
            }
        }

        if (nearestAlly != null) {
            int dx = nearestAlly.getPosition().x - bot.getPositionX();
            int dy = nearestAlly.getPosition().y - bot.getPositionY();
            int dist = (int) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                return new int[]{
                        bot.getPositionX() + dx * 300 / dist,
                        bot.getPositionY() + dy * 300 / dist
                };
            }
        }

        return new int[]{
                bot.getSession().getMapId() * 1000,
                bot.getTeamId() * 1000
        };
    }

    private int[] getAllyPosition() {
        List<Long> allies = bot.getNearbyAllies(1000);
        if (!allies.isEmpty()) {
            BattlePlayer ally = bot.getSession().getPlayer(allies.get(0));
            if (ally != null) {
                return new int[]{ally.getPosition().x, ally.getPosition().y};
            }
        }
        return null;
    }

    public int decideSkillToCast() {
        if (!bot.canCastSkill(bot.getLastDecisionFrame(), 2)) {
            return -1;
        }

        BattlePlayer target = bot.getTargetPlayer();
        if (target == null) return -1;

        int distance = bot.distanceTo(target);
        int skillRange = 400;

        if (distance <= skillRange) {
            int randomVal = random.nextInt(100);
            if (randomVal < 60) {
                return 2;
            }
        }

        return -1;
    }

    public Long decideAttackTarget() {
        if (bot.getTarget() != null) {
            BattlePlayer currentTarget = bot.getSession().getPlayer(bot.getTarget());
            if (currentTarget != null && !currentTarget.isDead() &&
                    currentTarget.getTeamId() != bot.getTeamId()) {
                int dist = bot.distanceTo(currentTarget);
                if (dist <= ATTACK_RANGE * 1.5) {
                    return bot.getTarget();
                }
            }
        }

        List<Long> nearbyEnemies = bot.getNearbyEnemies(ATTACK_RANGE);
        if (nearbyEnemies.isEmpty()) {
            nearbyEnemies = bot.getNearbyEnemies(ATTACK_RANGE * 2);
        }

        if (nearbyEnemies.isEmpty()) return null;

        if (aiLevel >= 7) {
            return selectLowestHpTarget(nearbyEnemies);
        } else if (aiLevel >= 4) {
            return selectBalancedTarget(nearbyEnemies);
        } else {
            return selectNearestTarget(nearbyEnemies);
        }
    }

    private Long selectLowestHpTarget(List<Long> enemies) {
        BattlePlayer lowest = null;
        Long lowestId = null;

        for (Long enemyId : enemies) {
            BattlePlayer enemy = bot.getSession().getPlayer(enemyId);
            if (enemy != null && (lowest == null || enemy.getCurrentHp() < lowest.getCurrentHp())) {
                lowest = enemy;
                lowestId = enemyId;
            }
        }
        return lowestId;
    }

    private Long selectNearestTarget(List<Long> enemies) {
        BattlePlayer nearest = null;
        Long nearestId = null;
        int nearestDist = Integer.MAX_VALUE;

        for (Long enemyId : enemies) {
            BattlePlayer enemy = bot.getSession().getPlayer(enemyId);
            if (enemy != null) {
                int dist = bot.distanceTo(enemy);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = enemy;
                    nearestId = enemyId;
                }
            }
        }
        return nearestId;
    }

    private Long selectBalancedTarget(List<Long> enemies) {
        BattlePlayer best = null;
        Long bestId = null;
        int bestScore = -1;

        for (Long enemyId : enemies) {
            BattlePlayer enemy = bot.getSession().getPlayer(enemyId);
            if (enemy != null) {
                int dist = bot.distanceTo(enemy);
                int hpScore = enemy.getMaxHp() - enemy.getCurrentHp();
                int distScore = 1000 - dist;
                int score = hpScore / 10 + distScore / 2 + random.nextInt(100);

                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                    bestId = enemyId;
                }
            }
        }
        return bestId;
    }
}
