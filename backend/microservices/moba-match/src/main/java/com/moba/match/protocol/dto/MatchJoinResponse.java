package com.moba.match.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchJoinResponse {

    private boolean success;
    private long battleId;
    private int gameMode;
    private int currentPlayers;
    private int neededPlayers;
    private String state;
    private int partySize;
    private String errorCode;
    private String errorMessage;
}
