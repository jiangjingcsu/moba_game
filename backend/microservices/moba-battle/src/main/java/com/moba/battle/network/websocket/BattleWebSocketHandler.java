package com.moba.battle.network.websocket;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.model.Player;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BattleWebSocketHandler extends BinaryWebSocketHandler {

    private final Map<String, Long> sessionPlayerMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        Long playerId = extractPlayerId(query);
        if (playerId != null) {
            sessionPlayerMap.put(session.getId(), playerId);
            PlayerManager.getInstance().addWebSocketSession(playerId, session);
            log.info("WebSocket connection established: sessionId={}, playerId={}", session.getId(), playerId);
        } else {
            log.warn("WebSocket connection without playerId, closing: {}", session.getId());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        Long playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) return;

        ByteBuffer payload = message.getPayload();
        byte[] data = new byte[payload.remaining()];
        payload.get(data);

        BattleManager.getInstance().handlePlayerAction(null, data);
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long playerId = sessionPlayerMap.get(session.getId());
        log.error("WebSocket transport error: sessionId={}, playerId={}", session.getId(), playerId, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long playerId = sessionPlayerMap.remove(session.getId());
        if (playerId != null) {
            PlayerManager.getInstance().removeWebSocketSession(playerId, session);
            BattleManager.getInstance().handleDisconnect(null);
            log.info("WebSocket connection closed: sessionId={}, playerId={}, status={}", session.getId(), playerId, status);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private Long extractPlayerId(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if ("playerId".equals(kv[0]) && kv.length == 2) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
