package com.moba.battle.manager;

import com.moba.battle.anticheat.AntiCheatValidator;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.battle.GridCollisionDetector;
import com.moba.battle.ai.AIController;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.event.BattleEventProducer;
import com.moba.battle.model.*;
import com.moba.battle.model.MOBAMap.GameMode;
import com.moba.battle.protocol.request.BattleEnterRequest;
import com.moba.battle.protocol.request.ReconnectRequest;
import com.moba.battle.protocol.response.BattleEnterResponse;
import com.moba.battle.protocol.response.ReconnectResponse;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.serialize.SerializerFactory;
import com.moba.battle.replay.ReplaySystem;
import com.moba.battle.storage.BattleLogStorage;
import com.moba.battle.monitor.ServerMonitor;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.event.BattleEndEvent;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BattleManager implements LockstepEngine.BattleEventListener {
    private final ScheduledExecutorService scheduler;
    private final MapConfig defaultMapConfig;
    private final AntiCheatValidator antiCheatValidator;
    private final ReplaySystem replaySystem;
    private final GridCollisionDetector collisionDetector;

    public BattleManager() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.defaultMapConfig = MapConfig.create3v3v3Map();
        this.antiCheatValidator = AntiCheatValidator.getInstance();
        this.replaySystem = new ReplaySystem();
        this.collisionDetector = new GridCollisionDetector(16000, 16000, 200);
    }

    public static BattleManager getInstance() {
        return SpringContextHolder.getBean(BattleManager.class);
    }

    public BattleRoom createBattle(String battleId, List<Long> playerIds, int teamCount) {
        return createBattle(battleId, playerIds, teamCount, 0, 5);
    }

    public BattleRoom createBattle(String battleId, List<Long> playerIds, int teamCount, int neededBots, int aiLevel) {
        BattleRoom room = RoomManager.getInstance().createRoom(battleId, playerIds, teamCount);
        if (room == null) {
            log.error("Failed to create room: {}", battleId);
            return null;
        }

        GameMode mode = teamCount == 3 ? GameMode.MODE_3V3V3 : GameMode.MODE_5V5;
        room.setGameMode(mode);

        MOBAMap mobaMap = MapManager.getInstance().createMap(battleId, 1, mode);
        room.getSession().setMapWidth(mobaMap.getWidth());
        room.getSession().setMapHeight(mobaMap.getHeight());

        room.getEngine().setEventListener(this);

        for (Long playerId : playerIds) {
            room.registerHumanPlayer(playerId);
        }

        if (neededBots > 0) {
            AIController aiController = AIController.getInstance();
            List<Long> botIds = aiController.createBotsForBattle(battleId, room.getSession(),
                    playerIds.size() / teamCount, neededBots, aiLevel);
            for (Long botId : botIds) {
                room.registerBotPlayer(botId);
            }
            room.getEngine().enableAI(aiController, aiLevel);
            log.info("AI bots created for battle {}, count={}, level={}", battleId, neededBots, aiLevel);
        }

        for (Long playerId : playerIds) {
            Optional<Player> playerOpt = PlayerManager.getInstance().getPlayerById(playerId);
            playerOpt.ifPresent(p -> {
                p.setState(Player.PlayerState.IN_BATTLE);
                p.setCurrentBattleId(Long.parseLong(battleId.replace("BATTLE_", "")));
            });
        }

        room.transitionToLoading();

        scheduler.schedule(() -> {
            if (room.getState() == BattleRoom.RoomState.LOADING && room.isLoadingTimeout()) {
                log.warn("Loading timeout for room {}, unready players will be replaced by AI", battleId);
                handleLoadingTimeout(room);
            }
        }, 60, TimeUnit.SECONDS);

        return room;
    }

    private void handleLoadingTimeout(BattleRoom room) {
        Set<Long> unreadyPlayers = room.getUnreadyPlayerIds();
        if (unreadyPlayers.isEmpty()) return;

        log.warn("Room {} loading timeout, {} players not ready: {}",
                room.getBattleId(), unreadyPlayers.size(), unreadyPlayers);

        AIController aiController = AIController.getInstance();
        for (Long playerId : unreadyPlayers) {
            log.info("Replacing unready player {} with AI bot in room {}", playerId, room.getBattleId());
            room.registerBotPlayer(playerId);
            room.humanPlayerIds.remove(playerId);
            room.markPlayerReady(playerId);
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
            log.info("Battle {} started! {} human + {} bot players, seed={}",
                    room.getBattleId(), room.getHumanPlayerIds().size(),
                    room.getBotPlayerIds().size(), room.getEngine().getRandomSeed());
        }, 3, TimeUnit.SECONDS);
    }

    private void broadcastCountdown(BattleRoom room, int seconds) {
        try {
            Map<String, Object> countdownData = new java.util.HashMap<>();
            countdownData.put("seconds", seconds);
            countdownData.put("roomId", room.getBattleId());
            byte[] body = SerializerFactory.json().serialize(countdownData);

            GamePacket packet = GamePacket.notify(MessageType.BATTLE_COUNTDOWN_NOTIFY, body);
            broadcastToRoom(room, packet);
        } catch (Exception e) {
            log.error("Failed to broadcast countdown for room {}", room.getBattleId(), e);
        }
    }

    private void broadcastStart(BattleRoom room) {
        try {
            Map<String, Object> startData = new java.util.HashMap<>();
            startData.put("roomId", room.getBattleId());
            startData.put("seed", room.getEngine().getRandomSeed());
            startData.put("tickRate", room.getEngine().getTickRate());
            byte[] body = SerializerFactory.json().serialize(startData);

            GamePacket packet = GamePacket.notify(MessageType.BATTLE_START_NOTIFY, body);
            broadcastToRoom(room, packet);
        } catch (Exception e) {
            log.error("Failed to broadcast start for room {}", room.getBattleId(), e);
        }
    }

    private void broadcastToRoom(BattleRoom room, GamePacket packet) {
        for (Long playerId : room.getSession().getBattlePlayers().keySet()) {
            Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                player.getCtx().writeAndFlush(packet);
            }
        }
    }

    public BattleEnterResponse enterBattle(ChannelHandlerContext ctx, BattleEnterRequest request) {
        String roomId = request.getRoomId();

        io.netty.util.AttributeKey<Object> playerIdKey = io.netty.util.AttributeKey.valueOf("playerId");
        Object playerIdObj = ctx.channel().attr(playerIdKey).get();
        if (!(playerIdObj instanceof Long)) {
            return BattleEnterResponse.failure("UNAUTHORIZED", "未认证的连接");
        }
        long playerId = (Long) playerIdObj;

        Optional<Player> playerOpt = PlayerManager.getInstance().getPlayerById(playerId);
        if (playerOpt.isEmpty()) {
            return BattleEnterResponse.failure("PLAYER_NOT_FOUND", "玩家不存在");
        }

        BattleRoom room = RoomManager.getInstance().getRoom(roomId);
        if (room == null) {
            return BattleEnterResponse.failure("ROOM_NOT_FOUND", "房间不存在");
        }

        BattlePlayer battlePlayer = room.getSession().getPlayer(playerId);
        if (battlePlayer == null) {
            return BattleEnterResponse.failure("NOT_IN_ROOM", "你不在此房间中");
        }

        if (playerOpt.get().isReconnecting()) {
            if (!ReconnectManager.getInstance().isReconnectValid(playerId)) {
                return BattleEnterResponse.failure("RECONNECT_TIMEOUT", "重连超时");
            }
            ReconnectManager.getInstance().cancelReconnectTimer(playerId);
        }

        PlayerManager.getInstance().updatePlayerContext(ctx, playerId);

        MOBAMap mobaMap = MapManager.getInstance().getMap(roomId);
        String mapConfigJson;
        int mapId;

        if (mobaMap != null) {
            mapId = mobaMap.getMapId();
            mapConfigJson = MapManager.getInstance().getMapStateJson(roomId);
        } else {
            mapId = defaultMapConfig.getMapId();
            mapConfigJson = "{\"mapId\":" + defaultMapConfig.getMapId() +
                    ",\"name\":\"" + defaultMapConfig.getMapName() +
                    "\",\"teams\":" + defaultMapConfig.getTeamCount() +
                    ",\"seed\":" + room.getEngine().getRandomSeed() + "}";
        }

        return BattleEnterResponse.success(
                roomId,
                mapId,
                mapConfigJson
        );
    }

    public void handlePlayerAction(ChannelHandlerContext ctx, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length < 2) return;

        long playerId = Long.parseLong(parts[0]);
        String actionType = parts[1];

        BattleRoom room = RoomManager.getInstance().getPlayerRoom(playerId);
        if (room == null || !room.isRunning()) return;

        BattlePlayer battlePlayer = room.getSession().getPlayer(playerId);
        if (battlePlayer == null) return;

        LockstepEngine engine = room.getEngine();

        FrameInput frameInput = new FrameInput();
        frameInput.setPlayerId(playerId);
        frameInput.setData(data);

        switch (actionType) {
            case "MOVE":
                if (parts.length >= 4) {
                    float targetX = Float.parseFloat(parts[2]);
                    float targetY = Float.parseFloat(parts[3]);

                    if (!antiCheatValidator.validateMove(playerId, battlePlayer, targetX, targetY, 100)) {
                        log.warn("Move validation failed for player {}", playerId);
                        return;
                    }

                    frameInput.setType(FrameInput.InputType.MOVE);
                    engine.submitInput(frameInput);
                }
                break;
            case "ATTACK":
                if (parts.length >= 3) {
                    long targetId = Long.parseLong(parts[2]);
                    frameInput.setType(FrameInput.InputType.ATTACK);
                    engine.submitInput(frameInput);
                }
                break;
            case "USE_ITEM":
                frameInput.setType(FrameInput.InputType.USE_ITEM);
                engine.submitInput(frameInput);
                break;
        }
    }

    public void handleSkillCast(ChannelHandlerContext ctx, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length < 3) return;

        long playerId = Long.parseLong(parts[0]);
        int skillId = Integer.parseInt(parts[1]);
        long targetId = Long.parseLong(parts[2]);

        BattleRoom room = RoomManager.getInstance().getPlayerRoom(playerId);
        if (room == null || !room.isRunning()) return;

        BattlePlayer battlePlayer = room.getSession().getPlayer(playerId);
        if (battlePlayer == null || battlePlayer.isDead()) return;

        if (!antiCheatValidator.validateSkillCast(playerId, battlePlayer, skillId)) {
            log.warn("Skill cast validation failed for player {}", playerId);
            return;
        }

        FrameInput frameInput = new FrameInput();
        frameInput.setPlayerId(playerId);
        frameInput.setType(FrameInput.InputType.SKILL_CAST);
        frameInput.setData(data);
        room.getEngine().submitInput(frameInput);
    }

    public ReconnectResponse handleReconnect(ChannelHandlerContext ctx, ReconnectRequest request) {
        Optional<Player> playerOpt = PlayerManager.getInstance().getPlayerById(request.getPlayerId());
        if (playerOpt.isEmpty()) {
            return ReconnectResponse.failure("Player not found");
        }

        BattleRoom room = RoomManager.getInstance().getRoom(request.getBattleId());
        if (room == null) {
            return ReconnectResponse.failure("Battle not found");
        }

        PlayerManager.getInstance().updatePlayerContext(ctx, request.getPlayerId());
        Player player = playerOpt.get();
        player.setReconnecting(false);

        BattlePlayer battlePlayer = room.getSession().getPlayer(request.getPlayerId());
        String stateJson = serializeBattleState(room, battlePlayer);

        log.info("Player {} reconnected to battle {}, frame={}",
                request.getPlayerId(), request.getBattleId(), room.getEngine().getCurrentFrame());
        return ReconnectResponse.success(stateJson);
    }

    @Override
    public void onFrameUpdate(int frameNumber, FrameState state) {
        for (BattleRoom room : RoomManager.getInstance().getAllRooms()) {
            if (room.getEngine().getCurrentFrame() == frameNumber && room.isRunning()) {
                SpectatorManager.getInstance().broadcastFrameState(room.getBattleId(), state);
                break;
            }
        }
    }

    @Override
    public void onPlayerMove(long playerId, int x, int y) {
    }

    @Override
    public void onPlayerAttack(long attackerId, long targetId, int damage) {
        BattleRoom room = RoomManager.getInstance().getPlayerRoom(attackerId);
        if (room != null) {
            BattlePlayer attacker = room.getSession().getPlayer(attackerId);
            BattlePlayer target = room.getSession().getPlayer(targetId);
            if (attacker != null && target != null) {
                antiCheatValidator.validateDamage(attackerId, targetId, damage, attacker, target);
            }
        }
    }

    @Override
    public void onSkillCast(long playerId, int skillId, long targetId, int damage) {
        BattleRoom room = RoomManager.getInstance().getPlayerRoom(playerId);
        if (room != null) {
            BattlePlayer attacker = room.getSession().getPlayer(playerId);
            BattlePlayer target = room.getSession().getPlayer(targetId);
            if (attacker != null && target != null) {
                boolean valid = antiCheatValidator.validateDamage(playerId, targetId, damage, attacker, target);
                if (!valid) {
                    ServerMonitor.getInstance().recordDesync();
                }
            }
        }
    }

    @Override
    public void onPlayerKill(long killerId, long victimId) {
        log.info("Player {} killed player {}", killerId, victimId);
    }

    @Override
    public void onPlayerRespawn(long playerId) {
        log.info("Player {} respawned", playerId);
    }

    @Override
    public void onItemUse(long playerId, int itemId) {
    }

    @Override
    public void onItemBuy(long playerId, int itemId, int price) {
    }

    @Override
    public void onGameOver() {
        BattleRoom room = findFinishedRoom();
        if (room != null) {
            room.setState(BattleRoom.RoomState.FINISHED);
            log.info("Battle {} ended, calculating settlement", room.getBattleId());

            SettlementSystem.BattleSettlementResult settlement = SettlementSystem.getInstance().calculateSettlement(room);
            log.info("Settlement result: {}", settlement.toJson());

            replaySystem.recordReplay(room.getBattleId(), room.getSession());

            publishBattleEndEvent(room, settlement);

            broadcastBattleEnd(room);

            scheduler.schedule(() -> {
                RoomManager.getInstance().removeRoom(room.getBattleId());
                log.info("Battle room {} cleaned up", room.getBattleId());
            }, 30, TimeUnit.SECONDS);
        }
    }

    private void publishBattleEndEvent(BattleRoom room, SettlementSystem.BattleSettlementResult settlement) {
        try {
            BattleResultDTO resultDTO = new BattleResultDTO();
            resultDTO.setBattleId(room.getBattleId());
            resultDTO.setGameMode(room.getGameMode() != null ? room.getGameMode().ordinal() : 0);
            resultDTO.setStartTime(room.getSession().getStartTime());
            resultDTO.setEndTime(room.getSession().getEndTime());
            resultDTO.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
            resultDTO.setWinnerTeamId(settlement.getWinningTeams().isEmpty() ? -1 : settlement.getWinningTeams().get(0));

            BattleEndEvent event = new BattleEndEvent();
            event.setEventId(java.util.UUID.randomUUID().toString());
            event.setTimestamp(System.currentTimeMillis());
            event.setResult(resultDTO);

            BattleEventProducer producer = SpringContextHolder.getBean(BattleEventProducer.class);
            if (producer != null) {
                producer.publishBattleEnd(event);
            } else {
                log.warn("BattleEventProducer not available (RocketMQ disabled), skipping event publish");
            }
        } catch (Exception e) {
            log.error("Failed to publish battle end event for battle: {}", room.getBattleId(), e);
        }
    }

    private BattleRoom findFinishedRoom() {
        for (BattleRoom room : RoomManager.getInstance().getAllRooms()) {
            if (room.getSession().getState() == BattleSession.BattleState.FINISHED) {
                return room;
            }
        }
        return null;
    }

    private void broadcastBattleEnd(BattleRoom room) {
        BattleSession session = room.getSession();
        StringBuilder sb = new StringBuilder();
        sb.append("NOTIFY|GAME_OVER|").append(room.getBattleId());
        sb.append("|duration=").append(session.getEndTime() - session.getStartTime());

        for (Map.Entry<Integer, BattleSession.Team> entry : session.getTeams().entrySet()) {
            BattleSession.Team team = entry.getValue();
            sb.append("|team").append(entry.getKey()).append("=");
            sb.append("hp=").append(team.getBaseHp());
            sb.append(",towers=").append(team.getTowerCount());
            sb.append(",defeated=").append(team.isDefeated());
        }

        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (Long playerId : session.getBattlePlayers().keySet()) {
            Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                GamePacket packet = GamePacket.notify(MessageType.BATTLE_END_NOTIFY, body);
                player.getCtx().writeAndFlush(packet);
            }
        }

        BattleLogStorage.getInstance().submitBattleEvent(room.getBattleId(), "GAME_OVER", sb.toString());
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        Optional<Player> playerOpt = PlayerManager.getInstance().getPlayerByChannel(ctx);
        playerOpt.ifPresent(player -> {
            BattleRoom room = RoomManager.getInstance().getPlayerRoom(player.getPlayerId());
            if (room != null) {
                player.setReconnecting(true);
                ReconnectManager.getInstance().startReconnectTimer(player.getPlayerId());
                log.info("Player {} disconnected from battle {}, waiting for reconnect (timeout={}s)",
                        player.getPlayerId(), room.getBattleId(), 30);
            }
        });
    }

    private String serializeBattleState(BattleRoom room, BattlePlayer player) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append("\"battleId\":\"").append(room.getBattleId()).append("\"");
        sb.append(",\"frame\":").append(room.getEngine().getCurrentFrame());
        sb.append(",\"state\":\"").append(room.getSession().getState()).append("\"");
        sb.append(",\"player\":{\"id\":").append(player.getPlayerId());
        sb.append(",\"hp\":").append(player.getCurrentHp());
        sb.append(",\"mp\":").append(player.getCurrentMp());
        sb.append(",\"level\":").append(player.getLevel());
        sb.append(",\"x\":").append(player.getPosition().x);
        sb.append(",\"y\":").append(player.getPosition().y);
        sb.append(",\"seed\":").append(room.getEngine().getRandomSeed());
        sb.append("}}");
        return sb.toString();
    }

    public BattleRoom getBattleRoom(String battleId) {
        return RoomManager.getInstance().getRoom(battleId);
    }

    public int getRoomCount() {
        return RoomManager.getInstance().getRoomCount();
    }

    public int getTotalPlayers() {
        return RoomManager.getInstance().getTotalPlayers();
    }
}
