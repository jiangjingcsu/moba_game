package com.moba.battle.service;

import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.handler.MessageHandlerRegistry;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.manager.BattleManager;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageDispatchService {

    private final PlayerManager playerManager;
    private final BattleManager battleManager;

    public MessageDispatchService() {
        this.playerManager = PlayerManager.getInstance();
        this.battleManager = BattleManager.getInstance();
    }

    public static MessageDispatchService getInstance() {
        return com.moba.battle.config.SpringContextHolder.getBean(MessageDispatchService.class);
    }

    public void dispatch(ChannelHandlerContext ctx, GamePacket packet) {
        if (packet.getMessageType() == null) {
            log.warn("未知消息类型: 0x{} 来自通道: {}",
                    Integer.toHexString(packet.getCommandCode()), ctx.channel().id().asShortText());
            return;
        }

        MessageType type = packet.getMessageType();

        if (type == MessageType.HEARTBEAT_REQ) {
            handleHeartbeat(ctx, packet);
            return;
        }

        if (type == MessageType.HEARTBEAT_RESP) {
            log.debug("收到心跳响应: {}", ctx.channel().id().asShortText());
            return;
        }

        MessageHandlerRegistry registry = MessageHandlerRegistry.getInstance();
        if (registry.hasHandler(type)) {
            registry.dispatch(ctx, packet);
        } else {
            log.warn("消息类型无处理器: {} 来自通道: {}",
                    type.name(), ctx.channel().id().asShortText());
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, GamePacket packet) {
        GamePacket response = new GamePacket(MessageType.HEARTBEAT_RESP, packet.getSequenceId());
        response.setBody(new byte[0]);
        ctx.writeAndFlush(response);
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        playerManager.handleDisconnect(ctx);
        battleManager.handleDisconnect(ctx);
    }
}
