package com.moba.battle.protocol.handler.impl;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.handler.MessageHandler;
import com.moba.battle.protocol.request.SkillCastRequest;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SkillCastHandler implements MessageHandler<SkillCastRequest> {

    @Override
    public GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, SkillCastRequest request) throws Exception {
        BattleManager.getInstance().handleSkillCast(ctx, packet.getBody());
        return null;
    }

    @Override
    public Class<SkillCastRequest> getRequestType() {
        return SkillCastRequest.class;
    }
}
