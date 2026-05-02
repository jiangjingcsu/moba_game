package com.moba.battle.ai;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.FrameInput;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AIController {
    private final Map<String, Map<Long, AIBot>> roomBots;
    private final Map<Long, AIDecisionMaker> decisionMakers;
    private final Map<Long, Long> botLastTickFrame;

    private final int minBotLevel;
    private final int maxBotLevel;
    private final int defaultBotCountPerTeam;
    private final int aiTickIntervalFrames;

    public AIController(ServerConfig serverConfig) {
        this.roomBots = new ConcurrentHashMap<>();
        this.decisionMakers = new ConcurrentHashMap<>();
        this.botLastTickFrame = new ConcurrentHashMap<>();
        this.minBotLevel = serverConfig.getMinBotLevel();
        this.maxBotLevel = serverConfig.getMaxBotLevel();
        this.defaultBotCountPerTeam = serverConfig.getDefaultBotCountPerTeam();
        this.aiTickIntervalFrames = serverConfig.getAiTickIntervalFrames();
    }

    public static AIController getInstance() {
        return SpringContextHolder.getBean(AIController.class);
    }

    public List<AIBot> createBotsForBattle(String battleId, BattleSession session,
                                            int humanPerTeam, int botPerTeam, int botLevel) {
        List<AIBot> bots = new ArrayList<>();
        int totalPlayers = session.getBattlePlayers().size();
        int teamCount = session.getTeamCount();
        int playersPerTeam = totalPlayers / teamCount;

        int existingHumans = countExistingHumans(session);
        int neededBots = (playersPerTeam - existingHumans) * teamCount;
        neededBots = Math.min(neededBots, botPerTeam * teamCount);

        for (int i = 0; i < neededBots; i++) {
            int teamId = i % teamCount;
            long botId = -1000 - i;

            int heroId = selectHeroForBot(i, botLevel);
            AIBot bot = AIBot.createBot(botId, heroId, teamId, session, botLevel);

            setupPatrolPoints(bot, session, teamId);

            addBotToRoom(battleId, bot);

            decisionMakers.put(botId, new AIDecisionMaker(bot));
            botLastTickFrame.put(botId, 0L);

            bots.add(bot);
            log.info("为战斗{}创建AI机器人{} (队伍={}, 英雄={}, 等级={})",
                    botId, battleId, teamId, heroId, botLevel);
        }

        return bots;
    }

    private int countExistingHumans(BattleSession session) {
        int count = 0;
        for (Long playerId : session.getBattlePlayers().keySet()) {
            if (playerId > 0) {
                count++;
            }
        }
        return count;
    }

    private int selectHeroForBot(int index, int level) {
        int[] assassinHeroes = {3};
        int[] mageHeroes = {2};
        int[] warriorHeroes = {1};
        int[] tankHeroes = {5};
        int[] supportHeroes = {6};
        int[] archerHeroes = {4};

        if (level >= 7) {
            return assassinHeroes[index % assassinHeroes.length];
        } else if (level >= 5) {
            return mageHeroes[index % mageHeroes.length];
        } else if (level >= 3) {
            return warriorHeroes[index % warriorHeroes.length];
        } else {
            int[] allHeroes = {1, 2, 3, 4, 5, 6};
            return allHeroes[index % allHeroes.length];
        }
    }

    private void setupPatrolPoints(AIBot bot, BattleSession session, int teamId) {
        int baseX = (teamId + 1) * 2500;
        int baseY = 5000;

        bot.addPatrolPoint(baseX - 500, baseY - 500);
        bot.addPatrolPoint(baseX + 500, baseY - 500);
        bot.addPatrolPoint(baseX + 500, baseY + 500);
        bot.addPatrolPoint(baseX - 500, baseY + 500);

        int midX = 5000;
        int midY = 5000;
        bot.addPatrolPoint(midX, midY);
    }

    private void addBotToRoom(String battleId, AIBot bot) {
        roomBots.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>())
                .put(bot.getBotId(), bot);
    }

    public List<FrameInput> updateBots(String battleId, long currentFrame) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        if (bots == null || bots.isEmpty()) {
            return Collections.emptyList();
        }

        List<FrameInput> inputs = new ArrayList<>();

        for (AIBot bot : bots.values()) {
            if (bot.isDead()) {
                continue;
            }

            Long lastTick = botLastTickFrame.get(bot.getBotId());
            if (lastTick != null && currentFrame - lastTick < aiTickIntervalFrames) {
                continue;
            }
            botLastTickFrame.put(bot.getBotId(), currentFrame);

            AIDecisionMaker decisionMaker = decisionMakers.get(bot.getBotId());
            if (decisionMaker == null) {
                decisionMaker = new AIDecisionMaker(bot);
                decisionMakers.put(bot.getBotId(), decisionMaker);
            }

            AIState newState = decisionMaker.decide(currentFrame);

            FrameInput input = processBotState(bot, newState, decisionMaker, currentFrame);
            if (input != null) {
                inputs.add(input);
            }
        }

        return inputs;
    }

    private FrameInput processBotState(AIBot bot, AIState state,
                                       AIDecisionMaker decisionMaker, long currentFrame) {
        switch (state) {
            case IDLE:
                return createIdleInput(bot);

            case PATROL:
                return createPatrolInput(bot, decisionMaker, currentFrame);

            case MOVE_TO_TARGET:
            case CHASE:
                return createChaseInput(bot, decisionMaker, currentFrame);

            case ATTACK:
                return createAttackInput(bot, decisionMaker, currentFrame);

            case CAST_SKILL:
                return createSkillInput(bot, decisionMaker, currentFrame);

            case RETREAT:
                return createRetreatInput(bot, decisionMaker, currentFrame);

            case GUARD:
            case FOLLOW_ALLY:
                return createGuardInput(bot, decisionMaker, currentFrame);

            default:
                return null;
        }
    }

    private FrameInput createIdleInput(AIBot bot) {
        return null;
    }

    private FrameInput createPatrolInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        if (!bot.canMove(currentFrame)) return null;

        int[] target = decisionMaker.decideMoveTarget();
        if (target == null) return null;

        int[] currentPatrol = bot.getNextPatrolPoint();
        if (currentPatrol == null) return null;

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.MOVE);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|MOVE|%d|%d", bot.getBotId(), currentPatrol[0], currentPatrol[1]);
        input.setData(data.getBytes());

        bot.recordMove(currentFrame);
        return input;
    }

    private FrameInput createChaseInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        if (!bot.canMove(currentFrame)) return null;

        int[] targetPos = decisionMaker.decideMoveTarget();
        if (targetPos == null) return null;

        int attackRange = bot.getAttackRange();
        BattlePlayer target = bot.getTargetPlayer();
        if (target != null) {
            int dist = bot.distanceTo(target);
            if (dist <= attackRange * 2) {
                return null;
            }
        }

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.MOVE);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|MOVE|%d|%d", bot.getBotId(), targetPos[0], targetPos[1]);
        input.setData(data.getBytes());

        bot.recordMove(currentFrame);
        return input;
    }

    private FrameInput createAttackInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        if (!bot.canAttack(currentFrame)) return null;

        Long targetId = decisionMaker.decideAttackTarget();
        if (targetId == null) return null;

        bot.setTarget(targetId);

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.ATTACK);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|ATTACK|%d", bot.getBotId(), targetId);
        input.setData(data.getBytes());

        bot.recordAttack(currentFrame);
        return input;
    }

    private FrameInput createSkillInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        int skillId = decisionMaker.decideSkillToCast();
        if (skillId <= 0) return null;

        BattlePlayer target = bot.getTargetPlayer();
        if (target == null) return null;

        int targetX = target.getPosition().x;
        int targetY = target.getPosition().y;

        int facing = calculateFacing(bot.getPositionX(), bot.getPositionY(), targetX, targetY);

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.SKILL_CAST);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|%d|%d|%d|%d", bot.getBotId(), skillId, targetX, targetY, facing);
        input.setData(data.getBytes());

        bot.recordSkillCast(currentFrame);
        return input;
    }

    private FrameInput createRetreatInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        if (!bot.canMove(currentFrame)) return null;

        int[] retreatPos = decisionMaker.decideMoveTarget();
        if (retreatPos == null) return null;

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.MOVE);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|MOVE|%d|%d", bot.getBotId(), retreatPos[0], retreatPos[1]);
        input.setData(data.getBytes());

        bot.recordMove(currentFrame);
        return input;
    }

    private FrameInput createGuardInput(AIBot bot, AIDecisionMaker decisionMaker, long currentFrame) {
        if (!bot.canMove(currentFrame)) return null;

        int[] guardPos = decisionMaker.decideMoveTarget();
        if (guardPos == null) return null;

        int dist = bot.distanceTo(guardPos[0], guardPos[1]);
        if (dist < 200) {
            return createAttackInput(bot, decisionMaker, currentFrame);
        }

        FrameInput input = new FrameInput();
        input.setPlayerId(bot.getBotId());
        input.setType(FrameInput.InputType.MOVE);
        input.setFrameNumber((int) currentFrame);

        String data = String.format("%d|MOVE|%d|%d", bot.getBotId(), guardPos[0], guardPos[1]);
        input.setData(data.getBytes());

        bot.recordMove(currentFrame);
        return input;
    }

    private int calculateFacing(int fromX, int fromY, int toX, int toY) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        return (int) Math.toDegrees(angle);
    }

    public void removeBotsFromRoom(String battleId) {
        Map<Long, AIBot> bots = roomBots.remove(battleId);
        if (bots != null) {
            for (AIBot bot : bots.values()) {
                decisionMakers.remove(bot.getBotId());
                botLastTickFrame.remove(bot.getBotId());
            }
            log.info("从战斗{}移除{}个机器人", battleId, bots.size());
        }
    }

    public int getBotCount(String battleId) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        return bots != null ? bots.size() : 0;
    }

    public boolean isInitializedForBattle(String battleId) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        return bots != null && !bots.isEmpty();
    }

    public List<AIBot> getBots(String battleId) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        return bots != null ? new ArrayList<>(bots.values()) : Collections.emptyList();
    }

    public void updateBotTarget(String battleId, long botId, Long targetId) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        if (bots != null) {
            AIBot bot = bots.get(botId);
            if (bot != null) {
                bot.setTarget(targetId);
            }
        }
    }

    public void notifyBotOfDeath(String battleId, long victimId, long killerId) {
        Map<Long, AIBot> bots = roomBots.get(battleId);
        if (bots == null) return;

        AIBot killerBot = bots.get(killerId);
        if (killerBot != null) {
            killerBot.updateThreatTable(victimId, -10f);
        }

        for (AIBot bot : bots.values()) {
            if (bot.getTarget() != null && bot.getTarget().equals(victimId)) {
                bot.setTarget(null);
                bot.setState(AIState.IDLE, 0);
            }
        }
    }
}
