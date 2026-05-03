package com.moba.battle.manager;

import com.moba.battle.anticheat.AntiCheatValidator;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.battle.GridCollisionDetector;
import com.moba.battle.ai.AIController;
import com.moba.battle.ai.AIBot;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.event.BattleEventProducer;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.MapConfig;
import com.moba.battle.model.MOBAMap;
import com.moba.battle.model.Player;
import com.moba.battle.protocol.request.BattleEnterRequest;
import com.moba.battle.protocol.request.ReconnectRequest;
import com.moba.battle.protocol.response.BattleEnterResponse;
import com.moba.battle.protocol.response.ReconnectResponse;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import com.moba.netty.user.User;
import com.moba.battle.replay.ReplaySystem;
import com.moba.battle.storage.BattleLogStorage;
import com.moba.battle.monitor.ServerMonitor;
import com.moba.common.constant.GameMode;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.event.BattleEndEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BattleManager implements LockstepEngine.BattleEventListener {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ScheduledExecutorService scheduler;
    private final MapConfig defaultMapConfig;
    private final AntiCheatValidator antiCheatValidator;
    private final ReplaySystem replaySystem;
    private final GridCollisionDetector collisionDetector;
    private final int loadingTimeoutSeconds;
    private final int roomCleanupDelaySeconds;
    private final int reconnectTimeoutSeconds;

    private final RoomManager roomManager;
    private final MapManager mapManager;
    private final PlayerManager playerManager;
    private final AIController aiController;
    private final SpectatorManager spectatorManager;
    private final ReconnectManager reconnectManager;
    private final SettlementSystem settlementSystem;
    private final BattleLogStorage battleLogStorage;
    private final ServerMonitor serverMonitor;
    private final BattleEventProducer battleEventProducer;

    public BattleManager(ServerConfig serverConfig,
                         RoomManager roomManager,
                         @Lazy MapManager mapManager,
                         PlayerManager playerManager,
                         AIController aiController,
                         SpectatorManager spectatorManager,
                         ReconnectManager reconnectManager,
                         SettlementSystem settlementSystem,
                         AntiCheatValidator antiCheatValidator,
                         BattleLogStorage battleLogStorage,
                         ServerMonitor serverMonitor,
                         @Lazy BattleEventProducer battleEventProducer) {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.defaultMapConfig = MapConfig.create3v3v3Map();
        this.antiCheatValidator = antiCheatValidator;
        this.replaySystem = new ReplaySystem(serverConfig);
        this.collisionDetector = new GridCollisionDetector(16000, 16000, serverConfig.getDefaultGridSize());
        this.loadingTimeoutSeconds = serverConfig.getLoadingTimeoutSeconds();
        this.roomCleanupDelaySeconds = serverConfig.getRoomCleanupDelaySeconds();
        this.reconnectTimeoutSeconds = serverConfig.getReconnectTimeoutSeconds();

        this.roomManager = roomManager;
        this.mapManager = mapManager;
        this.playerManager = playerManager;
        this.aiController = aiController;
        this.spectatorManager = spectatorManager;
        this.reconnectManager = reconnectManager;
        this.settlementSystem = settlementSystem;
        this.battleLogStorage = battleLogStorage;
        this.serverMonitor = serverMonitor;
        this.battleEventProducer = battleEventProducer;
    }

    public BattleRoom createBattle(long battleId, List<Long> userIds, int teamCount) {
        return createBattle(battleId, userIds, teamCount, 0, 5, false);
    }

    public BattleRoom createBattle(long battleId, List<Long> userIds, int teamCount, int neededBots, int aiLevel, boolean aiMode) {
        BattleRoom room = roomManager.createRoom(battleId, userIds, teamCount);
        if (room == null) {
            log.error("创建房间失败: {}", battleId);
            return null;
        }

        GameMode mode = teamCount == 3 ? GameMode.MODE_3V3V3 : GameMode.MODE_5V5;
        room.setGameMode(mode);
        room.setAiMode(aiMode);

        MOBAMap mobaMap = mapManager.createMap(battleId, 1, mode);
        room.getSession().setMapWidth(mobaMap.getWidth());
        room.getSession().setMapHeight(mobaMap.getHeight());

        room.getEngine().setEventListener(this);

        for (Long userId : userIds) {
            room.registerHumanPlayer(userId);
        }

        if (neededBots > 0) {
            int humanPerTeam = aiMode ? 1 : userIds.size() / teamCount;
            List<AIBot> bots = aiController.createBotsForBattle(battleId, room.getSession(),
                    humanPerTeam, neededBots, aiLevel);
            for (AIBot bot : bots) {
                room.registerBotPlayer(bot.getBotId());
            }
            room.getEngine().enableAI(aiController, aiLevel);
            log.info("战斗{}创建AI机器人, 数量={}, 等级={}, aiMode={}", battleId, neededBots, aiLevel, aiMode);
        }

        for (Long userId : userIds) {
            Optional<Player> playerOpt = playerManager.getPlayerById(userId);
            playerOpt.ifPresent(p -> {
                p.setState(Player.PlayerState.IN_BATTLE);
                p.setCurrentBattleId(battleId);
            });
        }

        room.transitionToLoading();

        scheduler.schedule(() -> {
            if (room.getState() == BattleRoom.RoomState.LOADING && room.isLoadingTimeout()) {
                log.warn("房间{}加载超时, 未准备的玩家将被AI替换", battleId);
                handleLoadingTimeout(room);
            }
        }, loadingTimeoutSeconds, TimeUnit.SECONDS);

        return room;
    }

    private void handleLoadingTimeout(BattleRoom room) {
        Set<Long> unreadyPlayers = room.getUnreadyuserIds();
        if (unreadyPlayers.isEmpty()) return;

        log.warn("房间{}加载超时, {}名玩家未准备: {}",
                room.getBattleId(), unreadyPlayers.size(), unreadyPlayers);

        for (Long userId : unreadyPlayers) {
            log.info("房间{}中未准备玩家{}被AI机器人替换", room.getBattleId(), userId);
            room.registerBotPlayer(userId);
            room.getHumanUserIds().remove(userId);
            room.markPlayerReady(userId);
        }

        if (!aiController.isInitializedForBattle(room.getBattleId())) {
            aiController.createBotsForBattle(room.getBattleId(), room.getSession(),
                    0, unreadyPlayers.size(), 3);
            room.getEngine().enableAI(aiController, 3);
        }

        if (room.allHumanPlayersReady()) {
            startCountdown(room);
        }
    }

    public void startCountdown(BattleRoom room) {
        room.transitionToCountdown();

        broadcastCountdown(room, 3);
        scheduler.schedule(() -> broadcastCountdown(room, 2), 1, TimeUnit.SECONDS);
        scheduler.schedule(() -> broadcastCountdown(room, 1), 2, TimeUnit.SECONDS);
        scheduler.schedule(() -> {
            broadcastStart(room);
            room.start();
            room.getSession().start();
            log.info("战斗{}开始! {}名真人 + {}名机器人, 随机种子={}",
                    room.getBattleId(), room.getHumanUserIds().size(),
                    room.getBotUserIds().size(), room.getEngine().getRandomSeed());
        }, 3, TimeUnit.SECONDS);
    }

    private static final byte CMD_COUNTDOWN_NOTIFY = ProtocolConstants.CMD_BATTLE_COUNTDOWN_NOTIFY;
    private static final byte CMD_START_NOTIFY = ProtocolConstants.CMD_BATTLE_START_NOTIFY;
    private static final byte CMD_END_NOTIFY = ProtocolConstants.CMD_BATTLE_END_NOTIFY;

    private void broadcastCountdown(BattleRoom room, int seconds) {
        try {
            Map<String, Object> countdownData = new HashMap<>();
            countdownData.put("seconds", seconds);
            countdownData.put("battleId", room.getBattleId());
            String jsonData = OBJECT_MAPPER.writeValueAsString(countdownData);

            MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, CMD_COUNTDOWN_NOTIFY, jsonData);
            broadcastToRoom(room, packet);
        } catch (Exception e) {
            log.error("房间{}广播倒计时失败", room.getBattleId(), e);
        }
    }

    private void broadcastStart(BattleRoom room) {
        try {
            Map<String, Object> startData = new HashMap<>();
            startData.put("battleId", room.getBattleId());
            startData.put("seed", room.getEngine().getRandomSeed());
            startData.put("tickRate", room.getEngine().getTickRate());
            String jsonData = OBJECT_MAPPER.writeValueAsString(startData);

            MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, CMD_START_NOTIFY, jsonData);
            broadcastToRoom(room, packet);
        } catch (Exception e) {
            log.error("房间{}广播开始信号失败", room.getBattleId(), e);
        }
    }

    private void broadcastToRoom(BattleRoom room, MessagePacket packet) {
        for (Long userId : room.getSession().getBattlePlayers().keySet()) {
            Player player = playerManager.getPlayerById(userId).orElse(null);
            if (player != null && player.isConnected()) {
                player.sendToClient(packet);
            }
        }
    }

    public BattleEnterResponse enterBattle(User user, BattleEnterRequest request) {
        if (user == null) {
            return BattleEnterResponse.failure("UNAUTHORIZED", "未认证的连接");
        }

        long userId = user.getUserId();
        long battleId = request.getBattleId();

        Optional<Player> playerOpt = playerManager.getPlayerById(userId);
        if (playerOpt.isEmpty()) {
            return BattleEnterResponse.failure("PLAYER_NOT_FOUND", "玩家不存在");
        }

        Player player = playerOpt.get();
        player.setUser(user);

        BattleRoom room = roomManager.getRoom(battleId);
        if (room == null) {
            return BattleEnterResponse.failure("ROOM_NOT_FOUND", "房间不存在");
        }

        BattlePlayer battlePlayer = room.getSession().getPlayer(userId);
        if (battlePlayer == null) {
            return BattleEnterResponse.failure("NOT_IN_ROOM", "你不在此房间中");
        }

        if (player.isReconnecting()) {
            if (!reconnectManager.isReconnectValid(userId)) {
                return BattleEnterResponse.failure("RECONNECT_TIMEOUT", "重连超时");
            }
            reconnectManager.cancelReconnectTimer(userId);
        }

        MOBAMap mobaMap = mapManager.getMap(battleId);
        String mapConfigJson;
        int mapId;

        if (mobaMap != null) {
            mapId = mobaMap.getMapId();
            mapConfigJson = mapManager.getMapStateJson(battleId);
        } else {
            mapId = defaultMapConfig.getMapId();
            try {
                Map<String, Object> defaultMapData = new HashMap<>();
                defaultMapData.put("mapId", defaultMapConfig.getMapId());
                defaultMapData.put("name", defaultMapConfig.getMapName());
                defaultMapData.put("teams", defaultMapConfig.getTeamCount());
                defaultMapData.put("seed", room.getEngine().getRandomSeed());
                mapConfigJson = OBJECT_MAPPER.writeValueAsString(defaultMapData);
            } catch (Exception e) {
                log.error("序列化默认地图配置失败", e);
                mapConfigJson = "{}";
            }
        }

        return BattleEnterResponse.success(battleId, mapId, mapConfigJson);
    }

    public void handlePlayerAction(User user, byte[] data) {
        if (user == null) return;
        long userId = user.getUserId();

        BattleRoom room = roomManager.getPlayerRoom(userId);
        if (room == null || !room.isRunning()) return;

        BattlePlayer battlePlayer = room.getSession().getPlayer(userId);
        if (battlePlayer == null || battlePlayer.isDead()) return;

        try {
            com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(data);
            String actionType = node.get("action").asText();

            com.moba.battle.model.FrameInput frameInput = new com.moba.battle.model.FrameInput();
            frameInput.setUserId(userId);
            frameInput.setData(data);

            switch (actionType) {
                case "MOVE":
                    float targetX = node.get("x").floatValue();
                    float targetY = node.get("y").floatValue();

                    if (!antiCheatValidator.validateMove(userId, battlePlayer, targetX, targetY, 100)) {
                        log.warn("玩家{}移动验证失败", userId);
                        return;
                    }

                    frameInput.setType(com.moba.battle.model.FrameInput.InputType.MOVE);
                    room.getEngine().submitInput(frameInput);
                    break;
                case "ATTACK":
                    frameInput.setType(com.moba.battle.model.FrameInput.InputType.ATTACK);
                    room.getEngine().submitInput(frameInput);
                    break;
                case "USE_ITEM":
                    frameInput.setType(com.moba.battle.model.FrameInput.InputType.USE_ITEM);
                    room.getEngine().submitInput(frameInput);
                    break;
            }
        } catch (Exception e) {
            log.warn("解析玩家操作数据失败: userId={}", userId, e);
        }
    }

    public void handleSkillCast(User user, byte[] data) {
        if (user == null) return;
        long userId = user.getUserId();

        try {
            com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(data);
            int skillId = node.get("skillId").asInt();
            long targetId = node.has("targetId") ? node.get("targetId").asLong() : 0;

            BattleRoom room = roomManager.getPlayerRoom(userId);
            if (room == null || !room.isRunning()) return;

            BattlePlayer battlePlayer = room.getSession().getPlayer(userId);
            if (battlePlayer == null || battlePlayer.isDead()) return;

            if (!antiCheatValidator.validateSkillCast(userId, battlePlayer, skillId)) {
                log.warn("玩家{}技能释放验证失败", userId);
                return;
            }

            com.moba.battle.model.FrameInput frameInput = new com.moba.battle.model.FrameInput();
            frameInput.setUserId(userId);
            frameInput.setType(com.moba.battle.model.FrameInput.InputType.SKILL_CAST);
            frameInput.setData(data);
            room.getEngine().submitInput(frameInput);
        } catch (Exception e) {
            log.warn("解析技能释放数据失败: userId={}", userId, e);
        }
    }

    public ReconnectResponse handleReconnect(User user, ReconnectRequest request) {
        if (user == null) {
            return ReconnectResponse.failure("未认证的连接");
        }

        long userId = user.getUserId();

        Optional<Player> playerOpt = playerManager.getPlayerById(userId);
        if (playerOpt.isEmpty()) {
            return ReconnectResponse.failure("玩家不存在");
        }

        Player player = playerOpt.get();
        player.setUser(user);

        BattleRoom room = roomManager.getRoom(request.getBattleId());
        if (room == null) {
            return ReconnectResponse.failure("战斗不存在");
        }

        player.setReconnecting(false);

        BattlePlayer battlePlayer = room.getSession().getPlayer(userId);
        String stateJson = serializeBattleState(room, battlePlayer);

        log.info("玩家{}重连到战斗{}, 当前帧={}",
                userId, request.getBattleId(), room.getEngine().getCurrentFrame());
        return ReconnectResponse.success(stateJson);
    }

    public void handleDisconnect(long userId) {
        Optional<Player> playerOpt = playerManager.getPlayerById(userId);
        playerOpt.ifPresent(player -> {
            BattleRoom room = roomManager.getPlayerRoom(player.getUserId());
            if (room != null) {
                player.setReconnecting(true);
                reconnectManager.startReconnectTimer(player.getUserId());
                log.info("玩家{}从战斗{}断开连接, 等待重连(超时={}秒)",
                        player.getUserId(), room.getBattleId(), reconnectTimeoutSeconds);
            }
        });
    }

    @Override
    public void onFrameUpdate(long battleId, int frameNumber, com.moba.battle.model.FrameState state) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room != null && room.isRunning()) {
            spectatorManager.broadcastFrameState(battleId, state);
        }
    }

    @Override
    public void onPlayerMove(long battleId, long userId, int x, int y) {
    }

    @Override
    public void onPlayerAttack(long battleId, long attackerId, long targetId, int damage) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room != null) {
            BattlePlayer attacker = room.getSession().getPlayer(attackerId);
            BattlePlayer target = room.getSession().getPlayer(targetId);
            if (attacker != null && target != null) {
                antiCheatValidator.validateDamage(attackerId, targetId, damage, attacker, target);
            }
        }
    }

    @Override
    public void onSkillCast(long battleId, long userId, int skillId, long targetId, int damage) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room != null) {
            BattlePlayer attacker = room.getSession().getPlayer(userId);
            BattlePlayer target = room.getSession().getPlayer(targetId);
            if (attacker != null && target != null) {
                boolean valid = antiCheatValidator.validateDamage(userId, targetId, damage, attacker, target);
                if (!valid) {
                    serverMonitor.recordDesync();
                }
            }
        }
    }

    @Override
    public void onPlayerKill(long battleId, long killerId, long victimId) {
        log.info("玩家{}击杀了玩家{}", killerId, victimId);
    }

    @Override
    public void onPlayerRespawn(long battleId, long userId) {
        log.info("玩家{}已复活", userId);
    }

    @Override
    public void onItemUse(long battleId, long userId, int itemId) {
    }

    @Override
    public void onItemBuy(long battleId, long userId, int itemId, int price) {
    }

    @Override
    public void onGameOver(long battleId) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room != null) {
            room.setState(BattleRoom.RoomState.FINISHED);
            log.info("战斗{}结束, 计算结算", battleId);

            SettlementSystem.BattleSettlementResult settlement = settlementSystem.calculateSettlement(room);
            log.info("结算结果: {}", settlement.toJson());

            replaySystem.recordReplay(room.getBattleId(), room.getSession());

            publishBattleEndEvent(room, settlement);

            broadcastBattleEnd(room);

            scheduler.schedule(() -> {
                roomManager.removeRoom(room.getBattleId());
                log.info("战斗房间{}已清理", room.getBattleId());
            }, roomCleanupDelaySeconds, TimeUnit.SECONDS);
        }
    }

    private void publishBattleEndEvent(BattleRoom room, SettlementSystem.BattleSettlementResult settlement) {
        try {
            BattleResultDTO resultDTO = new BattleResultDTO();
            resultDTO.setBattleId(room.getBattleId());
            resultDTO.setGameMode(room.getGameMode() != null ? room.getGameMode() : GameMode.MODE_5V5);
            resultDTO.setStartTime(room.getSession().getStartTime());
            resultDTO.setEndTime(room.getSession().getEndTime());
            resultDTO.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
            resultDTO.setWinnerTeamId(settlement.getWinningTeams().isEmpty() ? -1 : settlement.getWinningTeams().get(0));

            BattleEndEvent event = new BattleEndEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setTimestamp(System.currentTimeMillis());
            event.setResult(resultDTO);

            if (battleEventProducer != null) {
                battleEventProducer.publishBattleEnd(event);
            } else {
                log.warn("BattleEventProducer不可用(RocketMQ未启用), 跳过事件发布");
            }
        } catch (Exception e) {
            log.error("发布战斗结束事件失败: {}", room.getBattleId(), e);
        }
    }

    private void broadcastBattleEnd(BattleRoom room) {
        BattleSession session = room.getSession();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "GAME_OVER");
            data.put("battleId", room.getBattleId());
            data.put("duration", session.getEndTime() - session.getStartTime());

            Map<String, Object> teamsData = new HashMap<>();
            for (Map.Entry<Integer, BattleSession.Team> entry : session.getTeams().entrySet()) {
                BattleSession.Team team = entry.getValue();
                Map<String, Object> teamInfo = new HashMap<>();
                teamInfo.put("hp", team.getBaseHp());
                teamInfo.put("towers", team.getTowerCount());
                teamInfo.put("defeated", team.isDefeated());
                teamsData.put("team" + entry.getKey(), teamInfo);
            }
            data.put("teams", teamsData);

            String json = OBJECT_MAPPER.writeValueAsString(data);

            for (Long userId : session.getBattlePlayers().keySet()) {
                Player player = playerManager.getPlayerById(userId).orElse(null);
                if (player != null && player.isConnected()) {
                    MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, CMD_END_NOTIFY, json);
                    player.sendToClient(packet);
                }
            }

            battleLogStorage.submitBattleEvent(room.getBattleId(), "GAME_OVER", json);
        } catch (Exception e) {
            log.error("广播战斗结束消息失败: battleId={}", room.getBattleId(), e);
        }
    }

    private String serializeBattleState(BattleRoom room, BattlePlayer player) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("battleId", room.getBattleId());
            state.put("frame", room.getEngine().getCurrentFrame());
            state.put("state", room.getSession().getState());

            Map<String, Object> playerState = new HashMap<>();
            playerState.put("id", player.getUserId());
            playerState.put("hp", player.getCurrentHp());
            playerState.put("mp", player.getCurrentMp());
            playerState.put("level", player.getLevel());
            playerState.put("x", player.getPosition().x);
            playerState.put("y", player.getPosition().y);
            state.put("player", playerState);
            state.put("seed", room.getEngine().getRandomSeed());

            return OBJECT_MAPPER.writeValueAsString(state);
        } catch (Exception e) {
            log.error("序列化战斗状态失败: battleId={}", room.getBattleId(), e);
            return "{}";
        }
    }

    public BattleRoom getBattleRoom(long battleId) {
        return roomManager.getRoom(battleId);
    }

    public int getRoomCount() {
        return roomManager.getRoomCount();
    }

    public int getTotalPlayers() {
        return roomManager.getTotalPlayers();
    }
}
