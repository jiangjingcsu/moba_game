package com.moba.battle.battle;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.FrameInput;
import com.moba.battle.model.FrameState;
import com.moba.battle.model.SkillConfig;
import com.moba.battle.ai.AIController;
import com.moba.battle.ai.AIBot;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class LockstepEngine {
    private static final int MAX_FRAME_STATES = 3600;
    private static final int MAX_FRAME_HASHES = 1000;

    private final long battleId;
    private final BattleSession session;
    private final int tickRate;
    private final long tickIntervalMs;
    private final int inputDelayFrames;
    private final int hashCheckIntervalFrames;

    private volatile int currentFrame;
    private volatile boolean running;

    private final Map<Integer, List<FrameInput>> frameInputs;
    private final ConcurrentLinkedQueue<FrameInput> pendingInputs;

    private final List<FrameState> frameStates;
    private final Object frameStatesLock = new Object();
    private final Map<Integer, Long> frameHashes;

    private long lastTickTime;
    private final AtomicInteger syncErrorCount;

    private final Random randomGenerator;
    private final long randomSeed;

    private BattleEventListener eventListener;
    private AIController aiController;
    private boolean aiEnabled;

    private SkillCollisionSystem collisionSystem;
    private static final Map<Integer, SkillConfig> SKILL_CONFIGS = SkillConfig.loadAllSkills();

    public LockstepEngine(int tickRate, int defaultGridSize, long battleId) {
        this(battleId, null, tickRate, 2, 60);
    }

    public LockstepEngine(long battleId, BattleSession session, int tickRate, int inputDelayFrames, int hashCheckIntervalFrames) {
        this.battleId = battleId;
        this.session = session;
        this.tickRate = tickRate;
        this.tickIntervalMs = 1000 / tickRate;
        this.inputDelayFrames = inputDelayFrames;
        this.hashCheckIntervalFrames = hashCheckIntervalFrames;

        this.currentFrame = 0;
        this.running = false;

        this.frameInputs = new ConcurrentHashMap<>();
        this.pendingInputs = new ConcurrentLinkedQueue<>();
        this.frameStates = new ArrayList<>();
        this.frameHashes = new ConcurrentHashMap<>();

        this.lastTickTime = 0;
        this.syncErrorCount = new AtomicInteger(0);

        this.randomSeed = System.nanoTime();
        this.randomGenerator = new Random(randomSeed);
        this.aiEnabled = false;

        log.info("锁步引擎已创建: battleId={}, 帧率={}Hz, 输入延迟={}帧",
                battleId, tickRate, inputDelayFrames);
    }

    public void setEventListener(BattleEventListener listener) {
        this.eventListener = listener;
    }

    public void setCollisionSystem(SkillCollisionSystem system) {
        this.collisionSystem = system;
    }

    public void enableAI(AIController controller, int botLevel) {
        this.aiController = controller;
        this.aiEnabled = true;
        log.info("战斗{}启用AI, 机器人等级={}", battleId, botLevel);
    }

    public void disableAI() {
        this.aiEnabled = false;
        log.info("战斗{}禁用AI", battleId);
    }

    public boolean isAIEnabled() {
        return aiEnabled;
    }

    public void start() {
        running = true;
        lastTickTime = System.currentTimeMillis();
        log.info("锁步引擎已启动: battleId={}, 随机种子={}", battleId, randomSeed);
    }

    public void stop() {
        running = false;
        log.info("锁步引擎已停止: battleId={}, 总帧数={}", battleId, currentFrame);
    }

    public void tick() {
        if (!running) return;

        long now = System.currentTimeMillis();
        if (now - lastTickTime < tickIntervalMs) {
            return;
        }
        lastTickTime = now;

        currentFrame++;

        collectInputs();

        if (aiEnabled && aiController != null) {
            collectAIInputs();
        }

        executeGameLogic();

        FrameState state = captureState();
        synchronized (frameStatesLock) {
            if (frameStates.size() >= MAX_FRAME_STATES) {
                frameStates.subList(0, frameStates.size() - MAX_FRAME_STATES / 2).clear();
            }
            frameStates.add(state);
        }

        if (currentFrame % hashCheckIntervalFrames == 0) {
            long hash = state.computeHash();
            if (frameHashes.size() >= MAX_FRAME_HASHES) {
                int oldestFrame = frameHashes.keySet().stream().min(Integer::compareTo).orElse(0);
                frameHashes.keySet().removeIf(f -> f <= oldestFrame + 100);
            }
            frameHashes.put(currentFrame, hash);
            log.debug("帧{}哈希校验: {}", currentFrame, Long.toHexString(hash));
        }

        if (eventListener != null) {
            eventListener.onFrameUpdate(battleId, currentFrame, state);
        }

        checkGameOver();
    }

    private void collectInputs() {
        List<FrameInput> inputs = new ArrayList<>();

        for (long userId : session.getBattlePlayers().keySet()) {
            FrameInput input = pollInputForPlayer(userId);
            if (input != null) {
                input.setFrameNumber(currentFrame);
                inputs.add(input);
            }
        }

        frameInputs.put(currentFrame, inputs);
    }

    private void collectAIInputs() {
        List<FrameInput> aiInputs = aiController.updateBots(battleId, currentFrame);
        for (FrameInput input : aiInputs) {
            pendingInputs.offer(input);
        }
    }

    private FrameInput pollInputForPlayer(long userId) {
        Iterator<FrameInput> iterator = pendingInputs.iterator();
        while (iterator.hasNext()) {
            FrameInput input = iterator.next();
            if (input.getUserId() == userId) {
                iterator.remove();
                return input;
            }
        }
        return null;
    }

    public void submitInput(FrameInput input) {
        pendingInputs.offer(input);
    }

    public void submitInputs(List<FrameInput> inputs) {
        for (FrameInput input : inputs) {
            pendingInputs.offer(input);
        }
    }

    private void executeGameLogic() {
        List<FrameInput> inputs = frameInputs.getOrDefault(currentFrame, Collections.emptyList());

        for (FrameInput input : inputs) {
            BattlePlayer player = session.getPlayer(input.getUserId());
            if (player == null) continue;

            switch (input.getType()) {
                case MOVE:
                    handleMove(player, input);
                    break;
                case ATTACK:
                    handleAttack(player, input);
                    break;
                case SKILL_CAST:
                    handleSkillCast(player, input);
                    break;
                case USE_ITEM:
                    handleUseItem(player, input);
                    break;
                case BUY_ITEM:
                    handleBuyItem(player, input);
                    break;
            }
        }

        updateDeadPlayers();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private void handleMove(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON_MAPPER.readTree(input.getData());
            int targetX = node.get("x").asInt();
            int targetY = node.get("y").asInt();

            int dx = targetX - player.getPosition().x;
            int dy = targetY - player.getPosition().y;
            int distance = (int) Math.sqrt(dx * dx + dy * dy);

            int maxMove = player.getMoveSpeed() / tickRate;

            int newX, newY;
            if (distance <= maxMove) {
                newX = targetX;
                newY = targetY;
            } else {
                newX = player.getPosition().x + (dx * maxMove / Math.max(1, distance));
                newY = player.getPosition().y + (dy * maxMove / Math.max(1, distance));
            }

            player.getPosition().x = newX;
            player.getPosition().y = newY;

            if (collisionSystem != null) {
                collisionSystem.getGridDetector().updatePlayerPosition(player);
            }

            if (eventListener != null) {
                eventListener.onPlayerMove(battleId, player.getUserId(), newX, newY);
            }
        } catch (Exception e) {
            log.warn("移动数据解析失败: userId={}", player.getUserId());
        }
    }

    private void handleAttack(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON_MAPPER.readTree(input.getData());
            long targetId = node.get("targetId").asLong();
            BattlePlayer target = session.getPlayer(targetId);

            if (target == null || target.getTeamId() == player.getTeamId()) return;

            int damage = calculateDamage(player, target);
            target.takeDamage(damage);

            if (eventListener != null) {
                eventListener.onPlayerAttack(battleId, player.getUserId(), targetId, damage);
            }

            if (target.isDead()) {
                onPlayerKilled(player, target);
            }
        } catch (Exception e) {
            log.warn("攻击数据解析失败: userId={}", player.getUserId());
        }
    }

    private void handleSkillCast(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON_MAPPER.readTree(input.getData());
            int skillId = node.get("skillId").asInt();
            int targetX = node.get("x").asInt();
            int targetY = node.get("y").asInt();
            int facing = node.has("facing") ? node.get("facing").asInt() : 0;

            BattlePlayer.Skill skill = player.getSkills().get(skillId);
            if (skill == null) {
                skill = createDefaultSkill(skillId);
                player.getSkills().put(skillId, skill);
            }

            long currentTime = currentFrame * tickIntervalMs;
            if (!skill.canCast(currentTime)) {
                return;
            }

            if (player.getCurrentMp() < skill.getMpCost()) {
                return;
            }

            skill.setLastCastTime(currentTime);
            player.setCurrentMp(player.getCurrentMp() - skill.getMpCost());

            if (collisionSystem != null) {
                SkillConfig skillConfig = SKILL_CONFIGS.get(skillId);
                if (skillConfig == null) {
                    skillConfig = createDefaultSkillConfig(skillId);
                }

                long targetId = 0;
                SkillCollisionSystem.SkillCastInfo castInfo = skillConfig.toSkillCastInfo(
                        player, targetId, targetX, targetY, facing, currentFrame);

                List<Long> hitPlayers = collisionSystem.checkSkillHit(castInfo);

                for (Long hitUserId : hitPlayers) {
                    BattlePlayer hitPlayer = session.getPlayer(hitUserId);
                    if (hitPlayer != null && hitPlayer.getTeamId() != player.getTeamId()) {
                        int damage = calculateSkillDamage(player, hitPlayer, skill);
                        hitPlayer.takeDamage(damage);

                        if (eventListener != null) {
                            eventListener.onSkillCast(battleId, player.getUserId(), skillId, hitUserId, damage);
                        }

                        if (hitPlayer.isDead()) {
                            onPlayerKilled(player, hitPlayer);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("技能数据解析失败: userId={}", player.getUserId());
        }
    }

    private void handleUseItem(BattlePlayer player, FrameInput input) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON_MAPPER.readTree(input.getData());
            int itemId = node.get("itemId").asInt();
            player.getItems().merge(itemId, 1, Integer::sum);

            if (eventListener != null) {
                eventListener.onItemUse(battleId, player.getUserId(), itemId);
            }
        } catch (Exception e) {
            log.warn("道具使用数据解析失败: userId={}", player.getUserId());
        }
    }

    private void handleBuyItem(BattlePlayer player, FrameInput input) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON_MAPPER.readTree(input.getData());
            int itemId = node.get("itemId").asInt();
            int price = getItemPrice(itemId);

            if (player.getGold() >= price) {
                player.addGold(-price);
                player.getItems().merge(itemId, 1, Integer::sum);

                if (eventListener != null) {
                    eventListener.onItemBuy(battleId, player.getUserId(), itemId, price);
                }
            }
        } catch (Exception e) {
            log.warn("购买数据解析失败: userId={}", player.getUserId());
        }
    }

    private int calculateDamage(BattlePlayer attacker, BattlePlayer defender) {
        int baseAttack = attacker.getAttackPower();
        int defense = defender.getDefense();
        return Math.max(1, baseAttack - defense / 10);
    }

    private int calculateSkillDamage(BattlePlayer attacker, BattlePlayer defender, BattlePlayer.Skill skill) {
        int baseAttack = attacker.getAttackPower();
        int defense = defender.getDefense();
        float skillMultiplier = 1.0f + skill.getLevel() * 0.2f;
        return Math.max(1, (int)(baseAttack * skillMultiplier) - defense / 10);
    }

    private void onPlayerKilled(BattlePlayer killer, BattlePlayer victim) {
        killer.setKillCount(killer.getKillCount() + 1);
        killer.addGold(300);

        BattleSession.BattleEvent killEvent = new BattleSession.BattleEvent();
        killEvent.setTimestamp(System.currentTimeMillis());
        killEvent.setUserId(killer.getUserId());
        killEvent.setType(BattleSession.BattleEvent.EventType.KILL);
        killEvent.setData("target=" + victim.getUserId());
        session.recordEvent(killEvent);

        BattleSession.BattleEvent deathEvent = new BattleSession.BattleEvent();
        deathEvent.setTimestamp(System.currentTimeMillis());
        deathEvent.setUserId(victim.getUserId());
        deathEvent.setType(BattleSession.BattleEvent.EventType.DEATH);
        deathEvent.setData("killer=" + killer.getUserId());
        session.recordEvent(deathEvent);

        if (eventListener != null) {
            eventListener.onPlayerKill(battleId, killer.getUserId(), victim.getUserId());
        }
    }

    private void updateDeadPlayers() {
        for (BattlePlayer player : session.getBattlePlayers().values()) {
            if (player.isDead()) {
                player.updateDeadState();
                if (!player.isDead() && eventListener != null) {
                    eventListener.onPlayerRespawn(battleId, player.getUserId());
                }
            }
        }
    }

    private FrameState captureState() {
        FrameState state = new FrameState();
        state.setFrameNumber(currentFrame);

        Map<Long, FrameState.FixedPosition> positions = new HashMap<>();
        Map<Long, Integer> hpMap = new HashMap<>();
        Map<Long, Integer> mpMap = new HashMap<>();

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            positions.put(entry.getKey(), new FrameState.FixedPosition(
                    player.getPosition().x, player.getPosition().y));
            hpMap.put(entry.getKey(), player.getCurrentHp());
            mpMap.put(entry.getKey(), player.getCurrentMp());
        }

        state.setPlayerPositions(positions);
        state.setPlayerHp(hpMap);
        state.setPlayerMp(mpMap);
        state.setInputs(frameInputs.getOrDefault(currentFrame, Collections.emptyList()));

        return state;
    }

    private void checkGameOver() {
        Map<Integer, BattleSession.Team> teams = session.getTeams();
        int defeatedTeams = 0;

        for (BattleSession.Team team : teams.values()) {
            if (team.isDefeated()) {
                defeatedTeams++;
            }
        }

        if (defeatedTeams >= session.getTeamCount() - 1) {
            session.finish();
            stop();

            if (eventListener != null) {
                eventListener.onGameOver(battleId);
            }
        }
    }

    private BattlePlayer.Skill createDefaultSkill(int skillId) {
        BattlePlayer.Skill skill = new BattlePlayer.Skill();
        skill.setSkillId(skillId);
        skill.setLevel(1);
        skill.setCooldown(5000);
        skill.setMpCost(100);
        return skill;
    }

    private SkillConfig createDefaultSkillConfig(int skillId) {
        SkillConfig config = new SkillConfig();
        config.setSkillId(skillId);
        config.setSkillName("默认技能");
        config.setType(SkillConfig.SkillType.CIRCLE_AREA);
        config.setCooldown(5000);
        config.setMpCost(100);
        config.setCastRange(500);
        config.setDamage(100);
        config.setDamageRadius(200);
        config.setCanHitSelf(false);
        config.setCanHitAlly(false);
        config.setCanHitEnemy(true);
        return config;
    }

    private int getItemPrice(int itemId) {
        return itemId * 100;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getTickRate() {
        return tickRate;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public int getNextRandom(int bound) {
        return randomGenerator.nextInt(bound);
    }

    public Map<Integer, Long> getFrameHashes() {
        return Collections.unmodifiableMap(frameHashes);
    }

    public boolean verifyStateHash(int frameNumber, long expectedHash) {
        Long actualHash = frameHashes.get(frameNumber);
        if (actualHash == null) return false;
        boolean match = actualHash.equals(expectedHash);
        if (!match) {
            syncErrorCount.incrementAndGet();
            log.error("帧{}哈希不匹配: 期望={}, 实际={}",
                    frameNumber, Long.toHexString(expectedHash), Long.toHexString(actualHash));
        }
        return match;
    }

    public int getSyncErrorCount() {
        return syncErrorCount.get();
    }

    public List<FrameInput> getFrameInputs(int frameNumber) {
        return frameInputs.getOrDefault(frameNumber, Collections.emptyList());
    }

    public List<FrameState> getFrameStates() {
        synchronized (frameStatesLock) {
            return Collections.unmodifiableList(new ArrayList<>(frameStates));
        }
    }

    public BattleSession getSession() {
        return session;
    }

    public interface BattleEventListener {
        void onFrameUpdate(long battleId, int frameNumber, FrameState state);
        void onPlayerMove(long battleId, long userId, int x, int y);
        void onPlayerAttack(long battleId, long attackerId, long targetId, int damage);
        void onSkillCast(long battleId, long userId, int skillId, long targetId, int damage);
        void onPlayerKill(long battleId, long killerId, long victimId);
        void onPlayerRespawn(long battleId, long userId);
        void onItemUse(long battleId, long userId, int itemId);
        void onItemBuy(long battleId, long userId, int itemId, int price);
        void onGameOver(long battleId);
    }
}
