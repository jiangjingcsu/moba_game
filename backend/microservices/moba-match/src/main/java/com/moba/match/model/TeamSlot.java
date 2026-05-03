package com.moba.match.model;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Data
public class TeamSlot {

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

    public void removeMember(long userId) {
        members.removeIf(m -> m.getUserId() == userId);
    }

    public void removeParty(String partyId) {
        int idx = partyIds.indexOf(partyId);
        if (idx >= 0) {
            partyIds.remove(idx);
        }
    }

    public boolean containsPlayer(long userId) {
        return members.stream().anyMatch(m -> m.getUserId() == userId);
    }
}
