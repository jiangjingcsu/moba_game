package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class MatchRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long playerId;
    private String nickname;
    private int rankScore;
    private int gameMode;
}
