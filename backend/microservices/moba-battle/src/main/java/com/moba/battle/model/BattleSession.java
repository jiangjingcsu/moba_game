package com.moba.battle.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class BattleSession {
    private final long battleId;
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

    public BattleSession(long battleId, int teamCount, int mapId) {
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

    public void addPlayer(long userId, BattlePlayer player) {
        battlePlayers.put(userId, player);
        Team team = teams.get(player.getTeamId());
        if (team != null) {
            team.addPlayer(userId);
        }
    }

    public BattlePlayer getPlayer(long userId) {
        return battlePlayers.get(userId);
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
                    log.info("防御塔被摧毁! 队伍{}失去一座防御塔, 基地生命值={}", team.getTeamId(), team.getBaseHp());
                } else {
                    team.setBaseHp(Math.max(0, team.getBaseHp() - damage * 2));
                    log.info("基地受损! 队伍{} 基地生命值={}", team.getTeamId(), team.getBaseHp());
                }
            }
        }
    }

    public void applyBaseDamage(int defendingTeamId, int damage) {
        Team team = teams.get(defendingTeamId);
        if (team != null) {
            team.setBaseHp(Math.max(0, team.getBaseHp() - damage));
            log.info("基地直接受损! 队伍{} 基地生命值={}", defendingTeamId, team.getBaseHp());
        }
    }

    @Data
    public static class Team {
        private final int teamId;
        private final List<Long> userIds;
        private int towerCount;
        private int baseHp;

        public Team(int teamId) {
            this.teamId = teamId;
            this.userIds = Collections.synchronizedList(new ArrayList<>());
            this.towerCount = 3;
            this.baseHp = 10000;
        }

        public void addPlayer(Long userId) {
            userIds.add(userId);
        }

        public boolean isDefeated() {
            return baseHp <= 0;
        }
    }

    @Data
    public static class BattleEvent {
        private long timestamp;
        private EventType type;
        private long userId;
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
