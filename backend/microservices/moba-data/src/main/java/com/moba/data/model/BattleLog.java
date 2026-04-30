package com.moba.data.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "battle_logs")
public class BattleLog {
    @Id
    private String id;

    @Indexed
    private String battleId;

    @Indexed
    private int gameMode;

    @Indexed
    private long startTime;

    private long endTime;
    private long duration;

    @Indexed
    private int winnerTeamId;

    private List<PlayerLog> players;
    private List<FrameEvent> events;
    private Map<Integer, TeamStat> teamStats;

    @Data
    public static class PlayerLog {
        private long playerId;
        private int teamId;
        private int heroId;
        private int level;
        private int killCount;
        private int deathCount;
        private int assistCount;
        private int damageDealt;
        private int damageTaken;
        private int healing;
        private int goldEarned;
        private int experienceEarned;
        private boolean isAI;
        private boolean isWinner;
    }

    @Data
    public static class TeamStat {
        private int teamId;
        private int totalKills;
        private int totalDeaths;
        private int towerDestroyed;
        private int barracksDestroyed;
    }

    @Data
    public static class FrameEvent {
        private long frameNumber;
        private String eventType;
        private long playerId;
        private int value1;
        private int value2;
        private String description;
    }
}
