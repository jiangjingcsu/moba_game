package com.moba.common.dto;

import com.moba.common.constant.GameMode;
import lombok.Data;

import java.io.Serializable;

@Data
public class MatchRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String nickname;
    private int rankScore;
    private GameMode gameMode;
}
