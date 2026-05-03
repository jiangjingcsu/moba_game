package com.moba.match.service.impl;

import com.moba.common.constant.GameMode;
import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.service.MatchService;
import com.moba.match.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchmakingService matchmakingService;

    @Override
    public boolean joinMatch(MatchRequestDTO request) {
        return matchmakingService.joinMatch(
                request.getUserId(),
                request.getNickname(),
                request.getRankScore(),
                request.getGameMode()
        );
    }

    @Override
    public boolean cancelMatch(long userId) {
        return matchmakingService.cancelMatch(userId);
    }

    @Override
    public Optional<MatchResultDTO> getMatchResult(long userId) {
        return matchmakingService.getMatchResult(userId);
    }

    @Override
    public int getQueueSize(GameMode gameMode) {
        return matchmakingService.getQueueSize(gameMode);
    }
}
