package com.moba.match.network.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moba.common.model.MatchInfo;
import com.moba.common.protocol.MessageType;
import com.moba.match.service.MatchmakingService;
import com.moba.match.service.MatchmakingService.MatchParty;
import com.moba.match.service.MatchmakingService.MatchPartyMember;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MatchWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame textFrame) {
            handleTextFrame(ctx, textFrame);
        } else {
            log.warn("不支持的WebSocket帧类型: {}", frame.getClass().getSimpleName());
        }
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("来自网关的无效JSON: {}", text.substring(0, Math.min(text.length(), 200)));
            return;
        }

        int cmd = root.has("cmd") ? root.get("cmd").asInt() : -1;
        int seq = root.has("seq") ? root.get("seq").asInt() : 0;
        long playerId = root.has("playerId") ? root.get("playerId").asLong() : 0;
        long sessionId = root.has("sessionId") ? root.get("sessionId").asLong() : 0;

        if (playerId <= 0) {
            log.warn("网关消息中无效的playerId: cmd={}", cmd);
            return;
        }

        MessageType messageType = MessageType.fromCode(cmd);
        if (messageType == null) {
            log.warn("未知的消息类型: cmd=0x{}", Integer.toHexString(cmd));
            return;
        }

        MatchmakingService matchService = getMatchService();
        if (matchService == null) {
            log.error("MatchmakingService不可用");
            sendErrorResponse(ctx, messageType, seq, playerId, sessionId, "SERVICE_UNAVAILABLE");
            return;
        }

        try {
            switch (messageType) {
                case MATCH_JOIN_REQ -> handleJoinMatch(ctx, matchService, root, messageType, seq, playerId, sessionId);
                case MATCH_CANCEL_REQ -> handleCancelMatch(ctx, matchService, messageType, seq, playerId, sessionId);
                case MATCH_STATUS_REQ -> handleMatchStatus(ctx, matchService, messageType, seq, playerId, sessionId);
                default -> log.warn("未处理的匹配消息类型: {}", messageType.name());
            }
        } catch (Exception e) {
            log.error("处理匹配消息出错: type={}, playerId={}", messageType.name(), playerId, e);
            sendErrorResponse(ctx, messageType, seq, playerId, sessionId, "INTERNAL_ERROR");
        }
    }

    private void handleJoinMatch(ChannelHandlerContext ctx, MatchmakingService matchService,
                                  JsonNode root, MessageType messageType, int seq,
                                  long playerId, long sessionId) throws Exception {
        JsonNode data = root.get("data");
        int gameMode = (data != null && data.has("gameMode")) ? data.get("gameMode").asInt() : 1;

        MatchParty party = new MatchParty();
        party.setGameMode(gameMode);

        if (data != null && data.has("members") && data.get("members").isArray()) {
            JsonNode membersNode = data.get("members");
            List<MatchPartyMember> members = new ArrayList<>(membersNode.size());
            long leaderId = playerId;

            for (JsonNode memberNode : membersNode) {
                MatchPartyMember member = new MatchPartyMember();
                member.setPlayerId(memberNode.has("playerId") ? memberNode.get("playerId").asLong() : 0);
                member.setNickname(memberNode.has("nickname") ? memberNode.get("nickname").asText() : "");
                member.setRankScore(memberNode.has("rankScore") ? memberNode.get("rankScore").asInt() : 1000);
                if (member.getPlayerId() > 0) {
                    members.add(member);
                }
            }

            if (members.isEmpty()) {
                MatchPartyMember leader = new MatchPartyMember();
                leader.setPlayerId(playerId);
                leader.setNickname(data.has("nickname") ? data.get("nickname").asText() : "");
                leader.setRankScore(data.has("rankScore") ? data.get("rankScore").asInt() : 1000);
                members.add(leader);
            }

            party.setMembers(members);
            party.setLeaderId(leaderId);
            party.setPartyId("party_" + leaderId + "_" + System.currentTimeMillis());
        } else {
            MatchPartyMember leader = new MatchPartyMember();
            leader.setPlayerId(playerId);
            leader.setNickname(data != null && data.has("nickname") ? data.get("nickname").asText() : "");
            leader.setRankScore(data != null && data.has("rankScore") ? data.get("rankScore").asInt() : 1000);
            party.setMembers(List.of(leader));
            party.setLeaderId(playerId);
            party.setPartyId(String.valueOf(playerId));
        }

        boolean success = matchService.joinMatchAsParty(party);

        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("cmd", getResponseCode(messageType));
        response.put("seq", seq);
        response.put("playerId", playerId);
        response.put("sessionId", sessionId);

        ObjectNode respData = OBJECT_MAPPER.createObjectNode();
        respData.put("success", success);
        if (success) {
            MatchInfo matchInfo = matchService.getPlayerMatch(playerId);
            if (matchInfo != null) {
                respData.put("matchId", matchInfo.getMatchId());
                respData.put("gameMode", matchInfo.getGameMode());
                respData.put("currentPlayers", matchInfo.getPlayerIds().size());
                respData.put("neededPlayers", matchInfo.getNeededPlayers());
                respData.put("state", matchInfo.getState().name());
                respData.put("partySize", party.getMembers().size());
            }
        } else {
            respData.put("errorCode", "JOIN_FAILED");
            respData.put("errorMessage", "加入匹配失败");
        }
        response.set("data", respData);

        ctx.writeAndFlush(new TextWebSocketFrame(OBJECT_MAPPER.writeValueAsString(response)));
        log.info("匹配加入响应: playerId={}, partySize={}, success={}, sessionId={}",
                playerId, party.getMembers().size(), success, sessionId);
    }

    private void handleCancelMatch(ChannelHandlerContext ctx, MatchmakingService matchService,
                                    MessageType messageType, int seq,
                                    long playerId, long sessionId) throws Exception {
        boolean success = matchService.cancelMatch(playerId);

        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("cmd", getResponseCode(messageType));
        response.put("seq", seq);
        response.put("playerId", playerId);
        response.put("sessionId", sessionId);

        ObjectNode respData = OBJECT_MAPPER.createObjectNode();
        respData.put("success", success);
        response.set("data", respData);

        ctx.writeAndFlush(new TextWebSocketFrame(OBJECT_MAPPER.writeValueAsString(response)));
        log.info("匹配取消响应: playerId={}, success={}", playerId, success);
    }

    private void handleMatchStatus(ChannelHandlerContext ctx, MatchmakingService matchService,
                                    MessageType messageType, int seq,
                                    long playerId, long sessionId) throws Exception {
        com.moba.common.model.MatchInfo matchInfo = matchService.getPlayerMatch(playerId);

        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("cmd", getResponseCode(messageType));
        response.put("seq", seq);
        response.put("playerId", playerId);
        response.put("sessionId", sessionId);

        ObjectNode respData = OBJECT_MAPPER.createObjectNode();
        if (matchInfo != null) {
            respData.put("matchId", matchInfo.getMatchId());
            respData.put("gameMode", matchInfo.getGameMode());
            respData.put("matchTime", matchInfo.getCreateTime());
            respData.put("found", true);
            respData.put("state", matchInfo.getState().name());
            respData.put("currentPlayers", matchInfo.getPlayerIds().size());
            respData.put("neededPlayers", matchInfo.getNeededPlayers());

            if (matchInfo.getState() == com.moba.common.model.MatchInfo.MatchState.READY) {
                var room = matchService.getPlayerRoom(playerId);
                if (room != null && room.getAssignedBattleId() != null) {
                    respData.put("battleId", room.getAssignedBattleId());
                    respData.put("battleServerIp", room.getBattleServerIp());
                    respData.put("battleServerPort", room.getBattleServerPort());
                }
            }
        } else {
            respData.put("found", false);
        }
        response.set("data", respData);

        ctx.writeAndFlush(new TextWebSocketFrame(OBJECT_MAPPER.writeValueAsString(response)));
    }

    private int getResponseCode(MessageType requestType) {
        MessageType respType = requestType.correspondingResponse();
        return respType != null ? respType.getCode() : 0;
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, MessageType messageType, int seq,
                                    long playerId, long sessionId, String errorCode) {
        try {
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("cmd", getResponseCode(messageType));
            response.put("seq", seq);
            response.put("playerId", playerId);
            response.put("sessionId", sessionId);

            ObjectNode respData = OBJECT_MAPPER.createObjectNode();
            respData.put("success", false);
            respData.put("errorCode", errorCode);
            response.set("data", respData);

            ctx.writeAndFlush(new TextWebSocketFrame(OBJECT_MAPPER.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    private MatchmakingService getMatchService() {
        try {
            return com.moba.netty.spring.SpringContextHolder.getBean(MatchmakingService.class);
        } catch (Exception e) {
            log.error("获取MatchmakingService bean失败", e);
            return null;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("MatchWebSocketHandler错误, 来自 {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
