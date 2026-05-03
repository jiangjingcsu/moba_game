package com.moba.match.service;

import com.moba.common.constant.GameMode;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.model.MatchInfo;
import com.moba.match.config.NacosConfigManager;
import com.moba.match.config.RankTierRange;
import com.moba.match.model.MatchParty;
import com.moba.match.model.MatchPartyMember;
import com.moba.match.model.MatchRoom;
import com.moba.match.model.ScoreRange;
import com.moba.match.model.ScoreRangeMatcher;
import com.moba.match.network.MatchChannelManager;
import com.moba.match.protocol.dto.MatchProgressNotify;
import com.moba.match.repository.MatchRedisRepository;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MatchmakingService {

    private final MatchRedisRepository matchRedisRepository;
    private final NacosConfigManager nacosConfigManager;
    private final MatchBattleAllocator matchBattleAllocator;
    private final MatchPartyFactory matchPartyFactory;
    private final MatchChannelManager channelManager;
    private final ObjectMapper objectMapper;

    @Value("${match.defaultRankScore}")
    private int defaultRankScore;

    private final List<ScoreRange> scoreRanges = new ArrayList<>();
    private final Map<String, ScoreRangeMatcher> matchers = new ConcurrentHashMap<>();

    public MatchmakingService(MatchRedisRepository matchRedisRepository,
                              NacosConfigManager nacosConfigManager,
                              MatchBattleAllocator matchBattleAllocator,
                              MatchPartyFactory matchPartyFactory,
                              MatchChannelManager channelManager,
                              ObjectMapper objectMapper) {
        this.matchRedisRepository = matchRedisRepository;
        this.nacosConfigManager = nacosConfigManager;
        this.matchBattleAllocator = matchBattleAllocator;
        this.matchPartyFactory = matchPartyFactory;
        this.channelManager = channelManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void initializeMatchers() {
        List<RankTierRange> nacosRanges = nacosConfigManager.getRankTierConfig() != null
                ? nacosConfigManager.getRankTierConfig().getRankTierRanges() : null;

        if (nacosRanges != null && !nacosRanges.isEmpty()) {
            for (RankTierRange range : nacosRanges) {
                scoreRanges.add(new ScoreRange(range.getTierName(), range.getMinScore(), range.getMaxScore()));
            }
            log.info("从Nacos配置加载分数段: {}个", scoreRanges.size());
        } else {
            scoreRanges.add(new ScoreRange("bronze", 0, 999));
            scoreRanges.add(new ScoreRange("silver", 1000, 1499));
            scoreRanges.add(new ScoreRange("gold", 1500, 1999));
            scoreRanges.add(new ScoreRange("platinum", 2000, 2499));
            scoreRanges.add(new ScoreRange("diamond", 2500, 9999));
            log.info("使用默认分数段: {}个", scoreRanges.size());
        }

        for (ScoreRange range : scoreRanges) {
            matchers.put(matcherKey(range.getName(), GameMode.MODE_3V3V3),
                    new ScoreRangeMatcher(range, GameMode.MODE_3V3V3));
            matchers.put(matcherKey(range.getName(), GameMode.MODE_5V5),
                    new ScoreRangeMatcher(range, GameMode.MODE_5V5));
            matchers.put(matcherKey(range.getName(), GameMode.MODE_AI_3V3V3),
                    new ScoreRangeMatcher(range, GameMode.MODE_AI_3V3V3));
            matchers.put(matcherKey(range.getName(), GameMode.MODE_AI_5V5),
                    new ScoreRangeMatcher(range, GameMode.MODE_AI_5V5));
        }

        log.info("匹配器已初始化: {}个分数段 × 4种游戏模式 = {}个独立匹配器",
                scoreRanges.size(), matchers.size());
    }

    public Map<String, ScoreRangeMatcher> getAllMatchers() {
        return Collections.unmodifiableMap(matchers);
    }

    private String matcherKey(String tierName, GameMode gameMode) {
        return tierName + "_" + gameMode.getName();
    }

    private ScoreRange findScoreRange(int rankScore) {
        for (ScoreRange range : scoreRanges) {
            if (rankScore >= range.getMinScore() && rankScore <= range.getMaxScore()) {
                return range;
            }
        }
        return scoreRanges.get(scoreRanges.size() - 1);
    }

    public boolean joinMatch(long userId, String nickname, int rankScore, GameMode gameMode) {
        if (userId <= 0) {
            log.warn("无效的userId: {}", userId);
            return false;
        }
        if (rankScore < 0) {
            log.warn("无效的rankScore: {}, 使用默认值: {}", rankScore, defaultRankScore);
            rankScore = defaultRankScore;
        }

        MatchParty party = matchPartyFactory.createSoloParty(userId, nickname, rankScore, gameMode);
        return joinMatchAsParty(party);
    }

    public boolean joinMatchAsParty(MatchParty party) {
        try {
            int avgRankScore = party.getAvgRankScore();
            GameMode gameMode = party.getGameMode();

            ScoreRange range = findScoreRange(avgRankScore);
            String key = matcherKey(range.getName(), gameMode);
            ScoreRangeMatcher matcher = matchers.get(key);

            if (matcher == null) {
                log.error("未找到匹配器: scoreRange={}, gameMode={}, key={}", range.getName(), gameMode, key);
                return false;
            }

            if (!validatePartySize(party, matcher, gameMode)) {
                return false;
            }

            for (MatchPartyMember member : party.getMembers()) {
                if (matcher.isPlayerInQueue(member.getUserId())) {
                    log.warn("玩家 {} 已在匹配队列中", member.getUserId());
                    return false;
                }
            }

            joinQueue(party, matcher);
            return true;
        } catch (Exception e) {
            log.error("组队加入匹配失败: partyId={}", party.getPartyId(), e);
            return false;
        }
    }

    private boolean validatePartySize(MatchParty party, ScoreRangeMatcher matcher, GameMode gameMode) {
        if (matcher.isAiMode()) {
            if (party.getMembers().size() > 1) {
                log.error("AI模式仅允许1名真人玩家, 当前组队人数={}", party.getMembers().size());
                return false;
            }
        } else {
            if (party.getMembers().size() > matcher.getTeamSize()) {
                log.error("组队人数 {} 超过 {} 模式最大限制 {}", party.getMembers().size(),
                        gameMode.getName(), matcher.getTeamSize());
                return false;
            }
        }
        return true;
    }

    private void joinQueue(MatchParty party, ScoreRangeMatcher matcher) {
        int tolerance = nacosConfigManager.getInitialTolerance();
        int avgRankScore = party.getAvgRankScore();

        MatchRoom room = matcher.findBestRoom(avgRankScore, party.getMembers().size(), tolerance);
        if (room == null) {
            room = MatchRoom.createForMatcher(matcher, party);
            matcher.addRoom(room);
        }

        synchronized (room) {
            room.setState(MatchInfo.MatchState.FILLING);
            int teamIndex = room.assignParty(party);
            matcher.registerParty(party, room.getMatchId());

            for (MatchPartyMember member : party.getMembers()) {
                matchRedisRepository.bindPlayerToMatch(member.getUserId(), room.getMatchId());
            }

            log.info("组队 {}({}人, 平均段位={}) 加入匹配器[{}]: matchId={}, 队伍{}, 玩家 {}/{}, aiMode={}",
                    party.getPartyId(), party.getMembers().size(), avgRankScore,
                    matcher.getScoreRange().getName(), room.getMatchId(), teamIndex,
                    room.getTotalPlayerCount(), room.getNeededPlayers(), matcher.isAiMode());

            pushMatchProgress(room);

            int neededToStart = matcher.isAiMode() ? matcher.getMaxRealPlayers() : matcher.getTotalPlayers();
            if (room.getTotalPlayerCount() >= neededToStart) {
                room.setState(MatchInfo.MatchState.READY);
                checkAndStartMatch(matcher, room);
            }
        }
    }

    public boolean cancelMatch(long userId) {
        leaveQueue(userId);
        return true;
    }

    public void leaveQueue(long userId) {
        Long matchId = matchRedisRepository.getMatchIdByPlayer(userId);
        if (matchId == null || matchId == 0) return;

        for (ScoreRangeMatcher matcher : matchers.values()) {
            if (matcher.removePlayerFromRoom(userId, matchId)) {
                matchRedisRepository.unbindPlayerMatch(userId);
                log.info("玩家 {} 离开匹配队列", userId);

                MatchRoom room = matcher.getRoom(matchId);
                if (room != null && room.getState() != MatchInfo.MatchState.READY) {
                    pushMatchProgress(room);
                }
                break;
            }
        }
    }

    private void checkAndStartMatch(ScoreRangeMatcher matcher, MatchRoom room) {
        if (room.getState() != MatchInfo.MatchState.READY) return;
        try {
            boolean success = matchBattleAllocator.allocateAndStart(room);
            if (success) {
                matcher.removeRoom(room.getMatchId());
            } else {
                room.setState(MatchInfo.MatchState.FILLING);
                log.warn("匹配 {} 启动战斗失败(无可用服务器), 重新放回队列等待", room.getMatchId());
            }
        } catch (Exception e) {
            log.error("为匹配 {} 启动战斗失败", room.getMatchId(), e);
            room.setState(MatchInfo.MatchState.FILLING);
        }
    }

    public Optional<MatchResultDTO> getMatchResult(long userId) {
        MatchInfo match = getPlayerMatch(userId);
        if (match == null) return Optional.empty();

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(match.getMatchId());
        result.setGameMode(match.getGameMode());
        result.setUserIds(match.getUserIds());
        result.setMatchTime(match.getCreateTime());
        return Optional.of(result);
    }

    public MatchInfo getPlayerMatch(long userId) {
        Long matchId = matchRedisRepository.getMatchIdByPlayer(userId);
        if (matchId == null) return null;

        for (ScoreRangeMatcher matcher : matchers.values()) {
            MatchRoom room = matcher.getRoom(matchId);
            if (room != null) return room.toMatchInfo();
        }
        return null;
    }

    public MatchRoom getPlayerRoom(long userId) {
        Long matchId = matchRedisRepository.getMatchIdByPlayer(userId);
        if (matchId == null) return null;

        for (ScoreRangeMatcher matcher : matchers.values()) {
            MatchRoom room = matcher.getRoom(matchId);
            if (room != null) return room;
        }
        return null;
    }

    public long getBattleIdByMatchId(long matchId) {
        Long battleId = matchRedisRepository.getBattleIdByMatchId(matchId);
        return battleId != null ? battleId : 0;
    }

    public int getQueueSize(GameMode gameMode) {
        int total = 0;
        for (ScoreRangeMatcher matcher : matchers.values()) {
            if (matcher.getGameMode() == gameMode) {
                total += matcher.getWaitingPlayerCount();
            }
        }
        return total;
    }

    private void pushMatchProgress(MatchRoom room) {
        try {
            List<Long> userIds = room.getAllUserIds();
            if (userIds.isEmpty()) return;

            MatchProgressNotify notify = MatchProgressNotify.builder()
                    .matchId(room.getMatchId())
                    .currentPlayers(room.getTotalPlayerCount())
                    .neededPlayers(room.getNeededPlayers())
                    .gameMode(room.getGameMode().getCode())
                    .build();

            String json = objectMapper.writeValueAsString(notify);
            MessagePacket packet = MessagePacket.of(
                    ProtocolConstants.EXTENSION_MATCH,
                    ProtocolConstants.CMD_MATCH_PROGRESS_NOTIFY,
                    json);

            channelManager.pushToPlayers(userIds, packet);
            log.debug("推送匹配进度: matchId={}, {}/{}", room.getMatchId(),
                    room.getTotalPlayerCount(), room.getNeededPlayers());
        } catch (Exception e) {
            log.error("推送匹配进度失败: matchId={}", room.getMatchId(), e);
        }
    }
}
