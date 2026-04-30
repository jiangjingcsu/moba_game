package com.moba.battle.service;

import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.manager.MatchManager;
import com.moba.battle.manager.BattleManager;
import com.moba.battle.network.codec.GameMessage;
import com.moba.battle.protocol.*;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageDispatchService {

    private final PlayerManager playerManager;
    private final MatchManager matchManager;
    private final BattleManager battleManager;

    public MessageDispatchService() {
        this.playerManager = PlayerManager.getInstance();
        this.matchManager = MatchManager.getInstance();
        this.battleManager = BattleManager.getInstance();
    }

    public static MessageDispatchService getInstance() {
        return SpringContextHolder.getBean(MessageDispatchService.class);
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
                handlePlayerAction(ctx, msg);
                break;
            case GameMessage.SKILL_CAST_REQUEST:
                handleSkillCast(ctx, msg);
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
        LoginRequest request = LoginRequest.parseFrom(msg.getBody());
        LoginResponse response = playerManager.handleLogin(ctx, request);
        GameMessage responseMsg = new GameMessage();
        responseMsg.setMessageId(GameMessage.LOGIN_RESPONSE);
        responseMsg.setBody(response.toByteArray());
        ctx.writeAndFlush(responseMsg);
    }

    private void handleMatch(ChannelHandlerContext ctx, GameMessage msg) {
        MatchRequest request = MatchRequest.parseFrom(msg.getBody());
        MatchResponse response = matchManager.joinMatch(ctx, request);
        GameMessage responseMsg = new GameMessage();
        responseMsg.setMessageId(GameMessage.MATCH_RESPONSE);
        responseMsg.setBody(response.toByteArray());
        ctx.writeAndFlush(responseMsg);
    }

    private void handleMatchCancel(ChannelHandlerContext ctx, GameMessage msg) {
        matchManager.cancelMatch(ctx);
    }

    private void handleBattleEnter(ChannelHandlerContext ctx, GameMessage msg) {
        BattleEnterRequest request = BattleEnterRequest.parseFrom(msg.getBody());
        BattleEnterResponse response = battleManager.enterBattle(ctx, request);
        GameMessage responseMsg = new GameMessage();
        responseMsg.setMessageId(GameMessage.BATTLE_ENTER_RESPONSE);
        responseMsg.setBody(response.toByteArray());
        ctx.writeAndFlush(responseMsg);
    }

    private void handlePlayerAction(ChannelHandlerContext ctx, GameMessage msg) {
        battleManager.handlePlayerAction(ctx, msg.getBody());
    }

    private void handleSkillCast(ChannelHandlerContext ctx, GameMessage msg) {
        battleManager.handleSkillCast(ctx, msg.getBody());
    }

    private void handleReconnect(ChannelHandlerContext ctx, GameMessage msg) {
        ReconnectRequest request = ReconnectRequest.parseFrom(msg.getBody());
        ReconnectResponse response = battleManager.handleReconnect(ctx, request);
        GameMessage responseMsg = new GameMessage();
        responseMsg.setMessageId(GameMessage.RECONNECT_RESPONSE);
        responseMsg.setBody(response.toByteArray());
        ctx.writeAndFlush(responseMsg);
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        playerManager.handleDisconnect(ctx);
        matchManager.handleDisconnect(ctx);
        battleManager.handleDisconnect(ctx);
    }
}
