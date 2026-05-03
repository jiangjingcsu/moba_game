package com.moba.match.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStatusResponse {

    private boolean success;
    private boolean found;
    private long battleId;
    private int gameMode;
    private long matchTime;
    private String state;
    private int currentPlayers;
    private int neededPlayers;
    private String battleServerIp;
    private int battleServerPort;
}
