package com.moba.match.model;

import com.moba.common.constant.GameMode;
import com.moba.common.model.MatchInfo;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Getter
public class ScoreRangeMatcher {

    private final ScoreRange scoreRange;
    private final GameMode gameMode;
    private final Map<Long, MatchRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Long> playerToRoom = new ConcurrentHashMap<>();
    private final Map<String, MatchParty> partyRegistry = new ConcurrentHashMap<>();
    private final Map<Long, String> playerToParty = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ScoreRangeMatcher(ScoreRange scoreRange, GameMode gameMode) {
        this.scoreRange = scoreRange;
        this.gameMode = gameMode;
    }

    public int getTeamCount() { return gameMode.getTeamCount(); }
    public int getTeamSize() { return gameMode.getTeamSize(); }
    public int getTotalPlayers() { return gameMode.getNeededPlayers(); }
    public int getMaxRealPlayers() { return gameMode.getMaxRealPlayers(); }
    public boolean isAiMode() { return gameMode.isAiMode(); }

    public void addRoom(MatchRoom room) {
        lock.writeLock().lock();
        try {
            rooms.put(room.getMatchId(), room);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeRoom(long matchId) {
        lock.writeLock().lock();
        try {
            MatchRoom room = rooms.remove(matchId);
            if (room != null) {
                for (long userId : room.getAllUserIds()) {
                    playerToRoom.remove(userId);
                    playerToParty.remove(userId);
                }
                for (TeamSlot team : room.getTeams()) {
                    for (String partyId : team.getPartyIds()) {
                        partyRegistry.remove(partyId);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MatchRoom getRoom(long matchId) {
        lock.readLock().lock();
        try {
            return rooms.get(matchId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void registerParty(MatchParty party, long matchId) {
        lock.writeLock().lock();
        try {
            partyRegistry.put(party.getPartyId(), party);
            for (MatchPartyMember member : party.getMembers()) {
                playerToRoom.put(member.getUserId(), matchId);
                playerToParty.put(member.getUserId(), party.getPartyId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isPlayerInQueue(long userId) {
        lock.readLock().lock();
        try {
            return playerToRoom.containsKey(userId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean removePlayerFromRoom(long userId, long matchId) {
        lock.writeLock().lock();
        try {
            MatchRoom room = rooms.get(matchId);
            if (room == null) return false;

            for (TeamSlot team : room.getTeams()) {
                if (team.containsPlayer(userId)) {
                    String partyId = playerToParty.get(userId);
                    if (partyId != null) {
                        MatchParty party = partyRegistry.get(partyId);
                        if (party != null) {
                            for (MatchPartyMember member : party.getMembers()) {
                                playerToRoom.remove(member.getUserId());
                                playerToParty.remove(member.getUserId());
                                team.removeMember(member.getUserId());
                            }
                            partyRegistry.remove(partyId);
                        }
                    } else {
                        playerToRoom.remove(userId);
                        playerToParty.remove(userId);
                        team.removeMember(userId);
                    }

                    if (room.getTotalPlayerCount() == 0) {
                        rooms.remove(matchId);
                    }
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MatchRoom findBestRoom(int avgRankScore, int partySize, int tolerance) {
        lock.readLock().lock();
        try {
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
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<MatchRoom> getReadyRooms() {
        lock.readLock().lock();
        try {
            return rooms.values().stream()
                    .filter(r -> {
                        int needed = isAiMode() ? getMaxRealPlayers() : r.getNeededPlayers();
                        return r.getTotalPlayerCount() >= needed;
                    })
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<MatchParty> getWaitingPartiesNotInRoom(long matchId) {
        lock.readLock().lock();
        try {
            Set<Long> playersInRoom = new HashSet<>();
            MatchRoom room = rooms.get(matchId);
            if (room != null) {
                playersInRoom.addAll(room.getAllUserIds());
            }

            return partyRegistry.values().stream()
                    .filter(p -> p.getMembers().stream()
                            .noneMatch(m -> playersInRoom.contains(m.getUserId())))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWaitingPlayerCount() {
        lock.readLock().lock();
        try {
            return playerToRoom.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
