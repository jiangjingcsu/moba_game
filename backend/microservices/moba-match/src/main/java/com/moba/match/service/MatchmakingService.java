package com.moba.match.service;

import com.moba.common.constant.BattleConstants;
import com.moba.common.constant.GameMode;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.event.MatchSuccessEvent;
import com.moba.common.model.MatchInfo;
import com.moba.common.service.BattleService;
import com.moba.match.config.NacosConfigManager;
import com.moba.match.discovery.BattleServiceDiscovery;
import com.moba.match.discovery.BattleServiceDiscovery.BattleServerInfo;
import com.moba.match.event.MatchEventProducer;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchmakingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationContext applicationContext;
    private final BattleServiceDiscovery battleServiceDiscovery;
    private final NacosConfigManager nacosConfigManager;

    @DubboReference(interfaceClass = BattleService.class, check = false, timeout = 5000, retries = 0,
            parameters = {"serialization", "hessian2"})
    private BattleService battleService;

    @org.springframework.beans.factory.annotation.Value("${match.redisKeyExpireMinutes}")
    private long redisKeyExpireMinutes;

    @org.springframework.beans.factory.annotation.Value("${match.defaultRankScore}")
    private int defaultRankScore;

    private final Map<String, GameModeQueue> queues = new ConcurrentHashMap<>();

    public MatchmakingService(RedisTemplate<String, Object> redisTemplate,
                              ApplicationContext applicationContext,
                              BattleServiceDiscovery battleServiceDiscovery,
                              NacosConfigManager nacosConfigManager) {
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
        this.battleServiceDiscovery = battleServiceDiscovery;
        this.nacosConfigManager = nacosConfigManager;
    }

    @PostConstruct
    private void initializeQueues() {
        queues.put("3v3v3", new GameModeQueue(GameMode.MODE_3V3V3, 3, 3, 9));
        queues.put("5v5", new GameModeQueue(GameMode.MODE_5V5, 2, 5, 10));
        log.info("匹配队列已初始化: 3v3v3(3队/每队3人/最大组队3), 5v5(2队/每队5人/最大组队5)");
    }

    public boolean joinMatch(long playerId, String nickname, int rankScore, int gameMode) {
        MatchParty party = new MatchParty();
        party.setPartyId(String.valueOf(playerId));
        party.setGameMode(gameMode);
        MatchPartyMember leader = new MatchPartyMember();
        leader.setPlayerId(playerId);
        leader.setNickname(nickname);
        leader.setRankScore(rankScore);
        party.setMembers(List.of(leader));
        party.setLeaderId(playerId);
        return joinMatchAsParty(party);
    }

    public boolean joinMatchAsParty(MatchParty party) {
        try {
            String modeName = GameMode.getModeName(party.getGameMode());
            GameModeQueue queue = queues.get(modeName);

            if (queue == null) {
                log.error("无效的游戏模式: {}", party.getGameMode());
                return false;
            }

            int maxPartySize = queue.getMaxPartySize();
            if (party.getMembers().size() > maxPartySize) {
                log.error("组队人数 {} 超过 {} 模式最大限制 {}", party.getMembers().size(), modeName, maxPartySize);
                return false;
            }

            for (MatchPartyMember member : party.getMembers()) {
                if (queue.isPlayerInQueue(member.getPlayerId())) {
                    log.warn("玩家 {} 已在匹配队列中", member.getPlayerId());
                    return false;
                }
            }

            joinQueue(party, queue);
            return true;
        } catch (Exception e) {
            log.error("组队加入匹配失败: partyId={}", party.getPartyId(), e);
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
        GameModeQueue queue = queues.get(modeName);
        if (queue == null) return 0;
        return queue.getWaitingPlayerCount();
    }

    private void joinQueue(MatchParty party, GameModeQueue queue) {
        int tolerance = nacosConfigManager.getInitialTolerance();
        int avgRankScore = party.getAvgRankScore();

        MatchRoom room = queue.findBestRoom(avgRankScore, party.getMembers().size(), tolerance);
        if (room == null) {
            String matchId = UUID.randomUUID().toString();
            room = new MatchRoom();
            room.setMatchId(matchId);
            room.setGameMode(party.getGameMode());
            room.setTeamCount(queue.getTeamCount());
            room.setTeamSize(queue.getTeamSize());
            room.setNeededPlayers(queue.getTotalPlayers());
            room.setCreateTime(System.currentTimeMillis());
            room.setState(MatchInfo.MatchState.PENDING);
            room.initTeams();
            queue.addRoom(room);
        }

        room.setState(MatchInfo.MatchState.FILLING);
        int teamIndex = room.assignParty(party);
        queue.registerParty(party, room.getMatchId());

        for (MatchPartyMember member : party.getMembers()) {
            String playerMatchKey = "player:match:" + member.getPlayerId();
            redisTemplate.opsForValue().set(playerMatchKey, room.getMatchId(), redisKeyExpireMinutes, TimeUnit.MINUTES);
        }

        log.info("组队 {}({}人, 平均段位={}) 加入匹配: {}, matchId: {}, 分配到队伍{}, 当前玩家: {}/{}",
                party.getPartyId(), party.getMembers().size(), avgRankScore,
                GameMode.getModeName(party.getGameMode()), room.getMatchId(), teamIndex,
                room.getTotalPlayerCount(), room.getNeededPlayers());

        if (room.getTotalPlayerCount() >= room.getNeededPlayers()) {
            room.setState(MatchInfo.MatchState.READY);
        }

        checkAndStartMatch(queue);
    }

    public void leaveQueue(long playerId) {
        String playerMatchKey = "player:match:" + playerId;
        Object matchIdObj = redisTemplate.opsForValue().get(playerMatchKey);
        String matchId = matchIdObj != null ? matchIdObj.toString() : null;

        if (matchId != null) {
            for (GameModeQueue queue : queues.values()) {
                if (queue.removePlayerFromRoom(playerId, matchId)) {
                    redisTemplate.delete(playerMatchKey);
                    log.info("玩家 {} 离开匹配队列", playerId);
                    break;
                }
            }
        }
    }

    private void checkAndStartMatch(GameModeQueue queue) {
        List<MatchRoom> readyRooms = queue.getReadyRooms();
        for (MatchRoom room : readyRooms) {
            try {
                startBattle(room, queue);
                queue.removeRoom(room.getMatchId());
            } catch (Exception e) {
                log.error("为匹配 {} 启动战斗失败", room.getMatchId(), e);
            }
        }
    }

    private void startBattle(MatchRoom room, GameModeQueue queue) {
        BattleServerInfo battleServer = battleServiceDiscovery.selectBestBattleServer();
        if (battleServer == null) {
            log.error("没有可用的战斗服务器, 匹配 {} 无法启动", room.getMatchId());
            return;
        }

        CreateBattleRequest request = new CreateBattleRequest();
        request.setPlayerIds(room.getAllPlayerIds());
        request.setGameMode(room.getGameMode());
        request.setTeamCount(room.getTeamCount());
        request.setNeededBots(room.getNeededPlayers());
        request.setAiLevel(BattleConstants.DEFAULT_AI_LEVEL);

        CreateBattleResponse response;
        try {
            response = battleService.createBattle(request);
            if (response == null) {
                response = CreateBattleResponse.fail("战斗服务无响应");
            }
        } catch (Exception e) {
            log.error("Dubbo调用战斗服务创建战斗失败", e);
            response = CreateBattleResponse.fail(e.getMessage());
        }

        if (response.isSuccess()) {
            room.setState(MatchInfo.MatchState.READY);
            room.setAssignedBattleId(response.getBattleId());
            room.setBattleServerIp(battleServer.getIp());
            room.setBattleServerPort(battleServer.getWsPort());
            log.info("匹配 {} 已在战斗服务器 {}:{} 上启动战斗: {} (负载分数={})",
                    room.getMatchId(), battleServer.getIp(), battleServer.getWsPort(),
                    response.getBattleId(), battleServer.getLoadScore());

            for (Long pid : room.getAllPlayerIds()) {
                String matchResultKey = "match:result:" + pid;
                Map<String, String> resultData = new HashMap<>();
                resultData.put("matchId", room.getMatchId());
                resultData.put("battleId", response.getBattleId());
                resultData.put("battleServerIp", battleServer.getIp());
                resultData.put("battleServerPort", String.valueOf(battleServer.getWsPort()));
                resultData.put("gameMode", String.valueOf(room.getGameMode()));
                resultData.put("teamCount", String.valueOf(room.getTeamCount()));
                redisTemplate.opsForValue().set(matchResultKey, resultData, redisKeyExpireMinutes, TimeUnit.MINUTES);
            }

            try {
                MatchEventProducer producer = applicationContext.getBeanProvider(MatchEventProducer.class).getIfAvailable();
                if (producer != null) {
                    MatchSuccessEvent event = new MatchSuccessEvent();
                    event.setEventId(UUID.randomUUID().toString());
                    event.setTimestamp(System.currentTimeMillis());
                    event.setMatchId(room.getMatchId());
                    event.setGameMode(room.getGameMode());
                    event.setPlayerIds(new ArrayList<>(room.getAllPlayerIds()));
                    event.setNeededBots(room.getNeededPlayers());
                    event.setAiLevel(BattleConstants.DEFAULT_AI_LEVEL);
                    event.setMatchTime(System.currentTimeMillis());
                    producer.publishMatchSuccess(event);
                } else {
                    log.warn("MatchEventProducer不可用 (RocketMQ未启用), 跳过事件发布");
                }
            } catch (Exception e) {
                log.error("为匹配 {} 发布匹配成功事件失败", room.getMatchId(), e);
            }
        } else {
            log.error("为匹配 {} 在战斗服务器 {}:{} 创建战斗失败: {}",
                    room.getMatchId(), battleServer.getIp(), battleServer.getWsPort(), response.getErrorMessage());
        }
    }

    public MatchInfo getPlayerMatch(long playerId) {
        String playerMatchKey = "player:match:" + playerId;
        Object matchIdObj = redisTemplate.opsForValue().get(playerMatchKey);
        if (matchIdObj == null) {
            return null;
        }
        String matchId = matchIdObj.toString();
        for (GameModeQueue queue : queues.values()) {
            MatchRoom room = queue.getRoom(matchId);
            if (room != null) {
                return room.toMatchInfo();
            }
        }
        return null;
    }

    public MatchRoom getPlayerRoom(long playerId) {
        String playerMatchKey = "player:match:" + playerId;
        Object matchIdObj = redisTemplate.opsForValue().get(playerMatchKey);
        if (matchIdObj == null) {
            return null;
        }
        String matchId = matchIdObj.toString();
        for (GameModeQueue queue : queues.values()) {
            MatchRoom room = queue.getRoom(matchId);
            if (room != null) {
                return room;
            }
        }
        return null;
    }

    @Scheduled(fixedRateString = "${match.cleanupIntervalMs}")
    public void cleanupExpiredMatches() {
        long now = System.currentTimeMillis();
        long timeoutMs = BattleConstants.MATCHMAKING_TIMEOUT_SECONDS * 1000;

        for (GameModeQueue queue : queues.values()) {
            List<MatchRoom> expired = queue.rooms.values().stream()
                    .filter(r -> r.getState() == MatchInfo.MatchState.PENDING
                            || r.getState() == MatchInfo.MatchState.FILLING)
                    .filter(r -> now - r.getCreateTime() > timeoutMs)
                    .collect(Collectors.toList());

            for (MatchRoom room : expired) {
                room.setState(MatchInfo.MatchState.TIMEOUT);
                queue.removeRoom(room.getMatchId());
                log.info("匹配 {} 在 {}秒后超时", room.getMatchId(),
                        BattleConstants.MATCHMAKING_TIMEOUT_SECONDS);
            }
        }
    }

    @Scheduled(fixedRateString = "${match.toleranceExpandIntervalMs}")
    public void expandToleranceAndRematch() {
        long now = System.currentTimeMillis();
        int initialTolerance = nacosConfigManager.getInitialTolerance();
        int maxTolerance = nacosConfigManager.getMaxTolerance();
        int expandStep = nacosConfigManager.getToleranceExpandStep();
        int expandInterval = nacosConfigManager.getToleranceExpandIntervalSeconds();

        for (GameModeQueue queue : queues.values()) {
            List<MatchRoom> fillingRooms = queue.rooms.values().stream()
                    .filter(r -> r.getState() == MatchInfo.MatchState.FILLING)
                    .filter(r -> r.getTotalPlayerCount() < r.getNeededPlayers())
                    .collect(Collectors.toList());

            for (MatchRoom room : fillingRooms) {
                long waitSeconds = (now - room.getCreateTime()) / 1000;
                int expandedTolerance = (int) Math.min(
                        initialTolerance + (waitSeconds / expandInterval) * expandStep,
                        maxTolerance
                );

                if (expandedTolerance > initialTolerance) {
                    int avgRankScore = room.getAvgRankScore();
                    List<MatchParty> waitingParties = queue.getWaitingPartiesNotInRoom(room.getMatchId());

                    for (MatchParty party : waitingParties) {
                        if (Math.abs(party.getAvgRankScore() - avgRankScore) <= expandedTolerance) {
                            int availableSlots = room.getAvailableSlotsInBestTeam(party.getMembers().size());
                            if (availableSlots >= party.getMembers().size()) {
                                room.assignParty(party);
                                queue.registerParty(party, room.getMatchId());

                                for (MatchPartyMember member : party.getMembers()) {
                                    String playerMatchKey = "player:match:" + member.getPlayerId();
                                    redisTemplate.opsForValue().set(playerMatchKey, room.getMatchId(), redisKeyExpireMinutes, TimeUnit.MINUTES);
                                }

                                log.info("组队 {}(平均段位={}) 以扩展容忍度 {} 匹配到 {}",
                                        party.getPartyId(), party.getAvgRankScore(),
                                        room.getMatchId(), expandedTolerance);

                                if (room.getTotalPlayerCount() >= room.getNeededPlayers()) {
                                    room.setState(MatchInfo.MatchState.READY);
                                    break;
                                }
                            }
                        }
                    }

                    if (room.getState() == MatchInfo.MatchState.READY) {
                        checkAndStartMatch(queue);
                    }
                }
            }
        }
    }

    @Data
    public static class MatchParty {
        private String partyId;
        private long leaderId;
        private int gameMode;
        private List<MatchPartyMember> members;

        public int getAvgRankScore() {
            if (members == null || members.isEmpty()) return 0;
            return (int) members.stream()
                    .mapToInt(MatchPartyMember::getRankScore)
                    .average()
                    .orElse(0);
        }
    }

    @Data
    public static class MatchPartyMember {
        private long playerId;
        private String nickname;
        private int rankScore;
    }

    @Data
    public static class MatchRoom {
        private String matchId;
        private int gameMode;
        private int teamCount;
        private int teamSize;
        private int neededPlayers;
        private long createTime;
        private MatchInfo.MatchState state;
        private List<TeamSlot> teams;
        private int aiLevel = BattleConstants.DEFAULT_AI_LEVEL;
        private String assignedBattleId;
        private String battleServerIp;
        private int battleServerPort;

        public void initTeams() {
            teams = new ArrayList<>(teamCount);
            for (int i = 0; i < teamCount; i++) {
                teams.add(new TeamSlot(i, teamSize));
            }
        }

        public int assignParty(MatchParty party) {
            return assignParty(party, -1);
        }

        public int assignParty(MatchParty party, int preferredTeam) {
            if (preferredTeam >= 0 && preferredTeam < teamCount
                    && teams.get(preferredTeam).getAvailableSlots() >= party.getMembers().size()) {
                teams.get(preferredTeam).addParty(party);
                return preferredTeam;
            }

            TeamSlot bestTeam = null;
            int minRankDiff = Integer.MAX_VALUE;
            for (TeamSlot team : teams) {
                if (team.getAvailableSlots() < party.getMembers().size()) continue;
                int diff = Math.abs(team.getAvgRankScore() - party.getAvgRankScore());
                if (diff < minRankDiff) {
                    minRankDiff = diff;
                    bestTeam = team;
                }
            }

            if (bestTeam == null) {
                for (TeamSlot team : teams) {
                    if (team.getAvailableSlots() >= party.getMembers().size()) {
                        bestTeam = team;
                        break;
                    }
                }
            }

            if (bestTeam != null) {
                bestTeam.addParty(party);
                return bestTeam.getTeamIndex();
            }
            return -1;
        }

        public int getTotalPlayerCount() {
            return teams.stream().mapToInt(TeamSlot::getPlayerCount).sum();
        }

        public int getAvailableSlotsInBestTeam(int partySize) {
            int maxSlots = 0;
            for (TeamSlot team : teams) {
                if (team.getAvailableSlots() >= partySize) {
                    maxSlots = Math.max(maxSlots, team.getAvailableSlots());
                }
            }
            return maxSlots;
        }

        public int getAvgRankScore() {
            return (int) teams.stream()
                    .flatMap(t -> t.members.stream())
                    .mapToInt(MatchPartyMember::getRankScore)
                    .average()
                    .orElse(0);
        }

        public List<Long> getAllPlayerIds() {
            return teams.stream()
                    .flatMap(t -> t.members.stream())
                    .map(MatchPartyMember::getPlayerId)
                    .collect(Collectors.toList());
        }

        public MatchInfo toMatchInfo() {
            MatchInfo info = new MatchInfo();
            info.setMatchId(matchId);
            info.setGameMode(gameMode);
            info.setState(state);
            info.setCreateTime(createTime);
            info.setPlayerIds(getAllPlayerIds());
            info.setNeededPlayers(neededPlayers);
            info.setAiLevel(aiLevel);
            return info;
        }
    }

    @Data
    public static class TeamSlot {
        private final int teamIndex;
        private final int maxSize;
        private final List<MatchPartyMember> members = new CopyOnWriteArrayList<>();
        private final List<String> partyIds = new CopyOnWriteArrayList<>();

        public TeamSlot(int teamIndex, int maxSize) {
            this.teamIndex = teamIndex;
            this.maxSize = maxSize;
        }

        public int getAvailableSlots() {
            return maxSize - members.size();
        }

        public int getPlayerCount() {
            return members.size();
        }

        public int getAvgRankScore() {
            if (members.isEmpty()) return 0;
            return (int) members.stream()
                    .mapToInt(MatchPartyMember::getRankScore)
                    .average()
                    .orElse(0);
        }

        public void addParty(MatchParty party) {
            members.addAll(party.getMembers());
            partyIds.add(party.getPartyId());
        }

        public void removeParty(String partyId) {
            int idx = partyIds.indexOf(partyId);
            if (idx >= 0) {
                partyIds.remove(idx);
            }
        }

        public boolean containsPlayer(long playerId) {
            return members.stream().anyMatch(m -> m.getPlayerId() == playerId);
        }
    }

    public class GameModeQueue {
        private final int gameMode;
        private final int teamCount;
        private final int teamSize;
        private final int totalPlayers;
        private final int maxPartySize;
        private final Map<String, MatchRoom> rooms = new ConcurrentHashMap<>();
        private final Map<Long, String> playerToRoom = new ConcurrentHashMap<>();
        private final Map<String, MatchParty> partyRegistry = new ConcurrentHashMap<>();

        public GameModeQueue(int gameMode, int teamCount, int teamSize, int totalPlayers) {
            this.gameMode = gameMode;
            this.teamCount = teamCount;
            this.teamSize = teamSize;
            this.totalPlayers = totalPlayers;
            this.maxPartySize = teamSize;
        }

        public int getMaxPartySize() {
            return maxPartySize;
        }

        public int getTeamCount() {
            return teamCount;
        }

        public int getTeamSize() {
            return teamSize;
        }

        public int getTotalPlayers() {
            return totalPlayers;
        }

        public void addRoom(MatchRoom room) {
            rooms.put(room.getMatchId(), room);
        }

        public void removeRoom(String matchId) {
            MatchRoom room = rooms.remove(matchId);
            if (room != null) {
                for (Long playerId : room.getAllPlayerIds()) {
                    playerToRoom.remove(playerId);
                }
                for (TeamSlot team : room.getTeams()) {
                    for (String partyId : team.getPartyIds()) {
                        partyRegistry.remove(partyId);
                    }
                }
            }
        }

        public MatchRoom getRoom(String matchId) {
            return rooms.get(matchId);
        }

        public void registerParty(MatchParty party, String matchId) {
            partyRegistry.put(party.getPartyId(), party);
            for (MatchPartyMember member : party.getMembers()) {
                playerToRoom.put(member.getPlayerId(), matchId);
            }
        }

        public boolean isPlayerInQueue(long playerId) {
            return playerToRoom.containsKey(playerId);
        }

        public boolean removePlayerFromRoom(long playerId, String matchId) {
            MatchRoom room = rooms.get(matchId);
            if (room == null) return false;

            for (TeamSlot team : room.getTeams()) {
                if (team.containsPlayer(playerId)) {
                    String partyId = findPartyIdByPlayer(playerId);
                    if (partyId != null) {
                        MatchParty party = partyRegistry.get(partyId);
                        if (party != null) {
                            for (MatchPartyMember member : party.getMembers()) {
                                playerToRoom.remove(member.getPlayerId());
                            }
                            partyRegistry.remove(partyId);
                        }
                    }
                    playerToRoom.remove(playerId);

                    if (room.getTotalPlayerCount() == 0) {
                        rooms.remove(matchId);
                    }
                    return true;
                }
            }
            return false;
        }

        private String findPartyIdByPlayer(long playerId) {
            for (Map.Entry<String, MatchParty> entry : partyRegistry.entrySet()) {
                for (MatchPartyMember member : entry.getValue().getMembers()) {
                    if (member.getPlayerId() == playerId) {
                        return entry.getKey();
                    }
                }
            }
            return null;
        }

        public MatchRoom findBestRoom(int avgRankScore, int partySize, int tolerance) {
            return rooms.values().stream()
                    .filter(r -> r.getState() == MatchInfo.MatchState.PENDING
                            || r.getState() == MatchInfo.MatchState.FILLING)
                    .filter(r -> r.getAvailableSlotsInBestTeam(partySize) >= partySize)
                    .filter(r -> {
                        int roomAvg = r.getAvgRankScore();
                        return Math.abs(avgRankScore - roomAvg) <= tolerance;
                    })
                    .min(Comparator.comparingInt(r ->
                            Math.abs(avgRankScore - r.getAvgRankScore())))
                    .orElse(null);
        }

        public List<MatchRoom> getReadyRooms() {
            return rooms.values().stream()
                    .filter(r -> r.getTotalPlayerCount() >= r.getNeededPlayers())
                    .collect(Collectors.toList());
        }

        public List<MatchParty> getWaitingPartiesNotInRoom(String matchId) {
            Set<Long> playersInRoom = new HashSet<>();
            MatchRoom room = rooms.get(matchId);
            if (room != null) {
                playersInRoom.addAll(room.getAllPlayerIds());
            }

            return partyRegistry.values().stream()
                    .filter(p -> p.getMembers().stream()
                            .noneMatch(m -> playersInRoom.contains(m.getPlayerId())))
                    .collect(Collectors.toList());
        }

        public int getWaitingPlayerCount() {
            return playerToRoom.size();
        }
    }
}
