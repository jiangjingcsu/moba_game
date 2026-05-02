package com.moba.battle.protocol.handler.impl;

import com.moba.battle.manager.PlayerManager;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.handler.MessageHandler;
import com.moba.battle.protocol.request.LoginRequest;
import com.moba.battle.protocol.response.LoginResponse;
import com.moba.battle.protocol.serialize.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoginHandler implements MessageHandler<LoginRequest> {

    @Override
    public GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, LoginRequest request) throws Exception {
        LoginResponse response = PlayerManager.getInstance().handleLogin(ctx, request);
        byte[] body = SerializerFactory.getSerializer(SerializeType.JSON).serialize(response);

        GamePacket respPacket = GamePacket.response(MessageType.LOGIN_REQ, packet.getSequenceId(), body);
        log.info("登录已处理: playerName={}, success={}", request.getPlayerName(), response.isSuccess());
        return respPacket;
    }

    @Override
    public Class<LoginRequest> getRequestType() {
        return LoginRequest.class;
    }
}
