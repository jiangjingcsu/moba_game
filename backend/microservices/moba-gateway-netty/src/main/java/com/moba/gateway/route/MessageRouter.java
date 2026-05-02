package com.moba.gateway.route;

import com.moba.common.protocol.GamePacket;
import com.moba.common.protocol.MessageModule;
import com.moba.common.protocol.MessageType;
import com.moba.gateway.network.session.GatewaySession;
import com.moba.gateway.network.session.GatewaySessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageRouter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final BackendConnectionPool connectionPool;
    private final RankTierRouter rankTierRouter;

    public MessageRouter(BackendConnectionPool connectionPool, RankTierRouter rankTierRouter) {
        this.connectionPool = connectionPool;
        this.rankTierRouter = rankTierRouter;
    }

    public MessageRouter(BackendConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        this.rankTierRouter = null;
    }

    public void route(GatewaySession session, GamePacket packet) {
        MessageType messageType = packet.getMessageType();
        if (messageType == null) {
            log.warn("消息类型为空, sessionId={}", session.getSessionId());
            return;
        }

        MessageModule module = messageType.getModule();

        switch (module) {
            case MATCH -> routeToMatchService(session, packet);
            case BATTLE, ROOM, SOCIAL, SPECTATOR -> routeToBattleService(session, packet);
            default -> log.warn("未知的消息模块: {}, sessionId={}", module, session.getSessionId());
        }
    }

    private void routeToMatchService(GatewaySession session, GamePacket packet) {
        String json = buildForwardJson(session, packet);
        if (json != null) {
            if (rankTierRouter != null) {
                String serviceKey = rankTierRouter.resolveMatchServiceKey(session.getRankScore());
                connectionPool.sendToMatchService(serviceKey, json);
                log.debug("段位路由转发到匹配服务: type={}, sessionId={}, rankScore={}, serviceKey={}",
                        packet.getMessageType().name(), session.getSessionId(), session.getRankScore(), serviceKey);
            } else {
                connectionPool.sendToMatchService(json);
                log.debug("转发到匹配服务: type={}, sessionId={}", packet.getMessageType().name(), session.getSessionId());
            }
        }
    }

    private void routeToBattleService(GatewaySession session, GamePacket packet) {
        String json = buildForwardJson(session, packet);
        if (json != null) {
            connectionPool.sendToBattleService(json);
            log.debug("转发到战斗服务: type={}, sessionId={}", packet.getMessageType().name(), session.getSessionId());
        }
    }

    private String buildForwardJson(GatewaySession session, GamePacket packet) {
        try {
            Object data = null;
            if (packet.getBody() != null && packet.getBody().length > 0) {
                data = OBJECT_MAPPER.readTree(packet.getBody());
            }
            var msg = new java.util.LinkedHashMap<String, Object>();
            msg.put("cmd", packet.getCommandCode());
            msg.put("seq", packet.getSequenceId());
            msg.put("ver", packet.getVersion());
            msg.put("st", packet.getSerializeType().getCode());
            msg.put("data", data);
            msg.put("playerId", session.getPlayerId());
            msg.put("sessionId", session.getSessionId());
            msg.put("rankScore", session.getRankScore());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("构建转发消息失败: sessionId={}", session.getSessionId(), e);
            return null;
        }
    }

    public void handleBackendResponse(String json, String sourceService) {
        try {
            var node = OBJECT_MAPPER.readTree(json);
            long sessionId = node.has("sessionId") ? node.get("sessionId").asLong() : 0;

            if (sessionId > 0) {
                GatewaySession session = GatewaySessionManager.getInstance().getSessionById(sessionId);
                if (session != null && session.isActive()) {
                    if (node.has("rankScore") && !node.get("rankScore").isNull()) {
                        session.setRankScore(node.get("rankScore").asInt());
                    }
                    session.sendToClient(json);
                    log.debug("回传响应到客户端: sessionId={}, source={}", sessionId, sourceService);
                } else {
                    log.warn("回传响应但Session不存在: sessionId={}, source={}", sessionId, sourceService);
                }
            }
        } catch (Exception e) {
            log.error("处理后端响应失败: source={}", sourceService, e);
        }
    }
}
