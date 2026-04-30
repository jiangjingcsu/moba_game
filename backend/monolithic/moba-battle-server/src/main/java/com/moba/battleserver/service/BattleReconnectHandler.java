package com.moba.battleserver.service;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.manager.BattleRoom;
import com.moba.battleserver.manager.MapManager;
import com.moba.battleserver.manager.PlayerManager;
import com.moba.battleserver.manager.ReconnectManager;
import com.moba.battleserver.manager.RoomManager;
import com.moba.battleserver.model.BattlePlayer;
import com.moba.battleserver.model.MapConfig;
import com.moba.battleserver.model.MOBAMap;
import com.moba.battleserver.model.Player;
import com.moba.battleserver.protocol.BattleEnterRequest;
import com.moba.battleserver.protocol.BattleEnterResponse;
import com.moba.battleserver.protocol.ReconnectRequest;
import com.moba.battleserver.protocol.ReconnectResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class BattleReconnectHandler {
    private final PlayerManager playerManager;
    private final MapManager mapManager;
    private final ReconnectManager reconnectManager;
    private final MapConfig defaultMapConfig;

    public BattleReconnectHandler(PlayerManager playerManager, MapManager mapManager) {
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.reconnectManager = ServiceLocator.getInstance().getReconnectManager();
        this.defaultMapConfig = MapConfig.create3v3v3Map();
    }

    public BattleEnterResponse enterBattle(ChannelHandlerContext ctx, BattleEnterRequest request, RoomManager roomManager) {
        Optional<Player> playerOpt = playerManager.getPlayerById(request.getPlayerId());
        if (playerOpt.isEmpty()) {
            return BattleEnterResponse.failure("Player not found");
        }

        BattleRoom room = roomManager.getRoom(request.getBattleId());
        if (room == null) {
            return BattleEnterResponse.failure("Battle not found");
        }

        BattlePlayer battlePlayer = room.getSession().getPlayer(request.getPlayerId());
        if (battlePlayer == null) {
            return BattleEnterResponse.failure("Not in this battle");
        }

        Player player = playerOpt.get();
        if (player.isReconnecting()) {
            if (!reconnectManager.isReconnectValid(request.getPlayerId())) {
                return BattleEnterResponse.failure("Reconnect timeout");
            }
            reconnectManager.cancelReconnectTimer(request.getPlayerId());
        }

        playerManager.updatePlayerContext(ctx, request.getPlayerId());

        MOBAMap mobaMap = mapManager.getMap(request.getBattleId());
        String mapConfigJson;
        int mapId;

        if (mobaMap != null) {
            mapId = mobaMap.getMapId();
            mapConfigJson = mapManager.getMapStateJson(request.getBattleId());
        } else {
            mapId = defaultMapConfig.getMapId();
            mapConfigJson = "{\"mapId\":" + defaultMapConfig.getMapId()
                    + ",\"name\":\"" + defaultMapConfig.getMapName()
                    + "\",\"teams\":" + defaultMapConfig.getTeamCount()
                    + ",\"seed\":" + room.getEngine().getRandomSeed() + "}";
        }

        return BattleEnterResponse.success(request.getBattleId(), mapId, mapConfigJson);
    }

    public ReconnectResponse handleReconnect(ChannelHandlerContext ctx, ReconnectRequest request, RoomManager roomManager) {
        Optional<Player> playerOpt = playerManager.getPlayerById(request.getPlayerId());
        if (playerOpt.isEmpty()) {
            return ReconnectResponse.failure("Player not found");
        }

        BattleRoom room = roomManager.getRoom(request.getBattleId());
        if (room == null) {
            return ReconnectResponse.failure("Battle not found");
        }

        playerManager.updatePlayerContext(ctx, request.getPlayerId());
        Player player = playerOpt.get();
        player.setReconnecting(false);

        BattlePlayer battlePlayer = room.getSession().getPlayer(request.getPlayerId());
        String stateJson = serializeBattleState(room, battlePlayer);

        log.info("Player {} reconnected to battle {}, frame={}",
                request.getPlayerId(), request.getBattleId(), room.getEngine().getCurrentFrame());
        return ReconnectResponse.success(stateJson);
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
}
