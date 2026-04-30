package com.moba.battleserver.manager;

import com.moba.battleserver.model.Player;
import com.moba.battleserver.protocol.BattleEnterRequest;
import com.moba.battleserver.protocol.BattleEnterResponse;
import com.moba.battleserver.protocol.ReconnectRequest;
import com.moba.battleserver.protocol.ReconnectResponse;
import com.moba.battleserver.service.*;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class BattleManager {
    private final PlayerManager playerManager;
    private final RoomManager roomManager;
    private final MapManager mapManager;
    private final ReconnectManager reconnectManager;
    private final BattleCreator battleCreator;
    private final BattleInputHandler battleInputHandler;
    private final BattleStateBroadcaster battleStateBroadcaster;
    private final BattleReconnectHandler battleReconnectHandler;
    private final BattleEndHandler battleEndHandler;

    public BattleManager(PlayerManager playerManager, RoomManager roomManager,
                         MapManager mapManager, ReconnectManager reconnectManager,
                         BattleCreator battleCreator, BattleInputHandler battleInputHandler,
                         BattleStateBroadcaster battleStateBroadcaster,
                         BattleReconnectHandler battleReconnectHandler,
                         BattleEndHandler battleEndHandler) {
        this.playerManager = playerManager;
        this.roomManager = roomManager;
        this.mapManager = mapManager;
        this.reconnectManager = reconnectManager;
        this.battleCreator = battleCreator;
        this.battleInputHandler = battleInputHandler;
        this.battleStateBroadcaster = battleStateBroadcaster;
        this.battleReconnectHandler = battleReconnectHandler;
        this.battleEndHandler = battleEndHandler;
    }

    public BattleRoom createBattle(String battleId, java.util.List<Long> playerIds, int teamCount) {
        return createBattle(battleId, playerIds, teamCount, 0, 5);
    }

    public BattleRoom createBattle(String battleId, java.util.List<Long> playerIds, int teamCount,
                                   int neededBots, int aiLevel) {
        return battleCreator.createBattle(roomManager, battleId, playerIds, teamCount,
                neededBots, aiLevel, battleStateBroadcaster, battleEndHandler);
    }

    public BattleEnterResponse enterBattle(ChannelHandlerContext ctx, BattleEnterRequest request) {
        return battleReconnectHandler.enterBattle(ctx, request, roomManager);
    }

    public void handlePlayerAction(ChannelHandlerContext ctx, byte[] data) {
        battleInputHandler.handlePlayerAction(ctx, roomManager, playerManager, data);
    }

    public void handleSkillCast(ChannelHandlerContext ctx, byte[] data) {
        battleInputHandler.handleSkillCast(ctx, roomManager, playerManager,
                com.moba.battleserver.anticheat.AntiCheatValidator.getInstance(), data);
    }

    public ReconnectResponse handleReconnect(ChannelHandlerContext ctx, ReconnectRequest request) {
        return battleReconnectHandler.handleReconnect(ctx, request, roomManager);
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        Optional<Player> playerOpt = playerManager.getPlayerByChannel(ctx);
        playerOpt.ifPresent(player -> {
            BattleRoom room = roomManager.getPlayerRoom(player.getPlayerId());
            if (room != null) {
                player.setReconnecting(true);
                reconnectManager.startReconnectTimer(player.getPlayerId());
                log.info("Player {} disconnected from battle {}, waiting for reconnect",
                        player.getPlayerId(), room.getBattleId());
            }
        });
    }

    public BattleRoom getBattleRoom(String battleId) {
        return roomManager.getRoom(battleId);
    }

    public int getRoomCount() {
        return roomManager.getRoomCount();
    }

    public int getTotalPlayers() {
        return roomManager.getTotalPlayers();
    }
}
