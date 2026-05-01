package com.moba.battle.protocol.handler.impl;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.handler.MessageHandler;
import com.moba.battle.protocol.request.ReconnectRequest;
import com.moba.battle.protocol.response.ReconnectResponse;
import com.moba.battle.protocol.serialize.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconnectHandler implements MessageHandler<ReconnectRequest> {

    @Override
    public GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, ReconnectRequest request) throws Exception {
        ReconnectResponse response = BattleManager.getInstance().handleReconnect(ctx, request);
        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);
        return GamePacket.response(MessageType.RECONNECT_REQ, packet.getSequenceId(), body);
    }

    @Override
    public Class<ReconnectRequest> getRequestType() {
        return ReconnectRequest.class;
    }
}
