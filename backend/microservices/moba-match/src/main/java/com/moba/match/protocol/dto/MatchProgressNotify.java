package com.moba.match.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchProgressNotify {

    private long matchId;
    private int currentPlayers;
    private int neededPlayers;
    private int gameMode;
}
