package com.moba.common.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class Hero implements Serializable {
    private static final long serialVersionUID = 1L;

    private int heroId;
    private String heroName;
    private String heroType;
    private int baseHp;
    private int baseMp;
    private int baseAttack;
    private int baseDefense;
    private int baseMoveSpeed;
    private int attackRange;
    private int[] skillIds;
}
