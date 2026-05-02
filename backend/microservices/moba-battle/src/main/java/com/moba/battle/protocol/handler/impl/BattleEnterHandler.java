package com.moba.battle.protocol.handler.impl;

import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.model.Player;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.handler.MessageHandler;
import com.moba.battle.protocol.request.BattleEnterRequest;
import com.moba.battle.protocol.response.BattleEnterResponse;
import com.moba.battle.protocol.serialize.SerializerFactory;
import com.moba.battle.validator.RoomValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BattleEnterHandler implements MessageHandler<BattleEnterRequest> {

    @Override
    public GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, BattleEnterRequest request) throws Exception {
        Long playerId = getPlayerIdFromChannel(ctx);
        if (playerId == null) {
            return buildError(packet, "UNAUTHORIZED", "未认证的连接，请先完成登录");
        }

        if (request.getRoomId() == null || request.getRoomId().isEmpty()) {
            return buildError(packet, "MISSING_ROOM_ID", "房间号不能为空");
        }

        RoomValidator roomValidator = SpringContextHolder.getBean(RoomValidator.class);
        RoomValidator.RoomValidationResult validation = roomValidator.validate(playerId, request.getRoomId());

        if (!validation.isValid()) {
            log.warn("房间验证失败: player={}, room={}, error={}", playerId, request.getRoomId(), validation.getErrorMessage());
            return buildError(packet, validation.getErrorCode(), validation.getErrorMessage());
        }

        PlayerManager playerManager = PlayerManager.getInstance();
        Player player = playerManager.registerPlayerFromToken(ctx, playerId, "player_" + playerId);

        roomValidator.markPlayerEntered(playerId, request.getRoomId());

        BattleEnterResponse response = BattleManager.getInstance().enterBattle(ctx, request);
        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);

        log.info("玩家{}进入战斗房间{}", playerId, request.getRoomId());
        return GamePacket.response(MessageType.BATTLE_ENTER_REQ, packet.getSequenceId(), body);
    }

    private Long getPlayerIdFromChannel(ChannelHandlerContext ctx) {
        AttributeKey<Object> key = AttributeKey.valueOf("playerId");
        Object obj = ctx.channel().attr(key).get();
        if (obj instanceof Long) {
            return (Long) obj;
        }
        return null;
    }

    private GamePacket buildError(GamePacket request, String errorCode, String errorMessage) throws Exception {
        BattleEnterResponse response = BattleEnterResponse.failure(errorCode + ": " + errorMessage);
        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);
        return GamePacket.response(MessageType.BATTLE_ENTER_REQ, request.getSequenceId(), body);
    }

    @Override
    public Class<BattleEnterRequest> getRequestType() {
        return BattleEnterRequest.class;
    }
}
