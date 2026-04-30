package com.moba.battleserver.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FrameState {
    private int frameNumber;
    private Map<Long, FixedPosition> playerPositions;
    private Map<Long, Integer> playerHp;
    private Map<Long, Integer> playerMp;
    private List<FrameInput> inputs;

    @Data
    public static class FixedPosition {
        public final int x;
        public final int y;

        public FixedPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public long computeHash() {
        long hash = frameNumber;
        if (playerPositions != null) {
            for (Map.Entry<Long, FixedPosition> entry : playerPositions.entrySet()) {
                hash = hash * 31 + entry.getKey();
                hash = hash * 31 + entry.getValue().x;
                hash = hash * 31 + entry.getValue().y;
            }
        }
        if (playerHp != null) {
            for (Map.Entry<Long, Integer> entry : playerHp.entrySet()) {
                hash = hash * 31 + entry.getKey();
                hash = hash * 31 + entry.getValue();
            }
        }
        return hash;
    }
}
