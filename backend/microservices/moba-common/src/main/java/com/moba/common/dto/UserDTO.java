package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private Integer level;
    private Integer rankScore;
    private Integer totalBattles;
    private Integer winCount;
    private Integer loseCount;
    private String state;
}
