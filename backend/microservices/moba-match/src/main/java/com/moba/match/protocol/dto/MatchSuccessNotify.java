package com.moba.match.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchSuccessNotify {

    private long matchId;
    private long battleId;
    private int gameMode;
    private int teamCount;
    private List<Long> userIds;
    private String battleServerIp;
    private int battleServerPort;
    private boolean aiMode;
    private int aiLevel;
    private long matchTime;
}
