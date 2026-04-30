package com.moba.battleserver.service;

import com.moba.battleserver.manager.BattleManager;
import com.moba.battleserver.manager.MatchManager;
import com.moba.battleserver.manager.PlayerManager;
import com.moba.battleserver.network.codec.GameMessage;
import com.moba.battleserver.protocol.*;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageDispatchService {
    private final PlayerManager playerManager;
    private final MatchManager matchManager;
    private final BattleManager battleManager;

    public MessageDispatchService(PlayerManager playerManager, MatchManager matchManager, BattleManager battleManager) {
        this.playerManager = playerManager;
        this.matchManager = matchManager;
        this.battleManager = battleManager;
    }

    public void dispatch(ChannelHandlerContext ctx, GameMessage msg) {
        switch (msg.getMessageId()) {
            case GameMessage.HEARTBEAT_REQUEST:
                handleHeartbeat(ctx, msg);
                break;
            case GameMessage.HEARTBEAT_RESPONSE:
                log.debug("Received heartbeat response from: {}", ctx.channel().id().asShortText());
                break;
            case GameMessage.LOGIN_REQUEST:
                handleLogin(ctx, msg);
                break;
            case GameMessage.MATCH_REQUEST:
                handleMatch(ctx, msg);
                break;
            case GameMessage.MATCH_CANCEL_REQUEST:
                handleMatchCancel(ctx, msg);
                break;
            case GameMessage.BATTLE_ENTER_REQUEST:
                handleBattleEnter(ctx, msg);
                break;
            case GameMessage.PLAYER_ACTION_REQUEST:
                battleManager.handlePlayerAction(ctx, msg.getBody());
                break;
            case GameMessage.SKILL_CAST_REQUEST:
                battleManager.handleSkillCast(ctx, msg.getBody());
                break;
            case GameMessage.RECONNECT_REQUEST:
                handleReconnect(ctx, msg);
                break;
            default:
                log.warn("Unknown message id: 0x{} from channel: {}",
                        Integer.toHexString(msg.getMessageId()), ctx.channel().id().asShortText());
                break;
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, GameMessage msg) {
        GameMessage response = new GameMessage();
        response.setMessageId(GameMessage.HEARTBEAT_RESPONSE);
        response.setBody(new byte[0]);
        ctx.writeAndFlush(response);
    }

    private void handleLogin(ChannelHandlerContext ctx, GameMessage msg) {
        LoginRequest request = JsonProtocol.fromBytes(msg.getBody(), LoginRequest.class);
        if (request == null) {
            request = new LoginRequest();
            request.setPlayerName("Unknown");
        }
        LoginResponse response = playerManager.handleLogin(ctx, request);
        sendResponse(ctx, GameMessage.LOGIN_RESPONSE, response);
    }

    private void handleMatch(ChannelHandlerContext ctx, GameMessage msg) {
        MatchRequest request = JsonProtocol.fromBytes(msg.getBody(), MatchRequest.class);
        if (request == null) {
            request = new MatchRequest();
        }
        MatchResponse response = matchManager.joinMatch(ctx, request);
        sendResponse(ctx, GameMessage.MATCH_RESPONSE, response);
    }

    private void handleMatchCancel(ChannelHandlerContext ctx, GameMessage msg) {
        matchManager.cancelMatch(ctx);
    }

    private void handleBattleEnter(ChannelHandlerContext ctx, GameMessage msg) {
        BattleEnterRequest request = JsonProtocol.fromBytes(msg.getBody(), BattleEnterRequest.class);
        if (request == null) {
            sendResponse(ctx, GameMessage.BATTLE_ENTER_RESPONSE,
                    BattleEnterResponse.failure("Invalid request"));
            return;
        }
        BattleEnterResponse response = battleManager.enterBattle(ctx, request);
        sendResponse(ctx, GameMessage.BATTLE_ENTER_RESPONSE, response);
    }

    private void handleReconnect(ChannelHandlerContext ctx, GameMessage msg) {
        ReconnectRequest request = JsonProtocol.fromBytes(msg.getBody(), ReconnectRequest.class);
        if (request == null) {
            sendResponse(ctx, GameMessage.RECONNECT_RESPONSE,
                    ReconnectResponse.failure("Invalid request"));
            return;
        }
        ReconnectResponse response = battleManager.handleReconnect(ctx, request);
        sendResponse(ctx, GameMessage.RECONNECT_RESPONSE, response);
    }

    private void sendResponse(ChannelHandlerContext ctx, int messageId, Object response) {
        GameMessage responseMsg = new GameMessage();
        responseMsg.setMessageId(messageId);
        responseMsg.setBody(JsonProtocol.toBytes(response));
        ctx.writeAndFlush(responseMsg);
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        playerManager.handleDisconnect(ctx);
        matchManager.handleDisconnect(ctx);
        battleManager.handleDisconnect(ctx);
    }
}
