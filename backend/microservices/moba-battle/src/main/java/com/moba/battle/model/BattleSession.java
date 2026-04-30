package com.moba.battle.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class BattleSession {
    private final String battleId;
    private final int teamCount;
    private final int mapId;
    private int mapWidth;
    private int mapHeight;
    private BattleState state;
    private long startTime;
    private long endTime;
    private Map<Integer, Team> teams;
    private Map<Long, BattlePlayer> battlePlayers;
    private Map<String, Object> gameState;
    private List<BattleEvent> battleEvents;

    public enum BattleState {
        WAITING,
        LOADING,
        IN_PROGRESS,
        FINISHED
    }

    public BattleSession(String battleId, int teamCount, int mapId) {
        this.battleId = battleId;
        this.teamCount = teamCount;
        this.mapId = mapId;
        this.state = BattleState.WAITING;
        this.teams = new ConcurrentHashMap<>();
        this.battlePlayers = new ConcurrentHashMap<>();
        this.gameState = new ConcurrentHashMap<>();
        this.battleEvents = Collections.synchronizedList(new ArrayList<>());
        this.startTime = 0;
        this.endTime = 0;

        for (int i = 0; i < teamCount; i++) {
            teams.put(i, new Team(i));
        }
    }

    public void addPlayer(long playerId, BattlePlayer player) {
        battlePlayers.put(playerId, player);
        Team team = teams.get(player.getTeamId());
        if (team != null) {
            team.addPlayer(playerId);
        }
    }

    public BattlePlayer getPlayer(long playerId) {
        return battlePlayers.get(playerId);
    }

    public void start() {
        this.state = BattleState.IN_PROGRESS;
        this.startTime = System.currentTimeMillis();
    }

    public void finish() {
        this.state = BattleState.FINISHED;
        this.endTime = System.currentTimeMillis();
    }

    public void recordEvent(BattleEvent event) {
        battleEvents.add(event);
    }

    public int getPlayerCount() {
        return battlePlayers.size();
    }

    public int getAlivePlayerCount() {
        return (int) battlePlayers.values().stream()
                .filter(p -> !p.isDead())
                .count();
    }

    public void applyTowerDamage(int attackingTeamId, int damage) {
        for (Map.Entry<Integer, Team> entry : teams.entrySet()) {
            if (entry.getKey() != attackingTeamId) {
                Team team = entry.getValue();
                if (team.getTowerCount() > 0) {
                    team.setTowerCount(team.getTowerCount() - 1);
                    team.setBaseHp(Math.max(0, team.getBaseHp() - damage));
                    log.info("Tower destroyed! Team {} lost a tower, baseHp={}", team.getTeamId(), team.getBaseHp());
                } else {
                    team.setBaseHp(Math.max(0, team.getBaseHp() - damage * 2));
                    log.info("Base damaged! Team {} baseHp={}", team.getTeamId(), team.getBaseHp());
                }
            }
        }
    }

    public void applyBaseDamage(int defendingTeamId, int damage) {
        Team team = teams.get(defendingTeamId);
        if (team != null) {
            team.setBaseHp(Math.max(0, team.getBaseHp() - damage));
            log.info("Base damaged directly! Team {} baseHp={}", defendingTeamId, team.getBaseHp());
        }
    }

    @Data
    public static class Team {
        private final int teamId;
        private final List<Long> playerIds;
        private int towerCount;
        private int baseHp;

        public Team(int teamId) {
            this.teamId = teamId;
            this.playerIds = Collections.synchronizedList(new ArrayList<>());
            this.towerCount = 3;
            this.baseHp = 10000;
        }

        public void addPlayer(Long playerId) {
            playerIds.add(playerId);
        }

        public boolean isDefeated() {
            return baseHp <= 0;
        }
    }

    @Data
    public static class BattleEvent {
        private long timestamp;
        private EventType type;
        private long playerId;
        private String data;

        public enum EventType {
            KILL,
            DEATH,
            ASSIST,
            TOWER_DESTROY,
            JUNGLE_MONSTER_KILL,
            ITEM_PURCHASE,
            LEVEL_UP
        }
    }
}
