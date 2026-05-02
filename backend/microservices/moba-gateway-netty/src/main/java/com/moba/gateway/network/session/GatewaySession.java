package com.moba.gateway.network.session;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewaySession {

    private final long sessionId;
    private final long playerId;
    private final String username;
    private final ChannelHandlerContext clientChannel;
    private volatile long lastActiveTime;
    private volatile int rankScore;

    public GatewaySession(long sessionId, ChannelHandlerContext clientChannel, long playerId, String username) {
        this.sessionId = sessionId;
        this.clientChannel = clientChannel;
        this.playerId = playerId;
        this.username = username;
        this.lastActiveTime = System.currentTimeMillis();
        this.rankScore = 0;
    }

    public GatewaySession(long sessionId, ChannelHandlerContext clientChannel, long playerId, String username, int rankScore) {
        this.sessionId = sessionId;
        this.clientChannel = clientChannel;
        this.playerId = playerId;
        this.username = username;
        this.lastActiveTime = System.currentTimeMillis();
        this.rankScore = rankScore;
    }

    public void sendToClient(String text) {
        if (clientChannel != null && clientChannel.channel().isActive()) {
            clientChannel.writeAndFlush(new TextWebSocketFrame(text));
        }
    }

    public void sendToClient(GamePacketData packetData) {
        sendToClient(packetData.toJson());
    }

    public boolean isActive() {
        return clientChannel != null && clientChannel.channel().isActive();
    }

    public void touch() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public long getSessionId() { return sessionId; }
    public long getPlayerId() { return playerId; }
    public String getUsername() { return username; }
    public ChannelHandlerContext getClientChannel() { return clientChannel; }
    public long getLastActiveTime() { return lastActiveTime; }
    public int getRankScore() { return rankScore; }
    public void setRankScore(int rankScore) { this.rankScore = rankScore; }
}
