package com.moba.battle.battle;
import com.moba.battle.storage.BattleLogStorage;

import com.moba.battle.model.*;
import com.moba.battle.ai.AIController;
import com.moba.battle.ai.AIState;
import com.moba.battle.ai.AIBot;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class LockstepEngine {
    private static final int TICK_RATE = 15;
    private static final long TICK_INTERVAL_MS = 1000 / TICK_RATE;
    private static final int INPUT_DELAY_FRAMES = 3;

    private final String battleId;
    private final BattleSession session;
    private final int tickRate;
    private final long tickIntervalMs;
    private final int inputDelayFrames;

    private volatile int currentFrame;
    private volatile boolean running;

    private final Map<Integer, List<FrameInput>> frameInputs;
    private final ConcurrentLinkedQueue<FrameInput> pendingInputs;

    private final List<FrameState> frameStates;
    private static final int MAX_FRAME_STATES = 3600;
    private final Map<Integer, Long> frameHashes;
    private static final int MAX_FRAME_HASHES = 1000;

    private long lastTickTime;
    private final AtomicInteger syncErrorCount;

    private final Random randomGenerator;
    private final long randomSeed;

    private BattleEventListener eventListener;
    private AIController aiController;
    private boolean aiEnabled;

    public LockstepEngine(String battleId, BattleSession session) {
        this(battleId, session, TICK_RATE, INPUT_DELAY_FRAMES);
    }

    public LockstepEngine(String battleId, BattleSession session, int tickRate, int inputDelayFrames) {
        this.battleId = battleId;
        this.session = session;
        this.tickRate = tickRate;
        this.tickIntervalMs = 1000 / tickRate;
        this.inputDelayFrames = inputDelayFrames;

        this.currentFrame = 0;
        this.running = false;

        this.frameInputs = new ConcurrentHashMap<>();
        this.pendingInputs = new ConcurrentLinkedQueue<>();
        this.frameStates = Collections.synchronizedList(new ArrayList<>());
        this.frameHashes = new ConcurrentHashMap<>();

        this.lastTickTime = 0;
        this.syncErrorCount = new AtomicInteger(0);

        this.randomSeed = System.nanoTime();
        this.randomGenerator = new Random(randomSeed);
        this.aiEnabled = false;

        log.info("Lockstep engine created: battleId={}, tickRate={}Hz, inputDelay={} frames",
                battleId, tickRate, inputDelayFrames);
    }

    public void setEventListener(BattleEventListener listener) {
        this.eventListener = listener;
    }

    public void enableAI(AIController controller, int botLevel) {
        this.aiController = controller;
        this.aiEnabled = true;
        log.info("AI enabled for battle {}, botLevel={}", battleId, botLevel);
    }

    public void disableAI() {
        this.aiEnabled = false;
        log.info("AI disabled for battle {}", battleId);
    }

    public boolean isAIEnabled() {
        return aiEnabled;
    }

    public void start() {
        running = true;
        lastTickTime = System.currentTimeMillis();
        log.info("Lockstep engine started: battleId={}, randomSeed={}", battleId, randomSeed);
    }

    public void stop() {
        running = false;
        log.info("Lockstep engine stopped: battleId={}, totalFrames={}", battleId, currentFrame);
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
        if (frameStates.size() >= MAX_FRAME_STATES) {
            frameStates.subList(0, frameStates.size() - MAX_FRAME_STATES / 2).clear();
        }
        frameStates.add(state);

        if (currentFrame % 10 == 0) {
            long hash = state.computeHash();
            if (frameHashes.size() >= MAX_FRAME_HASHES) {
                int oldestFrame = frameHashes.keySet().stream().min(Integer::compareTo).orElse(0);
                frameHashes.keySet().removeIf(f -> f <= oldestFrame + 100);
            }
            frameHashes.put(currentFrame, hash);
            log.debug("Frame {} hash check: {}", currentFrame, Long.toHexString(hash));
        }

        if (eventListener != null) {
            eventListener.onFrameUpdate(currentFrame, state);
        }

        checkGameOver();
    }

    private void collectInputs() {
        List<FrameInput> inputs = new ArrayList<>();

        for (Long playerId : session.getBattlePlayers().keySet()) {
            FrameInput input = pollInputForPlayer(playerId);
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

    private FrameInput pollInputForPlayer(long playerId) {
        Iterator<FrameInput> iterator = pendingInputs.iterator();
        while (iterator.hasNext()) {
            FrameInput input = iterator.next();
            if (input.getPlayerId() == playerId) {
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
            BattlePlayer player = session.getPlayer(input.getPlayerId());
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

    private SkillCollisionSystem collisionSystem;
    private static final Map<Integer, SkillConfig> SKILL_CONFIGS = SkillConfig.loadAllSkills();

    public void setCollisionSystem(SkillCollisionSystem system) {
        this.collisionSystem = system;
    }

    private void handleMove(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        String content = new String(input.getData());
        String[] parts = content.split("\\|");
        if (parts.length < 4) return;

        int targetX = Integer.parseInt(parts[2]);
        int targetY = Integer.parseInt(parts[3]);

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
            eventListener.onPlayerMove(player.getPlayerId(), newX, newY);
        }
    }

    private void handleAttack(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        String content = new String(input.getData());
        String[] parts = content.split("\\|");
        if (parts.length < 3) return;

        long targetId = Long.parseLong(parts[2]);
        BattlePlayer target = session.getPlayer(targetId);

        if (target == null || target.getTeamId() == player.getTeamId()) return;

        int damage = calculateDamage(player, target);
        target.takeDamage(damage);

        if (eventListener != null) {
            eventListener.onPlayerAttack(player.getPlayerId(), targetId, damage);
        }

        if (target.isDead()) {
            onPlayerKilled(player, target);
        }
    }

    private void handleSkillCast(BattlePlayer player, FrameInput input) {
        if (player.isDead()) return;

        String content = new String(input.getData());
        String[] parts = content.split("\\|");
        if (parts.length < 4) return;

        int skillId = Integer.parseInt(parts[1]);
        long targetId = 0;
        int targetX = Integer.parseInt(parts[2]);
        int targetY = Integer.parseInt(parts[3]);
        int facing = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;

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

            SkillCollisionSystem.SkillCastInfo castInfo = skillConfig.toSkillCastInfo(
                    player, targetId, targetX, targetY, facing, currentFrame);

            List<Long> hitPlayers = collisionSystem.checkSkillHit(castInfo);

            for (Long hitPlayerId : hitPlayers) {
                BattlePlayer hitPlayer = session.getPlayer(hitPlayerId);
                if (hitPlayer != null && hitPlayer.getTeamId() != player.getTeamId()) {
                    int damage = calculateSkillDamage(player, hitPlayer, skill);
                    hitPlayer.takeDamage(damage);

                    if (eventListener != null) {
                        eventListener.onSkillCast(player.getPlayerId(), skillId, hitPlayerId, damage);
                    }

                    if (hitPlayer.isDead()) {
                        onPlayerKilled(player, hitPlayer);
                    }
                }
            }
        }
    }

    private void handleUseItem(BattlePlayer player, FrameInput input) {
        String content = new String(input.getData());
        String[] parts = content.split("\\|");
        if (parts.length < 3) return;

        int itemId = Integer.parseInt(parts[2]);
        player.getItems().merge(itemId, 1, Integer::sum);

        if (eventListener != null) {
            eventListener.onItemUse(player.getPlayerId(), itemId);
        }
    }

    private void handleBuyItem(BattlePlayer player, FrameInput input) {
        String content = new String(input.getData());
        String[] parts = content.split("\\|");
        if (parts.length < 3) return;

        int itemId = Integer.parseInt(parts[2]);
        int price = getItemPrice(itemId);

        if (player.getGold() >= price) {
            player.addGold(-price);
            player.getItems().merge(itemId, 1, Integer::sum);

            if (eventListener != null) {
                eventListener.onItemBuy(player.getPlayerId(), itemId, price);
            }
        }
    }

    private int calculateDamage(BattlePlayer attacker, BattlePlayer defender) {
        int baseAttack = attacker.getAttackPower();
        int defense = defender.getDefense();
        int damage = Math.max(1, baseAttack - defense / 10);
        return damage;
    }

    private int calculateSkillDamage(BattlePlayer attacker, BattlePlayer defender, BattlePlayer.Skill skill) {
        int baseAttack = attacker.getAttackPower();
        int defense = defender.getDefense();
        float skillMultiplier = 1.0f + skill.getLevel() * 0.2f;
        int damage = Math.max(1, (int)(baseAttack * skillMultiplier) - defense / 10);
        return damage;
    }

    private void onPlayerKilled(BattlePlayer killer, BattlePlayer victim) {
        killer.setKillCount(killer.getKillCount() + 1);
        killer.addGold(300);

        BattleSession.BattleEvent killEvent = new BattleSession.BattleEvent();
        killEvent.setTimestamp(System.currentTimeMillis());
        killEvent.setPlayerId(killer.getPlayerId());
        killEvent.setType(BattleSession.BattleEvent.EventType.KILL);
        killEvent.setData("target=" + victim.getPlayerId());
        session.recordEvent(killEvent);

        BattleSession.BattleEvent deathEvent = new BattleSession.BattleEvent();
        deathEvent.setTimestamp(System.currentTimeMillis());
        deathEvent.setPlayerId(victim.getPlayerId());
        deathEvent.setType(BattleSession.BattleEvent.EventType.DEATH);
        deathEvent.setData("killer=" + killer.getPlayerId());
        session.recordEvent(deathEvent);

        BattleLogStorage.getInstance().submitBattleEvent(
                session.getBattleId(),
                "PLAYER_KILL",
                "killer=" + killer.getPlayerId() + "|victim=" + victim.getPlayerId() + "|gold=300"
        );

        if (eventListener != null) {
            eventListener.onPlayerKill(killer.getPlayerId(), victim.getPlayerId());
        }
    }

    private void updateDeadPlayers() {
        for (BattlePlayer player : session.getBattlePlayers().values()) {
            if (player.isDead()) {
                player.updateDeadState();
                if (!player.isDead() && eventListener != null) {
                    eventListener.onPlayerRespawn(player.getPlayerId());
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
                eventListener.onGameOver();
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
        config.setSkillName("Default Skill");
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
            log.error("Hash mismatch at frame {}: expected={}, actual={}",
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
        return Collections.unmodifiableList(frameStates);
    }

    public BattleSession getSession() {
        return session;
    }

    public interface BattleEventListener {
        void onFrameUpdate(int frameNumber, FrameState state);
        void onPlayerMove(long playerId, int x, int y);
        void onPlayerAttack(long attackerId, long targetId, int damage);
        void onSkillCast(long playerId, int skillId, long targetId, int damage);
        void onPlayerKill(long killerId, long victimId);
        void onPlayerRespawn(long playerId);
        void onItemUse(long playerId, int itemId);
        void onItemBuy(long playerId, int itemId, int price);
        void onGameOver();
    }
}

