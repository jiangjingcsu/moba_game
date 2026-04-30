package com.moba.match.controller;

import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.protocol.ApiResponse;
import com.moba.match.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/internal/match")
@RequiredArgsConstructor
public class MatchInternalController {

    private final MatchmakingService matchmakingService;

    @PostMapping("/join")
    public ApiResponse<Boolean> joinMatch(@RequestBody MatchRequestDTO request) {
        try {
            boolean success = matchmakingService.joinMatch(
                    request.getPlayerId(),
                    request.getNickname(),
                    request.getRankScore(),
                    request.getGameMode()
            );
            return ApiResponse.success(success);
        } catch (Exception e) {
            log.error("Internal join match failed", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public ApiResponse<Boolean> cancelMatch(@RequestBody Map<String, Object> request) {
        try {
            long playerId = ((Number) request.get("playerId")).longValue();
            boolean success = matchmakingService.cancelMatch(playerId);
            return ApiResponse.success(success);
        } catch (Exception e) {
            log.error("Internal cancel match failed", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/result/{playerId}")
    public ApiResponse<MatchResultDTO> getMatchResult(@PathVariable("playerId") long playerId) {
        try {
            Optional<MatchResultDTO> result = matchmakingService.getMatchResult(playerId);
            return result.map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.success(null));
        } catch (Exception e) {
            log.error("Internal get match result failed for playerId={}", playerId, e);
            return ApiResponse.error(500, "getMatchResult error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @GetMapping("/queueSize")
    public ApiResponse<Integer> getQueueSize(@RequestParam("gameMode") int gameMode) {
        try {
            int size = matchmakingService.getQueueSize(gameMode);
            return ApiResponse.success(size);
        } catch (Exception e) {
            log.error("Internal get queue size failed", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
