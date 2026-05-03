package com.moba.match.model;

import com.moba.common.constant.GameMode;
import com.moba.common.model.MatchInfo;
import com.moba.common.util.SnowflakeIdGenerator;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class MatchRoom {

    private long matchId;
    private GameMode gameMode;
    private int teamCount;
    private int teamSize;
    private int neededPlayers;
    private long createTime;
    private MatchInfo.MatchState state;
    private List<TeamSlot> teams;
    private boolean aiMode;
    private int aiLevel = GameMode.MODE_AI_5V5.getDefaultAiLevel();
    private long assignedBattleId;
    private String battleServerIp;
    private int battleServerPort;

    public static MatchRoom createForMatcher(ScoreRangeMatcher matcher, MatchParty firstParty) {
        MatchRoom room = new MatchRoom();
        room.matchId = SnowflakeIdGenerator.getDefault().nextId();
        room.gameMode = matcher.getGameMode();
        room.teamCount = matcher.getTeamCount();
        room.teamSize = matcher.getTeamSize();
        room.neededPlayers = matcher.getTotalPlayers();
        room.createTime = System.currentTimeMillis();
        room.state = MatchInfo.MatchState.PENDING;
        room.aiMode = matcher.isAiMode();
        if (matcher.isAiMode() && firstParty.getAiLevel() > 0) {
            room.aiLevel = firstParty.getAiLevel();
        }
        room.initTeams();
        return room;
    }

    public synchronized void initTeams() {
        teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            teams.add(new TeamSlot(i, teamSize));
        }
    }

    public synchronized int assignParty(MatchParty party) {
        return assignParty(party, -1);
    }

    public synchronized int assignParty(MatchParty party, int preferredTeam) {
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

    public synchronized int getTotalPlayerCount() {
        return teams.stream().mapToInt(TeamSlot::getPlayerCount).sum();
    }

    public synchronized int getAvailableSlotsInBestTeam(int partySize) {
        int maxSlots = 0;
        for (TeamSlot team : teams) {
            if (team.getAvailableSlots() >= partySize) {
                maxSlots = Math.max(maxSlots, team.getAvailableSlots());
            }
        }
        return maxSlots;
    }

    public synchronized int getAvgRankScore() {
        return (int) teams.stream()
                .flatMap(t -> t.getMembers().stream())
                .mapToInt(MatchPartyMember::getRankScore)
                .average()
                .orElse(0);
    }

    public synchronized List<Long> getAllUserIds() {
        return teams.stream()
                .flatMap(t -> t.getMembers().stream())
                .map(MatchPartyMember::getUserId)
                .collect(Collectors.toList());
    }

    public synchronized MatchInfo toMatchInfo() {
        MatchInfo info = new MatchInfo();
        info.setMatchId(matchId);
        info.setGameMode(gameMode);
        info.setState(state);
        info.setCreateTime(createTime);
        info.setUserIds(getAllUserIds());
        info.setNeededPlayers(neededPlayers);
        info.setAiLevel(aiLevel);
        info.setAiMode(aiMode);
        return info;
    }

    public synchronized void setState(MatchInfo.MatchState state) {
        this.state = state;
    }

    public synchronized MatchInfo.MatchState getState() {
        return state;
    }
}
