package com.moba.battle.model;

import lombok.Data;

@Data
public class HeroConfig {
    private final int heroId;
    private final String heroName;
    private final int baseHp;
    private final int baseMp;
    private final int baseAttack;
    private final int baseDefense;
    private final int moveSpeed;
    private final int attackRange;
    private final int collisionRadius;

    public static HeroConfig getHeroConfig(int heroId) {
        switch (heroId) {
            case 1:
                return new HeroConfig(1, "战士", 6000, 1500, 120, 80, 350, 150, 50);
            case 2:
                return new HeroConfig(2, "法师", 4000, 3000, 150, 40, 330, 500, 40);
            case 3:
                return new HeroConfig(3, "刺客", 4500, 2000, 180, 50, 380, 150, 45);
            case 4:
                return new HeroConfig(4, "射手", 4200, 2200, 160, 45, 360, 600, 40);
            case 5:
                return new HeroConfig(5, "坦克", 7000, 1500, 90, 100, 320, 150, 60);
            case 6:
                return new HeroConfig(6, "辅助", 4800, 2500, 100, 60, 340, 400, 45);
            default:
                return new HeroConfig(heroId, "默认英雄", 5000, 2000, 100, 50, 350, 400, 50);
        }
    }
}
