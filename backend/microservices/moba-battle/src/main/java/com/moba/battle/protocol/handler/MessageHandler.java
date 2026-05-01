package com.moba.battle.protocol.handler;

import com.moba.battle.protocol.core.GamePacket;
import io.netty.channel.ChannelHandlerContext;

public interface MessageHandler<T> {
    GamePacket handle(ChannelHandlerContext ctx, GamePacket packet, T request) throws Exception;
    Class<T> getRequestType();
}
