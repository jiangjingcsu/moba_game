package com.moba.common.event;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class MatchSuccessEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private long timestamp;
    private String matchId;
    private int gameMode;
    private List<Long> playerIds;
    private List<Integer> teams;
    private int neededBots;
    private int aiLevel;
    private long matchTime;
}
