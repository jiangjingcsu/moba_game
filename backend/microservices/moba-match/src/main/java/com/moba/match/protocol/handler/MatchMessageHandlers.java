package com.moba.match.protocol.handler;

import com.moba.common.constant.GameMode;
import com.moba.common.model.MatchInfo;
import com.moba.match.protocol.dto.MatchCancelResponse;
import com.moba.match.protocol.dto.MatchJoinRequest;
import com.moba.match.protocol.dto.MatchJoinResponse;
import com.moba.match.protocol.dto.MatchStatusResponse;
import com.moba.match.service.MatchPartyFactory;
import com.moba.match.service.MatchmakingService;
import com.moba.match.model.MatchParty;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.annotation.MessageHandler;
import com.moba.netty.protocol.annotation.MessageMapping;
import com.moba.netty.protocol.ProtocolConstants;
import com.moba.netty.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@MessageHandler(extensionId = ProtocolConstants.EXTENSION_MATCH)
public class MatchMessageHandlers {

    private final MatchmakingService matchService;
    private final MatchPartyFactory matchPartyFactory;

    public MatchMessageHandlers(MatchmakingService matchService, MatchPartyFactory matchPartyFactory) {
        this.matchService = matchService;
        this.matchPartyFactory = matchPartyFactory;
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_MATCH_JOIN)
    public MatchJoinResponse handleJoin(User user, MatchJoinRequest request) {
        if (user == null) {
            return MatchJoinResponse.builder()
                    .success(false)
                    .errorCode("INVALID_PLAYER")
                    .errorMessage("未认证的连接")
                    .build();
        }

        long userId = user.getUserId();

        int gameModeCode = request.getGameMode();
        GameMode gameMode = GameMode.fromCodeOrNull(gameModeCode);
        if (gameMode == null) {
            return MatchJoinResponse.builder()
                    .success(false)
                    .errorCode("INVALID_GAME_MODE")
                    .errorMessage("无效的游戏模式: " + gameModeCode)
                    .build();
        }

        MatchParty party = matchPartyFactory.createSoloParty(
                userId,
                request.getNickname(),
                request.getRankScore() > 0 ? request.getRankScore() : 1000,
                gameMode
        );
        boolean success = matchService.joinMatchAsParty(party);

        if (success) {
            MatchInfo matchInfo = matchService.getPlayerMatch(userId);
            long battleId = resolveBattleId(userId, matchInfo);
            return MatchJoinResponse.builder()
                    .success(true)
                    .battleId(battleId)
                    .gameMode(matchInfo != null ? matchInfo.getGameMode().getCode() : gameModeCode)
                    .currentPlayers(matchInfo != null ? matchInfo.getUserIds().size() : 1)
                    .neededPlayers(matchInfo != null ? matchInfo.getNeededPlayers() : 0)
                    .state(matchInfo != null ? matchInfo.getState().name() : "WAITING")
                    .partySize(party.getMembers().size())
                    .build();
        }

        return MatchJoinResponse.builder()
                .success(false)
                .errorCode("JOIN_FAILED")
                .errorMessage("加入匹配失败")
                .build();
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_MATCH_STATUS)
    public MatchStatusResponse handleStatus(User user, MessagePacket packet) {
        if (user == null) {
            return MatchStatusResponse.builder()
                    .success(false)
                    .found(false)
                    .build();
        }

        long userId = user.getUserId();

        MatchInfo matchInfo = matchService.getPlayerMatch(userId);
        if (matchInfo != null) {
            long battleId = resolveBattleId(userId, matchInfo);
            MatchStatusResponse.MatchStatusResponseBuilder builder = MatchStatusResponse.builder()
                    .success(true)
                    .found(true)
                    .battleId(battleId)
                    .gameMode(matchInfo.getGameMode().getCode())
                    .matchTime(matchInfo.getCreateTime())
                    .state(matchInfo.getState().name())
                    .currentPlayers(matchInfo.getUserIds().size())
                    .neededPlayers(matchInfo.getNeededPlayers());

            return builder.build();
        }

        return MatchStatusResponse.builder()
                .success(true)
                .found(false)
                .build();
    }

    @MessageMapping(cmdId = ProtocolConstants.CMD_MATCH_CANCEL)
    public MatchCancelResponse handleCancel(User user, MessagePacket packet) {
        if (user == null) {
            return MatchCancelResponse.builder()
                    .success(false)
                    .errorCode("INVALID_PLAYER")
                    .errorMessage("未认证的连接")
                    .build();
        }

        long userId = user.getUserId();

        boolean success = matchService.cancelMatch(userId);
        log.info("匹配取消: userId={}, success={}", userId, success);

        return MatchCancelResponse.builder()
                .success(success)
                .errorCode(success ? null : "CANCEL_FAILED")
                .errorMessage(success ? null : "取消匹配失败")
                .build();
    }

    private long resolveBattleId(long userId, MatchInfo matchInfo) {
        if (matchInfo != null && matchInfo.getState() == MatchInfo.MatchState.READY) {
            long battleId = matchService.getBattleIdByMatchId(matchInfo.getMatchId());
            if (battleId != 0) {
                return battleId;
            }
        }
        return 0;
    }
}
