package com.moba.battle.protocol.handler.impl;

import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.manager.RoomManager;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.handler.MessageHandler;
import com.moba.battle.protocol.request.BattleReadyRequest;
import com.moba.battle.protocol.response.BattleReadyResponse;
import com.moba.battle.protocol.serialize.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BattleReadyHandler implements MessageHandler<BattleReadyRequest> {

    @Override
    public GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, BattleReadyRequest request) throws Exception {
        Long playerId = getPlayerIdFromChannel(ctx);
        if (playerId == null) {
            return buildError(packet, "未认证的连接");
        }

        String roomId = request.getRoomId();
        if (roomId == null || roomId.isEmpty()) {
            return buildError(packet, "房间号不能为空");
        }

        BattleRoom room = RoomManager.getInstance().getRoom(roomId);
        if (room == null) {
            return buildError(packet, "房间不存在");
        }

        if (room.getState() != BattleRoom.RoomState.LOADING && room.getState() != BattleRoom.RoomState.WAITING) {
            return buildError(packet, "房间状态不允许就绪操作");
        }

        boolean marked = room.markPlayerReady(playerId);
        if (!marked) {
            return buildError(packet, "你不是此房间的人类玩家");
        }

        boolean allReady = room.allHumanPlayersReady();

        if (allReady) {
            log.info("All human players ready in room {}, starting countdown", roomId);
            BattleManager.getInstance().startCountdown(room);
        }

        BattleReadyResponse response = BattleReadyResponse.success(
                room.getReadyCount(),
                room.getExpectedHumanCount(),
                allReady
        );

        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);
        return GamePacket.response(MessageType.BATTLE_READY_REQ, packet.getSequenceId(), body);
    }

    private Long getPlayerIdFromChannel(ChannelHandlerContext ctx) {
        AttributeKey<Object> key = AttributeKey.valueOf("playerId");
        Object obj = ctx.channel().attr(key).get();
        if (obj instanceof Long) {
            return (Long) obj;
        }
        return null;
    }

    private GamePacket buildError(GamePacket request, String errorMessage) throws Exception {
        BattleReadyResponse response = BattleReadyResponse.failure(errorMessage);
        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);
        return GamePacket.response(MessageType.BATTLE_READY_REQ, request.getSequenceId(), body);
    }

    @Override
    public Class<BattleReadyRequest> getRequestType() {
        return BattleReadyRequest.class;
    }
}
