package com.moba.data.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "replays")
public class Replay {
    @Id
    private String id;

    @Indexed
    private String battleId;

    @Indexed
    private int gameMode;

    @Indexed
    private long startTime;
    private long endTime;

    private int winnerTeamId;
    private long frameCount;
    private long randomSeed;

    private List<PlayerInfo> players;
    private byte[] frameData;
    private byte[] snapshotData;

    @Data
    public static class PlayerInfo {
        private long playerId;
        private String nickname;
        private int teamId;
        private int heroId;
        private int finalKillCount;
        private int finalDeathCount;
        private int finalAssistCount;
    }
}
