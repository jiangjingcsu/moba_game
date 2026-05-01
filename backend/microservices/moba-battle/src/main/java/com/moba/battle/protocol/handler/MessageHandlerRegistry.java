package com.moba.battle.protocol.handler;

import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.core.SerializeType;
import com.moba.battle.protocol.serialize.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MessageHandlerRegistry {

    private static final MessageHandlerRegistry INSTANCE = new MessageHandlerRegistry();

    private final Map<MessageType, MessageHandler<?>> handlers = new ConcurrentHashMap<>();

    public static MessageHandlerRegistry getInstance() {
        return INSTANCE;
    }

    public void register(MessageType type, MessageHandler<?> handler) {
        handlers.put(type, handler);
        log.info("Registered message handler: {} -> {}", type.name(), handler.getClass().getSimpleName());
    }

    public void dispatch(ChannelHandlerContext ctx, GamePacket packet) {
        MessageType type = packet.getMessageType();
        MessageHandler<?> handler = handlers.get(type);

        if (handler == null) {
            log.warn("No handler for message type: {} (0x{}) from channel: {}",
                    type, Integer.toHexString(packet.getCommandCode()), ctx.channel().id().asShortText());
            return;
        }

        try {
            Object request = null;
            if (packet.getBody() != null && packet.getBody().length > 0) {
                Class<?> requestType = handler.getRequestType();
                if (requestType != Void.class) {
                    request = SerializerFactory.getSerializer(packet.getSerializeType())
                            .deserialize(packet.getBody(), requestType);
                }
            }

            @SuppressWarnings("unchecked")
            GamePacket response = ((MessageHandler<Object>) handler).handle(ctx, packet, request);

            if (response != null) {
                ctx.writeAndFlush(response);
            }
        } catch (Exception e) {
            log.error("Error handling message: {} from channel: {}",
                    type, ctx.channel().id().asShortText(), e);
        }
    }

    public boolean hasHandler(MessageType type) {
        return handlers.containsKey(type);
    }
}
