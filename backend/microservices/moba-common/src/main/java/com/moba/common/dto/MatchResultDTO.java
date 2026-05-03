package com.moba.common.dto;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class MatchResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long matchId;
    private GameMode gameMode;
    private List<Long> userIds;
    private List<Integer> teams;
    private long matchTime;
}
