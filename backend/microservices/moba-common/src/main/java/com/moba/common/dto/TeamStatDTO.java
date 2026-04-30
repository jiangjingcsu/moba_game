package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TeamStatDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int teamId;
    private int totalKills;
    private int totalDeaths;
    private int towerDestroyed;
    private int barracksDestroyed;
}
