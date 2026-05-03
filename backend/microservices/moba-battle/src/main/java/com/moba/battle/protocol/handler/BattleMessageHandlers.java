package com.moba.battle.protocol.handler;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.manager.RoomManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.model.Player;
import com.moba.battle.protocol.request.BattleEnterRequest;
import com.moba.battle.protocol.request.BattleReadyRequest;
import com.moba.battle.protocol.request.ReconnectRequest;
import com.moba.battle.protocol.response.BattleEnterResponse;
import com.moba.battle.protocol.response.BattleReadyResponse;
import com.moba.battle.protocol.response.ReconnectResponse;
import com.moba.battle.validator.RoomValidator;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import com.moba.netty.protocol.annotation.MessageHandler;
import com.moba.netty.protocol.annotation.MessageMapping;
import com.moba.netty.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@MessageHandler(extensionId = ProtocolConstants.EXTENSION_BATTLE)
public class BattleMessageHandlers {

    private final PlayerManager playerManager;
    private final BattleManager battleManager;
    private final RoomManager roomManager;
    private final RoomValidator roomValidator;

    public BattleMessageHandlers(PlayerManager playerManager,
                                 BattleManager battleManager,
                                 RoomManager roomManager,
                                 RoomValidator roomValidator) {
        this.playerManager = playerManager;
        this.battleManager = battleManager;
        this.roomManager = roomManager;
        this.roomValidator = roomValidator;
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_BATTLE_ENTER)
    public BattleEnterResponse handleBattleEnter(User user, BattleEnterRequest request) {
        if (user == null) {
            return BattleEnterResponse.failure("UNAUTHORIZED: 未认证的连接，请先完成登录");
        }

        long userId = user.getUserId();

        if (request.getBattleId() <= 0) {
            return BattleEnterResponse.failure("MISSING_BATTLE_ID: 战斗ID不能为空");
        }

        RoomValidator.RoomValidationResult validation = roomValidator.validate(userId, request.getBattleId());
        if (!validation.isValid()) {
            log.warn("房间验证失败: user={}, battleId={}, error={}", userId, request.getBattleId(), validation.getErrorMessage());
            return BattleEnterResponse.failure(validation.getErrorCode() + ": " + validation.getErrorMessage());
        }

        Player player = playerManager.registerPlayerFromToken(
                user.getSession().getConnection(), userId, "player_" + userId);
        roomValidator.markPlayerEntered(userId, request.getBattleId());

        BattleEnterResponse response = battleManager.enterBattle(user, request);
        log.info("玩家{}进入战斗{}", userId, request.getBattleId());
        return response;
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_BATTLE_READY)
    public BattleReadyResponse handleBattleReady(User user, BattleReadyRequest request) {
        if (user == null) {
            return BattleReadyResponse.failure("未认证的连接");
        }

        long userId = user.getUserId();
        long battleId = request.getBattleId();
        if (battleId <= 0) {
            return BattleReadyResponse.failure("战斗ID不能为空");
        }

        BattleRoom room = roomManager.getRoom(battleId);
        if (room == null) {
            return BattleReadyResponse.failure("房间不存在");
        }

        if (room.getState() != BattleRoom.RoomState.LOADING && room.getState() != BattleRoom.RoomState.WAITING) {
            return BattleReadyResponse.failure("房间状态不允许就绪操作");
        }

        boolean marked = room.markPlayerReady(userId);
        if (!marked) {
            return BattleReadyResponse.failure("你不是此房间的人类玩家");
        }

        boolean allReady = room.allHumanPlayersReady();
        if (allReady) {
            log.info("战斗{}所有真人玩家已准备, 开始倒计时", battleId);
            battleManager.startCountdown(room);
        }

        return BattleReadyResponse.success(room.getReadyCount(), room.getExpectedHumanCount(), allReady);
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_BATTLE_ACTION)
    public void handleBattleAction(User user, MessagePacket packet) {
        battleManager.handlePlayerAction(user, packet.getData().getBytes(StandardCharsets.UTF_8));
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_BATTLE_SKILL_CAST)
    public void handleSkillCast(User user, MessagePacket packet) {
        battleManager.handleSkillCast(user, packet.getData().getBytes(StandardCharsets.UTF_8));
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_BATTLE_RECONNECT)
    public ReconnectResponse handleReconnect(User user, ReconnectRequest request) {
        return battleManager.handleReconnect(user, request);
    }
}
