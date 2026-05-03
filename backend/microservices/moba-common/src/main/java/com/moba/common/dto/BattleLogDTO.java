package com.moba.common.dto;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class BattleLogDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long battleId;
    private GameMode gameMode;
    private long startTime;
    private long endTime;
    private long duration;
    private int winnerTeamId;
    private List<PlayerStatDTO> players;
    private List<FrameEventDTO> events;
    private Map<Integer, TeamStatDTO> teamStats;

    @Data
    public static class FrameEventDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private long frameNumber;
        private String eventType;
        private long userId;
        private int value1;
        private int value2;
        private String description;
    }
}
