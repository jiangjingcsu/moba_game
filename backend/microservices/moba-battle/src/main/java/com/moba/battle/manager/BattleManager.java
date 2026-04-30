package com.moba.battle.manager;

import com.moba.battle.anticheat.AntiCheatValidator;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.battle.GridCollisionDetector;
import com.moba.battle.ai.AIController;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.event.BattleEventProducer;
import com.moba.battle.model.*;
import com.moba.battle.model.MOBAMap.GameMode;
import com.moba.battle.protocol.*;
import com.moba.battle.replay.ReplaySystem;
import com.moba.battle.storage.BattleLogStorage;
import com.moba.battle.monitor.ServerMonitor;
import com.moba.battle.network.codec.GameMessage;
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

        if (neededBots > 0) {
            AIController aiController = AIController.getInstance();
            aiController.createBotsForBattle(battleId, room.getSession(),
                    playerIds.size() / teamCount, neededBots, aiLevel);
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

        scheduler.schedule(() -> {
            room.setState(BattleRoom.RoomState.LOADING);
            room.getSession().setState(BattleSession.BattleState.LOADING);
            notifyPlayers(room);

            scheduler.schedule(() -> {
                room.start();
                room.getSession().start();
                log.info("Battle {} started with {} players ({} bots), seed={}",
                        battleId, playerIds.size(), neededBots, room.getEngine().getRandomSeed());
            }, 3, TimeUnit.SECONDS);
        }, 500, TimeUnit.MILLISECONDS);

        return room;
    }

    public BattleEnterResponse enterBattle(ChannelHandlerContext ctx, BattleEnterRequest request) {
        Optional<Player> playerOpt = PlayerManager.getInstance().getPlayerById(request.getPlayerId());
        if (playerOpt.isEmpty()) {
            return BattleEnterResponse.failure("Player not found");
        }

        BattleRoom room = RoomManager.getInstance().getRoom(request.getBattleId());
        if (room == null) {
            return BattleEnterResponse.failure("Battle not found");
        }

        BattlePlayer battlePlayer = room.getSession().getPlayer(request.getPlayerId());
        if (battlePlayer == null) {
            return BattleEnterResponse.failure("Not in this battle");
        }

        if (playerOpt.get().isReconnecting()) {
            if (!ReconnectManager.getInstance().isReconnectValid(request.getPlayerId())) {
                return BattleEnterResponse.failure("Reconnect timeout");
            }
            ReconnectManager.getInstance().cancelReconnectTimer(request.getPlayerId());
        }

        PlayerManager.getInstance().updatePlayerContext(ctx, request.getPlayerId());

        MOBAMap mobaMap = MapManager.getInstance().getMap(request.getBattleId());
        String mapConfigJson;
        int mapId;

        if (mobaMap != null) {
            mapId = mobaMap.getMapId();
            mapConfigJson = MapManager.getInstance().getMapStateJson(request.getBattleId());
        } else {
            mapId = defaultMapConfig.getMapId();
            mapConfigJson = "{\"mapId\":" + defaultMapConfig.getMapId() +
                    ",\"name\":\"" + defaultMapConfig.getMapName() +
                    "\",\"teams\":" + defaultMapConfig.getTeamCount() +
                    ",\"seed\":" + room.getEngine().getRandomSeed() + "}";
        }

        return BattleEnterResponse.success(
                request.getBattleId(),
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

    private void notifyPlayers(BattleRoom room) {
        String battleId = room.getBattleId();
        long randomSeed = room.getEngine().getRandomSeed();
        int playerCount = room.getPlayerCount();

        String notifyData = "NOTIFY|LOADING|" + battleId + "|seed=" + randomSeed + "|players=" + playerCount;
        byte[] body = notifyData.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (Long playerId : room.getSession().getBattlePlayers().keySet()) {
            Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                GameMessage msg = new GameMessage();
                msg.setMessageId(GameMessage.BATTLE_ENTER_RESPONSE);
                msg.setBody(body);
                player.getCtx().writeAndFlush(msg);
            }
        }

        BattleLogStorage.getInstance().submitBattleEvent(battleId, "LOADING_START", notifyData);
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

            BattleEventProducer producer = SpringContextHolder.getApplicationContext().getBeanProvider(BattleEventProducer.class).getIfAvailable();
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
                GameMessage msg = new GameMessage();
                msg.setMessageId(GameMessage.BATTLE_END_NOTIFY);
                msg.setBody(body);
                player.getCtx().writeAndFlush(msg);
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

