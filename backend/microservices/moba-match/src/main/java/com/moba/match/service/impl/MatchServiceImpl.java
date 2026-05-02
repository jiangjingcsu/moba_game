package com.moba.match.service.impl;

import com.moba.common.dto.MatchRequestDTO;
import com.moba.common.dto.MatchResultDTO;
import com.moba.common.service.MatchService;
import com.moba.match.service.MatchmakingService;
import com.moba.match.service.MatchmakingService.MatchParty;
import com.moba.match.service.MatchmakingService.MatchPartyMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;
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

    public boolean joinMatchAsParty(String partyId, long leaderId, int gameMode,
                                     List<Long> playerIds, List<String> nicknames,
                                     List<Integer> rankScores) {
        MatchParty party = new MatchParty();
        party.setPartyId(partyId);
        party.setLeaderId(leaderId);
        party.setGameMode(gameMode);

        List<MatchPartyMember> members = new ArrayList<>(playerIds.size());
        for (int i = 0; i < playerIds.size(); i++) {
            MatchPartyMember member = new MatchPartyMember();
            member.setPlayerId(playerIds.get(i));
            member.setNickname(i < nicknames.size() ? nicknames.get(i) : "");
            member.setRankScore(i < rankScores.size() ? rankScores.get(i) : 1000);
            members.add(member);
        }
        party.setMembers(members);

        return matchmakingService.joinMatchAsParty(party);
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
