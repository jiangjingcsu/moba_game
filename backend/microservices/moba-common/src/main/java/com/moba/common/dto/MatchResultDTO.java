package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class MatchResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String matchId;
    private int gameMode;
    private List<Long> playerIds;
    private List<Integer> teams;
    private long matchTime;
}
