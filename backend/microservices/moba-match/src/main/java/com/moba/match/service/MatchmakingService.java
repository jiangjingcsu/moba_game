package com.moba.match.service;

import com.moba.common.constant.BattleConstants;
import com.moba.common.constant.GameMode;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.event.MatchSuccessEvent;
import com.moba.common.model.MatchInfo;
import com.moba.common.protocol.ApiResponse;
import com.moba.match.event.MatchEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationContext applicationContext;

    private static final String BATTLE_SERVICE_URL = "http://localhost:8083/internal/battle";
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, MatchmakingQueue> queues = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    private void initializeQueues() {
        queues.put("3v3v3", new MatchmakingQueue(3, 9));
        queues.put("5v5", new MatchmakingQueue(5, 10));
    }

    public boolean joinMatch(long playerId, String nickname, int rankScore, int gameMode) {
        try {
            joinQueue(playerId, gameMode);
            return true;
        } catch (Exception e) {
            log.error("Player {} failed to join match", playerId, e);
            return false;
        }
    }

    public boolean cancelMatch(long playerId) {
        leaveQueue(playerId);
        return true;
    }

    public Optional<MatchResultDTO> getMatchResult(long playerId) {
        MatchInfo match = getPlayerMatch(playerId);
        if (match == null) return Optional.empty();

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(match.getMatchId());
        result.setGameMode(match.getGameMode());
        result.setPlayerIds(match.getPlayerIds());
        result.setMatchTime(match.getCreateTime());
        return Optional.of(result);
    }

    public int getQueueSize(int gameMode) {
        String modeName = GameMode.getModeName(gameMode);
        MatchmakingQueue queue = queues.get(modeName);
        if (queue == null) return 0;
        return (int) queue.playerToMatch.size();
    }

    public MatchInfo joinQueue(long playerId, int gameMode) {
        String modeName = GameMode.getModeName(gameMode);
        MatchmakingQueue queue = queues.get(modeName);

        if (queue == null) {
            throw new IllegalArgumentException("Invalid game mode: " + gameMode);
        }

        if (queue.isPlayerInQueue(playerId)) {
            log.warn("Player {} already in matchmaking queue", playerId);
            return queue.getPlayerMatch(playerId);
        }

        MatchInfo matchInfo = queue.findAvailableMatch(gameMode);
        if (matchInfo == null) {
            String matchId = UUID.randomUUID().toString();
            matchInfo = new MatchInfo();
            matchInfo.setMatchId(matchId);
            matchInfo.setGameMode(gameMode);
            matchInfo.setState(MatchInfo.MatchState.PENDING);
            matchInfo.setCreateTime(System.currentTimeMillis());
            matchInfo.setPlayerIds(new ArrayList<>());
            matchInfo.setNeededPlayers(getNeededPlayers(gameMode));
            matchInfo.setAiLevel(BattleConstants.DEFAULT_AI_LEVEL);
            queue.addMatch(matchInfo);
        }

        matchInfo.setState(MatchInfo.MatchState.FILLING);
        queue.addPlayer(playerId, matchInfo.getMatchId());

        String playerMatchKey = "player:match:" + playerId;
        redisTemplate.opsForValue().set(playerMatchKey, matchInfo.getMatchId(), 5, TimeUnit.MINUTES);

        log.info("Player {} joined matchmaking queue: {}, matchId: {}, currentPlayers: {}/{}",
                playerId, modeName, matchInfo.getMatchId(),
                matchInfo.getPlayerIds().size(), matchInfo.getNeededPlayers());

        if (matchInfo.getPlayerIds().size() >= matchInfo.getNeededPlayers()) {
            matchInfo.setState(MatchInfo.MatchState.READY);
        }

        checkAndStartMatch(modeName, queue);

        return matchInfo;
    }

    public void leaveQueue(long playerId) {
        String playerMatchKey = "player:match:" + playerId;
        Object matchIdObj = redisTemplate.opsForValue().get(playerMatchKey);
        String matchId = matchIdObj != null ? matchIdObj.toString() : null;

        if (matchId != null) {
            for (MatchmakingQueue queue : queues.values()) {
                if (queue.removePlayer(playerId, matchId)) {
                    redisTemplate.delete(playerMatchKey);
                    log.info("Player {} left matchmaking queue", playerId);
                    break;
                }
            }
        }
    }

    private void checkAndStartMatch(String modeName, MatchmakingQueue queue) {
        List<MatchInfo> readyMatches = queue.getReadyMatches();
        for (MatchInfo match : readyMatches) {
            try {
                startBattle(match);
                queue.removeMatch(match.getMatchId());
            } catch (Exception e) {
                log.error("Failed to start battle for match: {}", match.getMatchId(), e);
            }
        }
    }

    private void startBattle(MatchInfo match) {
        CreateBattleRequest request = new CreateBattleRequest();
        request.setPlayerIds(match.getPlayerIds());
        request.setGameMode(match.getGameMode());
        request.setTeamCount(match.getGameMode() == GameMode.MODE_3V3V3 ? 3 : 2);
        request.setNeededBots(match.getNeededPlayers());
        request.setAiLevel(match.getAiLevel());

        CreateBattleResponse response;
        try {
            ResponseEntity<ApiResponse<CreateBattleResponse>> httpResponse = restTemplate.exchange(
                    BATTLE_SERVICE_URL + "/create",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );
            ApiResponse<CreateBattleResponse> body = httpResponse.getBody();
            response = (body != null && body.getCode() == 0) ? body.getData() : null;
            if (response == null) {
                response = CreateBattleResponse.fail("No response from battle service");
            }
        } catch (Exception e) {
            log.error("Failed to call battle service via REST", e);
            response = CreateBattleResponse.fail(e.getMessage());
        }

        if (response.isSuccess()) {
            match.setState(MatchInfo.MatchState.READY);
            match.setAssignedBattleId(response.getBattleId());
            log.info("Match {} started battle: {}", match.getMatchId(), response.getBattleId());

            try {
                MatchEventProducer producer = applicationContext.getBeanProvider(MatchEventProducer.class).getIfAvailable();
                if (producer != null) {
                    MatchSuccessEvent event = new MatchSuccessEvent();
                    event.setEventId(java.util.UUID.randomUUID().toString());
                    event.setTimestamp(System.currentTimeMillis());
                    event.setMatchId(match.getMatchId());
                    event.setGameMode(match.getGameMode());
                    event.setPlayerIds(new ArrayList<>(match.getPlayerIds()));
                    event.setNeededBots(match.getNeededPlayers());
                    event.setAiLevel(match.getAiLevel());
                    event.setMatchTime(System.currentTimeMillis());
                    producer.publishMatchSuccess(event);
                } else {
                    log.warn("MatchEventProducer not available (RocketMQ disabled), skipping event publish");
                }
            } catch (Exception e) {
                log.error("Failed to publish match success event for match: {}", match.getMatchId(), e);
            }
        } else {
            log.error("Failed to create battle for match {}: {}", match.getMatchId(), response.getErrorMessage());
        }
    }

    public MatchInfo getMatchStatus(String matchId) {
        for (MatchmakingQueue queue : queues.values()) {
            MatchInfo match = queue.getMatch(matchId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    public MatchInfo getPlayerMatch(long playerId) {
        String playerMatchKey = "player:match:" + playerId;
        Object matchIdObj = redisTemplate.opsForValue().get(playerMatchKey);
        if (matchIdObj == null) {
            return null;
        }
        String matchId = matchIdObj.toString();
        return getMatchStatus(matchId);
    }

    private int getNeededPlayers(int gameMode) {
        if (gameMode == GameMode.MODE_3V3V3) {
            return BattleConstants.MIN_PLAYERS_FOR_3V3V3;
        } else {
            return BattleConstants.MIN_PLAYERS_FOR_5V5;
        }
    }

    @Scheduled(fixedRate = 30000)
    public void cleanupExpiredMatches() {
        long now = System.currentTimeMillis();
        long timeoutMs = BattleConstants.MATCHMAKING_TIMEOUT_SECONDS * 1000;

        for (Map.Entry<String, MatchmakingQueue> entry : queues.entrySet()) {
            MatchmakingQueue queue = entry.getValue();
            List<MatchInfo> expired = queue.matches.values().stream()
                    .filter(m -> m.getState() == MatchInfo.MatchState.PENDING
                            || m.getState() == MatchInfo.MatchState.FILLING)
                    .filter(m -> now - m.getCreateTime() > timeoutMs)
                    .collect(Collectors.toList());

            for (MatchInfo match : expired) {
                match.setState(MatchInfo.MatchState.TIMEOUT);
                queue.removeMatch(match.getMatchId());
                log.info("Match {} timed out after {}s", match.getMatchId(),
                        BattleConstants.MATCHMAKING_TIMEOUT_SECONDS);
            }
        }
    }

    public static class MatchmakingQueue {
        private final int teamCount;
        private final int maxPlayers;
        private final Map<String, MatchInfo> matches = new ConcurrentHashMap<>();
        private final Map<Long, String> playerToMatch = new ConcurrentHashMap<>();

        public MatchmakingQueue(int teamCount, int maxPlayers) {
            this.teamCount = teamCount;
            this.maxPlayers = maxPlayers;
        }

        public void addMatch(MatchInfo match) {
            matches.put(match.getMatchId(), match);
        }

        public void removeMatch(String matchId) {
            MatchInfo match = matches.remove(matchId);
            if (match != null) {
                for (Long playerId : match.getPlayerIds()) {
                    playerToMatch.remove(playerId);
                }
            }
        }

        public void addPlayer(long playerId, String matchId) {
            MatchInfo match = matches.get(matchId);
            if (match != null) {
                if (!(match.getPlayerIds() instanceof CopyOnWriteArrayList)) {
                    List<Long> safeList = new CopyOnWriteArrayList<>(match.getPlayerIds());
                    match.setPlayerIds(safeList);
                }
                match.getPlayerIds().add(playerId);
                playerToMatch.put(playerId, matchId);
            }
        }

        public boolean removePlayer(long playerId, String matchId) {
            MatchInfo match = matches.get(matchId);
            if (match != null) {
                match.getPlayerIds().remove(playerId);
                playerToMatch.remove(playerId);
                if (match.getPlayerIds().isEmpty()) {
                    matches.remove(matchId);
                }
                return true;
            }
            return false;
        }

        public MatchInfo getMatch(String matchId) {
            return matches.get(matchId);
        }

        public MatchInfo findAvailableMatch(int gameMode) {
            return matches.values().stream()
                    .filter(m -> m.getState() == MatchInfo.MatchState.PENDING
                            || m.getState() == MatchInfo.MatchState.FILLING)
                    .filter(m -> m.getPlayerIds().size() < m.getNeededPlayers())
                    .findFirst()
                    .orElse(null);
        }

        public boolean isPlayerInQueue(long playerId) {
            return playerToMatch.containsKey(playerId);
        }

        public MatchInfo getPlayerMatch(long playerId) {
            String matchId = playerToMatch.get(playerId);
            if (matchId == null) return null;
            return matches.get(matchId);
        }

        public List<MatchInfo> getReadyMatches() {
            return matches.values().stream()
                    .filter(m -> m.getPlayerIds().size() >= m.getNeededPlayers())
                    .collect(Collectors.toList());
        }
    }
}
