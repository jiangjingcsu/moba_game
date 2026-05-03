package com.moba.common.dto;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ReplayDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long battleId;
    private GameMode gameMode;
    private long startTime;
    private long endTime;
    private int winnerTeamId;
    private long frameCount;
    private long randomSeed;
    private List<PlayerInfoDTO> players;
    private byte[] frameData;
    private byte[] snapshotData;

    @Data
    public static class PlayerInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private long userId;
        private String nickname;
        private int teamId;
        private int heroId;
        private int finalKillCount;
        private int finalDeathCount;
        private int finalAssistCount;
    }
}
