package com.moba.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PlayerStatDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private long playerId;
    private int teamId;
    private int heroId;
    private int level;
    private int killCount;
    private int deathCount;
    private int assistCount;
    private int damageDealt;
    private int damageTaken;
    private int healing;
    private int goldEarned;
    private int experienceEarned;
    private boolean isAI;
    private boolean isWinner;
}
