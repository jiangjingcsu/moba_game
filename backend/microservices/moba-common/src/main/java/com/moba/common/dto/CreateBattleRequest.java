package com.moba.common.dto;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CreateBattleRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private long battleId;
    private List<Long> userIds;
    private GameMode gameMode;
    private int teamCount;
    private int neededBots;
    private int aiLevel;
    private boolean aiMode;
}
