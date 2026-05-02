package com.moba.battle.model;

import lombok.Data;

@Data
public class TerrainRegion {
    private int id;
    private int x;
    private int y;
    private int width;
    private int height;
    private TerrainType terrainType;
    private float speedFactor;
    private int teamId;
    private int healRate;

    public enum TerrainType {
        OBSTACLE,
        FLYABLE,
        BUSH,
        RIVER,
        BASE
    }

    public float getCenterX() {
        return x + width / 2f;
    }

    public float getCenterY() {
        return y + height / 2f;
    }

    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
