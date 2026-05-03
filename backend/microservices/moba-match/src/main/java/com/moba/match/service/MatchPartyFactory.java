package com.moba.match.service;

import com.moba.common.constant.GameMode;
import com.moba.match.model.MatchParty;
import com.moba.match.model.MatchPartyMember;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MatchPartyFactory {

    public MatchParty createSoloParty(long userId, String nickname, int rankScore, GameMode gameMode) {
        MatchPartyMember leader = createMember(userId, nickname, rankScore);
        MatchParty party = new MatchParty();
        party.setPartyId(String.valueOf(userId));
        party.setLeaderId(userId);
        party.setGameMode(gameMode);
        party.setMembers(List.of(leader));
        return party;
    }

    public MatchParty createMultiParty(String partyId, long leaderId, GameMode gameMode,
                                       List<Long> userIds, List<String> nicknames,
                                       List<Integer> rankScores) {
        MatchParty party = new MatchParty();
        party.setPartyId(partyId);
        party.setLeaderId(leaderId);
        party.setGameMode(gameMode);

        List<MatchPartyMember> members = new ArrayList<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            String nickname = i < nicknames.size() ? nicknames.get(i) : "";
            int rankScore = i < rankScores.size() ? rankScores.get(i) : 1000;
            members.add(createMember(userIds.get(i), nickname, rankScore));
        }
        party.setMembers(members);
        return party;
    }

    public MatchPartyMember createMember(long userId, String nickname, int rankScore) {
        MatchPartyMember member = new MatchPartyMember();
        member.setUserId(userId);
        member.setNickname(nickname != null ? nickname : "");
        member.setRankScore(rankScore > 0 ? rankScore : 1000);
        return member;
    }
}
