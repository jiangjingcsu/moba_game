package com.moba.gateway.network.session;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class GatewaySessionManager {

    private static final GatewaySessionManager INSTANCE = new GatewaySessionManager();

    private final AtomicLong sessionIdGenerator = new AtomicLong(1);
    private final Map<Long, GatewaySession> sessionsById = new ConcurrentHashMap<>();
    private final Map<Long, GatewaySession> sessionsByPlayerId = new ConcurrentHashMap<>();

    public GatewaySessionManager() {
    }

    public static GatewaySessionManager getInstance() {
        return INSTANCE;
    }

    public GatewaySession createSession(ChannelHandlerContext ctx, long playerId, String username) {
        return createSession(ctx, playerId, username, 0);
    }

    public GatewaySession createSession(ChannelHandlerContext ctx, long playerId, String username, int rankScore) {
        GatewaySession existing = sessionsByPlayerId.get(playerId);
        if (existing != null) {
            sessionsById.remove(existing.getSessionId());
            GatewaySession newSession = new GatewaySession(existing.getSessionId(), ctx, playerId, username, rankScore);
            sessionsById.put(newSession.getSessionId(), newSession);
            sessionsByPlayerId.put(playerId, newSession);
            log.info("网关Session重连: sessionId={}, playerId={}, rankScore={}", newSession.getSessionId(), playerId, rankScore);
            return newSession;
        }

        long sessionId = sessionIdGenerator.incrementAndGet();
        GatewaySession session = new GatewaySession(sessionId, ctx, playerId, username, rankScore);
        sessionsById.put(sessionId, session);
        sessionsByPlayerId.put(playerId, session);
        log.info("网关Session创建: sessionId={}, playerId={}, rankScore={}", sessionId, playerId, rankScore);
        return session;
    }

    public GatewaySession getSessionByPlayerId(long playerId) {
        return sessionsByPlayerId.get(playerId);
    }

    public GatewaySession getSessionById(long sessionId) {
        return sessionsById.get(sessionId);
    }

    public void removeSession(long sessionId) {
        GatewaySession session = sessionsById.remove(sessionId);
        if (session != null) {
            sessionsByPlayerId.remove(session.getPlayerId(), session);
            log.info("网关Session移除: sessionId={}, playerId={}", sessionId, session.getPlayerId());
        }
    }

    public int getActiveSessionCount() {
        return sessionsByPlayerId.size();
    }
}
