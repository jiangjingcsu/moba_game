package com.moba.match.service.impl;

import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.service.MatchService;
import com.moba.match.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Optional;

@Slf4j
@DubboService(parameters = {"serialization", "hessian2"})
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchmakingService matchmakingService;

    @Override
    public boolean joinMatch(MatchRequestDTO request) {
        return matchmakingService.joinMatch(
                request.getPlayerId(),
                request.getNickname(),
                request.getRankScore(),
                request.getGameMode()
        );
    }

    @Override
    public boolean cancelMatch(long playerId) {
        return matchmakingService.cancelMatch(playerId);
    }

    @Override
    public Optional<MatchResultDTO> getMatchResult(long playerId) {
        return matchmakingService.getMatchResult(playerId);
    }

    @Override
    public int getQueueSize(int gameMode) {
        return matchmakingService.getQueueSize(gameMode);
    }
}
