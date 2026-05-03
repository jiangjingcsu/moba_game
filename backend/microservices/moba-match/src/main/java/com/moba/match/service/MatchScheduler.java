package com.moba.match.service;

import com.moba.common.constant.BattleConstants;
import com.moba.common.model.MatchInfo;
import com.moba.match.config.NacosConfigManager;
import com.moba.match.model.MatchParty;
import com.moba.match.model.MatchPartyMember;
import com.moba.match.model.MatchRoom;
import com.moba.match.model.ScoreRangeMatcher;
import com.moba.match.model.TeamSlot;
import com.moba.match.repository.MatchRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchScheduler {

    private final MatchmakingService matchmakingService;
    private final MatchBattleAllocator matchBattleAllocator;
    private final NacosConfigManager nacosConfigManager;
    private final MatchRedisRepository matchRedisRepository;

    public MatchScheduler(MatchmakingService matchmakingService,
                          MatchBattleAllocator matchBattleAllocator,
                          NacosConfigManager nacosConfigManager,
                          MatchRedisRepository matchRedisRepository) {
        this.matchmakingService = matchmakingService;
        this.matchBattleAllocator = matchBattleAllocator;
        this.nacosConfigManager = nacosConfigManager;
        this.matchRedisRepository = matchRedisRepository;
    }

    @Scheduled(fixedRateString = "${match.cleanupIntervalMs}")
    public void cleanupExpiredMatches() {
        long now = System.currentTimeMillis();
        long timeoutMs = BattleConstants.MATCHMAKING_TIMEOUT_SECONDS * 1000L;

        for (ScoreRangeMatcher matcher : matchmakingService.getAllMatchers().values()) {
            if (matcher.isAiMode()) continue;

            List<MatchRoom> expired = matcher.getRooms().values().stream()
                    .filter(r -> r.getState() == MatchInfo.MatchState.PENDING
                            || r.getState() == MatchInfo.MatchState.FILLING)
                    .filter(r -> now - r.getCreateTime() > timeoutMs)
                    .collect(Collectors.toList());

            for (MatchRoom room : expired) {
                room.setState(MatchInfo.MatchState.TIMEOUT);
                for (Long pid : room.getAllUserIds()) {
                    matchRedisRepository.cleanupPlayerState(pid);
                }
                Long battleId = matchRedisRepository.getBattleIdByMatchId(room.getMatchId());
                matchRedisRepository.cleanupMatchState(room.getMatchId(), battleId != null ? battleId : 0);
                matcher.removeRoom(room.getMatchId());
                log.info("匹配 {} 在 {}秒后超时 (匹配器[{}]), 已清理{}名玩家缓存",
                        room.getMatchId(), BattleConstants.MATCHMAKING_TIMEOUT_SECONDS,
                        matcher.getScoreRange().getName(), room.getAllUserIds().size());
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

        for (ScoreRangeMatcher matcher : matchmakingService.getAllMatchers().values()) {
            if (matcher.isAiMode()) continue;

            List<MatchRoom> fillingRooms = matcher.getRooms().values().stream()
                    .filter(r -> r.getState() == MatchInfo.MatchState.FILLING)
                    .filter(r -> r.getTotalPlayerCount() < r.getNeededPlayers())
                    .collect(Collectors.toList());

            for (MatchRoom room : fillingRooms) {
                long waitSeconds = (now - room.getCreateTime()) / 1000;
                int expandedTolerance = (int) Math.min(
                        initialTolerance + (waitSeconds / expandInterval) * expandStep,
                        maxTolerance);

                if (expandedTolerance <= initialTolerance) continue;

                if (tryExpandRoom(room, matcher, expandedTolerance)) {
                    checkAndStartMatch(matcher, room);
                }
            }
        }
    }

    private boolean tryExpandRoom(MatchRoom room, ScoreRangeMatcher matcher, int expandedTolerance) {
        int avgRankScore = room.getAvgRankScore();
        List<MatchParty> waitingParties = matcher.getWaitingPartiesNotInRoom(room.getMatchId());
        boolean anyMigrated = false;

        for (MatchParty party : waitingParties) {
            if (Math.abs(party.getAvgRankScore() - avgRankScore) > expandedTolerance) continue;
            if (room.getAvailableSlotsInBestTeam(party.getMembers().size()) < party.getMembers().size()) continue;

            migratePartyToRoom(party, room, matcher);
            anyMigrated = true;

            if (room.getState() == MatchInfo.MatchState.READY) {
                break;
            }
        }
        return anyMigrated;
    }

    private void migratePartyToRoom(MatchParty party, MatchRoom targetRoom, ScoreRangeMatcher matcher) {
        Long oldMatchId = matcher.getPlayerToRoom().get(party.getMembers().get(0).getUserId());
        if (oldMatchId != null && !oldMatchId.equals(targetRoom.getMatchId())) {
            MatchRoom oldRoom = matcher.getRoom(oldMatchId);
            if (oldRoom != null) {
                synchronized (oldRoom) {
                    for (MatchPartyMember member : party.getMembers()) {
                        for (TeamSlot team : oldRoom.getTeams()) {
                            team.removeMember(member.getUserId());
                        }
                    }
                    for (TeamSlot team : oldRoom.getTeams()) {
                        team.removeParty(party.getPartyId());
                    }
                    if (oldRoom.getTotalPlayerCount() == 0) {
                        matcher.getRooms().remove(oldMatchId);
                    }
                }
            }
        }

        synchronized (targetRoom) {
            targetRoom.assignParty(party);
            matcher.registerParty(party, targetRoom.getMatchId());

            for (MatchPartyMember member : party.getMembers()) {
                matchRedisRepository.bindPlayerToMatch(member.getUserId(), targetRoom.getMatchId());
            }

            log.info("组队 {}(平均段位={}) 以扩展容忍度迁移到 {} (匹配器[{}])",
                    party.getPartyId(), party.getAvgRankScore(),
                    targetRoom.getMatchId(), matcher.getScoreRange().getName());

            if (targetRoom.getTotalPlayerCount() >= targetRoom.getNeededPlayers()) {
                targetRoom.setState(MatchInfo.MatchState.READY);
            }
        }
    }

    private void checkAndStartMatch(ScoreRangeMatcher matcher, MatchRoom room) {
        if (room.getState() != MatchInfo.MatchState.READY) return;
        synchronized (room) {
            try {
                boolean success = matchBattleAllocator.allocateAndStart(room);
                if (success) {
                    matcher.removeRoom(room.getMatchId());
                }
            } catch (Exception e) {
                log.error("为匹配 {} 启动战斗失败", room.getMatchId(), e);
                room.setState(MatchInfo.MatchState.FILLING);
            }
        }
    }
}
