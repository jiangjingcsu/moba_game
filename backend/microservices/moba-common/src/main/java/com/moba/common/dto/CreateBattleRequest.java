package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CreateBattleRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String battleId;
    private List<Long> playerIds;
    private int gameMode;
    private int teamCount;
    private int neededBots;
    private int aiLevel;
}
