package com.moba.common.event;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class MatchSuccessEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private long timestamp;
    private long matchId;
    private long battleId;
    private GameMode gameMode;
    private List<Long> userIds;
    private List<Integer> teams;
    private int teamCount;
    private int neededBots;
    private int aiLevel;
    private boolean aiMode;
    private long matchTime;
    private String battleServerIp;
    private int battleServerPort;
}
