package com.moba.business.controller;

import com.moba.business.repository.UserRepository;
import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.protocol.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/match")
public class MatchController {

    private static final String MATCH_SERVICE_URL = "http://localhost:8082/internal/match";

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/join")
    public ApiResponse joinMatch(@RequestHeader("X-Player-Id") String playerIdStr,
                                 @RequestBody Map<String, Object> request) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            int gameMode = request.containsKey("gameMode") ? ((Number) request.get("gameMode")).intValue() : 1;

            var userOpt = userRepository.findById(playerId);
            if (userOpt.isEmpty()) {
                return ApiResponse.error(404, "用户不存在");
            }
            var user = userOpt.get();

            MatchRequestDTO matchRequest = new MatchRequestDTO();
            matchRequest.setPlayerId(playerId);
            matchRequest.setNickname(user.getNickname());
            matchRequest.setRankScore(user.getRankScore() != null ? user.getRankScore() : 1000);
            matchRequest.setGameMode(gameMode);

            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    MATCH_SERVICE_URL + "/join",
                    HttpMethod.POST,
                    new HttpEntity<>(matchRequest),
                    new ParameterizedTypeReference<>() {}
            );

            ApiResponse<Boolean> body = response.getBody();
            if (body != null && body.getCode() == 0) {
                return ApiResponse.success(Map.of("message", "已加入匹配队列"));
            }
            return ApiResponse.error(500, "加入匹配队列失败: " + (body != null ? body.getMessage() : "unknown"));
        } catch (Exception e) {
            log.error("Join match failed", e);
            return ApiResponse.error(500, "加入匹配失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse getMatchStatus(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);

            ResponseEntity<ApiResponse<MatchResultDTO>> response = restTemplate.exchange(
                    MATCH_SERVICE_URL + "/result/" + playerId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            ApiResponse<MatchResultDTO> body = response.getBody();
            if (body == null || body.getData() == null) {
                return ApiResponse.success(Map.of("matchStatus", "NOT_IN_QUEUE"));
            }

            MatchResultDTO result = body.getData();
            String matchId = result.getMatchId();
            String battleId = "BATTLE_" + matchId.replace("-", "").substring(0, Math.min(8, matchId.replace("-", "").length()));
            return ApiResponse.success(Map.of(
                    "matchStatus", "MATCHED",
                    "matchId", matchId,
                    "battleId", battleId,
                    "gameMode", result.getGameMode(),
                    "playerIds", result.getPlayerIds(),
                    "battleServerUrl", "ws://localhost:8888/ws/battle"
            ));
        } catch (Exception e) {
            log.error("Get match status failed", e);
            return ApiResponse.error(500, "获取匹配状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public ApiResponse cancelMatch(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);

            ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                    MATCH_SERVICE_URL + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("playerId", playerId)),
                    new ParameterizedTypeReference<>() {}
            );

            ApiResponse<Boolean> body = response.getBody();
            return (body != null && body.getCode() == 0 && Boolean.TRUE.equals(body.getData()))
                    ? ApiResponse.success(Map.of("message", "已取消匹配"))
                    : ApiResponse.error(500, "取消匹配失败");
        } catch (Exception e) {
            log.error("Cancel match failed", e);
            return ApiResponse.error(500, "取消匹配失败: " + e.getMessage());
        }
    }
}
