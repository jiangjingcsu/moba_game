package com.moba.common.service;

import com.moba.common.constant.GameMode;
import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;

import java.util.Optional;

public interface MatchService {

    boolean joinMatch(MatchRequestDTO request);

    boolean cancelMatch(long userId);

    Optional<MatchResultDTO> getMatchResult(long userId);

    int getQueueSize(GameMode gameMode);
}
