package com.moba.match.model;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.util.List;

@Data
public class MatchParty {

    private String partyId;
    private long leaderId;
    private GameMode gameMode;
    private int aiLevel;
    private List<MatchPartyMember> members;

    public int getAvgRankScore() {
        if (members == null || members.isEmpty()) return 0;
        return (int) members.stream()
                .mapToInt(MatchPartyMember::getRankScore)
                .average()
                .orElse(0);
    }
}
