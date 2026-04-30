package com.moba.common.service;

import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;

import java.util.Optional;

public interface MatchService {

    boolean joinMatch(MatchRequestDTO request);

    boolean cancelMatch(long playerId);

    Optional<MatchResultDTO> getMatchResult(long playerId);

    int getQueueSize(int gameMode);
}
